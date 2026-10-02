package dev.openallay.guide.ui.hud;

import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideUiConfig;
import dev.openallay.guide.ui.GuideUiProgress;
import java.util.Objects;

/** Immutable render input. It retains no service, transcript, tool result, or semantic document. */
public record GuideHudView(
        GuideUiConfig.Hud hud,
        String assistantName,
        String selectedSession,
        String latestReply,
        String streamingPreview,
        GuideUiProgress progress,
        int otherRunningTasks) {
    public static final int MAX_PREVIEW_CODE_POINTS = 512;

    public GuideHudView {
        Objects.requireNonNull(hud, "hud");
        Objects.requireNonNull(assistantName, "assistantName");
        Objects.requireNonNull(selectedSession, "selectedSession");
        requirePreview(latestReply, "latestReply");
        requirePreview(streamingPreview, "streamingPreview");
        if (otherRunningTasks < 0) {
            throw new IllegalArgumentException("otherRunningTasks must not be negative");
        }
    }

    public static GuideHudView empty(GuideDisplayConfig config) {
        Objects.requireNonNull(config, "config");
        return new GuideHudView(config.ui().hud(), config.assistantName(), "", "", "", null, 0);
    }

    public boolean hasContent() {
        return progress != null || otherRunningTasks > 0
                || !latestReply.isBlank() || !streamingPreview.isBlank();
    }

    private static void requirePreview(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.length() > MAX_PREVIEW_CODE_POINTS * 2
                || value.codePointCount(0, value.length()) > MAX_PREVIEW_CODE_POINTS) {
            throw new IllegalArgumentException(name + " exceeds the HUD preview limit");
        }
    }
}
