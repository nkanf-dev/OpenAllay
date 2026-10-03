package dev.openallay.client.voice;

import dev.openallay.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class VoiceConfigStoreTest {
    @TempDir Path directory;
    @Test void defaultsNativeDisabledAndExactShapeRejectsSecretsAndFractionalLimits() {
        VoiceConfig config = VoiceConfig.defaults();
        assertFalse(config.enabled()); assertEquals(VoiceConfig.Backend.NATIVE, config.backend());
        String json = VoiceConfigStore.encode(config);
        assertEquals(config, VoiceConfigStore.decode(json));
        assertThrows(RuntimeException.class, () -> VoiceConfigStore.decode(json.replace("\"maxClipSeconds\":20", "\"maxClipSeconds\":1.5")));
        assertThrows(RuntimeException.class, () -> VoiceConfigStore.decode(json.replace("{", "{\"apiKey\":\"secret\",")));
        assertThrows(RuntimeException.class, () -> VoiceConfigStore.decode(json.replace("\"enabled\":false", "\"enabled\":\"false\"")));
        assertThrows(RuntimeException.class, () -> config.withLimits(61, 4));
        assertThrows(RuntimeException.class, () -> config.withLimits(20, 9));
        assertThrows(RuntimeException.class, () -> config.withHttp(java.net.URI.create("https://user:secret@example.test/v1"), "asr"));
    }
    @Test void httpUrlWithoutSchemeIsRejectedAsInvalidInputNotNullPointer() {
        VoiceConfig config = VoiceConfig.defaults();
        for (String address : new String[]{"not-a-url", "/v1/audio/transcriptions", "//example.test/v1", ""}) {
            assertThrows(IllegalArgumentException.class,
                    () -> config.withHttp(java.net.URI.create(address), "asr"), address);
        }
        assertEquals(java.net.URI.create("https://example.test/v1"),
                config.withHttp(java.net.URI.create("https://example.test/v1"), "asr").httpBaseUrl());
    }
    @Test void badReloadAndFailedSaveRetainLastValidWithoutDestroyingFile() throws Exception {
        Path path = directory.resolve("voice.json"); VoiceConfigStore store = new VoiceConfigStore(path);
        VoiceConfig valid = VoiceConfig.defaults().withEnabled(true).withDevice("fake-id");
        assertInstanceOf(ToolResult.Success.class, store.save(valid));
        Files.writeString(path, "not json");
        assertInstanceOf(ToolResult.Failure.class, store.reload());
        assertEquals(valid, store.config()); assertEquals("not json", Files.readString(path));
        Path bad = directory.resolve("bad"); Files.writeString(bad, "file");
        VoiceConfigStore failed = new VoiceConfigStore(bad.resolve("voice.json"));
        assertInstanceOf(ToolResult.Failure.class, failed.save(valid));
        assertEquals(VoiceConfig.defaults(), failed.config());
    }
    @Test void credentialOnlyQualifiedReferenceIsPersisted() {
        var ref = dev.openallay.model.config.CredentialReference.local(java.util.UUID.randomUUID());
        String json = VoiceConfigStore.encode(VoiceConfig.defaults().withCredential(ref));
        assertTrue(json.contains(ref.encoded())); assertFalse(json.contains("apiKey"));
    }
}
