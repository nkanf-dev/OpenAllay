package dev.openallay.world;

import dev.openallay.context.EvidenceMetadata;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/** A detached owner-thread sample of the connected client's current focus, not an input anchor. */
public record WorldFocusObservation(
        Instant capturedAt,
        UUID actorId,
        String dimension,
        Camera camera,
        Target target,
        Item mainHand,
        Item offHand,
        Screen screen,
        Menu menu,
        Hover hover,
        EvidenceMetadata evidence) {
    public WorldFocusObservation {
        Objects.requireNonNull(capturedAt, "capturedAt");
        Objects.requireNonNull(actorId, "actorId");
        dimension = nonBlank(dimension, "dimension");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(mainHand, "mainHand");
        Objects.requireNonNull(offHand, "offHand");
        Objects.requireNonNull(screen, "screen");
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(hover, "hover");
        Objects.requireNonNull(evidence, "evidence");
        if (!capturedAt.equals(evidence.capturedAt())) {
            throw new IllegalArgumentException("Focus source times differ");
        }
    }

    /** Numeric camera state; image captures instead copy the camera state of their source frame. */
    public record Camera(
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            float fov,
            String mode,
            boolean initialized,
            boolean detached,
            UUID entityUuid) {
        public Camera {
            finite(x, "camera.x");
            finite(y, "camera.y");
            finite(z, "camera.z");
            finite(yaw, "camera.yaw");
            finite(pitch, "camera.pitch");
            finite(fov, "camera.fov");
            mode = nonBlank(mode, "mode");
        }
    }

    public record Position(double x, double y, double z) {
        public Position {
            finite(x, "position.x");
            finite(y, "position.y");
            finite(z, "position.z");
        }
    }

    /** Hit position is present for MISS too. A missing native HitResult has kind "none". */
    public record Target(String kind, Position hit, Block block, Entity entity) {
        public Target {
            kind = choice(kind, "kind", "none", "miss", "block", "entity");
            if (kind.equals("none") != (hit == null)
                    || kind.equals("block") != (block != null)
                    || kind.equals("entity") != (entity != null)) {
                throw new IllegalArgumentException("Target payload must match its kind");
            }
        }
    }

    /** worldBorderHit is null when the native hit object cannot attest border provenance. */
    public record Block(
            String id,
            WorldPosition position,
            String face,
            boolean inside,
            Boolean worldBorderHit,
            Map<String, String> properties,
            String fluid) {
        public Block {
            id = nonBlank(id, "block.id");
            Objects.requireNonNull(position, "position");
            face = nonBlank(face, "face");
            TreeMap<String, String> copy = new TreeMap<>();
            Objects.requireNonNull(properties, "properties").forEach((key, value) ->
                    copy.put(nonBlank(key, "property name"), Objects.requireNonNull(value, "property value")));
            properties = Collections.unmodifiableMap(copy);
            fluid = Objects.requireNonNull(fluid, "fluid");
        }
    }

    /** Runtime id and persistent UUID are native identities, not world.entities observation IDs. */
    public record Entity(
            UUID uuid,
            int id,
            String type,
            String name,
            Position position,
            WorldPosition blockPosition,
            boolean alive) {
        public Entity {
            Objects.requireNonNull(uuid, "uuid");
            type = nonBlank(type, "entity.type");
            name = Objects.requireNonNull(name, "name");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(blockPosition, "blockPosition");
        }
    }

    /**
     * Effective persistent components encoded by the native codec. Transient components are not
     * part of that codec. The JSON-domain component tree is copied at construction and access,
     * and contains no native stacks, component instances, or NBT objects.
     */
    public record Item(
            String id,
            int count,
            String name,
            int damage,
            int maxDamage,
            JsonObject components,
            boolean componentsAvailable,
            String diagnostic) {
        public Item {
            id = nonBlank(id, "item.id");
            name = Objects.requireNonNull(name, "name");
            if (count < 0 || damage < 0 || maxDamage < 0) {
                throw new IllegalArgumentException("Item counts and damage must not be negative");
            }
            components = dev.openallay.json.JsonTrees.copy(Objects.requireNonNull(components, "components"));
            diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
            if (componentsAvailable && !diagnostic.isEmpty()) {
                throw new IllegalArgumentException("Available item components must not report a failure");
            }
            if (!componentsAvailable && diagnostic.isBlank()) {
                throw new IllegalArgumentException("Unavailable item components require a diagnostic");
            }
        }

        @Override public JsonObject components() { return dev.openallay.json.JsonTrees.copy(components); }
    }

    /** Empty identity means no current Screen; native overlays are identified in evidence details. */
    public record Screen(
            String className,
            String title,
            int width,
            int height,
            boolean pauseScreen,
            boolean inGameUi,
            String role) {
        public Screen {
            className = Objects.requireNonNull(className, "className");
            title = Objects.requireNonNull(title, "title");
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("Screen dimensions must not be negative");
            }
            role = choice(role, "role", "gameplay", "game_ui", "openallay", "overlay");
        }
    }

    /**
     * Displayed native screen menu, or the player's synchronized menu hidden by another Screen.
     * Slot contents are deliberately not scanned by lightweight focus capture.
     */
    public record Menu(
            String className,
            int containerId,
            int stateId,
            String type,
            boolean typeAvailable,
            boolean displayed,
            boolean synchronizedWithPlayer,
            int slotCount,
            Item carried,
            String diagnostic) {
        public Menu {
            className = nonBlank(className, "className");
            type = Objects.requireNonNull(type, "type");
            Objects.requireNonNull(carried, "carried");
            diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
            if (slotCount < 0 || typeAvailable != !type.isEmpty()) {
                throw new IllegalArgumentException("Invalid menu slot count or type availability");
            }
        }
    }

    /** Current scaled cursor and native active slot, never AbstractContainerScreen's prior-frame field. */
    public record Hover(
            double x,
            double y,
            boolean mouseGrabbed,
            String kind,
            int menuSlot,
            int containerSlot,
            Item item,
            String diagnostic) {
        public Hover {
            finite(x, "hover.x");
            finite(y, "hover.y");
            kind = choice(kind, "kind", "none", "slot", "unavailable");
            diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
            if (kind.equals("slot")) {
                if (menuSlot < 0 || containerSlot < 0 || item == null) {
                    throw new IllegalArgumentException("Slot hover requires native slot identities and an item");
                }
            } else if (menuSlot != -1 || containerSlot != -1 || item != null) {
                throw new IllegalArgumentException("Non-slot hover must not contain a slot payload");
            }
        }
    }

    private static String nonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String choice(String value, String field, String... choices) {
        for (String candidate : choices) {
            if (candidate.equals(value)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unsupported " + field + ": " + value);
    }

    private static void finite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }
}
