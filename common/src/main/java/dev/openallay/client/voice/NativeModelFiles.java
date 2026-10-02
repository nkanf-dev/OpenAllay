package dev.openallay.client.voice;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Exact, current model metadata. Models contain data, never runtime jar selections. */
public final class NativeModelFiles {
    public static final String MANIFEST = "model.json";
    static final long MAX_MODEL_BYTES = 1_500_000_000L;
    static final long MAX_TOTAL_BYTES = 2_000_000_000L;
    private static final int MAX_MANIFEST_BYTES = 16_384;
    public enum ModelFamily { SENSE_VOICE, PARA_FORMER, WHISPER }
    public enum Role { TOKENS, SENSE_VOICE_MODEL, PARA_FORMER_MODEL, WHISPER_ENCODER, WHISPER_DECODER }
    public record ModelFile(Role role, String path, long bytes, String sha256) {}
    public record Model(String name, ModelFamily family, List<ModelFile> files) {
        public Model { files = List.copyOf(files); }
        public ModelFile file(Role role) {
            return files.stream().filter(file -> file.role() == role).findFirst().orElseThrow();
        }
        public Path file(Path directory, Role role) { return directory.resolve(file(role).path()); }
    }
    private NativeModelFiles() {}
    public static boolean modelReady(Path directory) {
        try { validate(directory); return true; }
        catch (IOException | RuntimeException failure) { return false; }
    }
    public static void validate(Path directory) throws IOException { readValidated(directory, new VoiceCancellation()); }
    static Model readValidated(Path directory, VoiceCancellation cancellation) throws IOException {
        cancellation.check();
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new NativeSpeechToText.Failure("model_not_installed");
        Path manifest = directory.resolve(MANIFEST);
        if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)) throw new NativeSpeechToText.Failure("model_not_installed");
        if (Files.size(manifest) > MAX_MANIFEST_BYTES) throw new NativeSpeechToText.Failure("model_integrity");
        Model model;
        try (InputStream input = Files.newInputStream(manifest); AutoCloseable hook = cancellation.onCancel(() -> close(input))) {
            byte[] bytes = input.readNBytes(MAX_MANIFEST_BYTES + 1);
            cancellation.check();
            if (bytes.length > MAX_MANIFEST_BYTES) throw new NativeSpeechToText.Failure("model_integrity");
            model = parse(new String(bytes, StandardCharsets.UTF_8));
        } catch (NativeSpeechToText.Failure failure) { throw failure;
        } catch (Exception failure) {
            cancellation.check();
            throw new NativeSpeechToText.Failure("model_integrity", failure);
        }
        for (ModelFile file : model.files()) verifyFile(directory, file.path(), file.bytes(), file.sha256(), cancellation);
        return model;
    }
    static Model parse(String json) throws IOException {
        try {
            try (var reader = new com.google.gson.stream.JsonReader(new java.io.StringReader(json))) {
                reader.setStrictness(com.google.gson.Strictness.STRICT);
                checkJson(reader, 0);
                if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT) throw new IllegalArgumentException();
            }
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) throw new IllegalArgumentException();
            JsonObject root = element.getAsJsonObject();
            exactKeys(root, Set.of("name", "family", "files"));
            String name = string(root, "name");
            if (name.isBlank() || name.length() > 100 || name.chars().anyMatch(c -> c < 32)) throw new IllegalArgumentException();
            ModelFamily family = ModelFamily.valueOf(string(root, "family"));
            JsonArray entries = root.getAsJsonArray("files");
            if (entries.size() < 2 || entries.size() > 3) throw new IllegalArgumentException();
            List<ModelFile> files = new ArrayList<>();
            Set<Role> roles = new HashSet<>();
            Set<String> paths = new HashSet<>();
            long total = 0;
            for (JsonElement entry : entries) {
                JsonObject file = entry.getAsJsonObject();
                exactKeys(file, Set.of("role", "path", "bytes", "sha256"));
                Role role = Role.valueOf(string(file, "role"));
                String path = string(file, "path");
                // One simple filename avoids traversal, drive names, symlink ancestors and archives.
                if (!path.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}") || path.equals(MANIFEST)) throw new IllegalArgumentException();
                String hash = string(file, "sha256");
                if (!hash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException();
                JsonElement count = file.get("bytes");
                if (!count.isJsonPrimitive() || !count.getAsJsonPrimitive().isNumber()
                        || !count.getAsString().matches("[1-9][0-9]{0,9}")) throw new IllegalArgumentException();
                long bytes = count.getAsLong();
                long limit = role == Role.TOKENS ? 16_000_000 : MAX_MODEL_BYTES;
                if (bytes > limit || !roles.add(role) || !paths.add(path)) throw new IllegalArgumentException();
                total += bytes;
                files.add(new ModelFile(role, path, bytes, hash));
            }
            Set<Role> required = switch (family) {
                case SENSE_VOICE -> Set.of(Role.TOKENS, Role.SENSE_VOICE_MODEL);
                case PARA_FORMER -> Set.of(Role.TOKENS, Role.PARA_FORMER_MODEL);
                case WHISPER -> Set.of(Role.TOKENS, Role.WHISPER_ENCODER, Role.WHISPER_DECODER);
            };
            if (!roles.equals(required) || total > MAX_TOTAL_BYTES) throw new IllegalArgumentException();
            return new Model(name, family, files);
        } catch (RuntimeException | IOException failure) { throw new NativeSpeechToText.Failure("model_integrity", failure); }
    }
    private static void checkJson(com.google.gson.stream.JsonReader reader, int depth) throws IOException {
        if (depth > 4) throw new IllegalArgumentException();
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject(); Set<String> names = new HashSet<>();
                while (reader.hasNext()) {
                    if (!names.add(reader.nextName())) throw new IllegalArgumentException();
                    checkJson(reader, depth + 1);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray(); int count = 0;
                while (reader.hasNext()) { if (++count > 16) throw new IllegalArgumentException(); checkJson(reader, depth + 1); }
                reader.endArray();
            }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new IllegalArgumentException();
        }
    }
    static String json(Model model) {
        JsonObject object = new JsonObject();
        object.addProperty("name", model.name());
        object.addProperty("family", model.family().name());
        JsonArray files = new JsonArray();
        for (ModelFile file : model.files()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("role", file.role().name()); entry.addProperty("path", file.path());
            entry.addProperty("bytes", file.bytes()); entry.addProperty("sha256", file.sha256());
            files.add(entry);
        }
        object.add("files", files);
        return object.toString();
    }
    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
        return value.getAsString();
    }
    private static void exactKeys(JsonObject object, Set<String> keys) {
        if (!object.keySet().equals(keys)) throw new IllegalArgumentException();
    }
    static void verifyFile(Path directory, String name, long bytes, String sha256, VoiceCancellation cancellation) throws IOException {
        cancellation.check();
        Path file = directory.resolve(name);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) != bytes) {
            throw new NativeSpeechToText.Failure("model_integrity");
        }
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(file); AutoCloseable hook = cancellation.onCancel(() -> close(input))) {
            byte[] buffer = new byte[64 * 1024];
            long count = 0;
            int length;
            while ((length = input.read(buffer)) != -1) {
                cancellation.check(); count += length;
                if (count > bytes) throw new NativeSpeechToText.Failure("model_integrity");
                digest.update(buffer, 0, length);
            }
            cancellation.check();
            if (count != bytes || !HexFormat.of().formatHex(digest.digest()).equals(sha256)) {
                throw new NativeSpeechToText.Failure("model_integrity");
            }
        } catch (NativeSpeechToText.Failure failure) { throw failure;
        } catch (Exception failure) {
            cancellation.check();
            throw new NativeSpeechToText.Failure("model_integrity", failure);
        }
    }
    static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void close(InputStream stream) { try { stream.close(); } catch (IOException ignored) {} }
}
