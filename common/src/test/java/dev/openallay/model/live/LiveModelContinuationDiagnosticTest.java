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
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.ProviderModelClients;
import dev.openallay.model.config.ModelProfilesConfigLoader;
import dev.openallay.model.config.ResolvedModelProfile;
import dev.openallay.tool.ToolResult;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
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
        JsonDeserializer<ModelContent> content = (json, type, context) -> {
            JsonObject block = json.getAsJsonObject();
            if (block.has("toolUseId")) {
                return context.deserialize(block, ModelContent.ToolResult.class);
            }
            if (block.has("id") && block.has("name") && block.has("input")) {
                return context.deserialize(block, ModelContent.ToolUse.class);
            }
            if (block.has("text") && block.has("signature")) {
                return context.deserialize(block, ModelContent.Reasoning.class);
            }
            if (block.keySet().equals(Set.of("text"))) {
                return context.deserialize(block, ModelContent.Text.class);
            }
            throw new IllegalArgumentException("unsupported retained content shape");
        };
        Gson gson = new GsonBuilder().registerTypeAdapter(ModelContent.class, content).create();
        ModelRequest request = gson.fromJson(payload, ModelRequest.class);
        // Gson omits a null reasoning signature. Reject ambiguous assistant text-only blocks.
        for (JsonElement message : payload.getAsJsonArray("messages")) {
            JsonObject encodedMessage = message.getAsJsonObject();
            if ("ASSISTANT".equals(encodedMessage.get("role").getAsString())) {
                for (JsonElement block : encodedMessage.getAsJsonArray("content")) {
                    if (block.getAsJsonObject().keySet().equals(Set.of("text"))) {
                        throw new IllegalArgumentException("retained assistant content type is ambiguous");
                    }
                }
            }
        }
        // LiveAgentTraceRecorder writes Gson.toJsonTree(ModelRequest). Require an exact round trip.
        if (!new Gson().toJsonTree(request).equals(payload)) {
            throw new IllegalArgumentException("retained request did not round-trip exactly");
        }
        return request;
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        Assumptions.assumeTrue(value != null && !value.isBlank(), name + " is required");
        return value;
    }
}
