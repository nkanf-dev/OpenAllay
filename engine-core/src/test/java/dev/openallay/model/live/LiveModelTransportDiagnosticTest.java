package dev.openallay.model.live;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClientException;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.config.ModelConfig;
import dev.openallay.model.config.ModelProfilesConfigLoader;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.ResolvedModelProfile;
import dev.openallay.model.http.ModelHttpErrors;
import dev.openallay.model.openai.OpenAiJsonCodec;
import dev.openallay.net.HttpExchangeRequest;
import dev.openallay.net.HttpResponseHeaders;
import dev.openallay.net.HttpTransportPolicy;
import dev.openallay.net.JdkHttpTransport;
import dev.openallay.tool.ToolResult;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Explicit live diagnostics. Never renders endpoints, credentials, bodies, or exception messages. */
final class LiveModelTransportDiagnosticTest {
    private static final int BODY_LIMIT_BYTES = 65_536;

    @Test
    void reportsOnlySafeTransportAndResponseMetadata() {
        Map<String, String> environment = System.getenv();
        Assumptions.assumeTrue(Boolean.parseBoolean(
                environment.get("OPENALLAY_LIVE_TRANSPORT_DIAGNOSTIC")));
        String configPath = required(environment, "OPENALLAY_SETTINGS_PROBE_CONFIG");
        String selectedId = required(environment, "OPENALLAY_SETTINGS_PROBE_PROFILE");
        String operation = environment.getOrDefault(
                "OPENALLAY_TRANSPORT_DIAGNOSTIC_OPERATION", "catalog");
        assertTrue(operation.equals("catalog") || operation.equals("probe"),
                "diagnostic operation must be catalog or probe");
        boolean succeeded;
        String previousHost = System.getProperty("https.proxyHost");
        String previousPort = System.getProperty("https.proxyPort");
        try {
            if (Boolean.parseBoolean(environment.get(
                    "OPENALLAY_TRANSPORT_DIAGNOSTIC_PROXY_FROM_ENV"))) {
                configureLoopbackProxy(environment);
            }
            ToolResult<ModelProfilesConfigLoader.Load> loaded = new ModelProfilesConfigLoader().load(
                    Path.of(configPath), environment);
            if (!(loaded instanceof ToolResult.Success<ModelProfilesConfigLoader.Load> success)) {
                throw new IllegalArgumentException("diagnostic configuration is invalid");
            }
            ResolvedModelProfile profile = success.value().profiles().stream()
                    .filter(candidate -> candidate.definition().id().equals(selectedId))
                    .findFirst().orElseThrow();
            if (!profile.available() || profile.runtimeConfig().protocol() != ModelProtocol.OPENAI_CHAT) {
                throw new IllegalArgumentException("diagnostic requires an available OpenAI profile");
            }
            ModelConfig config = profile.runtimeConfig();
            URI endpoint = config.baseUri().resolve(
                    operation.equals("catalog") ? "models" : "chat/completions");
            ProxySelector selector = ProxySelector.getDefault();
            List<Proxy> proxies = selector == null ? List.of() : selector.select(endpoint);
            System.out.println("OPENALLAY_TRANSPORT_DIAGNOSTIC operation=" + operation
                    + " proxyEnvPresent=" + environment.containsKey("HTTPS_PROXY")
                    + " javaProxyPropertyPresent=" + (System.getProperty("https.proxyHost") != null)
                    + " selectedHttpProxy=" + proxies.stream().anyMatch(
                            proxy -> proxy.type() == Proxy.Type.HTTP));
            HttpExchangeRequest.Builder request = HttpExchangeRequest.newBuilder(endpoint)
                    .timeout(config.requestTimeout())
                    .header("authorization", "Bearer " + config.apiKey().reveal())
                    .header("accept", "application/json");
            OpenAiJsonCodec codec = new OpenAiJsonCodec(new Gson());
            if (operation.equals("probe")) {
                ModelConfig probeConfig = new ModelConfig(
                        config.enabled(), config.protocol(), config.baseUri(), config.model(),
                        config.apiKey(), config.contextWindowTokens(),
                        Math.min(config.maxOutputTokens(), 64),
                        config.connectTimeout(), config.requestTimeout());
                ModelRequest probe = new ModelRequest(
                        "OpenAllay connectivity check. Do not provide any other content.",
                        List.of(ModelMessage.userText("Reply exactly OK.")), List.of(), false,
                        "openallay-settings-probe");
                request.header("content-type", "application/json")
                        .postJson(codec.requestBody(probeConfig, probe));
            } else {
                request.get();
            }
            JdkHttpTransport transport = new JdkHttpTransport(new HttpTransportPolicy(
                    config.connectTimeout(), "openallay-live-transport-diagnostic"));
            succeeded = transport.execute(request.build(), new CancellationSignal(),
                    (status, headers, body) -> inspectResponse(status, headers, body, operation, codec))
                    .get(config.requestTimeout().toSeconds() + 10, TimeUnit.SECONDS);
        } catch (Exception failure) {
            System.out.println("OPENALLAY_TRANSPORT_DIAGNOSTIC exceptionClasses=" + classes(failure));
            succeeded = false;
        } finally {
            restore("https.proxyHost", previousHost);
            restore("https.proxyPort", previousPort);
        }
        assertTrue(succeeded, "transport diagnostic failed; see sanitized metadata");
    }

