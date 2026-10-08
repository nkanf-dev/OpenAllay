package dev.openallay.net;

import dev.openallay.util.Java8Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Case-insensitive, immutable response headers. */
public final class HttpResponseHeaders {
    private final Map<String, List<String>> values;

    public HttpResponseHeaders(Map<String, List<String>> values) {
        LinkedHashMap<String, List<String>> normalized = new LinkedHashMap<>();
        Objects.requireNonNull(values, "values").forEach((name, entries) -> {
            // URLConnection includes a null-key status line; it is not an HTTP header.
            if (name != null) {
                String key = name.toLowerCase(Locale.ROOT);
                List<String> merged = new ArrayList<>();
                if (normalized.containsKey(key)) merged.addAll(normalized.get(key));
                merged.addAll(Java8Collections.listCopyOf(entries));
                normalized.put(key, Java8Collections.listCopyOf(merged));
            }
        });
        this.values = Java8Collections.mapCopyOf(normalized);
    }

    public Map<String, List<String>> values() { return values; }

    public Optional<String> firstValue(String name) {
        List<String> found = values.get(name.toLowerCase(Locale.ROOT));
        return found == null || found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof HttpResponseHeaders
                && values.equals(((HttpResponseHeaders) other).values);
    }
    @Override public int hashCode() { return values.hashCode(); }
    @Override public String toString() { return "HttpResponseHeaders[values=" + values + "]"; }
}
