package dev.openallay.skill;

import dev.openallay.model.ModelContent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/** Session-owned facts about actual retained plaintext. Never owns document text or model messages. */
public final class RetainedSkillContext {
    @dev.openallay.value.ValueType(Key.ValueSchemaProvider.class)
public static final class Key {
    private final String skill;
    private final String document;
    private final String source;
    private final String fingerprint;
    public Key(String skill, String document, String source, String fingerprint) {
        this.skill = skill;
        this.document = document;
        this.source = source;
        this.fingerprint = fingerprint;
    }
    public String skill() { return skill; }
    public String document() { return document; }
    public String source() { return source; }
    public String fingerprint() { return fingerprint; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Key)) return false;
        Key that = (Key) other;
        return java.util.Objects.equals(skill, that.skill) && java.util.Objects.equals(document, that.document) && java.util.Objects.equals(source, that.source) && java.util.Objects.equals(fingerprint, that.fingerprint);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(skill);
        hash = 31 * hash + java.util.Objects.hashCode(document);
        hash = 31 * hash + java.util.Objects.hashCode(source);
        hash = 31 * hash + java.util.Objects.hashCode(fingerprint);
        return hash;
    }
    @Override public String toString() { return "Key[skill=" + skill + ", document=" + document + ", source=" + source + ", fingerprint=" + fingerprint + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Key> schema() {
            return new dev.openallay.value.ValueSchema<>(Key.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Key>>asList(new dev.openallay.value.ValueSchema.Component<>(Key.class, "skill", Key::skill), new dev.openallay.value.ValueSchema.Component<>(Key.class, "document", Key::document), new dev.openallay.value.ValueSchema.Component<>(Key.class, "source", Key::source), new dev.openallay.value.ValueSchema.Component<>(Key.class, "fingerprint", Key::fingerprint)), arguments -> new Key((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3]));
        }
    }
}
    @dev.openallay.value.ValueType(Range.ValueSchemaProvider.class)
public static final class Range {
    private final Key key;
    private final int offset;
    private final int end;
    private final int length;
    public Range(Key key, int offset, int end, int length) {

            if (offset < 0 || end < offset || end > length) {
                throw new IllegalArgumentException("Invalid retained Skill range");
            }

        this.key = key;
        this.offset = offset;
        this.end = end;
        this.length = length;
    }
    public Key key() { return key; }
    public int offset() { return offset; }
    public int end() { return end; }
    public int length() { return length; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Range)) return false;
        Range that = (Range) other;
        return java.util.Objects.equals(key, that.key) && offset == that.offset && end == that.end && length == that.length;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(key);
        hash = 31 * hash + Integer.hashCode(offset);
        hash = 31 * hash + Integer.hashCode(end);
        hash = 31 * hash + Integer.hashCode(length);
        return hash;
    }
    @Override public String toString() { return "Range[key=" + key + ", offset=" + offset + ", end=" + end + ", length=" + length + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Range> schema() {
            return new dev.openallay.value.ValueSchema<>(Range.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Range>>asList(new dev.openallay.value.ValueSchema.Component<>(Range.class, "key", Range::key), new dev.openallay.value.ValueSchema.Component<>(Range.class, "offset", Range::offset), new dev.openallay.value.ValueSchema.Component<>(Range.class, "end", Range::end), new dev.openallay.value.ValueSchema.Component<>(Range.class, "length", Range::length)), arguments -> new Range((Key) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (Integer) arguments[3]));
        }
    }
}

    private List<Range> ranges = dev.openallay.util.Java8Collections.listOf();
    private Coverage coverage = new Coverage(dev.openallay.util.Java8Collections.listOf());
    private List<Range> systemRanges = dev.openallay.util.Java8Collections.listOf();
    synchronized List<Range> systemRanges() { return systemRanges; }
    synchronized void systemRanges(List<Range> actual) { systemRanges = dev.openallay.util.Java8Collections.listCopyOf(actual); }
    // Identity keys avoid hashing the entire JsonPrimitive body on every model turn. Weak references
    // prevent this validation index from keeping discarded Skill plaintext alive after compaction.
    private final Map<Integer, List<Validation>> validations = new HashMap<>();

    public synchronized List<Range> ranges() { return ranges; }

    synchronized void reconcile(List<Range> actual) {
        ranges = dev.openallay.util.Java8Collections.listCopyOf(actual);
        coverage = new Coverage(actual);
        validations.values().forEach(entries -> entries.removeIf(Validation::expired));
        validations.values().removeIf(List::isEmpty);
    }

    public synchronized boolean contains(Key key, int offset, int end) {
        return coverage.contains(key, offset, end);
    }

    /** A union of validated plaintext ranges. Receipts alone must never enter this index. */
    static final class Coverage {
        private final Map<Key, NavigableMap<Integer, Integer>> byKey = new HashMap<>();

        Coverage(List<Range> actual) { addAll(actual); }

        void addAll(List<Range> actual) { actual.forEach(this::add); }

        void add(Range range) {
            java.util.NavigableMap<java.lang.Integer, java.lang.Integer> intervals = byKey.computeIfAbsent(range.key(), ignored -> new TreeMap<>());
            int start = range.offset();
            int end = range.end();
            java.util.Map.Entry<java.lang.Integer, java.lang.Integer> previous = intervals.floorEntry(start);
            if (previous != null && previous.getValue() >= start) {
                start = previous.getKey();
                end = Math.max(end, previous.getValue());
                intervals.remove(previous.getKey());
            }
            java.util.Map.Entry<java.lang.Integer, java.lang.Integer> next = intervals.ceilingEntry(start);
            while (next != null && next.getKey() <= end) {
                end = Math.max(end, next.getValue());
                intervals.remove(next.getKey());
                next = intervals.ceilingEntry(start);
            }
            intervals.put(start, end);
        }

        boolean contains(Range requested) {
            return contains(requested.key(), requested.offset(), requested.end());
        }

        boolean contains(Key key, int offset, int end) {
            java.util.NavigableMap<java.lang.Integer, java.lang.Integer> intervals = byKey.get(key);
            if (intervals == null) return false;
            java.util.Map.Entry<java.lang.Integer, java.lang.Integer> start = intervals.floorEntry(offset);
            return start != null && start.getValue() >= end;
        }
    }

    synchronized Range validated(ModelContent.ToolUse use, ModelContent.ToolResult result,
            SkillCatalogManifest.Document current) {
        for (Validation validation : validations.getOrDefault(System.identityHashCode(result), dev.openallay.util.Java8Collections.listOf())) {
            if (validation.use().get() == use && validation.result().get() == result
                    && validation.document().equals(current)) return validation.range();
        }
        return null;
    }

    synchronized void remember(ModelContent.ToolUse use, ModelContent.ToolResult result, Range range,
            SkillCatalogManifest.Document document) {
        validations.computeIfAbsent(System.identityHashCode(result), ignored -> new ArrayList<>())
                .add(new Validation(new WeakReference<>(use), new WeakReference<>(result), range, document));
    }

    synchronized void retainValidations(java.util.Set<ModelContent.ToolResult> active) {
        validations.values().forEach(entries -> entries.removeIf(validation ->
                validation.expired() || !active.contains(validation.result().get())));
        validations.values().removeIf(List::isEmpty);
    }

    synchronized int validationCount() {
        return validations.values().stream().mapToInt(List::size).sum();
    }

    @dev.openallay.value.ValueType(Validation.ValueSchemaProvider.class)
