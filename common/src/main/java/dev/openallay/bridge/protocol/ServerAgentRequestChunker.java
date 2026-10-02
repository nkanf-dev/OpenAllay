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

public final class ServerAgentRequestChunker {
    public List<ServerAgentRequestChunkPayload> split(
            UUID requestId, String content, int transportChunkBytes) {
        return split(requestId, content, transportChunkBytes, BridgeProtocol.MAX_OPENAI_REQUEST_BYTES);
    }

    public List<ServerAgentRequestChunkPayload> split(
            UUID requestId, String content, int transportChunkBytes, int maxEncodedRequestBytes) {
        java.util.Objects.requireNonNull(requestId, "requestId");
        java.util.Objects.requireNonNull(content, "content");
        validateMaximum(maxEncodedRequestBytes);
        if (transportChunkBytes <= 0 || transportChunkBytes > BridgeProtocol.TRANSPORT_CHUNK_BYTES) {
            throw new IllegalArgumentException("transportChunkBytes exceeds the transport envelope");
        }
        if (content.length() > maxEncodedRequestBytes || utf8Bytes(content, maxEncodedRequestBytes) > maxEncodedRequestBytes) {
            throw new IllegalArgumentException("Encoded server Agent request exceeds its application envelope");
        }
        byte[] all = content.getBytes(StandardCharsets.UTF_8);
        String hash = ResultChunker.sha256(all);
        int total = Math.max(1, (int) ((all.length + (long) transportChunkBytes - 1) / transportChunkBytes));
        if (total > maxChunks(maxEncodedRequestBytes)) {
            throw new IllegalArgumentException("Encoded request requires too many transport chunks");
        }
        List<ServerAgentRequestChunkPayload> chunks = new ArrayList<>(total);
        for (int index = 0; index < total; index++) {
            int start = index * transportChunkBytes;
            int end = Math.min(all.length, start + transportChunkBytes);
            byte[] part = java.util.Arrays.copyOfRange(all, start, end);
            chunks.add(new ServerAgentRequestChunkPayload(
                    requestId,
                    index,
                    total,
                    hash,
                    Base64.getEncoder().encodeToString(part)));
        }
        return List.copyOf(chunks);
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
                    Thread thread = new Thread(runnable, "openallay-request-chunk-timeouts");
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
                UUID actorId, ServerAgentRequestChunkPayload chunk) {
            java.util.Objects.requireNonNull(chunk, "chunk");
            Key key = new Key(actorId, chunk.requestId());
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
                output.writeBytes(assembly.parts.get(index));
            }
            byte[] complete = output.toByteArray();
            remove(key);
            if (!ResultChunker.sha256(complete).equals(assembly.hash)) {
                throw new IllegalArgumentException(
                        "Reassembled server Agent request hash does not match");
            }
            return java.util.Optional.of(new String(complete, StandardCharsets.UTF_8));
        }

        public synchronized boolean cancel(UUID actorId, UUID requestId) {
            return remove(new Key(actorId, requestId));
        }

        public synchronized void clearActor(UUID actorId) {
            List<Key> owned = assemblies.keySet().stream()
                    .filter(key -> key.actorId().equals(actorId))
                    .toList();
            owned.forEach(this::remove);
        }

        synchronized int activeAssemblies() {
            return assemblies.size();
        }

        private synchronized void expire(Key key, Assembly expected) {
            assemblies.remove(key, expected);
        }

        private boolean remove(Key key) {
            Assembly removed = assemblies.remove(key);
            if (removed != null) removed.cancelDeadline();
            return removed != null;
        }

        private record Key(UUID actorId, UUID requestId) {
            private Key {
                java.util.Objects.requireNonNull(actorId, "actorId");
                java.util.Objects.requireNonNull(requestId, "requestId");
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
