package dev.openallay.api.extension;

/** Narrow host injection, not a generic service locator or authority grant. */
public interface ExtensionHost {
    ExtensionEnvironment environment();
    /** May be unavailable; pure-JavaScript and Skill contributions need not request it. */
    MinecraftWorldAccess minecraftWorldAccess();
}
