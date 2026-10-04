package dev.openallay.client.voice;

import dev.openallay.model.config.CredentialReference;
import dev.openallay.model.config.LocalCredentialStore;
import dev.openallay.model.config.SecretValue;
import dev.openallay.tool.ToolResult;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

/** Client lifecycle bundle. Construction never opens capture or downloads files. */
public final class VoiceClientRuntime implements AutoCloseable {
    private final Path runtimeDirectory;
    private final VoiceConfigStore store;
    private final LocalCredentialStore credentials;
    private final dev.openallay.model.config.CredentialResolver credentialResolver;
    private final AudioCapture.Factory captures;
    private final NativeModelInstaller installer;
    private final ExecutorService settingsWorker;
    private final ExecutorService inferenceWorker;
    private final ExecutorService captureWorker;
    private final VoiceRuntime input;
    private final Settings actions = new Settings();
    private volatile List<AudioCapture.Device> devices = List.of();
    private volatile boolean closed;
    private volatile boolean busy;
    private volatile boolean modelReady;
    private volatile String code = "idle";
    private volatile long downloaded;
    private volatile long total;
    private volatile VoiceCancellation download;

    /** Environment and capture provider are supplied by the client boundary; neither is discovered here. */
    public VoiceClientRuntime(Path directory, VoiceRuntime.DraftPort drafts, Executor dispatcher,
            java.util.Map<String, String> credentialEnvironment, AudioCapture.Factory captures) {
        Objects.requireNonNull(directory); Objects.requireNonNull(dispatcher);
        this.captures = Objects.requireNonNull(captures, "captures");
        credentialEnvironment = java.util.Map.copyOf(credentialEnvironment);
        runtimeDirectory = directory.resolve("voice-models").resolve("runtime");
        store = new VoiceConfigStore(directory.resolve("voice.json"));
        credentials = new LocalCredentialStore(directory.resolve("voice-credentials.sqlite3"), Clock.systemUTC());
        credentialResolver = dev.openallay.model.config.CredentialResolver.composite(credentials, credentialEnvironment);
        installer = new NativeModelInstaller(directory.resolve("voice-models"));
        ThreadFactory factory = Thread.ofVirtual().name("openallay-voice-", 0).factory();
        settingsWorker = Executors.newSingleThreadExecutor(factory);
        inferenceWorker = Executors.newSingleThreadExecutor(factory);
        captureWorker = Executors.newThreadPerTaskExecutor(factory);
        // A dedicated serial inference slot prevents competing native processes/resource spikes.
        input = new VoiceRuntime(drafts, captures, this::backend, store::config, captureWorker, dispatcher);
        input.setFeedbackVisible(false);
        settingsWorker.execute(() -> {
            ToolResult<VoiceConfig> result = store.reload();
            if (result instanceof ToolResult.Failure<VoiceConfig>) code = "invalid_voice_config";
            refreshModelReady();
        });
    }
    private SpeechToText backend(VoiceConfig config) {
        SpeechToText delegate;
        if (config.backend() == VoiceConfig.Backend.HTTP) {
            delegate = new HttpSpeechToText(config.httpBaseUrl(), config.httpModel(), config.credential(), credentialResolver, Duration.ofSeconds(90));
        } else {
            if (config.nativeModelDirectory().isEmpty()) throw new IllegalStateException("model_not_installed");
            delegate = new NativeSpeechToText(Path.of(config.nativeModelDirectory()), runtimeDirectory);
        }
        return (request, cancellation) -> {
            cancellation.check();
            CompletableFuture<SpeechToText.Result> result = new CompletableFuture<>();
            inferenceWorker.execute(() -> {
                try { cancellation.check(); result.complete(delegate.transcribe(request, cancellation)); }
                catch (Throwable failure) { result.completeExceptionally(failure); }
            });
            try (AutoCloseable hook = cancellation.onCancel(() -> result.cancel(false))) {
                try { return result.get(); }
                catch (java.util.concurrent.ExecutionException failure) {
                    Throwable cause = failure.getCause();
                    if (cause instanceof Exception e) throw e;
                    if (cause instanceof Error e) throw e;
                    throw new IllegalStateException("voice_failed");
                }
            }
        };
    }
    public VoiceRuntime input() { return input; }
    public VoiceSettingsActions settings() { return actions; }
    private void refreshModelReady() {
        String directory = store.config().nativeModelDirectory();
        modelReady = !directory.isEmpty() && NativeModelFiles.modelReady(Path.of(directory));
        if (modelReady) {
            try { NativeRuntimeCatalog.validate(runtimeDirectory); }
            catch (Exception failure) { modelReady = false; }
        }
    }
    private final class Settings implements VoiceSettingsActions {
        @Override public Path runtimeNoticesDirectory() { return runtimeDirectory; }
        @Override public VoiceSettingsView view() {
            return new VoiceSettingsView(store.config(), devices, busy, modelReady, installer.modelName(), code, downloaded, total);
        }
        private <T> CompletableFuture<ToolResult<T>> submit(Supplier<ToolResult<T>> operation) {
            if (closed) return CompletableFuture.completedFuture(new ToolResult.Failure<>("voice_closed", "Voice input is closed"));
            CompletableFuture<ToolResult<T>> result = new CompletableFuture<>();
            try {
                settingsWorker.execute(() -> {
                    if (closed) { result.complete(new ToolResult.Failure<>("voice_closed", "Voice input is closed")); return; }
                    busy = true;
                    try { result.complete(operation.get()); }
                    catch (Exception | LinkageError failure) { code = "voice_settings_failed"; result.complete(new ToolResult.Failure<>(code, "Unable to update voice settings")); }
                    finally { busy = false; }
                });
            } catch (java.util.concurrent.RejectedExecutionException failure) {
                result.complete(new ToolResult.Failure<>("voice_closed", "Voice input is closed"));
            }
            return result;
        }
        private ToolResult<VoiceConfig> saved(VoiceConfig candidate) {
            input.cancel(VoiceRuntime.CancelReason.USER);
            ToolResult<VoiceConfig> result = store.save(candidate);
            code = result instanceof ToolResult.Success<VoiceConfig> ? "voice_saved" : "voice_save_failed";
            refreshModelReady();
            return result;
        }
        @Override public CompletableFuture<ToolResult<VoiceConfig>> update(VoiceConfig candidate) {
            return update(candidate, null);
        }
        @Override public CompletableFuture<ToolResult<VoiceConfig>> update(VoiceConfig candidate, char[] replacement) {
            Objects.requireNonNull(candidate, "candidate");
            char[] privateCopy = replacement == null ? null : replacement.clone();
            if (replacement != null) Arrays.fill(replacement, '\0');
            return submit(() -> saveCandidate(candidate, store.config(), privateCopy,
                    runtimeDirectory, credentials, this::saved))
                    .whenComplete((result, failure) -> {
                        if (privateCopy != null) Arrays.fill(privateCopy, '\0');
                    });
        }
        @Override public CompletableFuture<ToolResult<VoiceConfig>> reload() { return submit(() -> {
            input.cancel(VoiceRuntime.CancelReason.USER);
            ToolResult<VoiceConfig> result = store.reload(); refreshModelReady(); return result;
        }); }
        @Override public CompletableFuture<ToolResult<VoiceConfig>> importModel(Path directory) { return submit(() -> {
            try { NativeModelFiles.validate(directory); return saved(store.config().withModelDirectory(directory)); }
            catch (Exception failure) { code = "model_invalid"; return new ToolResult.Failure<>(code, "The selected model files or runtime integrity are invalid"); }
        }); }
        @Override public CompletableFuture<ToolResult<VoiceConfig>> importRuntime(Path directory) { return submit(() -> {
            try {
                installer.importRuntime(directory, new VoiceCancellation());
                refreshModelReady(); code = "runtime_imported";
                return new ToolResult.Success<>(store.config());
            } catch (Exception failure) {
                code = "runtime_invalid";
                return new ToolResult.Failure<>(code, "The selected runtime files do not match the trusted platform catalog");
            }
        }); }
        @Override public CompletableFuture<ToolResult<VoiceConfig>> downloadDefaultModel() {
            synchronized (VoiceClientRuntime.this) {
                if (closed) return CompletableFuture.completedFuture(new ToolResult.Failure<>("voice_closed", "Voice input is closed"));
                if (download != null) return CompletableFuture.completedFuture(new ToolResult.Failure<>("model_download_busy", "A model download is already running"));
                download = new VoiceCancellation();
            }
            VoiceCancellation cancellation = download;
            return submit(() -> {
                try {
                    code = "model_downloading"; downloaded = 0; total = 0;
                    Path directory = installer.install(cancellation, (bytes, length) -> { downloaded = bytes; total = length; });
                    cancellation.check();
                    return saved(store.config().withModelDirectory(directory));
                } catch (java.util.concurrent.CancellationException cancelled) {
                    code = "model_download_cancelled";
                    return new ToolResult.Failure<>(code, "Model download was cancelled; the previous model is retained");
                } catch (Exception failure) {
                    code = "model_download_failed";
                    return new ToolResult.Failure<>(code, "Unable to download and verify the model; the previous model is retained");
                } finally { download = null; }
            });
        }
        @Override public void cancelDownload() { VoiceCancellation current = download; if (current != null) captureWorker.execute(current::cancel); }
        @Override public CompletableFuture<ToolResult<VoiceConfig>> setApiKey(char[] key) {
            return update(store.config(), Objects.requireNonNull(key, "key"));
        }
        @Override public CompletableFuture<ToolResult<List<AudioCapture.Device>>> refreshDevices() { return submit(() -> {
            try {
                devices = captures.devices();
                code = "idle";
                return new ToolResult.Success<>(devices);
            } catch (AudioCapture.CaptureException failure) {
                devices = List.of();
                code = VoiceRuntime.safeCode(failure);
                return new ToolResult.Failure<>(code, "The microphone capture runtime is unavailable");
            }
        }); }
    }
    /** Worker-only candidate transaction. No capture, network, native loading or installation occurs here. */
    static ToolResult<VoiceConfig> saveCandidate(VoiceConfig candidate, VoiceConfig previous,
            char[] replacement, Path runtimeDirectory, LocalCredentialStore credentials,
            java.util.function.Function<VoiceConfig, ToolResult<VoiceConfig>> save) {
        CredentialReference inserted = null;
        boolean committed = false;
        try {
            boolean changedDirectory = !candidate.nativeModelDirectory().equals(previous.nativeModelDirectory());
            boolean activatingNative = candidate.enabled() && candidate.backend() == VoiceConfig.Backend.NATIVE
                    && (!previous.enabled() || previous.backend() != VoiceConfig.Backend.NATIVE);
            if (candidate.backend() == VoiceConfig.Backend.NATIVE && candidate.enabled()
                    && candidate.nativeModelDirectory().isEmpty()) {
                return new ToolResult.Failure<>("model_not_installed",
                        "Choose a verified voice model directory or disable native voice input");
            }
            if (!candidate.nativeModelDirectory().isEmpty() && (changedDirectory || activatingNative)) {
                try {
                    NativeModelFiles.validate(Path.of(candidate.nativeModelDirectory()));
                    NativeRuntimeCatalog.validate(runtimeDirectory);
                } catch (Exception invalid) {
                    return new ToolResult.Failure<>("model_invalid",
                            "The selected model or installed native runtime is invalid; import verified model and runtime files");
                }
            }
            if (replacement != null) {
                ToolResult<CredentialReference> result = credentials.insert(SecretValue.of(new String(replacement)));
                if (result instanceof ToolResult.Failure<CredentialReference> failure) {
                    return new ToolResult.Failure<>(failure.code(), failure.message());
                }
                inserted = ((ToolResult.Success<CredentialReference>) result).value();
            }
            VoiceConfig submitted = inserted == null ? candidate : candidate.withCredential(inserted);
            ToolResult<VoiceConfig> result = save.apply(submitted);
            committed = result instanceof ToolResult.Success<VoiceConfig>;
            if (committed && !Objects.equals(previous.credential(), submitted.credential())
                    && previous.credential() != null) {
                // This store owns only voice refs. Delete only the replaced ref, never unrelated rows.
                credentials.deleteIfUnreferenced(previous.credential(), submitted.credential() == null
                        ? java.util.Set.of() : java.util.Set.of(submitted.credential()));
            }
            return result;
        } finally {
            if (!committed && inserted != null) {
                credentials.deleteIfUnreferenced(inserted, previous.credential() == null
                        ? java.util.Set.of() : java.util.Set.of(previous.credential()));
            }
            if (replacement != null) Arrays.fill(replacement, '\0');
        }
    }

    @Override public void close() {
        closed = true;
        input.close();
        VoiceCancellation current = download;
        if (current != null) captureWorker.execute(current::cancel);
        settingsWorker.shutdown(); inferenceWorker.shutdown(); captureWorker.shutdown();
        // Store close runs behind already admitted settings work, not on the game thread.
        Thread.ofVirtual().name("openallay-voice-cleanup").start(() -> {
            try { settingsWorker.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            credentials.close();
        });
    }
}
