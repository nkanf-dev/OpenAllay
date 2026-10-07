package dev.openallay.skill.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.skill.SkillParser;
import dev.openallay.community.CommunityCatalogManifest;
import dev.openallay.model.CancellationSignal;
import dev.openallay.net.HttpExchangeRequest;
import dev.openallay.net.HttpResponseHeaders;
import dev.openallay.net.HttpTransport;
import dev.openallay.tool.ToolResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class SkillPackageInstallerTest {
    @TempDir Path temporaryDirectory;

    @Test
    void importsDirectoryAndZipThroughSameValidatedAtomicPublication() throws Exception {
        Path root = temporaryDirectory.resolve("managed");
        SkillPackageInstaller installer = new SkillPackageInstaller(root, new SkillParser(), "26.2");
        Path source = temporaryDirectory.resolve("downloaded-skill");
        Files.createDirectories(source.resolve("references"));
        Files.writeString(source.resolve("SKILL.md"), skill("demo", "first"));
        Files.writeString(source.resolve("references/facts.md"), "facts");

        assertEquals("demo", success(installer.importLocal(source)).skillName());
        assertEquals("first", Files.readString(root.resolve("demo/SKILL.md"))
                .lines().reduce((left, right) -> right).orElseThrow());

        Path archive = temporaryDirectory.resolve("demo.zip");
        zip(archive, "SKILL.md", skill("demo", "second"));
        assertEquals("demo", success(installer.importLocal(archive)).skillName());
        assertTrue(Files.readString(root.resolve("demo/SKILL.md")).endsWith("second\n"));
    }

    @Test
    void invalidReplacementAndZipSlipRetainPriorPackage() throws Exception {
        Path root = temporaryDirectory.resolve("managed");
        SkillPackageInstaller installer = new SkillPackageInstaller(root, new SkillParser(), "26.2");
        Path source = temporaryDirectory.resolve("demo");
        Files.createDirectories(source);
        Files.writeString(source.resolve("SKILL.md"), skill("demo", "valid"));
        success(installer.importLocal(source));

        Path broken = temporaryDirectory.resolve("broken.zip");
        zip(broken, "demo/SKILL.md", "not a skill");
        assertEquals("skill_install_failed",
                assertInstanceOf(ToolResult.Failure.class, installer.importLocal(broken)).code());
        assertTrue(Files.readString(root.resolve("demo/SKILL.md")).endsWith("valid\n"));

        Path slip = temporaryDirectory.resolve("slip.zip");
        zip(slip, "../escape", "bad");
        assertInstanceOf(ToolResult.Failure.class, installer.importLocal(slip));
        assertFalse(Files.exists(temporaryDirectory.resolve("escape")));
    }

    @Test
    void remoteInstallVerifiesChecksumAndCompatibilityBeforePublishing() throws Exception {
        byte[] archive = zipBytes("demo/SKILL.md", skill("demo", "remote"));
        HttpTransport transport = new HttpTransport() {
            @Override
            public <T> CompletableFuture<T> execute(
                    HttpExchangeRequest request,
                    dev.openallay.net.HttpCancellation cancellation,
                    ResponseDecoder<T> decoder) {
                try {
                    return CompletableFuture.completedFuture(decoder.decode(
                            200,
                            new HttpResponseHeaders(Map.of()),
                            new java.io.ByteArrayInputStream(archive)));
                } catch (IOException failure) {
                    return CompletableFuture.failedFuture(failure);
                }
            }
        };
        Path root = temporaryDirectory.resolve("remote-managed");
        SkillPackageInstaller installer = new SkillPackageInstaller(
                root, new SkillParser(), transport, "26.2", "0.2");

        assertEquals("demo", success(installer.install(
                entry(sha256(archive), "26.2"), new CancellationSignal()).join()).skillName());
        assertEquals("skill_install_incompatible", assertInstanceOf(
                ToolResult.Failure.class,
                installer.install(entry(sha256(archive), "1.21.1"), new CancellationSignal()).join())
                .code());
        assertEquals("skill_install_failed", assertInstanceOf(
                ToolResult.Failure.class,
                installer.install(entry("0".repeat(64), "26.2"), new CancellationSignal()).join())
                .code());
        assertTrue(Files.readString(root.resolve("demo/SKILL.md")).endsWith("remote\n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.12.2", "1.16.5", "26.2"})
    void defaultInstallerUsesSuppliedTargetAndIndependentSkillApiWithoutDownloading(
            String minecraftVersion) {
        Path root = temporaryDirectory.resolve("managed");
        SkillPackageInstaller installer = new SkillPackageInstaller(
                root, new SkillParser(), minecraftVersion);
        CancellationSignal cancelled = new CancellationSignal();
        cancelled.cancel();
        String otherTarget = minecraftVersion.equals("26.2") ? "1.12.2" : "26.2";

        assertEquals("skill_install_cancelled", assertInstanceOf(ToolResult.Failure.class,
                installer.prepare(entry("a".repeat(64), minecraftVersion), cancelled).join()).code());
        assertEquals("skill_install_cancelled", assertInstanceOf(ToolResult.Failure.class,
                installer.prepare(entry("a".repeat(64), "[1.12.2,26.2]"), cancelled).join()).code());
        assertEquals("skill_install_incompatible", assertInstanceOf(ToolResult.Failure.class,
                installer.prepare(entry("a".repeat(64), otherTarget), cancelled).join()).code());
        assertEquals("skill_install_incompatible", assertInstanceOf(ToolResult.Failure.class,
                installer.prepare(entry("a".repeat(64), "[1.0,1.12.2)"), cancelled).join()).code());
        assertEquals("skill_install_incompatible", assertInstanceOf(ToolResult.Failure.class,
                installer.prepare(entry("a".repeat(64), minecraftVersion, "0.4.0"), cancelled)
                        .join()).code());
        assertFalse(Files.exists(root));
    }

    @Test
    void preparedDirectoryIsHiddenAndCommitsCapturedBytesDespiteSourceChanges() throws Exception {
        Path root = temporaryDirectory.resolve("managed");
        SkillPackageInstaller installer = new SkillPackageInstaller(root, new SkillParser(), "26.2");
        Path source = temporaryDirectory.resolve("source");
        Files.createDirectories(source.resolve("references"));
        String reviewed = skill("demo", "reviewed").replace(
                "  openallay/version:",
                "  openallay/requires-capabilities: \"missing:capability\"\n"
                        + "  openallay/requires-extensions: \"missing:extension\"\n"
                        + "  openallay/requires-skills: \"missing-skill\"\n"
                        + "  openallay/version:");
        Files.writeString(source.resolve("SKILL.md"), reviewed);
        Files.writeString(source.resolve("references/facts.md"), "original facts");

        PreparedSkillInstall candidate = prepared(installer.prepareLocal(source));
        assertEquals(64, candidate.sha256().length());
        assertEquals(java.util.Set.of("missing:capability"), candidate.requirements().capabilities());
        assertFalse(Files.exists(root.resolve("demo")));
        assertTrue(new dev.openallay.skill.FilesystemSkillLoader().load(root).sources().isEmpty());
        Files.writeString(source.resolve("SKILL.md"), skill("demo", "unreviewed"));
        Files.writeString(source.resolve("references/facts.md"), "changed facts");

        assertInstanceOf(ToolResult.Success.class, candidate.commit());
        assertEquals(reviewed, Files.readString(root.resolve("demo/SKILL.md")));
        assertEquals("original facts", Files.readString(root.resolve("demo/references/facts.md")));
        assertEquals("prepared_install_consumed",
                assertInstanceOf(ToolResult.Failure.class, candidate.commit()).code());
        candidate.close();
        assertEquals(java.util.List.of("demo"), children(root));
    }

    @Test
    void discardedAndTamperedCandidatesRetainPriorPackageAndCleanStaging() throws Exception {
        Path root = temporaryDirectory.resolve("managed");
        SkillPackageInstaller installer = new SkillPackageInstaller(root, new SkillParser(), "26.2");
        Path archive = temporaryDirectory.resolve("demo.zip");
        zip(archive, "SKILL.md", skill("demo", "prior"));
        success(installer.importLocal(archive));
        String prior = Files.readString(root.resolve("demo/SKILL.md"));
        zip(archive, "SKILL.md", skill("demo", "replacement"));
        PreparedSkillInstall discarded = prepared(installer.prepareLocal(archive));
        discarded.close();
        discarded.close();
        assertInstanceOf(ToolResult.Failure.class, discarded.commit());
        assertEquals(prior, Files.readString(root.resolve("demo/SKILL.md")));

        PreparedSkillInstall tampered = prepared(installer.prepareLocal(archive));
        Path stagedEntry;
        try (var paths = Files.walk(root)) {
            stagedEntry = paths.filter(path -> path.getFileName().toString().equals("SKILL.md"))
                    .filter(path -> !path.equals(root.resolve("demo/SKILL.md")))
                    .findFirst().orElseThrow();
        }
        Files.writeString(stagedEntry, skill("demo", "tampered"));
        assertEquals("prepared_install_changed",
                assertInstanceOf(ToolResult.Failure.class, tampered.commit()).code());
        assertEquals(prior, Files.readString(root.resolve("demo/SKILL.md")));
        assertEquals(java.util.List.of("demo"), children(root));
    }

    @Test
    void cancellationBeforeOrAfterDownloadDiscardsEveryCandidate() throws Exception {
        byte[] archive = zipBytes("SKILL.md", skill("demo", "remote"));
        CompletableFuture<byte[]> response = new CompletableFuture<>();
        Path root = temporaryDirectory.resolve("managed");
        SkillPackageInstaller installer = new SkillPackageInstaller(
                root, new SkillParser(), transport(response), "26.2", "0.2");
        CancellationSignal early = new CancellationSignal();
        var future = installer.prepare(entry(sha256(archive), "26.2"), early);
        early.cancel();
        response.complete(archive);
        assertInstanceOf(ToolResult.Failure.class, future.join());
        assertFalse(Files.exists(root.resolve("demo")));

        CancellationSignal late = new CancellationSignal();
        PreparedSkillInstall candidate = prepared(installer.prepare(
                entry(sha256(archive), "26.2"), late).join());
        assertTrue(new dev.openallay.skill.FilesystemSkillLoader().load(root).sources().isEmpty());
        late.cancel();
        assertInstanceOf(ToolResult.Failure.class, candidate.commit());
        assertTrue(children(root).isEmpty());
    }

    @Test
    void preparationPreservesExecutableAndLegacyDependencyRejections() throws Exception {
        Path root = temporaryDirectory.resolve("managed");
        SkillPackageInstaller installer = new SkillPackageInstaller(root, new SkillParser(), "26.2");
        Path source = temporaryDirectory.resolve("source");
        Files.createDirectories(source.resolve("assets"));
        Files.writeString(source.resolve("SKILL.md"), skill("demo", "valid"));
        Files.writeString(source.resolve("assets/run.js"), "execute()");
        assertInstanceOf(ToolResult.Failure.class, installer.prepareLocal(source));
        Files.delete(source.resolve("assets/run.js"));
        Files.writeString(source.resolve("SKILL.md"), skill("demo", "valid")
                .replace("allowed-tools: \"\"", "allowed-tools: \"missing:tool\""));
        assertInstanceOf(ToolResult.Failure.class, installer.prepareLocal(source));
        assertTrue(children(root).isEmpty());
    }

    private static java.util.List<String> children(Path root) throws IOException {
        try (var paths = Files.list(root)) {
            return paths.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    @SuppressWarnings("unchecked")
    private static PreparedSkillInstall prepared(ToolResult<PreparedSkillInstall> result) {
        return ((ToolResult.Success<PreparedSkillInstall>)
                assertInstanceOf(ToolResult.Success.class, result)).value();
    }

    private static HttpTransport transport(CompletableFuture<byte[]> bytes) {
        return new HttpTransport() {
            @Override
            public <T> CompletableFuture<T> execute(HttpExchangeRequest request,
                    dev.openallay.net.HttpCancellation cancellation, ResponseDecoder<T> decoder) {
                return bytes.thenApply(value -> {
                    try {
                        return decoder.decode(200, new HttpResponseHeaders(Map.of()),
                                new java.io.ByteArrayInputStream(value));
                    } catch (IOException failure) {
                        throw new java.util.concurrent.CompletionException(failure);
                    }
                });
            }
        };
    }

    private static SkillPackageInstaller.InstallResult success(
            ToolResult<SkillPackageInstaller.InstallResult> result) {
        return ((ToolResult.Success<SkillPackageInstaller.InstallResult>)
                assertInstanceOf(ToolResult.Success.class, result)).value();
    }

    private static void zip(Path archive, String name, String contents) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry(name));
            output.write(contents.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }

    private static byte[] zipBytes(String name, String contents) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            output.putNextEntry(new ZipEntry(name));
            output.write(contents.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static CommunityCatalogManifest.PackageEntry entry(
            String checksum, String minecraft) {
        return entry(checksum, minecraft, "0.2");
    }

    private static CommunityCatalogManifest.PackageEntry entry(
            String checksum, String minecraft, String api) {
        return new CommunityCatalogManifest.PackageEntry(
                "demo",
                "Demo Skill",
                "A demo vertical workflow.",
                "Test Publisher",
                "1.0.0",
                URI.create("https://example.test/demo.zip"),
                checksum,
                new CommunityCatalogManifest.Compatibility(minecraft, api),
                URI.create("https://example.test/demo"));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String skill(String name, String body) {
        return """
                ---
                name: %s
                description: Demo
                metadata:
                  openallay/version: "1.0.0"
                allowed-tools: ""
                ---
                %s
                """.formatted(name, body);
    }
}
