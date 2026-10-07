package dev.openallay.client.observation;

import dev.openallay.world.ClientObservationAnchor;
import dev.openallay.world.WorldFocusObservation;
import java.util.ArrayList;
import java.util.List;

/** Compact labels from one actual sample. Native identities remain in the anchor, not in UI task modes. */
public final class ObservationAnchorPresentation {
    private ObservationAnchorPresentation() {}
    @dev.openallay.value.ValueType(Chip.ValueSchemaProvider.class)
public static final class Chip {
    private final String key;
    private final String value;
    public Chip(String key, String value) {
        this.key = key;
        this.value = value;
    }
    public String key() { return key; }
    public String value() { return value; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Chip)) return false;
        Chip that = (Chip) other;
        return java.util.Objects.equals(key, that.key) && java.util.Objects.equals(value, that.value);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(key);
        hash = 31 * hash + java.util.Objects.hashCode(value);
        return hash;
    }
    @Override public String toString() { return "Chip[key=" + key + ", value=" + value + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Chip> schema() {
            return new dev.openallay.value.ValueSchema<>(Chip.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Chip>>asList(new dev.openallay.value.ValueSchema.Component<>(Chip.class, "key", Chip::key), new dev.openallay.value.ValueSchema.Component<>(Chip.class, "value", Chip::value)), arguments -> new Chip((String) arguments[0], (String) arguments[1]));
        }
    }
}

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
