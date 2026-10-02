package dev.openallay.client.voice;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.model.config.CredentialReference;
import dev.openallay.model.config.LocalCredentialStore;
import dev.openallay.model.config.SecretValue;
import dev.openallay.tool.ToolResult;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** All files and credentials here are synthetic. No live runtime, network or microphone is created. */
final class VoiceSettingsCandidateSaveTest {
    @TempDir Path root;

    @Test
    void replacementAndAllEditedFieldsCommitTogetherOnceAndOnlyTheOldVoiceRefIsCollected() {
        try (var credentials = credentials()) {
            var old = insert(credentials, "old voice key");
            var unrelated = insert(credentials, "unrelated retained key");
            var previous = VoiceConfig.defaults().withCredential(old);
            var candidate = previous.withBackend(VoiceConfig.Backend.HTTP).withEnabled(true)
                    .withDevice("chosen-device").withLanguage("zh").withLimits(37, 4)
                    .withHttp(URI.create("https://voice.example/v1"), "chosen-model");
            var persisted = new AtomicReference<>(previous);
            var saves = new AtomicInteger();
            char[] replacement = "new voice key".toCharArray();
            var result = VoiceClientRuntime.saveCandidate(candidate, previous, replacement,
                    root.resolve("runtime"), credentials, submitted -> {
                        saves.incrementAndGet();
                        assertNotEquals(old, submitted.credential());
                        assertEquals(candidate.withCredential(submitted.credential()), submitted);
                        assertEquals(previous, persisted.get(), "No old-config credential-only write");
                        persisted.set(submitted);
                        return new ToolResult.Success<>(submitted);
                    });
            assertInstanceOf(ToolResult.Success.class, result);
            assertEquals(1, saves.get());
            assertNotEquals(old, persisted.get().credential());
            assertFalse(contains(credentials, old));
            assertTrue(contains(credentials, persisted.get().credential()));
            assertTrue(contains(credentials, unrelated), "Never collect unrelated credential rows");
            assertZeroed(replacement);
        }
    }

    @Test
    void failedWriteRollsBackOnlyTheNewRefAndLeavesTheOldConfigAndOldRefUntouched() {
        try (var credentials = credentials()) {
            var old = insert(credentials, "old key");
            var unrelated = insert(credentials, "another key");
            var previous = VoiceConfig.defaults().withCredential(old);
            var candidate = previous.withBackend(VoiceConfig.Backend.HTTP).withDevice("draft-device");
            var inserted = new AtomicReference<CredentialReference>();
            char[] replacement = "retry key".toCharArray();
            var result = VoiceClientRuntime.saveCandidate(candidate, previous, replacement,
                    root.resolve("runtime"), credentials, submitted -> {
                        inserted.set(submitted.credential());
                        return new ToolResult.Failure<>("voice_write_failed", "Synthetic write failure");
                    });
            assertEquals("voice_write_failed", assertInstanceOf(ToolResult.Failure.class, result).code());
            assertNotNull(inserted.get());
            assertFalse(contains(credentials, inserted.get()));
            assertTrue(contains(credentials, old));
            assertTrue(contains(credentials, unrelated));
            assertEquals(old, previous.credential());
            assertZeroed(replacement);
        }
    }

    @Test
    void thrownWriteAlsoRollsBackTheInsertedRefAndClearsOwnedChars() {
        try (var credentials = credentials()) {
            var old = insert(credentials, "old key");
            var previous = VoiceConfig.defaults().withCredential(old);
            var inserted = new AtomicReference<CredentialReference>();
            char[] replacement = "retry key".toCharArray();
            assertThrows(IllegalStateException.class, () -> VoiceClientRuntime.saveCandidate(
                    previous.withBackend(VoiceConfig.Backend.HTTP), previous, replacement,
                    root.resolve("runtime"), credentials, submitted -> {
                        inserted.set(submitted.credential()); throw new IllegalStateException("Synthetic write failure");
                    }));
            assertFalse(contains(credentials, inserted.get()));
            assertTrue(contains(credentials, old));
            assertZeroed(replacement);
        }
    }

