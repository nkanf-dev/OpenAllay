package dev.openallay.guide.ui.hud;

import dev.openallay.guide.GuideRequestSnapshot;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideSessionSnapshot;
import dev.openallay.guide.GuideSnapshot;
import dev.openallay.guide.GuideTimelineEntry;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideUiProgress;
import dev.openallay.guide.ui.GuideUiRow;
import dev.openallay.guide.ui.GuideUiView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Owner-thread latest-task projection from the same validated rows used by fullscreen. */
public final class GuideHudPresenter {
    private final Map<String, Reply> replies = new HashMap<>();
    private final Map<String, Reply> results = new HashMap<>();
    private final Map<UUID, Projected> projected = new HashMap<>();
    private UUID actor;
    private GuideSnapshot lastSnapshot;
    private GuideDisplayConfig lastConfig;
    private boolean lastInteractive;
    private GuideHudView lastView;

    public GuideHudView project(GuideSnapshot snapshot, GuideDisplayConfig config) {
        return project(snapshot, config, false);
    }

    /** Explicit interaction always exposes complete content, regardless of passive preview toggles. */
    public GuideHudView projectInteractive(GuideSnapshot snapshot, GuideDisplayConfig config) {
        return project(snapshot, config, true);
    }

    private GuideHudView project(GuideSnapshot snapshot, GuideDisplayConfig config, boolean interactive) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(config, "config");
        if (snapshot == lastSnapshot && config.equals(lastConfig) && interactive == lastInteractive) return lastView;
        if (!snapshot.actorId().equals(actor)) {
            clear();
            actor = snapshot.actorId();
        }
        Set<String> presentSessions = new HashSet<>();
        GuideSessionSnapshot selected = null;
        long runningTasks = 0;
        for (GuideSessionSnapshot session : snapshot.sessions()) {
            presentSessions.add(session.sessionId());
            if (session.sessionId().equals(snapshot.selectedSession())) selected = session;
            rememberReply(session);
            rememberResult(session);
            for (GuideRequestSnapshot request : session.requests()) {
                if (!request.terminal()) runningTasks++;
            }
        }
        replies.keySet().retainAll(presentSessions);
        results.keySet().retainAll(presentSessions);
        GuideRequestSnapshot active = selected == null ? null : activeRequest(selected);
        if (active != null) runningTasks--;
        Reply latest = replies.get(snapshot.selectedSession());
        String latestReply = (interactive || config.ui().hud().showLatestReply()) && latest != null
                ? latest.text() : "";
        String streaming = (interactive || config.ui().hud().showStreamingPreview()) && active != null
                ? assistantPreview(active, true) : "";
        List<GuideUiRow> rows = new ArrayList<>();
        Reply result = results.get(snapshot.selectedSession());
        Set<UUID> retained = new HashSet<>();
        if (interactive && selected != null) {
            // Read only the admitted window. Older loaded cards stay reachable without history I/O.
            List<GuideRequestSnapshot> admitted = new ArrayList<>();
            for (GuideRequestSnapshot request : selected.requests()) {
                if (retained.add(request.requestId())) admitted.add(request);
            }
            if (result != null && retained.add(result.requestId())) {
                // A historical window can omit the last result. Keep it beside current work,
                // without reordering the authoritative sequence or putting it after a live tail.
                int beforeActive = active == null ? -1 : admitted.indexOf(active);
                admitted.add(beforeActive < 0 ? admitted.size() : beforeActive, result.request());
            }
            admitted.forEach(request -> rows.addAll(rows(request, config)));
        } else {
            if (config.ui().hud().showLatestReply() && result != null) rows.addAll(rows(result.request(), config));
            if (active != null) {
                for (GuideUiRow row : rows(active, config)) {
                    if (!(row instanceof GuideUiRow.Assistant) || config.ui().hud().showStreamingPreview()) rows.add(row);
                }
            }
        }
        replies.values().forEach(value -> retained.add(value.requestId()));
        results.values().forEach(value -> retained.add(value.requestId()));
        if (active != null) retained.add(active.requestId());
        projected.keySet().retainAll(retained);
        lastSnapshot = snapshot;
        lastConfig = config;
        lastInteractive = interactive;
        lastView = new GuideHudView(
                config.ui().hud(),
                config.assistantName(),
                snapshot.selectedSession(),
                latestReply,
                streaming,
                active == null ? null : GuideUiProgress.from(active.progress()),
                (int) Math.min(Integer.MAX_VALUE, runningTasks), rows,
                config.ui().fullscreen(), config.animationsEnabled());
        return lastView;
    }

    public void clear() {
        replies.clear();
        results.clear();
        projected.clear();
        actor = null;
        lastSnapshot = null;
        lastConfig = null;
        lastView = null;
    }

    private void rememberReply(GuideSessionSnapshot session) {
        Reply latest = replies.get(session.sessionId());
        for (GuideRequestSnapshot request : session.requests()) {
            if (request.status() != GuideRequestStatus.COMPLETED || request.terminalAt() == null
                    || latest != null && (latest.request() == request || compare(request, latest) < 0)) continue;
            String text = assistantPreview(request, false);
            if (text.isBlank()) continue;
            latest = new Reply(request, text);
        }
        if (latest != null) replies.put(session.sessionId(), latest);
    }

    private void rememberResult(GuideSessionSnapshot session) {
        Reply latest = results.get(session.sessionId());
        for (GuideRequestSnapshot request : session.requests()) {
            if (!request.terminal() || request.terminalAt() == null
                    || latest != null && (latest.request() == request || compare(request, latest) < 0)) continue;
            if (request.timeline().isEmpty() && request.status() == GuideRequestStatus.COMPLETED) continue;
            Reply reply = replies.get(session.sessionId());
            latest = reply != null && reply.request() == request
                    ? reply : new Reply(request, assistantPreview(request, false));
        }
        if (latest != null) results.put(session.sessionId(), latest);
    }

    private List<GuideUiRow> rows(GuideRequestSnapshot request, GuideDisplayConfig config) {
        Projected cached = projected.get(request.requestId());
        if (cached == null || cached.request() != request || cached.debug() != config.debugMode()) {
            cached = new Projected(request, config.debugMode(), GuideUiView.projectRequestRows(request, config)
                    .stream().filter(row -> !(row instanceof GuideUiRow.User)).toList());
            projected.put(request.requestId(), cached);
        }
        return cached.rows();
    }

    /** terminalAt is authoritative; stable ties prevent an older page from replacing cached text. */
    private static int compare(GuideRequestSnapshot request, Reply reply) {
        int terminal = request.terminalAt().compareTo(reply.terminalAt());
        if (terminal != 0) return terminal;
        int created = request.createdAt().compareTo(reply.createdAt());
        return created != 0 ? created : request.requestId().compareTo(reply.requestId());
    }

    private static GuideRequestSnapshot activeRequest(GuideSessionSnapshot session) {
        GuideRequestSnapshot latest = null;
        for (GuideRequestSnapshot request : session.requests()) {
            if (request.terminal()) continue;
            if (request.requestId().equals(session.workingRequestId())) return request;
            if (latest == null || !request.createdAt().isBefore(latest.createdAt())) latest = request;
        }
        return latest;
    }

    private static String assistantPreview(GuideRequestSnapshot request, boolean streaming) {
        for (int index = request.timeline().size() - 1; index >= 0; index--) {
            {
final java.lang.Object $oaPattern0_value = request.timeline().get(index);
final boolean $oaPattern0_match = $oaPattern0_value instanceof GuideTimelineEntry.Assistant;
GuideTimelineEntry.Assistant $oaPattern0_bound = $oaPattern0_match ? (GuideTimelineEntry.Assistant) $oaPattern0_value : null;
if ($oaPattern0_match) {
                if ($oaPattern0_bound.streaming() != streaming) return "";
                return $oaPattern0_bound.semantic().fallbackText();
            }
}
        }
        return "";
    }

    @dev.openallay.value.ValueType(Reply.ValueSchemaProvider.class)
