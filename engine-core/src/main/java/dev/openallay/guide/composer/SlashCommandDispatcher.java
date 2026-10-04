package dev.openallay.guide.composer;

import dev.openallay.guide.GuideCompactResult;
import dev.openallay.guide.GuideService;
import dev.openallay.tool.ToolResult;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A control command never reaches ordinary model submission or Minecraft command execution. */
public final class SlashCommandDispatcher {
    private SlashCommandDispatcher() {}
    public record Dispatch(boolean handled, boolean retainDraft, String normalizedText) {}
    public record Completion(boolean successful, String code, GuideCompactResult result) {}

    /** Completion follows the endpoint future; the screen must marshal it onto its owner thread. */
    public static Dispatch dispatch(String draft, GuideService service, Consumer<Completion> completion) {
        Objects.requireNonNull(service, "service");
        return dispatch(draft, service::compactSelectedSession, completion);
    }

    /** The injectable endpoint keeps parser/control routing independently testable. */
    public static Dispatch dispatch(String draft,
            Supplier<CompletableFuture<ToolResult<GuideCompactResult>>> compact,
            Consumer<Completion> completion) {
        Objects.requireNonNull(compact, "compact");
        Objects.requireNonNull(completion, "completion");
        SlashCommandParser.Parsed parsed = SlashCommandParser.parse(draft);
        if (parsed.kind() == SlashCommandParser.Kind.TEXT) {
            return new Dispatch(false, false, parsed.text());
        }
        if (parsed.kind() == SlashCommandParser.Kind.ERROR) {
            completion.accept(new Completion(false, parsed.code(), null));
            return new Dispatch(true, true, draft);
        }
        try {
            Objects.requireNonNull(compact.get(), "compact future").whenComplete((result, failure) -> {
                if (failure != null || result == null) {
                    completion.accept(new Completion(false, "compact_failed", null));
                } else if (result instanceof ToolResult.Failure<GuideCompactResult> rejected) {
                    completion.accept(new Completion(false, rejected.code(), null));
                } else {
                    GuideCompactResult value = ((ToolResult.Success<GuideCompactResult>) result).value();
                    completion.accept(new Completion(true,
                            value.status() == GuideCompactResult.Status.COMPACTED
                                    ? "compact_completed" : "compact_not_needed", value));
                }
            });
        } catch (RuntimeException failure) {
            completion.accept(new Completion(false, "compact_failed", null));
        }
        // Only successful completion may clear the command text. Attachments are never owned here.
        return new Dispatch(true, true, draft);
    }
}
