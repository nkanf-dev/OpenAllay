package dev.openallay.client.gui.settings;

import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideUiConfig;
import java.util.List;
import java.util.Objects;

/** Only implemented appearance domains; HUD and notification switches are independent. */
public record UiSettingsProjection(GuideUiConfig config, boolean animationsEnabled, List<Group> groups) {
    public enum Group {
        FULLSCREEN, HUD;
        public String translationKey() {
            return "screen.openallay.settings.ui." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public UiSettingsProjection {
        Objects.requireNonNull(config, "config");
        groups = List.copyOf(groups);
    }

    public static UiSettingsProjection from(GuideDisplayConfig display) {
        Objects.requireNonNull(display, "display");
        return new UiSettingsProjection(display.ui(), display.animationsEnabled(), List.of(Group.values()));
    }
}
