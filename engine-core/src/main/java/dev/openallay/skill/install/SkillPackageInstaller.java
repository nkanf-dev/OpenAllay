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
        this(managedRoot, parser, minecraftVersion, dev.openallay.util.Java8Collections.setOf());
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
                dev.openallay.util.Java8Collections.setOf("openallay:run_javascript", "openallay:load_skill"),
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
                dev.openallay.util.Java8Collections.setOf("openallay:run_javascript", "openallay:load_skill"),
                dev.openallay.util.Java8Collections.setOf());
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
        this.availableTools = dev.openallay.util.Java8Collections.setCopyOf(availableTools);
        this.installedMods = dev.openallay.util.Java8Collections.setCopyOf(installedMods);
    }

    public CompletableFuture<ToolResult<InstallResult>> install(
            CommunityCatalogManifest.PackageEntry entry, CancellationSignal cancellation) {
        return prepare(entry, cancellation).thenApply(result -> commitPrepared(result));
    }

    public ToolResult<InstallResult> importLocal(Path source) {
        return commitPrepared(prepareLocal(source));
    }

    private ToolResult<InstallResult> commitPrepared(ToolResult<PreparedSkillInstall> result) {
        final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<dev.openallay.skill.install.PreparedSkillInstall> value; ToolResult.Failure<PreparedSkillInstall> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = result) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern0_holder.bound = (ToolResult.Failure<PreparedSkillInstall>) $oaPattern0_holder.value) != null))) {
            return new ToolResult.Failure<>($oaPattern0_holder.bound.code(), $oaPattern0_holder.bound.message());
        }
        try (PreparedSkillInstall prepared =
                ((ToolResult.Success<PreparedSkillInstall>) result).value()) {
            ToolResult<Boolean> committed = prepared.commit();
            final class $oaPattern1_Holder { dev.openallay.tool.ToolResult<java.lang.Boolean> value; ToolResult.Failure<Boolean> bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = committed) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern1_holder.bound = (ToolResult.Failure<Boolean>) $oaPattern1_holder.value) != null))) {
                return new ToolResult.Failure<>($oaPattern1_holder.bound.code(), $oaPattern1_holder.bound.message());
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
                final class $oaPattern2_Holder { dev.openallay.tool.ToolResult<dev.openallay.skill.install.PreparedSkillInstall> value; ToolResult.Success<PreparedSkillInstall> bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = result) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern2_holder.bound = (ToolResult.Success<PreparedSkillInstall>) $oaPattern2_holder.value) != null))) {
                    PreparedSkillInstall candidate = $oaPattern2_holder.bound.value();
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
            dev.openallay.skill.SkillMetadata metadata = candidate.document().metadata();
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
            for (java.util.Map.Entry<java.lang.String, byte[]> file : captured.files().entrySet()) {
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
        try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(root)) {
            for (Path path : dev.openallay.util.Java8Collections.toList(paths.sorted())) {
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
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            for (String directory : directories) {
                digest.update(("D" + directory + "\0").getBytes(StandardCharsets.UTF_8));
            }
            for (java.util.Map.Entry<java.lang.String, byte[]> file : files.entrySet()) {
                digest.update(("F" + file.getKey() + "\0" + file.getValue().length + "\0")
                        .getBytes(StandardCharsets.UTF_8));
                digest.update(file.getValue());
            }
            return new CapturedTree(files, directories, dev.openallay.util.Java8Hex.formatHex(digest.digest()));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    @dev.openallay.value.ValueType(CapturedTree.ValueSchemaProvider.class)
private static final class CapturedTree {
    private final Map<String, byte[]> files;
    private final Set<String> directories;
    private final String sha256;
    private CapturedTree(Map<String, byte[]> files, Set<String> directories, String sha256) {
        this.files = files;
        this.directories = directories;
        this.sha256 = sha256;
    }
    public Map<String, byte[]> files() { return files; }
    public Set<String> directories() { return directories; }
    public String sha256() { return sha256; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CapturedTree)) return false;
        CapturedTree that = (CapturedTree) other;
        return java.util.Objects.equals(files, that.files) && java.util.Objects.equals(directories, that.directories) && java.util.Objects.equals(sha256, that.sha256);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(files);
        hash = 31 * hash + java.util.Objects.hashCode(directories);
        hash = 31 * hash + java.util.Objects.hashCode(sha256);
        return hash;
    }
    @Override public String toString() { return "CapturedTree[files=" + files + ", directories=" + directories + ", sha256=" + sha256 + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CapturedTree> schema() {
            return new dev.openallay.value.ValueSchema<>(CapturedTree.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CapturedTree>>asList(new dev.openallay.value.ValueSchema.Component<>(CapturedTree.class, "files", CapturedTree::files), new dev.openallay.value.ValueSchema.Component<>(CapturedTree.class, "directories", CapturedTree::directories), new dev.openallay.value.ValueSchema.Component<>(CapturedTree.class, "sha256", CapturedTree::sha256)), arguments -> new CapturedTree((Map) arguments[0], (Set) arguments[1], (String) arguments[2]));
        }
    }
}

    private static <T> ToolResult.Failure<T> cancelled() {
        return new ToolResult.Failure<>("skill_install_cancelled", "Skill installation was cancelled");
    }

    private static <T> ToolResult.Failure<T> changed() {
        return new ToolResult.Failure<>("prepared_install_changed", "The prepared Skill changed; review it again");
    }

    private Candidate candidate(Path extracted) throws IOException {
        java.util.List<Path> entries;
        try (java.util.stream.Stream<java.nio.file.Path> stream = Files.walk(extracted)) {
            entries = dev.openallay.util.Java8Collections.toList(stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                            && path.getFileName().toString().equals("SKILL.md")));
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
        for (java.util.Map.Entry<java.lang.String, byte[]> file : captured.files().entrySet()) {
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
                if (dev.openallay.util.Java8Strings.isBlank(raw) || raw.startsWith("/") || raw.contains("\\")) {
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
        try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(root)) {
            for (Path path : dev.openallay.util.Java8Collections.toList(paths.sorted(java.util.Comparator.reverseOrder()))) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // Staging cleanup is best-effort; no unpublished path is discoverable as a Skill.
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            return dev.openallay.util.Java8Hex.formatHex(digest);
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
        if (value == null || dev.openallay.util.Java8Strings.isBlank(value)) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }

    @dev.openallay.value.ValueType(InstallResult.ValueSchemaProvider.class)
public static final class InstallResult {
    private final String skillName;
    private final String provenance;
    public InstallResult(String skillName, String provenance) {

            if (skillName == null || dev.openallay.util.Java8Strings.isBlank(skillName)
                    || provenance == null || dev.openallay.util.Java8Strings.isBlank(provenance)) {
                throw new IllegalArgumentException("Installed Skill identity is required");
            }

        this.skillName = skillName;
        this.provenance = provenance;
    }
    public String skillName() { return skillName; }
    public String provenance() { return provenance; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof InstallResult)) return false;
        InstallResult that = (InstallResult) other;
        return java.util.Objects.equals(skillName, that.skillName) && java.util.Objects.equals(provenance, that.provenance);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(skillName);
        hash = 31 * hash + java.util.Objects.hashCode(provenance);
        return hash;
    }
    @Override public String toString() { return "InstallResult[skillName=" + skillName + ", provenance=" + provenance + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<InstallResult> schema() {
            return new dev.openallay.value.ValueSchema<>(InstallResult.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<InstallResult>>asList(new dev.openallay.value.ValueSchema.Component<>(InstallResult.class, "skillName", InstallResult::skillName), new dev.openallay.value.ValueSchema.Component<>(InstallResult.class, "provenance", InstallResult::provenance)), arguments -> new InstallResult((String) arguments[0], (String) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(Candidate.ValueSchemaProvider.class)
private static final class Candidate {
    private final Path packageRoot;
    private final SkillDocument document;
    private final String sha256;
    private Candidate(Path packageRoot, SkillDocument document, String sha256) {
        this.packageRoot = packageRoot;
        this.document = document;
        this.sha256 = sha256;
    }
    public Path packageRoot() { return packageRoot; }
    public SkillDocument document() { return document; }
    public String sha256() { return sha256; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Candidate)) return false;
        Candidate that = (Candidate) other;
        return java.util.Objects.equals(packageRoot, that.packageRoot) && java.util.Objects.equals(document, that.document) && java.util.Objects.equals(sha256, that.sha256);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(packageRoot);
        hash = 31 * hash + java.util.Objects.hashCode(document);
        hash = 31 * hash + java.util.Objects.hashCode(sha256);
        return hash;
    }
    @Override public String toString() { return "Candidate[packageRoot=" + packageRoot + ", document=" + document + ", sha256=" + sha256 + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Candidate> schema() {
            return new dev.openallay.value.ValueSchema<>(Candidate.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Candidate>>asList(new dev.openallay.value.ValueSchema.Component<>(Candidate.class, "packageRoot", Candidate::packageRoot), new dev.openallay.value.ValueSchema.Component<>(Candidate.class, "document", Candidate::document), new dev.openallay.value.ValueSchema.Component<>(Candidate.class, "sha256", Candidate::sha256)), arguments -> new Candidate((Path) arguments[0], (SkillDocument) arguments[1], (String) arguments[2]));
        }
    }
}

    @dev.openallay.value.ValueType(ArchiveResponse.ValueSchemaProvider.class)
private static final class ArchiveResponse {
    private final int status;
    private final byte[] bytes;
    private ArchiveResponse(int status, byte[] bytes) {

            bytes = bytes.clone();

        this.status = status;
        this.bytes = bytes;
    }
    public int status() { return status; }

        public byte[] bytes() {
            return bytes.clone();
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ArchiveResponse)) return false;
        ArchiveResponse that = (ArchiveResponse) other;
        return status == that.status && java.util.Objects.equals(bytes, that.bytes);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(status);
        hash = 31 * hash + java.util.Objects.hashCode(bytes);
        return hash;
    }
    @Override public String toString() { return "ArchiveResponse[status=" + status + ", bytes=" + bytes + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ArchiveResponse> schema() {
            return new dev.openallay.value.ValueSchema<>(ArchiveResponse.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ArchiveResponse>>asList(new dev.openallay.value.ValueSchema.Component<>(ArchiveResponse.class, "status", ArchiveResponse::status), new dev.openallay.value.ValueSchema.Component<>(ArchiveResponse.class, "bytes", ArchiveResponse::bytes)), arguments -> new ArchiveResponse((Integer) arguments[0], (byte[]) arguments[1]));
        }
    }
}
}