private static final class Validation {
    private final WeakReference<ModelContent.ToolUse> use;
    private final WeakReference<ModelContent.ToolResult> result;
    private final Range range;
    private final SkillCatalogManifest.Document document;
    private Validation(WeakReference<ModelContent.ToolUse> use, WeakReference<ModelContent.ToolResult> result, Range range, SkillCatalogManifest.Document document) {
        this.use = use;
        this.result = result;
        this.range = range;
        this.document = document;
    }
    public WeakReference<ModelContent.ToolUse> use() { return use; }
    public WeakReference<ModelContent.ToolResult> result() { return result; }
    public Range range() { return range; }
    public SkillCatalogManifest.Document document() { return document; }
boolean expired() { return use.get() == null || result.get() == null; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Validation)) return false;
        Validation that = (Validation) other;
        return java.util.Objects.equals(use, that.use) && java.util.Objects.equals(result, that.result) && java.util.Objects.equals(range, that.range) && java.util.Objects.equals(document, that.document);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(use);
        hash = 31 * hash + java.util.Objects.hashCode(result);
        hash = 31 * hash + java.util.Objects.hashCode(range);
        hash = 31 * hash + java.util.Objects.hashCode(document);
        return hash;
    }
    @Override public String toString() { return "Validation[use=" + use + ", result=" + result + ", range=" + range + ", document=" + document + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Validation> schema() {
            return new dev.openallay.value.ValueSchema<>(Validation.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Validation>>asList(new dev.openallay.value.ValueSchema.Component<>(Validation.class, "use", Validation::use), new dev.openallay.value.ValueSchema.Component<>(Validation.class, "result", Validation::result), new dev.openallay.value.ValueSchema.Component<>(Validation.class, "range", Validation::range), new dev.openallay.value.ValueSchema.Component<>(Validation.class, "document", Validation::document)), arguments -> new Validation((WeakReference) arguments[0], (WeakReference) arguments[1], (Range) arguments[2], (SkillCatalogManifest.Document) arguments[3]));
        }
    }
}
}
