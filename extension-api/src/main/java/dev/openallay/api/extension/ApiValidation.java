package dev.openallay.api.extension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Syntax and defensive copies only; compatibility matching belongs to the core. */
final class ApiValidation {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9][a-z0-9_.-]*(?::[a-z0-9_][a-z0-9_./-]*)?");
    private static final Pattern VERSION = Pattern.compile("[0-9A-Za-z][0-9A-Za-z._+\\-]*");
    private ApiValidation() {}

    static String text(String value, String name) {
        Objects.requireNonNull(value, name);
        boolean content = false;
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) { content = true; break; }
        }
        if (!content) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    static String id(String value, String name) {
        if (value == null || !ID.matcher(value).matches())
            throw new IllegalArgumentException("Invalid " + name + ": " + value);
        return value;
    }

    static String token(String value, String name) {
        if (value == null || !TOKEN.matcher(value).matches())
            throw new IllegalArgumentException("Invalid " + name + ": " + value);
        return value;
    }

    static String loader(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9_.-]*"))
            throw new IllegalArgumentException("Invalid loader ID: " + value);
        return value;
    }

    static int javaVersion(int value) {
        if (value < 8) throw new IllegalArgumentException("Java version must be at least 8");
        return value;
    }

    static String version(String value, String name) {
        if (value == null || !VERSION.matcher(value).matches())
            throw new IllegalArgumentException("Invalid " + name + ": " + value);
        return value;
    }

    /** Exact version, [version], or one Maven-style interval; never compares versions. */
    static String range(String value, String name) {
        text(value, name);
        if (VERSION.matcher(value).matches()) return value;
        if (value.length() < 3) throw new IllegalArgumentException("Invalid " + name + ": " + value);
        char first = value.charAt(0), last = value.charAt(value.length() - 1);
        if ((first != '[' && first != '(') || (last != ']' && last != ')'))
            throw new IllegalArgumentException("Invalid " + name + ": " + value);
        String body = value.substring(1, value.length() - 1);
        int comma = body.indexOf(',');
        if (comma < 0) {
            if (first != '[' || last != ']')
                throw new IllegalArgumentException("Invalid exact interval: " + value);
            version(body, name);
        } else {
            if (comma != body.lastIndexOf(','))
                throw new IllegalArgumentException("Declare separate targets for range unions: " + value);
            String lower = body.substring(0, comma).trim();
            String upper = body.substring(comma + 1).trim();
            if (lower.isEmpty() && upper.isEmpty())
                throw new IllegalArgumentException("Range must declare at least one bound: " + value);
            if (lower.isEmpty() && first != '(' || upper.isEmpty() && last != ')')
                throw new IllegalArgumentException("Unbounded range endpoints must be open: " + value);
            if (!lower.isEmpty()) version(lower, name);
            if (!upper.isEmpty()) version(upper, name);
        }
        return value;
    }

    static <T> List<T> list(List<T> values, String name) {
        ArrayList<T> copy = new ArrayList<T>(Objects.requireNonNull(values, name));
        for (T value : copy) Objects.requireNonNull(value, name + " element");
        return Collections.unmodifiableList(copy);
    }

    static Set<String> ids(Set<String> values, String name, boolean namespaced) {
        LinkedHashSet<String> copy = new LinkedHashSet<String>();
        for (String value : Objects.requireNonNull(values, name))
            copy.add(namespaced ? id(value, name) : token(value, name));
        return Collections.unmodifiableSet(copy);
    }

    static void unique(List<String> ids, String name) {
        Set<String> seen = new LinkedHashSet<String>();
        for (String id : ids) {
            id(id, name);
            if (!seen.add(id)) throw new IllegalArgumentException("Duplicate " + name + ": " + id);
        }
    }

    static String skillPath(String value) {
        text(value, "Skill path");
        if (value.indexOf('\\') >= 0 || value.indexOf(':') >= 0)
            throw new IllegalArgumentException("Invalid Skill path: " + value);
        Path path = Paths.get(value);
        if (path.isAbsolute()) throw new IllegalArgumentException("Absolute Skill path: " + value);
        String normalized = path.normalize().toString().replace('\\', '/');
        if (normalized.isEmpty() || normalized.equals("..") || normalized.startsWith("../"))
            throw new IllegalArgumentException("Skill path escapes its root: " + value);
        return normalized;
    }
}
