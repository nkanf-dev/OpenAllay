package dev.openallay.guide.ui.hud;

import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideUiConfig;
import dev.openallay.guide.ui.GuideUiProgress;
import dev.openallay.guide.ui.GuideUiRow;
import java.util.List;
import java.util.Objects;

/** Immutable task-result render input. Preview budgets never trim the typed content rows. */
public record GuideHudView(
        GuideUiConfig.Hud hud,
        String assistantName,
        String selectedSession,
        String latestReply,
        String streamingPreview,
        GuideUiProgress progress,
        int otherRunningTasks,
        List<GuideUiRow> rows,
        GuideUiConfig.Fullscreen presentation,
        boolean animationsEnabled) {
    public GuideHudView {
        rows = List.copyOf(rows);
        Objects.requireNonNull(presentation, "presentation");
        if (rows.stream().anyMatch(row -> row instanceof GuideUiRow.User
                || row instanceof GuideUiRow.Persistence)) {
            throw new IllegalArgumentException("HUD task rows cannot contain composer/history rows");
        }
        Objects.requireNonNull(hud, "hud");
        Objects.requireNonNull(assistantName, "assistantName");
        Objects.requireNonNull(selectedSession, "selectedSession");
        Objects.requireNonNull(latestReply, "latestReply");
        Objects.requireNonNull(streamingPreview, "streamingPreview");
        if (otherRunningTasks < 0) {
            throw new IllegalArgumentException("otherRunningTasks must not be negative");
        }
    }

    public GuideHudView(GuideUiConfig.Hud hud, String assistantName, String selectedSession,
            String latestReply, String streamingPreview, GuideUiProgress progress, int otherRunningTasks) {
        this(hud, assistantName, selectedSession, latestReply, streamingPreview, progress,
                otherRunningTasks, List.of(), GuideUiConfig.Fullscreen.defaults(), false);
    }

    public static GuideHudView empty(GuideDisplayConfig config) {
        Objects.requireNonNull(config, "config");
        return new GuideHudView(config.ui().hud(), config.assistantName(), "", "", "", null, 0,
                List.of(), config.ui().fullscreen(), config.animationsEnabled());
    }

    public boolean hasContent() {
        return !rows.isEmpty() || progress != null || otherRunningTasks > 0
                || !latestReply.isBlank() || !streamingPreview.isBlank();
    }
}
