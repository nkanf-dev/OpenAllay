package dev.openallay.model.live;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClientException;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelToolDefinition;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.ProviderModelClients;
import dev.openallay.model.config.ModelProfilesConfigLoader;
import dev.openallay.model.config.ResolvedModelProfile;
import dev.openallay.tool.ToolResult;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Replays one retained model request through the real adapter. Never executes tools or world writes. */
final class LiveModelContinuationDiagnosticTest {
    @Test
    void replaysOnlyLastRetainedModelRequestWithoutRenderingContent() {
        Map<String, String> environment = System.getenv();
        Assumptions.assumeTrue(Boolean.parseBoolean(
                environment.get("OPENALLAY_LIVE_CONTINUATION_DIAGNOSTIC")));
        String tracePath = required(environment, "OPENALLAY_CONTINUATION_DIAGNOSTIC_TRACE");
        String configPath = required(environment, "OPENALLAY_SETTINGS_PROBE_CONFIG");
        String selectedId = required(environment, "OPENALLAY_SETTINGS_PROBE_PROFILE");
        String previous = System.getProperty("openallay.model.diagnostics");
        boolean succeeded = false;
        try {
            System.setProperty("openallay.model.diagnostics", "true");
            ModelRequest request;
            try (Reader reader = Files.newBufferedReader(Path.of(tracePath))) {
                JsonReader json = new JsonReader(reader);
                json.setStrictness(Strictness.STRICT);
                request = retainedRequest(JsonParser.parseReader(json).getAsJsonObject());
                if (json.peek() != JsonToken.END_DOCUMENT) {
                    throw new IllegalArgumentException("unexpected data after retained trace");
                }
            }
            ToolResult<ModelProfilesConfigLoader.Load> loaded = new ModelProfilesConfigLoader().load(
                    Path.of(configPath), environment);
            if (!(loaded instanceof ToolResult.Success<ModelProfilesConfigLoader.Load> success)) {
                throw new IllegalArgumentException("continuation configuration is invalid");
            }
            ResolvedModelProfile profile = success.value().profiles().stream()
                    .filter(candidate -> candidate.definition().id().equals(selectedId))
                    .findFirst().orElseThrow();
            if (!profile.available()) {
                throw new IllegalArgumentException("continuation profile is unavailable");
            }
            AtomicInteger responseStarted = new AtomicInteger();
            AtomicInteger attemptStarted = new AtomicInteger();
            System.out.println("OPENALLAY_CONTINUATION_DIAGNOSTIC retainedRequest=true stream="
                    + request.stream() + " messageCount=" + request.messages().size()
                    + " toolDefinitionCount=" + request.tools().size());
            ModelTurn turn = ProviderModelClients.create(profile.runtimeConfig(), new Gson())
                    .complete(request, event -> {
                        if (event instanceof ModelEvent.ResponseStarted) {
                            responseStarted.incrementAndGet();
                        } else if (event instanceof ModelEvent.AttemptStarted) {
                            attemptStarted.incrementAndGet();
                        }
                    }, new CancellationSignal())
                    .get(profile.runtimeConfig().requestTimeout().toSeconds() + 10, TimeUnit.SECONDS);
            String finish = Set.of("stop", "length", "tool_calls", "content_filter", "function_call")
                    .contains(turn.stopReason()) ? turn.stopReason() : "other";
            System.out.println("OPENALLAY_CONTINUATION_DIAGNOSTIC parsedTurn=true textBlank="
                    + turn.text().isBlank() + " toolCount=" + turn.toolUses().size()
                    + " finishReason=" + finish + " attemptCount=" + attemptStarted.get()
                    + " responseStartedCount=" + responseStarted.get());
            succeeded = true;
        } catch (Exception failure) {
            Throwable cause = failure;
            while ((cause instanceof java.util.concurrent.ExecutionException
                    || cause instanceof java.util.concurrent.CompletionException)
                    && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof ModelClientException modelFailure) {
                System.out.println("OPENALLAY_CONTINUATION_DIAGNOSTIC modelFailure="
                        + modelFailure.failure().code() + " httpStatus=" + modelFailure.failure().httpStatus());
            } else {
                System.out.println("OPENALLAY_CONTINUATION_DIAGNOSTIC exceptionClass="
                        + cause.getClass().getName());
            }
        } finally {
            if (previous == null) {
                System.clearProperty("openallay.model.diagnostics");
            } else {
                System.setProperty("openallay.model.diagnostics", previous);
            }
        }
        assertTrue(succeeded, "retained continuation diagnostic failed; see sanitized metadata");
    }

    static ModelRequest retainedRequest(JsonObject trace) {
        JsonObject payload = null;
        for (JsonElement element : trace.getAsJsonArray("events")) {
            JsonObject event = element.getAsJsonObject();
            if ("model_request".equals(event.get("type").getAsString())) {
                payload = event.getAsJsonObject("payload");
            }
        }
        if (payload == null) {
            throw new IllegalArgumentException("trace has no retained model request");
        }
        JsonElement messages = payload.get("messages");
        if (messages == null || !messages.isJsonArray()) {
            throw new IllegalArgumentException("retained request messages must be an array");
        }
        for (JsonElement encoded : messages.getAsJsonArray()) {
            if (!encoded.isJsonObject()
                    || !encoded.getAsJsonObject().keySet().equals(Set.of("role", "content", "inputObservation"))) {
                throw new IllegalArgumentException("retained message requires current role, content and nullable inputObservation fields");
            }
            dev.openallay.world.ClientObservationAnchorJson.decode(encoded.getAsJsonObject().get("inputObservation"));
        }
        JsonDeserializer<ModelContent> content = (json, type, context) -> {
            JsonObject block = json.getAsJsonObject();
            Set<String> fields = block.keySet();
            if (fields.equals(Set.of("toolUseId", "value", "error", "images"))) {
                return context.deserialize(block, ModelContent.ToolResult.class);
            }
            if (fields.equals(Set.of("id", "name", "input"))) {
                return context.deserialize(block, ModelContent.ToolUse.class);
            }
            if (fields.equals(Set.of("reference")) || fields.equals(Set.of("reference", "originToolUseId"))) {
                return context.deserialize(block, ModelContent.Image.class);
            }
            if (fields.equals(Set.of("text"))) {
                return context.deserialize(block, ModelContent.Text.class);
            }
            throw new IllegalArgumentException("unsupported retained content shape");
        };
        Gson gson = new GsonBuilder().registerTypeAdapter(ModelContent.class, content).create();
        RetainedRequest retained = gson.fromJson(payload, RetainedRequest.class);
        // The trace contains only model-facing fields, not the request-only image resolver.
        ModelRequest request = new ModelRequest(retained.systemPrompt(), retained.messages(),
                retained.tools(), retained.stream(), retained.sessionKey(), retained.maxOutputTokens());
        // Require the exact current DTO shape, including every message and tool definition.
        if (!new Gson().toJsonTree(retained).equals(payload)) {
            throw new IllegalArgumentException("retained request did not round-trip exactly");
        }
        return request;
    }

    private record RetainedRequest(
            String systemPrompt,
            List<ModelMessage> messages,
            List<ModelToolDefinition> tools,
            boolean stream,
            String sessionKey,
            Integer maxOutputTokens) {}

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        Assumptions.assumeTrue(value != null && !value.isBlank(), name + " is required");
        return value;
    }
}
