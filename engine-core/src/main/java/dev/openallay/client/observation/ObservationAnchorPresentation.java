package dev.openallay.client.observation;

import dev.openallay.world.ClientObservationAnchor;
import dev.openallay.world.WorldFocusObservation;
import java.util.ArrayList;
import java.util.List;

/** Compact labels from one actual sample. Native identities remain in the anchor, not in UI task modes. */
public final class ObservationAnchorPresentation {
    private ObservationAnchorPresentation() {}
    public record Chip(String key, String value) {}

    public static List<Chip> chips(ClientObservationAnchor anchor) {
        List<Chip> result = new ArrayList<>();
        WorldFocusObservation focus = anchor.focus();
        if (focus.hover().kind().equals("slot")) {
            result.add(new Chip("screen.openallay.observation.slot", focus.hover().menuSlot()
                    + " · " + itemName(focus.hover().item())));
        }
        if (focus.target().block() != null) {
            dev.openallay.world.WorldFocusObservation.Block block = focus.target().block();
            result.add(new Chip("screen.openallay.observation.crosshair", block.id()
                    + " · " + block.position().x() + "," + block.position().y() + "," + block.position().z()));
        } else if (focus.target().entity() != null) {
            dev.openallay.world.WorldFocusObservation.Entity entity = focus.target().entity();
            result.add(new Chip("screen.openallay.observation.crosshair",
                    entity.name().isBlank() ? entity.type() : entity.name()));
        }
        if (focus.mainHand().count() > 0) result.add(new Chip("screen.openallay.observation.held", itemName(focus.mainHand())));
        if (result.isEmpty()) result.add(new Chip("screen.openallay.observation.focus",
                focus.screen().title().isBlank() ? focus.dimension() : focus.screen().title()));
        return List.copyOf(result);
    }

    private static String itemName(WorldFocusObservation.Item item) {
        String name = item.name().isBlank() ? item.id() : item.name();
        return item.count() > 1 ? name + " ×" + item.count() : name;
    }
}
