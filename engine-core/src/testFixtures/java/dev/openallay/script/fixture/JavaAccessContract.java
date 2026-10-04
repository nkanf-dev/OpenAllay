package dev.openallay.script.fixture;

public interface JavaAccessContract {
    default String defaultMethod(String value) { return "default:" + value; }
}
