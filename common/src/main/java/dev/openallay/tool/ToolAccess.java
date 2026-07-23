package dev.openallay.tool;

public enum ToolAccess {
    READ_ONLY,
    /** Write is confined to one validated OpenAllay-owned store, never an arbitrary path. */
    MANAGED_WRITE
}
