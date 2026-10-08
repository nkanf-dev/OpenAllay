package dev.openallay.model.catalog;

import com.google.gson.JsonElement;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.SecretValue;
import dev.openallay.net.HttpExchangeRequest;
import dev.openallay.net.HttpTransport;
import dev.openallay.net.HttpTransportPolicy;
import dev.openallay.net.JdkHttpTransport;
import dev.openallay.tool.ToolResult;
import java.io.InputStream;
import dev.openallay.net.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/** Strict configuration-layer adapter for OpenAI-style provider model catalogs. */
public final class ProviderModelCatalogClient {
    private final HttpTransport transport;

    public ProviderModelCatalogClient(Duration connectTimeout) {
        this(new JdkHttpTransport(new HttpTransportPolicy(
                connectTimeout, "openallay-model-catalog-http")));
    }

    public ProviderModelCatalogClient(HttpTransport transport) {
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    public CompletableFuture<ToolResult<ModelCatalog>> fetch(
            ModelCatalogRequest request,
            SecretValue credential,
            CancellationSignal cancellation) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(credential, "credential");
        Objects.requireNonNull(cancellation, "cancellation");
        if (cancellation.isCancelled()) {
            return CompletableFuture.completedFuture(cancelled());
        }
        HttpExchangeRequest.Builder encoded = HttpExchangeRequest.newBuilder(
                        request.baseUri().resolve("models"))
                .timeout(request.requestTimeout())
                .header("accept", "application/json")
                .get();
        if (request.protocol() == ModelProtocol.OPENAI_CHAT) {
            encoded.header("authorization", "Bearer " + credential.reveal());
        } else {
            encoded.header("x-api-key", credential.reveal())
                    // Anthropic-compatible inference gateways commonly expose an OpenAI-style
                    // /models route even when /messages uses x-api-key authentication.
                    .header("authorization", "Bearer " + credential.reveal())
                    .header("anthropic-version", "2023-06-01");
        }
        CompletableFuture<Response> response;
        try {
            response = transport.execute(
                    encoded.build(),
                    cancellation,
                    (status, headers, body) -> new Response(status, read(body)));
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(classify(failure, cancellation));
        }
        return response.handle((received, failure) -> {
            if (cancellation.isCancelled()) {
                return cancelled();
            }
            if (failure != null || received == null) {
                return classify(failure, cancellation);
            }
            if (received.status() != 200) {
                return statusFailure(received.status());
            }
            try {
                return new ToolResult.Success<>(decode(received.body()));
            } catch (RuntimeException malformed) {
                return failure(
                        "model_catalog_malformed",
                        "The model provider returned an invalid model catalog");
            }
        });
    }

    private static ModelCatalog decode(String json) {
        JsonElement parsed = dev.openallay.json.JsonTrees.parse(json);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("catalog root");
        }
        JsonElement data = parsed.getAsJsonObject().get("data");
        if (data == null || !data.isJsonArray()) {
            throw new IllegalArgumentException("catalog data");
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (JsonElement element : data.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("catalog entry");
            }
            JsonElement id = element.getAsJsonObject().get("id");
            if (id == null || !id.isJsonPrimitive()
                    || !id.getAsJsonPrimitive().isString()
                    || dev.openallay.util.Java8Strings.isBlank(id.getAsString())) {
                throw new IllegalArgumentException("catalog model id");
            }
            ids.add(id.getAsString());
        }
        return new ModelCatalog(dev.openallay.util.Java8Collections.toList(ids.stream()));
    }

    private static String read(InputStream body) throws java.io.IOException {
        return new String(body.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static ToolResult.Failure<ModelCatalog> statusFailure(int status) {
        {
dev.openallay.tool.ToolResult.Failure<dev.openallay.model.catalog.ModelCatalog> $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((status)) {
case 401:
case 403:
{
$oaSwitch0_exit_result = failure(
                    "model_catalog_auth_failed",
                    "The model provider rejected catalog authentication"); break $oaSwitch0_exit;
}
case 429:
{
$oaSwitch0_exit_result = failure(
                    "model_catalog_rate_limited",
                    "The model provider rate-limited the catalog request"); break $oaSwitch0_exit;
}
default:
{
$oaSwitch0_exit_result = failure(
                    "model_catalog_unavailable",
                    "The model provider catalog is unavailable"); break $oaSwitch0_exit;
}
}
}
return $oaSwitch0_exit_result;
}
    }

    private static ToolResult.Failure<ModelCatalog> classify(
            Throwable thrown, CancellationSignal cancellation) {
        if (cancellation.isCancelled()) {
            return cancelled();
        }
        Throwable cause = unwrap(thrown);
        if (cause instanceof CancellationException) {
            return cancelled();
        }
        if (cause instanceof HttpTimeoutException || cause instanceof TimeoutException) {
            return failure(
                    "model_catalog_timeout", "The model catalog request timed out");
        }
        return failure(
                "model_catalog_transport_failed",
                "The model provider catalog could not be reached");
    }

    private static Throwable unwrap(Throwable thrown) {
        Throwable current = thrown;
        if (current == null) {
            return null;
        }
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static ToolResult.Failure<ModelCatalog> cancelled() {
        return failure("model_catalog_cancelled", "The model catalog request was cancelled");
    }

    private static ToolResult.Failure<ModelCatalog> failure(String code, String message) {
        return new ToolResult.Failure<>(code, message);
    }

    @dev.openallay.value.ValueType(Response.ValueSchemaProvider.class)
private static final class Response {
    private final int status;
    private final String body;
    private Response(int status, String body) {

            Objects.requireNonNull(body, "body");

        this.status = status;
        this.body = body;
    }
    public int status() { return status; }
    public String body() { return body; }
@Override
        public String toString() {
            return "Response[status=" + status + "]";
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Response)) return false;
        Response that = (Response) other;
        return status == that.status && java.util.Objects.equals(body, that.body);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(status);
        hash = 31 * hash + java.util.Objects.hashCode(body);
        return hash;
    }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Response> schema() {
            return new dev.openallay.value.ValueSchema<>(Response.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Response>>asList(new dev.openallay.value.ValueSchema.Component<>(Response.class, "status", Response::status), new dev.openallay.value.ValueSchema.Component<>(Response.class, "body", Response::body)), arguments -> new Response((Integer) arguments[0], (String) arguments[1]));
        }
    }
}
}
