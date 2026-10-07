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
@dev.openallay.value.ValueType(WorldFocusObservation.ValueSchemaProvider.class)
public final class WorldFocusObservation {
    private final Instant capturedAt;
    private final UUID actorId;
    private final String dimension;
    private final Camera camera;
    private final Target target;
    private final Item mainHand;
    private final Item offHand;
    private final Screen screen;
    private final Menu menu;
    private final Hover hover;
    private final EvidenceMetadata evidence;
    public WorldFocusObservation(Instant capturedAt, UUID actorId, String dimension, Camera camera, Target target, Item mainHand, Item offHand, Screen screen, Menu menu, Hover hover, EvidenceMetadata evidence) {

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

        this.capturedAt = capturedAt;
        this.actorId = actorId;
        this.dimension = dimension;
        this.camera = camera;
        this.target = target;
        this.mainHand = mainHand;
        this.offHand = offHand;
        this.screen = screen;
        this.menu = menu;
        this.hover = hover;
        this.evidence = evidence;
    }
    public Instant capturedAt() { return capturedAt; }
    public UUID actorId() { return actorId; }
    public String dimension() { return dimension; }
    public Camera camera() { return camera; }
    public Target target() { return target; }
    public Item mainHand() { return mainHand; }
    public Item offHand() { return offHand; }
    public Screen screen() { return screen; }
    public Menu menu() { return menu; }
    public Hover hover() { return hover; }
    public EvidenceMetadata evidence() { return evidence; }
@dev.openallay.value.ValueType(Camera.ValueSchemaProvider.class)
public static final class Camera {
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final float fov;
    private final String mode;
    private final boolean initialized;
    private final boolean detached;
    private final UUID entityUuid;
    public Camera(double x, double y, double z, float yaw, float pitch, float fov, String mode, boolean initialized, boolean detached, UUID entityUuid) {

            finite(x, "camera.x");
            finite(y, "camera.y");
            finite(z, "camera.z");
            finite(yaw, "camera.yaw");
            finite(pitch, "camera.pitch");
            finite(fov, "camera.fov");
            mode = nonBlank(mode, "mode");

        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.fov = fov;
        this.mode = mode;
        this.initialized = initialized;
        this.detached = detached;
        this.entityUuid = entityUuid;
    }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public float fov() { return fov; }
    public String mode() { return mode; }
    public boolean initialized() { return initialized; }
    public boolean detached() { return detached; }
    public UUID entityUuid() { return entityUuid; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Camera)) return false;
        Camera that = (Camera) other;
        return Double.compare(x, that.x) == 0 && Double.compare(y, that.y) == 0 && Double.compare(z, that.z) == 0 && Float.compare(yaw, that.yaw) == 0 && Float.compare(pitch, that.pitch) == 0 && Float.compare(fov, that.fov) == 0 && java.util.Objects.equals(mode, that.mode) && initialized == that.initialized && detached == that.detached && java.util.Objects.equals(entityUuid, that.entityUuid);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Double.hashCode(x);
        hash = 31 * hash + Double.hashCode(y);
        hash = 31 * hash + Double.hashCode(z);
        hash = 31 * hash + Float.hashCode(yaw);
        hash = 31 * hash + Float.hashCode(pitch);
        hash = 31 * hash + Float.hashCode(fov);
        hash = 31 * hash + java.util.Objects.hashCode(mode);
        hash = 31 * hash + Boolean.hashCode(initialized);
        hash = 31 * hash + Boolean.hashCode(detached);
        hash = 31 * hash + java.util.Objects.hashCode(entityUuid);
        return hash;
    }
    @Override public String toString() { return "Camera[x=" + x + ", y=" + y + ", z=" + z + ", yaw=" + yaw + ", pitch=" + pitch + ", fov=" + fov + ", mode=" + mode + ", initialized=" + initialized + ", detached=" + detached + ", entityUuid=" + entityUuid + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Camera> schema() {
            return new dev.openallay.value.ValueSchema<>(Camera.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Camera>>asList(new dev.openallay.value.ValueSchema.Component<>(Camera.class, "x", Camera::x), new dev.openallay.value.ValueSchema.Component<>(Camera.class, "y", Camera::y), new dev.openallay.value.ValueSchema.Component<>(Camera.class, "z", Camera::z), new dev.openallay.value.ValueSchema.Component<>(Camera.class, "yaw", Camera::yaw), new dev.openallay.value.ValueSchema.Component<>(Camera.class, "pitch", Camera::pitch), new dev.openallay.value.ValueSchema.Component<>(Camera.class, "fov", Camera::fov), new dev.openallay.value.ValueSchema.Component<>(Camera.class, "mode", Camera::mode), new dev.openallay.value.ValueSchema.Component<>(Camera.class, "initialized", Camera::initialized), new dev.openallay.value.ValueSchema.Component<>(Camera.class, "detached", Camera::detached), new dev.openallay.value.ValueSchema.Component<>(Camera.class, "entityUuid", Camera::entityUuid)), arguments -> new Camera((Double) arguments[0], (Double) arguments[1], (Double) arguments[2], (Float) arguments[3], (Float) arguments[4], (Float) arguments[5], (String) arguments[6], (Boolean) arguments[7], (Boolean) arguments[8], (UUID) arguments[9]));
        }
    }
}
@dev.openallay.value.ValueType(Position.ValueSchemaProvider.class)
public static final class Position {
    private final double x;
    private final double y;
    private final double z;
    public Position(double x, double y, double z) {

            finite(x, "position.x");
            finite(y, "position.y");
            finite(z, "position.z");

        this.x = x;
        this.y = y;
        this.z = z;
    }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Position)) return false;
        Position that = (Position) other;
        return Double.compare(x, that.x) == 0 && Double.compare(y, that.y) == 0 && Double.compare(z, that.z) == 0;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Double.hashCode(x);
        hash = 31 * hash + Double.hashCode(y);
        hash = 31 * hash + Double.hashCode(z);
        return hash;
    }
    @Override public String toString() { return "Position[x=" + x + ", y=" + y + ", z=" + z + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Position> schema() {
            return new dev.openallay.value.ValueSchema<>(Position.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Position>>asList(new dev.openallay.value.ValueSchema.Component<>(Position.class, "x", Position::x), new dev.openallay.value.ValueSchema.Component<>(Position.class, "y", Position::y), new dev.openallay.value.ValueSchema.Component<>(Position.class, "z", Position::z)), arguments -> new Position((Double) arguments[0], (Double) arguments[1], (Double) arguments[2]));
        }
    }
}
@dev.openallay.value.ValueType(Target.ValueSchemaProvider.class)
public static final class Target {
    private final String kind;
    private final Position hit;
    private final Block block;
    private final Entity entity;
    public Target(String kind, Position hit, Block block, Entity entity) {

            kind = choice(kind, "kind", "none", "miss", "block", "entity");
            if (kind.equals("none") != (hit == null)
                    || kind.equals("block") != (block != null)
                    || kind.equals("entity") != (entity != null)) {
                throw new IllegalArgumentException("Target payload must match its kind");
            }

        this.kind = kind;
        this.hit = hit;
        this.block = block;
        this.entity = entity;
    }
    public String kind() { return kind; }
    public Position hit() { return hit; }
    public Block block() { return block; }
    public Entity entity() { return entity; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Target)) return false;
        Target that = (Target) other;
        return java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(hit, that.hit) && java.util.Objects.equals(block, that.block) && java.util.Objects.equals(entity, that.entity);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(hit);
        hash = 31 * hash + java.util.Objects.hashCode(block);
        hash = 31 * hash + java.util.Objects.hashCode(entity);
        return hash;
    }
    @Override public String toString() { return "Target[kind=" + kind + ", hit=" + hit + ", block=" + block + ", entity=" + entity + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Target> schema() {
            return new dev.openallay.value.ValueSchema<>(Target.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Target>>asList(new dev.openallay.value.ValueSchema.Component<>(Target.class, "kind", Target::kind), new dev.openallay.value.ValueSchema.Component<>(Target.class, "hit", Target::hit), new dev.openallay.value.ValueSchema.Component<>(Target.class, "block", Target::block), new dev.openallay.value.ValueSchema.Component<>(Target.class, "entity", Target::entity)), arguments -> new Target((String) arguments[0], (Position) arguments[1], (Block) arguments[2], (Entity) arguments[3]));
        }
    }
}
@dev.openallay.value.ValueType(Block.ValueSchemaProvider.class)
public static final class Block {
    private final String id;
    private final WorldPosition position;
    private final String face;
    private final boolean inside;
    private final Boolean worldBorderHit;
    private final Map<String, String> properties;
    private final String fluid;
    public Block(String id, WorldPosition position, String face, boolean inside, Boolean worldBorderHit, Map<String, String> properties, String fluid) {

            id = nonBlank(id, "block.id");
            Objects.requireNonNull(position, "position");
            face = nonBlank(face, "face");
            TreeMap<String, String> copy = new TreeMap<>();
            Objects.requireNonNull(properties, "properties").forEach((key, value) ->
                    copy.put(nonBlank(key, "property name"), Objects.requireNonNull(value, "property value")));
            properties = Collections.unmodifiableMap(copy);
            fluid = Objects.requireNonNull(fluid, "fluid");

        this.id = id;
        this.position = position;
        this.face = face;
        this.inside = inside;
        this.worldBorderHit = worldBorderHit;
        this.properties = properties;
        this.fluid = fluid;
    }
    public String id() { return id; }
    public WorldPosition position() { return position; }
    public String face() { return face; }
    public boolean inside() { return inside; }
    public Boolean worldBorderHit() { return worldBorderHit; }
    public Map<String, String> properties() { return properties; }
    public String fluid() { return fluid; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Block)) return false;
        Block that = (Block) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(position, that.position) && java.util.Objects.equals(face, that.face) && inside == that.inside && java.util.Objects.equals(worldBorderHit, that.worldBorderHit) && java.util.Objects.equals(properties, that.properties) && java.util.Objects.equals(fluid, that.fluid);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(position);
        hash = 31 * hash + java.util.Objects.hashCode(face);
        hash = 31 * hash + Boolean.hashCode(inside);
        hash = 31 * hash + java.util.Objects.hashCode(worldBorderHit);
        hash = 31 * hash + java.util.Objects.hashCode(properties);
        hash = 31 * hash + java.util.Objects.hashCode(fluid);
        return hash;
    }
    @Override public String toString() { return "Block[id=" + id + ", position=" + position + ", face=" + face + ", inside=" + inside + ", worldBorderHit=" + worldBorderHit + ", properties=" + properties + ", fluid=" + fluid + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Block> schema() {
            return new dev.openallay.value.ValueSchema<>(Block.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Block>>asList(new dev.openallay.value.ValueSchema.Component<>(Block.class, "id", Block::id), new dev.openallay.value.ValueSchema.Component<>(Block.class, "position", Block::position), new dev.openallay.value.ValueSchema.Component<>(Block.class, "face", Block::face), new dev.openallay.value.ValueSchema.Component<>(Block.class, "inside", Block::inside), new dev.openallay.value.ValueSchema.Component<>(Block.class, "worldBorderHit", Block::worldBorderHit), new dev.openallay.value.ValueSchema.Component<>(Block.class, "properties", Block::properties), new dev.openallay.value.ValueSchema.Component<>(Block.class, "fluid", Block::fluid)), arguments -> new Block((String) arguments[0], (WorldPosition) arguments[1], (String) arguments[2], (Boolean) arguments[3], (Boolean) arguments[4], (Map) arguments[5], (String) arguments[6]));
        }
    }
}
@dev.openallay.value.ValueType(Entity.ValueSchemaProvider.class)
public static final class Entity {
    private final UUID uuid;
    private final int id;
    private final String type;
    private final String name;
    private final Position position;
    private final WorldPosition blockPosition;
    private final boolean alive;
    public Entity(UUID uuid, int id, String type, String name, Position position, WorldPosition blockPosition, boolean alive) {

            Objects.requireNonNull(uuid, "uuid");
            type = nonBlank(type, "entity.type");
            name = Objects.requireNonNull(name, "name");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(blockPosition, "blockPosition");

        this.uuid = uuid;
        this.id = id;
        this.type = type;
        this.name = name;
        this.position = position;
        this.blockPosition = blockPosition;
        this.alive = alive;
    }
    public UUID uuid() { return uuid; }
    public int id() { return id; }
    public String type() { return type; }
    public String name() { return name; }
    public Position position() { return position; }
    public WorldPosition blockPosition() { return blockPosition; }
    public boolean alive() { return alive; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Entity)) return false;
        Entity that = (Entity) other;
        return java.util.Objects.equals(uuid, that.uuid) && id == that.id && java.util.Objects.equals(type, that.type) && java.util.Objects.equals(name, that.name) && java.util.Objects.equals(position, that.position) && java.util.Objects.equals(blockPosition, that.blockPosition) && alive == that.alive;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(uuid);
        hash = 31 * hash + Integer.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(type);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(position);
        hash = 31 * hash + java.util.Objects.hashCode(blockPosition);
        hash = 31 * hash + Boolean.hashCode(alive);
        return hash;
    }
    @Override public String toString() { return "Entity[uuid=" + uuid + ", id=" + id + ", type=" + type + ", name=" + name + ", position=" + position + ", blockPosition=" + blockPosition + ", alive=" + alive + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Entity> schema() {
            return new dev.openallay.value.ValueSchema<>(Entity.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Entity>>asList(new dev.openallay.value.ValueSchema.Component<>(Entity.class, "uuid", Entity::uuid), new dev.openallay.value.ValueSchema.Component<>(Entity.class, "id", Entity::id), new dev.openallay.value.ValueSchema.Component<>(Entity.class, "type", Entity::type), new dev.openallay.value.ValueSchema.Component<>(Entity.class, "name", Entity::name), new dev.openallay.value.ValueSchema.Component<>(Entity.class, "position", Entity::position), new dev.openallay.value.ValueSchema.Component<>(Entity.class, "blockPosition", Entity::blockPosition), new dev.openallay.value.ValueSchema.Component<>(Entity.class, "alive", Entity::alive)), arguments -> new Entity((UUID) arguments[0], (Integer) arguments[1], (String) arguments[2], (String) arguments[3], (Position) arguments[4], (WorldPosition) arguments[5], (Boolean) arguments[6]));
        }
    }
}
@dev.openallay.value.ValueType(Item.ValueSchemaProvider.class)
public static final class Item {
    private final String id;
    private final int count;
    private final String name;
    private final int damage;
    private final int maxDamage;
    private final JsonObject components;
    private final boolean componentsAvailable;
    private final String diagnostic;
    public Item(String id, int count, String name, int damage, int maxDamage, JsonObject components, boolean componentsAvailable, String diagnostic) {

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
            if (!componentsAvailable && dev.openallay.util.Java8Strings.isBlank(diagnostic)) {
                throw new IllegalArgumentException("Unavailable item components require a diagnostic");
            }

        this.id = id;
        this.count = count;
        this.name = name;
        this.damage = damage;
        this.maxDamage = maxDamage;
        this.components = components;
        this.componentsAvailable = componentsAvailable;
        this.diagnostic = diagnostic;
    }
    public String id() { return id; }
    public int count() { return count; }
    public String name() { return name; }
    public int damage() { return damage; }
    public int maxDamage() { return maxDamage; }
    public boolean componentsAvailable() { return componentsAvailable; }
    public String diagnostic() { return diagnostic; }
 public JsonObject components() { return dev.openallay.json.JsonTrees.copy(components); }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Item)) return false;
        Item that = (Item) other;
        return java.util.Objects.equals(id, that.id) && count == that.count && java.util.Objects.equals(name, that.name) && damage == that.damage && maxDamage == that.maxDamage && java.util.Objects.equals(components, that.components) && componentsAvailable == that.componentsAvailable && java.util.Objects.equals(diagnostic, that.diagnostic);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + Integer.hashCode(count);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + Integer.hashCode(damage);
        hash = 31 * hash + Integer.hashCode(maxDamage);
        hash = 31 * hash + java.util.Objects.hashCode(components);
        hash = 31 * hash + Boolean.hashCode(componentsAvailable);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostic);
        return hash;
    }
    @Override public String toString() { return "Item[id=" + id + ", count=" + count + ", name=" + name + ", damage=" + damage + ", maxDamage=" + maxDamage + ", components=" + components + ", componentsAvailable=" + componentsAvailable + ", diagnostic=" + diagnostic + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Item> schema() {
            return new dev.openallay.value.ValueSchema<>(Item.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Item>>asList(new dev.openallay.value.ValueSchema.Component<>(Item.class, "id", Item::id), new dev.openallay.value.ValueSchema.Component<>(Item.class, "count", Item::count), new dev.openallay.value.ValueSchema.Component<>(Item.class, "name", Item::name), new dev.openallay.value.ValueSchema.Component<>(Item.class, "damage", Item::damage), new dev.openallay.value.ValueSchema.Component<>(Item.class, "maxDamage", Item::maxDamage), new dev.openallay.value.ValueSchema.Component<>(Item.class, "components", Item::components), new dev.openallay.value.ValueSchema.Component<>(Item.class, "componentsAvailable", Item::componentsAvailable), new dev.openallay.value.ValueSchema.Component<>(Item.class, "diagnostic", Item::diagnostic)), arguments -> new Item((String) arguments[0], (Integer) arguments[1], (String) arguments[2], (Integer) arguments[3], (Integer) arguments[4], (JsonObject) arguments[5], (Boolean) arguments[6], (String) arguments[7]));
        }
    }
}
@dev.openallay.value.ValueType(Screen.ValueSchemaProvider.class)
public static final class Screen {
    private final String className;
    private final String title;
    private final int width;
    private final int height;
    private final boolean pauseScreen;
    private final boolean inGameUi;
    private final String role;
    public Screen(String className, String title, int width, int height, boolean pauseScreen, boolean inGameUi, String role) {

            className = Objects.requireNonNull(className, "className");
            title = Objects.requireNonNull(title, "title");
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("Screen dimensions must not be negative");
            }
            role = choice(role, "role", "gameplay", "game_ui", "openallay", "overlay");

