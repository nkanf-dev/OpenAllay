package dev.openallay.rhino.fixture;
import dev.latvian.mods.rhino.util.ListCompat;
import java.util.*;
public final class RhinoJava8CollectionsFixture {
    interface Action { void run(); }
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    static void fails(Class<? extends Throwable> kind, Action action) {
        checks++; try { action.run(); } catch (Throwable failure) {
            if (kind.isInstance(failure)) return;
            throw new AssertionError("Expected " + kind, failure);
        } throw new AssertionError("Expected " + kind);
    }
    public static void main(String[] args) {
        if (!"1.8".equals(System.getProperty("java.specification.version"))) throw new AssertionError("Require actual Java8");
        for (List<String> list : Arrays.asList(ListCompat.<String>listOf(), ListCompat.listOf("a"), ListCompat.listOf("a", "b", "a"))) {
            fails(NullPointerException.class, () -> list.contains(null));
            fails(NullPointerException.class, () -> list.indexOf(null));
            fails(NullPointerException.class, () -> list.lastIndexOf(null));
            fails(NullPointerException.class, () -> list.subList(0, 0).contains(null));
            fails(UnsupportedOperationException.class, () -> list.clear());
            fails(UnsupportedOperationException.class, () -> list.remove("absent"));
            fails(UnsupportedOperationException.class, () -> list.removeIf(v -> false));
            fails(UnsupportedOperationException.class, () -> list.replaceAll(v -> v));
            fails(UnsupportedOperationException.class, () -> list.sort(null));
            fails(UnsupportedOperationException.class, () -> list.iterator().remove());
        }
        String[] source = {"a", "b"}; List<String> copied = ListCompat.listOf(source); source[0] = "changed";
        check(copied.equals(Arrays.asList("a", "b")));
        Object[] nested = {"a"}; check(ListCompat.<Object>listOf((Object) nested).get(0) == nested);
        for (Set<String> set : Arrays.asList(ListCompat.<String>setOf(), ListCompat.setOf("a"), ListCompat.setOf("a", "b", "c"))) {
            fails(NullPointerException.class, () -> set.contains(null));
            fails(UnsupportedOperationException.class, () -> set.clear());
            fails(UnsupportedOperationException.class, () -> set.remove("absent"));
            fails(UnsupportedOperationException.class, () -> set.removeIf(v -> false));
        }
        fails(IllegalArgumentException.class, () -> ListCompat.setOf("a", "a"));
        check(ListCompat.copySet(Arrays.asList("a", "a")).size() == 1);
        for (Map<String,String> map : Arrays.asList(ListCompat.<String,String>mapOf(), ListCompat.mapOf("a", "b"), ListCompat.mapOf("a", "b", "c", "d"))) {
            fails(NullPointerException.class, () -> map.get(null));
            fails(NullPointerException.class, () -> map.containsKey(null));
            fails(NullPointerException.class, () -> map.containsValue(null));
            fails(NullPointerException.class, () -> map.keySet().contains(null));
            fails(NullPointerException.class, () -> map.values().contains(null));
            fails(UnsupportedOperationException.class, () -> map.clear());
            fails(UnsupportedOperationException.class, () -> map.remove("absent"));
            fails(UnsupportedOperationException.class, () -> map.computeIfAbsent("a", k -> "x"));
            fails(UnsupportedOperationException.class, () -> map.replaceAll((k,v) -> v));
            if (!map.isEmpty()) fails(UnsupportedOperationException.class, () -> map.entrySet().iterator().next().setValue("x"));
        }
        check(!ListCompat.mapOf().entrySet().contains(null));
        fails(NullPointerException.class, () -> ListCompat.mapOf("a", "b").entrySet().contains(null));
        check(!ListCompat.mapOf("a", "b", "c", "d").entrySet().contains(null));
        fails(IllegalArgumentException.class, () -> ListCompat.mapOf("a", "b", "a", "c"));
        fails(NullPointerException.class, () -> ListCompat.entry(null, "x"));
        fails(UnsupportedOperationException.class, () -> ListCompat.entry("a", "b").setValue("c"));
        check(ListCompat.repeat("ab", 3).equals("ababab"));
        check(ListCompat.repeat("", Integer.MAX_VALUE).equals(""));
        fails(IllegalArgumentException.class, () -> ListCompat.repeat("", -1));
        fails(OutOfMemoryError.class, () -> ListCompat.repeat("ab", Integer.MAX_VALUE));
        System.out.println("PASS canonical Rhino collection helper actualJava8 " + System.getProperty("java.version") + " checks=" + checks);
    }
}
