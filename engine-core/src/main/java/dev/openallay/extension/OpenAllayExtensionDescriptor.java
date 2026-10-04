package dev.openallay.extension;

import dev.openallay.requirement.RequirementSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Stable identity and compatibility metadata declared by an Extension JAR. */
public record OpenAllayExtensionDescriptor(
        String id,
        String name,
        String version,
        String provider,
        String summary,
        Set<String> loaders,
        String minecraftVersionRange,
        String openAllayApiVersionRange,
        String source,
        RequirementSet requirements) {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** Retained source and binary constructor for Extensions compiled before advisory metadata. */
    public OpenAllayExtensionDescriptor(
            String id, String name, String version, String provider, String summary,
            Set<String> loaders, String minecraftVersionRange, String openAllayApiVersionRange,
            String source) {
        this(id, name, version, provider, summary, loaders, minecraftVersionRange,
                openAllayApiVersionRange, source, RequirementSet.EMPTY);
    }

    public OpenAllayExtensionDescriptor {
        id = require(id, "id");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid Extension ID: " + id);
        }
        name = require(name, "name");
        version = require(version, "version");
        provider = require(provider, "provider");
        summary = require(summary, "summary");
        TreeSet<String> normalizedLoaders = new TreeSet<>();
        for (String loader : Set.copyOf(loaders)) {
            normalizedLoaders.add(require(loader, "loader").toLowerCase(java.util.Locale.ROOT));
        }
        if (normalizedLoaders.isEmpty()) {
            throw new IllegalArgumentException("Extension must declare at least one loader");
        }
        loaders = Set.copyOf(normalizedLoaders);
        minecraftVersionRange = ExtensionCompatibility.requireRange(
                minecraftVersionRange, "minecraftVersionRange");
        openAllayApiVersionRange = ExtensionCompatibility.requireRange(
                openAllayApiVersionRange, "openAllayApiVersionRange");
        source = require(source, "source");
        requirements = java.util.Objects.requireNonNull(requirements, "requirements");
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
