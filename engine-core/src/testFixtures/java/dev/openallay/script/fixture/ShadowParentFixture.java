package dev.openallay.script.fixture;

/** Parent declaration and its parameter class belong to the application loader. */
public class ShadowParentFixture {
    private String shadow(ShadowParameter value) { return "parent-parameter"; }
    private boolean parameterClass(Class<?> value) { return value == ShadowParameter.class; }
}
