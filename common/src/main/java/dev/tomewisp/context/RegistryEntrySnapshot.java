package dev.tomewisp.context;

public record RegistryEntrySnapshot(
        String id, String kind, String displayName, String namespace, String provenance) {
    public RegistryEntrySnapshot {
        id = ContextValidation.identifier(id, "id");
        kind = ContextValidation.nonBlank(kind, "kind");
        displayName = ContextValidation.nonBlank(displayName, "displayName");
        namespace = ContextValidation.nonBlank(namespace, "namespace");
        provenance = ContextValidation.identifier(provenance, "provenance");
    }
}
