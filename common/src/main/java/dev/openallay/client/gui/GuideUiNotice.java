package dev.openallay.client.gui;

import dev.openallay.guide.GuidePendingMessage;
import dev.openallay.guide.GuideSnapshot;
import dev.openallay.tool.ToolResult;
import java.util.Objects;
import java.util.UUID;
import dev.openallay.platform.minecraft.MinecraftComponents;
import net.minecraft.network.chat.Component;

/** Local presentation feedback. Input rejection stays next to the composer, not the model status. */
@dev.openallay.value.ValueType(GuideUiNotice.ValueSchemaProvider.class)
public final class GuideUiNotice {
    private final Severity severity;
    private final Placement placement;
    private final String message;
    public GuideUiNotice(Severity severity, Placement placement, String message) {

        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(message, "message");

        this.severity = severity;
        this.placement = placement;
        this.message = message;
    }
    public Severity severity() { return severity; }
    public Placement placement() { return placement; }
    public String message() { return message; }
public static GuideUiNotice info(String message) { return new GuideUiNotice(Severity.INFO, Placement.COMPOSER, message); }
public static GuideUiNotice success(String message) { return new GuideUiNotice(Severity.SUCCESS, Placement.COMPOSER, message); }
public static GuideUiNotice warning(String message) { return new GuideUiNotice(Severity.WARNING, Placement.COMPOSER, message); }
public static GuideUiNotice error(String message) { return new GuideUiNotice(Severity.ERROR, Placement.COMPOSER, message); }
public static GuideUiNotice acceptedSubmission(GuideClientUiState.SubmissionRoute route, UUID pendingId,
            ToolResult<?> result, GuideSnapshot snapshot, String sessionId) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(sessionId, "sessionId");
        if (result instanceof ToolResult.Failure<?> failure) return error(failure.code() + ": " + failure.message());
        if (!(result instanceof ToolResult.Success<?> success)) return error(
                MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.composer.submit_failed")));
        boolean editing = route == GuideClientUiState.SubmissionRoute.EDIT_PENDING;
        if (editing && !Boolean.TRUE.equals(success.value())) return warning(
                MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.pending.already_consumed")));
        UUID receiptId = editing ? pendingId : success.value() instanceof UUID id ? id : null;
        if (receiptId == null || route == GuideClientUiState.SubmissionRoute.EDIT_INVALID) return error(
                MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.composer.submit_failed")));
        var session = snapshot.sessions().stream().filter(value -> value.sessionId().equals(sessionId)).findFirst().orElse(null);
        if (session != null) {
            GuidePendingMessage pending = session.pendingMessages().stream()
                    .filter(value -> value.id().equals(receiptId)).findFirst().orElse(null);
            if (pending != null) {
                if (pending.failure() != null) return error(pending.failure().code() + ": " + pending.failure().message());
                return info(MinecraftComponents.getString(MinecraftComponents.translatable(pending.kind() == GuidePendingMessage.Kind.STEER
                        ? "screen.openallay.composer.accepted.steer"
                        : "screen.openallay.composer.accepted.follow_up")));
            }
            if (session.requests().stream().anyMatch(request -> request.requestId().equals(receiptId))) return info(
                    MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.composer.accepted.sent")));
        }
        // A drained Follow-up has a fresh request ID; absence alone cannot prove execution.
        return info(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.composer.accepted.message")));
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
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideUiNotice)) return false;
        GuideUiNotice that = (GuideUiNotice) other;
        return java.util.Objects.equals(severity, that.severity) && java.util.Objects.equals(placement, that.placement) && java.util.Objects.equals(message, that.message);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(severity);
        hash = 31 * hash + java.util.Objects.hashCode(placement);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        return hash;
    }
    @Override public String toString() { return "GuideUiNotice[severity=" + severity + ", placement=" + placement + ", message=" + message + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideUiNotice> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideUiNotice.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideUiNotice>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideUiNotice.class, "severity", GuideUiNotice::severity), new dev.openallay.value.ValueSchema.Component<>(GuideUiNotice.class, "placement", GuideUiNotice::placement), new dev.openallay.value.ValueSchema.Component<>(GuideUiNotice.class, "message", GuideUiNotice::message)), arguments -> new GuideUiNotice((Severity) arguments[0], (Placement) arguments[1], (String) arguments[2]));
        }
    }
}
