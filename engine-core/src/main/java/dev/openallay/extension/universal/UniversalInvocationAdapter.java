package dev.openallay.extension.universal;

import dev.openallay.api.extension.ExtensionEvidence;
import dev.openallay.api.extension.ExtensionInvocation;
import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.extension.JavascriptInvocationContext;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Trusted public-ABI facade over one Extension's frozen invocation authority. */
public final class UniversalInvocationAdapter implements ExtensionInvocation {
    private final JavascriptInvocationContext context;

    public UniversalInvocationAdapter(JavascriptInvocationContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    /** Core native adapters can recover authority; this object is never a script host value. */
    public JavascriptInvocationContext legacyContext() { return context; }
    @Override public String extensionId() { return context.extensionId(); }
    @Override public String correlationId() { return context.invocation().correlationId(); }
    @Override public Instant capturedAt() { return context.invocation().capturedAt(); }
    @Override public CallerKind callerKind() {
        return CallerKind.valueOf(context.invocation().caller().kind().name());
    }
    @Override public UUID callerUuid() { return context.invocation().caller().uuid(); }
    @Override public Optional<String> playerDimension() {
        return context.invocation().player().map(player -> player.dimension());
    }
    @Override public void requireActive() { context.requireActive(); }
    @Override public boolean isCancelled() { return context.cancellation().isCancelled(); }
    @Override public void onCancel(Runnable listener) { context.cancellation().onCancel(listener); }
    @Override public boolean hasCapability(String id) { return context.hasCapability(id); }
    @Override public void requireCapability(String id) { context.requireCapability(id); }
    @Override public boolean completedSuccessfully() { return context.completedSuccessfully(); }
    @Override public void recordEvidence(ExtensionEvidence evidence) {
        context.requireActive();
        Objects.requireNonNull(evidence, "evidence");
        context.recordEvidence(new EvidenceMetadata(
                DataAuthority.valueOf(evidence.authority().name()),
                DataCompleteness.valueOf(evidence.completeness().name()), evidence.capturedAt(),
                evidence.sourceId(), evidence.provenance(), evidence.gameVersion(), evidence.loader(),
                evidence.details()));
    }
}
