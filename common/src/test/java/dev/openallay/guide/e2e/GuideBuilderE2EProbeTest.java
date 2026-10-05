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
    @Test void completeAcceptanceKeepsNineBuildAndFiveLifecycleNativeJournalBindings() {
        var history = acceptanceHistory();
        var request = fixtureRequest(history);
        assertTrue(GuideBuilderE2EProbe.toolContract("builder-acceptance", request));
        var parsed = GuideBuilderE2EProbe.builderReceipt(request);
        assertEquals(9, parsed.getAsJsonArray("operations").size());
        assertEquals(14, parsed.getAsJsonArray("durableOperations").size());
        for (var value : parsed.getAsJsonArray("operations")) {
            var item = value.getAsJsonObject();
            assertEquals(item.get("operationId").getAsString(), item.getAsJsonObject("journal").get("id").getAsString());
            assertEquals("completed", item.getAsJsonObject("journal").get("status").getAsString());
        }
        for (String kind : List.of("partial", "cancel")) {
            var item = parsed.getAsJsonObject("lifecycle").getAsJsonObject(kind);
            assertFalse(item.has("status"));
            assertEquals(1, item.getAsJsonObject("journal").get("entries").getAsInt());
        }
        var undo = parsed.getAsJsonObject("lifecycle").getAsJsonObject("undo");
        for (String key : List.of("originalStatus", "interventionStatus", "status"))
            assertEquals(undo.getAsJsonObject(key).get("operationId").getAsString(),
                    undo.getAsJsonObject(key).getAsJsonObject("journal").get("id").getAsString());
        // Enrichment is real final-row evidence. It must not mutate normalized Tool wire data.
        assertFalse(GuideBuilderE2EProbe.builderReceipt(request).getAsJsonArray("operations").get(0)
                .getAsJsonObject().getAsJsonObject("journal").has("beforeImages"));
        assertFalse(com.google.gson.JsonParser.parseString(history.getLast().getAsJsonObject("value").get("preview").getAsString())
                .getAsJsonObject().getAsJsonArray("operations").get(0).getAsJsonObject().has("journal"));
    }
    @Test void finalSuccessCannotHideMissingReorderedWrongOrUnexpectedFailedTools() {
        assertRejected(List.of(normalized(acceptanceReceipt())), "old single success fixture");
        for (int index = 0; index < 7; index++) {
            var missing = acceptanceHistory(); missing.remove(index);
            assertRejected(missing, "missing actual Tool " + index);
        }
        for (int index : List.of(1, 3)) {
            for (String code : List.of("javascript_error", "permission_denied", "other_native_failure",
                    index == 1 ? "session_closed" : "invalid_native_input")) {
                var wrong = acceptanceHistory(); wrong.set(index, failure(code));
                assertRejected(wrong, "wrong failed Tool code " + index + " " + code);
            }
            var success = acceptanceHistory(); success.set(index, normalized(receipt("builder_acceptance", "completed")));
            assertRejected(success, "expected rejection became success " + index);
            var message = acceptanceHistory(); message.get(index).addProperty("message", "unrelated domain failure");
            assertRejected(message, "wrong native message " + index);
        }
        for (int index : List.of(0, 2, 4, 5, 6)) {
            var unexpected = acceptanceHistory(); unexpected.set(index, failure("invalid_native_input"));
            assertRejected(unexpected, "unexpected failed positive Tool " + index);
        }
        var reordered = acceptanceHistory(); java.util.Collections.swap(reordered, 1, 3);
        assertRejected(reordered, "reordered expected failures");
        var extra = acceptanceHistory(); extra.add(failure("session_closed"));
        assertRejected(extra, "extra failure");
        var corruptedEarlyReceipt = acceptanceHistory(); corruptedEarlyReceipt.get(0).getAsJsonObject("value").addProperty("preview", "{} {}");
        assertRejected(corruptedEarlyReceipt, "strict parsing covers early success, not just final");
        var wrongActualStatus = fixtureTools(acceptanceHistory());
        var tool = wrongActualStatus.get(2);
        wrongActualStatus.set(2, new GuideToolActivity(tool.invocationId(), tool.index(), tool.toolId(), GuideToolStatus.SUCCEEDED,
                tool.invocationArguments(), tool.normalized(), List.of(), List.of()));
        assertFalse(GuideBuilderE2EProbe.toolContract("builder-acceptance", requestTools(wrongActualStatus)));
        var duplicateCalls = fixtureTools(acceptanceHistory());
        var lastCall = duplicateCalls.getLast();
        duplicateCalls.set(7, new GuideToolActivity(duplicateCalls.get(1).invocationId(), lastCall.index(), lastCall.toolId(),
                lastCall.status(), lastCall.invocationArguments(), lastCall.normalized(), List.of(), List.of()));
        assertFalse(GuideBuilderE2EProbe.toolContract("builder-acceptance", requestTools(duplicateCalls)));
        var unrelated = fixtureTools(acceptanceHistory());
        unrelated.add(new GuideToolActivity("unrelated", 8, "openallay:other", GuideToolStatus.SUCCEEDED,
                normalized(receipt("other", "completed")), List.of(), List.of()));
        assertFalse(GuideBuilderE2EProbe.toolContract("builder-acceptance", requestTools(unrelated)));
        var wrongSkill = fixtureTools(acceptanceHistory());
        wrongSkill.set(0, new GuideToolActivity("skill", 0, "openallay:load_skill", GuideToolStatus.FAILED,
                skillArguments(), failure("skill_unavailable"), List.of(), List.of()));
        assertFalse(GuideBuilderE2EProbe.toolContract("builder-acceptance", requestTools(wrongSkill)));
    }
    @Test void acceptanceRequiresAllNineCompletedReceiptsAndExactNativeRows() {
        for (int index = 0; index < 9; index++) {
            final int row = index;
            assertAcceptanceMutation(0, r -> r.getAsJsonArray("operations").get(row).getAsJsonObject().remove("operationId"), "missing build ID " + row);
            assertAcceptanceMutation(0, r -> r.getAsJsonArray("operations").get(row).getAsJsonObject().addProperty("state", "running"), "unclosed build " + row);
            assertAcceptanceMutation(0, r -> r.getAsJsonArray("baselineOperations").remove(row), "missing completed journal " + row);
        }
        assertAcceptanceMutation(0, r -> r.getAsJsonArray("operations").get(1).getAsJsonObject().addProperty("operationId", "unit-house"), "duplicate build ID");
        assertAcceptanceMutation(0, r -> r.getAsJsonArray("operations").get(1).getAsJsonObject().addProperty("name", "house"), "wrong nine-operation order");
        assertAcceptanceMutation(6, r -> r.getAsJsonObject("observationStatus").addProperty("writes", 1), "final read-only Tool wrote");
        assertAcceptanceMutation(6, r -> r.getAsJsonArray("operations").get(0).getAsJsonObject().addProperty("operationId", "final-only-build"), "final cannot change original completed build ID");
        assertAcceptanceMutation(0, r -> r.getAsJsonArray("baselineOperations").get(0).getAsJsonObject().addProperty("entries", 0), "empty completed journal");
        assertAcceptanceMutation(0, r -> r.getAsJsonArray("baselineOperations").add(journal("old", "old", "completed", 1)), "unlinked baseline journal");
        assertAcceptanceMutation(0, r -> r.getAsJsonObject("templates").remove("saved"), "missing saved template");
        assertAcceptanceMutation(0, r -> r.getAsJsonArray("actions").get(0).getAsJsonObject().addProperty("status", "failed"), "path not built");
        assertAcceptanceMutation(0, r -> r.addProperty("probeToken", "other-call"), "not actual JS call ID");
        assertAcceptanceMutation(2, r -> r.getAsJsonObject("context").addProperty("dimension", "minecraft:the_nether"), "foreign context");
    }
    @Test void durableFailureRequiresSoleNewLabelledIdOneEntryAndExactNativeEffects() {
        for (int stage : List.of(2, 4)) {
            String kind = stage == 2 ? "partial" : "cancel";
            for (String key : List.of("id", "label", "status", "entries"))
                assertAcceptanceMutation(stage, r -> r.getAsJsonObject("lifecycle").getAsJsonObject(kind).getAsJsonObject("journal").remove(key), "missing " + kind + " journal " + key);
            assertAcceptanceMutation(stage, r -> r.getAsJsonObject("lifecycle").getAsJsonObject(kind).getAsJsonObject("journal").addProperty("id", "unit-house"), "old ID reused");
            assertAcceptanceMutation(stage, r -> r.getAsJsonObject("lifecycle").getAsJsonObject(kind).getAsJsonObject("journal").addProperty("entries", 2), "wrong failure count");
            assertAcceptanceMutation(stage, r -> r.getAsJsonObject("lifecycle").getAsJsonObject(kind).getAsJsonObject("journal").addProperty("entries", "1"), "string count is not native integer");
            assertAcceptanceMutation(stage, r -> r.getAsJsonObject("lifecycle").getAsJsonObject(kind).getAsJsonObject("journal").add("beforeImages", images("air", "air")), "fake public journal beforeimage getter");
            assertAcceptanceMutation(stage, r -> r.getAsJsonObject("lifecycle").getAsJsonObject(kind).getAsJsonObject("failure").addProperty("code", "other_native_failure"), "failure receipt must bind actual failed Tool");
            assertAcceptanceMutation(stage, r -> r.getAsJsonObject("lifecycle").getAsJsonObject(kind).getAsJsonObject("journal").addProperty("label", "other-label"), "wrong request label");
            assertAcceptanceMutation(stage, r -> r.getAsJsonObject("lifecycle").getAsJsonObject(kind).getAsJsonObject("journal").addProperty("status", "completed"), "failure relabelled completed");
            assertAcceptanceMutation(stage, r -> r.getAsJsonObject("lifecycle").getAsJsonObject(kind).add("status", nativeStatus("unit-" + kind)), "fabricated dead status");
            assertAcceptanceMutation(stage, r -> r.getAsJsonObject("lifecycle").getAsJsonObject(kind).getAsJsonArray("afterImages").set(0, image("air")), "earlier write lost");
            assertAcceptanceMutation(stage, r -> r.getAsJsonObject("lifecycle").getAsJsonObject(kind).getAsJsonArray("afterImages").set(1, image("gold_block")), "rejected write applied");
            assertAcceptanceMutation(stage, r -> r.getAsJsonObject("lifecycle").getAsJsonObject(kind).getAsJsonArray("beforeImages").set(0, image("gold_block")), "false before-image");
            assertAcceptanceMutation(stage, r -> r.getAsJsonObject("lifecycle").getAsJsonObject(kind).getAsJsonArray("positions").get(1).getAsJsonObject().addProperty("x", 123), "wrong marker cell");
            assertAcceptanceMutation(stage, r -> r.getAsJsonArray("durableOperations").add(journal("extra", "extra", "completed", 1)), "unexpected new journal");
            assertAcceptanceMutation(stage, r -> r.getAsJsonArray("durableOperations").get(0).getAsJsonObject().addProperty("entries", 99), "changed old journal");
            assertAcceptanceMutation(stage, r -> r.getAsJsonObject("observationStatus").addProperty("writes", 1), "observer wrote");
        }
        assertAcceptanceMutation(6, r -> r.getAsJsonObject("lifecycle").getAsJsonObject("partial").getAsJsonObject("journal").addProperty("id", "final-only"), "final did not retain actual failed ID");
        assertAcceptanceMutation(6, r -> r.getAsJsonArray("durableOperations").remove(9), "final lost failed journal");
    }
    @Test void undoRequiresExactOriginalInterventionRestoreBindingsAndSecondConflictCell() {
        for (String key : List.of("originalStatus", "interventionStatus", "status")) {
            assertAcceptanceMutation(5, r -> r.getAsJsonObject("lifecycle").getAsJsonObject("undo").getAsJsonObject(key).remove("operationId"), "missing undo ID " + key);
            assertAcceptanceMutation(5, r -> r.getAsJsonObject("lifecycle").getAsJsonObject("undo").getAsJsonObject(key).addProperty("operationId", "unit-house"), "old undo ID " + key);
        }
        for (int row = 11; row < 14; row++) {
            final int index = row;
            assertAcceptanceMutation(5, r -> r.getAsJsonArray("durableOperations").get(index).getAsJsonObject().addProperty("entries", 7), "wrong undo count " + index);
            assertAcceptanceMutation(5, r -> r.getAsJsonArray("durableOperations").get(index).getAsJsonObject().addProperty("status", "failed"), "wrong undo state " + index);
        }
        assertAcceptanceMutation(5, r -> r.getAsJsonObject("lifecycle").getAsJsonObject("undo").getAsJsonObject("result").addProperty("restored", 2), "wrong restored count");
        assertAcceptanceMutation(5, r -> r.getAsJsonObject("lifecycle").getAsJsonObject("undo").getAsJsonObject("result").addProperty("operationId", "different-restore"), "result/status ID mismatch");
        assertAcceptanceMutation(5, r -> r.getAsJsonObject("lifecycle").getAsJsonObject("undo").getAsJsonObject("result").getAsJsonArray("conflicts").set(0, r.getAsJsonObject("lifecycle").getAsJsonObject("undo").getAsJsonArray("positions").get(0).deepCopy()), "first rather than second conflict cell");
        assertAcceptanceMutation(5, r -> r.getAsJsonObject("lifecycle").getAsJsonObject("undo").getAsJsonObject("result").getAsJsonArray("uncertain").add(position(0, 1, 0)), "uncertain undo");
        assertAcceptanceMutation(5, r -> r.getAsJsonObject("lifecycle").getAsJsonObject("undo").getAsJsonArray("afterImages").set(1, image("air")), "conflict overwritten");
        var receipt = GuideBuilderE2EProbe.builderReceipt(fixtureRequest(acceptanceHistory()));
        assertTrue(GuideBuilderE2EProbe.compositeLifecycle(receipt));
        receipt.getAsJsonObject("lifecycle").getAsJsonObject("undo").getAsJsonObject("result").getAsJsonArray("conflicts").get(0).getAsJsonObject().addProperty("z", 1);
        assertFalse(GuideBuilderE2EProbe.compositeLifecycle(receipt));
    }
    @Test void standaloneFailureCasesNeedActualFailedToolAndFreshDurableObserver() {
        for (String kind : List.of("partial", "cancel")) {
            var history = standaloneHistory(kind);
            assertTrue(GuideBuilderE2EProbe.toolContract("builder-" + kind, fixtureRequest(history)));
            var wrong = standaloneHistory(kind); wrong.set(1, failure(kind.equals("partial") ? "session_closed" : "invalid_native_input"));
            assertFalse(GuideBuilderE2EProbe.toolContract("builder-" + kind, fixtureRequest(wrong)));
            var missing = standaloneHistory(kind); missing.remove(1);
            assertFalse(GuideBuilderE2EProbe.toolContract("builder-" + kind, fixtureRequest(missing)));
            var altered = standaloneHistory(kind);
            var finalReceipt = wireReceipt(altered.getLast());
            finalReceipt.getAsJsonObject("lifecycle").getAsJsonObject(kind).getAsJsonObject("journal").addProperty("entries", 2);
            altered.set(2, normalized(finalReceipt));
            assertFalse(GuideBuilderE2EProbe.toolContract("builder-" + kind, fixtureRequest(altered)));
        }
    }
    @Test void reloadRequiresAllFourteenExactIdsLabelsStatusesAndCountsFromActualFinalRows() {
        var retained = GuideBuilderE2EProbe.builderReceipt(fixtureRequest(acceptanceHistory()));
        var reload = reloadReceipt(retained);
        assertTrue(GuideBuilderE2EProbe.persistedOperationsMatch(retained, reload));
        assertTrue(GuideBuilderE2EProbe.toolContract("builder-reload", request(normalized(reload))));
        for (int index = 0; index < 14; index++) {
            var missing = reload.deepCopy(); missing.getAsJsonArray("operations").remove(index); missing.addProperty("operationCount", 13);
            assertFalse(GuideBuilderE2EProbe.persistedOperationsMatch(retained, missing), "missing row " + index);
            for (String key : List.of("id", "label", "status", "entries")) {
                var changed = reload.deepCopy(); var row = changed.getAsJsonArray("operations").get(index).getAsJsonObject();
                if (key.equals("entries")) row.addProperty(key, row.get(key).getAsInt() + 1); else row.addProperty(key, "wrong");
                assertFalse(GuideBuilderE2EProbe.persistedOperationsMatch(retained, changed), "changed row " + index + " " + key);
            }
        }
        var duplicate = reload.deepCopy(); duplicate.getAsJsonArray("operations").add(duplicate.getAsJsonArray("operations").get(0).deepCopy()); duplicate.addProperty("operationCount", 15);
        assertFalse(GuideBuilderE2EProbe.persistedOperationsMatch(retained, duplicate));
        var extra = reload.deepCopy(); extra.getAsJsonArray("operations").add(journal("extra", "extra", "completed", 1)); extra.addProperty("operationCount", 15);
        assertFalse(GuideBuilderE2EProbe.persistedOperationsMatch(retained, extra));
        var wrongCount = reload.deepCopy(); wrongCount.addProperty("operationCount", 9);
        assertFalse(GuideBuilderE2EProbe.persistedOperationsMatch(retained, wrongCount));
        var noTemplate = reload.deepCopy(); noTemplate.getAsJsonArray("listed").remove(0);
        assertFalse(GuideBuilderE2EProbe.persistedOperationsMatch(retained, noTemplate));
        var noBinding = retained.deepCopy(); noBinding.getAsJsonArray("operations").get(0).getAsJsonObject().remove("journal");
        assertFalse(GuideBuilderE2EProbe.persistedOperationsMatch(noBinding, reload));
    }
    @Test void allEightyFiveNativeExpectationsAndActorAnchorBindingRemainIndependent() throws Exception {
        var anchor = new GuideBuilderE2EProbe.Anchor(10, 64, 20, "minecraft:overworld");
        var checks = GuideBuilderE2EProbe.oracleLandmarks("builder-acceptance", anchor);
        assertEquals(85, checks.size());
        assertEquals(checks, GuideBuilderE2EProbe.oracleLandmarks("builder-reload", anchor));
        assertEquals(85, checks.stream().map(GuideBuilderE2EProbe.Landmark::name).distinct().count());
        var descriptor = new StringBuilder();
        for (var expected : checks) {
            descriptor.append(expected.name()).append('|').append(expected.x()).append(',').append(expected.y()).append(',')
                    .append(expected.z()).append('|').append(expected.id()).append('|');
            expected.properties().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> descriptor.append(entry.getKey()).append('=').append(entry.getValue()).append(';'));
            descriptor.append('\n');
        }
        assertEquals("3c57b2038c213c6b3d7622a258411e3bb8464b88a4e2c45c31b02653a11f207c", java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(descriptor.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))));

        for (var expected : checks) {
            assertTrue(GuideBuilderE2EProbe.matches(expected, expected.id(), expected.properties()), expected.name());
            assertFalse(GuideBuilderE2EProbe.matches(expected, "minecraft:invalid", expected.properties()), expected.name());
            for (var property : expected.properties().entrySet()) {
                var wrong = new java.util.HashMap<>(expected.properties()); wrong.put(property.getKey(), "wrong");
                assertFalse(GuideBuilderE2EProbe.matches(expected, expected.id(), wrong), expected.name() + " " + property.getKey());
            }
        }
        var crop = checks.stream().filter(value -> value.name().equals("farm-crop")).findFirst().orElseThrow();
        assertEquals(Map.of("age", "3"), crop.properties());
        var checkerboard = checks.getLast(); assertEquals("minecraft:quartz_block", checkerboard.id());
        assertEquals("minecraft:black_concrete", GuideBuilderE2EProbe.oracleLandmarks("builder-acceptance",
                new GuideBuilderE2EProbe.Anchor(11, 64, 20, "minecraft:overworld")).getLast().id());
        var receipt = GuideBuilderE2EProbe.builderReceipt(fixtureRequest(acceptanceHistory()));
        var actor = UUID.fromString("00000000-0000-0000-0000-000000000017");
        assertTrue(GuideBuilderE2EProbe.nativeReceiptBinding(receipt, anchor, actor));
        assertFalse(GuideBuilderE2EProbe.nativeReceiptBinding(receipt, new GuideBuilderE2EProbe.Anchor(11, 64, 20, "minecraft:overworld"), actor));
        assertFalse(GuideBuilderE2EProbe.nativeReceiptBinding(receipt, new GuideBuilderE2EProbe.Anchor(10, 64, 20, "minecraft:the_nether"), actor));
        assertFalse(GuideBuilderE2EProbe.nativeReceiptBinding(receipt, anchor, UUID.randomUUID()));
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
    private static final String PROBE_TOKEN = "fixture-call-0";
    private static final List<String> BUILD_NAMES = List.of("house", "skyscraper", "cottage", "windmill", "farm", "dock", "geometry_decoration", "terrain", "templates");

    private static JsonObject commonReceipt(String scenario, String stage) {
        var receipt = receipt("builder_" + scenario, "completed"); receipt.addProperty("stage", stage);
        receipt.addProperty("probeToken", PROBE_TOKEN); receipt.add("anchor", position(10, 64, 20));
        receipt.add("context", com.google.gson.JsonParser.parseString("""
                {"dimension":"minecraft:overworld","playerUuid":"00000000-0000-0000-0000-000000000017"}
                """));
        return receipt;
    }
    private static JsonObject acceptanceReceipt() {
        return wireReceipt(acceptanceHistory().getLast());
    }
    private static List<JsonObject> acceptanceHistory() {
        var build = commonReceipt("acceptance", "build");
        var operations = new com.google.gson.JsonArray(); var rows = new com.google.gson.JsonArray();
        for (String name : BUILD_NAMES) {
            var operation = nativeStatus("unit-" + name); operation.addProperty("name", name);
            operation.addProperty("reads", 10); operation.addProperty("writes", 100);
            operations.add(operation); rows.add(journal("unit-" + name, "OpenAllay E2E Builder acceptance", "completed", 3));
        }
        build.add("operations", operations); build.add("status", nativeStatus("unit-templates"));
        build.add("baselineOperations", rows.deepCopy());
        build.add("actions", com.google.gson.JsonParser.parseString("""
                [{"name":"terrain_path","status":"built"},{"name":"terrain_smart_path","status":"built"}]
                """));
        build.add("templates", com.google.gson.JsonParser.parseString("""
                {"listed":true,"saved":["openallay_e2e_builder_native"]}
                """));
        build.add("sites", new JsonObject()); build.add("terrain", new JsonObject());
        build.addProperty("seed", 17); build.addProperty("provider", "deterministic_loopback_fixture_not_live_model");
        var before = new JsonObject();
        before.add("partial", lifecyclePrerequisite(44, 32)); before.add("cancel", lifecyclePrerequisite(44, 34));
        before.add("undo", lifecyclePrerequisite(44, 36)); build.add("lifecycle", before);
        var partialFailure = failure("invalid_native_input"); var cancelFailure = failure("session_closed");
        var partialRecord = failureLifecycle("partial", 44, 32, partialFailure);
        rows.add(partialRecord.getAsJsonObject("journal").deepCopy());
        var partial = observation("acceptance", "partial_observation", "partial", partialRecord, rows);
        var cancelRecord = failureLifecycle("cancel", 44, 34, cancelFailure);
        rows.add(cancelRecord.getAsJsonObject("journal").deepCopy());
        var cancel = observation("acceptance", "cancel_observation", "cancel", cancelRecord, rows);
        var undoRecord = lifecyclePrerequisite(44, 36);
        undoRecord.add("originalStatus", nativeStatus("unit-original"));
        undoRecord.add("interventionStatus", nativeStatus("unit-intervention"));
        undoRecord.add("status", nativeStatus("unit-undo"));
        var result = new JsonObject(); result.addProperty("restored", 1); result.addProperty("operationId", "unit-undo");
        var conflicts = new com.google.gson.JsonArray(); conflicts.add(undoRecord.getAsJsonArray("positions").get(1).deepCopy());
        result.add("conflicts", conflicts); result.add("uncertain", new com.google.gson.JsonArray()); undoRecord.add("result", result);
        undoRecord.add("afterImages", images("air", "diamond_block"));
        rows.add(journal("unit-original", label("undo original"), "completed", 2));
        rows.add(journal("unit-intervention", label("undo intervention"), "completed", 1));
        rows.add(journal("unit-undo", "Undo unit-original", "completed", 1));
        var undo = commonReceipt("acceptance", "undo"); var undoLifecycle = new JsonObject(); undoLifecycle.add("undo", undoRecord);
        undo.add("lifecycle", undoLifecycle); undo.add("durableOperations", rows.deepCopy());
        var finalReceipt = build.deepCopy(); finalReceipt.addProperty("stage", "final");
        var lifecycle = new JsonObject(); lifecycle.add("partial", partialRecord.deepCopy()); lifecycle.add("cancel", cancelRecord.deepCopy()); lifecycle.add("undo", undoRecord.deepCopy());
        finalReceipt.add("lifecycle", lifecycle); finalReceipt.add("durableOperations", rows.deepCopy()); finalReceipt.add("observationStatus", readOnlyStatus());
        return new java.util.ArrayList<>(List.of(normalized(build), partialFailure, normalized(partial), cancelFailure,
                normalized(cancel), normalized(undo), normalized(finalReceipt)));
    }
    private static List<JsonObject> standaloneHistory(String kind) {
        var baseline = commonReceipt(kind, "baseline"); baseline.add("status", readOnlyStatus());
        baseline.add("baselineOperations", new com.google.gson.JsonArray()); var lifecycle = new JsonObject();
        lifecycle.add(kind, lifecyclePrerequisite(0, 0)); baseline.add("lifecycle", lifecycle);
        var failed = failure(kind.equals("partial") ? "invalid_native_input" : "session_closed");
        var observed = failureLifecycle(kind, 0, 0, failed); var rows = new com.google.gson.JsonArray(); rows.add(observed.getAsJsonObject("journal").deepCopy());
        var finalReceipt = observation(kind, "final", kind, observed, rows); finalReceipt.add("status", readOnlyStatus());
        finalReceipt.add("baselineOperations", new com.google.gson.JsonArray());
        return new java.util.ArrayList<>(List.of(normalized(baseline), failed, normalized(finalReceipt)));
    }
    private static JsonObject observation(String scenario, String stage, String kind, JsonObject item, com.google.gson.JsonArray rows) {
        var receipt = commonReceipt(scenario, stage); var lifecycle = new JsonObject(); lifecycle.add(kind, item.deepCopy());
        receipt.add("lifecycle", lifecycle); receipt.add("durableOperations", rows.deepCopy()); receipt.add("observationStatus", readOnlyStatus());
        return receipt;
    }
    private static JsonObject failureLifecycle(String kind, int x, int z, JsonObject failure) {
        var item = lifecyclePrerequisite(x, z); item.add("failure", failure.deepCopy());
        item.add("journal", journal("unit-" + kind, label(kind), kind.equals("partial") ? "failed" : "cancelled", 1));
        item.add("afterImages", images(kind.equals("partial") ? "gold_block" : "diamond_block", "air"));
        return item;
    }
    private static JsonObject lifecyclePrerequisite(int x, int z) {
        var item = new JsonObject(); var positions = new com.google.gson.JsonArray();
        positions.add(position(10 + x, 65, 20 + z)); positions.add(position(11 + x, 65, 20 + z));
        item.add("positions", positions); item.add("beforeImages", images("air", "air")); return item;
    }
    private static JsonObject position(int x, int y, int z) {
        var value = new JsonObject(); value.addProperty("x", x); value.addProperty("y", y); value.addProperty("z", z); return value;
    }
    private static JsonObject image(String id) {
        var value = new JsonObject(); value.addProperty("id", "minecraft:" + id); value.add("properties", new JsonObject()); return value;
    }
    private static com.google.gson.JsonArray images(String first, String second) {
        var images = new com.google.gson.JsonArray(); images.add(image(first)); images.add(image(second)); return images;
    }
    private static String label(String kind) { return "OpenAllay E2E lifecycle " + PROBE_TOKEN + " " + kind; }
    private static JsonObject journal(String id, String label, String status, int entries) {
        var row = new JsonObject(); row.addProperty("id", id); row.addProperty("label", label);
        row.addProperty("status", status); row.addProperty("entries", entries); return row;
    }
    private static JsonObject nativeStatus(String id) {
        var status = new JsonObject(); status.addProperty("state", "completed"); status.addProperty("operationId", id); return status;
    }
    private static JsonObject readOnlyStatus() {
        var status = new JsonObject(); status.addProperty("state", "completed"); status.addProperty("writes", 0); return status;
    }
    private static JsonObject failure(String code) {
        var failure = new JsonObject(); failure.addProperty("status", "failure"); failure.addProperty("code", code);
        failure.addProperty("message", code.equals("invalid_native_input") ? "Builder native operation failed; inspect the session status"
                : code.equals("session_closed") ? "Builder session is closed or cancelled" : "other native failure"); return failure;
    }
    private static JsonObject wireReceipt(JsonObject normalized) {
        return com.google.gson.JsonParser.parseString(normalized.getAsJsonObject("value").get("preview").getAsString()).getAsJsonObject();
    }
    private static JsonObject reloadReceipt(JsonObject retained) {
        var reload = receipt("builder_reload", "completed"); reload.add("status", readOnlyStatus());
        reload.add("operations", retained.getAsJsonArray("durableOperations").deepCopy()); reload.addProperty("operationCount", 14);
        reload.add("listed", com.google.gson.JsonParser.parseString("[\"openallay_e2e_builder_native\"]"));
        var template = new JsonObject(); template.addProperty("name", "openallay_e2e_builder_native"); reload.add("template", template); return reload;
    }
    private static void assertRejected(List<JsonObject> history, String reason) {
        var request = fixtureRequest(history);
        assertFalse(GuideBuilderE2EProbe.toolContract("builder-acceptance", request), reason);
        assertThrows(IllegalArgumentException.class, () -> GuideBuilderE2EProbe.builderReceipt(request), reason);
    }
    private static void assertAcceptanceMutation(int index, java.util.function.Consumer<JsonObject> change, String reason) {
        var history = acceptanceHistory(); var receipt = wireReceipt(history.get(index)); change.accept(receipt);
        history.set(index, normalized(receipt));
        var finalReceipt = wireReceipt(history.getLast());
        if (index == 0) {
            for (String key : List.of("anchor", "context", "probeToken", "operations", "status", "actions", "templates", "sites", "terrain", "baselineOperations")) {
                if (receipt.has(key)) finalReceipt.add(key, receipt.get(key).deepCopy()); else finalReceipt.remove(key);
            }
        } else if (index == 2 || index == 4 || index == 5) {
            String kind = index == 2 ? "partial" : index == 4 ? "cancel" : "undo";
            finalReceipt.getAsJsonObject("lifecycle").add(kind, receipt.getAsJsonObject("lifecycle").get(kind).deepCopy());
            if (index == 5) finalReceipt.add("durableOperations", receipt.get("durableOperations").deepCopy());
        }
        if (index != 6) history.set(6, normalized(finalReceipt));
        assertRejected(history, reason);
    }
    private static JsonObject skillArguments() {
        var args = new JsonObject(); args.addProperty("name", "minecraft-builder"); return args;
    }
    private static List<GuideToolActivity> fixtureTools(List<JsonObject> history) {
        var tools = new java.util.ArrayList<GuideToolActivity>(); var skill = new JsonObject(); skill.addProperty("status", "success");
        tools.add(new GuideToolActivity("fixture-skill", 0, "openallay:load_skill", GuideToolStatus.SUCCEEDED, skillArguments(), skill, List.of(), List.of()));
        for (int index = 0; index < history.size(); index++) {
            var normalized = history.get(index); tools.add(new GuideToolActivity("fixture-call-" + index, index + 1, "openallay:run_javascript",
                    normalized.get("status").getAsString().equals("success") ? GuideToolStatus.SUCCEEDED : GuideToolStatus.FAILED,
                    normalized, List.of(), List.of()));
        }
        return tools;
    }
    private static GuideRequestSnapshot fixtureRequest(List<JsonObject> history) { return requestTools(fixtureTools(history)); }
    private static GuideRequestSnapshot requestTools(List<GuideToolActivity> tools) {
        var timeline = new java.util.ArrayList<GuideTimelineEntry>();
        for (int index = 0; index < tools.size(); index++) timeline.add(new GuideTimelineEntry.Tool(index, tools.get(index)));
        Instant now = Instant.now();
        return new GuideRequestSnapshot(UUID.randomUUID(), "e2e", GuideTopology.CLIENT_LOCAL, "question", timeline,
                GuideRequestStatus.COMPLETED, List.of(), dev.openallay.model.ModelUsage.empty(), null, null, now, now, now);
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
