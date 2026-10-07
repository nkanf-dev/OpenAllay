package dev.openallay.guide.ui;

import dev.openallay.guide.GuideClientModelProfile;
import dev.openallay.guide.GuideModelMode;
import dev.openallay.guide.GuideModelSelection;
import dev.openallay.guide.GuideRequestSnapshot;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideSessionSnapshot;
import dev.openallay.guide.GuideSnapshot;
import dev.openallay.guide.GuideTimelineEntry;
import java.util.ArrayList;
import java.util.List;

/** Pure, immutable screen view derived from the one GuideService snapshot. */
@dev.openallay.value.ValueType(GuideUiView.ValueSchemaProvider.class)
public final class GuideUiView {
    private final String selectedSession;
    private final GuideModelMode modelMode;
    private final boolean clientModelAvailable;
    private final boolean serverModelAvailable;
    private final boolean canSend;
    private final boolean canCancel;
    private final boolean canRetry;
    private final GuideUiProgress progress;
    private final List<GuideUiSession> sessions;
    private final List<GuideUiRow> rows;
    private final List<GuideUiModelChoice> modelChoices;
    private final String capabilityMessage;
    public GuideUiView(String selectedSession, GuideModelMode modelMode, boolean clientModelAvailable, boolean serverModelAvailable, boolean canSend, boolean canCancel, boolean canRetry, GuideUiProgress progress, List<GuideUiSession> sessions, List<GuideUiRow> rows, List<GuideUiModelChoice> modelChoices, String capabilityMessage) {

        sessions = List.copyOf(sessions);
        rows = List.copyOf(rows);
        modelChoices = List.copyOf(modelChoices);
        if (modelChoices.stream().filter(GuideUiModelChoice::selected).count() != 1) {
            throw new IllegalArgumentException("exactly one model choice must be selected");
        }
        if (modelChoices.stream().filter(GuideUiModelChoice::running).count() > 1) {
            throw new IllegalArgumentException("at most one model choice may be running");
        }

        this.selectedSession = selectedSession;
        this.modelMode = modelMode;
        this.clientModelAvailable = clientModelAvailable;
        this.serverModelAvailable = serverModelAvailable;
        this.canSend = canSend;
        this.canCancel = canCancel;
        this.canRetry = canRetry;
        this.progress = progress;
        this.sessions = sessions;
        this.rows = rows;
        this.modelChoices = modelChoices;
        this.capabilityMessage = capabilityMessage;
    }
    public String selectedSession() { return selectedSession; }
    public GuideModelMode modelMode() { return modelMode; }
    public boolean clientModelAvailable() { return clientModelAvailable; }
    public boolean serverModelAvailable() { return serverModelAvailable; }
    public boolean canSend() { return canSend; }
    public boolean canCancel() { return canCancel; }
    public boolean canRetry() { return canRetry; }
    public GuideUiProgress progress() { return progress; }
    public List<GuideUiSession> sessions() { return sessions; }
    public List<GuideUiRow> rows() { return rows; }
    public List<GuideUiModelChoice> modelChoices() { return modelChoices; }
    public String capabilityMessage() { return capabilityMessage; }
public GuideUiModelChoice selectedModel() {
        return modelChoices.stream().filter(GuideUiModelChoice::selected)
                .findFirst().orElseThrow();
    }
public dev.openallay.model.image.ImageInputCapability selectedImageInputCapability() {
        return selectedModel().imageInput();
    }
public GuideUiModelChoice runningModel() {
        return modelChoices.stream().filter(GuideUiModelChoice::running)
                .findFirst().orElseGet(this::selectedModel);
    }
public boolean modelSwitchPending() {
        return modelChoices.stream().anyMatch(GuideUiModelChoice::running)
                && !runningModel().selection().equals(selectedModel().selection());
    }
public static GuideUiView from(GuideSnapshot snapshot) {
        return from(snapshot, GuideDisplayConfig.defaults());
    }
public static GuideUiView from(GuideSnapshot snapshot, GuideDisplayConfig displayConfig) {
        java.util.Objects.requireNonNull(displayConfig, "displayConfig");
        GuideSessionSnapshot selected = snapshot.sessions().stream()
                .filter(value -> value.sessionId().equals(snapshot.selectedSession()))
                .findFirst().orElseThrow();
        GuideRequestSnapshot active = selected.requests().stream()
                .filter(value -> !value.terminal()).reduce((first, second) -> second).orElse(null);
        GuideRequestSnapshot retry = selected.requests().stream()
                .filter(value -> value.status() == GuideRequestStatus.FAILED
                        || value.status() == GuideRequestStatus.CANCELLED
                        || value.status() == GuideRequestStatus.INTERRUPTED)
                .reduce((first, second) -> second).orElse(null);
        List<GuideUiModelChoice> modelChoices = modelChoices(snapshot, active);
        GuideUiModelChoice selectedModel = modelChoices.stream()
                .filter(GuideUiModelChoice::selected)
                .findFirst().orElseThrow();
        boolean targetAvailable = selectedModel.available();
        List<GuideUiSession> sessions = snapshot.sessions().stream()
                .map(value -> new GuideUiSession(
                        value.sessionId(),
                        value.sessionId().equals(snapshot.selectedSession()),
                        value.requests().stream().anyMatch(request -> !request.terminal()),
                        Math.toIntExact(Math.min(
                                Integer.MAX_VALUE, value.historyWindow().totalRequests()))))
                .toList();
        List<GuideUiRow> rows = new ArrayList<>();
        switch (snapshot.persistence().state()) {
            case LOADING -> rows.add(new GuideUiRow.Persistence(
                    snapshot.persistence().state(),
                    "screen.openallay.history.loading",
                    null));
            case UNAVAILABLE -> rows.add(new GuideUiRow.Persistence(
                    snapshot.persistence().state(),
                    "screen.openallay.history.unavailable",
                    snapshot.persistence().failure()));
            case SAVING, DISABLED, AVAILABLE -> { }
        }
        for (GuideRequestSnapshot request : selected.requests()) {
            rows.addAll(projectRequestRows(request, displayConfig));
        }
        GuideUiModelChoice runningModel = modelChoices.stream()
                .filter(GuideUiModelChoice::running)
                .findFirst().orElse(selectedModel);
        String capability = !targetAvailable
                ? "所选模型未配置或不可用：" + selectedModel.displayName()
                : !runningModel.selection().equals(selectedModel.selection())
                        ? "正在使用 " + runningModel.displayName()
                                + "；下次请求 " + selectedModel.displayName()
                        : "当前模型 " + selectedModel.displayName();
        return new GuideUiView(
                snapshot.selectedSession(),
                snapshot.modelMode(),
                snapshot.clientModelAvailable(),
                snapshot.serverModelAvailable(),
                targetAvailable
                        && snapshot.persistence().state()
                                != dev.openallay.guide.GuidePersistenceSnapshot.State.LOADING,
                active != null || selected.workingRequestId() != null || !selected.pendingMessages().isEmpty(),
                retry != null && active == null && selected.workingRequestId() == null,
                active == null ? null : GuideUiProgress.from(active.progress()),
                sessions,
                rows,
                modelChoices,
                capability);
    }
public static List<GuideUiRow> projectRequestRows(
            GuideRequestSnapshot request, GuideDisplayConfig displayConfig) {
        java.util.Objects.requireNonNull(request, "request");
        java.util.Objects.requireNonNull(displayConfig, "displayConfig");
        List<GuideUiRow> rows = new ArrayList<>();
        rows.add(new GuideUiRow.User(request.requestId(), request.userMessage()));
        for (GuideTimelineEntry entry : request.timeline()) {
            java.util.Objects.requireNonNull(entry);
            if (entry instanceof GuideTimelineEntry.User user) {
                rows.add(new GuideUiRow.User(user.messageId(), user.text()));
            } else if (entry instanceof GuideTimelineEntry.Assistant assistant) {
                rows.add(new GuideUiRow.Assistant(
                        request.requestId(),
                        assistant.ordinal(),
                        assistant.text(),
                        assistant.semantic(),
                        assistant.streaming(),
                        assistant.sources()));
            } else if (entry instanceof GuideTimelineEntry.Tool tool) {
                rows.add(new GuideUiRow.Tool(
                        request.requestId(),
                        tool.ordinal(),
                        tool.activity(),
                        GuideToolDetailPresenter.project(
                                tool.activity(), displayConfig.debugMode())
                                .forRequest(request.terminal())));
            } else {
                throw new IncompatibleClassChangeError();
            }
        }
        if (request.status() == GuideRequestStatus.FAILED
                || request.status() == GuideRequestStatus.CANCELLED
                || request.status() == GuideRequestStatus.INTERRUPTED) {
            rows.add(new GuideUiRow.Status(
                    request.requestId(),
                    request.status(),
                    request.failure() == null ? request.status().name() : request.failure().message(),
                    request.failure()));
        }
        return List.copyOf(rows);
    }
private static List<GuideUiModelChoice> modelChoices(
            GuideSnapshot snapshot, GuideRequestSnapshot active) {
        List<ChoiceSeed> seeds = new ArrayList<>();
        for (GuideClientModelProfile profile : snapshot.clientProfiles()) {
            if (profile.enabled()) {
                seeds.add(new ChoiceSeed(
                        GuideModelSelection.client(profile.id()),
                        profile.displayName(),
                        ModelOrigin.CLIENT,
                        true,
                        profile.available()));
            }
        }
        boolean compatibilityClientAvailable = snapshot.clientProfiles().isEmpty()
                && snapshot.clientModelAvailable();
        ensureClientChoice(
                seeds,
                snapshot.clientProfiles(),
                snapshot.modelSelection(),
                compatibilityClientAvailable);
        if (active != null) {
            ensureClientChoice(
                    seeds,
                    snapshot.clientProfiles(),
                    active.modelSelection(),
                    compatibilityClientAvailable
                            && active.modelSelection().equals(snapshot.modelSelection()));
        }
        boolean serverRelevant = snapshot.serverModelAvailable()
                || snapshot.modelSelection().kind() == GuideModelSelection.Kind.SERVER
                || active != null
                        && active.modelSelection().kind() == GuideModelSelection.Kind.SERVER;
        if (serverRelevant) {
            seeds.add(new ChoiceSeed(
                    GuideModelSelection.server(),
                    snapshot.serverModel()
                            .map(dev.openallay.guide.GuideContextSpec::canonicalModelId)
                            .orElse("Server model"),
                    ModelOrigin.SERVER,
                    false,
                    snapshot.serverModelAvailable()));
        }
        GuideModelSelection running = active == null ? null : active.modelSelection();
        return seeds.stream().map(seed -> new GuideUiModelChoice(
                seed.selection(),
                seed.displayName(),
                seed.origin(),
                seed.editable(),
                seed.available(),
                seed.selection().equals(snapshot.modelSelection()),
                seed.selection().equals(running),
                snapshot.imageInputCapability(seed.selection()),
                seed.selection().kind() == GuideModelSelection.Kind.SERVER
                        ? snapshot.serverImageInputSource()
                        : snapshot.clientProfiles().stream()
                                .filter(profile -> profile.id().equals(seed.selection().profileId()))
                                .map(GuideClientModelProfile::imageInputSource)
                                .filter(java.util.Objects::nonNull).findFirst().orElse(null))).toList();
    }
private static void ensureClientChoice(
            List<ChoiceSeed> seeds,
            List<GuideClientModelProfile> profiles,
            GuideModelSelection selection,
            boolean compatibilityAvailable) {
        if (selection.kind() != GuideModelSelection.Kind.CLIENT
                || seeds.stream().anyMatch(seed -> seed.selection().equals(selection))) {
            return;
        }
        GuideClientModelProfile retained = profiles.stream()
                .filter(profile -> profile.id().equals(selection.profileId()))
                .findFirst().orElse(null);
        seeds.add(new ChoiceSeed(
                selection,
                retained == null ? selection.profileId() : retained.displayName(),
                ModelOrigin.CLIENT,
                true,
                retained == null ? compatibilityAvailable : retained.available()));
    }
@dev.openallay.value.ValueType(ChoiceSeed.ValueSchemaProvider.class)
private static final class ChoiceSeed {
    private final GuideModelSelection selection;
    private final String displayName;
    private final ModelOrigin origin;
    private final boolean editable;
    private final boolean available;
    private ChoiceSeed(GuideModelSelection selection, String displayName, ModelOrigin origin, boolean editable, boolean available) {
        this.selection = selection;
        this.displayName = displayName;
        this.origin = origin;
        this.editable = editable;
        this.available = available;
    }
    public GuideModelSelection selection() { return selection; }
    public String displayName() { return displayName; }
    public ModelOrigin origin() { return origin; }
    public boolean editable() { return editable; }
    public boolean available() { return available; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ChoiceSeed)) return false;
        ChoiceSeed that = (ChoiceSeed) other;
        return java.util.Objects.equals(selection, that.selection) && java.util.Objects.equals(displayName, that.displayName) && java.util.Objects.equals(origin, that.origin) && editable == that.editable && available == that.available;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(selection);
        hash = 31 * hash + java.util.Objects.hashCode(displayName);
        hash = 31 * hash + java.util.Objects.hashCode(origin);
        hash = 31 * hash + Boolean.hashCode(editable);
        hash = 31 * hash + Boolean.hashCode(available);
        return hash;
    }
    @Override public String toString() { return "ChoiceSeed[selection=" + selection + ", displayName=" + displayName + ", origin=" + origin + ", editable=" + editable + ", available=" + available + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ChoiceSeed> schema() {
            return new dev.openallay.value.ValueSchema<>(ChoiceSeed.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ChoiceSeed>>asList(new dev.openallay.value.ValueSchema.Component<>(ChoiceSeed.class, "selection", ChoiceSeed::selection), new dev.openallay.value.ValueSchema.Component<>(ChoiceSeed.class, "displayName", ChoiceSeed::displayName), new dev.openallay.value.ValueSchema.Component<>(ChoiceSeed.class, "origin", ChoiceSeed::origin), new dev.openallay.value.ValueSchema.Component<>(ChoiceSeed.class, "editable", ChoiceSeed::editable), new dev.openallay.value.ValueSchema.Component<>(ChoiceSeed.class, "available", ChoiceSeed::available)), arguments -> new ChoiceSeed((GuideModelSelection) arguments[0], (String) arguments[1], (ModelOrigin) arguments[2], (Boolean) arguments[3], (Boolean) arguments[4]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideUiView)) return false;
        GuideUiView that = (GuideUiView) other;
        return java.util.Objects.equals(selectedSession, that.selectedSession) && java.util.Objects.equals(modelMode, that.modelMode) && clientModelAvailable == that.clientModelAvailable && serverModelAvailable == that.serverModelAvailable && canSend == that.canSend && canCancel == that.canCancel && canRetry == that.canRetry && java.util.Objects.equals(progress, that.progress) && java.util.Objects.equals(sessions, that.sessions) && java.util.Objects.equals(rows, that.rows) && java.util.Objects.equals(modelChoices, that.modelChoices) && java.util.Objects.equals(capabilityMessage, that.capabilityMessage);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(selectedSession);
        hash = 31 * hash + java.util.Objects.hashCode(modelMode);
        hash = 31 * hash + Boolean.hashCode(clientModelAvailable);
        hash = 31 * hash + Boolean.hashCode(serverModelAvailable);
        hash = 31 * hash + Boolean.hashCode(canSend);
        hash = 31 * hash + Boolean.hashCode(canCancel);
        hash = 31 * hash + Boolean.hashCode(canRetry);
        hash = 31 * hash + java.util.Objects.hashCode(progress);
        hash = 31 * hash + java.util.Objects.hashCode(sessions);
        hash = 31 * hash + java.util.Objects.hashCode(rows);
        hash = 31 * hash + java.util.Objects.hashCode(modelChoices);
        hash = 31 * hash + java.util.Objects.hashCode(capabilityMessage);
        return hash;
    }
    @Override public String toString() { return "GuideUiView[selectedSession=" + selectedSession + ", modelMode=" + modelMode + ", clientModelAvailable=" + clientModelAvailable + ", serverModelAvailable=" + serverModelAvailable + ", canSend=" + canSend + ", canCancel=" + canCancel + ", canRetry=" + canRetry + ", progress=" + progress + ", sessions=" + sessions + ", rows=" + rows + ", modelChoices=" + modelChoices + ", capabilityMessage=" + capabilityMessage + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideUiView> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideUiView.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideUiView>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideUiView.class, "selectedSession", GuideUiView::selectedSession), new dev.openallay.value.ValueSchema.Component<>(GuideUiView.class, "modelMode", GuideUiView::modelMode), new dev.openallay.value.ValueSchema.Component<>(GuideUiView.class, "clientModelAvailable", GuideUiView::clientModelAvailable), new dev.openallay.value.ValueSchema.Component<>(GuideUiView.class, "serverModelAvailable", GuideUiView::serverModelAvailable), new dev.openallay.value.ValueSchema.Component<>(GuideUiView.class, "canSend", GuideUiView::canSend), new dev.openallay.value.ValueSchema.Component<>(GuideUiView.class, "canCancel", GuideUiView::canCancel), new dev.openallay.value.ValueSchema.Component<>(GuideUiView.class, "canRetry", GuideUiView::canRetry), new dev.openallay.value.ValueSchema.Component<>(GuideUiView.class, "progress", GuideUiView::progress), new dev.openallay.value.ValueSchema.Component<>(GuideUiView.class, "sessions", GuideUiView::sessions), new dev.openallay.value.ValueSchema.Component<>(GuideUiView.class, "rows", GuideUiView::rows), new dev.openallay.value.ValueSchema.Component<>(GuideUiView.class, "modelChoices", GuideUiView::modelChoices), new dev.openallay.value.ValueSchema.Component<>(GuideUiView.class, "capabilityMessage", GuideUiView::capabilityMessage)), arguments -> new GuideUiView((String) arguments[0], (GuideModelMode) arguments[1], (Boolean) arguments[2], (Boolean) arguments[3], (Boolean) arguments[4], (Boolean) arguments[5], (Boolean) arguments[6], (GuideUiProgress) arguments[7], (List) arguments[8], (List) arguments[9], (List) arguments[10], (String) arguments[11]));
        }
    }
}
