package dev.openallay.script.fixture;

/** Its own parameter class has the parent's parameter binary name but another identity. */
public final class ShadowChildFixture extends ShadowParentFixture {
    public ShadowChildFixture() {}
    public Object shadowParameter() { return new ShadowParameter(); }
}
