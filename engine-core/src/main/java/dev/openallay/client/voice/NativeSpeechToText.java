package dev.openallay.client.voice;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Official sherpa-onnx JNI ASR in a killable process, never in Minecraft's VM. */
public final class NativeSpeechToText implements SpeechToText {
    private static final Semaphore NATIVE_SLOT = new Semaphore(1, true);
    private static final long TIMEOUT_SECONDS = 180;
    private static final int MAX_TEXT_BYTES = 131_072;
    private final Path modelDirectory;
    private final Path runtimeRoot;
    private final ModelReader modelReader;
    private final RuntimeValidator runtimeValidator;
    private final WorkerExecutor worker;

    public static final class Failure extends IOException {
        private final String code;
        public Failure(String code) { super(code); this.code = code; }
        public Failure(String code, Throwable cause) { super(code, cause); this.code = code; }
        public String code() { return code; }
    }
    @FunctionalInterface interface ModelReader {
        NativeModelFiles.Model read(Path directory, VoiceCancellation cancellation) throws IOException;
    }
    @FunctionalInterface interface RuntimeValidator { void validate(Path root, VoiceCancellation cancellation) throws IOException; }
    @FunctionalInterface interface WorkerExecutor { String recognize(Call call, VoiceCancellation cancellation) throws Exception; }
    record Call(Path modelDirectory, Path runtimeRoot, NativeModelFiles.Model model, Request request) {}

