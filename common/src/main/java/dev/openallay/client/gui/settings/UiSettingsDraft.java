package dev.openallay.client.gui.settings;

import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideUiConfig;
import java.util.Objects;

/** In-memory preview. No render, widget change or cancel performs IO. */
public final class UiSettingsDraft {
    private GuideUiConfig ui;
    private boolean animations;
    private GuideDisplayConfig committed;

    public UiSettingsDraft(GuideDisplayConfig committed) {
        this.committed = Objects.requireNonNull(committed, "committed");
        cancel();
    }

    public GuideUiConfig ui() { return ui; }
    public boolean animationsEnabled() { return animations; }
    public boolean dirty() {
        return !ui.equals(committed.ui()) || animations != committed.animationsEnabled();
    }

    public void preview(GuideUiConfig value) { ui = Objects.requireNonNull(value, "ui"); }
    public void previewAnimations(boolean value) { animations = value; }

    /** Service publication may change nickname/debug; never overwrite a dirty preview. */
    public void published(GuideDisplayConfig next) {
        boolean retain = dirty();
        committed = Objects.requireNonNull(next, "next");
        if (!retain) cancel();
    }

    /** Build Apply from the latest general fields, not from an old full config snapshot. */
    public GuideDisplayConfig candidate(GuideDisplayConfig latest) {
        return Objects.requireNonNull(latest, "latest").withUi(ui).withAnimationsEnabled(animations);
    }

    public void cancel() {
        ui = committed.ui();
        animations = committed.animationsEnabled();
    }

    public void reset(UiSettingsProjection.Group group) {
        switch (group) {
            case FULLSCREEN -> {
                ui = ui.withFullscreen(GuideUiConfig.Fullscreen.defaults());
                animations = GuideDisplayConfig.defaults().animationsEnabled();
            }
            case HUD -> ui = ui.withHud(GuideUiConfig.Hud.defaults());
            case NOTIFICATIONS -> ui = ui.withNotifications(GuideUiConfig.Notifications.defaults());
        }
    }
}
