package dev.openallay.guide.ui.hud;

import dev.openallay.guide.GuideRequestSnapshot;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideSessionSnapshot;
import dev.openallay.guide.GuideSnapshot;
import dev.openallay.guide.GuideTimelineEntry;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideUiProgress;
import java.text.BreakIterator;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Owner-thread projection of the narrow HUD fields from already-built snapshot content. */
public final class GuideHudPresenter {
    private final Map<String, Reply> replies = new HashMap<>();
    private UUID actor;

    public GuideHudView project(GuideSnapshot snapshot, GuideDisplayConfig config) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(config, "config");
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
            for (GuideRequestSnapshot request : session.requests()) {
                if (!request.terminal()) runningTasks++;
            }
        }
        replies.keySet().retainAll(presentSessions);
        GuideRequestSnapshot active = selected == null ? null : activeRequest(selected);
        if (active != null) runningTasks--;
        Reply latest = replies.get(snapshot.selectedSession());
        String latestReply = config.ui().hud().showLatestReply() && latest != null
                ? latest.text() : "";
        String streaming = config.ui().hud().showStreamingPreview() && active != null
                ? assistantPreview(active, true) : "";
        return new GuideHudView(
                config.ui().hud(),
                config.assistantName(),
                snapshot.selectedSession(),
                latestReply,
                streaming,
                active == null ? null : GuideUiProgress.from(active.progress()),
                (int) Math.min(Integer.MAX_VALUE, runningTasks));
    }

    public void clear() {
        replies.clear();
        actor = null;
    }

    private void rememberReply(GuideSessionSnapshot session) {
        Reply latest = replies.get(session.sessionId());
        for (GuideRequestSnapshot request : session.requests()) {
            if (request.status() != GuideRequestStatus.COMPLETED || request.terminalAt() == null
                    || latest != null && compare(request, latest) <= 0) continue;
            String text = assistantPreview(request, false);
            if (text.isBlank()) continue;
            latest = new Reply(request.requestId(), request.terminalAt(), request.createdAt(), text);
        }
        if (latest != null) replies.put(session.sessionId(), latest);
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
            if (request.timeline().get(index) instanceof GuideTimelineEntry.Assistant assistant) {
                if (assistant.streaming() != streaming) return "";
                return preview(assistant.semantic().fallbackText());
            }
        }
        return "";
    }

    /** Reads at most 513 code points; grapheme boundaries keep a truncated cluster intact. */
    static String preview(String text) {
        Objects.requireNonNull(text, "text");
        int index = 0;
        int count = 0;
        int contentEnd = 0;
        int contentLimit = GuideHudView.MAX_PREVIEW_CODE_POINTS - 1;
        while (index < text.length() && count <= GuideHudView.MAX_PREVIEW_CODE_POINTS) {
            index += Character.charCount(text.codePointAt(index));
            count++;
            if (count == contentLimit) contentEnd = index;
        }
        if (count <= GuideHudView.MAX_PREVIEW_CODE_POINTS) return text;
        String prefix = text.substring(0, index);
        BreakIterator characters = BreakIterator.getCharacterInstance(Locale.ROOT);
        characters.setText(prefix);
        int boundary = characters.isBoundary(contentEnd)
                ? contentEnd : characters.preceding(contentEnd);
        if (boundary == BreakIterator.DONE) boundary = 0;
        return prefix.substring(0, boundary) + "…";
    }

    private record Reply(UUID requestId, Instant terminalAt, Instant createdAt, String text) {}
}
