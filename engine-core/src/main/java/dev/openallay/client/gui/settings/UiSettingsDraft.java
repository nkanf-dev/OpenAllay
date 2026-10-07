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

    /** A child editor's complete UI candidate is authoritative, not input from removed parent widgets. */
    public void adopt(GuideDisplayConfig candidate) {
        Objects.requireNonNull(candidate, "candidate");
        ui = candidate.ui();
        animations = candidate.animationsEnabled();
    }

    /** Merge each clean group from publication without overwriting another group's dirty preview. */
    public void published(GuideDisplayConfig next) {
        next = Objects.requireNonNull(next, "next");
        ui = new GuideUiConfig(
                ui.fullscreen().equals(committed.ui().fullscreen()) ? next.ui().fullscreen() : ui.fullscreen(),
                ui.hud().equals(committed.ui().hud()) ? next.ui().hud() : ui.hud(),
                ui.notifications().equals(committed.ui().notifications())
                        ? next.ui().notifications() : ui.notifications());
        if (animations == committed.animationsEnabled()) animations = next.animationsEnabled();
        committed = next;
    }

    /** Save only changed groups over the latest general fields and unrelated UI groups. */
    public GuideDisplayConfig candidate(GuideDisplayConfig latest) {
        Objects.requireNonNull(latest, "latest");
        GuideUiConfig candidate = new GuideUiConfig(
                ui.fullscreen().equals(committed.ui().fullscreen()) ? latest.ui().fullscreen() : ui.fullscreen(),
                ui.hud().equals(committed.ui().hud()) ? latest.ui().hud() : ui.hud(),
                ui.notifications().equals(committed.ui().notifications())
                        ? latest.ui().notifications() : ui.notifications());
        return latest.withUi(candidate).withAnimationsEnabled(animations == committed.animationsEnabled()
                ? latest.animationsEnabled() : animations);
    }

    public void cancel() {
        ui = committed.ui();
        animations = committed.animationsEnabled();
    }

    public void reset(UiSettingsProjection.Group group) {
        switch ((group)) {
case FULLSCREEN:
{
{
                ui = ui.withFullscreen(GuideUiConfig.Fullscreen.defaults());
                animations = GuideDisplayConfig.defaults().animationsEnabled();
            }
break;
}
case HUD:
{
ui = ui.withHud(GuideUiConfig.Hud.defaults());
break;
}
case NOTIFICATIONS:
{
ui = ui.withNotifications(GuideUiConfig.Notifications.defaults());
break;
}
}

    }
}
