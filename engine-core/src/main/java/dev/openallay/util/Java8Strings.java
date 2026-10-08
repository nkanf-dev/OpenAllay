package dev.openallay.util;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** String operations using the running Java 8 Character whitespace tables. */
public final class Java8Strings {
    private Java8Strings() {}

    public static boolean isBlank(String value) {
        Objects.requireNonNull(value, "value");
        return firstNonWhitespace(value) == value.length();
    }

    public static String strip(String value) {
        Objects.requireNonNull(value, "value");
        int start = firstNonWhitespace(value);
        return value.substring(start, Math.max(start, lastNonWhitespaceEnd(value)));
    }

    public static String stripLeading(String value) {
        Objects.requireNonNull(value, "value");
        return value.substring(firstNonWhitespace(value));
    }

    public static String stripTrailing(String value) {
        Objects.requireNonNull(value, "value");
        return value.substring(0, lastNonWhitespaceEnd(value));
    }

    private static int firstNonWhitespace(String value) {
        int index = 0;
        while (index < value.length()) {
            int codePoint = value.codePointAt(index);
            if (!Character.isWhitespace(codePoint)) break;
            index += Character.charCount(codePoint);
        }
        return index;
    }

    private static int lastNonWhitespaceEnd(String value) {
        int index = value.length();
        while (index > 0) {
            int codePoint = value.codePointBefore(index);
            if (!Character.isWhitespace(codePoint)) break;
            index -= Character.charCount(codePoint);
        }
        return index;
    }

    public static String repeat(String value, int count) {
        Objects.requireNonNull(value, "value");
        if (count < 0) throw new IllegalArgumentException("count is negative: " + count);
        if (count == 0 || value.isEmpty()) return "";
        if (count == 1) return value;
        if (value.length() > Integer.MAX_VALUE / count) {
            throw new OutOfMemoryError("repeated string exceeds maximum length");
        }
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) result.append(value);
        return result.toString();
    }

    /** Lines are split lazily at LF, CR or CRLF; a final terminator adds no line. */
    public static Stream<String> lines(final String value) {
        Objects.requireNonNull(value, "value");
        Iterator<String> iterator = new Iterator<String>() {
            private int start;

            @Override public boolean hasNext() { return start < value.length(); }

            @Override public String next() {
                if (!hasNext()) throw new NoSuchElementException();
                int end = start;
                while (end < value.length() && value.charAt(end) != '\n'
                        && value.charAt(end) != '\r') end++;
                String line = value.substring(start, end);
                start = end;
                if (start < value.length()) {
                    char terminator = value.charAt(start++);
                    if (terminator == '\r' && start < value.length()
                            && value.charAt(start) == '\n') start++;
                }
                return line;
            }
        };
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator,
                Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.IMMUTABLE), false);
    }
}
