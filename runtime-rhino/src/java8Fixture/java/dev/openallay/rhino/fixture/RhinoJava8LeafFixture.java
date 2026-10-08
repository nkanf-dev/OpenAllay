package dev.openallay.rhino.fixture;

import dev.latvian.mods.rhino.MethodSignature;
import dev.latvian.mods.rhino.util.ListCompat;
import dev.latvian.mods.rhino.util.Possible;
import java.lang.reflect.Method;
import java.util.*;

public final class RhinoJava8LeafFixture {
    private interface Action { void run() throws Exception; }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    private static void fails(Class<? extends Throwable> expected, Action action) throws Exception {
        try { action.run(); } catch (Throwable failure) {
            if (expected.isInstance(failure)) return;
            throw new AssertionError("Expected " + expected + ", got " + failure, failure);
        }
        throw new AssertionError("Expected " + expected);
    }
    public static void noArgs() {}
    public static void oneArg(String value) {}
    public static void main(String[] args) throws Exception {
        if (!System.getProperty("java.specification.version").equals("1.8")) {
            throw new AssertionError("Fixture must execute on genuine Java8");
        }
        List<String> indexed = new ArrayList<String>(Arrays.asList("a", "b"));
        check(ListCompat.getFirst(indexed).equals("a") && ListCompat.getLast(indexed).equals("b"));
        check(ListCompat.removeLast(indexed).equals("b"));
        ListCompat.addFirst(indexed, "z");
        check(ListCompat.removeFirst(indexed).equals("z"));
        final LinkedList<String> deque = new LinkedList<String>(Arrays.asList("a", "b"));
        check(ListCompat.removeFirst(deque).equals("a"));
        check(ListCompat.removeLast(deque).equals("b"));
        fails(NoSuchElementException.class, () -> ListCompat.getFirst(deque));
        fails(NoSuchElementException.class, () -> ListCompat.getLast(deque));
        fails(NoSuchElementException.class, () -> ListCompat.removeFirst(deque));
        fails(NoSuchElementException.class, () -> ListCompat.removeLast(deque));
        final List<String> empty = new ArrayList<String>();
        fails(NoSuchElementException.class, () -> ListCompat.getFirst(empty));
        fails(NoSuchElementException.class, () -> ListCompat.getLast(empty));
        fails(NoSuchElementException.class, () -> ListCompat.removeFirst(empty));
        fails(NoSuchElementException.class, () -> ListCompat.removeLast(empty));
        final List<String> fixed = Arrays.asList("x", "y");
        fails(UnsupportedOperationException.class, () -> ListCompat.removeLast(fixed));
        fails(UnsupportedOperationException.class, () -> ListCompat.addFirst(fixed, "z"));
        String[] array = {"a", "b"};
        List<String> frozen = ListCompat.listOf(array);
        array[0] = "changed";
        check(frozen.equals(Arrays.asList("a", "b")));
        fails(UnsupportedOperationException.class, () -> frozen.set(0, "c"));
        fails(NullPointerException.class, () -> ListCompat.listOf("a", null));
        List<String> original = new ArrayList<String>(Arrays.asList("a"));
        List<String> copied = ListCompat.copyList(original); original.add("b");
        check(copied.size() == 1 && ListCompat.listOf("a", "a").size() == 2);
        check(ListCompat.copySet(Arrays.asList("a", "a")).size() == 1);
        fails(IllegalArgumentException.class, () -> ListCompat.setOf("a", "a"));
        fails(NullPointerException.class, () -> ListCompat.setOf("a", null));
        Set<String> set = ListCompat.setOf("a", "b");
        fails(UnsupportedOperationException.class, () -> set.add("c"));
        Map<String, String> map = new LinkedHashMap<String, String>(); map.put("a", "b");
        Map<String, String> frozenMap = ListCompat.copyMap(map); map.put("a", "changed");
        check(frozenMap.get("a").equals("b"));
        fails(UnsupportedOperationException.class, () -> frozenMap.put("c", "d"));
        map.put(null, "x"); fails(NullPointerException.class, () -> ListCompat.copyMap(map));
        map.remove(null); map.put("x", null);
        fails(NullPointerException.class, () -> ListCompat.copyMap(map));
        check(ListCompat.repeat("abc", 3).equals("abcabcabc"));
        check(ListCompat.repeat("", Integer.MAX_VALUE).equals(""));
        check(ListCompat.repeat("a", 0).equals(""));
        check(ListCompat.repeat("\uD83D\uDE00", 2).length() == 4);
        fails(IllegalArgumentException.class, () -> ListCompat.repeat("", -1));
        fails(OutOfMemoryError.class, () -> ListCompat.repeat("ab", Integer.MAX_VALUE));
        Class<?>[] parameters = {String.class};
        MethodSignature signature = new MethodSignature("oneArg", parameters);
        check(signature.name().equals("oneArg") && signature.args() == parameters);
        check(signature.equals(new MethodSignature("oneArg", new Class<?>[]{String.class})));
        check(!signature.equals(new MethodSignature("different", parameters)) && !signature.equals(null));
        check(signature.hashCode() == ("oneArg".hashCode() ^ 1));
        check(signature.toString().equals("MethodSignature[name=oneArg, args=" + parameters + "]"));
        MethodSignature reflected = new MethodSignature(RhinoJava8LeafFixture.class.getMethod("oneArg", String.class));
        check(signature.equals(reflected));
        check(new MethodSignature(RhinoJava8LeafFixture.class.getMethod("noArgs")).args().length == 0);
        check(Possible.EMPTY != Possible.NULL && Possible.EMPTY.equals(Possible.NULL));
        check(Possible.absent() == Possible.EMPTY && Possible.of(null) == Possible.NULL);
        check(!Possible.EMPTY.isSet() && Possible.EMPTY.isEmpty());
        check(Possible.NULL.isSet() && !Possible.NULL.isEmpty());
        Possible<String> present = Possible.of("x");
        check(present.value().equals("x") && present.equals(new Possible<Integer>("x")));
        check(present.hashCode() == "x".hashCode() && present.cast(String.class) == present);
        check(Possible.EMPTY.toString().equals("EMPTY") && Possible.NULL.toString().equals("null"));
        System.out.println("PASS canonical Rhino Java8 leaf subset: " + System.getProperty("java.version"));
    }
}
