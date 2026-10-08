package dev.openallay.build;

import org.gradle.api.tasks.*;
import org.gradle.api.file.*;
import org.gradle.api.provider.*;
import org.gradle.process.CommandLineArgumentProvider;
import javax.inject.Inject;
import java.util.*;

/** Build-only producer process. Consumer supplies the tooling launcher and exact selected plans. */
public abstract class NormalizeMinecraftClassNamespace extends JavaExec {
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getUnitsPlan();
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getSymbolUnitsPlan();
    @InputFiles @PathSensitive(PathSensitivity.RELATIVE) public abstract ConfigurableFileCollection getCanonicalSources();
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getClientMappings();
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getServerMappings();
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getJoinedTsrg();
    @Input public abstract Property<String> getClientSha256();
    @Input public abstract Property<String> getServerSha256();
    @Input public abstract Property<String> getTsrgSha256();
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getMetadataClasspathPlan();
    @Classpath public abstract ConfigurableFileCollection getMetadataArtifacts();
    @InputFile @PathSensitive(PathSensitivity.NONE) public abstract RegularFileProperty getMetadataAcceptance();
    @Input public abstract Property<String> getSyntaxLevel();
    @Input public abstract Property<Boolean> getPreviewSyntax();
    @Input public abstract Property<String> getExactTargetTuple();
    @OutputDirectory public abstract DirectoryProperty getGeneratedJava();
    @OutputFile public abstract RegularFileProperty getNamespaceReceipt();
    @Inject public NormalizeMinecraftClassNamespace() {
        getMainClass().set("dev.openallay.build.MinecraftClassNamespaceProducerMain");
        getPreviewSyntax().convention(false);
        doFirst(task -> {
            try {
                List<java.nio.file.Path> protectedInputs = new ArrayList<>();
                // Parent task must protect the configured child process inputs, not only this JVM.
                for (java.io.File childToolInput : getClasspath().getFiles()) protectedInputs.add(childToolInput.toPath());
                if (!getJavaLauncher().isPresent()) throw new IllegalStateException("Explicit full tooling JavaLauncher required");
                java.nio.file.Path childHome = getJavaLauncher().get().getMetadata().getInstallationPath().getAsFile().toPath();
                protectedInputs.add(childHome.resolve("release"));
                protectedInputs.add(getJavaLauncher().get().getExecutablePath().getAsFile().toPath());
                protectedInputs.add(childHome.resolve("lib/modules"));

                for (java.io.File file : getCanonicalSources().getFiles()) protectedInputs.add(file.toPath());
                for (java.io.File file : getMetadataArtifacts().getFiles()) protectedInputs.add(file.toPath());
                for (RegularFileProperty input : List.of(getUnitsPlan(), getSymbolUnitsPlan(), getClientMappings(), getServerMappings(), getJoinedTsrg(), getMetadataClasspathPlan(), getMetadataAcceptance())) protectedInputs.add(input.get().getAsFile().toPath());
                java.nio.file.Path planFile = getMetadataClasspathPlan().get().getAsFile().toPath();
                protectedInputs.addAll(MinecraftClassNamespaceProducer.planInputPaths(List.of(
                        getUnitsPlan().get().getAsFile().toPath(), getSymbolUnitsPlan().get().getAsFile().toPath(), planFile)));
                List<String> planned = java.nio.file.Files.isRegularFile(planFile) ? java.nio.file.Files.readAllLines(planFile, java.nio.charset.StandardCharsets.UTF_8) : List.of();
                List<java.nio.file.Path> planPaths = new ArrayList<>();
                for (String row : planned) try { planPaths.add(java.nio.file.Path.of(row)); } catch (java.nio.file.InvalidPathException malformed) { /* Strict equality check fails after safe receipt invalidation. */ }
                protectedInputs.addAll(planPaths);
                MinecraftClassNamespaceProducer.preflightPaths(getGeneratedJava().get().getAsFile().toPath(), getNamespaceReceipt().get().getAsFile().toPath(), protectedInputs);
                java.nio.file.Files.deleteIfExists(getNamespaceReceipt().get().getAsFile().toPath());
                Set<java.nio.file.Path> plannedSources = new LinkedHashSet<>();
                for (RegularFileProperty sourcePlan : List.of(getUnitsPlan(), getSymbolUnitsPlan())) {
                    for (String row : java.nio.file.Files.readAllLines(sourcePlan.get().getAsFile().toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                        String[] cells = row.split("\t", -1);
                        if (cells.length != 4 || !java.nio.file.Path.of(cells[3]).isAbsolute()) throw new IllegalStateException("Invalid source ownership plan row");
                        plannedSources.add(java.nio.file.Path.of(cells[3]).toAbsolutePath().normalize());
                    }
                }
                Set<java.nio.file.Path> declaredSources = new LinkedHashSet<>();
                for (java.io.File file : getCanonicalSources().getFiles()) declaredSources.add(file.toPath().toAbsolutePath().normalize());
                if (!plannedSources.equals(declaredSources)) throw new IllegalStateException("Declared canonicalSources differs from units/symbol source plans");
                List<java.nio.file.Path> declared = getMetadataArtifacts().getFiles().stream().map(java.io.File::toPath).map(path -> path.toAbsolutePath().normalize()).toList();
                if (!planPaths.stream().map(path -> path.toAbsolutePath().normalize()).toList().equals(declared)) throw new IllegalStateException("Declared ordered metadataArtifacts differs from CLI plan");
            } catch (Exception failure) { throw new IllegalStateException("Namespace task input contract failed", failure); }
        });
        getArgumentProviders().add((CommandLineArgumentProvider) () -> List.of(
            "--units", getUnitsPlan().get().getAsFile().getAbsolutePath(),
            "--symbol-units", getSymbolUnitsPlan().get().getAsFile().getAbsolutePath(),
            "--client", getClientMappings().get().getAsFile().getAbsolutePath(),
            "--client-sha256", getClientSha256().get(),
            "--server", getServerMappings().get().getAsFile().getAbsolutePath(),
            "--server-sha256", getServerSha256().get(),
            "--tsrg", getJoinedTsrg().get().getAsFile().getAbsolutePath(),
            "--tsrg-sha256", getTsrgSha256().get(),
            "--classpath", getMetadataClasspathPlan().get().getAsFile().getAbsolutePath(),
            "--source", getSyntaxLevel().get(),
            "--preview", getPreviewSyntax().get().toString(),
            "--metadata-acceptance", getMetadataAcceptance().get().getAsFile().getAbsolutePath(),
            "--output", getGeneratedJava().get().getAsFile().getAbsolutePath(),
            "--receipt", getNamespaceReceipt().get().getAsFile().getAbsolutePath()));
    }
}
