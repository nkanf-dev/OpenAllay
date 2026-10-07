package dev.openallay.skill.install;

import dev.openallay.OpenAllayConstants;
import dev.openallay.community.CommunityCatalogManifest;
import dev.openallay.model.CancellationSignal;
import dev.openallay.net.HttpExchangeRequest;
import dev.openallay.net.HttpTransport;
import dev.openallay.net.HttpTransportPolicy;
import dev.openallay.net.JdkHttpTransport;
import dev.openallay.skill.SkillDocument;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillSource;
import dev.openallay.tool.ToolResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** One validation and atomic-publication path for local and downloaded Skill packages. */
public final class SkillPackageInstaller {
    private final Path managedRoot;
    private final SkillParser parser;
    private final HttpTransport transport;
    private final String minecraftVersion;
    private final String openallayApiVersion;
    private final Set<String> availableTools;
    private final Set<String> installedMods;

    public SkillPackageInstaller(Path managedRoot, SkillParser parser, String minecraftVersion) {
        this(managedRoot, parser, minecraftVersion, Set.of());
    }

    public SkillPackageInstaller(
            Path managedRoot, SkillParser parser, String minecraftVersion, Set<String> installedMods) {
        this(
                managedRoot,
                parser,
                new JdkHttpTransport(new HttpTransportPolicy(
                        java.time.Duration.ofSeconds(15), "openallay-skill-package-http")),
                minecraftVersion,
                OpenAllayConstants.SKILL_API_VERSION,
                Set.of("openallay:run_javascript", "openallay:load_skill"),
                installedMods);
    }

    public SkillPackageInstaller(
            Path managedRoot,
            SkillParser parser,
            HttpTransport transport,
            String minecraftVersion,
            String openallayApiVersion) {
        this(
                managedRoot,
                parser,
                transport,
                minecraftVersion,
                openallayApiVersion,
                Set.of("openallay:run_javascript", "openallay:load_skill"),
                Set.of());
    }

    public SkillPackageInstaller(
            Path managedRoot,
            SkillParser parser,
            HttpTransport transport,
            String minecraftVersion,
            String openallayApiVersion,
            Set<String> availableTools,
            Set<String> installedMods) {
        this.managedRoot = Objects.requireNonNull(managedRoot, "managedRoot")
                .toAbsolutePath().normalize();
        this.parser = Objects.requireNonNull(parser, "parser");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.minecraftVersion = requireText(minecraftVersion, "minecraftVersion");
        this.openallayApiVersion = requireText(openallayApiVersion, "openallayApiVersion");
        this.availableTools = Set.copyOf(availableTools);
        this.installedMods = Set.copyOf(installedMods);
    }

    public CompletableFuture<ToolResult<InstallResult>> install(
            CommunityCatalogManifest.PackageEntry entry, CancellationSignal cancellation) {
        return prepare(entry, cancellation).thenApply(result -> commitPrepared(result));
    }

    public ToolResult<InstallResult> importLocal(Path source) {
        return commitPrepared(prepareLocal(source));
    }

    private ToolResult<InstallResult> commitPrepared(ToolResult<PreparedSkillInstall> result) {
        if (result instanceof ToolResult.Failure<PreparedSkillInstall> failure) {
            return new ToolResult.Failure<>(failure.code(), failure.message());
        }
        try (PreparedSkillInstall prepared =
                ((ToolResult.Success<PreparedSkillInstall>) result).value()) {
            ToolResult<Boolean> committed = prepared.commit();
            if (committed instanceof ToolResult.Failure<Boolean> failure) {
                return new ToolResult.Failure<>(failure.code(), failure.message());
            }
            return new ToolResult.Success<>(new InstallResult(prepared.id(), prepared.provenance()));
        }
    }

