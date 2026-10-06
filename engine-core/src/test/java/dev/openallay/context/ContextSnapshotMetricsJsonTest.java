package dev.openallay.context;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonSerializer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ContextSnapshotMetricsJsonTest {
    private record Detached(Instant capturedAt, Optional<Map<String, String>> focus) {}

    @Test void emptyAndPresentOptionalUseRealUtf8JsonBytesWithoutModuleReflection() {
        Gson nativeGson = dev.openallay.json.EngineJson.create();
        ContextSnapshotMetricsJson metrics = new ContextSnapshotMetricsJson(nativeGson);
        for (var focus : java.util.List.of(Optional.<Map<String, String>>empty(), Optional.of(Map.of("title", "箱子")))) {
            Detached value = new Detached(Instant.EPOCH, focus);
            String expected = focus.isPresent()
                    ? "{\"capturedAt\":{\"seconds\":0,\"nanos\":0},\"focus\":{\"title\":\"箱子\"}}"
                    : "{\"capturedAt\":{\"seconds\":0,\"nanos\":0}}";
            assertEquals(expected.getBytes(StandardCharsets.UTF_8).length, metrics.bytes(value));
        }
        assertEquals(0, metrics.bytes(null));
    }

    @Test void suppliedAdaptersArePreservedRatherThanReplacedByGlobalDefaults() {
        Gson supplied = dev.openallay.json.EngineJson.create(builder -> builder.registerTypeAdapter(Instant.class,
                (JsonSerializer<Instant>) (value, type, context) -> new com.google.gson.JsonPrimitive("native:" + value)));
        var value = new Detached(Instant.EPOCH, Optional.empty());
        assertEquals("{\"capturedAt\":\"native:1970-01-01T00:00:00Z\"}".getBytes(StandardCharsets.UTF_8).length,
                new ContextSnapshotMetricsJson(supplied).bytes(value));
    }
}
