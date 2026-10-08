package dev.openallay.guide.semantic;

import dev.openallay.util.Java8Collections;
import dev.openallay.util.Java8Futures;
import dev.openallay.util.Java8Hex;
import dev.openallay.util.Java8Strings;
import java.io.DataInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Exercises canonical production sources, including the real package-local hash caller. */
public final class PrimitivesFixture {
    private static int checks;

    private PrimitivesFixture() {}

    public static void main(String[] args) throws Exception {
        boolean oracle = args.length == 1 && "--modern-oracle".equals(args[0]);
        String specification = System.getProperty("java.specification.version");
        if (!oracle && !"1.8".equals(specification)) {
            throw new AssertionError("Genuine Java 8 required; got " + specification);
        }
        bytecodeOwners();
        collections();
        futures();
        strings();
        hexAndSemanticIds();
        if (oracle) modernOracle();
        System.out.println("PASS " + checks + " checks; java.specification.version=" + specification
                + "; mode=" + (oracle ? "modern-oracle" : "genuine-java8"));
    }

    private static void bytecodeOwners() throws Exception {
        Class<?>[] owners = {Java8Collections.class, Java8Futures.class, Java8Strings.class,
                Java8Hex.class, SemanticIds.class, PrimitivesFixture.class};
        for (Class<?> owner : owners) {
            String path = "/" + owner.getName().replace('.', '/') + ".class";
            InputStream resource = owner.getResourceAsStream(path);
            if (resource == null) throw new AssertionError("Missing owner bytecode: " + path);
            try (DataInputStream input = new DataInputStream(resource)) {
                eq(Integer.valueOf(0xcafebabe), Integer.valueOf(input.readInt()));
                eq(Integer.valueOf(0), Integer.valueOf(input.readUnsignedShort()));
                eq(Integer.valueOf(52), Integer.valueOf(input.readUnsignedShort()));
            }
        }
    }

