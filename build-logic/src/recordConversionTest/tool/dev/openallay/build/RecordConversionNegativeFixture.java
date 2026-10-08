package dev.openallay.build;

import java.nio.file.Paths;
import java.util.Set;

public final class RecordConversionNegativeFixture {
    private RecordConversionNegativeFixture() {}
    public static void main(String[] args) throws Exception {
        rejects("class Holder { void call() { record Local(int value) {} } }", "Holder.Local");
        rejects("record Bad(@Unknown String value) {}", "Bad");
        rejects("record Bad(String... values) {}", "Bad");
        rejects("record Bad(int value) { public static class ValueSchemaProvider {} }", "Bad");
        rejects("record Bad(int value) { public Bad(int value) { this.value = value; } }", "Bad");
        rejects("record Present(int value) {}", "Absent");
        rejects("interface Contract { int value(); } record Bad(int value) implements Contract { @Override public int value() { return value; } }", "Bad");
        rejects("record Bad(int value) implements ExternalMarker { @Override public int value() { return value; } }", "Bad");
        rejects("interface Contract { default int value() { return 1; } } record Bad(int value) implements Contract { @Override public int value() { return value; } }", "Bad");
        System.out.println("PASS fail-closed unsupported record forms");
    }
    private static void rejects(String source, String selected) throws Exception {
        try { RecordValueSourceConverter.convert(Paths.get("Fixture.java").toAbsolutePath(), source, Set.of(selected)); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Unsupported form accepted: " + source);
    }
}
