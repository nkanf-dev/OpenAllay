package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.io.StringReader;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Native, live-world block serialization. All calls belong on the game owner thread;
 * methods taking a level enforce that requirement. No offline region data is used.
 */
final class NativeBlockCodec {
    // Send clients the update, but defer ordinary neighbor and shape propagation.
    // Native onPlace/preRemoveSideEffects hooks still run with these flags.
    private static final int WRITE_FLAGS = 18;
    // Verified against the 26.2 container save/load methods and their base classes.
    // Inventory item components are content, not the placed container's orientation.
    private static final Set<String> CONTAINER_FIELDS = Set.of(
            "id", "x", "y", "z", "Items", "LootTable", "LootTableSeed", NativeContainerFieldNames.lock(), "CustomName", "components");

    // Native states are canonical immutable values. Keep a bounded, owner-thread-local
    // palette, never world handles, live entities, positions or mutable SNBT compounds.
    private static final int PALETTE_SIZE = 4096;
    private static final ThreadLocal<Palette> PALETTE = ThreadLocal.withInitial(Palette::new);
    private static final class Palette {
        final Map<String, BlockState> inputs = boundedPalette();
        final Map<BlockState, String> encoded = boundedPalette();
    }
    private static <K,V> Map<K,V> boundedPalette() {
        return new java.util.LinkedHashMap<>(64, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<K,V> entry) { return size() > PALETTE_SIZE; }
        };
    }

    private NativeBlockCodec() {}

    /** Reads a block and its full native block-entity metadata from the live level. */
    public static String read(ServerLevel level, BlockPos pos) { return snapshot(level,pos).json(); }

    /** Owner-only pre-hook snapshot. Non-BE states remain immutable native values until needed. */
    record Snapshot(BlockState state,CompoundTag tag) { String json(){return encode(state,tag);} }
    static Snapshot snapshot(ServerLevel level,BlockPos pos) {
        checkOwnerAndPosition(level,pos);
        BlockState state=level.getBlockState(pos);
        BlockEntity entity=level.getBlockEntity(pos);
        if(state.hasBlockEntity()&&entity==null)
            throw new ExtensionException("missing_block_entity","Missing live block entity at "+pos);
        // Opaque BE data must be captured before arbitrary native shape hooks. Plain
        // states need no JSON, properties map or string allocation at this stage.
        return new Snapshot(state,entity==null?null:save(level,entity));
    }

    /** Terrain reads preserve IDs/properties, but never serialize container content. */
    static String terrainState(ServerLevel level, BlockPos pos) {
        checkOwnerAndPosition(level,pos);
        BlockState state = level.getBlockState(pos);
        if (state.hasBlockEntity() && level.getBlockEntity(pos) == null)
            throw new ExtensionException("missing_block_entity", "Missing live block entity at " + pos);
        return stateJson(state);
    }

    /** Detached equality for failure accounting; SNBT remains opaque, as in the domain image. */
    static boolean sameImage(String first, String second) {
        return parse(first).equals(parse(second));
    }

    /** Actual native states for the fixed, bounded preset material-role inventory. */
    static JsonObject materialPalette() {
        // Loaded only inside the authorized context owner action, never at registration.
        try (InputStream stream = NativeBlockCodec.class.getResourceAsStream("material-palette-inputs.json")) {
            if (stream == null) throw new ExtensionException("material_unavailable", "The native material palette is unavailable");
            JsonObject inputs = com.google.gson.JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject actual = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : inputs.entrySet()) {
                try {
                    // Strict decode checks the registered ID and every supplied property.
                    // Full default properties are encoded; none are silently discarded.
                    actual.add(entry.getKey(), encodeState(decode(entry.getValue().toString())));
                } catch (RuntimeException failure) {
                    throw new ExtensionException("material_unavailable", "A required native preset material is unavailable: "
                            + entry.getKey(), failure);
                }
            }
            return actual;
        } catch (IOException failure) {
            throw new ExtensionException("material_unavailable", "The native material palette could not be read", failure);
        }
    }

    /** Drop owner-thread-local immutable palettes at the end of each bounded action. */
    static void releasePalette() { PALETTE.remove(); }

    /**
     * Decodes a registered block and its properties without registry default fallback.
     * Omitted properties use the block's native defaults. Present values must be strings.
     * The caller must dispatch to the game owner thread before touching native registries.
     */
    public static BlockState decode(String stateJson) {
        BlockState cached = PALETTE.get().inputs.get(stateJson);
        if (cached != null) return cached;
        JsonObject json = parse(stateJson);
        BlockState state = decode(json);
        cacheInput(stateJson, json, state);
        return state;
    }

    private static void cacheInput(String input, JsonObject json, BlockState state) {
        if (!state.hasBlockEntity() && (!json.has("blockEntity") || json.get("blockEntity").isJsonNull()))
            PALETTE.get().inputs.put(input, state);
    }

    /** Returns a new compound, or null when blockEntity is absent/null. */
    public static CompoundTag blockEntity(String stateJson) {
        return blockEntity(parse(stateJson));
    }

    /**
     * Mirrors first, then rotates clockwise around Y, using native state behavior.
     * x/front_back negates X; z/left_right negates Z; none leaves axes unchanged.
     * Vanilla chest, trapped chest, barrel, and shulker-box inventories with known
     * fields can retain their SNBT: facing belongs to BlockState and preview rebases
     * metadata coordinates. Other opaque BE data may contain internal positions or
     * orientation not covered by BlockState.mirror/rotate, so nonidentity transforms
     * fail explicitly. Identity transforms preserve the original SNBT string.
     */
    public static String transform(String stateJson, int degrees, String mirror) {
        JsonObject json = parse(stateJson);
        BlockState state = decode(json);
        CompoundTag tag = blockEntity(json);
        if (degrees % 90 != 0) {
            throw new IllegalArgumentException("Rotation must be a multiple of 90 degrees");
        }
        Rotation rotation = switch (Math.floorMod(degrees, 360)) {
            case 0 -> Rotation.NONE;
            case 90 -> Rotation.CLOCKWISE_90;
            case 180 -> Rotation.CLOCKWISE_180;
            case 270 -> Rotation.COUNTERCLOCKWISE_90;
            default -> throw new IllegalArgumentException("Invalid rotation: " + degrees);
        };
        Mirror nativeMirror = switch (Objects.requireNonNull(mirror, "mirror")) {
            case "none" -> Mirror.NONE;
            case "x", "front_back" -> Mirror.FRONT_BACK;
            case "z", "left_right" -> Mirror.LEFT_RIGHT;
            default -> throw new IllegalArgumentException("Unknown mirror: " + mirror);
        };
        if (tag != null && !tag.isEmpty() && (rotation != Rotation.NONE || nativeMirror != Mirror.NONE)
                && !canTransformContainer(state, tag)) {
            throw new ExtensionException("unsupported_opaque_block_entity_transform",
                    "Native block-state transforms do not transform opaque block-entity data");
        }
        JsonObject result = encodeState(state.mirror(nativeMirror).rotate(rotation));
        if (json.has("blockEntity")) result.add("blockEntity", json.get("blockEntity"));
        return result.toString();
    }

    /**
     * Produces the normalized intended state without placing anything in the level.
     * Full default properties and native BE data are included. BE x/y/z are rebased to
     * pos, and an omitted BE creates the block's fresh default entity, not old contents.
     * Native BE decoding uses the level's registries, but the entity stays detached.
     */
    public static String preview(ServerLevel level, BlockPos pos, String stateJson) {
        checkOwnerAndPosition(level, pos);
        Prepared prepared = prepare(level, pos, stateJson);
        return encode(prepared.state(), prepared.tag());
    }

    /**
     * Prevalidates on a detached BE, then writes live state with flags 18. Returns false
     * only for a proven unchanged state and BE. A rejected or altered placement throws
     * placement_failed; false never hides a failed placement. This is not a transaction:
     * native replacement hooks may have side effects, and callers must journal first.
     */
    public static boolean write(ServerLevel level, BlockPos pos, String stateJson) {
        return write(level, pos, stateJson, () -> {});
    }

    /**
     * Java-owned invocation validation; no guest-language callback crosses this API.
     * Cancellation gates the whole synchronous commit once, after all preparation.
     * Once admitted, BE removal/replacement and readback finish without another
     * cancellation boundary. The next owner action rejects a cancelled invocation.
     */
    static boolean write(ServerLevel level, BlockPos pos, String stateJson, Runnable requireActive) {
        return writeVerified(level, pos, stateJson, requireActive).changed();
    }

    /** Reuses the exact native verification readback; callers must not serialize it again. */
    record VerifiedWrite(String actual, boolean changed) {}
    static VerifiedWrite writeVerified(ServerLevel level, BlockPos pos, String stateJson, Runnable requireActive) {
        checkOwnerAndPosition(level, pos);
        Objects.requireNonNull(requireActive, "requireActive");
        Prepared prepared = prepare(level, pos, stateJson);
        BlockState before = level.getBlockState(pos);
        BlockEntity previousEntity = level.getBlockEntity(pos);
        CompoundTag previousTag = previousEntity == null ? null : save(level, previousEntity);
        if (before.equals(prepared.state()) && Objects.equals(previousTag, prepared.tag()))
            return new VerifiedWrite(encode(before, previousTag), false);

        // One admission point for the synchronous owner-thread commit. Rechecking
        // between BE removal and insertion could leave a block with missing contents.
        requireActive.run();
        if (!before.equals(prepared.state())) {
            if (!level.setBlock(pos, prepared.state(), WRITE_FLAGS)) {
                throw placementFailed(pos, "Native setBlock refused the placement");
            }
        }
        if (!level.getBlockState(pos).equals(prepared.state())) {
            throw placementFailed(pos, "Native placement did not retain the requested block state");
        }
        if (prepared.entity() != null) {
            // Unregister the old listener/ticker before installing the validated entity.
            level.removeBlockEntity(pos);
            level.setBlockEntity(prepared.entity());
            // BlockEntity.setChanged also updates comparator neighbors. Defer that work
            // to the controller's neighbor pass and mark the chunk dirty directly.
            level.blockEntityChanged(pos);
            level.sendBlockUpdated(pos, prepared.state(), prepared.state(), WRITE_FLAGS);
        }
        String actual = read(level, pos);
        if (!actual.equals(encode(prepared.state(), prepared.tag()))) {
            throw placementFailed(pos, "Native readback differs from the validated intended state");
        }
        return new VerifiedWrite(actual, true);
    }

    private static boolean canTransformContainer(BlockState state, CompoundTag tag) {
        var blockId = NativeWorldRegistries.blockId(state.getBlock());
        if (blockId == null || !"minecraft".equals(blockId.getNamespace())) return false;
        Class<?> blockClass = state.getBlock().getClass();
        BlockEntityType<?> expected;
        if (blockClass == ChestBlock.class) expected = NativeContainerEntityTypes.chest();
        else if (blockClass == TrappedChestBlock.class) expected = NativeContainerEntityTypes.trappedChest();
        else if (blockClass == BarrelBlock.class) expected = NativeContainerEntityTypes.barrel();
        else if (blockClass == ShulkerBoxBlock.class) expected = NativeContainerEntityTypes.shulkerBox();
        else return false;
        var id = NativeWorldResourceIds.tryParse(NativeBlockEntityTags.containerTransformId(tag));
        if (id == null || !expected.isValid(state)
                || NativeWorldRegistries.blockEntity(id.toString()).map(type -> type != expected).orElse(true)) return false;
        if (!NativeBlockEntityTags.hasOnlyContainerFields(tag, CONTAINER_FIELDS)) return false;
        // Unknown attached BE components can contain orientation. Fail rather than guess.
        return !tag.contains("components") || tag.get("components") instanceof CompoundTag components && components.isEmpty();
    }

    private static Prepared prepare(ServerLevel level, BlockPos pos, String stateJson) {
        BlockState cached = PALETTE.get().inputs.get(stateJson);
        if (cached != null) return new Prepared(cached, null, null);
        JsonObject json = parse(stateJson);
        BlockState state = decode(json);
        CompoundTag tag = blockEntity(json);
        if (!state.hasBlockEntity()) {
            if (tag != null) throw new IllegalArgumentException("Block does not support blockEntity: " + json.get("id"));
            cacheInput(stateJson, json, state);
            return new Prepared(state, null, null);
        }
        if (!(state.getBlock() instanceof EntityBlock entityBlock)) {
            throw new ExtensionException("invalid_block_entity", "Block has no native block-entity factory");
        }
        BlockEntity entity = entityBlock.newBlockEntity(pos, state);
        if (entity == null || !entity.getType().isValid(state)) {
            throw new ExtensionException("invalid_block_entity", "Native block-entity factory did not create a valid entity");
        }
        if (tag != null) {
            String rawId = NativeBlockEntityTags.requiredId(tag);
            var id = NativeWorldResourceIds.parse(rawId, "blockEntity id");
            BlockEntityType<?> type = NativeWorldRegistries.blockEntity(id.toString())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown blockEntity id: " + rawId));
            if (type != entity.getType() || !type.isValid(state)) {
                throw new IllegalArgumentException("blockEntity id " + rawId + " does not match block " + json.get("id"));
            }
            tag.putString("id", id.toString());
            tag.putInt("x", pos.getX());
            tag.putInt("y", pos.getY());
            tag.putInt("z", pos.getZ());
            NativeBlockEntityData.load(level, entity, tag);
        }
        return new Prepared(state, entity, save(level, entity));
    }

    private static CompoundTag save(ServerLevel level, BlockEntity entity) {
        return NativeBlockEntityData.save(level, entity);
    }

    private static BlockState decode(JsonObject json) {
        String rawId = string(json.get("id"), "id");
        var id = NativeWorldResourceIds.parse(rawId, "block id");
        Block block = NativeWorldRegistries.block(id.toString())
                .orElseThrow(() -> new IllegalArgumentException("Unknown block id: " + rawId));
        BlockState state = block.defaultBlockState();
        JsonElement properties = json.get("properties");
        if (properties != null) {
            if (!properties.isJsonObject()) throw new IllegalArgumentException("properties must be an object");
            for (Map.Entry<String, JsonElement> entry : properties.getAsJsonObject().entrySet()) {
                Property<?> property = block.getStateDefinition().getProperty(entry.getKey());
                if (property == null) {
                    throw new IllegalArgumentException("Unknown property " + entry.getKey() + " for " + id);
                }
                state = setProperty(state, property, string(entry.getValue(), "property " + entry.getKey()));
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState setProperty(BlockState state, Property<T> property, String name) {
        T value = property.getValue(name).orElseThrow(
                () -> new IllegalArgumentException("Invalid value " + name + " for property " + property.getName()));
        return state.setValue(property, value);
    }

    private static CompoundTag blockEntity(JsonObject json) {
        JsonElement value = json.get("blockEntity");
        if (value == null || value.isJsonNull()) return null;
        CompoundTag tag;
        try {
            tag = NativeBlockEntityTags.parseCompound(string(value, "blockEntity"));
        } catch (CommandSyntaxException failure) {
            throw new IllegalArgumentException("blockEntity must be a complete SNBT compound", failure);
        }
        for (String coordinate : new String[] {"x", "y", "z"}) {
            if (tag.contains(coordinate) && !(tag.get(coordinate) instanceof IntTag)) {
                throw new IllegalArgumentException("blockEntity " + coordinate + " must be an integer tag");
            }
        }
        return tag;
    }

    private static JsonObject encodeState(BlockState state) {
        JsonObject json = new JsonObject();
        var id = NativeWorldRegistries.blockId(state.getBlock());
        if (id == null) throw new IllegalArgumentException("Cannot encode an unregistered block");
        json.addProperty("id", id.toString());
        json.add("properties", NativeBlockStateProperties.encode(state));
        return json;
    }

    private static String encode(BlockState state, CompoundTag tag) {
        if (tag == null) return stateJson(state);
        JsonObject json = encodeState(state);
        json.addProperty("blockEntity", tag.toString());
        return json.toString();
    }

    static String stateJson(BlockState state) {
        return PALETTE.get().encoded.computeIfAbsent(state, value -> encodeState(value).toString());
    }

    /** Flat schema parsing rejects duplicate fields and Gson's legacy JSON extensions. */
    private static JsonObject parse(String stateJson) {
        if (stateJson == null) throw new IllegalArgumentException("Block state JSON must not be null");
        try (JsonReader reader = dev.openallay.json.JsonReaders.strict(new java.io.StringReader(stateJson))) {

            JsonObject json = new JsonObject();
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (json.has(name)) throw new IllegalArgumentException("Duplicate block-state field: " + name);
                switch (name) {
                    case "id" -> json.addProperty(name, readString(reader, name));
                    case "properties" -> {
                        JsonObject properties = new JsonObject();
                        reader.beginObject();
                        while (reader.hasNext()) {
                            String property = reader.nextName();
                            if (properties.has(property)) throw new IllegalArgumentException("Duplicate property: " + property);
                            properties.addProperty(property, readString(reader, "property " + property));
                        }
                        reader.endObject();
                        json.add(name, properties);
                    }
                    case "blockEntity" -> {
                        if (reader.peek() == JsonToken.NULL) {
                            reader.nextNull();
                            json.add(name, com.google.gson.JsonNull.INSTANCE);
                        } else json.addProperty(name, readString(reader, name));
                    }
                    default -> throw new IllegalArgumentException("Unknown block-state field: " + name);
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing block-state JSON data");
            if (!json.has("id")) throw new IllegalArgumentException("Block state requires id");
            return json;
        } catch (IOException | IllegalStateException failure) {
            throw new IllegalArgumentException("Invalid block-state JSON", failure);
        }
    }

    private static String readString(JsonReader reader, String field) throws IOException {
        if (reader.peek() != JsonToken.STRING) throw new IllegalArgumentException(field + " must be a string");
        return reader.nextString();
    }

    private static String string(JsonElement value, String field) {
        if (!(value instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return primitive.getAsString();
    }

    private static void checkOwnerAndPosition(ServerLevel level, BlockPos pos) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        if (!level.getServer().isSameThread()) {
            throw new ExtensionException("wrong_owner", "Native block operations require the server owner thread");
        }
        if (!NativeWorldBounds.contains(level, pos)) {
            throw new IllegalArgumentException("Block position is outside the level's native bounds: " + pos);
        }
    }

    private static ExtensionException placementFailed(BlockPos pos, String reason) {
        return new ExtensionException("placement_failed", reason + " at " + pos);
    }

    private record Prepared(BlockState state, BlockEntity entity, CompoundTag tag) {}
}
