package dev.openallay.extension;

import java.util.Set;

/** Actual implemented public API coordinates; the legacy primary accessor is retained. */
public record OpenAllayExtensionEnvironment(
        String loader,
        String minecraftVersion,
        String openAllayApiVersion,
        Set<String> implementedApiVersions) {
    /** Preserves the constructor used by already released 0.2.x Extensions. */
    public OpenAllayExtensionEnvironment(String loader, String minecraftVersion, String openAllayApiVersion) {
        this(loader, minecraftVersion, openAllayApiVersion, Set.of(openAllayApiVersion));
    }

    public OpenAllayExtensionEnvironment {
        loader = require(loader, "loader").toLowerCase(java.util.Locale.ROOT);
        minecraftVersion = require(minecraftVersion, "minecraftVersion");
        openAllayApiVersion = require(openAllayApiVersion, "openAllayApiVersion");
        implementedApiVersions = Set.copyOf(implementedApiVersions);
        implementedApiVersions.forEach(version -> require(version, "implemented API version"));
        if (!implementedApiVersions.contains(openAllayApiVersion)) {
            throw new IllegalArgumentException("Implemented APIs must include the legacy primary API");
        }
    }

    public String incompatibility(OpenAllayExtensionDescriptor descriptor) {
        if (!descriptor.loaders().contains(loader)) return "incompatible_loader";
        if (!ExtensionCompatibility.includes(descriptor.minecraftVersionRange(), minecraftVersion)) {
            return "incompatible_game_version";
        }
        if (implementedApiVersions.stream().noneMatch(version -> ExtensionCompatibility.includes(
                descriptor.openAllayApiVersionRange(), version))) {
            return "incompatible_openallay_api";
        }
        return "";
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.strip();
    }
}
