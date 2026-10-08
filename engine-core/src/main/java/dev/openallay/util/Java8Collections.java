package dev.openallay.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Java 8 collection snapshots. Elements themselves are not copied. */
public final class Java8Collections {
    private Java8Collections() {}

    @SafeVarargs
    public static <E> List<E> listOf(E... elements) {
        Objects.requireNonNull(elements, "elements");
        List<E> copy = new ArrayList<E>(elements.length);
        for (E element : elements) copy.add(Objects.requireNonNull(element, "element"));
        return Collections.unmodifiableList(copy);
    }

    public static <E> List<E> listCopyOf(Collection<? extends E> elements) {
        Objects.requireNonNull(elements, "elements");
        List<E> copy = new ArrayList<E>(elements.size());
        for (E element : elements) copy.add(Objects.requireNonNull(element, "element"));
        return Collections.unmodifiableList(copy);
    }

    @SafeVarargs
    public static <E> Set<E> setOf(E... elements) {
        Objects.requireNonNull(elements, "elements");
        Set<E> copy = new LinkedHashSet<E>();
        for (E element : elements) {
            if (!copy.add(Objects.requireNonNull(element, "element"))) {
                throw new IllegalArgumentException("duplicate element: " + element);
            }
        }
        return Collections.unmodifiableSet(copy);
    }

    public static <E> Set<E> setCopyOf(Collection<? extends E> elements) {
        Objects.requireNonNull(elements, "elements");
        Set<E> copy = new LinkedHashSet<E>();
        for (E element : elements) copy.add(Objects.requireNonNull(element, "element"));
        return Collections.unmodifiableSet(copy);
    }

    public static <K, V> Map<K, V> mapOf() {
        return Collections.unmodifiableMap(new LinkedHashMap<K, V>());
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1) {
        Map<K, V> copy = new LinkedHashMap<K, V>();
        putUnique(copy, k1, v1);
        return Collections.unmodifiableMap(copy);
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2) {
        Map<K, V> copy = new LinkedHashMap<K, V>();
        putUnique(copy, k1, v1);
        putUnique(copy, k2, v2);
        return Collections.unmodifiableMap(copy);
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3,
                                       K k4, V v4, K k5, V v5, K k6, V v6) {
        Map<K, V> copy = new LinkedHashMap<K, V>();
        putUnique(copy, k1, v1);
        putUnique(copy, k2, v2);
        putUnique(copy, k3, v3);
        putUnique(copy, k4, v4);
        putUnique(copy, k5, v5);
        putUnique(copy, k6, v6);
        return Collections.unmodifiableMap(copy);
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3) {
        Map<K, V> copy = new LinkedHashMap<K, V>();
        putUnique(copy, k1, v1);
        putUnique(copy, k2, v2);
        putUnique(copy, k3, v3);
        return Collections.unmodifiableMap(copy);
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        Map<K, V> copy = new LinkedHashMap<K, V>();
        putUnique(copy, k1, v1);
        putUnique(copy, k2, v2);
        putUnique(copy, k3, v3);
        putUnique(copy, k4, v4);
        return Collections.unmodifiableMap(copy);
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        Map<K, V> copy = new LinkedHashMap<K, V>();
        putUnique(copy, k1, v1);
        putUnique(copy, k2, v2);
        putUnique(copy, k3, v3);
        putUnique(copy, k4, v4);
        putUnique(copy, k5, v5);
        return Collections.unmodifiableMap(copy);
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7) {
        Map<K, V> copy = new LinkedHashMap<K, V>();
        putUnique(copy, k1, v1);
        putUnique(copy, k2, v2);
        putUnique(copy, k3, v3);
        putUnique(copy, k4, v4);
        putUnique(copy, k5, v5);
        putUnique(copy, k6, v6);
        putUnique(copy, k7, v7);
        return Collections.unmodifiableMap(copy);
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8) {
        Map<K, V> copy = new LinkedHashMap<K, V>();
        putUnique(copy, k1, v1);
        putUnique(copy, k2, v2);
        putUnique(copy, k3, v3);
        putUnique(copy, k4, v4);
        putUnique(copy, k5, v5);
        putUnique(copy, k6, v6);
        putUnique(copy, k7, v7);
        putUnique(copy, k8, v8);
        return Collections.unmodifiableMap(copy);
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9) {
        Map<K, V> copy = new LinkedHashMap<K, V>();
        putUnique(copy, k1, v1);
        putUnique(copy, k2, v2);
        putUnique(copy, k3, v3);
        putUnique(copy, k4, v4);
        putUnique(copy, k5, v5);
        putUnique(copy, k6, v6);
        putUnique(copy, k7, v7);
        putUnique(copy, k8, v8);
        putUnique(copy, k9, v9);
        return Collections.unmodifiableMap(copy);
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9, K k10, V v10) {
        Map<K, V> copy = new LinkedHashMap<K, V>();
        putUnique(copy, k1, v1);
        putUnique(copy, k2, v2);
        putUnique(copy, k3, v3);
        putUnique(copy, k4, v4);
        putUnique(copy, k5, v5);
        putUnique(copy, k6, v6);
        putUnique(copy, k7, v7);
        putUnique(copy, k8, v8);
        putUnique(copy, k9, v9);
        putUnique(copy, k10, v10);
        return Collections.unmodifiableMap(copy);
    }

    public static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return new java.util.AbstractMap.SimpleImmutableEntry<K, V>(
                Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
    }

    @SafeVarargs
    public static <K, V> Map<K, V> mapOfEntries(Map.Entry<? extends K, ? extends V>... entries) {
        Objects.requireNonNull(entries, "entries");
        Map<K, V> copy = new LinkedHashMap<K, V>();
        for (Map.Entry<? extends K, ? extends V> entry : entries) {
            Objects.requireNonNull(entry, "entry");
            putUnique(copy, entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    public static <K, V> Map<K, V> mapCopyOf(Map<? extends K, ? extends V> entries) {
        Objects.requireNonNull(entries, "entries");
        Map<K, V> copy = new LinkedHashMap<K, V>();
        for (Map.Entry<? extends K, ? extends V> entry : entries.entrySet()) {
            putUnique(copy, entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    private static <K, V> void putUnique(Map<K, V> map, K key, V value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (map.containsKey(key)) throw new IllegalArgumentException("duplicate key: " + key);
        map.put(key, value);
    }

    /** Unlike listOf/listCopyOf, a Stream.toList snapshot permits null elements. */
    public static <E> List<E> toList(Stream<? extends E> stream) {
        List<E> copy = Objects.requireNonNull(stream, "stream")
                .collect(Collectors.toCollection(ArrayList<E>::new));
        return Collections.unmodifiableList(copy);
    }
}