    private static void collections() {
        eq(Collections.emptyList(), Java8Collections.listOf());
        eq(Arrays.asList("a", "a"), Java8Collections.listOf("a", "a"));
        String[] array = {"a", "b"};
        List<String> list = Java8Collections.listOf(array);
        array[0] = "changed";
        eq(Arrays.asList("a", "b"), list);
        fails(UnsupportedOperationException.class, () -> list.set(0, "x"));
        fails(UnsupportedOperationException.class, () -> list.add("x"));
        List<String> mutable = new ArrayList<String>(Arrays.asList("a", "b"));
        List<String> snapshot = Java8Collections.listCopyOf(mutable);
        mutable.clear();
        eq(Arrays.asList("a", "b"), snapshot);
        fails(NullPointerException.class, () -> Java8Collections.listOf((String[]) null));
        fails(NullPointerException.class, () -> Java8Collections.listOf("a", null));
        fails(NullPointerException.class, () -> Java8Collections.listCopyOf(null));
        fails(NullPointerException.class, () -> Java8Collections.listCopyOf(Arrays.asList("a", null)));
        eq(Collections.emptySet(), Java8Collections.setOf());
        fails(IllegalArgumentException.class, () -> Java8Collections.setOf("a", new String("a")));
        fails(NullPointerException.class, () -> Java8Collections.setOf((String[]) null));
        fails(NullPointerException.class, () -> Java8Collections.setOf("a", null));
        fails(NullPointerException.class, () -> Java8Collections.setCopyOf(null));
        fails(NullPointerException.class, () -> Java8Collections.setCopyOf(Arrays.asList("a", null)));
        mutable.addAll(Arrays.asList("b", "a", "b"));
        java.util.Set<String> set = Java8Collections.setCopyOf(mutable);
        mutable.clear();
        eq(Arrays.asList("b", "a"), new ArrayList<String>(set));
        fails(UnsupportedOperationException.class, () -> set.add("c"));
        fails(UnsupportedOperationException.class, () -> set.remove("a"));
        eq(Collections.emptyMap(), Java8Collections.mapOf());
        eq(Integer.valueOf(1), Java8Collections.mapOf("a", 1).get("a"));
        Map<String, Integer> pairs = Java8Collections.mapOf("b", 2, "a", 1);
        eq(Arrays.asList("b", "a"), new ArrayList<String>(pairs.keySet()));
        eq(Integer.valueOf(6), Java8Collections.mapOf("a", 1, "b", 2, "c", 3,
                "d", 4, "e", 5, "f", 6).get("f"));
        fails(IllegalArgumentException.class, () -> Java8Collections.mapOf("a", 1, new String("a"), 2));
        fails(IllegalArgumentException.class, () -> Java8Collections.mapOf("a", 1, "b", 2,
                "c", 3, "d", 4, "e", 5, "a", 6));
        fails(NullPointerException.class, () -> Java8Collections.mapOf(null, 1));
        fails(NullPointerException.class, () -> Java8Collections.mapOf("a", null));
        fails(UnsupportedOperationException.class, () -> pairs.put("c", 3));
        fails(UnsupportedOperationException.class, () -> pairs.entrySet().iterator().next().setValue(9));
        Map<String, Integer> original = new LinkedHashMap<String, Integer>();
        original.put("b", 2);
        original.put("a", 1);
        Map<String, Integer> copied = Java8Collections.mapCopyOf(original);
        original.clear();
        eq(pairs, copied);
        eq(Arrays.asList("b", "a"), new ArrayList<String>(copied.keySet()));
        fails(UnsupportedOperationException.class, () -> copied.clear());
        fails(NullPointerException.class, () -> Java8Collections.mapCopyOf(null));
        Map<String, Integer> bad = new HashMap<String, Integer>();
        bad.put(null, 1);
        fails(NullPointerException.class, () -> Java8Collections.mapCopyOf(bad));
        bad.clear();
        bad.put("a", null);
        fails(NullPointerException.class, () -> Java8Collections.mapCopyOf(bad));
        Map.Entry<String, Integer> entry = Java8Collections.entry("a", 1);
        fails(UnsupportedOperationException.class, () -> entry.setValue(2));
        fails(NullPointerException.class, () -> Java8Collections.entry(null, 1));
        fails(NullPointerException.class, () -> Java8Collections.entry("a", null));
        eq(Collections.emptyMap(), Java8Collections.mapOfEntries());
        java.util.AbstractMap.SimpleEntry<String, Integer> mutableEntry =
                new java.util.AbstractMap.SimpleEntry<String, Integer>("a", 1);
        Map<String, Integer> entries = Java8Collections.mapOfEntries(mutableEntry);
        mutableEntry.setValue(99);
        eq(Integer.valueOf(1), entries.get("a"));
        fails(IllegalArgumentException.class, () -> Java8Collections.mapOfEntries(entry, entry));
        fails(NullPointerException.class, () -> Java8Collections.mapOfEntries(
                (Map.Entry<String, Integer>[]) null));
        fails(NullPointerException.class, () -> Java8Collections.mapOfEntries(entry, null));
        fails(NullPointerException.class, () -> Java8Collections.mapOfEntries(
                new java.util.AbstractMap.SimpleEntry<String, Integer>(null, 1)));
        fails(NullPointerException.class, () -> Java8Collections.mapOfEntries(
                new java.util.AbstractMap.SimpleEntry<String, Integer>("a", null)));
        mutable.addAll(Arrays.asList("a", null, "b"));
        List<String> streamed = Java8Collections.toList(mutable.stream());
        mutable.clear();
        eq(Arrays.asList("a", null, "b"), streamed);
        fails(UnsupportedOperationException.class, () -> streamed.add("c"));
        fails(UnsupportedOperationException.class, () -> streamed.set(1, "c"));
        eq(Arrays.asList("a", null, "b"), Java8Collections.toList(Stream.of("a", null, "b").parallel()));
        eq(Collections.emptyList(), Java8Collections.toList(Stream.empty()));
        fails(NullPointerException.class, () -> Java8Collections.toList(null));
        List<String> collectorList = Stream.of("a", null).collect(Collectors.toList());
        collectorList.add("b");
        eq(Arrays.asList("a", null, "b"), collectorList);
    }

    private static void futures() {
        Throwable failure = new IllegalStateException("fixture");
        CompletableFuture<String> first = Java8Futures.failedFuture(failure);
        CompletableFuture<String> second = Java8Futures.failedFuture(failure);
        truth(first != second);
        truth(first.isDone() && first.isCompletedExceptionally() && !first.isCancelled());
        Throwable joined = thrown(first::join);
        truth(joined instanceof CompletionException && joined.getCause() == failure);
        truth(!first.complete("changed"));
        fails(NullPointerException.class, () -> Java8Futures.failedFuture(null));
    }

