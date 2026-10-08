package dev.openallay.util;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Collection;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/** Exact Java8 adaptations for public JDK convenience APIs. */
public final class Java8ApiSupport {
    private Java8ApiSupport() {}
    public static <T> T orElseThrow(Optional<T> optional) {
        return Objects.requireNonNull(optional).orElseThrow(() -> new NoSuchElementException("No value present"));
    }
    public static boolean isEmpty(Optional<?> optional) { return !Objects.requireNonNull(optional).isPresent(); }
    public static <T> Stream<T> stream(Optional<T> optional) {
        Objects.requireNonNull(optional);
        return optional.isPresent() ? Stream.of(optional.get()) : Stream.empty();
    }
    public static <T> Optional<T> or(Optional<T> optional, Supplier<? extends Optional<? extends T>> supplier) {
        Objects.requireNonNull(optional); Objects.requireNonNull(supplier);
        if (optional.isPresent()) return optional;
        @SuppressWarnings("unchecked") Optional<T> replacement = (Optional<T>) Objects.requireNonNull(supplier.get());
        return replacement;
    }
    public static <T> T[] toArray(Collection<T> collection, IntFunction<T[]> generator) {
        Objects.requireNonNull(collection); Objects.requireNonNull(generator);
        return collection.toArray(generator.apply(0));
    }
    public static String toString(ByteArrayOutputStream output, Charset charset) {
        Objects.requireNonNull(output); Objects.requireNonNull(charset);
        return new String(output.toByteArray(), charset);
    }
    public static <T> Collector<T, ?, List<T>> toUnmodifiableList() {
        return Collectors.collectingAndThen(Collectors.toList(), Java8Collections::listCopyOf);
    }
    public static <T> Collector<T, ?, Set<T>> toUnmodifiableSet() {
        return Collectors.collectingAndThen(Collectors.toSet(), Java8Collections::setCopyOf);
    }
    public static String urlEncodeUtf8(String value) {
        try { return java.net.URLEncoder.encode(value, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }
    /** External running JVM feature fact. This never selects another Java runtime. */
    public static int runtimeVersionFeature() {
        try {
            Object version = Runtime.class.getMethod("version").invoke(null);
            return (Integer) version.getClass().getMethod("feature").invoke(version);
        } catch (NoSuchMethodException java8) {
            String value = java.lang.management.ManagementFactory.getRuntimeMXBean().getSpecVersion();
            if (value == null) throw new IllegalStateException("Missing java.specification.version");
            String feature = value.startsWith("1.") ? value.substring(2) : value;
            int dot = feature.indexOf('.');
            if (dot >= 0) feature = feature.substring(0, dot);
            return Integer.parseInt(feature);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("Cannot obtain public JVM version fact", cause);
        } catch (IllegalAccessException impossible) {
            throw new IllegalStateException("Cannot access public JVM version fact", impossible);
        }
    }
    public static String formatted(String format, Object... arguments) {
        Objects.requireNonNull(format);
        return String.format(format, arguments);
    }
}
