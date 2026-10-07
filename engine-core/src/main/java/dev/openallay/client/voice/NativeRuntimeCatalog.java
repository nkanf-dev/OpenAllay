package dev.openallay.client.voice;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Product-owned executable pins. A player model manifest cannot select JNI code. */
public final class NativeRuntimeCatalog {
    public static final String RELEASE = "1.13.8";
    private static final String RELEASE_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v" + RELEASE + "/";
    @dev.openallay.value.ValueType(Artifact.ValueSchemaProvider.class)
public static final class Artifact {
    private final String name;
    private final long bytes;
    private final String sha256;
    public Artifact(String name, long bytes, String sha256) {
        this.name = name;
        this.bytes = bytes;
        this.sha256 = sha256;
    }
    public String name() { return name; }
    public long bytes() { return bytes; }
    public String sha256() { return sha256; }
public URI uri() { return URI.create(RELEASE_URL + name); }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Artifact)) return false;
        Artifact that = (Artifact) other;
        return java.util.Objects.equals(name, that.name) && bytes == that.bytes && java.util.Objects.equals(sha256, that.sha256);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + Long.hashCode(bytes);
        hash = 31 * hash + java.util.Objects.hashCode(sha256);
        return hash;
    }
    @Override public String toString() { return "Artifact[name=" + name + ", bytes=" + bytes + ", sha256=" + sha256 + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Artifact> schema() {
            return new dev.openallay.value.ValueSchema<>(Artifact.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Artifact>>asList(new dev.openallay.value.ValueSchema.Component<>(Artifact.class, "name", Artifact::name), new dev.openallay.value.ValueSchema.Component<>(Artifact.class, "bytes", Artifact::bytes), new dev.openallay.value.ValueSchema.Component<>(Artifact.class, "sha256", Artifact::sha256)), arguments -> new Artifact((String) arguments[0], (Long) arguments[1], (String) arguments[2]));
        }
    }
}
    private static final Artifact JVM = new Artifact("sherpa-onnx-jvm-1.13.8.jar", 187490,
            "77b7b047fade4eadada96b568eb92615049aaf1dc317c7244e46c1ea38b9a63b");
    private static final Map<String, Artifact> NATIVES = Map.of(
            "linux-aarch64", new Artifact("sherpa-onnx-native-lib-linux-aarch64-1.13.8.jar", 13224859,
                    "5123d2e48ae1a7ce82ba89ce153906c651bf418a63db2f7dff82265e6d49c104"),
            "linux-x64", new Artifact("sherpa-onnx-native-lib-linux-x64-1.13.8.jar", 10515871,
                    "30c93b59381113f9c20aedbbf9fc1ad399158f6bc03dddc0f8934a6e28e069ba"),
            "osx-aarch64", new Artifact("sherpa-onnx-native-lib-osx-aarch64-1.13.8.jar", 9460832,
                    "42e272180c8836127f024f3335d7afcdfb30fe0164b78330d5d449034e34ce34"),
            "osx-x64", new Artifact("sherpa-onnx-native-lib-osx-x64-1.13.8.jar", 10782466,
                    "9190c28951d85efdbd376bae6b6dff12993311ad605945289e8459bf9c886a96"),
            "win-arm64", new Artifact("sherpa-onnx-native-lib-win-arm64-1.13.8.jar", 7889954,
                    "986660bef51f0ca4f5635b763c172c64bb056eabebcd319f42185d7b23337fb8"),
            "win-x64", new Artifact("sherpa-onnx-native-lib-win-x64-1.13.8.jar", 8277046,
                    "33fbdbd5410e9ba9bdda94aa164ec8f7825bb49246420d8ce9bdd88219d97039"));

    private NativeRuntimeCatalog() {}
    public static String platform() throws NativeSpeechToText.Failure {
        return platform(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }
    static String platform(String osName, String architecture) throws NativeSpeechToText.Failure {
        String os = osName.toLowerCase(Locale.ROOT);
        String arch = architecture.toLowerCase(Locale.ROOT);
        String system;
        if (os.contains("mac") || os.contains("darwin")) system = "osx";
        else if (os.contains("win")) system = "win";
        else if (os.contains("linux")) system = "linux";
        else throw new NativeSpeechToText.Failure("unsupported_platform");
        String cpu;
        if (arch.equals("amd64") || arch.equals("x86_64")) cpu = "x64";
        else if (arch.equals("aarch64") || arch.equals("arm64")) cpu = system.equals("win") ? "arm64" : "aarch64";
        else throw new NativeSpeechToText.Failure("unsupported_platform");
        return system + "-" + cpu;
    }
    public static List<Artifact> artifacts() throws NativeSpeechToText.Failure {
        return List.of(JVM, NATIVES.get(platform()));
    }
    public static Path directory(Path runtimeRoot) throws NativeSpeechToText.Failure {
        return runtimeRoot.toAbsolutePath().normalize().resolve("sherpa-" + RELEASE).resolve(platform());
    }
    public static void validate(Path runtimeRoot) throws IOException { validate(runtimeRoot, new VoiceCancellation()); }
    public static void validate(Path runtimeRoot, VoiceCancellation cancellation) throws IOException {
        cancellation.check();
        Path directory = directory(runtimeRoot);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new NativeSpeechToText.Failure("native_unavailable");
        }
        for (Artifact artifact : artifacts()) {
            NativeModelFiles.verifyFile(directory, artifact.name(), artifact.bytes(), artifact.sha256(), cancellation);
        }
    }
}
