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
    @dev.openallay.value.ValueType(Dispatch.ValueSchemaProvider.class)
public static final class Dispatch {
    private final boolean handled;
    private final boolean retainDraft;
    private final String normalizedText;
    public Dispatch(boolean handled, boolean retainDraft, String normalizedText) {
        this.handled = handled;
        this.retainDraft = retainDraft;
        this.normalizedText = normalizedText;
    }
    public boolean handled() { return handled; }
    public boolean retainDraft() { return retainDraft; }
    public String normalizedText() { return normalizedText; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Dispatch)) return false;
        Dispatch that = (Dispatch) other;
        return handled == that.handled && retainDraft == that.retainDraft && java.util.Objects.equals(normalizedText, that.normalizedText);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(handled);
        hash = 31 * hash + Boolean.hashCode(retainDraft);
        hash = 31 * hash + java.util.Objects.hashCode(normalizedText);
        return hash;
    }
    @Override public String toString() { return "Dispatch[handled=" + handled + ", retainDraft=" + retainDraft + ", normalizedText=" + normalizedText + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Dispatch> schema() {
            return new dev.openallay.value.ValueSchema<>(Dispatch.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Dispatch>>asList(new dev.openallay.value.ValueSchema.Component<>(Dispatch.class, "handled", Dispatch::handled), new dev.openallay.value.ValueSchema.Component<>(Dispatch.class, "retainDraft", Dispatch::retainDraft), new dev.openallay.value.ValueSchema.Component<>(Dispatch.class, "normalizedText", Dispatch::normalizedText)), arguments -> new Dispatch((Boolean) arguments[0], (Boolean) arguments[1], (String) arguments[2]));
        }
    }
}
    @dev.openallay.value.ValueType(Completion.ValueSchemaProvider.class)
public static final class Completion {
    private final boolean successful;
    private final String code;
    private final GuideCompactResult result;
    public Completion(boolean successful, String code, GuideCompactResult result) {
        this.successful = successful;
        this.code = code;
        this.result = result;
    }
    public boolean successful() { return successful; }
    public String code() { return code; }
    public GuideCompactResult result() { return result; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Completion)) return false;
        Completion that = (Completion) other;
        return successful == that.successful && java.util.Objects.equals(code, that.code) && java.util.Objects.equals(result, that.result);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(successful);
        hash = 31 * hash + java.util.Objects.hashCode(code);
        hash = 31 * hash + java.util.Objects.hashCode(result);
        return hash;
    }
    @Override public String toString() { return "Completion[successful=" + successful + ", code=" + code + ", result=" + result + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Completion> schema() {
            return new dev.openallay.value.ValueSchema<>(Completion.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Completion>>asList(new dev.openallay.value.ValueSchema.Component<>(Completion.class, "successful", Completion::successful), new dev.openallay.value.ValueSchema.Component<>(Completion.class, "code", Completion::code), new dev.openallay.value.ValueSchema.Component<>(Completion.class, "result", Completion::result)), arguments -> new Completion((Boolean) arguments[0], (String) arguments[1], (GuideCompactResult) arguments[2]));
        }
    }
}

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
                } else {
final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<dev.openallay.guide.GuideCompactResult> value; ToolResult.Failure<GuideCompactResult> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = result) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern0_holder.bound = (ToolResult.Failure<GuideCompactResult>) $oaPattern0_holder.value) != null))) {
                    completion.accept(new Completion(false, $oaPattern0_holder.bound.code(), null));
                } else {
                    GuideCompactResult value = ((ToolResult.Success<GuideCompactResult>) result).value();
                    completion.accept(new Completion(true,
                            value.status() == GuideCompactResult.Status.COMPACTED
                                    ? "compact_completed" : "compact_not_needed", value));
                }
}
            });
        } catch (RuntimeException failure) {
            completion.accept(new Completion(false, "compact_failed", null));
        }
        // Only successful completion may clear the command text. Attachments are never owned here.
        return new Dispatch(true, true, draft);
    }
}
