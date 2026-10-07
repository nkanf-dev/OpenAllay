import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compares the immutable factory contracts without a compile-time helper dependency. */
public final class ImmutableFactoriesGolden {
    private static Object call(String kind, Object... arguments) throws Exception {
        boolean upstream = Boolean.getBoolean("golden.upstream");
        Class<?> owner = upstream ? (kind.equals("list") ? List.class : kind.equals("set") ? Set.class : Map.class)
                : Class.forName("org.commonmark.internal.util.Java8Collections");
        String name = upstream ? "of" : kind + "Of";
        try {
            if (kind.equals("map")) {
                return arguments.length == 0 ? owner.getMethod(name).invoke(null)
                        : owner.getMethod(name, Object.class, Object.class).invoke(null, arguments);
            }
            return owner.getMethod(name, Object[].class).invoke(null, new Object[] { arguments });
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw error;
        }
    }
    private static void rejected(String label, Runnable action, Class<?> expected) {
        try { action.run(); throw new AssertionError("accepted " + label); }
        catch (RuntimeException error) {
            if (!expected.isInstance(error)) throw error;
            System.out.println(label + "=" + error.getClass().getName());
        }
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        List<String> list = (List<String>) call("list", "a", "a", "b");
        if (!list.equals(Arrays.asList("a", "a", "b"))) throw new AssertionError("list order/duplicates");
        Object[] array = new Object[] { "a", "b" };
        List<String> copied = (List<String>) call("list", array);
        array[0] = "changed";
        if (!copied.equals(Arrays.asList("a", "b"))) throw new AssertionError("array alias");
        Set<String> set = (Set<String>) call("set", "a", "b");
        if (set.size() != 2 || !set.containsAll(Arrays.asList("a", "b"))) throw new AssertionError("set values");
        Map<String, String> map = (Map<String, String>) call("map", "key", "value");
        if (!"value".equals(map.get("key")) || map.size() != 1) throw new AssertionError("map values");
        List<String> emptyList = (List<String>) call("list");
        Set<String> emptySet = (Set<String>) call("set");
        Map<String, String> emptyMap = (Map<String, String>) call("map");
        rejected("list-null-query", () -> list.contains(null), NullPointerException.class);
        rejected("empty-list-null-query", () -> emptyList.contains(null), NullPointerException.class);
        rejected("set-null-query", () -> set.contains(null), NullPointerException.class);
        rejected("empty-set-null-query", () -> emptySet.contains(null), NullPointerException.class);
        rejected("map-null-query", () -> map.get(null), NullPointerException.class);
        rejected("empty-map-null-query", () -> emptyMap.get(null), NullPointerException.class);
        rejected("list-set", () -> list.set(0, "x"), UnsupportedOperationException.class);
        rejected("list-clear", () -> emptyList.clear(), UnsupportedOperationException.class);
        rejected("list-remove-missing", () -> emptyList.remove("missing"), UnsupportedOperationException.class);
        rejected("set-remove-missing", () -> emptySet.remove("missing"), UnsupportedOperationException.class);
        rejected("map-remove-missing", () -> emptyMap.remove("missing"), UnsupportedOperationException.class);
        rejected("map-clear", () -> emptyMap.clear(), UnsupportedOperationException.class);
        rejected("map-putAll-empty", () -> emptyMap.putAll(Collections.emptyMap()), UnsupportedOperationException.class);
        for (String kind : Arrays.asList("list", "set", "map")) {
            try {
                if (kind.equals("map")) call(kind, "key", null); else call(kind, (Object) null);
                throw new AssertionError("null accepted " + kind);
            } catch (NullPointerException expected) { System.out.println(kind + "-null-element=" + expected.getClass().getName()); }
        }
        try { call("set", "a", "a"); throw new AssertionError("duplicate set element accepted"); }
        catch (IllegalArgumentException expected) { System.out.println("set-duplicate=" + expected.getClass().getName()); }
        System.out.println("IMMUTABLE_FACTORIES_PASS");
    }
}
