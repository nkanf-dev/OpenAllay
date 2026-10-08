package dev.openallay.bridge.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class ServerAgentSteerChunker {
    public List<ServerAgentSteerChunkPayload> split(
            UUID requestId, UUID messageId, String content, int transportChunkBytes) {
        return split(requestId, messageId, content, transportChunkBytes, BridgeProtocol.MAX_OPENAI_REQUEST_BYTES);
    }

    public List<ServerAgentSteerChunkPayload> split(
            UUID requestId, UUID messageId, String content, int transportChunkBytes, int maximumBytes) {
        java.util.Objects.requireNonNull(requestId, "requestId");
        return dev.openallay.util.Java8Collections.toList(new ServerAgentRequestChunker().split(messageId, content, transportChunkBytes, maximumBytes).stream()
                .map(chunk -> new ServerAgentSteerChunkPayload(requestId, messageId, chunk.index(),
                        chunk.total(), chunk.contentHash(), chunk.base64Data())));
    }

    private static long utf8Bytes(String content, int maximum) {
        long bytes = 0;
        for (int index = 0; index < content.length(); index++) {
            char value = content.charAt(index);
            if (value <= 0x7f) bytes++;
            else if (value <= 0x7ff) bytes += 2;
            else if (Character.isHighSurrogate(value) && index + 1 < content.length()
                    && Character.isLowSurrogate(content.charAt(index + 1))) {
                bytes += 4;
                index++;
            } else bytes += Character.isSurrogate(value) ? 1 : 3;
            if (bytes > maximum) return bytes;
        }
        return bytes;
    }

    private static void validateMaximum(int maximum) {
        if (maximum <= 0 || maximum > BridgeProtocol.MAX_OPENAI_REQUEST_BYTES) {
            throw new IllegalArgumentException("Encoded request maximum must fit the application envelope");
        }
    }

    private static int maxChunks(int maximum) {
        return (int) ((maximum + (long) BridgeProtocol.TRANSPORT_CHUNK_BYTES - 1)
                / BridgeProtocol.TRANSPORT_CHUNK_BYTES);
    }

    public static final class Reassembler {
        private static final java.util.concurrent.ScheduledExecutorService TIMEOUTS =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "openallay-steer-chunk-timeouts");
                    thread.setDaemon(true);
                    return thread;
                });
        private final Map<Key, Assembly> assemblies = new HashMap<>();
        private final Duration timeout;
        private final int maxEncodedRequestBytes;

        public Reassembler() {
            this(BridgeProtocol.PARTIAL_ASSEMBLY_TIMEOUT, BridgeProtocol.MAX_OPENAI_REQUEST_BYTES);
        }

        public Reassembler(Duration timeout) {
            this(timeout, BridgeProtocol.MAX_OPENAI_REQUEST_BYTES);
        }

        public Reassembler(int maxEncodedRequestBytes) {
            this(BridgeProtocol.PARTIAL_ASSEMBLY_TIMEOUT, maxEncodedRequestBytes);
        }

        public Reassembler(Duration timeout, int maxEncodedRequestBytes) {
            validateMaximum(maxEncodedRequestBytes);
            this.maxEncodedRequestBytes = maxEncodedRequestBytes;
            this.timeout = java.util.Objects.requireNonNull(timeout, "timeout");
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("timeout must be positive");
            }
        }

        public synchronized java.util.Optional<String> accept(
                UUID actorId, ServerAgentSteerChunkPayload chunk) {
            java.util.Objects.requireNonNull(chunk, "chunk");
            Key key = new Key(actorId, chunk.requestId(), chunk.messageId());
            if (chunk.total() > maxChunks(maxEncodedRequestBytes)) {
                throw new IllegalArgumentException("Server Agent request has too many transport chunks");
            }
            Assembly assembly = assemblies.get(key);
            if (assembly == null && assemblies.keySet().stream().anyMatch(active -> active.actorId().equals(actorId))) {
                throw new IllegalStateException("Server Agent request resources are busy for this actor");
            }
            byte[] value = Base64.getDecoder().decode(chunk.base64Data());
            if (value.length > maxEncodedRequestBytes) {
                throw new IllegalArgumentException("Server Agent request exceeds its application envelope");
            }
            if (assembly == null) {
                assembly = new Assembly(chunk.total(), chunk.contentHash());
                Assembly scheduled = assembly;
                assembly.deadline = TIMEOUTS.schedule(
                        () -> expire(key, scheduled), timeout.toMillis(), TimeUnit.MILLISECONDS);
                assemblies.put(key, assembly);
            }
            if (assembly.total != chunk.total() || !assembly.hash.equals(chunk.contentHash())) {
                remove(key);
                throw new IllegalArgumentException(
                        "Chunk metadata changed during server Agent request assembly");
            }
            byte[] existing = assembly.parts.get(chunk.index());
            if (existing != null) {
                if (!java.util.Arrays.equals(existing, value)) {
                    remove(key);
                    throw new IllegalArgumentException("Duplicate chunk has different content");
                }
                return java.util.Optional.empty();
            }
            if (value.length > maxEncodedRequestBytes - assembly.decodedBytes) {
                remove(key);
                throw new IllegalArgumentException("Server Agent request exceeds its application envelope");
            }
            assembly.parts.put(chunk.index(), value);
            assembly.decodedBytes += value.length;
            if (assembly.parts.size() != assembly.total) {
                return java.util.Optional.empty();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream(assembly.decodedBytes);
            for (int index = 0; index < assembly.total; index++) {
                byte[] part = assembly.parts.get(index);
                output.write(part, 0, part.length);
            }
            byte[] complete = output.toByteArray();
            remove(key);
            if (!ResultChunker.sha256(complete).equals(assembly.hash)) {
                throw new IllegalArgumentException(
                        "Reassembled server Agent request hash does not match");
            }
            return java.util.Optional.of(new String(complete, StandardCharsets.UTF_8));
        }

        public synchronized boolean cancel(UUID actorId, UUID requestId, UUID messageId) {
            Key key = new Key(actorId, requestId, messageId);
            boolean present = assemblies.containsKey(key);
            remove(key);
            return present;
        }

        public synchronized void clearRequest(UUID actorId, UUID requestId) {
            List<Key> owned = dev.openallay.util.Java8Collections.toList(assemblies.keySet().stream()
                    .filter(key -> key.actorId().equals(actorId) && key.requestId().equals(requestId)));
            owned.forEach(this::remove);
        }

        public synchronized void clearActor(UUID actorId) {
            List<Key> owned = dev.openallay.util.Java8Collections.toList(assemblies.keySet().stream()
                    .filter(key -> key.actorId().equals(actorId)));
            owned.forEach(this::remove);
        }

        synchronized int activeAssemblies() {
            return assemblies.size();
        }

        private synchronized void expire(Key key, Assembly expected) {
            assemblies.remove(key, expected);
        }

        private void remove(Key key) {
            Assembly removed = assemblies.remove(key);
            if (removed != null) removed.cancelDeadline();
        }

        @dev.openallay.value.ValueType(Key.ValueSchemaProvider.class)
private static final class Key {
    private final UUID actorId;
    private final UUID requestId;
    private final UUID messageId;
    private Key(UUID actorId, UUID requestId, UUID messageId) {

                java.util.Objects.requireNonNull(actorId, "actorId");
                java.util.Objects.requireNonNull(requestId, "requestId");
                java.util.Objects.requireNonNull(messageId, "messageId");

        this.actorId = actorId;
        this.requestId = requestId;
        this.messageId = messageId;
    }
    public UUID actorId() { return actorId; }
    public UUID requestId() { return requestId; }
    public UUID messageId() { return messageId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Key)) return false;
        Key that = (Key) other;
        return java.util.Objects.equals(actorId, that.actorId) && java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(messageId, that.messageId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(actorId);
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(messageId);
        return hash;
    }
    @Override public String toString() { return "Key[actorId=" + actorId + ", requestId=" + requestId + ", messageId=" + messageId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Key> schema() {
            return new dev.openallay.value.ValueSchema<>(Key.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Key>>asList(new dev.openallay.value.ValueSchema.Component<>(Key.class, "actorId", Key::actorId), new dev.openallay.value.ValueSchema.Component<>(Key.class, "requestId", Key::requestId), new dev.openallay.value.ValueSchema.Component<>(Key.class, "messageId", Key::messageId)), arguments -> new Key((UUID) arguments[0], (UUID) arguments[1], (UUID) arguments[2]));
        }
    }
}

        private static final class Assembly {
            private final int total;
            private final String hash;
            private final Map<Integer, byte[]> parts;
            private volatile ScheduledFuture<?> deadline;
            private int decodedBytes;

            private Assembly(int total, String hash) {
                this.total = total;
                this.hash = hash;
                parts = new HashMap<>();
            }

            private void cancelDeadline() {
                ScheduledFuture<?> current = deadline;
                if (current != null) current.cancel(false);
            }
        }
    }
}
