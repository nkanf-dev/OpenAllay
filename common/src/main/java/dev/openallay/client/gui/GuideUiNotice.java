package dev.openallay.client.gui;

import dev.openallay.guide.GuidePendingMessage;
import dev.openallay.guide.GuideSnapshot;
import dev.openallay.tool.ToolResult;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.chat.Component;

/** Local presentation feedback. Input rejection stays next to the composer, not the model status. */
public record GuideUiNotice(Severity severity, Placement placement, String message) {
    public GuideUiNotice {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(message, "message");
    }
    public static GuideUiNotice info(String message) { return new GuideUiNotice(Severity.INFO, Placement.COMPOSER, message); }
    public static GuideUiNotice success(String message) { return new GuideUiNotice(Severity.SUCCESS, Placement.COMPOSER, message); }
    public static GuideUiNotice warning(String message) { return new GuideUiNotice(Severity.WARNING, Placement.COMPOSER, message); }
    public static GuideUiNotice error(String message) { return new GuideUiNotice(Severity.ERROR, Placement.COMPOSER, message); }
    /** A service acceptance is not completion or proof that a model has used the message. */
    public static GuideUiNotice acceptedSubmission(GuideClientUiState.SubmissionRoute route, UUID pendingId,
            ToolResult<?> result, GuideSnapshot snapshot, String sessionId) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(sessionId, "sessionId");
        if (result instanceof ToolResult.Failure<?> failure) return error(failure.code() + ": " + failure.message());
        if (!(result instanceof ToolResult.Success<?> success)) return error(
                Component.translatable("screen.openallay.composer.submit_failed").getString());
        boolean editing = route == GuideClientUiState.SubmissionRoute.EDIT_PENDING;
        if (editing && !Boolean.TRUE.equals(success.value())) return warning(
                Component.translatable("screen.openallay.pending.already_consumed").getString());
        UUID receiptId = editing ? pendingId : success.value() instanceof UUID id ? id : null;
        if (receiptId == null || route == GuideClientUiState.SubmissionRoute.EDIT_INVALID) return error(
                Component.translatable("screen.openallay.composer.submit_failed").getString());
        var session = snapshot.sessions().stream().filter(value -> value.sessionId().equals(sessionId)).findFirst().orElse(null);
        if (session != null) {
            GuidePendingMessage pending = session.pendingMessages().stream()
                    .filter(value -> value.id().equals(receiptId)).findFirst().orElse(null);
            if (pending != null) {
                if (pending.failure() != null) return error(pending.failure().code() + ": " + pending.failure().message());
                return info(Component.translatable(pending.kind() == GuidePendingMessage.Kind.STEER
                        ? "screen.openallay.composer.accepted.steer"
                        : "screen.openallay.composer.accepted.follow_up").getString());
            }
            if (session.requests().stream().anyMatch(request -> request.requestId().equals(receiptId))) return info(
                    Component.translatable("screen.openallay.composer.accepted.sent").getString());
        }
        // A drained Follow-up has a fresh request ID; absence alone cannot prove execution.
        return info(Component.translatable("screen.openallay.composer.accepted.message").getString());
    }
    public boolean empty() { return message.isBlank(); }
    public int color() { return switch (severity) {
        case SUCCESS -> OpenAllayWidgetTheme.SUCCESS;
        case INFO -> OpenAllayWidgetTheme.INFO;
        case WARNING -> OpenAllayWidgetTheme.WARNING;
        case ERROR -> OpenAllayWidgetTheme.ERROR;
    }; }
    public enum Severity { INFO, SUCCESS, WARNING, ERROR }
    public enum Placement { HEADER, COMPOSER }
}
