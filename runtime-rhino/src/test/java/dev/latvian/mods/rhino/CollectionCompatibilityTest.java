package dev.latvian.mods.rhino;

import dev.latvian.mods.rhino.util.ListCompat;
import java.io.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class CollectionCompatibilityTest {
    private static Object outcome(Supplier<?> operation) {
        try { return operation.get(); } catch (RuntimeException failure) { return failure.getClass(); }
    }
    private static void same(Supplier<?> original, Supplier<?> port) {
        assertEquals(outcome(original), outcome(port));
    }
    private static <E> void listQueries(List<E> expected, List<E> actual) {
        assertEquals(expected, actual); assertEquals(expected.hashCode(), actual.hashCode());
        same(() -> expected.contains(null), () -> actual.contains(null));
        same(() -> expected.indexOf(null), () -> actual.indexOf(null));
        same(() -> expected.lastIndexOf(null), () -> actual.lastIndexOf(null));
        same(() -> expected.containsAll(Arrays.asList((E) null)), () -> actual.containsAll(Arrays.asList((E) null)));
        same(() -> expected.subList(0, 0).contains(null), () -> actual.subList(0, 0).contains(null));
        same(() -> { expected.clear(); return null; }, () -> { actual.clear(); return null; });
        same(() -> expected.remove("absent"), () -> actual.remove("absent"));
        same(() -> expected.removeAll(Collections.emptyList()), () -> actual.removeAll(Collections.emptyList()));
        same(() -> expected.retainAll(expected), () -> actual.retainAll(actual));
        same(() -> expected.addAll(Collections.emptyList()), () -> actual.addAll(Collections.emptyList()));
        same(() -> expected.removeIf(value -> false), () -> actual.removeIf(value -> false));
        same(() -> { expected.replaceAll(value -> value); return null; }, () -> { actual.replaceAll(value -> value); return null; });
        same(() -> { expected.sort(null); return null; }, () -> { actual.sort(null); return null; });
        same(() -> expected.set(-1, null), () -> actual.set(-1, null));
        same(() -> { expected.listIterator().remove(); return null; }, () -> { actual.listIterator().remove(); return null; });
    }
    @Test void listsPreserveNullQueriesNoOpMutationArraysAndSerialization() throws Exception {
        listQueries(List.of(), ListCompat.listOf());
        listQueries(List.of("a"), ListCompat.listOf("a"));
        listQueries(List.of("a", "b", "a"), ListCompat.listOf("a", "b", "a"));
        String[] input = {"a", "b"}; List<String> copy = ListCompat.listOf(input); input[0] = "changed";
        assertEquals(List.of("a", "b"), copy);
        Object[] nested = {"a"}; assertSame(nested, ListCompat.<Object>listOf((Object) nested).get(0));
        same(() -> List.of("a", null), () -> ListCompat.listOf("a", null));
        same(() -> List.copyOf(Arrays.asList("a", null)), () -> ListCompat.copyList(Arrays.asList("a", null)));
        assertEquals(copy, roundTrip(copy));
        assertTrue(copy instanceof RandomAccess);
    }
    private static void setQueries(Set<String> expected, Set<String> actual) {
        assertEquals(expected, actual); assertEquals(expected.hashCode(), actual.hashCode());
        same(() -> expected.contains(null), () -> actual.contains(null));
        same(() -> expected.containsAll(Arrays.asList((String) null)), () -> actual.containsAll(Arrays.asList((String) null)));
        same(() -> { expected.clear(); return null; }, () -> { actual.clear(); return null; });
        same(() -> expected.remove("absent"), () -> actual.remove("absent"));
        same(() -> expected.removeAll(Set.of()), () -> actual.removeAll(Set.of()));
        same(() -> expected.retainAll(expected), () -> actual.retainAll(actual));
        same(() -> expected.removeIf(v -> false), () -> actual.removeIf(v -> false));
        same(() -> { expected.iterator().remove(); return null; }, () -> { actual.iterator().remove(); return null; });
    }
    @Test void setsPreserveNullDuplicatesMutationAndCopies() throws Exception {
        setQueries(Set.of(), ListCompat.setOf()); setQueries(Set.of("a"), ListCompat.setOf("a"));
        setQueries(Set.of("a", "b", "c"), ListCompat.setOf("a", "b", "c"));
        same(() -> Set.of("a", "a"), () -> ListCompat.setOf("a", "a"));
        same(() -> Set.of("a", null), () -> ListCompat.setOf("a", null));
        assertEquals(Set.copyOf(List.of("a", "a")), ListCompat.copySet(List.of("a", "a")));
        assertEquals(ListCompat.setOf("a"), roundTrip(ListCompat.setOf("a")));
    }
    private static void mapQueries(Map<String, String> expected, Map<String, String> actual) {
        assertEquals(expected, actual); assertEquals(expected.hashCode(), actual.hashCode());
        same(() -> expected.get(null), () -> actual.get(null));
        same(() -> expected.getOrDefault(null, "fallback"), () -> actual.getOrDefault(null, "fallback"));
        same(() -> expected.containsKey(null), () -> actual.containsKey(null));
        same(() -> expected.containsValue(null), () -> actual.containsValue(null));
        same(() -> expected.keySet().contains(null), () -> actual.keySet().contains(null));
        same(() -> expected.values().contains(null), () -> actual.values().contains(null));
        same(() -> expected.entrySet().contains(null), () -> actual.entrySet().contains(null));
        same(() -> expected.entrySet().remove(null), () -> actual.entrySet().remove(null));
        same(() -> expected.entrySet().removeAll(Set.of()), () -> actual.entrySet().removeAll(Set.of()));
        same(() -> { expected.entrySet().clear(); return null; }, () -> { actual.entrySet().clear(); return null; });
        same(() -> expected.values().remove("absent"), () -> actual.values().remove("absent"));
        same(() -> expected.keySet().remove("absent"), () -> actual.keySet().remove("absent"));
        same(() -> expected.remove("absent"), () -> actual.remove("absent"));
        same(() -> { expected.clear(); return null; }, () -> { actual.clear(); return null; });
        same(() -> { expected.putAll(Map.of()); return null; }, () -> { actual.putAll(Map.of()); return null; });
        same(() -> expected.putIfAbsent("absent", "x"), () -> actual.putIfAbsent("absent", "x"));
        same(() -> expected.replace("absent", "x"), () -> actual.replace("absent", "x"));
        same(() -> expected.computeIfAbsent("a", key -> "x"), () -> actual.computeIfAbsent("a", key -> "x"));
        same(() -> { expected.replaceAll((k, v) -> v); return null; }, () -> { actual.replaceAll((k, v) -> v); return null; });
        if (!expected.isEmpty()) {
            same(() -> expected.entrySet().iterator().next().setValue("x"), () -> actual.entrySet().iterator().next().setValue("x"));
        }
    }
    @Test void mapsPreserveViewNullRulesEntriesNoOpMutationsAndCopies() throws Exception {
        mapQueries(Map.of(), ListCompat.mapOf()); mapQueries(Map.of("a", "b"), ListCompat.mapOf("a", "b"));
        mapQueries(Map.of("a", "b", "c", "d"), ListCompat.mapOf("a", "b", "c", "d"));
        same(() -> Map.of("a", "b", "a", "c"), () -> ListCompat.mapOf("a", "b", "a", "c"));
        same(() -> Map.entry(null, "x"), () -> ListCompat.entry(null, "x"));
        same(() -> Map.ofEntries((Map.Entry<String,String>) null), () -> ListCompat.mapOfEntries((Map.Entry<String,String>) null));
        Map.Entry<String, String> mutable = new AbstractMap.SimpleEntry<>("a", "b");
        Map<String, String> copied = ListCompat.mapOfEntries(mutable); mutable.setValue("changed");
        assertEquals(Map.of("a", "b"), copied); assertEquals(copied, roundTrip(copied));
        assertFalse(ListCompat.entry("a", "b") instanceof Serializable);
        assertEquals(Map.entry("a", "b"), ListCompat.entry("a", "b"));
        assertEquals("a=b", ListCompat.entry("a", "b").toString());
    }
    @Test void repetitionKeepsUtf16AndEdgeRules() {
        for (String value : List.of("", "x", "ab", "\uD83D\uDE00")) {
            for (int count : new int[]{0, 1, 4, -1}) {
                same(() -> value.repeat(count), () -> ListCompat.repeat(value, count));
            }
        }
    }
    private static Object roundTrip(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) { out.writeObject(value); }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { return in.readObject(); }
    }
}
