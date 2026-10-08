package dev.openallay.build;

import java.nio.file.Paths;
import java.util.Set;

/** Compiler-tree shape checks; genuine owner runtime proof is separate. */
public final class RecordConstructorTypeFixture {
    public static void main(String[] args) throws Exception {
        accepts("record Box(String label, Long value) { public Box(String label, long value) { this(label, Long.valueOf(value)); } public Box { if (value != null && value < 0) throw new IllegalArgumentException(); } }", "Box", "Box(String label, long value)");
        accepts("record Box(String label, long value) { public Box(String label, Long value) { this(label, value.longValue()); } public Box {} }", "Box", "Box(String label, Long value)");
        accepts("record Box(int value) { public Box(long value) { this((int) value); } public Box {} }", "Box", "Box(long value)");
        rejects("record Box(String label, Long value) { public Box(String label, Long value) { this.label = label; this.value = value; } }", "Box");
        rejects("record Box(String label, Long value) { public Box(String label, java.lang.Long value) { this.label = label; this.value = value; } }", "Box");
        rejects("record Box(java.util.List<String> value) { public Box(java.util.List< String > value) { this.value = value; } }", "Box");
        rejects("record Box(String value) { public Box(Integer value) { this(value.toString()); } public Box {} }", "Box");
        System.out.println("PASS constructor parameter types, primitive boxing overloads, canonical and ambiguous fail-closed forms");
    }
    private static void accepts(String source, String selected, String overload) throws Exception {
        String result = RecordValueSourceConverter.convert(Paths.get("Fixture.java").toAbsolutePath(), source, Set.of(selected));
        if (!result.contains(overload) || !result.contains("ValueSchemaProvider")) throw new AssertionError("Actual overload/provider absent");
    }
    private static void rejects(String source, String selected) throws Exception {
        try { RecordValueSourceConverter.convert(Paths.get("Fixture.java").toAbsolutePath(), source, Set.of(selected)); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Canonical or ambiguous declaration admitted");
    }
}
