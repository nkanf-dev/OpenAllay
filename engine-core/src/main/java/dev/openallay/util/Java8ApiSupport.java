package dev.openallay.util;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/** Exact Java8 adaptations for public JDK convenience APIs. */
public final class Java8ApiSupport {
    private Java8ApiSupport() {}
    public static <T> T orElseThrow(Optional<T> optional) {
        return Objects.requireNonNull(optional).orElseThrow(() -> new NoSuchElementException("No value present"));
    }
    public static <T> Collector<T, ?, List<T>> toUnmodifiableList() {
        return Collectors.collectingAndThen(Collectors.toList(), Java8Collections::listCopyOf);
    }
    public static <T> Collector<T, ?, Set<T>> toUnmodifiableSet() {
        return Collectors.collectingAndThen(Collectors.toSet(), Java8Collections::setCopyOf);
    }
    public static String formatted(String format, Object... arguments) {
        Objects.requireNonNull(format);
        return String.format(format, arguments);
    }
}
