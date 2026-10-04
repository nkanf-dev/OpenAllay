package dev.openallay.extension.universal;

import dev.openallay.api.extension.ExtensionDescriptor;
import dev.openallay.api.extension.ExtensionEnvironment;
import dev.openallay.api.extension.SupportDeclaration;
import dev.openallay.api.extension.SupportTarget;
import dev.openallay.extension.ExtensionCompatibility;
import dev.openallay.extension.OpenAllayExtensionDescriptor;
import dev.openallay.requirement.RequirementSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Matches public support coordinates against actual host facts, never against validation labels. */
public final class UniversalExtensionSupport {
    private UniversalExtensionSupport() {}

    public static Optional<SupportTarget> matchingTarget(
            SupportDeclaration support, ExtensionEnvironment environment) {
        Objects.requireNonNull(support, "support");
        Objects.requireNonNull(environment, "environment");
        if (environment.javaVersion() < support.minimumJavaVersion()
                || !environment.hostFeatures().containsAll(support.requiredHostFeatures())) {
            return Optional.empty();
        }
        return support.targets().stream().filter(target ->
                target.loader().equals(environment.loader())
                && ExtensionCompatibility.includes(target.minecraftVersionRange(), environment.minecraftVersion())
                && ExtensionCompatibility.includes(target.openAllayVersionRange(), environment.openAllayVersion())
                && environment.openAllayApiVersions().stream().anyMatch(version ->
                        ExtensionCompatibility.includes(target.openAllayApiVersionRange(), version)))
                .findFirst();
    }

    public static String incompatibility(SupportDeclaration support, ExtensionEnvironment environment) {
        Objects.requireNonNull(support, "support");
        Objects.requireNonNull(environment, "environment");
        if (environment.javaVersion() < support.minimumJavaVersion()) return "incompatible_java_version";
        if (!environment.hostFeatures().containsAll(support.requiredHostFeatures())) {
            return "incompatible_host_features";
        }
        if (matchingTarget(support, environment).isPresent()) return "";
        if (support.targets().stream().noneMatch(target -> target.loader().equals(environment.loader()))) {
            return "incompatible_loader";
        }
        return "incompatible_support_target";
    }

    public static OpenAllayExtensionDescriptor legacyDescriptor(
            ExtensionDescriptor descriptor, SupportTarget target) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(target, "target");
        if (!descriptor.support().targets().contains(target)) {
            throw new IllegalArgumentException("Target is not declared by this Extension");
        }
        var requirements = descriptor.requirements();
        return new OpenAllayExtensionDescriptor(descriptor.id(), descriptor.name(), descriptor.version(),
                descriptor.provider(), descriptor.summary(), Set.of(target.loader()),
                target.minecraftVersionRange(), target.openAllayApiVersionRange(), descriptor.source(),
                new RequirementSet(requirements.capabilities(), requirements.extensions(), requirements.skills()));
    }
}
