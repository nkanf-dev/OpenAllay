package dev.openallay.capability;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.openallay.tool.ToolResult;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

/** Strict current-schema decoder for local capability policy. */
public final class CapabilityPolicyLoader {
    private static final Set<String> ROOT_FIELDS =
            dev.openallay.util.Java8Collections.setOf("disabledTools", "disabledSkills");

    public ToolResult<CapabilityPolicy> load(Path path) {
        Objects.requireNonNull(path, "path");
        if (!Files.exists(path)) {
            return new ToolResult.Success<>(CapabilityPolicy.defaults());
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            return load(reader);
        } catch (IOException failure) {
            return failed("Unable to read capability configuration");
        }
    }

    public ToolResult<CapabilityPolicy> load(Reader reader) {
        Objects.requireNonNull(reader, "reader");
        try {
            JsonElement parsed = dev.openallay.json.JsonTrees.parse(reader);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Capability configuration must be an object");
            }
            JsonObject root = parsed.getAsJsonObject();
            if (!dev.openallay.json.JsonTrees.keys(root).equals(ROOT_FIELDS)) {
                throw new IllegalArgumentException(
                        "capability configuration fields must be exactly " + ROOT_FIELDS);
            }
            Set<String> disabledTools = identities(
                    root, "disabledTools", CapabilityPolicy::requireToolId);
            Set<String> disabledSkills = identities(
                    root, "disabledSkills", CapabilityPolicy::requireSkillName);
            return new ToolResult.Success<>(new CapabilityPolicy(
                    disabledTools, disabledSkills));
        } catch (RuntimeException failure) {
            return failed(failure.getMessage() == null
                    ? "Invalid capability configuration"
                    : failure.getMessage());
        }
    }

    private static Set<String> identities(
            JsonObject root, String field, UnaryOperator<String> validator) {
        JsonElement encoded = required(root, field);
        if (!encoded.isJsonArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        JsonArray array = encoded.getAsJsonArray();
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(field + " entries must be strings");
            }
            String value = validator.apply(element.getAsString());
            if (!values.add(value)) {
                throw new IllegalArgumentException(field + " must not contain duplicates");
            }
        }
        return values;
    }

    private static JsonElement required(JsonObject root, String field) {
        if (!root.has(field) || root.get(field).isJsonNull()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return root.get(field);
    }

    private static ToolResult.Failure<CapabilityPolicy> failed(String message) {
        return new ToolResult.Failure<>("invalid_capability_config", message);
    }
}
