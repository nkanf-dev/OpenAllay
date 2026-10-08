package dev.openallay.json;

import dev.openallay.agent.tool.ToolDescription;
import dev.openallay.agent.tool.ToolOptional;
import java.util.List;

/** Original modern records are fixture inputs, not a production engine copy. */
public final class RecordFixtureValues {
    private RecordFixtureValues() {}
    public interface InterfaceOwner {
        record MemberValue(int count) {}
    }
    private record PrivateValue(String value) {}
    public static Object privateValue(String value) { return new PrivateValue(value); }
    public interface Marker {
        static void require(java.util.List<String> values) {
            if (values == null) throw new IllegalArgumentException("marker input required");
        }
    }
    public record MarkerCopy(List<String> input) implements Marker {
        public MarkerCopy { Marker.require(input); input = dev.openallay.util.Java8Collections.listCopyOf(input); }
        @Override public List<String> input() { return new java.util.ArrayList<>(input); }
    }
    public record Empty() {}
    public record Numbers(boolean flag, byte small, short medium, char letter, int count,
            long large, float fraction, double precise, String nullable) {}
    public record Normalized(@ToolDescription("Name") String name, @ToolOptional List<String> values) {
        public Normalized {
            if (name == null || name.isEmpty()) throw new IllegalArgumentException("name required");
            name = name.trim();
            values = dev.openallay.util.Java8Collections.listCopyOf(values);
        }
        public Normalized(String name) { this(name, dev.openallay.util.Java8Collections.<String>listOf()); }
    }
    public record Generic<T>(T value, List<T> history) {}
    public record OverrideValue(List<String> values) {
        @Override public List<String> values() { return new java.util.ArrayList<>(values); }
    }
    public record Custom(String value) {
        @Override public int hashCode() { return 7; }
        @Override public String toString() { return "custom:" + value; }
    }
}
