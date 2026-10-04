package dev.openallay.script.command;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Detached projection of the complete Brigadier tree visible to one requesting player. */
public record CommandCatalogSnapshot(
        Instant capturedAt,
        List<CommandNodeSnapshot> nodes) {
    public CommandCatalogSnapshot {
        Objects.requireNonNull(capturedAt, "capturedAt");
        nodes = List.copyOf(nodes);
    }

    public Optional<CommandNodeSnapshot> describe(String path) {
        if (path == null) {
            return Optional.empty();
        }
        String canonical = canonical(path);
        return nodes.stream().filter(node -> node.path().equals(canonical)).findFirst();
    }

    public record CommandNodeSnapshot(
            String path,
            String name,
            String kind,
            String argumentType,
            boolean executable,
            String redirect,
            List<String> usage,
            List<String> children) {
        public CommandNodeSnapshot {
            path = require(path, "path");
            name = require(name, "name");
            kind = require(kind, "kind");
            argumentType = argumentType == null ? "" : argumentType;
            redirect = redirect == null ? "" : redirect;
            usage = List.copyOf(usage);
            children = List.copyOf(children);
        }
    }

    static String canonical(String value) {
        String result = value.strip();
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result.replaceAll("\\s+", " ");
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
