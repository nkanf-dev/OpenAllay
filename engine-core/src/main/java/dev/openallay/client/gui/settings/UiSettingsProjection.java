package dev.openallay.client.gui.settings;

import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideUiConfig;
import java.util.List;
import java.util.Objects;

/** Only implemented appearance domains; HUD and notification switches are independent. */
@dev.openallay.value.ValueType(UiSettingsProjection.ValueSchemaProvider.class)
public final class UiSettingsProjection {
    private final GuideUiConfig config;
    private final boolean animationsEnabled;
    private final List<Group> groups;
    public UiSettingsProjection(GuideUiConfig config, boolean animationsEnabled, List<Group> groups) {

        Objects.requireNonNull(config, "config");
        groups = List.copyOf(groups);

        this.config = config;
        this.animationsEnabled = animationsEnabled;
        this.groups = groups;
    }
    public GuideUiConfig config() { return config; }
    public boolean animationsEnabled() { return animationsEnabled; }
    public List<Group> groups() { return groups; }
public enum Group {
        FULLSCREEN, HUD, NOTIFICATIONS;
        public String translationKey() {
            return "screen.openallay.settings.ui." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }
public static UiSettingsProjection from(GuideDisplayConfig display) {
        Objects.requireNonNull(display, "display");
        return new UiSettingsProjection(display.ui(), display.animationsEnabled(), List.of(Group.values()));
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UiSettingsProjection)) return false;
        UiSettingsProjection that = (UiSettingsProjection) other;
        return java.util.Objects.equals(config, that.config) && animationsEnabled == that.animationsEnabled && java.util.Objects.equals(groups, that.groups);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(config);
        hash = 31 * hash + Boolean.hashCode(animationsEnabled);
        hash = 31 * hash + java.util.Objects.hashCode(groups);
        return hash;
    }
    @Override public String toString() { return "UiSettingsProjection[config=" + config + ", animationsEnabled=" + animationsEnabled + ", groups=" + groups + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<UiSettingsProjection> schema() {
            return new dev.openallay.value.ValueSchema<>(UiSettingsProjection.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<UiSettingsProjection>>asList(new dev.openallay.value.ValueSchema.Component<>(UiSettingsProjection.class, "config", UiSettingsProjection::config), new dev.openallay.value.ValueSchema.Component<>(UiSettingsProjection.class, "animationsEnabled", UiSettingsProjection::animationsEnabled), new dev.openallay.value.ValueSchema.Component<>(UiSettingsProjection.class, "groups", UiSettingsProjection::groups)), arguments -> new UiSettingsProjection((GuideUiConfig) arguments[0], (Boolean) arguments[1], (List) arguments[2]));
        }
    }
}
