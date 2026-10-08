package dev.openallay.client.voice;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real installer + canonical downloader. Tiny HTTPS fixture, no native runtime/model download. */
final class NativeModelInstallerHttpTest {
    @TempDir Path root;
    @Test void realHttpsPinnedDownloadsPublishAtomicallyAndPreserveLastValidModel() throws Exception {
        String fixtureKeys = System.getenv("OPENALLAY_VOICE_TLS_KEYS");
        org.junit.jupiter.api.Assumptions.assumeTrue(fixtureKeys != null, "TLS fixture opt-in supplies test-only JKS");
        KeyStore keys = KeyStore.getInstance("JKS");
        try (InputStream input = Files.newInputStream(Path.of(fixtureKeys))) { keys.load(input, "fixture-only".toCharArray()); }
        KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(keys, "fixture-only".toCharArray());
        SSLContext context = SSLContext.getInstance("TLS"); context.init(managers.getKeyManagers(), null, null);
        HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(context));
        byte[] model = {1, 2, 3}, tokens = {4, 5};
        AtomicBoolean corrupt = new AtomicBoolean();
        server.createContext("/model.onnx", exchange -> {
            byte[] bytes = corrupt.get() ? new byte[] {9, 9, 9} : model;
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.createContext("/tokens.txt", exchange -> {
            exchange.sendResponseHeaders(200, tokens.length); exchange.getResponseBody().write(tokens); exchange.close();
        });
        server.start();
        try {
            var description = new NativeModelFiles.Model("tiny HTTPS", NativeModelFiles.ModelFamily.SENSE_VOICE, List.of(
                    file(NativeModelFiles.Role.SENSE_VOICE_MODEL, "model.onnx", model),
                    file(NativeModelFiles.Role.TOKENS, "tokens.txt", tokens)));
            var downloads = description.files().stream().map(file -> new NativeModelInstaller.Download(file.path(),
                    URI.create("https://localhost:" + server.getAddress().getPort() + "/" + file.path()), file.bytes(), file.sha256())).toList();
            var installer = new NativeModelInstaller(root, description, downloads, NativeModelInstaller::download, false);
            Path installed = installer.install(new VoiceCancellation(), (bytes, total) -> assertTrue(bytes <= total));
            NativeModelFiles.validate(installed);
            assertArrayEquals(model, Files.readAllBytes(installed.resolve("model.onnx")));
            corrupt.set(true);
            assertEquals(installed, installer.install(new VoiceCancellation(), (bytes, total) -> {}));
            assertArrayEquals(model, Files.readAllBytes(installed.resolve("model.onnx")));
            var broken = new NativeModelInstaller(root.resolve("broken"), description, downloads, NativeModelInstaller::download, false);
            assertEquals("model_integrity", assertThrows(NativeSpeechToText.Failure.class,
                    () -> broken.install(new VoiceCancellation(), (bytes, total) -> {})).code());
            try (var entries = Files.list(root.resolve("broken"))) { assertEquals(0, entries.count()); }
            VoiceCancellation cancelled = new VoiceCancellation(); cancelled.cancel();
            assertThrows(java.util.concurrent.CancellationException.class,
                    () -> installer.install(cancelled, (bytes, total) -> {}));
            assertArrayEquals(model, Files.readAllBytes(installed.resolve("model.onnx")));
        } finally { server.stop(0); }
    }
    private static NativeModelFiles.ModelFile file(NativeModelFiles.Role role, String name, byte[] bytes) {
        return new NativeModelFiles.ModelFile(role, name, bytes.length,
                HexFormat.of().formatHex(NativeModelFiles.digest().digest(bytes)));
    }
}