    /** Downloads and validates without creating a discoverable package. */
    public CompletableFuture<ToolResult<PreparedSkillInstall>> prepare(
            CommunityCatalogManifest.PackageEntry entry, CancellationSignal cancellation) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(cancellation, "cancellation");
        if (!entry.compatibility().supports(minecraftVersion, openallayApiVersion)) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "skill_install_incompatible",
                    "The Skill package is not compatible with this OpenAllay game runtime"));
        }
        if (cancellation.isCancelled()) {
            return CompletableFuture.completedFuture(cancelled());
        }
        CompletableFuture<ArchiveResponse> response;
        try {
            response = transport.execute(
                    HttpExchangeRequest.newBuilder(entry.archive())
                            .timeout(java.time.Duration.ofSeconds(60))
                            .header("accept", "application/zip, application/octet-stream")
                            .get().build(),
                    cancellation,
                    (status, headers, body) -> new ArchiveResponse(status, body.readAllBytes()));
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(installFailure());
        }
        return response.handle((archive, failure) -> {
            if (cancellation.isCancelled()) {
                return cancelled();
            }
            if (failure != null || archive == null || archive.status() != 200
                    || !sha256(archive.bytes()).equals(entry.sha256())) {
                return installFailure();
            }
            Path temporary = null;
            try {
                Files.createDirectories(managedRoot);
                temporary = Files.createTempFile(managedRoot, ".download-", ".zip");
                Files.write(temporary, archive.bytes());
                ToolResult<PreparedSkillInstall> result = prepareLocal(temporary,
                        entry.version(), entry.source().toString(), cancellation);
                if (result instanceof ToolResult.Success<PreparedSkillInstall> success) {
                    PreparedSkillInstall candidate = success.value();
                    if (!candidate.id().equals(entry.id())
                            || !candidate.metadata().attributes()
                                    .getOrDefault("openallay/version", entry.version())
                                    .equals(entry.version())) {
                        candidate.close();
                        return installFailure();
                    }
                    cancellation.onCancel(candidate::close);
                    if (cancellation.isCancelled()) {
                        candidate.close();
                        return cancelled();
                    }
                }
                return result;
            } catch (IOException | RuntimeException invalid) {
                return installFailure();
            } finally {
                if (temporary != null) {
                    try {
                        Files.deleteIfExists(temporary);
                    } catch (IOException ignored) {
                        // A hidden, non-discoverable download is harmless if cleanup fails.
                    }
                }
            }
        });
    }

    public ToolResult<PreparedSkillInstall> prepareLocal(Path source) {
        return prepareLocal(source, null, null, new CancellationSignal());
    }

    private ToolResult<PreparedSkillInstall> prepareLocal(
            Path source, String version, String provenance, CancellationSignal cancellation) {
        Objects.requireNonNull(source, "source");
        Path normalized = source.toAbsolutePath().normalize();
        Path operation = managedRoot.resolve(".install-" + UUID.randomUUID()).normalize();
        boolean retained = false;
        try {
            if (managedRoot.startsWith(normalized)) {
                throw new IllegalArgumentException(
                        "Skill import source cannot contain the managed Skill root");
            }
            Files.createDirectories(managedRoot);
            if (!operation.getParent().equals(managedRoot)) {
                throw new IllegalArgumentException("Invalid Skill staging path");
            }
            Files.createDirectory(operation);
            Path extracted = operation.resolve("package");
            Files.createDirectory(extracted);
            if (Files.isSymbolicLink(normalized)) {
                throw new IllegalArgumentException("Skill import cannot be a symbolic link");
            }
            if (Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
                Path packageTarget = extracted.resolve(normalized.getFileName().toString()).normalize();
                Files.createDirectory(packageTarget);
                copyDirectory(normalized, packageTarget);
            } else if (Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)
                    && normalized.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                            .endsWith(".zip")) {
                extractZip(normalized, extracted);
            } else {
                throw new IllegalArgumentException("Skill import must be a directory or ZIP archive");
            }
            Candidate candidate = candidate(extracted);
            String digest = candidate.sha256();
            var metadata = candidate.document().metadata();
            PreparedSkillInstall prepared = new PreparedSkillInstall(metadata, digest,
                    version == null ? metadata.attributes().getOrDefault("openallay/version", "") : version,
                    provenance == null ? metadata.provenance() : provenance,
                    () -> commit(candidate, operation, digest, cancellation),
                    () -> deleteTree(operation));
            retained = true;
            return new ToolResult.Success<>(prepared);
        } catch (RuntimeException | IOException failure) {
            return installFailure();
        } finally {
            if (!retained) {
                deleteTree(operation);
            }
        }
    }

    private synchronized ToolResult<Boolean> commit(
            Candidate candidate, Path operation, String digest, CancellationSignal cancellation) {
        try {
            if (cancellation.isCancelled()) {
                return cancelled();
            }
            for (Path path = candidate.packageRoot(); !path.equals(managedRoot); path = path.getParent()) {
                if (Files.isSymbolicLink(path)) {
                    return changed();
                }
            }
            // Read once and publish this verified snapshot, not files read after checksum validation.
            CapturedTree captured = capture(candidate.packageRoot());
            if (!captured.sha256().equals(digest)) {
                return changed();
            }
            Path verified = operation.resolve("verified");
            Files.createDirectory(verified);
            for (String directory : captured.directories()) {
                Files.createDirectories(verified.resolve(directory));
            }
            for (var file : captured.files().entrySet()) {
                Path target = verified.resolve(file.getKey());
                Files.createDirectories(target.getParent());
                Files.write(target, file.getValue());
            }
            if (cancellation.isCancelled()) {
                return cancelled();
            }
            publish(verified, candidate.document().metadata().name(), operation);
            return new ToolResult.Success<>(true);
        } catch (IOException | RuntimeException failure) {
            return installFailure();
        }
    }

    private static CapturedTree capture(Path root) throws IOException {
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Prepared Skill directory is unavailable");
        }
        Map<String, byte[]> files = new java.util.TreeMap<>();
        Set<String> directories = new java.util.TreeSet<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted().toList()) {
                if (path.equals(root)) {
                    continue;
                }
                String relative = root.relativize(path).toString().replace(java.io.File.separatorChar, '/');
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("Prepared Skill contains a symbolic link");
                }
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    directories.add(relative);
                } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    files.put(relative, Files.readAllBytes(path));
                } else {
                    throw new IOException("Prepared Skill contains an unsupported file");
                }
            }
        }
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            for (String directory : directories) {
                digest.update(("D" + directory + "\0").getBytes(StandardCharsets.UTF_8));
            }
            for (var file : files.entrySet()) {
                digest.update(("F" + file.getKey() + "\0" + file.getValue().length + "\0")
                        .getBytes(StandardCharsets.UTF_8));
                digest.update(file.getValue());
            }
            return new CapturedTree(files, directories, java.util.HexFormat.of().formatHex(digest.digest()));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private record CapturedTree(Map<String, byte[]> files, Set<String> directories, String sha256) {}

    private static <T> ToolResult.Failure<T> cancelled() {
        return new ToolResult.Failure<>("skill_install_cancelled", "Skill installation was cancelled");
    }

    private static <T> ToolResult.Failure<T> changed() {
        return new ToolResult.Failure<>("prepared_install_changed", "The prepared Skill changed; review it again");
    }

    private Candidate candidate(Path extracted) throws IOException {
        java.util.List<Path> entries;
        try (var stream = Files.walk(extracted)) {
            entries = stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                            && path.getFileName().toString().equals("SKILL.md"))
                    .toList();
        }
        if (entries.size() != 1) {
            throw new IllegalArgumentException("Skill package must contain exactly one SKILL.md");
        }
        Path entry = entries.get(0);
        Path packageRoot = entry.getParent();
        Path relativeRoot = extracted.relativize(packageRoot);
        if (relativeRoot.getNameCount() > 1) {
            throw new IllegalArgumentException("Skill package has unsupported wrapper directories");
        }
        CapturedTree captured = capture(packageRoot);
        Map<String, String> encoded = new LinkedHashMap<>();
        for (var file : captured.files().entrySet()) {
            encoded.put(file.getKey(), StandardCharsets.UTF_8.newDecoder()
                    .decode(java.nio.ByteBuffer.wrap(file.getValue())).toString());
        }
        SkillDocument document = parser.parsePackage(
                "local-import:" + packageRoot.getFileName(),
                encoded,
                SkillSource.Origin.LOCAL);
        if (!availableTools.containsAll(document.metadata().allowedTools())) {
            throw new IllegalArgumentException("Skill declares unavailable Tools");
        }
        if (!installedMods.containsAll(document.metadata().requiredMods())) {
            throw new IllegalArgumentException("Skill requires unavailable mods");
        }
        return new Candidate(packageRoot, document, captured.sha256());
    }

    private void publish(Path source, String name, Path operation) throws IOException {
        Path target = managedRoot.resolve(name).normalize();
        if (!target.getParent().equals(managedRoot)) {
            throw new IllegalArgumentException("Skill name escapes managed root");
        }
        Path published = operation.resolve("published");
        Files.move(source, published);
        Path backup = operation.resolve("prior");
        boolean hadPrior = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
        if (hadPrior) {
            Files.move(target, backup, StandardCopyOption.ATOMIC_MOVE);
        }
        try {
            Files.move(published, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException failure) {
            if (hadPrior && Files.exists(backup) && !Files.exists(target)) {
                Files.move(backup, target, StandardCopyOption.ATOMIC_MOVE);
            }
            throw failure;
        }
        deleteTree(backup);
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Path realSource = source.toRealPath();
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                if (Files.isSymbolicLink(directory)
                        || !directory.toRealPath().startsWith(realSource)) {
                    throw new IOException("Skill directory contains an unsafe path");
                }
                Files.createDirectories(target.resolve(source.relativize(directory)).normalize());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                    throws IOException {
                if (Files.isSymbolicLink(file) || !file.toRealPath().startsWith(realSource)) {
                    throw new IOException("Skill directory contains an unsafe file");
                }
                Files.copy(file, target.resolve(source.relativize(file)).normalize());
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void extractZip(Path archive, Path target) throws IOException {
        try (InputStream input = Files.newInputStream(archive);
                ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String raw = entry.getName();
                if (raw.isBlank() || raw.startsWith("/") || raw.contains("\\")) {
                    throw new IOException("Unsafe ZIP entry");
                }
                Path destination = target.resolve(raw).normalize();
                if (!destination.startsWith(target)) {
                    throw new IOException("ZIP entry escapes staging root");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(zip, destination);
                }
                zip.closeEntry();
            }
        }
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // Staging cleanup is best-effort; no unpublished path is discoverable as a Skill.
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static <T> ToolResult.Failure<T> installFailure() {
        return new ToolResult.Failure<>(
                "skill_install_failed",
                "The Skill package could not be validated and installed");
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }

    public record InstallResult(String skillName, String provenance) {
        public InstallResult {
            if (skillName == null || skillName.isBlank()
                    || provenance == null || provenance.isBlank()) {
                throw new IllegalArgumentException("Installed Skill identity is required");
            }
        }
    }

    private record Candidate(Path packageRoot, SkillDocument document, String sha256) {}

    private record ArchiveResponse(int status, byte[] bytes) {
        private ArchiveResponse {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
