package dev.openallay.client.observation;

import com.google.gson.JsonObject;
import dev.openallay.client.gui.GuideClientUiState;
import dev.openallay.client.gui.clipboard.ImageClipboard;
import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.model.image.ImageReference;
import dev.openallay.tool.ToolResult;
import dev.openallay.world.ClientObservationAnchor;
import dev.openallay.world.WorldFocusObservation;
import dev.openallay.world.WorldPosition;
import dev.openallay.world.WorldViewCapture;
import dev.openallay.world.WorldViewRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class ObservationUiFixture {
    static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final ImageReference FRAME = new ImageReference("a".repeat(64), "image/png", 12, 8, 100);
    static final ImageReference PASTE = new ImageReference("b".repeat(64), "image/png", 6, 4, 80);
    final List<List<ImageReference>> retained = new ArrayList<>();
    int releases;
    int clipboardReads;
    final GuideClientUiState state = new GuideClientUiState("one", () -> {
        clipboardReads++;
        return ImageClipboard.Read.empty();
    }, Runnable::run, Runnable::run,
            bytes -> CompletableFuture.completedFuture(new ToolResult.Success<>(PASTE)), ignored -> {},
            (owner, refs) -> { retained.add(List.copyOf(refs)); return CompletableFuture.completedFuture(new ToolResult.Success<>(true)); },
            owner -> releases++);

    static ClientObservationAnchor anchor(int second, boolean image, boolean menu) {
        Instant time = Instant.parse("2026-10-04T00:00:00Z").plusSeconds(second);
        var evidence = new EvidenceMetadata(DataAuthority.CLIENT_VISIBLE, DataCompleteness.PARTIAL, time,
                "minecraft:client_focus", "test:detached_focus", "test-game", "test-platform", Map.of());
        var camera = new WorldFocusObservation.Camera(12, 65, -4, 90, 10, 70, "first_person", true, false, ACTOR);
        var empty = new WorldFocusObservation.Item("minecraft:air", 0, "", 0, 0, new JsonObject(), true, "");
        var held = new WorldFocusObservation.Item("minecraft:stick", 2, "Stick", 0, 0, new JsonObject(), true, "");
        var block = new WorldFocusObservation.Block("minecraft:oak_log", new WorldPosition(12, 65, -4),
                "north", false, false, Map.of("axis", "y"), "");
        var screen = new WorldFocusObservation.Screen(menu ? "native.Container" : "", menu ? "Chest" : "", 300, 200,
                false, menu, menu ? "game_ui" : "gameplay");
        var focus = new WorldFocusObservation(time, ACTOR, "minecraft:overworld", camera,
                new WorldFocusObservation.Target("block", new WorldFocusObservation.Position(12, 65, -4), block, null),
                held, empty, screen,
                new WorldFocusObservation.Menu("native.Menu", 1, 4, "minecraft:generic_9x3", true, menu, true, 63, empty, ""),
                menu ? new WorldFocusObservation.Hover(15, 20, false, "slot", 8, 8, held, "")
                        : new WorldFocusObservation.Hover(15, 20, true, "none", -1, -1, null, ""), evidence);
        Optional<WorldViewCapture> capture = image ? Optional.of(new WorldViewCapture("capture-" + second, time,
                ACTOR, focus.dimension(), menu ? WorldViewRequest.Target.GAME_UI : WorldViewRequest.Target.WORLD,
                menu, menu, 12, 8, 1, camera, screen, FRAME, evidence)) : Optional.empty();
        return new ClientObservationAnchor(UUID.randomUUID(), time, focus, capture);
    }
}