    private static boolean inspectResponse(
            int status, HttpResponseHeaders headers, InputStream body, String operation,
            OpenAiJsonCodec codec) throws java.io.IOException {
        byte[] encoded = body.readNBytes(BODY_LIMIT_BYTES + 1);
        String type = headers.firstValue("content-type").orElse("").toLowerCase(java.util.Locale.ROOT);
        System.out.println("OPENALLAY_TRANSPORT_DIAGNOSTIC httpStatus=" + status
                + " jsonContentType=" + type.contains("json")
                + " sseContentType=" + type.contains("text/event-stream")
                + " redirectLocationPresent=" + headers.firstValue("location").isPresent()
                + " bodyPresent=" + (encoded.length != 0)
                + " bodyOverDiagnosticLimit=" + (encoded.length > BODY_LIMIT_BYTES));
        try {
            ModelHttpErrors.requireSuccess(status, headers, new ByteArrayInputStream(encoded));
        } catch (ModelClientException failure) {
            System.out.println("OPENALLAY_TRANSPORT_DIAGNOSTIC modelFailure=" + failure.failure().code());
            return false;
        }
        if (operation.equals("catalog")) {
            return status == 200;
        }
        if (encoded.length > BODY_LIMIT_BYTES) {
            return false;
        }
        String json = new String(encoded, StandardCharsets.UTF_8);
        reportShape(json);
        try {
            ModelTurn turn = codec.parseTurn(json, ignored -> {});
            String finish = Set.of("stop", "length", "tool_calls", "content_filter", "function_call")
                    .contains(turn.stopReason()) ? turn.stopReason() : "other";
            System.out.println("OPENALLAY_TRANSPORT_DIAGNOSTIC parsedTurn=true textBlank="
                    + turn.text().isBlank() + " toolCount=" + turn.toolUses().size()
                    + " finishReason=" + finish);
            return !turn.text().isBlank() && turn.toolUses().isEmpty();
        } catch (RuntimeException failure) {
            System.out.println("OPENALLAY_TRANSPORT_DIAGNOSTIC parsedTurn=false exceptionClasses="
                    + classes(failure) + " codecFrame=" + codecFrame(failure));
            return false;
        }
    }

