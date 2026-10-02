package dev.openallay.script.fixture;

/** Declared return type deliberately differs from the actual child instance. */
public class JavaAccessParent {
    private String hidden = "parent";
    protected String inherited = "inherited";
    private String parentMethod() { return "parent-method"; }
}
