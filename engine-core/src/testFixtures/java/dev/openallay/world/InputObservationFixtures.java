package dev.openallay.world;

import com.google.gson.JsonParser;
import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.model.image.ImageReference;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Detached typed input sources only; no Minecraft runtime is created. */
public final class InputObservationFixtures {
    public static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000041");
    public static final UUID ASSOCIATION = UUID.fromString("00000000-0000-0000-0000-000000000042");
    public static final Instant SOURCE = Instant.parse("2026-10-04T11:27:00.125Z");
    public static final Instant FRAME_SOURCE = SOURCE.minusMillis(17);

    private InputObservationFixtures() {}

    public static ClientObservationAnchor anchor(ImageReference image) {
        WorldFocusObservation focus = focus(ACTOR);
        return new ClientObservationAnchor(ASSOCIATION, SOURCE, focus, image == null ? Optional.empty()
                : Optional.of(new WorldViewCapture("source-frame-73", FRAME_SOURCE, ACTOR,
                        "minecraft:overworld", WorldViewRequest.Target.GAME_UI, true, true, 1920, 1080, 2,
                        focus.camera(), focus.screen(), image, evidence(FRAME_SOURCE))));
    }

    public static WorldFocusObservation focus(UUID actor) {
        var empty = new WorldFocusObservation.Item("minecraft:air", 0, "", 0, 0,
                new com.google.gson.JsonObject(), true, "");
        var item = new WorldFocusObservation.Item("minecraft:diamond_sword", 1, "Named sword", 7, 1561,
                JsonParser.parseString("{\"minecraft:custom_data\":{\"precise\":9007199254740993.125,\"flags\":[true,null,\"kept\"]}}")
                        .getAsJsonObject(), true, "");
        return new WorldFocusObservation(SOURCE, actor, "minecraft:overworld",
                new WorldFocusObservation.Camera(12.125, 65.5, -3.75, 90.125f, 10.5f, 70.1f, "first_person", true, false, actor),
                new WorldFocusObservation.Target("block", new WorldFocusObservation.Position(12.125, 65.5, -3.75),
                        new WorldFocusObservation.Block("minecraft:oak_stairs", new WorldPosition(12, 65, -4),
                                "north", true, false, Map.of("facing", "east"), "minecraft:empty"), null),
                item, empty, new WorldFocusObservation.Screen("NativeContainerScreen", "Chest", 960, 540,
                        false, true, "game_ui"),
                new WorldFocusObservation.Menu("ChestMenu", 3, 7, "minecraft:generic_9x3", true,
                        true, true, 63, empty, ""),
                new WorldFocusObservation.Hover(145.25, 99.5, false, "slot", 4, 4, item, ""), evidence(SOURCE));
    }

    private static EvidenceMetadata evidence(Instant source) {
        return new EvidenceMetadata(DataAuthority.CLIENT_VISIBLE, DataCompleteness.COMPLETE, source,
                "openallay:client_focus", "openallay:client_sample", "1.21.11", "fabric",
                Map.of("openallay:source", "native"));
    }
}