private static final class Reply {
    private final GuideRequestSnapshot request;
    private final String text;
    private Reply(GuideRequestSnapshot request, String text) {
        this.request = request;
        this.text = text;
    }
    public GuideRequestSnapshot request() { return request; }
    public String text() { return text; }
UUID requestId() { return request.requestId(); }
Instant terminalAt() { return request.terminalAt(); }
Instant createdAt() { return request.createdAt(); }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Reply)) return false;
        Reply that = (Reply) other;
        return java.util.Objects.equals(request, that.request) && java.util.Objects.equals(text, that.text);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(request);
        hash = 31 * hash + java.util.Objects.hashCode(text);
        return hash;
    }
    @Override public String toString() { return "Reply[request=" + request + ", text=" + text + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Reply> schema() {
            return new dev.openallay.value.ValueSchema<>(Reply.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Reply>>asList(new dev.openallay.value.ValueSchema.Component<>(Reply.class, "request", Reply::request), new dev.openallay.value.ValueSchema.Component<>(Reply.class, "text", Reply::text)), arguments -> new Reply((GuideRequestSnapshot) arguments[0], (String) arguments[1]));
        }
    }
}
    @dev.openallay.value.ValueType(Projected.ValueSchemaProvider.class)
private static final class Projected {
    private final GuideRequestSnapshot request;
    private final boolean debug;
    private final List<GuideUiRow> rows;
    private Projected(GuideRequestSnapshot request, boolean debug, List<GuideUiRow> rows) {
        this.request = request;
        this.debug = debug;
        this.rows = rows;
    }
    public GuideRequestSnapshot request() { return request; }
    public boolean debug() { return debug; }
    public List<GuideUiRow> rows() { return rows; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Projected)) return false;
        Projected that = (Projected) other;
        return java.util.Objects.equals(request, that.request) && debug == that.debug && java.util.Objects.equals(rows, that.rows);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(request);
        hash = 31 * hash + Boolean.hashCode(debug);
        hash = 31 * hash + java.util.Objects.hashCode(rows);
        return hash;
    }
    @Override public String toString() { return "Projected[request=" + request + ", debug=" + debug + ", rows=" + rows + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Projected> schema() {
            return new dev.openallay.value.ValueSchema<>(Projected.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Projected>>asList(new dev.openallay.value.ValueSchema.Component<>(Projected.class, "request", Projected::request), new dev.openallay.value.ValueSchema.Component<>(Projected.class, "debug", Projected::debug), new dev.openallay.value.ValueSchema.Component<>(Projected.class, "rows", Projected::rows)), arguments -> new Projected((GuideRequestSnapshot) arguments[0], (Boolean) arguments[1], (List) arguments[2]));
        }
    }
}
}
