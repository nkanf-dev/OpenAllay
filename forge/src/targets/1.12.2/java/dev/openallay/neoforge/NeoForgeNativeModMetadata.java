package dev.openallay.neoforge;

import dev.openallay.platform.InstalledModMetadata;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.ModMetadata;

/** Detaches actual FML14 mod-container facts without pretending to have modern SPI fields. */
final class NeoForgeNativeModMetadata {
    private NeoForgeNativeModMetadata() {}
    static String productVersion() {
        ModContainer mod = Loader.instance().getIndexedModList().get("openallay");
        return java.util.Objects.requireNonNull(mod, "OpenAllay mod container missing").getVersion();
    }
    static boolean isModLoaded(String id) { return Loader.isModLoaded(id); }
    static List<InstalledModMetadata> installedMods() {
        return Loader.instance().getModList().stream().map(NeoForgeNativeModMetadata::metadata)
                .sorted(Comparator.comparing(InstalledModMetadata::id)).toList();
    }
    private static InstalledModMetadata metadata(ModContainer mod) {
        ModMetadata facts = mod.getMetadata();
        Map<String, String> contacts = new TreeMap<>();
        if (facts.url != null && !facts.url.isBlank()) contacts.put("homepage", facts.url);
        List<String> dependencies = mod.getRequirements().stream()
                .map(dependency -> "required:" + dependency.getLabel() + ":" + dependency.getRangeString())
                .sorted().toList();
        return new InstalledModMetadata(mod.getModId(), mod.getName(), mod.getVersion(),
                facts.description == null ? "" : facts.description,
                facts.authorList == null ? List.of() : List.copyOf(facts.authorList),
                List.of(), contacts, "both", dependencies);
    }
}
