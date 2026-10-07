package dev.openallay.client.voice;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
    @dev.openallay.value.ValueType(ModelFile.ValueSchemaProvider.class)
public static final class ModelFile {
    private final Role role;
    private final String path;
    private final long bytes;
    private final String sha256;
    public ModelFile(Role role, String path, long bytes, String sha256) {
        this.role = role;
        this.path = path;
        this.bytes = bytes;
        this.sha256 = sha256;
    }
    public Role role() { return role; }
    public String path() { return path; }
    public long bytes() { return bytes; }
    public String sha256() { return sha256; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelFile)) return false;
        ModelFile that = (ModelFile) other;
        return java.util.Objects.equals(role, that.role) && java.util.Objects.equals(path, that.path) && bytes == that.bytes && java.util.Objects.equals(sha256, that.sha256);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(role);
        hash = 31 * hash + java.util.Objects.hashCode(path);
        hash = 31 * hash + Long.hashCode(bytes);
        hash = 31 * hash + java.util.Objects.hashCode(sha256);
        return hash;
    }
    @Override public String toString() { return "ModelFile[role=" + role + ", path=" + path + ", bytes=" + bytes + ", sha256=" + sha256 + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelFile> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelFile.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelFile>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelFile.class, "role", ModelFile::role), new dev.openallay.value.ValueSchema.Component<>(ModelFile.class, "path", ModelFile::path), new dev.openallay.value.ValueSchema.Component<>(ModelFile.class, "bytes", ModelFile::bytes), new dev.openallay.value.ValueSchema.Component<>(ModelFile.class, "sha256", ModelFile::sha256)), arguments -> new ModelFile((Role) arguments[0], (String) arguments[1], (Long) arguments[2], (String) arguments[3]));
        }
    }
}
    @dev.openallay.value.ValueType(Model.ValueSchemaProvider.class)
public static final class Model {
    private final String name;
    private final ModelFamily family;
    private final List<ModelFile> files;
    public Model(String name, ModelFamily family, List<ModelFile> files) {
 files = dev.openallay.util.Java8Collections.listCopyOf(files);
        this.name = name;
        this.family = family;
        this.files = files;
    }
    public String name() { return name; }
    public ModelFamily family() { return family; }
    public List<ModelFile> files() { return files; }
public ModelFile file(Role role) {
            return files.stream().filter(file -> file.role() == role).findFirst().orElseThrow();
        }
public Path file(Path directory, Role role) { return directory.resolve(file(role).path()); }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Model)) return false;
        Model that = (Model) other;
        return java.util.Objects.equals(name, that.name) && java.util.Objects.equals(family, that.family) && java.util.Objects.equals(files, that.files);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(family);
        hash = 31 * hash + java.util.Objects.hashCode(files);
        return hash;
    }
    @Override public String toString() { return "Model[name=" + name + ", family=" + family + ", files=" + files + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Model> schema() {
            return new dev.openallay.value.ValueSchema<>(Model.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Model>>asList(new dev.openallay.value.ValueSchema.Component<>(Model.class, "name", Model::name), new dev.openallay.value.ValueSchema.Component<>(Model.class, "family", Model::family), new dev.openallay.value.ValueSchema.Component<>(Model.class, "files", Model::files)), arguments -> new Model((String) arguments[0], (ModelFamily) arguments[1], (List) arguments[2]));
        }
    }
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
            try (com.google.gson.stream.JsonReader reader = dev.openallay.json.JsonReaders.strict(new java.io.StringReader(json))) {

                checkJson(reader, 0);
                if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT) throw new IllegalArgumentException();
            }
            JsonElement element = dev.openallay.json.JsonTrees.parse(json);
            if (!element.isJsonObject()) throw new IllegalArgumentException();
            JsonObject root = element.getAsJsonObject();
            exactKeys(root, dev.openallay.util.Java8Collections.setOf("name", "family", "files"));
            String name = string(root, "name");
            if (dev.openallay.util.Java8Strings.isBlank(name) || name.length() > 100 || name.chars().anyMatch(c -> c < 32)) throw new IllegalArgumentException();
            ModelFamily family = ModelFamily.valueOf(string(root, "family"));
            JsonArray entries = root.getAsJsonArray("files");
            if (entries.size() < 2 || entries.size() > 3) throw new IllegalArgumentException();
            List<ModelFile> files = new ArrayList<>();
            Set<Role> roles = new HashSet<>();
            Set<String> paths = new HashSet<>();
            long total = 0;
            for (JsonElement entry : entries) {
                JsonObject file = entry.getAsJsonObject();
                exactKeys(file, dev.openallay.util.Java8Collections.setOf("role", "path", "bytes", "sha256"));
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
            java.util.Set<dev.openallay.client.voice.NativeModelFiles.Role> $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((family)) {
case SENSE_VOICE:
{
$oaSwitch0_exit_result = dev.openallay.util.Java8Collections.setOf(Role.TOKENS, Role.SENSE_VOICE_MODEL); break $oaSwitch0_exit;
}
case PARA_FORMER:
{
$oaSwitch0_exit_result = dev.openallay.util.Java8Collections.setOf(Role.TOKENS, Role.PARA_FORMER_MODEL); break $oaSwitch0_exit;
}
case WHISPER:
{
$oaSwitch0_exit_result = dev.openallay.util.Java8Collections.setOf(Role.TOKENS, Role.WHISPER_ENCODER, Role.WHISPER_DECODER); break $oaSwitch0_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
Set<Role> required = $oaSwitch0_exit_result;
            if (!roles.equals(required) || total > MAX_TOTAL_BYTES) throw new IllegalArgumentException();
            return new Model(name, family, files);
        } catch (RuntimeException | IOException failure) { throw new NativeSpeechToText.Failure("model_integrity", failure); }
    }
    private static void checkJson(com.google.gson.stream.JsonReader reader, int depth) throws IOException {
        if (depth > 4) throw new IllegalArgumentException();
        switch ((reader.peek())) {
case BEGIN_OBJECT:
{
{
                reader.beginObject(); Set<String> names = new HashSet<>();
                while (reader.hasNext()) {
                    if (!names.add(reader.nextName())) throw new IllegalArgumentException();
                    checkJson(reader, depth + 1);
                }
                reader.endObject();
            }
break;
}
case BEGIN_ARRAY:
{
{
                reader.beginArray(); int count = 0;
                while (reader.hasNext()) { if (++count > 16) throw new IllegalArgumentException(); checkJson(reader, depth + 1); }
                reader.endArray();
            }
break;
}
case STRING:
case NUMBER:
{
reader.nextString();
break;
}
case BOOLEAN:
{
reader.nextBoolean();
break;
}
case NULL:
{
reader.nextNull();
break;
}
default:
{
throw new IllegalArgumentException();
}
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
        if (!dev.openallay.json.JsonTrees.keys(object).equals(keys)) throw new IllegalArgumentException();
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
            if (count != bytes || !dev.openallay.util.Java8Hex.formatHex(digest.digest()).equals(sha256)) {
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
