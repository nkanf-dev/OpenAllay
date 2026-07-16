package dev.tomewisp.context;

import java.util.List;

public record RegistrySnapshot(List<RegistryEntrySnapshot> entries) {
    public RegistrySnapshot {
        entries = List.copyOf(entries);
    }
}