        this.className = className;
        this.title = title;
        this.width = width;
        this.height = height;
        this.pauseScreen = pauseScreen;
        this.inGameUi = inGameUi;
        this.role = role;
    }
    public String className() { return className; }
    public String title() { return title; }
    public int width() { return width; }
    public int height() { return height; }
    public boolean pauseScreen() { return pauseScreen; }
    public boolean inGameUi() { return inGameUi; }
    public String role() { return role; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Screen)) return false;
        Screen that = (Screen) other;
        return java.util.Objects.equals(className, that.className) && java.util.Objects.equals(title, that.title) && width == that.width && height == that.height && pauseScreen == that.pauseScreen && inGameUi == that.inGameUi && java.util.Objects.equals(role, that.role);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(className);
        hash = 31 * hash + java.util.Objects.hashCode(title);
        hash = 31 * hash + Integer.hashCode(width);
        hash = 31 * hash + Integer.hashCode(height);
        hash = 31 * hash + Boolean.hashCode(pauseScreen);
        hash = 31 * hash + Boolean.hashCode(inGameUi);
        hash = 31 * hash + java.util.Objects.hashCode(role);
        return hash;
    }
    @Override public String toString() { return "Screen[className=" + className + ", title=" + title + ", width=" + width + ", height=" + height + ", pauseScreen=" + pauseScreen + ", inGameUi=" + inGameUi + ", role=" + role + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Screen> schema() {
            return new dev.openallay.value.ValueSchema<>(Screen.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Screen>>asList(new dev.openallay.value.ValueSchema.Component<>(Screen.class, "className", Screen::className), new dev.openallay.value.ValueSchema.Component<>(Screen.class, "title", Screen::title), new dev.openallay.value.ValueSchema.Component<>(Screen.class, "width", Screen::width), new dev.openallay.value.ValueSchema.Component<>(Screen.class, "height", Screen::height), new dev.openallay.value.ValueSchema.Component<>(Screen.class, "pauseScreen", Screen::pauseScreen), new dev.openallay.value.ValueSchema.Component<>(Screen.class, "inGameUi", Screen::inGameUi), new dev.openallay.value.ValueSchema.Component<>(Screen.class, "role", Screen::role)), arguments -> new Screen((String) arguments[0], (String) arguments[1], (Integer) arguments[2], (Integer) arguments[3], (Boolean) arguments[4], (Boolean) arguments[5], (String) arguments[6]));
        }
    }
}
@dev.openallay.value.ValueType(Menu.ValueSchemaProvider.class)
public static final class Menu {
    private final String className;
    private final int containerId;
    private final int stateId;
    private final String type;
    private final boolean typeAvailable;
    private final boolean displayed;
    private final boolean synchronizedWithPlayer;
    private final int slotCount;
    private final Item carried;
    private final String diagnostic;
    public Menu(String className, int containerId, int stateId, String type, boolean typeAvailable, boolean displayed, boolean synchronizedWithPlayer, int slotCount, Item carried, String diagnostic) {

            className = nonBlank(className, "className");
            type = Objects.requireNonNull(type, "type");
            Objects.requireNonNull(carried, "carried");
            diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
            if (slotCount < 0 || typeAvailable != !type.isEmpty()) {
                throw new IllegalArgumentException("Invalid menu slot count or type availability");
            }

        this.className = className;
        this.containerId = containerId;
        this.stateId = stateId;
        this.type = type;
        this.typeAvailable = typeAvailable;
        this.displayed = displayed;
        this.synchronizedWithPlayer = synchronizedWithPlayer;
        this.slotCount = slotCount;
        this.carried = carried;
        this.diagnostic = diagnostic;
    }
    public String className() { return className; }
    public int containerId() { return containerId; }
    public int stateId() { return stateId; }
    public String type() { return type; }
    public boolean typeAvailable() { return typeAvailable; }
    public boolean displayed() { return displayed; }
    public boolean synchronizedWithPlayer() { return synchronizedWithPlayer; }
    public int slotCount() { return slotCount; }
    public Item carried() { return carried; }
    public String diagnostic() { return diagnostic; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Menu)) return false;
        Menu that = (Menu) other;
        return java.util.Objects.equals(className, that.className) && containerId == that.containerId && stateId == that.stateId && java.util.Objects.equals(type, that.type) && typeAvailable == that.typeAvailable && displayed == that.displayed && synchronizedWithPlayer == that.synchronizedWithPlayer && slotCount == that.slotCount && java.util.Objects.equals(carried, that.carried) && java.util.Objects.equals(diagnostic, that.diagnostic);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(className);
        hash = 31 * hash + Integer.hashCode(containerId);
        hash = 31 * hash + Integer.hashCode(stateId);
        hash = 31 * hash + java.util.Objects.hashCode(type);
        hash = 31 * hash + Boolean.hashCode(typeAvailable);
        hash = 31 * hash + Boolean.hashCode(displayed);
        hash = 31 * hash + Boolean.hashCode(synchronizedWithPlayer);
        hash = 31 * hash + Integer.hashCode(slotCount);
        hash = 31 * hash + java.util.Objects.hashCode(carried);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostic);
        return hash;
    }
    @Override public String toString() { return "Menu[className=" + className + ", containerId=" + containerId + ", stateId=" + stateId + ", type=" + type + ", typeAvailable=" + typeAvailable + ", displayed=" + displayed + ", synchronizedWithPlayer=" + synchronizedWithPlayer + ", slotCount=" + slotCount + ", carried=" + carried + ", diagnostic=" + diagnostic + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Menu> schema() {
            return new dev.openallay.value.ValueSchema<>(Menu.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Menu>>asList(new dev.openallay.value.ValueSchema.Component<>(Menu.class, "className", Menu::className), new dev.openallay.value.ValueSchema.Component<>(Menu.class, "containerId", Menu::containerId), new dev.openallay.value.ValueSchema.Component<>(Menu.class, "stateId", Menu::stateId), new dev.openallay.value.ValueSchema.Component<>(Menu.class, "type", Menu::type), new dev.openallay.value.ValueSchema.Component<>(Menu.class, "typeAvailable", Menu::typeAvailable), new dev.openallay.value.ValueSchema.Component<>(Menu.class, "displayed", Menu::displayed), new dev.openallay.value.ValueSchema.Component<>(Menu.class, "synchronizedWithPlayer", Menu::synchronizedWithPlayer), new dev.openallay.value.ValueSchema.Component<>(Menu.class, "slotCount", Menu::slotCount), new dev.openallay.value.ValueSchema.Component<>(Menu.class, "carried", Menu::carried), new dev.openallay.value.ValueSchema.Component<>(Menu.class, "diagnostic", Menu::diagnostic)), arguments -> new Menu((String) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (String) arguments[3], (Boolean) arguments[4], (Boolean) arguments[5], (Boolean) arguments[6], (Integer) arguments[7], (Item) arguments[8], (String) arguments[9]));
        }
    }
}
@dev.openallay.value.ValueType(Hover.ValueSchemaProvider.class)
public static final class Hover {
    private final double x;
    private final double y;
    private final boolean mouseGrabbed;
    private final String kind;
    private final int menuSlot;
    private final int containerSlot;
    private final Item item;
    private final String diagnostic;
    public Hover(double x, double y, boolean mouseGrabbed, String kind, int menuSlot, int containerSlot, Item item, String diagnostic) {

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

        this.x = x;
        this.y = y;
        this.mouseGrabbed = mouseGrabbed;
        this.kind = kind;
        this.menuSlot = menuSlot;
        this.containerSlot = containerSlot;
        this.item = item;
        this.diagnostic = diagnostic;
    }
    public double x() { return x; }
    public double y() { return y; }
    public boolean mouseGrabbed() { return mouseGrabbed; }
    public String kind() { return kind; }
    public int menuSlot() { return menuSlot; }
    public int containerSlot() { return containerSlot; }
    public Item item() { return item; }
    public String diagnostic() { return diagnostic; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Hover)) return false;
        Hover that = (Hover) other;
        return Double.compare(x, that.x) == 0 && Double.compare(y, that.y) == 0 && mouseGrabbed == that.mouseGrabbed && java.util.Objects.equals(kind, that.kind) && menuSlot == that.menuSlot && containerSlot == that.containerSlot && java.util.Objects.equals(item, that.item) && java.util.Objects.equals(diagnostic, that.diagnostic);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Double.hashCode(x);
        hash = 31 * hash + Double.hashCode(y);
        hash = 31 * hash + Boolean.hashCode(mouseGrabbed);
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + Integer.hashCode(menuSlot);
        hash = 31 * hash + Integer.hashCode(containerSlot);
        hash = 31 * hash + java.util.Objects.hashCode(item);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostic);
        return hash;
    }
    @Override public String toString() { return "Hover[x=" + x + ", y=" + y + ", mouseGrabbed=" + mouseGrabbed + ", kind=" + kind + ", menuSlot=" + menuSlot + ", containerSlot=" + containerSlot + ", item=" + item + ", diagnostic=" + diagnostic + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Hover> schema() {
            return new dev.openallay.value.ValueSchema<>(Hover.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Hover>>asList(new dev.openallay.value.ValueSchema.Component<>(Hover.class, "x", Hover::x), new dev.openallay.value.ValueSchema.Component<>(Hover.class, "y", Hover::y), new dev.openallay.value.ValueSchema.Component<>(Hover.class, "mouseGrabbed", Hover::mouseGrabbed), new dev.openallay.value.ValueSchema.Component<>(Hover.class, "kind", Hover::kind), new dev.openallay.value.ValueSchema.Component<>(Hover.class, "menuSlot", Hover::menuSlot), new dev.openallay.value.ValueSchema.Component<>(Hover.class, "containerSlot", Hover::containerSlot), new dev.openallay.value.ValueSchema.Component<>(Hover.class, "item", Hover::item), new dev.openallay.value.ValueSchema.Component<>(Hover.class, "diagnostic", Hover::diagnostic)), arguments -> new Hover((Double) arguments[0], (Double) arguments[1], (Boolean) arguments[2], (String) arguments[3], (Integer) arguments[4], (Integer) arguments[5], (Item) arguments[6], (String) arguments[7]));
        }
    }
}
private static String nonBlank(String value, String field) {
        if (value == null || dev.openallay.util.Java8Strings.isBlank(value)) {
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
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WorldFocusObservation)) return false;
        WorldFocusObservation that = (WorldFocusObservation) other;
        return java.util.Objects.equals(capturedAt, that.capturedAt) && java.util.Objects.equals(actorId, that.actorId) && java.util.Objects.equals(dimension, that.dimension) && java.util.Objects.equals(camera, that.camera) && java.util.Objects.equals(target, that.target) && java.util.Objects.equals(mainHand, that.mainHand) && java.util.Objects.equals(offHand, that.offHand) && java.util.Objects.equals(screen, that.screen) && java.util.Objects.equals(menu, that.menu) && java.util.Objects.equals(hover, that.hover) && java.util.Objects.equals(evidence, that.evidence);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(capturedAt);
        hash = 31 * hash + java.util.Objects.hashCode(actorId);
        hash = 31 * hash + java.util.Objects.hashCode(dimension);
        hash = 31 * hash + java.util.Objects.hashCode(camera);
        hash = 31 * hash + java.util.Objects.hashCode(target);
        hash = 31 * hash + java.util.Objects.hashCode(mainHand);
        hash = 31 * hash + java.util.Objects.hashCode(offHand);
        hash = 31 * hash + java.util.Objects.hashCode(screen);
        hash = 31 * hash + java.util.Objects.hashCode(menu);
        hash = 31 * hash + java.util.Objects.hashCode(hover);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        return hash;
    }
    @Override public String toString() { return "WorldFocusObservation[capturedAt=" + capturedAt + ", actorId=" + actorId + ", dimension=" + dimension + ", camera=" + camera + ", target=" + target + ", mainHand=" + mainHand + ", offHand=" + offHand + ", screen=" + screen + ", menu=" + menu + ", hover=" + hover + ", evidence=" + evidence + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<WorldFocusObservation> schema() {
            return new dev.openallay.value.ValueSchema<>(WorldFocusObservation.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<WorldFocusObservation>>asList(new dev.openallay.value.ValueSchema.Component<>(WorldFocusObservation.class, "capturedAt", WorldFocusObservation::capturedAt), new dev.openallay.value.ValueSchema.Component<>(WorldFocusObservation.class, "actorId", WorldFocusObservation::actorId), new dev.openallay.value.ValueSchema.Component<>(WorldFocusObservation.class, "dimension", WorldFocusObservation::dimension), new dev.openallay.value.ValueSchema.Component<>(WorldFocusObservation.class, "camera", WorldFocusObservation::camera), new dev.openallay.value.ValueSchema.Component<>(WorldFocusObservation.class, "target", WorldFocusObservation::target), new dev.openallay.value.ValueSchema.Component<>(WorldFocusObservation.class, "mainHand", WorldFocusObservation::mainHand), new dev.openallay.value.ValueSchema.Component<>(WorldFocusObservation.class, "offHand", WorldFocusObservation::offHand), new dev.openallay.value.ValueSchema.Component<>(WorldFocusObservation.class, "screen", WorldFocusObservation::screen), new dev.openallay.value.ValueSchema.Component<>(WorldFocusObservation.class, "menu", WorldFocusObservation::menu), new dev.openallay.value.ValueSchema.Component<>(WorldFocusObservation.class, "hover", WorldFocusObservation::hover), new dev.openallay.value.ValueSchema.Component<>(WorldFocusObservation.class, "evidence", WorldFocusObservation::evidence)), arguments -> new WorldFocusObservation((Instant) arguments[0], (UUID) arguments[1], (String) arguments[2], (Camera) arguments[3], (Target) arguments[4], (Item) arguments[5], (Item) arguments[6], (Screen) arguments[7], (Menu) arguments[8], (Hover) arguments[9], (EvidenceMetadata) arguments[10]));
        }
    }
}
