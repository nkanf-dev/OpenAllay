package dev.openallay.extension.install;

import dev.openallay.extension.OpenAllayExtensionEnvironment;
import dev.openallay.extension.OpenAllayExtensionDescriptor;
import dev.openallay.tool.ToolResult;
import dev.openallay.extension.catalog.ExtensionCatalogArtifact;
import dev.openallay.extension.catalog.ExtensionCatalogEntry;
import dev.openallay.model.CancellationSignal;
import dev.openallay.net.HttpExchangeRequest;
import dev.openallay.net.HttpTransport;
import dev.openallay.net.HttpTransportPolicy;
import dev.openallay.net.JdkHttpTransport;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarInputStream;

/** Validates an Extension JAR and atomically stages it for the next loader restart. */
public final class ExtensionPackageInstaller {
    private final OpenAllayExtensionEnvironment environment;
    private final Path stagingRoot;
    private final HttpTransport transport;
    private final ExtensionPackageManifestCodec manifestCodec =
            new ExtensionPackageManifestCodec();

    public ExtensionPackageInstaller(
            OpenAllayExtensionEnvironment environment, Path stagingRoot) {
        this(
                environment,
                stagingRoot,
                new JdkHttpTransport(new HttpTransportPolicy(
                        java.time.Duration.ofSeconds(15),
                        "openallay-extension-package-http")));
    }

    public ExtensionPackageInstaller(
            OpenAllayExtensionEnvironment environment,
            Path stagingRoot,
            HttpTransport transport) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.stagingRoot = Objects.requireNonNull(stagingRoot, "stagingRoot")
                .toAbsolutePath()
                .normalize();
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    public ExtensionInstallResult stageLocal(ExtensionCatalogEntry entry, Path source) {
        return commitPrepared(entry.id(), prepareLocal(entry, source));
    }

    /** Stages a local package using only its embedded Extension manifest. */
    public ExtensionInstallResult stageLocal(Path source) {
        return commitPrepared("", prepareLocal(source));
    }

    public CompletableFuture<ExtensionInstallResult> stageDownload(
            ExtensionCatalogEntry entry, CancellationSignal cancellation) {
        return prepareDownload(entry, cancellation)
                .thenApply(result -> commitPrepared(entry.id(), result));
    }

    private ExtensionInstallResult commitPrepared(
            String id, ToolResult<PreparedExtensionInstall> result) {
        if (result instanceof ToolResult.Failure<PreparedExtensionInstall> failure) {
            return failed(id, failure.code());
        }
        try (PreparedExtensionInstall prepared =
                ((ToolResult.Success<PreparedExtensionInstall>) result).value()) {
            return prepared.commitInstall();
        }
    }

    public ToolResult<PreparedExtensionInstall> prepareLocal(
            ExtensionCatalogEntry entry, Path source) {
        Objects.requireNonNull(entry, "entry");
        return prepareLocal(Optional.of(entry), source);
    }

    public ToolResult<PreparedExtensionInstall> prepareLocal(Path source) {
        return prepareLocal(Optional.empty(), source);
    }

    private ToolResult<PreparedExtensionInstall> prepareLocal(
            Optional<ExtensionCatalogEntry> entry, Path source) {
        Objects.requireNonNull(source, "source");
        Path normalized = source.toAbsolutePath().normalize();
        try {
            if (Files.isSymbolicLink(normalized)
                    || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
                return preparationFailure("extension_install_failed");
            }
            return prepare(entry, Files.readAllBytes(normalized), new CancellationSignal());
        } catch (IOException | RuntimeException failure) {
            return preparationFailure("extension_install_failed");
        }
    }

