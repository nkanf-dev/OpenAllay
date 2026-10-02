package dev.openallay.script.fixture;

/** Loaded both by the test application loader and a synthetic child loader. */
public final class LoaderFixture {
    public LoaderFixture() {}
    private String identify(LoaderFixture value) { return value == this ? "same-loader" : "other"; }
    private int count(LoaderFixture[] values) { return values.length; }
    private boolean identifyClass(Class<?> value) { return value == getClass(); }
}
