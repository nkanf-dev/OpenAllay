package dev.openallay.guide.e2e;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import dev.openallay.guide.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class GuideBuilderE2EProbeTest {
    @Test void oracleRequiresExactNativeIdAndEveryExpectedProperty() {
        var expected = new GuideBuilderE2EProbe.Landmark("door", 3, 1, 0,
                "minecraft:oak_door", Map.of("half", "lower", "facing", "north"));
        assertTrue(GuideBuilderE2EProbe.matches(expected, "minecraft:oak_door",
                Map.of("half", "lower", "facing", "north", "open", "false")));
        assertFalse(GuideBuilderE2EProbe.matches(expected, "minecraft:air", expected.properties()));
        assertFalse(GuideBuilderE2EProbe.matches(expected, "minecraft:oak_door", Map.of("half", "upper", "facing", "north")));
        assertFalse(GuideBuilderE2EProbe.matches(expected, "minecraft:oak_door", Map.of("half", "lower")));
    }
    @Test void fixedExpectationsCoverEachPresetAndCannotComeFromClaimedSuccess() {
        var landmarks = GuideBuilderE2EProbe.landmarks("builder-acceptance");
        for (String prefix : List.of("house-", "skyscraper-", "cottage-", "windmill-", "farm-", "dock-"))
            assertTrue(landmarks.stream().anyMatch(value -> value.name().startsWith(prefix)), prefix);
        assertEquals(landmarks, GuideBuilderE2EProbe.landmarks("builder-reload"));
        assertEquals("minecraft:air", GuideBuilderE2EProbe.landmarks("builder-disabled").getFirst().id());
        assertEquals("minecraft:gold_block", GuideBuilderE2EProbe.landmarks("builder-partial").getFirst().id());
        assertEquals("minecraft:diamond_block", GuideBuilderE2EProbe.landmarks("builder-cancel").getFirst().id());
    }
    @Test void deniedContractRejectsClaimedSuccessOrWrongFailure() {
        assertTrue(GuideBuilderE2EProbe.toolContract("builder-disabled", request("failure", "javascript_error", null)));
        assertFalse(GuideBuilderE2EProbe.toolContract("builder-disabled", request("success", null, "builder_disabled")));
        assertFalse(GuideBuilderE2EProbe.toolContract("builder-disabled", request("failure", "permission_denied", null)));
        assertFalse(GuideBuilderE2EProbe.toolContract("builder-acceptance", request("success", null, "other_scenario")));
    }
    @Test void compositeLifecycleRequiresPartialCancellationAndExactUndoConflict() {
        var preview = com.google.gson.JsonParser.parseString("""
                {"lifecycle":{"partial":{"failure":"unknown native block","status":{"state":"failed-partial"}},
                 "cancel":{"deniedAfterCancel":true,"failure":"session closed","status":{"state":"cancelled-partial"}},
                 "undo":{"result":{"restored":1,"conflicts":[{"x":1}],"uncertain":[]},"status":{"state":"completed"}}}}
                """).getAsJsonObject();
        assertTrue(GuideBuilderE2EProbe.compositeLifecycle(preview));
        preview.getAsJsonObject("lifecycle").getAsJsonObject("cancel").addProperty("deniedAfterCancel", false);
        assertFalse(GuideBuilderE2EProbe.compositeLifecycle(preview));
        assertFalse(GuideBuilderE2EProbe.compositeLifecycle(new JsonObject()));
    }
    @Test void reloadRequiresExactPriorIdsAndStatusesNotOnlyOperationCount() {
        var retained = com.google.gson.JsonParser.parseString("""
                {"operations":[{"operationId":"source"}],"templates":{"saved":["template"]},
                 "lifecycle":{"partial":{"status":{"operationId":"partial"}},
                 "cancel":{"status":{"operationId":"cancel"}},"undo":{"originalStatus":{"operationId":"original"},
                 "interventionStatus":{"operationId":"intervention"},"status":{"operationId":"undo"}}}}
                """).getAsJsonObject();
        var reload = com.google.gson.JsonParser.parseString("""
                {"listed":["template"],"operations":[{"id":"source","status":"completed"},
                 {"id":"partial","status":"failed"},{"id":"cancel","status":"cancelled"},
                 {"id":"original","status":"completed"},{"id":"intervention","status":"completed"},
                 {"id":"undo","status":"completed"}]}
                """).getAsJsonObject();
        assertTrue(GuideBuilderE2EProbe.persistedOperationsMatch(retained, reload));
        reload.getAsJsonArray("operations").get(1).getAsJsonObject().addProperty("status", "completed");
        assertFalse(GuideBuilderE2EProbe.persistedOperationsMatch(retained, reload));
        assertFalse(GuideBuilderE2EProbe.persistedOperationsMatch(retained, new JsonObject()));
    }
    @Test void reloadUsesPassedRetainedOriginDespiteMovedPlayerAndRejectsForeignEvidence() {
        var original = new GuideBuilderE2EProbe.Anchor(-1, -61, 4, "minecraft:overworld");
        var moved = new GuideBuilderE2EProbe.Anchor(22, -61, 42, "minecraft:overworld");
        var proof = com.google.gson.JsonParser.parseString("""
                {"outcome":"PASSED","worldName":"openallay-builder-test","nativeAnchor":{"x":-1,"y":-61,"z":4}}
                """).getAsJsonObject();
        assertEquals(original, GuideBuilderE2EProbe.retainedOrigin(moved, original, proof, "openallay-builder-test"));
        assertEquals("E2E retained native anchor: x=-1,y=-61,z=4", GuideBuilderE2EProbe.retainedOriginLine(original));
        assertThrows(IllegalStateException.class, () -> GuideBuilderE2EProbe.retainedOrigin(
                new GuideBuilderE2EProbe.Anchor(22, -61, 42, "minecraft:the_nether"), original, proof, "openallay-builder-test"));
        assertThrows(IllegalStateException.class, () -> GuideBuilderE2EProbe.retainedOrigin(moved, original, proof, "openallay-builder-other"));
        proof.getAsJsonObject("nativeAnchor").addProperty("x", 22);
        assertThrows(IllegalStateException.class, () -> GuideBuilderE2EProbe.retainedOrigin(moved, original, proof, "openallay-builder-test"));
        proof.addProperty("outcome", "FAILED");
        assertThrows(IllegalStateException.class, () -> GuideBuilderE2EProbe.retainedOrigin(moved, original, proof, "openallay-builder-test"));
    }
    @Test void frozenTimingRejectsMissingProofOrAnyNativeUseBeforeRevocation() {
        var revoked = java.time.Instant.parse("2026-09-30T12:00:00Z");
        assertTrue(GuideBuilderE2EProbe.frozenNativeTiming(revoked, revoked.plusMillis(1), revoked.plusMillis(1), false));
        assertFalse(GuideBuilderE2EProbe.frozenNativeTiming(revoked, revoked.minusMillis(1), revoked.plusMillis(1), false));
        assertFalse(GuideBuilderE2EProbe.frozenNativeTiming(revoked, revoked.plusMillis(1), revoked.plusMillis(1), true));
        assertFalse(GuideBuilderE2EProbe.frozenNativeTiming(revoked, null, revoked.plusMillis(1), false));
        assertFalse(GuideBuilderE2EProbe.frozenNativeTiming(null, revoked, revoked.plusMillis(1), false));
        assertFalse(GuideBuilderE2EProbe.frozenNativeTiming(revoked, revoked.plusMillis(1), null, false));
        assertFalse(GuideBuilderE2EProbe.frozenNativeTiming(revoked, revoked.plusMillis(1), revoked.minusMillis(1), false));
        assertTrue(GuideBuilderE2EProbe.frozenNativeTiming(revoked, revoked, revoked, false));
    }
    @Test void liveCopyRequiresRealRotatedNativeLandmarksBeforeUndo() {
        var checks = GuideBuilderE2EProbe.landmarks("builder-live-copy");
        var door = checks.stream().filter(value -> value.name().equals("live-copy-door-lower")).findFirst().orElseThrow();
        assertEquals(12, door.x()); assertEquals(1, door.y()); assertEquals(2, door.z());
        assertEquals("east", door.properties().get("facing"));
        assertEquals("minecraft:oak_door", door.id());
    }
    @Test void liveOracleHasNoReturnedAnswerSchemaOrFixtureClaimDependency() {
        var checks = GuideBuilderE2EProbe.landmarks("builder-live-undo");
        assertTrue(checks.stream().anyMatch(value -> value.name().equals("live-chest")));
        assertEquals(25, checks.stream().filter(value -> value.name().startsWith("live-platform-")).count());
        assertEquals(25, checks.stream().filter(value -> value.name().startsWith("live-copy-ground-")).count());
        assertTrue(GuideBuilderE2EProbe.enabled("builder-live-copy"));
    }
    private static GuideRequestSnapshot request(String status, String code, String scenario) {
        JsonObject normalized = new JsonObject(); normalized.addProperty("status", status);
        if (code != null) normalized.addProperty("code", code);
        if (scenario != null) {
            var preview = new JsonObject(); preview.addProperty("scenario", scenario);
            var output = new JsonObject(); output.add("preview", preview); normalized.add("value", output);
        }
        var tool = new GuideToolActivity("fixture-call", 0, "openallay:run_javascript",
                status.equals("success") ? GuideToolStatus.SUCCEEDED : GuideToolStatus.FAILED,
                normalized, List.of(), List.of());
        Instant now = Instant.now();
        return new GuideRequestSnapshot(UUID.randomUUID(), "e2e", GuideTopology.CLIENT_LOCAL,
                "question", List.of(new GuideTimelineEntry.Tool(0, tool)), GuideRequestStatus.COMPLETED,
                List.of(), dev.openallay.model.ModelUsage.empty(), null, null, now, now, now);
    }
}
