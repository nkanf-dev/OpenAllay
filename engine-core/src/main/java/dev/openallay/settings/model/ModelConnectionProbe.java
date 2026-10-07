package dev.openallay.settings.model;

import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClient;
import dev.openallay.model.ModelClientException;
import dev.openallay.model.ModelFailure;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.config.ModelConfig;
import dev.openallay.model.config.ResolvedModelProfile;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.LongSupplier;

/** Performs one explicit, context-free and credential-safe provider connectivity probe. */
public final class ModelConnectionProbe {
    private static final int OUTPUT_TOKEN_LIMIT = 64;
    private static final String SYSTEM_PROMPT =
            "OpenAllay connectivity check. Do not provide any other content.";

    private final Function<ModelConfig, ModelClient> clientFactory;
    private final Clock clock;
    private final LongSupplier nanoTime;

    public ModelConnectionProbe(
            Function<ModelConfig, ModelClient> clientFactory,
            Clock clock,
            LongSupplier nanoTime) {
        this.clientFactory = Objects.requireNonNull(clientFactory, "clientFactory");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    public CompletableFuture<ModelConnectionResult> test(
            ResolvedModelProfile profile, CancellationSignal cancellation) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(cancellation, "cancellation");
        if (cancellation.isCancelled()) {
            return CompletableFuture.completedFuture(failure("connection_cancelled"));
        }
        if (!profile.available()) {
            return CompletableFuture.completedFuture(new ModelConnectionResult.Failure(
                    profile.failure().code(), safeUnavailableMessage(profile.failure().code())));
        }

        ModelConfig probeConfig = probeConfig(profile.runtimeConfig());
        ModelRequest request = new ModelRequest(
                SYSTEM_PROMPT,
                dev.openallay.util.Java8Collections.listOf(ModelMessage.userText("Reply exactly OK.")),
                dev.openallay.util.Java8Collections.listOf(),
                false,
                "openallay-settings-probe");
        long startedAt = nanoTime.getAsLong();
        CompletableFuture<ModelTurn> response;
        try {
            response = clientFactory.apply(probeConfig).complete(request, ignored -> {}, cancellation);
        } catch (RuntimeException thrown) {
            return CompletableFuture.completedFuture(classify(thrown, cancellation));
        }
        return response.handle((turn, thrown) -> {
            if (thrown != null) {
                return classify(thrown, cancellation);
            }
            if (cancellation.isCancelled()) {
                return failure("connection_cancelled");
            }
            if (turn == null || dev.openallay.util.Java8Strings.isBlank(turn.text()) || !turn.toolUses().isEmpty()) {
                return failure("connection_protocol_failed");
            }
            long elapsedMillis = Math.max(0, (nanoTime.getAsLong() - startedAt) / 1_000_000L);
            return new ModelConnectionResult.Success(
                    profile.definition().id(),
                    profile.definition().protocol(),
                    authority(profile.definition().baseUri()),
                    clock.instant(),
                    elapsedMillis);
        });
    }

    private static ModelConfig probeConfig(ModelConfig config) {
        return new ModelConfig(
                config.enabled(),
                config.protocol(),
                config.baseUri(),
                config.model(),
                config.apiKey(),
                config.contextWindowTokens(),
                Math.min(config.maxOutputTokens(), OUTPUT_TOKEN_LIMIT),
                config.connectTimeout(),
                config.requestTimeout(),
                config.reasoningEffort(), config.tokenEncoding());
    }

    private static ModelConnectionResult.Failure classify(
            Throwable thrown, CancellationSignal cancellation) {
        Throwable cause = unwrap(thrown);
        if (cancellation.isCancelled()) {
            return failure("connection_cancelled");
        }
        final class $oaPattern0_Holder { java.lang.Throwable value; ModelClientException bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = cause) instanceof dev.openallay.model.ModelClientException && (($oaPattern0_holder.bound = (ModelClientException) $oaPattern0_holder.value) != null))) {
            ModelFailure modelFailure = $oaPattern0_holder.bound.failure();
            if ("agent_cancelled".equals(modelFailure.code())) {
                return failure("connection_cancelled");
            }
            if ("model_timeout".equals(modelFailure.code())) {
                return failure("connection_timeout");
            }
            Integer status = modelFailure.httpStatus();
            if (status != null) {
                return switch (status) {
                    case 401, 403 -> failure("connection_auth_failed");
                    case 404 -> failure("connection_model_unavailable");
                    case 429 -> failure("connection_rate_limited");
                    default -> failure("connection_protocol_failed");
                };
            }
        }
        return failure("connection_transport_failed");
    }

    private static Throwable unwrap(Throwable thrown) {
        Throwable current = thrown;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String authority(URI uri) {
        return uri.getScheme() + "://" + uri.getRawAuthority();
    }

    private static ModelConnectionResult.Failure failure(String code) {
        return new ModelConnectionResult.Failure(code, switch (code) {
            case "connection_auth_failed" -> "The model provider rejected authentication";
            case "connection_model_unavailable" -> "The configured model is unavailable";
            case "connection_rate_limited" -> "The model provider rate-limited the test";
            case "connection_timeout" -> "The connection test timed out";
            case "connection_cancelled" -> "The connection test was cancelled";
            case "connection_protocol_failed" ->
                "The model provider returned an invalid test response";
            default -> "The model provider could not be reached";
        });
    }

    private static String safeUnavailableMessage(String code) {
        return "model_not_configured".equals(code)
                ? "The configured credential is unavailable"
                : "The model profile is unavailable";
    }
}
