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
    public record Key(String skill, String document, String source, String fingerprint) {}
    public record Range(Key key, int offset, int end, int length) {
        public Range {
            if (offset < 0 || end < offset || end > length) {
                throw new IllegalArgumentException("Invalid retained Skill range");
            }
        }
    }

    private List<Range> ranges = List.of();
    private Coverage coverage = new Coverage(List.of());
    private List<Range> systemRanges = List.of();
    synchronized List<Range> systemRanges() { return systemRanges; }
    synchronized void systemRanges(List<Range> actual) { systemRanges = List.copyOf(actual); }
    // Identity keys avoid hashing the entire JsonPrimitive body on every model turn. Weak references
    // prevent this validation index from keeping discarded Skill plaintext alive after compaction.
    private final Map<Integer, List<Validation>> validations = new HashMap<>();

    public synchronized List<Range> ranges() { return ranges; }

    synchronized void reconcile(List<Range> actual) {
        ranges = List.copyOf(actual);
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
        for (Validation validation : validations.getOrDefault(System.identityHashCode(result), List.of())) {
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

    private record Validation(
            WeakReference<ModelContent.ToolUse> use,
            WeakReference<ModelContent.ToolResult> result,
            Range range,
            SkillCatalogManifest.Document document) {
        boolean expired() { return use.get() == null || result.get() == null; }
    }
}