    private static void reportShape(String json) {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            System.out.println("OPENALLAY_TRANSPORT_DIAGNOSTIC rootType=" + type(parsed));
            if (!parsed.isJsonObject()) {
                return;
            }
            JsonObject root = parsed.getAsJsonObject();
            reportFields("root", root, "model", "choices", "usage", "error");
            JsonElement choices = root.get("choices");
            if (choices != null && choices.isJsonArray() && !choices.getAsJsonArray().isEmpty()) {
                JsonElement first = choices.getAsJsonArray().get(0);
                System.out.println("OPENALLAY_TRANSPORT_DIAGNOSTIC choiceType=" + type(first));
                if (first.isJsonObject()) {
                    JsonObject choice = first.getAsJsonObject();
                    reportFields("choice", choice, "finish_reason", "message", "delta");
                    JsonElement message = choice.get("message");
                    if (message != null && message.isJsonObject()) {
                        reportFields("message", message.getAsJsonObject(),
                                "content", "reasoning_content", "tool_calls", "function_call");
                    }
                }
            }
            JsonElement usage = root.get("usage");
            if (usage != null && usage.isJsonObject()) {
                JsonObject object = usage.getAsJsonObject();
                reportFields("usage", object,
                        "prompt_tokens", "completion_tokens", "prompt_tokens_details");
                JsonElement details = object.get("prompt_tokens_details");
                if (details != null && details.isJsonObject()) {
                    reportFields("promptTokensDetails", details.getAsJsonObject(), "cached_tokens");
                }
            }
        } catch (RuntimeException failure) {
            System.out.println("OPENALLAY_TRANSPORT_DIAGNOSTIC jsonShapeExceptionClasses="
                    + classes(failure));
        }
    }

    private static void reportFields(String label, JsonObject object, String... fields) {
        StringBuilder output = new StringBuilder("OPENALLAY_TRANSPORT_DIAGNOSTIC " + label + "Types=");
        for (int index = 0; index < fields.length; index++) {
            if (index != 0) {
                output.append(",");
            }
            String field = fields[index];
            output.append(field).append(":").append(type(object.get(field)));
        }
        System.out.println(output);
    }

    private static String type(JsonElement value) {
        if (value == null) {
            return "missing";
        }
        if (value.isJsonNull()) {
            return "null";
        }
        if (value.isJsonObject()) {
            return "object";
        }
        if (value.isJsonArray()) {
            return "array";
        }
        if (value.getAsJsonPrimitive().isString()) {
            return "string";
        }
        return value.getAsJsonPrimitive().isBoolean() ? "boolean" : "number";
    }

    private static String codecFrame(Throwable failure) {
        for (StackTraceElement frame : failure.getStackTrace()) {
            if (frame.getClassName().equals(OpenAiJsonCodec.class.getName())) {
                return frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber();
            }
        }
        return "none";
    }

    private static void configureLoopbackProxy(Map<String, String> environment) {
        URI proxy = URI.create(environment.getOrDefault("HTTPS_PROXY", ""));
        String host = proxy.getHost();
        if (!"http".equalsIgnoreCase(proxy.getScheme())
                || !("127.0.0.1".equals(host) || "localhost".equals(host) || "[::1]".equals(host))
                || proxy.getPort() <= 0 || proxy.getUserInfo() != null
                || proxy.getQuery() != null || proxy.getFragment() != null) {
            throw new IllegalArgumentException("diagnostic proxy must be a credential-free loopback HTTP proxy");
        }
        System.setProperty("https.proxyHost", host);
        System.setProperty("https.proxyPort", Integer.toString(proxy.getPort()));
    }

    private static String classes(Throwable failure) {
        StringBuilder output = new StringBuilder();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = failure; current != null && visited.add(current);
                current = current.getCause()) {
            if (!output.isEmpty()) {
                output.append(",");
            }
            output.append(current.getClass().getName());
        }
        return output.toString();
    }

    private static void restore(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        Assumptions.assumeTrue(value != null && !value.isBlank(), name + " is required");
        return value;
    }
}
