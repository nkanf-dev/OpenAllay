package dev.openallay.script.fixture;

/** Loaded without initialization, then initialized once through native construct. */
public final class FailingInitializerFixture {
    static { fail(); }
    private static void fail() { throw new IllegalStateException("fixture initialization failed: player_value"); }
    private FailingInitializerFixture() {}
}
