package dev.tomewisp.recipe.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tomewisp.recipe.RecipeVisibilityPolicy;
import dev.tomewisp.tool.ToolResult;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class RecipeClientConfigLoader {
    private static final Set<String> ROOT_FIELDS = Set.of(
            "schemaVersion", "visibility", "preferredViewer", "sources");
    private static final Set<String> SOURCE_FIELDS = Set.of("vanilla", "jei", "rei");

    public ToolResult<RecipeClientConfig> load(Path path) {
        Objects.requireNonNull(path, "path");
        if (!Files.exists(path)) {
            return new ToolResult.Success<>(RecipeClientConfig.defaults());
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            return load(reader);
        } catch (IOException failure) {
            return new ToolResult.Failure<>(
                    "invalid_recipe_config", "Unable to read recipe configuration");
        }
    }

    public ToolResult<RecipeClientConfig> load(Reader reader) {
        Objects.requireNonNull(reader, "reader");
        try {
            JsonElement root = JsonParser.parseReader(reader);
            JsonObject object = object(root, "Recipe configuration");
            exactFields(object, ROOT_FIELDS, "recipe configuration");
            int schema = integer(object, "schemaVersion");
            RecipeVisibilityPolicy visibility = enumValue(
                    RecipeVisibilityPolicy.class, string(object, "visibility"));
            RecipeViewerPreference preferred = enumValue(
                    RecipeViewerPreference.class, string(object, "preferredViewer"));
            JsonObject sources = object(required(object, "sources"), "sources");
            exactFields(sources, SOURCE_FIELDS, "recipe sources");
            return new ToolResult.Success<>(new RecipeClientConfig(
                    schema,
                    visibility,
                    preferred,
                    bool(sources, "vanilla"),
                    bool(sources, "jei"),
                    bool(sources, "rei")));
        } catch (RuntimeException failure) {
            return new ToolResult.Failure<>(
                    "invalid_recipe_config",
                    failure.getMessage() == null
                            ? "Invalid recipe configuration"
                            : failure.getMessage());
        }
    }

    private static void exactFields(JsonObject object, Set<String> fields, String name) {
        if (!object.keySet().equals(fields)) {
            throw new IllegalArgumentException(name + " fields must be exactly " + fields);
        }
    }

    private static JsonElement required(JsonObject object, String field) {
        if (!object.has(field) || object.get(field).isJsonNull()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return object.get(field);
    }

    private static JsonObject object(JsonElement value, String name) {
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(name + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static String string(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return value.getAsString();
    }

    private static int integer(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        try {
            return new BigDecimal(value.getAsString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException failure) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
    }

    private static boolean bool(JsonObject object, String field) {
        JsonElement value = required(object, field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    }
}
