package dev.openallay.world;

/** Selects real native frame content. WORLD never includes a game Screen or 2D HUD. */
public record WorldViewRequest(Target target) {
    public enum Target { WORLD, GAME_UI, ASSOCIATED_UI }

    public WorldViewRequest { java.util.Objects.requireNonNull(target, "target"); }

    public static WorldViewRequest defaults() { return new WorldViewRequest(Target.WORLD); }
}
