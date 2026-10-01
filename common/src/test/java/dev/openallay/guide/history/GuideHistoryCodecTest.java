package dev.openallay.guide.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.guide.GuideModelSelection;
import dev.openallay.guide.GuideSource;
import dev.openallay.guide.GuideTimelineEntry;
import dev.openallay.guide.GuideToolActivity;
import dev.openallay.guide.GuideToolMessage;
import dev.openallay.guide.GuideToolStatus;
import dev.openallay.testing.GroundedTestFixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class GuideHistoryCodecTest {
    private static final UUID ACTOR =
            UUID.fromString("849d783f-aa16-4c7f-ac0f-cd41f073c75f");

    @Test
    void roundTripsStrictCredentialFreeModelSelections() {
        GuideHistoryCodec codec = new GuideHistoryCodec();

        assertEquals(
                GuideModelSelection.client("openrouter-claude"),
                codec.decodeModelSelection(codec.encodeModelSelection(
                        GuideModelSelection.client("openrouter-claude"))));
        assertEquals(
                GuideModelSelection.server(),
                codec.decodeModelSelection(codec.encodeModelSelection(
                        GuideModelSelection.server())));
        assertThrows(IllegalArgumentException.class, () -> codec.decodeModelSelection(
                "{\"kind\":\"CLIENT\",\"profileId\":\"a\",\"apiKey\":\"secret\"}"));
        assertThrows(IllegalArgumentException.class, () -> codec.decodeModelSelection(
                "{\"kind\":\"SERVER\",\"profileId\":\"a\"}"));
        assertThrows(IllegalArgumentException.class, () -> codec.decodeModelSelection(
                "{\"kind\":\"UNKNOWN\"}"));
    }

    @Test
    void derivesPrivateStablePartitionIdentity() {
        GuideHistoryScope first = GuideHistoryScope.derive(
                ACTOR, GuideHistoryScope.Kind.MULTIPLAYER, " Example.COM:25565 ");
        GuideHistoryScope same = GuideHistoryScope.derive(
                ACTOR, GuideHistoryScope.Kind.MULTIPLAYER, "example.com:25565");
        GuideHistoryScope differentActor = GuideHistoryScope.derive(
                UUID.fromString("bc773dfc-008f-49ec-9313-354734ab9b9b"),
                GuideHistoryScope.Kind.MULTIPLAYER,
                "example.com:25565");
        GuideHistoryScope differentServer = GuideHistoryScope.derive(
                ACTOR, GuideHistoryScope.Kind.MULTIPLAYER, "other.example:25565");

        assertEquals(first, same);
        assertNotEquals(first.scopeId(), differentActor.scopeId());
        assertNotEquals(first.scopeId(), differentServer.scopeId());
        assertEquals(64, first.scopeId().length());
        assertFalse(first.scopeId().contains("example"));
    }

    @Test
    void roundTripsOnlyNormalModeTimelineProjection() {
        GuideHistoryCodec codec = new GuideHistoryCodec();
        JsonObject normalized = new JsonObject();
        normalized.addProperty("secretRawField", "must-not-persist");
        JsonObject invocationArguments = new JsonObject();
        invocationArguments.addProperty(
                "source", "return 'debug-source-must-not-persist';");
        invocationArguments.add(
                "roots", JsonParser.parseString("[\"items\",\"recipes\"]"));
        invocationArguments.add(
                "handles", JsonParser.parseString("[\"r_previous\"]"));
        normalized.add("value", JsonParser.parseString(
                "{\"modules\":[\"openallay:crafting\"]}"));
        GuideSource source = new GuideSource(
                "openallay:get_recipe", GroundedTestFixtures.serverEvidence());
        List<GuideTimelineEntry> timeline = List.of(
                new GuideTimelineEntry.Assistant(0, "I will inspect it.", false, List.of(source)),
                new GuideTimelineEntry.Tool(1, new GuideToolActivity(
                        "call-7",
                        0,
                        "openallay:run_javascript",
                        GuideToolStatus.SUCCEEDED,
                        invocationArguments,
                        normalized,
                        List.of(
                                GuideToolMessage.of(
                                        GuideToolMessage.Key.RESULT_COMPLETED,
                                        "minecraft:iron_block"),
                                GuideToolMessage.of(
                                        GuideToolMessage.Key.RESULT_COMPLETED,
                                        "minecraft:iron_block",
                                        "1")),
                        List.of(source))),
                new GuideTimelineEntry.Assistant(2, "It needs nine ingots.", false, List.of()));

        String encoded = codec.encodeTimeline(timeline);
        List<GuideTimelineEntry> restored = codec.decodeTimeline(encoded);

        assertEquals(3, restored.size());
        assertEquals(List.of(0, 1, 2), restored.stream()
                .map(GuideTimelineEntry::ordinal).toList());
        GuideToolActivity tool = ((GuideTimelineEntry.Tool) restored.get(1)).activity();
        assertNull(tool.normalized());
        assertNull(tool.invocationArguments());
        assertEquals(List.of("r_previous"), tool.invocation().handles());
        assertEquals(List.of("openallay:crafting"), tool.invocation().modules());
        assertFalse(tool.invocation().liveArgumentsAvailable());
        assertEquals(List.of(
                        GuideToolMessage.of(
                                GuideToolMessage.Key.RESULT_COMPLETED,
                                "minecraft:iron_block"),
                        GuideToolMessage.of(
                                GuideToolMessage.Key.RESULT_COMPLETED,
                                "minecraft:iron_block",
                                "1")),
                tool.presentationMessages());
        assertEquals(List.of(source), tool.sources());
        assertFalse(encoded.contains("secretRawField"));
        assertFalse(encoded.contains("must-not-persist"));
        assertFalse(encoded.contains("debug-source-must-not-persist"));
        assertFalse(encoded.contains("\"roots\""));
        assertTrue(encoded.contains("\"invocation\""));
    }

    @Test
    void restoresPerCallIntentWithoutAddingFieldsOrPersistingRawArguments() {
        GuideHistoryCodec codec = new GuideHistoryCodec();
        var timeline = new java.util.ArrayList<GuideTimelineEntry>();
        for (int index = 0; index < 2; index++) {
            JsonObject input = new JsonObject();
            input.addProperty("source", "private source must not persist");
            input.addProperty("title", "Title " + index);
            input.addProperty("description", "Description " + index);
            timeline.add(new GuideTimelineEntry.Tool(index, new GuideToolActivity(
                    "call-" + index, index, "openallay:run_javascript",
                    index == 0 ? GuideToolStatus.SUCCEEDED : GuideToolStatus.FAILED,
                    input, null, dev.openallay.guide.GuideToolInvocationPresentation.messages(
                            "openallay:run_javascript", input), List.of())));
        }
        String encoded = codec.encodeTimeline(timeline);
        List<GuideTimelineEntry> restored = codec.decodeTimeline(encoded);
        for (int index = 0; index < 2; index++) {
            GuideToolActivity original = ((GuideTimelineEntry.Tool) timeline.get(index)).activity();
            GuideToolActivity activity = ((GuideTimelineEntry.Tool) restored.get(index)).activity();
            assertEquals(original.intent(), activity.intent());
            assertEquals(original.status(), activity.status());
            assertEquals(original.invocationId(), activity.invocationId());
            assertNull(activity.invocationArguments());
            assertNull(activity.normalized());
        }
        assertFalse(encoded.contains("private source"));
        JsonObject stored = JsonParser.parseString(encoded).getAsJsonArray().get(0).getAsJsonObject();
        assertEquals(java.util.Set.of("handles", "modules"), stored.getAsJsonObject("invocation").keySet());
        assertFalse(stored.has("title"));
        assertFalse(stored.has("invocationArguments"));
    }

    @Test
    void legacyAndWellTypedUnsupportedIntentArityFallBackButCorruptTypesStayStrict() {
        GuideHistoryCodec codec = new GuideHistoryCodec();
        for (List<String> arguments : List.of(List.<String>of(), List.of("unsupported"), List.of(" ", ""))) {
            GuideTimelineEntry.Tool tool = new GuideTimelineEntry.Tool(0, new GuideToolActivity(
                    "legacy", 0, "openallay:run_javascript", GuideToolStatus.SUCCEEDED, null,
                    List.of(new GuideToolMessage(GuideToolMessage.Key.INVOCATION_RUN_JAVASCRIPT, arguments)), List.of()));
            var restored = (GuideTimelineEntry.Tool) codec.decodeEntry(codec.encodeEntry(tool));
            assertTrue(restored.activity().intent().empty());
        }
        GuideTimelineEntry.Tool tool = new GuideTimelineEntry.Tool(0, new GuideToolActivity(
                "legacy", 0, "openallay:run_javascript", GuideToolStatus.SUCCEEDED, null,
                List.of(GuideToolMessage.of(GuideToolMessage.Key.INVOCATION_RUN_JAVASCRIPT, "Title", "Description")),
                List.of()));
        JsonObject corrupt = JsonParser.parseString(codec.encodeEntry(tool)).getAsJsonObject();
        corrupt.getAsJsonArray("presentationMessages").get(0).getAsJsonObject()
                .getAsJsonArray("arguments").set(0, JsonParser.parseString("7"));
        assertThrows(IllegalArgumentException.class, () -> codec.decodeEntry(corrupt.toString()));
    }

    @Test
    void roundTripsCaptureRangeAndRejectsPreviousSourceShape() {
        GuideHistoryCodec codec = new GuideHistoryCodec();
        var evidence = GroundedTestFixtures.serverEvidence();
        GuideSource source = new GuideSource("openallay:run_javascript", evidence,
                evidence.capturedAt().plusSeconds(30));

        String encoded = codec.encodeSources(List.of(source));
        assertEquals(List.of(source), codec.decodeSources(encoded));
        JsonObject serialized = JsonParser.parseString(encoded).getAsJsonArray()
                .get(0).getAsJsonObject();
        assertEquals(java.util.Set.of("toolId", "evidence", "lastCapturedAt"),
                serialized.keySet());
        JsonArray previousShape = JsonParser.parseString(encoded).getAsJsonArray();
        previousShape.get(0).getAsJsonObject().remove("lastCapturedAt");
        assertThrows(IllegalArgumentException.class,
                () -> codec.decodeSources(previousShape.toString()));
        serialized.addProperty("lastCapturedAt", "not-an-instant");
        assertThrows(java.time.format.DateTimeParseException.class,
                () -> codec.decodeSources("[" + serialized + "]"));
    }

    @Test
    void rejectsUnknownAndMissingDurableFields() {
        GuideHistoryCodec codec = new GuideHistoryCodec();
        String encoded = codec.encodeTimeline(List.of(
                new GuideTimelineEntry.Assistant(0, "answer", false, List.of())));
        JsonArray unknown = JsonParser.parseString(encoded).getAsJsonArray();
        unknown.get(0).getAsJsonObject().addProperty("unknown", true);

        JsonArray missing = JsonParser.parseString(encoded).getAsJsonArray();
        missing.get(0).getAsJsonObject().remove("text");

        JsonArray fractional = JsonParser.parseString(encoded).getAsJsonArray();
        fractional.get(0).getAsJsonObject().addProperty("ordinal", 0.5);

        assertThrows(IllegalArgumentException.class,
                () -> codec.decodeTimeline(unknown.toString()));
        assertThrows(IllegalArgumentException.class,
                () -> codec.decodeTimeline(missing.toString()));
        assertThrows(IllegalArgumentException.class,
                () -> codec.decodeTimeline(fractional.toString()));
        assertThrows(IllegalArgumentException.class,
                () -> codec.decodeTimeline("{}"));
    }

    @Test
    void rejectsUnknownTranslationMessagesInsideDurableToolProjection() {
        GuideHistoryCodec codec = new GuideHistoryCodec();
        String encoded = codec.encodeTimeline(List.of(new GuideTimelineEntry.Tool(
                0,
                new GuideToolActivity(
                        "call-1",
                        0,
                        "openallay:get_recipe",
                        GuideToolStatus.SUCCEEDED,
                        null,
                        List.of(GuideToolMessage.of(GuideToolMessage.Key.RESULT_COMPLETED)),
                        List.of()))));
        JsonArray unknownKey = JsonParser.parseString(encoded).getAsJsonArray();
        unknownKey.get(0).getAsJsonObject()
                .getAsJsonArray("presentationMessages")
                .get(0).getAsJsonObject()
                .addProperty("key", "ARBITRARY_TRANSLATION_KEY");

        assertThrows(IllegalArgumentException.class,
                () -> codec.decodeTimeline(unknownKey.toString()));
    }
}