    @Test
    void emptyReplacementDoesNotClearASavedCredentialAndNoCredentialInsertionIsNeeded() {
        try (var credentials = credentials()) {
            var old = insert(credentials, "saved key");
            var previous = VoiceConfig.defaults().withCredential(old);
            var candidate = previous.withDevice("new-device");
            var result = VoiceClientRuntime.saveCandidate(candidate, previous, null,
                    root.resolve("runtime"), credentials, submitted -> {
                        assertEquals(old, submitted.credential());
                        return new ToolResult.Success<>(submitted);
                    });
            assertInstanceOf(ToolResult.Success.class, result);
            assertTrue(contains(credentials, old));
        }
    }

    @Test
    void enablingNativeWithoutAModelFailsBeforeCredentialOrConfigWrites() {
        try (var credentials = credentials()) {
            var previous = VoiceConfig.defaults();
            var saves = new AtomicInteger();
            char[] replacement = "must not insert".toCharArray();
            var result = VoiceClientRuntime.saveCandidate(previous.withEnabled(true), previous, replacement,
                    root.resolve("runtime"), credentials, submitted -> {
                        saves.incrementAndGet(); return new ToolResult.Success<>(submitted);
                    });
            assertEquals("model_not_installed", assertInstanceOf(ToolResult.Failure.class, result).code());
            assertEquals(0, saves.get());
            assertFalse(Files.exists(root.resolve("credentials.sqlite3")));
            assertZeroed(replacement);
        }
    }

    @Test
    void invalidTypedModelOrMissingTrustedRuntimeFailsBeforeAnyCredentialOrConfigWrite() throws Exception {
        var previous = VoiceConfig.defaults();
        var data = root.resolve("synthetic-model");
        Files.createDirectories(data);
        Files.write(data.resolve("model.onnx"), new byte[] {1, 2, 3});
        Files.write(data.resolve("tokens.txt"), new byte[] {4, 5});
        var model = new NativeModelFiles.Model("Synthetic data-only model", NativeModelFiles.ModelFamily.SENSE_VOICE,
                List.of(new NativeModelFiles.ModelFile(NativeModelFiles.Role.SENSE_VOICE_MODEL, "model.onnx", 3,
                        java.util.HexFormat.of().formatHex(NativeModelFiles.digest().digest(new byte[] {1, 2, 3}))),
                        new NativeModelFiles.ModelFile(NativeModelFiles.Role.TOKENS, "tokens.txt", 2,
                        java.util.HexFormat.of().formatHex(NativeModelFiles.digest().digest(new byte[] {4, 5})))));
        Files.writeString(data.resolve(NativeModelFiles.MANIFEST), NativeModelFiles.json(model));
        NativeModelFiles.validate(data); // Valid data does not authorize arbitrary runtime code.
        try (var credentials = credentials()) {
            for (Path directory : List.of(root.resolve("missing-model"), data)) {
                char[] replacement = "must not insert".toCharArray();
                var result = VoiceClientRuntime.saveCandidate(previous.withModelDirectory(directory), previous,
                        replacement, root.resolve("missing-runtime"), credentials,
                        submitted -> { throw new AssertionError("Invalid data/runtime must not be saved"); });
                assertEquals("model_invalid", assertInstanceOf(ToolResult.Failure.class, result).code());
                assertZeroed(replacement);
                assertFalse(Files.exists(root.resolve("credentials.sqlite3")));
            }
        }
    }

    @Test
    void disabledEmptyNativeConfigurationCanBeSavedWithoutModelOrRuntimeAccess() {
        try (var credentials = credentials()) {
            var previous = VoiceConfig.defaults().withBackend(VoiceConfig.Backend.HTTP);
            var candidate = previous.withBackend(VoiceConfig.Backend.NATIVE);
            assertInstanceOf(ToolResult.Success.class, VoiceClientRuntime.saveCandidate(candidate, previous, null,
                    root.resolve("nonexistent-runtime"), credentials, ToolResult.Success::new));
            assertFalse(Files.exists(root.resolve("credentials.sqlite3")));
        }
    }

    private LocalCredentialStore credentials() { return new LocalCredentialStore(root.resolve("credentials.sqlite3"), Clock.systemUTC()); }
    private static CredentialReference insert(LocalCredentialStore store, String value) {
        return ((ToolResult.Success<CredentialReference>) store.insert(SecretValue.of(value))).value();
    }
    private static boolean contains(LocalCredentialStore store, CredentialReference ref) {
        return ((ToolResult.Success<Boolean>) store.contains(ref)).value();
    }
    private static void assertZeroed(char[] chars) { for (char c : chars) assertEquals('\0', c); }
}