    private static void strings() {
        truth(Java8Strings.isBlank(""));
        truth(Java8Strings.isBlank(" \t\n\r\u2003\u3000"));
        truth(!Java8Strings.isBlank("\u00a0"));
        truth(!Java8Strings.isBlank("\u2007\u202f"));
        truth(!Java8Strings.isBlank("x"));
        eq("text", Java8Strings.strip("\u2003 text\t\u3000"));
        eq("", Java8Strings.strip("\u2003\t"));
        eq("x ", Java8Strings.stripLeading("\u3000x "));
        eq(" x", Java8Strings.stripTrailing(" x\u2003"));
        String supplementary = "\ud83d\ude00\ud840\udc00";
        truth(!Java8Strings.isBlank(supplementary));
        eq(supplementary, Java8Strings.strip("\u2003" + supplementary + "\u3000"));
        eq("\ud800", Java8Strings.strip(" \ud800 "));
        eq("\udc00", Java8Strings.strip(" \udc00 "));
        eq("\u00a0x\u00a0", Java8Strings.strip("\u00a0x\u00a0"));
        // U+180E differs between Unicode tables; use this runtime's Character contract.
        eq(Boolean.valueOf(Character.isWhitespace(0x180e)),
                Boolean.valueOf(Java8Strings.isBlank("\u180e")));
        fails(NullPointerException.class, () -> Java8Strings.isBlank(null));
        fails(NullPointerException.class, () -> Java8Strings.strip(null));
        fails(NullPointerException.class, () -> Java8Strings.stripLeading(null));
        fails(NullPointerException.class, () -> Java8Strings.stripTrailing(null));
        eq("", Java8Strings.repeat("ab", 0));
        eq("ab", Java8Strings.repeat("ab", 1));
        eq("ababab", Java8Strings.repeat("ab", 3));
        eq(supplementary + supplementary, Java8Strings.repeat(supplementary, 2));
        eq("", Java8Strings.repeat("", Integer.MAX_VALUE));
        fails(IllegalArgumentException.class, () -> Java8Strings.repeat("", -1));
        fails(IllegalArgumentException.class, () -> Java8Strings.repeat("a", -1));
        fails(NullPointerException.class, () -> Java8Strings.repeat(null, 0));
        fails(OutOfMemoryError.class, () -> Java8Strings.repeat("ab", Integer.MAX_VALUE));
        eq(Collections.emptyList(), lines(""));
        eq(Arrays.asList(""), lines("\n"));
        eq(Arrays.asList(""), lines("\r\n"));
        eq(Arrays.asList("", ""), lines("\n\n"));
        eq(Arrays.asList("a"), lines("a\r\n"));
        eq(Arrays.asList("a", "b", "", "c"), lines("a\r\nb\r\rc\n"));
        eq(Arrays.asList("a", ""), lines("a\n\n"));
        eq(Arrays.asList("a", "", "b"), lines("a\n\rb"));
        eq(Arrays.asList("a\u2028b"), lines("a\u2028b"));
        fails(NullPointerException.class, () -> Java8Strings.lines(null));
    }

    private static List<String> lines(String text) {
        return Java8Strings.lines(text).collect(Collectors.toList());
    }

