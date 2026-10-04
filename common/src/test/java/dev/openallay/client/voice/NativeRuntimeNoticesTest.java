package dev.openallay.client.voice;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NativeRuntimeNoticesTest {
    @TempDir Path temporary;

    @Test void fixedOfflineResourcesMatchExactBytesAndHashes() throws Exception {
        for (var notice : NativeRuntimeNotices.notices()) {
            byte[] bytes = NativeRuntimeNotices.resource(notice);
            assertEquals(notice.bytes(), bytes.length, notice.name());
            assertEquals(notice.sha256(), HexFormat.of().formatHex(NativeModelFiles.digest().digest(bytes)), notice.name());
        }
        assertTrue(NativeRuntimeNotices.notices().stream().anyMatch(notice -> notice.name().equals("GPL3.txt")));
        assertTrue(NativeRuntimeNotices.notices().stream().anyMatch(notice -> notice.name().equals("SOURCES.json")));
    }
    @Test void noticeGenerationIsOfflineImmutableAndNeverChangesRuntimeJars() throws Exception {
        for (String operation : new String[] {"download-staging", "import-staging"}) {
            Path runtime = Files.createDirectory(temporary.resolve(operation));
            byte[] executable = new byte[] {4, 7, 8};
            Path jar = runtime.resolve("pinned-runtime.jar"); Files.write(jar, executable);
            NativeRuntimeNotices.write(runtime);
            NativeRuntimeNotices.write(runtime);
            assertArrayEquals(executable, Files.readAllBytes(jar));
            for (var notice : NativeRuntimeNotices.notices()) {
                assertArrayEquals(NativeRuntimeNotices.resource(notice), Files.readAllBytes(runtime.resolve(notice.name())));
            }
            try (var files = Files.list(runtime)) {
                assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".notice-")));
            }
        }
    }
    @Test void refusesToOverwriteUnexpectedExistingNoticeOrFollowSymlink() throws Exception {
        Path runtime = Files.createDirectory(temporary.resolve("runtime"));
        Files.writeString(runtime.resolve("GPL3.txt"), "Unexpected player text");
        assertThrows(IOException.class, () -> NativeRuntimeNotices.write(runtime));
        assertEquals("Unexpected player text", Files.readString(runtime.resolve("GPL3.txt")));
        assertThrows(IOException.class, () -> NativeRuntimeNotices.write(temporary.resolve("absent")));
    }
    @Test void installerHooksRunAfterKnownPinValidationAndBeforePromotion() throws Exception {
        Path source = Path.of("../engine-core/src/main/java/dev/openallay/client/voice/NativeModelInstaller.java");
        if (!Files.exists(source)) source = Path.of("engine-core/src/main/java/dev/openallay/client/voice/NativeModelInstaller.java");
        String installer = Files.readString(source);
        int importStart = installer.indexOf("public void importRuntime(");
        int downloadStart = installer.indexOf("public Path install(");
        String imported = installer.substring(importStart, downloadStart);
        assertTrue(imported.contains("NativeRuntimeNotices.write(destination)"));
        assertTrue(imported.indexOf("NativeModelFiles.verifyFile(staging") < imported.indexOf("NativeRuntimeNotices.write(staging)"));
        assertTrue(imported.indexOf("NativeRuntimeNotices.write(staging)") < imported.indexOf("Files.move(staging, destination"));
        String downloaded = installer.substring(downloadStart);
        assertTrue(downloaded.contains("NativeRuntimeNotices.write(runtime)"));
        assertTrue(downloaded.indexOf("fetchAll(runtimeDownloads, staging") < downloaded.indexOf("NativeRuntimeNotices.write(staging)"));
        assertTrue(downloaded.indexOf("NativeRuntimeNotices.write(staging)") < downloaded.indexOf("Files.move(staging, runtime"));
    }
}
