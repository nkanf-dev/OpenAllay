package dev.openallay.extension;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Independent, default-off user grants for each Extension's declared native capabilities. */
public record ExtensionCapabilityPolicy(Map<String, Set<String>> grants) {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public ExtensionCapabilityPolicy {
        Objects.requireNonNull(grants, "grants");
        TreeMap<String, Set<String>> snapshot = new TreeMap<>();
        grants.forEach((extensionId, capabilities) -> {
            requireExtensionId(extensionId);
            TreeSet<String> scopes = new TreeSet<>();
            for (String capability : Objects.requireNonNull(capabilities, "capabilities")) {
                scopes.add(requireCapabilityId(capability));
            }
            snapshot.put(extensionId, Collections.unmodifiableSet(scopes));
        });
        grants = Collections.unmodifiableMap(snapshot);
    }

    public static ExtensionCapabilityPolicy defaults() {
        return new ExtensionCapabilityPolicy(Map.of());
    }

    public boolean allows(String extensionId, String capabilityId) {
        return grants.getOrDefault(extensionId, Set.of()).contains(capabilityId);
    }

    public ExtensionCapabilityPolicy withGrant(
            String extensionId, String capabilityId, boolean enabled) {
        requireExtensionId(extensionId);
        requireCapabilityId(capabilityId);
        TreeMap<String, Set<String>> replacement = new TreeMap<>(grants);
        TreeSet<String> capabilities = new TreeSet<>(grants.getOrDefault(extensionId, Set.of()));
        if (enabled) capabilities.add(capabilityId);
        else capabilities.remove(capabilityId);
        if (capabilities.isEmpty()) replacement.remove(extensionId);
        else replacement.put(extensionId, capabilities);
        return new ExtensionCapabilityPolicy(replacement);
    }

    public static String requireExtensionId(String value) {
        return requireId(value, "Extension");
    }

    public static String requireCapabilityId(String value) {
        return requireId(value, "Extension capability");
    }

    private static String requireId(String value, String kind) {
        if (value == null || !ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + kind + " ID: " + value);
        }
        return value;
    }
}
