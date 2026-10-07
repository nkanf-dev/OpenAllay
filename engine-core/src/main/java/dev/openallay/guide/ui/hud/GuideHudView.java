package dev.openallay.guide.ui.hud;

import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideUiConfig;
import dev.openallay.guide.ui.GuideUiProgress;
import dev.openallay.guide.ui.GuideUiRow;
import java.util.List;
import java.util.Objects;

/** Immutable task-result render input. Preview budgets never trim the typed content rows. */
@dev.openallay.value.ValueType(GuideHudView.ValueSchemaProvider.class)
public final class GuideHudView {
    private final GuideUiConfig.Hud hud;
    private final String assistantName;
    private final String selectedSession;
    private final String latestReply;
    private final String streamingPreview;
    private final GuideUiProgress progress;
    private final int otherRunningTasks;
    private final List<GuideUiRow> rows;
    private final GuideUiConfig.Fullscreen presentation;
    private final boolean animationsEnabled;
    public GuideHudView(GuideUiConfig.Hud hud, String assistantName, String selectedSession, String latestReply, String streamingPreview, GuideUiProgress progress, int otherRunningTasks, List<GuideUiRow> rows, GuideUiConfig.Fullscreen presentation, boolean animationsEnabled) {

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

        this.hud = hud;
        this.assistantName = assistantName;
        this.selectedSession = selectedSession;
        this.latestReply = latestReply;
        this.streamingPreview = streamingPreview;
        this.progress = progress;
        this.otherRunningTasks = otherRunningTasks;
        this.rows = rows;
        this.presentation = presentation;
        this.animationsEnabled = animationsEnabled;
    }
    public GuideUiConfig.Hud hud() { return hud; }
    public String assistantName() { return assistantName; }
    public String selectedSession() { return selectedSession; }
    public String latestReply() { return latestReply; }
    public String streamingPreview() { return streamingPreview; }
    public GuideUiProgress progress() { return progress; }
    public int otherRunningTasks() { return otherRunningTasks; }
    public List<GuideUiRow> rows() { return rows; }
    public GuideUiConfig.Fullscreen presentation() { return presentation; }
    public boolean animationsEnabled() { return animationsEnabled; }
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
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideHudView)) return false;
        GuideHudView that = (GuideHudView) other;
        return java.util.Objects.equals(hud, that.hud) && java.util.Objects.equals(assistantName, that.assistantName) && java.util.Objects.equals(selectedSession, that.selectedSession) && java.util.Objects.equals(latestReply, that.latestReply) && java.util.Objects.equals(streamingPreview, that.streamingPreview) && java.util.Objects.equals(progress, that.progress) && otherRunningTasks == that.otherRunningTasks && java.util.Objects.equals(rows, that.rows) && java.util.Objects.equals(presentation, that.presentation) && animationsEnabled == that.animationsEnabled;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(hud);
        hash = 31 * hash + java.util.Objects.hashCode(assistantName);
        hash = 31 * hash + java.util.Objects.hashCode(selectedSession);
        hash = 31 * hash + java.util.Objects.hashCode(latestReply);
        hash = 31 * hash + java.util.Objects.hashCode(streamingPreview);
        hash = 31 * hash + java.util.Objects.hashCode(progress);
        hash = 31 * hash + Integer.hashCode(otherRunningTasks);
        hash = 31 * hash + java.util.Objects.hashCode(rows);
        hash = 31 * hash + java.util.Objects.hashCode(presentation);
        hash = 31 * hash + Boolean.hashCode(animationsEnabled);
        return hash;
    }
    @Override public String toString() { return "GuideHudView[hud=" + hud + ", assistantName=" + assistantName + ", selectedSession=" + selectedSession + ", latestReply=" + latestReply + ", streamingPreview=" + streamingPreview + ", progress=" + progress + ", otherRunningTasks=" + otherRunningTasks + ", rows=" + rows + ", presentation=" + presentation + ", animationsEnabled=" + animationsEnabled + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideHudView> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideHudView.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideHudView>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideHudView.class, "hud", GuideHudView::hud), new dev.openallay.value.ValueSchema.Component<>(GuideHudView.class, "assistantName", GuideHudView::assistantName), new dev.openallay.value.ValueSchema.Component<>(GuideHudView.class, "selectedSession", GuideHudView::selectedSession), new dev.openallay.value.ValueSchema.Component<>(GuideHudView.class, "latestReply", GuideHudView::latestReply), new dev.openallay.value.ValueSchema.Component<>(GuideHudView.class, "streamingPreview", GuideHudView::streamingPreview), new dev.openallay.value.ValueSchema.Component<>(GuideHudView.class, "progress", GuideHudView::progress), new dev.openallay.value.ValueSchema.Component<>(GuideHudView.class, "otherRunningTasks", GuideHudView::otherRunningTasks), new dev.openallay.value.ValueSchema.Component<>(GuideHudView.class, "rows", GuideHudView::rows), new dev.openallay.value.ValueSchema.Component<>(GuideHudView.class, "presentation", GuideHudView::presentation), new dev.openallay.value.ValueSchema.Component<>(GuideHudView.class, "animationsEnabled", GuideHudView::animationsEnabled)), arguments -> new GuideHudView((GuideUiConfig.Hud) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4], (GuideUiProgress) arguments[5], (Integer) arguments[6], (List) arguments[7], (GuideUiConfig.Fullscreen) arguments[8], (Boolean) arguments[9]));
        }
    }
}
