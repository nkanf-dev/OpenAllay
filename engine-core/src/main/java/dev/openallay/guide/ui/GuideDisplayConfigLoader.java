package dev.openallay.guide.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.openallay.guide.GuideFailure;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

public final class GuideDisplayConfigLoader {
    private static final Set<String> FIELDS = dev.openallay.util.Java8Collections.setOf("debugMode", "animationsEnabled", "assistantName", "ui");

    @dev.openallay.value.ValueType(Load.ValueSchemaProvider.class)
public static final class Load {
    private final GuideDisplayConfig config;
    private final GuideFailure failure;
    public Load(GuideDisplayConfig config, GuideFailure failure) {

            Objects.requireNonNull(config, "config");

        this.config = config;
        this.failure = failure;
    }
    public GuideDisplayConfig config() { return config; }
    public GuideFailure failure() { return failure; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Load)) return false;
        Load that = (Load) other;
        return java.util.Objects.equals(config, that.config) && java.util.Objects.equals(failure, that.failure);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(config);
        hash = 31 * hash + java.util.Objects.hashCode(failure);
        return hash;
    }
    @Override public String toString() { return "Load[config=" + config + ", failure=" + failure + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Load> schema() {
            return new dev.openallay.value.ValueSchema<>(Load.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Load>>asList(new dev.openallay.value.ValueSchema.Component<>(Load.class, "config", Load::config), new dev.openallay.value.ValueSchema.Component<>(Load.class, "failure", Load::failure)), arguments -> new Load((GuideDisplayConfig) arguments[0], (GuideFailure) arguments[1]));
        }
    }
}

    public Load load(Path path) {
        Objects.requireNonNull(path, "path");
        if (!Files.exists(path)) {
            return new Load(GuideDisplayConfig.defaults(), null);
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            return load(reader);
        } catch (IOException failure) {
            return invalid(failure);
        }
    }

    public Load load(Reader reader) {
        Objects.requireNonNull(reader, "reader");
        try {
            JsonElement parsed = dev.openallay.json.JsonTrees.parse(reader);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("Display configuration must be an object");
            }
            JsonObject object = parsed.getAsJsonObject();
            if (!dev.openallay.json.JsonTrees.keys(object).equals(FIELDS)) {
                Set<String> missing = new java.util.TreeSet<>(FIELDS);
                missing.removeAll(dev.openallay.json.JsonTrees.keys(object));
                Set<String> extra = new java.util.TreeSet<>(dev.openallay.json.JsonTrees.keys(object));
                extra.removeAll(FIELDS);
                throw new IllegalArgumentException(
                        "Display configuration schema mismatch; missing=" + missing
                                + ", extra=" + extra);
            }
            boolean debugMode = bool(object, "debugMode");
            boolean animationsEnabled = bool(object, "animationsEnabled");
            String assistantName = string(object, "assistantName");
            return new Load(
                    new GuideDisplayConfig(
                            debugMode, animationsEnabled, assistantName, ui(object.get("ui"))), null);
        } catch (RuntimeException failure) {
            return invalid(failure);
        }
    }

    private static GuideUiConfig ui(JsonElement value) {
        JsonObject ui = object(value, "ui", dev.openallay.util.Java8Collections.setOf("fullscreen", "hud", "notifications"));
        JsonObject full = object(ui.get("fullscreen"), "fullscreen", dev.openallay.util.Java8Collections.setOf("density", "sessionRailVisible", "theme"));
        JsonObject hud = object(ui.get("hud"), "hud", dev.openallay.util.Java8Collections.setOf("enabled", "anchor", "offsetX", "offsetY", "width", "height", "scale", "backgroundOpacity", "collapsed", "maxReplyLines", "showLatestReply", "showStreamingPreview", "hideWithDebug", "hideOnOtherScreens"));
        JsonObject notifications = object(ui.get("notifications"), "notifications", dev.openallay.util.Java8Collections.setOf("enabled", "policy", "replyCompleted", "cardBatches", "taskFailures", "durationSeconds"));
        return new GuideUiConfig(
                new GuideUiConfig.Fullscreen(
                        enumeration(full, "density", GuideUiConfig.Density.class),
                        bool(full, "sessionRailVisible"),
                        enumeration(full, "theme", GuideUiConfig.Theme.class)),
                new GuideUiConfig.Hud(
                        bool(hud, "enabled"), enumeration(hud, "anchor", GuideUiConfig.Anchor.class),
                        integer(hud, "offsetX"), integer(hud, "offsetY"), integer(hud, "width"),
                        integer(hud, "height"), number(hud, "scale"), number(hud, "backgroundOpacity"),
                        bool(hud, "collapsed"), integer(hud, "maxReplyLines"), bool(hud, "showLatestReply"),
                        bool(hud, "showStreamingPreview"), bool(hud, "hideWithDebug"),
                        bool(hud, "hideOnOtherScreens")),
                new GuideUiConfig.Notifications(
                        bool(notifications, "enabled"),
                        enumeration(notifications, "policy", GuideUiConfig.NotificationPolicy.class),
                        bool(notifications, "replyCompleted"), bool(notifications, "cardBatches"),
                        bool(notifications, "taskFailures"), integer(notifications, "durationSeconds")));
    }

    private static JsonObject object(JsonElement value, String field, Set<String> fields) {
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        JsonObject result = value.getAsJsonObject();
        if (!dev.openallay.json.JsonTrees.keys(result).equals(fields)) {
            throw new IllegalArgumentException(field + " has missing or unknown fields");
        }
        return result;
    }

    private static <E extends Enum<E>> E enumeration(JsonObject object, String field, Class<E> type) {
        return Enum.valueOf(type, string(object, field));
    }

    private static double number(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        double result = value.getAsDouble();
        if (!Double.isFinite(result)) throw new IllegalArgumentException(field + " must be finite");
        return result;
    }

    private static int integer(JsonObject object, String field) {
        number(object, field);
        try {
            return object.get(field).getAsBigDecimal().intValueExact();
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException(field + " must be an integer", failure);
        }
    }

    private static Load invalid(Exception failure) {
        String message = failure.getMessage();
        return new Load(
                GuideDisplayConfig.defaults(),
                new GuideFailure(
                        "invalid_display_config",
                        message == null || dev.openallay.util.Java8Strings.isBlank(message)
                                ? "Invalid display configuration"
                                : message));
    }

    private static boolean bool(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static String string(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return value.getAsString();
    }
}
