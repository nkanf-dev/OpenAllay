package dev.openallay.trace.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializer;
import dev.openallay.context.EvidenceBearing;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.json.EngineJson;
import dev.openallay.testing.GroundedTestFixtures;
import dev.openallay.tool.ToolResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ToolCodecAndNormalizerTest {
    record Input(int count) {}

    record Output(String zeta, String alpha, List<Integer> complete) {}

    record UngroundedOutput(String fact, List<EvidenceMetadata> evidence)
            implements EvidenceBearing {}

    private final Gson gson = new Gson();

    @Test
    void decodesRecordsAndReturnsInvalidArgumentsOnConversionFailure() {
        ToolArgumentCodec codec = new ToolArgumentCodec(gson);
        ToolResult.Success<Input> success = assertInstanceOf(
                ToolResult.Success.class,
                codec.decode(object("{\"count\":3}"), Input.class));
        assertEquals(3, success.value().count());

        ToolResult.Failure<Input> failure = assertInstanceOf(
                ToolResult.Failure.class,
                codec.decode(object("{\"count\":{}}"), Input.class));
        assertEquals("invalid_arguments", failure.code());
    }

    @Test
    void rejectsUndeclaredArgumentsWithoutEchoingForeignText() {
        ToolResult.Failure<?> failure = assertInstanceOf(ToolResult.Failure.class,
                new ToolArgumentCodec(gson).decode(object("{\"count\":3,\"removedField\":true}"), Input.class));
        assertEquals("invalid_arguments", failure.code());
        assertEquals("tool arguments contain an undeclared field", failure.message());
    }

    @Test
    void canonicalizationSortsObjectsAndPreservesCompleteArraysAndStrings() {
        ToolResultNormalizer normalizer = new ToolResultNormalizer(gson);
        JsonObject value = normalizer.normalize(
                new ToolResult.Success<>(new Output("unabridged", "first", List.of(3, 2, 1))),
                Output.class);

        assertEquals(List.of("outputType", "status", "value"), new ArrayList<>(value.keySet()));
        assertEquals(
                List.of("alpha", "complete", "zeta"),
                new ArrayList<>(value.getAsJsonObject("value").keySet()));
        assertEquals("unabridged", value.getAsJsonObject("value").get("zeta").getAsString());
        assertEquals(3, value.getAsJsonObject("value").getAsJsonArray("complete").size());
    }

    @Test
    void rejectsSuccessfulGroundedOutputsWithoutEvidence() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new ToolResultNormalizer(gson).normalize(
                        new ToolResult.Success<>(new UngroundedOutput("claim", List.of())),
                        UngroundedOutput.class));

        assertEquals("Grounded tool output has no evidence", failure.getMessage());
    }

    @Test
    void evidenceOutputRoundTripsItsPreciseTypedTimestamp() {
        var output = new UngroundedOutput("player fact", List.of(GroundedTestFixtures.serverEvidence()));
        var normalized = new ToolResultNormalizer(gson).normalize(new ToolResult.Success<>(output), UngroundedOutput.class);
        var value = normalized.getAsJsonObject("value");
        assertEquals(output, EngineJson.withInstant(gson).fromJson(value, UngroundedOutput.class));
        var time = output.evidence().getFirst().capturedAt();
        assertEquals(JsonParser.parseString("{\"seconds\":" + time.getEpochSecond()
                + ",\"nanos\":" + time.getNano() + "}"),
                value.getAsJsonArray("evidence").get(0).getAsJsonObject().get("capturedAt"));
    }

    @Test
    void suppliedTimestampAdaptersSurviveOutputNormalizationAndRoundTrip() {
        Gson supplied = new GsonBuilder()
                .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (value, type, context) ->
                        new com.google.gson.JsonPrimitive("caller:" + value))
                .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (value, type, context) ->
                        Instant.parse(value.getAsString().substring("caller:".length()))).create();
        var output = new UngroundedOutput("unchanged", List.of(GroundedTestFixtures.serverEvidence()));
        var value = new ToolResultNormalizer(supplied).normalize(new ToolResult.Success<>(output), UngroundedOutput.class)
                .getAsJsonObject("value");
        assertEquals("caller:" + output.evidence().getFirst().capturedAt(),
                value.getAsJsonArray("evidence").get(0).getAsJsonObject().get("capturedAt").getAsString());
        assertEquals(output, EngineJson.withInstant(supplied).fromJson(value, UngroundedOutput.class));
    }

    private static JsonObject object(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