    /** Selects and verifies the loader artifact before returning an unpublished candidate. */
    public CompletableFuture<ToolResult<PreparedExtensionInstall>> prepareDownload(
            ExtensionCatalogEntry entry, CancellationSignal cancellation) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(cancellation, "cancellation");
        Optional<ExtensionCatalogArtifact> selected = entry.artifactFor(environment.loader());
        if (selected.isEmpty()) {
            return CompletableFuture.completedFuture(preparationFailure("incompatible_loader"));
        }
        String incompatibility = environment.incompatibility(entry.descriptorFor(environment.loader()));
        if (!incompatibility.isEmpty()) {
            return CompletableFuture.completedFuture(preparationFailure(incompatibility));
        }
        if (cancellation.isCancelled()) {
            return CompletableFuture.completedFuture(preparationFailure("extension_install_cancelled"));
        }
        CompletableFuture<Download> response;
        try {
            response = transport.execute(
                    HttpExchangeRequest.newBuilder(selected.orElseThrow().artifact())
                            .timeout(java.time.Duration.ofSeconds(60))
                            .header("accept", "application/java-archive, application/octet-stream")
                            .get().build(),
                    cancellation,
                    (status, headers, body) -> new Download(status, body.readAllBytes()));
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(preparationFailure("extension_install_failed"));
        }
        return response.handle((download, failure) -> {
            if (cancellation.isCancelled()) {
                return preparationFailure("extension_install_cancelled");
            }
            if (failure != null || download == null || download.status() != 200) {
                return preparationFailure("extension_install_failed");
            }
            ToolResult<PreparedExtensionInstall> result = prepare(Optional.of(entry), download.bytes(), cancellation);
            if (result instanceof ToolResult.Success<PreparedExtensionInstall> success) {
                cancellation.onCancel(success.value()::close);
                if (cancellation.isCancelled()) {
                    success.value().close();
                    return preparationFailure("extension_install_cancelled");
                }
            }
            return result;
        });
    }

    private ToolResult<PreparedExtensionInstall> prepare(
            Optional<ExtensionCatalogEntry> catalog, byte[] bytes, CancellationSignal cancellation) {
        String checksum = sha256(bytes);
        ExtensionCatalogArtifact artifact = null;
        if (catalog.isPresent()) {
            ExtensionCatalogEntry entry = catalog.orElseThrow();
            artifact = entry.artifactFor(environment.loader()).orElse(null);
            if (artifact == null) {
                return preparationFailure("incompatible_loader");
            }
            String incompatibility = environment.incompatibility(entry.descriptorFor(environment.loader()));
            if (!incompatibility.isEmpty()) {
                return preparationFailure(incompatibility);
            }
            if (!checksum.equals(artifact.sha256())) {
                return preparationFailure("checksum_mismatch");
            }
        }
        InspectedPackage inspected;
        try {
            inspected = inspect(bytes);
        } catch (RuntimeException failure) {
            return preparationFailure("extension_manifest_invalid");
        }
        ExtensionPackageManifest manifest = inspected.manifest();
        if (catalog.isPresent()
                && (!sameIdentity(manifest.descriptor(), catalog.orElseThrow().descriptorFor(environment.loader()))
                        || !manifest.modIds().equals(artifact.modIds()))) {
            return preparationFailure("extension_manifest_mismatch");
        }
        String incompatibility = environment.incompatibility(manifest.descriptor());
        if (!incompatibility.isEmpty()) {
            return preparationFailure(incompatibility);
        }
        if (!inspected.declaredModIds().containsAll(manifest.modIds())) {
            return preparationFailure("mod_metadata_mismatch");
        }
        Path temporary = null;
        try {
            Files.createDirectories(stagingRoot);
            // No .jar suffix: this file is not a loader-visible installed artifact.
            temporary = Files.createTempFile(stagingRoot, ".extension-review-", ".candidate");
            Files.write(temporary, bytes);
            Path captured = temporary;
            boolean differs = catalog.isPresent()
                    && !catalog.orElseThrow().requirements().equals(manifest.descriptor().requirements());
            PreparedExtensionInstall prepared = new PreparedExtensionInstall(manifest, checksum, differs,
                    () -> commit(manifest, captured, checksum, cancellation), () -> discard(captured));
            temporary = null;
            return new ToolResult.Success<>(prepared);
        } catch (IOException | RuntimeException failure) {
            return preparationFailure("extension_install_failed");
        } finally {
            discard(temporary);
        }
    }

    private synchronized ExtensionInstallResult commit(
            ExtensionPackageManifest manifest, Path captured, String checksum, CancellationSignal cancellation) {
        try {
            if (cancellation.isCancelled()) {
                return failed(manifest.descriptor().id(), "extension_install_cancelled");
            }
            if (Files.isSymbolicLink(captured)
                    || !Files.isRegularFile(captured, LinkOption.NOFOLLOW_LINKS)) {
                return failed(manifest.descriptor().id(), "prepared_install_changed");
            }
            byte[] bytes = Files.readAllBytes(captured);
            if (!sha256(bytes).equals(checksum)) {
                return failed(manifest.descriptor().id(), "prepared_install_changed");
            }
            if (cancellation.isCancelled()) {
                return failed(manifest.descriptor().id(), "extension_install_cancelled");
            }
            return publish(manifest, bytes, checksum);
        } catch (IOException | RuntimeException failure) {
            return failed(manifest.descriptor().id(), "extension_install_failed");
        }
    }

    /** Advisory requirements never become a new artifact identity check. */
    private static boolean sameIdentity(
            OpenAllayExtensionDescriptor left, OpenAllayExtensionDescriptor right) {
        return left.id().equals(right.id())
                && left.name().equals(right.name())
                && left.version().equals(right.version())
                && left.provider().equals(right.provider())
                && left.summary().equals(right.summary())
                && left.loaders().equals(right.loaders())
                && left.minecraftVersionRange().equals(right.minecraftVersionRange())
                && left.openAllayApiVersionRange().equals(right.openAllayApiVersionRange())
                && left.source().equals(right.source());
    }

    private static ToolResult.Failure<PreparedExtensionInstall> preparationFailure(String diagnostic) {
        return new ToolResult.Failure<>(diagnostic, "The Extension package could not be validated and staged");
    }

    private static void discard(Path captured) {
        if (captured != null) {
            try {
                Files.deleteIfExists(captured);
            } catch (IOException ignored) {
                // An unpublished candidate remains hidden if best-effort cleanup fails.
            }
        }
    }

    private ExtensionInstallResult publish(
            ExtensionPackageManifest manifest, byte[] bytes, String checksum) {
        Path temporary = null;
        try {
            Files.createDirectories(stagingRoot);
            temporary = Files.createTempFile(stagingRoot, ".extension-", ".jar.tmp");
            Files.write(temporary, bytes);
            // A stable managed filename makes catalog updates an atomic replacement instead
            // of leaving two loader-visible versions of the same mod ID in the mods directory.
            String fileName = "openallay-extension-"
                    + manifest.descriptor().id().replace(':', '_').replace('/', '_')
                    + ".jar";
            Path target = stagingRoot.resolve(fileName).normalize();
            if (!target.getParent().equals(stagingRoot)) {
                throw new IllegalArgumentException("Extension staging path escapes its root");
            }
            Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            temporary = null;
            return new ExtensionInstallResult(
                    manifest.descriptor().id(),
                    ExtensionInstallState.RESTART_REQUIRED,
                    "restart_required",
                    Optional.of(target),
                    Optional.of(manifest),
                    checksum);
        } catch (IOException | RuntimeException failure) {
            return failed(manifest.descriptor().id(), "extension_install_failed");
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Hidden staging files are never loader-visible.
                }
            }
        }
    }

    private InspectedPackage inspect(byte[] bytes) {
        Set<String> modIds = new HashSet<>();
        ExtensionPackageManifest manifest = null;
        try (JarInputStream jar = new JarInputStream(new ByteArrayInputStream(bytes))) {
            for (java.util.jar.JarEntry entry = jar.getNextJarEntry(); entry != null; entry = jar.getNextJarEntry()) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (entry.getName().equals(ExtensionPackageManifest.JAR_PATH)) {
                    if (manifest != null) {
                        throw new IllegalArgumentException(
                                "Extension JAR has duplicate package manifests");
                    }
                    manifest = manifestCodec.decode(new String(
                            jar.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                } else if (entry.getName().equals("fabric.mod.json")) {
                    com.google.gson.JsonObject root = dev.openallay.json.JsonTrees.parse(
                                    new java.io.InputStreamReader(
                                            jar, java.nio.charset.StandardCharsets.UTF_8))
                            .getAsJsonObject();
                    if (root.has("id") && root.get("id").isJsonPrimitive()) {
                        modIds.add(root.get("id").getAsString());
                    }
                } else if (entry.getName().equals("META-INF/neoforge.mods.toml")
                        || entry.getName().equals("META-INF/mods.toml")) {
                    String metadata = new String(
                            jar.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    java.util.regex.Matcher matcher = java.util.regex.Pattern
                            .compile("(?m)^\\s*modId\\s*=\\s*[\"']([a-z0-9_.-]+)[\"']")
                            .matcher(metadata);
                    while (matcher.find()) {
                        modIds.add(matcher.group(1));
                    }
                }
            }
        } catch (IOException | RuntimeException failure) {
            throw new IllegalArgumentException("Invalid Extension JAR metadata", failure);
        }
        if (manifest == null) {
            throw new IllegalArgumentException(
                    "Extension JAR has no " + ExtensionPackageManifest.JAR_PATH);
        }
        if (modIds.isEmpty()) {
            throw new IllegalArgumentException("Extension JAR has no loader metadata");
        }
        return new InspectedPackage(manifest, Set.copyOf(modIds));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static ExtensionInstallResult failed(String extensionId, String diagnostic) {
        return new ExtensionInstallResult(
                extensionId,
                ExtensionInstallState.FAILED,
                diagnostic,
                Optional.empty(),
                Optional.empty(),
                "");
    }

    private record InspectedPackage(
            ExtensionPackageManifest manifest, Set<String> declaredModIds) {}

    private record Download(int status, byte[] bytes) {
        private Download {
            bytes = bytes.clone();
        }
    }
}
