package dev.openallay.integration.patchouli;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PatchouliMultiblockStore {
    private volatile Map<String, PatchouliMultiblock> snapshot = dev.openallay.util.Java8Collections.mapOf();

    public void replace(Map<String, PatchouliMultiblock> multiblocks) {
        snapshot = dev.openallay.util.Java8Collections.mapCopyOf(multiblocks);
    }

    public Optional<PatchouliMultiblock> find(String id) {
        return Optional.ofNullable(snapshot.get(id));
    }

    public List<String> ids() {
        return dev.openallay.util.Java8Collections.toList(snapshot.keySet().stream().sorted());
    }
}
