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
                {
dev.openallay.settings.model.ModelConnectionResult.Failure $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((status)) {
case 401:
case 403:
{
$oaSwitch0_exit_result = failure("connection_auth_failed"); break $oaSwitch0_exit;
}
case 404:
{
$oaSwitch0_exit_result = failure("connection_model_unavailable"); break $oaSwitch0_exit;
}
case 429:
{
$oaSwitch0_exit_result = failure("connection_rate_limited"); break $oaSwitch0_exit;
}
default:
{
$oaSwitch0_exit_result = failure("connection_protocol_failed"); break $oaSwitch0_exit;
}
}
}
return $oaSwitch0_exit_result;
}
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
        {
final java.lang.String $oaSwitch1_exit_result_prior0 = code;
java.lang.String $oaSwitch1_exit_result;
$oaSwitch1_exit: {
switch ((code)) {
case "connection_auth_failed":
{
$oaSwitch1_exit_result = "The model provider rejected authentication"; break $oaSwitch1_exit;
}
case "connection_model_unavailable":
{
$oaSwitch1_exit_result = "The configured model is unavailable"; break $oaSwitch1_exit;
}
case "connection_rate_limited":
{
$oaSwitch1_exit_result = "The model provider rate-limited the test"; break $oaSwitch1_exit;
}
case "connection_timeout":
{
$oaSwitch1_exit_result = "The connection test timed out"; break $oaSwitch1_exit;
}
case "connection_cancelled":
{
$oaSwitch1_exit_result = "The connection test was cancelled"; break $oaSwitch1_exit;
}
case "connection_protocol_failed":
{
$oaSwitch1_exit_result = "The model provider returned an invalid test response"; break $oaSwitch1_exit;
}
default:
{
$oaSwitch1_exit_result = "The model provider could not be reached"; break $oaSwitch1_exit;
}
}
}
return new ModelConnectionResult.Failure($oaSwitch1_exit_result_prior0, $oaSwitch1_exit_result);
}
    }

    private static String safeUnavailableMessage(String code) {
        return "model_not_configured".equals(code)
                ? "The configured credential is unavailable"
                : "The model profile is unavailable";
    }
}
