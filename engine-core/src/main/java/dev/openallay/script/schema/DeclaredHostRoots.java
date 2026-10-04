package dev.openallay.script.schema;

/** Marker for root selections that retain their request-scoped declared schema catalog. */
public interface DeclaredHostRoots {
    HostSchemaCatalog schemaCatalog();
}
