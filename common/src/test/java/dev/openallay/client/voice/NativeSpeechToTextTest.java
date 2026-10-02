package dev.openallay.client.voice;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NativeSpeechToTextTest {
    @TempDir Path temporary;

    @Test void validatesExactModelShapeAndDetectsCorruption() throws Exception {
        NativeModelFiles.Model model = tinyModel(); Path directory = writeModel(model);
        NativeModelFiles.validate(directory); assertTrue(NativeModelFiles.modelReady(directory));
        Files.write(directory.resolve("model.onnx"), new byte[] {4, 5, 7});
        var failure = assertThrows(NativeSpeechToText.Failure.class, () -> NativeModelFiles.validate(directory));
        assertEquals("model_integrity", failure.code()); assertFalse(NativeModelFiles.modelReady(directory));
        assertFalse(NativeModelFiles.modelReady(temporary.resolve("absent")));
    }
    @Test void importedModelCannotSelectExecutableOrUseTraversal() throws Exception {
        String json = NativeModelFiles.json(tinyModel());
        for (String bad : List.of(
                json.replace("\"model.onnx\"", "\"../model.onnx\""),
                json.replace("\"model.onnx\"", "\"C:evil.jar\""),
                json.replace("\"SENSE_VOICE_MODEL\"", "\"JVM\""),
                json.replace("\"family\":", "\"platform\":\"osx-aarch64\",\"family\":"),
                json.replace("\"family\":", "\"runtime\":\"arbitrary.jar\",\"family\":"),
                json.replace("\"name\":", "\"name\":\"duplicate\",\"name\":"),
                json.replace("\"bytes\":3", "\"bytes\":3.0"),
                json.replace("\"bytes\":3", "\"bytes\":1500000001"),
                json + " trailing")) {
            assertEquals("model_integrity", assertThrows(NativeSpeechToText.Failure.class,
                    () -> NativeModelFiles.parse(bad), bad).code());
        }
    }
    @Test void typedSupportedFamiliesHaveExactRoles() throws Exception {
        var paraformer = new NativeModelFiles.Model("Paraformer", NativeModelFiles.ModelFamily.PARA_FORMER,
                List.of(file(NativeModelFiles.Role.PARA_FORMER_MODEL, "model.onnx", new byte[] {1}),
                        file(NativeModelFiles.Role.TOKENS, "tokens.txt", new byte[] {2})));
        assertEquals(NativeModelFiles.ModelFamily.PARA_FORMER, NativeModelFiles.parse(NativeModelFiles.json(paraformer)).family());
        var whisper = new NativeModelFiles.Model("Whisper", NativeModelFiles.ModelFamily.WHISPER,
                List.of(file(NativeModelFiles.Role.WHISPER_ENCODER, "encoder.onnx", new byte[] {1}),
                        file(NativeModelFiles.Role.WHISPER_DECODER, "decoder.onnx", new byte[] {2}),
                        file(NativeModelFiles.Role.TOKENS, "tokens.txt", new byte[] {3})));
        assertEquals(NativeModelFiles.ModelFamily.WHISPER, NativeModelFiles.parse(NativeModelFiles.json(whisper)).family());
        assertThrows(NativeSpeechToText.Failure.class, () -> NativeModelFiles.parse(NativeModelFiles.json(
                new NativeModelFiles.Model("Wrong", NativeModelFiles.ModelFamily.WHISPER, tinyModel().files()))));
    }
    @Test void honorsLanguageCpuAndReportsRealAudioUsageWithoutInventingTokens() throws Exception {
        AtomicReference<NativeSpeechToText.Call> observed = new AtomicReference<>();
        NativeSpeechToText speech = fake((call, cancellation) -> { observed.set(call); return "  打开背包  "; });
        var result = speech.transcribe(request("zh-CN", 2), new VoiceCancellation());
        assertEquals("打开背包", result.text()); assertEquals("native:tiny", result.source());
        assertEquals("zh", observed.get().request().language()); assertEquals(2, observed.get().request().cpuThreads());
        assertEquals(0.01, result.usage().audioSeconds()); assertNull(result.usage().inputTokens()); assertNull(result.usage().outputTokens());
        assertEquals("", NativeSpeechToText.language(NativeModelFiles.ModelFamily.SENSE_VOICE, "auto"));
        assertThrows(NativeSpeechToText.Failure.class,
                () -> speech.transcribe(request("fr", 2), new VoiceCancellation()));
        assertThrows(IllegalArgumentException.class, () -> request("auto", 0));
        assertThrows(IllegalArgumentException.class, () -> request("auto", 9));
    }
    @Test void rejectsEmptyRecognitionAndChecksCancellationBeforeLoading() throws Exception {
        assertEquals("no_speech", assertThrows(NativeSpeechToText.Failure.class,
                () -> fake((call, cancellation) -> " ").transcribe(request("auto", 1), new VoiceCancellation())).code());
        AtomicBoolean loaded = new AtomicBoolean(); VoiceCancellation cancellation = new VoiceCancellation(); cancellation.cancel();
        NativeSpeechToText speech = new NativeSpeechToText(temporary, temporary,
                (path, signal) -> { loaded.set(true); return tinyModel(); }, (path, signal) -> {}, (call, signal) -> "text");
        assertThrows(CancellationException.class, () -> speech.transcribe(request("auto", 1), cancellation)); assertFalse(loaded.get());
    }
    @Test void processCancellationKillsWorkerBeforeDeletingPrivateAudio() throws Exception {
        CountDownLatch started = new CountDownLatch(1); VoiceCancellation cancellation = new VoiceCancellation();
        FakeProcess process = new FakeProcess(); AtomicReference<Path> job = new AtomicReference<>();
        var call = new NativeSpeechToText.Call(temporary, temporary, tinyModel(), request("", 2));
        try (var executor = Executors.newSingleThreadExecutor()) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try { return NativeSpeechToText.runProcess(call, cancellation, (directory, command) -> {
                    job.set(directory); assertTrue(Files.exists(directory.resolve("audio.pcm")));
                    assertEquals("-Xmx256m", command.get(2));
                    assertEquals("--enable-native-access=ALL-UNNAMED", command.get(3));
                    assertTrue(command.contains(NativeSpeechToText.Worker.class.getName()));
                    started.countDown(); return process;
                }); } catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
            }, executor);
            assertTrue(started.await(3, TimeUnit.SECONDS)); cancellation.cancel();
            ExecutionException failure = assertThrows(ExecutionException.class, () -> future.get(3, TimeUnit.SECONDS));
            assertInstanceOf(CancellationException.class, failure.getCause());
            assertTrue(process.killed.get()); assertFalse(Files.exists(job.get()));
        }
    }
    @Test void nativeFailureDoesNotLeakDiagnosticsAndRemovesAudio() throws Exception {
        AtomicReference<Path> job = new AtomicReference<>();
        var call = new NativeSpeechToText.Call(temporary, temporary, tinyModel(), request("", 2));
        var failure = assertThrows(NativeSpeechToText.Failure.class, () -> NativeSpeechToText.runProcess(call,
                new VoiceCancellation(), (directory, command) -> { job.set(directory); FakeProcess process = new FakeProcess(); process.finish(1); return process; }));
        assertEquals("native_failed", failure.code()); assertEquals("native_failed", failure.getMessage()); assertFalse(Files.exists(job.get()));
    }
    @Test void runtimeCatalogUsesPlatformSpecificOfficialPins() throws Exception {
        assertEquals("osx-aarch64", NativeRuntimeCatalog.platform("Mac OS X", "aarch64"));
        assertEquals("osx-x64", NativeRuntimeCatalog.platform("Darwin", "x86_64"));
        assertEquals("win-arm64", NativeRuntimeCatalog.platform("Windows 11", "arm64"));
        assertEquals("linux-x64", NativeRuntimeCatalog.platform("Linux", "amd64"));
        assertEquals("unsupported_platform", assertThrows(NativeSpeechToText.Failure.class,
                () -> NativeRuntimeCatalog.platform("Linux", "x86")).code());
        assertEquals(2, NativeRuntimeCatalog.artifacts().size());
        assertTrue(NativeRuntimeCatalog.artifacts().stream().allMatch(artifact -> artifact.sha256().matches("[a-f0-9]{64}")));
    }
    @Test void explicitInstallerIsImmutableAndNeverDownloadsAtConstruction() throws Exception {
        AtomicBoolean downloaded = new AtomicBoolean(); List<Long> progress = new java.util.ArrayList<>();
        NativeModelInstaller installer = tinyInstaller(tinyModel(), (download, file, cancellation, callback) -> {
            downloaded.set(true); byte[] bytes = download.name().equals("model.onnx") ? new byte[] {4, 5, 6} : new byte[] {7};
            Files.write(file, bytes); callback.update(bytes.length, download.bytes());
        });
        assertFalse(downloaded.get()); Path first = installer.install(new VoiceCancellation(), (bytes, total) -> progress.add(bytes));
        assertTrue(downloaded.get()); NativeModelFiles.validate(first); assertEquals("tiny", installer.modelName());
        assertTrue(Files.isRegularFile(first.resolve("MODEL_LICENSE"))); downloaded.set(false);
        assertEquals(first, installer.install(new VoiceCancellation(), (bytes, total) -> {})); assertFalse(downloaded.get());
        assertEquals(4L, progress.getLast());
    }
    @Test void installerCancelAndHashFailurePreserveLastValidDirectory() throws Exception {
        Path lastValid = writeModel(tinyModel());
        VoiceCancellation cancelled = new VoiceCancellation(); cancelled.cancel(); AtomicBoolean downloaded = new AtomicBoolean();
        NativeModelInstaller beforeDownload = tinyInstaller(tinyModel(), (download, file, cancellation, progress) -> downloaded.set(true));
        assertThrows(CancellationException.class, () -> beforeDownload.install(cancelled, (bytes, total) -> {})); assertFalse(downloaded.get());
        VoiceCancellation duringCopy = new VoiceCancellation();
        NativeModelInstaller cancelling = tinyInstaller(tinyModel(), (download, file, cancellation, progress) -> {
            Files.write(file, new byte[] {4}); cancellation.cancel(); cancellation.check();
        });
        assertThrows(CancellationException.class, () -> cancelling.install(duringCopy, (bytes, total) -> {}));
        NativeModelInstaller corrupt = tinyInstaller(tinyModel(), (download, file, cancellation, progress) -> Files.write(file, new byte[] {0, 0, 0}));
        assertThrows(NativeSpeechToText.Failure.class, () -> corrupt.install(new VoiceCancellation(), (bytes, total) -> {}));
        assertTrue(NativeModelFiles.modelReady(lastValid));
        try (var entries = Files.list(temporary.resolve("installs"))) { assertEquals(0, entries.count()); }
    }
    private NativeSpeechToText fake(NativeSpeechToText.WorkerExecutor worker) {
        return new NativeSpeechToText(temporary, temporary, (path, cancellation) -> tinyModel(), (path, cancellation) -> {}, worker);
    }
    private static SpeechToText.Request request(String language, int threads) {
        return new SpeechToText.Request(new PcmClip(new byte[320]), language, threads);
    }
    private static NativeModelFiles.Model tinyModel() {
        return new NativeModelFiles.Model("tiny", NativeModelFiles.ModelFamily.SENSE_VOICE,
                List.of(file(NativeModelFiles.Role.SENSE_VOICE_MODEL, "model.onnx", new byte[] {4, 5, 6}),
                        file(NativeModelFiles.Role.TOKENS, "tokens.txt", new byte[] {7})));
    }
    private static NativeModelFiles.ModelFile file(NativeModelFiles.Role role, String name, byte[] bytes) {
        return new NativeModelFiles.ModelFile(role, name, bytes.length, HexFormat.of().formatHex(NativeModelFiles.digest().digest(bytes)));
    }
    private Path writeModel(NativeModelFiles.Model model) throws Exception {
        Path directory = Files.createTempDirectory(temporary, "model-");
        Files.writeString(directory.resolve(NativeModelFiles.MANIFEST), NativeModelFiles.json(model));
        Files.write(directory.resolve("model.onnx"), new byte[] {4, 5, 6}); Files.write(directory.resolve("tokens.txt"), new byte[] {7}); return directory;
    }
    private NativeModelInstaller tinyInstaller(NativeModelFiles.Model model, NativeModelInstaller.Downloader downloader) {
        var downloads = model.files().stream().map(file -> new NativeModelInstaller.Download(file.path(),
                java.net.URI.create("https://example.invalid/" + file.path()), file.bytes(), file.sha256())).toList();
        return new NativeModelInstaller(temporary.resolve("installs"), model, downloads, downloader, false);
    }
    private static final class FakeProcess extends Process {
        final AtomicBoolean killed = new AtomicBoolean(); private final CountDownLatch ended = new CountDownLatch(1); private int exit;
        void finish(int code) { exit = code; ended.countDown(); }
        @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor() throws InterruptedException { ended.await(); return exit; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException { return ended.await(timeout, unit); }
        @Override public int exitValue() { if (isAlive()) throw new IllegalThreadStateException(); return exit; }
        @Override public void destroy() { killed.set(true); finish(137); }
        @Override public Process destroyForcibly() { destroy(); return this; }
        @Override public boolean isAlive() { return ended.getCount() != 0; }
    }
}
