package dev.openallay.tool;

public enum ToolAccess {
    READ_ONLY,
    /**
     * A default-off player-enabled action whose final authority remains with Minecraft.
     *
     * <p>This classification is eligible for the player-client Tool route, but never for
     * server-side remote export.
     */
    EXPERIMENTAL_ACTION,
    /** Write is confined to one validated OpenAllay-owned store, never an arbitrary path. */
    MANAGED_WRITE
}
