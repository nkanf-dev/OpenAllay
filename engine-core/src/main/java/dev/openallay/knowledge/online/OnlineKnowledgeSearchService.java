package dev.openallay.knowledge.online;

import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.net.HttpCancellation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Concurrent, independently degrading search across fixed public documentation sources. */
public final class OnlineKnowledgeSearchService {
    private final List<OnlineKnowledgeSource> sources;

    public OnlineKnowledgeSearchService(List<? extends OnlineKnowledgeSource> sources) {
        this.sources = dev.openallay.util.Java8Collections.listCopyOf(sources);
    }

    public CompletableFuture<OnlineKnowledgeSearch> search(
            String query,
            int limit,
            ToolInvocationContext context,
            HttpCancellation cancellation) {
        List<CompletableFuture<SourceOutcome>> pending = dev.openallay.util.Java8Collections.toList(sources.stream()
                .map(source -> source.search(query, limit, cancellation)
                        .handle((hits, failure) -> failure == null
                                ? SourceOutcome.success(source, hits)
                                : SourceOutcome.failure(source, unwrap(failure)))));
        return CompletableFuture.allOf(pending.toArray(new CompletableFuture<?>[0]))
                .thenApply(ignored -> combine(pending, context));
    }

    private static OnlineKnowledgeSearch combine(
            List<CompletableFuture<SourceOutcome>> pending,
            ToolInvocationContext context) {
        List<OnlineKnowledgeHit> hits = new ArrayList<>();
        List<OnlineKnowledgeDiagnostic> diagnostics = new ArrayList<>();
        for (CompletableFuture<SourceOutcome> future : pending) {
            SourceOutcome outcome = future.join();
            if (outcome.failure() != null) {
                Throwable failure = outcome.failure();
                final class $oaPattern0_Holder { java.lang.Throwable value; OnlineKnowledgeException bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
String code = (($oaPattern0_holder.value = failure) instanceof dev.openallay.knowledge.online.OnlineKnowledgeException && (($oaPattern0_holder.bound = (OnlineKnowledgeException) $oaPattern0_holder.value) != null))
                        ? $oaPattern0_holder.bound.code()
                        : "online_source_unavailable";
                diagnostics.add(new OnlineKnowledgeDiagnostic(
                        outcome.source().sourceId(), code, playerSafeMessage(code)));
                continue;
            }
            EvidenceMetadata evidence = evidence(context, outcome.source());
            for (OnlineKnowledgeSource.RawHit hit : outcome.hits()) {
                hits.add(new OnlineKnowledgeHit(
                        outcome.source().sourceId(),
                        hit.title(),
                        hit.excerpt(),
                        hit.reference(),
                        evidence));
            }
        }
        return new OnlineKnowledgeSearch(hits, diagnostics);
    }

    private static EvidenceMetadata evidence(
            ToolInvocationContext context, OnlineKnowledgeSource source) {
        String gameVersion = context.observableGameState()
                .map(snapshot -> snapshot.runtime().gameVersion())
                .orElseGet(() -> context.registries()
                        .map(snapshot -> snapshot.evidence().gameVersion())
                        .orElse("unknown"));
        String loader = context.observableGameState()
                .map(snapshot -> snapshot.runtime().loader())
                .orElseGet(() -> context.registries()
                        .map(snapshot -> snapshot.evidence().loader())
                        .orElse("unknown"));
        return new EvidenceMetadata(
                DataAuthority.INTEGRATION_API,
                DataCompleteness.PARTIAL,
                context.capturedAt(),
                source.sourceId(),
                source.provenance(),
                gameVersion,
                loader,
                dev.openallay.util.Java8Collections.mapOf("openallay:scope", "public_search_excerpt"));
    }

    private static String playerSafeMessage(String code) {
        {
java.lang.String $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((code)) {
case "online_http_status":
{
$oaSwitch0_exit_result = "The public knowledge source returned an error"; break $oaSwitch0_exit;
}
case "online_parse_failed":
{
$oaSwitch0_exit_result = "The public knowledge source response could not be read"; break $oaSwitch0_exit;
}
default:
{
$oaSwitch0_exit_result = "The public knowledge source is temporarily unavailable"; break $oaSwitch0_exit;
}
}
}
return $oaSwitch0_exit_result;
}
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                        || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @dev.openallay.value.ValueType(SourceOutcome.ValueSchemaProvider.class)
private static final class SourceOutcome {
    private final OnlineKnowledgeSource source;
    private final List<OnlineKnowledgeSource.RawHit> hits;
    private final Throwable failure;
    private SourceOutcome(OnlineKnowledgeSource source, List<OnlineKnowledgeSource.RawHit> hits, Throwable failure) {
        this.source = source;
        this.hits = hits;
        this.failure = failure;
    }
    public OnlineKnowledgeSource source() { return source; }
    public List<OnlineKnowledgeSource.RawHit> hits() { return hits; }
    public Throwable failure() { return failure; }
private static SourceOutcome success(
                OnlineKnowledgeSource source, List<OnlineKnowledgeSource.RawHit> hits) {
            return new SourceOutcome(source, dev.openallay.util.Java8Collections.listCopyOf(hits), null);
        }
private static SourceOutcome failure(OnlineKnowledgeSource source, Throwable failure) {
            return new SourceOutcome(source, dev.openallay.util.Java8Collections.listOf(), failure);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SourceOutcome)) return false;
        SourceOutcome that = (SourceOutcome) other;
        return java.util.Objects.equals(source, that.source) && java.util.Objects.equals(hits, that.hits) && java.util.Objects.equals(failure, that.failure);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(source);
        hash = 31 * hash + java.util.Objects.hashCode(hits);
        hash = 31 * hash + java.util.Objects.hashCode(failure);
        return hash;
    }
    @Override public String toString() { return "SourceOutcome[source=" + source + ", hits=" + hits + ", failure=" + failure + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SourceOutcome> schema() {
            return new dev.openallay.value.ValueSchema<>(SourceOutcome.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SourceOutcome>>asList(new dev.openallay.value.ValueSchema.Component<>(SourceOutcome.class, "source", SourceOutcome::source), new dev.openallay.value.ValueSchema.Component<>(SourceOutcome.class, "hits", SourceOutcome::hits), new dev.openallay.value.ValueSchema.Component<>(SourceOutcome.class, "failure", SourceOutcome::failure)), arguments -> new SourceOutcome((OnlineKnowledgeSource) arguments[0], (List) arguments[1], (Throwable) arguments[2]));
        }
    }
}
}
