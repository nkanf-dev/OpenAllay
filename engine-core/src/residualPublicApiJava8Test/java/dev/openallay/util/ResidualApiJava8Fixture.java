package dev.openallay.util;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/** One public native-modern API vs complete canonical Java8 helper oracle. */
public final class ResidualApiJava8Fixture {
    private ResidualApiJava8Fixture() {}
    private static boolean modern;
    private interface Action { void run() throws Throwable; }
    public static void main(String[] args) throws Throwable {
        modern = args.length == 0;
        if (!modern && !"1.8".equals(System.getProperty("java.specification.version"))) throw new AssertionError("genuineJava8 required");
        Object identity = new Object();
        check(optional(Optional.of(identity)) == identity, "Optional identity");
        exception("optionalEmpty", () -> optional(Optional.empty()), NoSuchElementException.class, "No value present");
        exception("optionalNull", () -> optional(null), NullPointerException.class, null);
        List<String> list = Arrays.asList("a", "b", "a");
        List<String> snapshot = list.stream().collect(immutableList());
        check(snapshot.equals(list), "immutable List order");
        exception("listMutation", () -> snapshot.add("x"), UnsupportedOperationException.class, null);
        exception("listNull", () -> Arrays.asList("a", null).stream().collect(immutableList()), NullPointerException.class, null);
        Set<String> set = list.parallelStream().collect(immutableSet());
        check(set.equals(new HashSet<>(Arrays.asList("a", "b"))), "parallel duplicate collapse");
        exception("setMutation", () -> set.add("x"), UnsupportedOperationException.class, null);
        exception("setNull", () -> Arrays.asList("a", null).stream().collect(immutableSet()), NullPointerException.class, null);
        System.out.println("collectorList=" + snapshot);
        System.out.println("collectorSet=" + new TreeSet<>(set)); // Set iteration order is unspecified, values are exact.
        check(Java8Objects.requireNonNullElse(identity, null) == identity, "fallback identity");
        check(requireNonNullElse(null, "fallback").equals("fallback"), "fallback value");
        exception("fallbackNull", () -> requireNonNullElse(null, null), NullPointerException.class, "defaultObj");
        Path directory = Files.createTempDirectory("openallay-api-oracle-");
        try {
            Path path = directory.resolve("text.txt"); String text = "BOM \ufeff / Ω / 中 / 🐝\r\nline\n";
            check(write(path, text, StandardCharsets.UTF_8) == path, "write path identity");
            check(read(path, StandardCharsets.UTF_8).equals(text), "UTF8 roundtrip");
            byte[] utf8 = Files.readAllBytes(path); System.out.println("fileUtf8=" + Java8Hex.formatHex(utf8));
            Path utf16 = directory.resolve("utf16.txt"); write(utf16, text, StandardCharsets.UTF_16); check(read(utf16, StandardCharsets.UTF_16).equals(text), "Charset is not forced UTF8");
            System.out.println("fileUtf16=" + Java8Hex.formatHex(Files.readAllBytes(utf16)));
            exception("createNew", () -> write(path, "overwrite", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW), FileAlreadyExistsException.class, null);
            write(path, "append", StandardCharsets.UTF_8, StandardOpenOption.APPEND); check(read(path, StandardCharsets.UTF_8).equals(text + "append"), "APPEND");
            write(path, "short", StandardCharsets.UTF_8); check(read(path, StandardCharsets.UTF_8).equals("short"), "default truncate");
            Path bad = directory.resolve("bad.txt"); Files.write(bad, new byte[] {(byte)0xc3, 0x28});
            exception("readMalformed", () -> read(bad, StandardCharsets.UTF_8), java.nio.charset.MalformedInputException.class, null);
            Path unmappable = directory.resolve("unmappable.txt");
            exception("writeUnmappable", () -> write(unmappable, "Ω", StandardCharsets.US_ASCII), java.nio.charset.UnmappableCharacterException.class, null);
            check(!Files.exists(unmappable), "encoding fails before opening destination");
            exception("writeMalformedSurrogate", () -> write(unmappable, "\ud800", StandardCharsets.UTF_8), java.nio.charset.MalformedInputException.class, null);
            exception("readNullCharset", () -> read(path, null), NullPointerException.class, null);
            exception("writeNullText", () -> write(path, null, StandardCharsets.UTF_8), NullPointerException.class, null);
            exception("writeNullOptions", () -> write(path, "x", StandardCharsets.UTF_8, (OpenOption[]) null), NullPointerException.class, null);
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.list(directory)) { paths.forEach(path -> {try {Files.delete(path);}catch(IOException failure){throw new UncheckedIOException(failure);}}); }
            Files.delete(directory);
        }
        ProgressInput input = new ProgressInput(new byte[] {1,2,3,4,5});
        check(Arrays.equals(readN(input, 3), new byte[] {1,2,3}), "N exact prefix");
        check(input.read() == 4 && !input.closed, "N stream position/no close");
        check(Arrays.equals(readAll(input), new byte[] {5}) && !input.closed, "all EOF/no close");
        check(readN(new ByteArrayInputStream(new byte[] {1}), 3).length == 1, "short EOF");
        check(readN(new ByteArrayInputStream(new byte[] {1}), 0).length == 0, "zero N");
        exception("negativeN", () -> readN(new ByteArrayInputStream(new byte[0]), -1), IllegalArgumentException.class, "len < 0");
        ProgressInput transferred = new ProgressInput(new byte[] {7,8,9}); TrackingOutput output = new TrackingOutput();
        check(transfer(transferred, output) == 3L && Arrays.equals(output.toByteArray(), new byte[] {7,8,9}), "transfer bytes/count");
        check(!transferred.closed && !output.closed, "transfer never closes");
        ProgressInput untouched = new ProgressInput(new byte[] {1});
        exception("nullOutput", () -> transfer(untouched, null), NullPointerException.class, "out"); check(untouched.position == 0, "null output before consumption");
        exception("readFailure", () -> readAll(new InputStream(){public int read() throws IOException {throw new IOException("read failure");}}), IOException.class, "read failure");
        exception("writeFailure", () -> transfer(new ByteArrayInputStream(new byte[] {1}), new OutputStream(){public void write(int b)throws IOException {throw new IOException("write failure");}}), IOException.class, "write failure");
        System.out.println("streams=prefix3/position4/all5/transfer789/no-close");
        Locale previous = Locale.getDefault();
        try { Locale.setDefault(Locale.FRANCE); check(format("value=%.2f/%s/%d", 1.25, null, 7).equals("value=1,25/null/7"), "default locale/args"); System.out.println("format=" + format("value=%.2f/%s/%d", 1.25, null, 7)); }
        finally {Locale.setDefault(previous);}
        exception("formatIllegal", () -> format("%d", "text"), IllegalFormatConversionException.class, null);
        exception("formatNull", () -> format(null, "x"), NullPointerException.class, null);
        System.out.println("PASS six public API families original-modern vs canonicalJava8");
    }
    private static Object invoke(Class<?> type, String name, Class<?>[] parameters, Object target, Object... args) throws Throwable {
        try {return type.getMethod(name, parameters).invoke(target, args);} catch(InvocationTargetException failure){throw failure.getCause();}
    }
    @SuppressWarnings("unchecked") private static <T> T optional(Optional<T> value) throws Throwable {return modern ? (T)invoke(Optional.class,"orElseThrow",new Class<?>[0],value) : Java8ApiSupport.orElseThrow(value);}
    @SuppressWarnings("unchecked") private static <T> Collector<T,?,List<T>> immutableList() throws Throwable {return modern ? (Collector<T,?,List<T>>)invoke(Collectors.class,"toUnmodifiableList",new Class<?>[0],null) : Java8ApiSupport.toUnmodifiableList();}
    @SuppressWarnings("unchecked") private static <T> Collector<T,?,Set<T>> immutableSet() throws Throwable {return modern ? (Collector<T,?,Set<T>>)invoke(Collectors.class,"toUnmodifiableSet",new Class<?>[0],null) : Java8ApiSupport.toUnmodifiableSet();}
    private static Object requireNonNullElse(Object value,Object fallback) throws Throwable {return modern ? invoke(Objects.class,"requireNonNullElse",new Class<?>[]{Object.class,Object.class},null,value,fallback) : Java8Objects.requireNonNullElse(value,fallback);}
    private static String read(Path path,Charset charset) throws Throwable {return modern ? (String)invoke(Files.class,"readString",new Class<?>[]{Path.class,Charset.class},null,path,charset) : Java8Files.readString(path,charset);}
    private static Path write(Path path,CharSequence text,Charset charset,OpenOption... options) throws Throwable {return modern ? (Path)invoke(Files.class,"writeString",new Class<?>[]{Path.class,CharSequence.class,Charset.class,OpenOption[].class},null,path,text,charset,options) : Java8Files.writeString(path,text,charset,options);}
    private static byte[] readN(InputStream input,int n) throws Throwable {return modern ? (byte[])invoke(InputStream.class,"readNBytes",new Class<?>[]{int.class},input,n) : Java8Streams.readNBytes(input,n);}
    private static byte[] readAll(InputStream input) throws Throwable {return modern ? (byte[])invoke(InputStream.class,"readAllBytes",new Class<?>[0],input) : Java8Streams.readAllBytes(input);}
    private static long transfer(InputStream input,OutputStream output) throws Throwable {return modern ? (Long)invoke(InputStream.class,"transferTo",new Class<?>[]{OutputStream.class},input,output) : Java8Streams.transferTo(input,output);}
    private static String format(String format,Object... args) throws Throwable {return modern ? (String)invoke(String.class,"formatted",new Class<?>[]{Object[].class},format,(Object)args) : Java8ApiSupport.formatted(format,args);}
    private static void exception(String label, Action action,Class<? extends Throwable> expected,String message) throws Throwable {try{action.run();throw new AssertionError("Expected "+label);}catch(Throwable failure){if(!expected.isInstance(failure))throw new AssertionError(label+" got "+failure,failure);if(message!=null&&!message.equals(failure.getMessage()))throw new AssertionError(label+" message "+failure.getMessage());System.out.println(label+"="+failure.getClass().getSimpleName()+(message==null?"":"/"+message));}}
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    private static final class ProgressInput extends InputStream {
        final byte[] bytes;int position;boolean zero=true,closed;
        ProgressInput(byte[] bytes){this.bytes=bytes;}
        public int read(){return position==bytes.length?-1:bytes[position++]&255;}
        public int read(byte[] b,int off,int len){if(len==0)return 0;if(zero){zero=false;return 0;}if(position==bytes.length)return -1;int n=Math.min(len,bytes.length-position);System.arraycopy(bytes,position,b,off,n);position+=n;return n;}
        public void close(){closed=true;}
    }
    private static final class TrackingOutput extends ByteArrayOutputStream {boolean closed;public void close(){closed=true;}}
}
