package dev.openallay.json;
import java.net.URI;
import java.util.List;
public final class AttributedRecordFixtures {
    public interface OtherContract { String label(); }
    public interface GetterContract<T> { T values(); }
    public record UriArtifact(String loader, java.net.URI artifact) {
        public UriArtifact { if (loader == null || artifact == null) throw new IllegalArgumentException("required"); }
        public UriArtifact(String loader, String artifact) { this(loader, URI.create(artifact)); }
    }
    public record Boxed(int count) {
        public Boxed(Integer count) { this(count.intValue()); }
    }
    public record NonGetter(List<String> input, String label) implements OtherContract {
        public NonGetter { input = dev.openallay.util.Java8Collections.listCopyOf(input); }
        @Override public List<String> input() { return new java.util.ArrayList<>(input); }
    }
    public record GenuineGetter(List<String> values) implements GetterContract<List<String>> {
        public GenuineGetter { values = dev.openallay.util.Java8Collections.listCopyOf(values); }
        @Override public List<String> values() { return new java.util.ArrayList<>(values); }
    }
}
