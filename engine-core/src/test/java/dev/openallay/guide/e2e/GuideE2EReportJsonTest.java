package dev.openallay.guide.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideTopology;
import dev.openallay.json.EngineJson;
import dev.openallay.testing.GroundedTestFixtures;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class GuideE2EReportJsonTest {
    @Test
    void canonicalReportRetainsEvidenceAndPlayerValues() {
        String playerValue = "player-plain-value-123456";
        GuideE2EReport report = new GuideE2EReport(
                "fabric",
                "26.2",
                "test",
                "client_local_" + playerValue,
                GuideTopology.CLIENT_LOCAL,
                UUID.randomUUID(),
                "main",
                List.of(GuideRequestStatus.PREPARING, GuideRequestStatus.COMPLETED),
                List.of("openallay:search_recipes"),
                List.of(new GuideE2EReport.ToolProbe(
                        "openallay:search_recipes",
                        dev.openallay.guide.GuideToolStatus.SUCCEEDED,
                        null,
                        null)),
                List.of(GroundedTestFixtures.serverEvidence()),
                List.of("assistant", "tool", "assistant"),
                Map.of("assistantSegments", 2L, "semanticFallbacks", 1L),
                List.of("semantic_component_unsupported"),
                List.of("status_badge"),
                "IDLE",
                Map.of("loadedRequests", 1L, "totalRequests", 1L),
                GuideRequestStatus.COMPLETED,
                null,
                null,
                Map.of("total", 10L),
                Map.of("result", "abc"));

        String encoded = new GuideE2EReportJson(new Gson()).encode(report);

        var json = JsonParser.parseString(encoded).getAsJsonObject();
        assertEquals("client_local_" + playerValue, json.get("scenario").getAsString());
        assertEquals("fabric", json.get("loader").getAsString());
        assertEquals("26.2", json.get("gameVersion").getAsString());
        assertEquals("test", json.get("modVersion").getAsString());
        assertEquals(report.requestId().toString(), json.get("requestId").getAsString());
        assertEquals("main", json.get("sessionId").getAsString());
        assertEquals("COMPLETED", json.get("outcome").getAsString());
        assertEquals(2L, json.getAsJsonObject("semanticMetrics")
                .get("assistantSegments").getAsLong());
        assertEquals(1L, json.getAsJsonObject("historyMetrics")
                .get("totalRequests").getAsLong());
        assertEquals("openallay:search_recipes", json.getAsJsonArray("toolProbes")
                .get(0).getAsJsonObject().get("toolId").getAsString());
        assertEquals("SUCCEEDED", json.getAsJsonArray("toolProbes")
                .get(0).getAsJsonObject().get("status").getAsString());
        assertEquals(report, EngineJson.withInstant(new Gson()).fromJson(encoded, GuideE2EReport.class));
        var evidenceTime = report.evidence().getFirst().capturedAt();
        assertEquals(JsonParser.parseString("{\"seconds\":" + evidenceTime.getEpochSecond()
                + ",\"nanos\":" + evidenceTime.getNano() + "}"),
                json.getAsJsonArray("evidence").get(0).getAsJsonObject().get("capturedAt"));
        assertTrue(encoded.contains("minecraft:recipe_manager"));
        assertTrue(encoded.contains("semantic_component_unsupported"));
        assertFalse(encoded.contains("component payload"));
    }
}
