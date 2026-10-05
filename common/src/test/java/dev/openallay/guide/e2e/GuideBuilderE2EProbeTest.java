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
        assertEquals(84, landmarks.size()); // The coordinate-dependent checkerboard is the 85th native check.
        var restricted = GuideBuilderE2EProbe.landmarks("builder-restricted");
        assertEquals(1, restricted.size());
        assertEquals("minecraft:gold_block", restricted.getFirst().id());
        assertEquals(0, restricted.getFirst().x()); assertEquals(1, restricted.getFirst().y()); assertEquals(0, restricted.getFirst().z());
        assertEquals("minecraft:gold_block", GuideBuilderE2EProbe.landmarks("builder-partial").getFirst().id());
        assertEquals("minecraft:diamond_block", GuideBuilderE2EProbe.landmarks("builder-cancel").getFirst().id());
    }
    @Test void restrictedBuilderRequiresRealSuccessfulReceiptAndGoldReadback() {
        var receipt = receipt("builder_restricted", "completed");
        receipt.addProperty("readback", "minecraft:gold_block");
        assertTrue(GuideBuilderE2EProbe.toolContract("builder-restricted", request(normalized(receipt))));
        receipt.addProperty("readback", "minecraft:air");
        assertFalse(GuideBuilderE2EProbe.toolContract("builder-restricted", request(normalized(receipt))));
        receipt.remove("readback");
        assertFalse(GuideBuilderE2EProbe.toolContract("builder-restricted", request(normalized(receipt))));
        assertFalse(GuideBuilderE2EProbe.toolContract("builder-restricted", request("failure", "javascript_error", null)));
        assertTrue(GuideBuilderE2EProbe.startupSettingsMatch("builder-restricted", false));
        assertFalse(GuideBuilderE2EProbe.startupSettingsMatch("builder-restricted", true));
        for (String scenario : List.of("builder-acceptance", "builder-partial", "builder-cancel", "builder-reload", "builder-undo", "builder-live-copy", "builder-live-undo")) {
            assertTrue(GuideBuilderE2EProbe.startupSettingsMatch(scenario, false), scenario);
            assertTrue(GuideBuilderE2EProbe.startupSettingsMatch(scenario, true), scenario);
        }
    }
    @Test void serverJavaDenialRemainsIndependentOfBuilderWrites() {
        var denied = new JsonObject(); denied.addProperty("status", "failure"); denied.addProperty("code", "javascript_error");
        denied.addProperty("message", "ReferenceError: \"Java\" is not defined. (openallay-agent.js#1)");
        assertTrue(GuideBuilderE2EProbe.toolContract("builder-server-denied", request(denied)));
        denied.addProperty("message", "Error: unknown block");
        assertFalse(GuideBuilderE2EProbe.toolContract("builder-server-denied", request(denied)));
        assertFalse(GuideBuilderE2EProbe.toolContract("builder-server-denied", request("failure", "javascript_error", null)));
        assertFalse(GuideBuilderE2EProbe.toolContract("builder-server-denied", request("success", null, "builder_server_denied")));
        assertFalse(GuideBuilderE2EProbe.toolContract("builder-server-denied", request("failure", "permission_denied", null)));
        assertFalse(GuideBuilderE2EProbe.toolContract("builder-acceptance", request("success", null, "other_scenario")));
    }
    @Test void scalarReceiptParserRequiresSuccessCompleteStringAndStrictSingleObject() {
        var expected = receipt("builder_restricted", "completed");
        expected.addProperty("readback", "minecraft:gold_block");
        var good = normalized(expected);
        assertEquals(expected, GuideBuilderE2EProbe.builderReceipt(request(good)));
        for (String json : List.of("not JSON", "[]", "null", "{} {}", "{scenario:'builder_restricted'}",
                "{\"scenario\":\"builder_restricted\",\"scenario\":\"builder_restricted\"}",
                "{\"status\":TRUE}", "{/*comment*/\"scenario\":\"builder_restricted\"}")) {
            var invalid = good.deepCopy(); invalid.getAsJsonObject("value").addProperty("preview", json);
            assertThrows(IllegalArgumentException.class, () -> GuideBuilderE2EProbe.builderReceipt(request(invalid)), json);
            assertFalse(GuideBuilderE2EProbe.toolContract("builder-restricted", request(invalid)), json);
        }
        var oldObjectPreview = good.deepCopy(); oldObjectPreview.getAsJsonObject("value").add("preview", expected);
        var incomplete = good.deepCopy(); incomplete.getAsJsonObject("value").addProperty("complete", false);
        var stringComplete = good.deepCopy(); stringComplete.getAsJsonObject("value").addProperty("complete", "true");
        var wrongType = good.deepCopy(); wrongType.getAsJsonObject("value").addProperty("resultType", "object");
        var absentType = good.deepCopy(); absentType.getAsJsonObject("value").remove("resultType");
        var failed = good.deepCopy(); failed.addProperty("status", "failure");
        for (var invalid : List.of(oldObjectPreview, incomplete, stringComplete, wrongType, absentType, failed)) {
            assertThrows(IllegalArgumentException.class, () -> GuideBuilderE2EProbe.builderReceipt(request(invalid)));
            assertFalse(GuideBuilderE2EProbe.toolContract("builder-restricted", request(invalid)));
        }
    }
    @Test void completeAcceptanceReceiptKeepsNineOperationIdsTemplateAndLifecycle() {
        var receipt = acceptanceReceipt();
        var parsed = GuideBuilderE2EProbe.builderReceipt(request(normalized(receipt)));
        assertEquals(receipt, parsed);
        assertEquals(9, parsed.getAsJsonArray("operations").size());
        assertTrue(GuideBuilderE2EProbe.toolContract("builder-acceptance", request(normalized(receipt))));
        for (int index = 0; index < 9; index++) {
            var altered = receipt.deepCopy(); altered.getAsJsonArray("operations").get(index).getAsJsonObject().remove("operationId");
            assertFalse(GuideBuilderE2EProbe.toolContract("builder-acceptance", request(normalized(altered))));
        }
        var duplicate = receipt.deepCopy(); duplicate.getAsJsonArray("operations").get(1).getAsJsonObject().addProperty("operationId", "unit-house");
        assertFalse(GuideBuilderE2EProbe.toolContract("builder-acceptance", request(normalized(duplicate))));
        var absentTemplate = receipt.deepCopy(); absentTemplate.getAsJsonObject("templates").remove("saved");
        assertFalse(GuideBuilderE2EProbe.toolContract("builder-acceptance", request(normalized(absentTemplate))));
        var brokenLifecycle = receipt.deepCopy(); brokenLifecycle.getAsJsonObject("lifecycle").getAsJsonObject("cancel").addProperty("deniedAfterCancel", false);
        assertFalse(GuideBuilderE2EProbe.toolContract("builder-acceptance", request(normalized(brokenLifecycle))));
    }
    @Test void compositeLifecycleRequiresPartialCancellationAndExactUndoConflict() {
        var preview = acceptanceReceipt();
        assertTrue(GuideBuilderE2EProbe.compositeLifecycle(preview));
        preview.getAsJsonObject("lifecycle").getAsJsonObject("cancel").addProperty("deniedAfterCancel", false);
        assertFalse(GuideBuilderE2EProbe.compositeLifecycle(preview));
        assertFalse(GuideBuilderE2EProbe.compositeLifecycle(new JsonObject()));
        for (String key : List.of("originalStatus", "interventionStatus", "status")) {
            var missingId = acceptanceReceipt(); missingId.getAsJsonObject("lifecycle").getAsJsonObject("undo").getAsJsonObject(key).remove("operationId");
            assertFalse(GuideBuilderE2EProbe.compositeLifecycle(missingId), key);
        }
    }
    @Test void reloadRequiresAllNinePriorIdsLifecycleStatusesAndSavedTemplates() {
        var retained = GuideBuilderE2EProbe.builderReceipt(request(normalized(acceptanceReceipt())));
        var reload = receipt("builder_reload", "completed");
        var listed = new com.google.gson.JsonArray(); listed.add("openallay_e2e_builder_native"); reload.add("listed", listed);
        var operations = new com.google.gson.JsonArray(); reload.add("operations", operations);
        for (var value : retained.getAsJsonArray("operations")) {
            var row = new JsonObject(); row.addProperty("id", value.getAsJsonObject().get("operationId").getAsString());
            row.addProperty("status", "completed"); operations.add(row);
        }
        for (String id : List.of("partial", "cancel", "original", "intervention", "undo")) {
            var row = new JsonObject(); row.addProperty("id", "unit-" + id);
            row.addProperty("status", id.equals("partial") ? "failed" : id.equals("cancel") ? "cancelled" : "completed"); operations.add(row);
        }
        assertTrue(GuideBuilderE2EProbe.persistedOperationsMatch(retained, reload));
        for (int index = 0; index < operations.size(); index++) {
            var altered = reload.deepCopy(); altered.getAsJsonArray("operations").remove(index);
            assertFalse(GuideBuilderE2EProbe.persistedOperationsMatch(retained, altered), "missing row " + index);
        }
        var wrongStatus = reload.deepCopy(); wrongStatus.getAsJsonArray("operations").get(9).getAsJsonObject().addProperty("status", "completed");
        assertFalse(GuideBuilderE2EProbe.persistedOperationsMatch(retained, wrongStatus));
        var duplicate = reload.deepCopy(); duplicate.getAsJsonArray("operations").add(duplicate.getAsJsonArray("operations").get(0).deepCopy());
        assertFalse(GuideBuilderE2EProbe.persistedOperationsMatch(retained, duplicate));
        var missingTemplate = reload.deepCopy(); missingTemplate.getAsJsonArray("listed").remove(0);
        assertFalse(GuideBuilderE2EProbe.persistedOperationsMatch(retained, missingTemplate));
        var incompleteRetained = retained.deepCopy(); incompleteRetained.getAsJsonArray("operations").remove(0);
        assertFalse(GuideBuilderE2EProbe.persistedOperationsMatch(incompleteRetained, reload));
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
    private static JsonObject receipt(String scenario, String state) {
        var receipt = new JsonObject(); receipt.addProperty("scenario", scenario);
        var status = new JsonObject(); status.addProperty("state", state); receipt.add("status", status);
        return receipt;
    }
    private static JsonObject acceptanceReceipt() {
        var receipt = receipt("builder_acceptance", "completed");
        var operations = new com.google.gson.JsonArray(); receipt.add("operations", operations);
        for (String name : List.of("house", "skyscraper", "cottage", "windmill", "farm", "dock", "geometry_decoration", "terrain", "templates")) {
            var row = new JsonObject(); row.addProperty("name", name); row.addProperty("operationId", "unit-" + name);
            row.addProperty("state", "completed"); operations.add(row);
        }
        receipt.add("actions", com.google.gson.JsonParser.parseString("""
                [{"name":"terrain_path","status":"built"},{"name":"terrain_smart_path","status":"built"}]
                """));
        receipt.add("templates", com.google.gson.JsonParser.parseString("""
                {"listed":true,"saved":["openallay_e2e_builder_native"]}
                """));
        receipt.add("lifecycle", com.google.gson.JsonParser.parseString("""
                {"partial":{"failure":"unknown native block","status":{"state":"failed-partial","operationId":"unit-partial"}},
                 "cancel":{"deniedAfterCancel":true,"failure":"session closed","status":{"state":"cancelled-partial","operationId":"unit-cancel"}},
                 "undo":{"result":{"restored":1,"conflicts":[{"x":1}],"uncertain":[]},
                 "originalStatus":{"state":"completed","operationId":"unit-original"},
                 "interventionStatus":{"state":"completed","operationId":"unit-intervention"},
                 "status":{"state":"completed","operationId":"unit-undo"}}}
                """));
        return receipt;
    }
    private static JsonObject normalized(JsonObject receipt) {
        var normalized = new JsonObject(); normalized.addProperty("status", "success");
        var output = new JsonObject(); output.addProperty("resultType", "string"); output.addProperty("complete", true);
        output.addProperty("preview", receipt.toString()); normalized.add("value", output);
        return normalized;
    }
    private static GuideRequestSnapshot request(String status, String code, String scenario) {
        var normalized = scenario == null ? new JsonObject() : normalized(receipt(scenario, "completed"));
        normalized.addProperty("status", status);
        if (code != null) normalized.addProperty("code", code);
        return request(normalized);
    }
    private static GuideRequestSnapshot request(JsonObject normalized) {
        var tool = new GuideToolActivity("fixture-call", 0, "openallay:run_javascript",
                normalized.get("status").getAsString().equals("success") ? GuideToolStatus.SUCCEEDED : GuideToolStatus.FAILED,
                normalized, List.of(), List.of());
        Instant now = Instant.now();
        return new GuideRequestSnapshot(UUID.randomUUID(), "e2e", GuideTopology.CLIENT_LOCAL,
                "question", List.of(new GuideTimelineEntry.Tool(0, tool)), GuideRequestStatus.COMPLETED,
                List.of(), dev.openallay.model.ModelUsage.empty(), null, null, now, now, now);
    }
}