    private static void hexAndSemanticIds() {
        eq("", Java8Hex.formatHex(new byte[0]));
        byte[] bytes = {0, 1, 15, 16, 127, (byte) 128, (byte) 255};
        eq("00010f107f80ff", Java8Hex.formatHex(bytes));
        eq("010f10", Java8Hex.formatHex(bytes, 1, 4));
        eq("", Java8Hex.formatHex(bytes, bytes.length, bytes.length));
        eq("", Java8Hex.formatHex(bytes, 2, 2));
        byte[] all = new byte[256];
        for (int index = 0; index < all.length; index++) all[index] = (byte) index;
        String encoded = Java8Hex.formatHex(all);
        eq(Integer.valueOf(512), Integer.valueOf(encoded.length()));
        for (int index = 0; index < all.length; index++) {
            eq(Integer.valueOf(index), Integer.valueOf(Integer.parseInt(encoded.substring(index * 2, index * 2 + 2), 16)));
        }
        truth(encoded.matches("[0-9a-f]+"));
        fails(NullPointerException.class, () -> Java8Hex.formatHex(null));
        fails(NullPointerException.class, () -> Java8Hex.formatHex(null, 0, 0));
        fails(IndexOutOfBoundsException.class, () -> Java8Hex.formatHex(bytes, -1, 1));
        fails(IndexOutOfBoundsException.class, () -> Java8Hex.formatHex(bytes, 1, 0));
        fails(IndexOutOfBoundsException.class, () -> Java8Hex.formatHex(bytes, 0, 8));
        fails(IndexOutOfBoundsException.class, () -> Java8Hex.formatHex(bytes, 8, 8));
        eq("96a296d224f285c67bee93c30f8a309157f0daa35dc5b87e410b78630a09cfc7", SemanticIds.create("", "", ""));
        eq("f6f231516822bc8293f25d8da87f86b6b0288b0786be005a38a9e445c2b4cc66", SemanticIds.create("path", "kind", "content"));
        eq("da1fab5d28a8ab051c788df54674f839af29b3baca485128414d404db53090ba", SemanticIds.create("雪/😀", "text", "héllo\n世界"));
        String id = SemanticIds.create("path", "kind", "content");
        eq(id, SemanticIds.require(id));
        fails(IllegalArgumentException.class, () -> SemanticIds.require(id.toUpperCase(java.util.Locale.ROOT)));
        fails(IllegalArgumentException.class, () -> SemanticIds.require(null));
        fails(IllegalArgumentException.class, () -> SemanticIds.require("abc"));
    }

    private static void modernOracle() throws Exception {
        String[] vectors = {"", " \t\r\n", "\u2003x\u3000", "\u00a0", "\u2007\u202f",
                "\u180e", " \ud83d\ude00 ", " \ud840\udc00 ", " \ud800 ", " \udc00 "};
        for (String text : vectors) {
            eq(invoke("isBlank", text), Boolean.valueOf(Java8Strings.isBlank(text)));
            eq(invoke("strip", text), Java8Strings.strip(text));
            eq(invoke("stripLeading", text), Java8Strings.stripLeading(text));
            eq(invoke("stripTrailing", text), Java8Strings.stripTrailing(text));
        }
        String[] lineVectors = {"", "\n", "\r", "\r\n", "\n\n", "a\r\n", "a\n\n",
                "a\r\nb\r\rc\n", "a\n\rb", "a\u2028b"};
        for (String text : lineVectors) {
            Object result = invoke("lines", text);
            if (!(result instanceof Stream<?>)) throw new AssertionError("String.lines did not return Stream");
            try (Stream<?> stream = (Stream<?>) result) {
                eq(stream.collect(Collectors.toList()), lines(text));
            }
        }
        Method repeat = String.class.getMethod("repeat", int.class);
        for (String text : new String[] {"", "a", "ab", "\ud83d\ude00"}) {
            for (int count : new int[] {0, 1, 3}) eq(repeat.invoke(text, count), Java8Strings.repeat(text, count));
            eq(thrown(() -> repeat.invoke(text, -1)).getClass(),
                    thrown(() -> Java8Strings.repeat(text, -1)).getClass());
        }
        eq(thrown(() -> repeat.invoke("ab", Integer.MAX_VALUE)).getClass(),
                thrown(() -> Java8Strings.repeat("ab", Integer.MAX_VALUE)).getClass());
    }

    private static Object invoke(String method, String text) throws Exception {
        return String.class.getMethod(method).invoke(text);
    }

    private interface Action { void run() throws Exception; }

    private static Throwable thrown(Action action) {
        try {
            action.run();
        } catch (InvocationTargetException failure) {
            return failure.getCause();
        } catch (Throwable failure) {
            return failure;
        }
        throw new AssertionError("Expected failure");
    }

    private static void fails(Class<? extends Throwable> type, Action action) {
        Throwable actual = thrown(action);
        if (!type.isInstance(actual)) throw new AssertionError("Expected " + type + ", got " + actual, actual);
        checks++;
    }

    private static void eq(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
        checks++;
    }

    private static void truth(boolean value) {
        if (!value) throw new AssertionError("Condition failed");
        checks++;
    }
}