    /** Convenience layout matches NativeModelInstaller: model and runtime are siblings. */
    public NativeSpeechToText(Path modelDirectory) {
        this(modelDirectory, modelDirectory.toAbsolutePath().normalize().getParent().resolve("runtime"));
    }
    public NativeSpeechToText(Path modelDirectory, Path runtimeRoot) {
        this(modelDirectory, runtimeRoot, NativeModelFiles::readValidated, NativeRuntimeCatalog::validate, NativeSpeechToText::runProcess);
    }
    NativeSpeechToText(Path modelDirectory, Path runtimeRoot, ModelReader reader,
            RuntimeValidator validator, WorkerExecutor worker) {
        this.modelDirectory = Objects.requireNonNull(modelDirectory).toAbsolutePath().normalize();
        this.runtimeRoot = Objects.requireNonNull(runtimeRoot).toAbsolutePath().normalize();
        this.modelReader = Objects.requireNonNull(reader);
        this.runtimeValidator = Objects.requireNonNull(validator);
        this.worker = Objects.requireNonNull(worker);
    }
    @Override public Result transcribe(Request request, VoiceCancellation cancellation) throws Exception {
        Objects.requireNonNull(request); Objects.requireNonNull(cancellation);
        cancellation.check();
        if (request.cpuThreads() < 1 || request.cpuThreads() > 8) throw new IllegalArgumentException("cpuThreads");
        // Reject unsupported hosts before hashing large models or starting any native process.
        NativeRuntimeCatalog.platform();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        boolean acquired = false;
        try {
            while (!(acquired = NATIVE_SLOT.tryAcquire(100, TimeUnit.MILLISECONDS))) {
                cancellation.check();
                if (System.nanoTime() >= deadline) throw new Failure("native_timeout");
            }
            cancellation.check();
            NativeModelFiles.Model model = modelReader.read(modelDirectory, cancellation);
            runtimeValidator.validate(runtimeRoot, cancellation);
            cancellation.check();
            String language = language(model.family(), request.language());
            Request nativeRequest = new Request(request.clip(), language, request.cpuThreads());
            String text = worker.recognize(new Call(modelDirectory, runtimeRoot, model, nativeRequest), cancellation);
            cancellation.check();
            if (text == null || text.isBlank()) throw new Failure("no_speech");
            try { return new Result(text, "native:" + model.name(), new Usage(request.clip().durationSeconds(), null, null)); }
            catch (IllegalArgumentException invalid) { throw new Failure("native_failed", invalid); }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); cancellation.check(); throw interrupted;
        } finally { if (acquired) NATIVE_SLOT.release(); }
    }
    static String language(NativeModelFiles.ModelFamily family, String language) throws Failure {
        if (!language.matches("auto|[a-z]{2,3}(-[A-Z]{2})?")) throw new Failure("unsupported_language");
        String base = language.equals("auto") ? "" : language.split("-", 2)[0];
        if (family == NativeModelFiles.ModelFamily.SENSE_VOICE
                && !List.of("", "zh", "en", "ja", "ko", "yue").contains(base)) throw new Failure("unsupported_language");
        if (family == NativeModelFiles.ModelFamily.PARA_FORMER
                && !List.of("", "zh", "en").contains(base)) throw new Failure("unsupported_language");
        return base;
    }
    @FunctionalInterface interface ProcessLauncher { Process start(Path job, List<String> command) throws IOException; }
    private static String runProcess(Call call, VoiceCancellation cancellation) throws Exception {
        return runProcess(call, cancellation, (job, command) -> new ProcessBuilder(command).directory(job.toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start());
    }
    static String runProcess(Call call, VoiceCancellation cancellation, ProcessLauncher launcher) throws Exception {
        Path job = Files.createTempDirectory("openallay-asr-");
        Process process = null;
        try {
            Path audio = job.resolve("audio.pcm");
            Path transcript = job.resolve("transcript.txt");
            // Restrict the directory before writing PCM. Windows inherits the user's temp ACL.
            var permissions = Files.getFileAttributeView(job, java.nio.file.attribute.PosixFileAttributeView.class);
            if (permissions != null) permissions.setPermissions(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
            Files.write(audio, call.request().clip().pcm(), StandardOpenOption.CREATE_NEW);
            var audioPermissions = Files.getFileAttributeView(audio, java.nio.file.attribute.PosixFileAttributeView.class);
            if (audioPermissions != null) audioPermissions.setPermissions(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            cancellation.check();
            List<String> command = command(call, audio, transcript);
            process = launcher.start(job, command);
            Process owned = process;
            try (AutoCloseable hook = cancellation.onCancel(owned::destroyForcibly)) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
                while (!owned.waitFor(100, TimeUnit.MILLISECONDS)) {
                    cancellation.check();
                    if (System.nanoTime() >= deadline) throw new Failure("native_timeout");
                }
                cancellation.check();
                if (owned.exitValue() != 0 || !Files.isRegularFile(transcript)) throw new Failure("native_failed");
                if (Files.size(transcript) > MAX_TEXT_BYTES) throw new Failure("native_failed");
                return Files.readString(transcript, StandardCharsets.UTF_8);
            }
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                // Native objects are never released while decode runs. The OS owns cleanup.
                boolean interrupted = Thread.interrupted();
                try { process.waitFor(5, TimeUnit.SECONDS); }
                catch (InterruptedException ignored) { interrupted = true; }
                if (interrupted) Thread.currentThread().interrupt();
            }
            // Delete only this operation's files, never imported models or earlier installations.
            if (process == null || !process.isAlive()) deleteJob(job);
        }
    }
    static List<String> command(Call call, Path audio, Path transcript) throws Exception {
        String javaName = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", javaName);
        if (!Files.isRegularFile(java)) throw new Failure("native_unavailable");
        URL codeSource = Worker.class.getProtectionDomain().getCodeSource().getLocation();
        if (!codeSource.getProtocol().equals("file")) throw new Failure("native_unavailable");
        Path classes = Path.of(codeSource.toURI());
        Path runtime = NativeRuntimeCatalog.directory(call.runtimeRoot());
        List<NativeRuntimeCatalog.Artifact> artifacts = NativeRuntimeCatalog.artifacts();
        NativeModelFiles.Model model = call.model();
        NativeModelFiles.Role primary = switch (model.family()) {
            case SENSE_VOICE -> NativeModelFiles.Role.SENSE_VOICE_MODEL;
            case PARA_FORMER -> NativeModelFiles.Role.PARA_FORMER_MODEL;
            case WHISPER -> NativeModelFiles.Role.WHISPER_ENCODER;
        };
        String secondary = model.family() == NativeModelFiles.ModelFamily.WHISPER
                ? model.file(call.modelDirectory(), NativeModelFiles.Role.WHISPER_DECODER).toString() : "";
        // Fixed executable and argv; model metadata can never add VM flags or a classpath entry.
        return List.of(java.toString(), "-Xms32m", "-Xmx256m", "--enable-native-access=ALL-UNNAMED",
                "-Djava.io.tmpdir=" + audio.getParent(), "-cp", classes.toString(), Worker.class.getName(),
                model.family().name(), model.file(call.modelDirectory(), primary).toString(), secondary,
                model.file(call.modelDirectory(), NativeModelFiles.Role.TOKENS).toString(),
                runtime.resolve(artifacts.get(0).name()).toString(), runtime.resolve(artifacts.get(1).name()).toString(),
                NativeRuntimeCatalog.platform(), audio.toString(), transcript.toString(),
                call.request().language(), Integer.toString(call.request().cpuThreads()));
    }
    private static void deleteJob(Path job) {
        try (var entries = Files.walk(job)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                try { Files.deleteIfExists(entry); } catch (IOException ignored) { /* Best effort after exit. */ }
            }
        } catch (IOException ignored) { /* Only a temporary voice operation directory. */ }
    }

    /** JDK-only entry point. Delegates recognition to the official, isolated sherpa API. */
    public static final class Worker {
        private static final String PACKAGE = "com.k2fsa.sherpa.onnx.";
        private Worker() {}
        public static void main(String[] args) {
            try {
                if (args.length != 11) throw new IllegalArgumentException("arguments");
                int threads = Integer.parseInt(args[10]);
                if (threads < 1 || threads > 8) throw new IllegalArgumentException("threads");
                Path audio = Path.of(args[7]);
                if (Files.size(audio) == 0 || Files.size(audio) > 1_920_000 || Files.size(audio) % 2 != 0) {
                    throw new IllegalArgumentException("audio");
                }
                byte[] pcm = Files.readAllBytes(audio);
                ByteBuffer input = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
                float[] samples = new float[pcm.length / 2];
                for (int i = 0; i < samples.length; i++) samples[i] = input.getShort() / 32768.0f;
                try (IsolatedLoader loader = new IsolatedLoader(new URL[] {
                        Path.of(args[4]).toUri().toURL(), Path.of(args[5]).toUri().toURL() })) {
                    invoke(loader.loadClass(PACKAGE + "LibraryLoader"), "setAutoLoadEnabled", new Class<?>[] {boolean.class}, false);
                    Path nativeDirectory = audio.getParent().resolve("native");
                    Files.createDirectory(nativeDirectory);
                    String ort = System.mapLibraryName("onnxruntime");
                    String jni = System.mapLibraryName("sherpa-onnx-jni");
                    extract(loader, "sherpa-onnx/native/" + args[6] + "/" + ort, nativeDirectory.resolve(ort));
                    extract(loader, "sherpa-onnx/native/" + args[6] + "/" + jni, nativeDirectory.resolve(jni));
                    // LibraryUtils is defined by the child loader: System.load runs in that same loader.
                    System.setProperty("sherpa_onnx.native.path", nativeDirectory.toString());
                    Class<?> utils = loader.loadClass(PACKAGE + "LibraryUtils");
                    invoke(utils, "load", new Class<?>[0]);
                    String text = recognize(loader, args, threads, samples);
                    if (text == null || text.length() > 32_768) throw new IllegalArgumentException("text");
                    Files.writeString(Path.of(args[8]), text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                }
            } catch (Throwable failure) {
                // No native diagnostics, audio, filesystem paths or model data enter the chat.
                System.exit(1);
            }
        }
        private static String recognize(ClassLoader loader, String[] args, int threads, float[] samples) throws Exception {
            Object family;
            String setter;
            switch (args[0]) {
                case "SENSE_VOICE" -> {
                    Object builder = builder(loader, "OfflineSenseVoiceModelConfig");
                    invoke(builder, "setModel", new Class<?>[] {String.class}, args[1]);
                    invoke(builder, "setLanguage", new Class<?>[] {String.class}, args[9]);
                    invoke(builder, "setInverseTextNormalization", new Class<?>[] {boolean.class}, true);
                    family = invoke(builder, "build", new Class<?>[0]); setter = "setSenseVoice";
                }
                case "PARA_FORMER" -> {
                    Object builder = builder(loader, "OfflineParaformerModelConfig");
                    invoke(builder, "setModel", new Class<?>[] {String.class}, args[1]);
                    family = invoke(builder, "build", new Class<?>[0]); setter = "setParaformer";
                }
                case "WHISPER" -> {
                    Object builder = builder(loader, "OfflineWhisperModelConfig");
                    invoke(builder, "setEncoder", new Class<?>[] {String.class}, args[1]);
                    invoke(builder, "setDecoder", new Class<?>[] {String.class}, args[2]);
                    invoke(builder, "setLanguage", new Class<?>[] {String.class}, args[9]);
                    invoke(builder, "setTask", new Class<?>[] {String.class}, "transcribe");
                    family = invoke(builder, "build", new Class<?>[0]); setter = "setWhisper";
                }
                default -> throw new IllegalArgumentException("family");
            }
            Object modelBuilder = builder(loader, "OfflineModelConfig");
            invoke(modelBuilder, setter, new Class<?>[] {family.getClass()}, family);
            invoke(modelBuilder, "setTokens", new Class<?>[] {String.class}, args[3]);
            invoke(modelBuilder, "setNumThreads", new Class<?>[] {int.class}, threads);
            invoke(modelBuilder, "setProvider", new Class<?>[] {String.class}, "cpu");
            invoke(modelBuilder, "setDebug", new Class<?>[] {boolean.class}, false);
            Object model = invoke(modelBuilder, "build", new Class<?>[0]);
            Object configBuilder = builder(loader, "OfflineRecognizerConfig");
            invoke(configBuilder, "setOfflineModelConfig", new Class<?>[] {model.getClass()}, model);
            Object config = invoke(configBuilder, "build", new Class<?>[0]);
            Object recognizer = null;
            Object stream = null;
            try {
                recognizer = loader.loadClass(PACKAGE + "OfflineRecognizer").getConstructor(config.getClass()).newInstance(config);
                stream = invoke(recognizer, "createStream", new Class<?>[0]);
                invoke(stream, "acceptWaveform", new Class<?>[] {float[].class, int.class}, samples, 16000);
                invoke(recognizer, "decode", new Class<?>[] {stream.getClass()}, stream);
                Object result = invoke(recognizer, "getResult", new Class<?>[] {stream.getClass()}, stream);
                return (String) invoke(result, "getText", new Class<?>[0]);
            } finally {
                // This thread releases only after decode returns. Cancellation terminates the process instead.
                if (stream != null) invoke(stream, "release", new Class<?>[0]);
                if (recognizer != null) invoke(recognizer, "release", new Class<?>[0]);
            }
        }
        private static Object builder(ClassLoader loader, String name) throws Exception {
            return invoke(loader.loadClass(PACKAGE + name), "builder", new Class<?>[0]);
        }
        private static Object invoke(Object target, String name, Class<?>[] types, Object... arguments) throws Exception {
            Class<?> type = target instanceof Class<?> value ? value : target.getClass();
            Method method = type.getMethod(name, types);
            try { return method.invoke(target instanceof Class<?> ? null : target, arguments); }
            catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof Exception exception) throw exception;
                if (cause instanceof Error error) throw error;
                throw failure;
            }
        }
        private static void extract(ClassLoader loader, String resource, Path target) throws IOException {
            try (InputStream input = loader.getResourceAsStream(resource)) {
                if (input == null) throw new IOException("Missing official native resource");
                try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                    byte[] buffer = new byte[64 * 1024]; long bytes = 0; int count;
                    while ((count = input.read(buffer)) != -1) {
                        bytes += count;
                        if (bytes > 512_000_000) throw new IOException("Oversized official native resource");
                        output.write(buffer, 0, count);
                    }
                }
            }
        }
        private static final class IsolatedLoader extends URLClassLoader {
            IsolatedLoader(URL[] jars) { super(jars, ClassLoader.getPlatformClassLoader()); }
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) loaded = name.startsWith(PACKAGE) ? findClass(name) : super.loadClass(name, false);
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
        }
    }
}
