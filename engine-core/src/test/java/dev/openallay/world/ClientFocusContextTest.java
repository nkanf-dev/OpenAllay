package dev.openallay.world;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.game.ObservableGameStateSnapshot;
import dev.openallay.model.CancellationSignal;
import dev.openallay.script.RhinoJavascriptRuntime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Declared detached-host shape proof, not a fake native client. */
final class ClientFocusContextTest {
    @Test void nestedCurrentFocusIsReachableWithItsOwnSourceTimeAndCamera() {
        EvidenceMetadata evidence = new EvidenceMetadata(DataAuthority.CLIENT_VISIBLE, DataCompleteness.PARTIAL,
                Instant.EPOCH, "minecraft:client_focus", "minecraft:client_focus_observation", "26.2", "fabric", Map.of());
        var empty = new WorldFocusObservation.Item("minecraft:air", 0, "", 0, 0, new com.google.gson.JsonObject(), true, "");
        var focus = new WorldFocusObservation(Instant.EPOCH,
                UUID.fromString("00000000-0000-0000-0000-000000000001"), "minecraft:overworld",
                new WorldFocusObservation.Camera(1, 2, 3, 90, 20, 70, "first_person", true, false, null),
                new WorldFocusObservation.Target("miss", new WorldFocusObservation.Position(4, 5, 6), null, null),
                empty, empty, new WorldFocusObservation.Screen("test.NativeMenu", "Chest", 20, 10, false, true, "game_ui"),
                new WorldFocusObservation.Menu("test.Menu", 1, 2, "minecraft:generic_9x3", true, true, true, 27, empty, ""),
                new WorldFocusObservation.Hover(2, 3, false, "none", -1, -1, null, ""), evidence);
        var state = new ObservableGameStateSnapshot.PlayerUiState(null, "test.NativeMenu", "Chest", evidence,
                List.of(), Optional.of(focus));
        var value = new RhinoJavascriptRuntime().execute(
                "return {yaw:mc.ui.focus.camera.yaw, screen:mc.ui.focus.screen.title, kind:mc.ui.focus.target.kind,"
                        + "time:mc.ui.focus.capturedAt, menuDisplayed:mc.ui.focus.menu.displayed};",
                Map.of("ui", state), Map.of(), new CancellationSignal()).value().getAsJsonObject();
        assertEquals(90, value.get("yaw").getAsInt());
        assertEquals("Chest", value.get("screen").getAsString());
        assertEquals("miss", value.get("kind").getAsString());
        assertEquals(Instant.EPOCH.toString(), value.get("time").getAsString());
        assertTrue(value.get("menuDisplayed").getAsBoolean());
    }

    @Test void sourceOnlyServerStateHasNoClientFocusAndMismatchedSourceTimesAreRejected() {
        EvidenceMetadata evidence = new EvidenceMetadata(DataAuthority.SERVER_AUTHORITATIVE, DataCompleteness.PARTIAL,
                Instant.EPOCH, "minecraft:server_player", "minecraft:server_player", "26.2", "fabric", Map.of());
        var state = new ObservableGameStateSnapshot.PlayerUiState(null, "unavailable", "", evidence, List.of());
        assertTrue(state.focus().isEmpty());
        var empty = new WorldFocusObservation.Item("minecraft:air", 0, "", 0, 0, new com.google.gson.JsonObject(), true, "");
        assertThrows(IllegalArgumentException.class, () -> new WorldFocusObservation(Instant.EPOCH.plusSeconds(1),
                UUID.randomUUID(), "minecraft:overworld",
                new WorldFocusObservation.Camera(1, 2, 3, 90, 20, 70, "first_person", true, false, null),
                new WorldFocusObservation.Target("none", null, null, null), empty, empty,
                new WorldFocusObservation.Screen("", "", 20, 10, false, false, "gameplay"),
                new WorldFocusObservation.Menu("test.Menu", 0, 0, "", false, false, true, 0, empty, "untyped_menu"),
                new WorldFocusObservation.Hover(0, 0, false, "none", -1, -1, null, ""), evidence));
    }
}
