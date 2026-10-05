package dev.openallay.neoforge;

import dev.openallay.platform.InstalledModMetadata;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

/** Detaches installed mod metadata at the selected loader SPI boundary. */
final class NeoForgeNativeModMetadata {
    private NeoForgeNativeModMetadata() {}

    static String productVersion() {
        return ModList.get().getModContainerById("openallay").orElseThrow()
                .getModInfo().getVersion().toString();
    }

    static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    static List<InstalledModMetadata> installedMods() {
        return ModList.get().getMods().stream()
                .map(NeoForgeNativeModMetadata::metadata)
                .sorted(Comparator.comparing(InstalledModMetadata::id))
                .toList();
    }

    private static InstalledModMetadata metadata(IModInfo mod) {
        Map<String, String> contacts = new TreeMap<>();
        mod.getModURL().ifPresent(url -> contacts.put("homepage", url.toString()));
        mod.getUpdateURL().ifPresent(url -> contacts.put("update", url.toString()));
        List<String> dependencies = mod.getDependencies().stream()
                .map(dependency -> (dependency.isMandatory() ? "required" : "optional")
                        + ":" + dependency.getModId() + ":"
                        + dependency.getVersionRange() + ":"
                        + dependency.getSide().name().toLowerCase(java.util.Locale.ROOT))
                .sorted()
                .toList();
        String license = mod.getOwningFile().getLicense();
        return new InstalledModMetadata(
                mod.getModId(),
                mod.getDisplayName(),
                mod.getVersion().toString(),
                mod.getDescription(),
                configStrings(mod, "authors"),
                license == null || license.isBlank() ? List.of() : List.of(license),
                contacts,
                "both",
                dependencies);
    }

    private static List<String> configStrings(IModInfo mod, String key) {
        Object value = mod.getConfig().getConfigElement(key).orElse(null);
        if (value instanceof String text && !text.isBlank()) {
            return List.of(text);
        }
        if (value instanceof List<?> values) {
            List<String> result = new ArrayList<>();
            for (Object item : values) {
                if (item != null && !item.toString().isBlank()) {
                    result.add(item.toString());
                }
            }
            return List.copyOf(result);
        }
        return List.of();
    }
}
