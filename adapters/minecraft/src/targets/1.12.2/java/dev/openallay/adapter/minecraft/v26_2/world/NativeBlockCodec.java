package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.nbt.NBTException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.io.StringReader;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.world.WorldServer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.properties.IProperty;

/**
 * Native, live-world block serialization. All calls belong on the game owner thread;
 * methods taking a level enforce that requirement. No offline region data is used.
 */
final class NativeBlockCodec {
    // Send clients the update, but defer ordinary neighbor and shape propagation.
    // Native onPlace/preRemoveSideEffects hooks still run with these flags.
    private static final int WRITE_FLAGS = 18;
    // Native 1.12 chest/shulker metadata; attached Forge data is opaque and rejected for nonidentity transforms.
    // Only exact native container payload fields may retain nonidentity transforms.
    private static final Set<String> CONTAINER_FIELDS = dev.openallay.util.Java8Collections.setOf("id", "x", "y", "z", "Items", "LootTable", "LootTableSeed", "Lock", "CustomName");

    // Native states are canonical immutable values. Keep a bounded, owner-thread-local
    // palette, never world handles, live entities, positions or mutable SNBT compounds.
    private static final int PALETTE_SIZE = 4096;
    private static final ThreadLocal<Palette> PALETTE = ThreadLocal.withInitial(Palette::new);
    private static final class Palette {
        final Map<String, IBlockState> inputs = boundedPalette();
        final Map<IBlockState, String> encoded = boundedPalette();
    }
    private static <K,V> Map<K,V> boundedPalette() {
        return new java.util.LinkedHashMap<>(64, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<K,V> entry) { return size() > PALETTE_SIZE; }
        };
    }

    private NativeBlockCodec() {}

    /** Reads a block and its full native block-entity metadata from the live level. */
    public static String read(WorldServer level, BlockPos pos) { return snapshot(level,pos).json(); }

    /** Owner-only pre-hook snapshot. Non-BE states remain immutable native values until needed. */
    @dev.openallay.value.ValueType(Snapshot.ValueSchemaProvider.class)
static final class Snapshot {
    private final IBlockState state;
    private final NBTTagCompound tag;
    Snapshot(IBlockState state, NBTTagCompound tag) {
        this.state = state;
        this.tag = tag;
    }
    public IBlockState state() { return state; }
    public NBTTagCompound tag() { return tag; }
String json(){return encode(state,tag);}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Snapshot)) return false;
        Snapshot that = (Snapshot) other;
        return java.util.Objects.equals(state, that.state) && java.util.Objects.equals(tag, that.tag);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + java.util.Objects.hashCode(tag);
        return hash;
    }
    @Override public String toString() { return "Snapshot[state=" + state + ", tag=" + tag + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Snapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(Snapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Snapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(Snapshot.class, "state", Snapshot::state), new dev.openallay.value.ValueSchema.Component<>(Snapshot.class, "tag", Snapshot::tag)), arguments -> new Snapshot((IBlockState) arguments[0], (NBTTagCompound) arguments[1]));
        }
    }
}
    static Snapshot snapshot(WorldServer level,BlockPos pos) {
        checkOwnerAndPosition(level,pos);
        IBlockState state=level.getBlockState(pos);
        TileEntity entity=NativeBlockEntityLifecycle.live(level,pos);
        if(NativeBlockEntityLifecycle.hasEntity(state)&&entity==null)
            throw new ExtensionException("missing_block_entity","Missing live block entity at "+pos);
        // Opaque BE data must be captured before arbitrary native shape hooks. Plain
        // states need no JSON, properties map or string allocation at this stage.
        return new Snapshot(state,entity==null?null:save(level,entity));
    }

    /** Terrain reads preserve IDs/properties, but never serialize container content. */
    static String terrainState(WorldServer level, BlockPos pos) {
        checkOwnerAndPosition(level,pos);
        IBlockState state = level.getBlockState(pos);
        if (NativeBlockEntityLifecycle.hasEntity(state) && NativeBlockEntityLifecycle.live(level,pos) == null)
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
            JsonObject inputs = dev.openallay.json.JsonTrees.parse(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject actual = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : inputs.entrySet()) {
                try {
                    // Strict decode checks the registered ID and every supplied property.
                    // Full default properties are encoded; none are silently discarded.
                    actual.add(entry.getKey(), encodeState(decode(entry.getValue().toString())));
                } catch (IllegalArgumentException unavailable) {
                    // Omit only a genuinely absent native ID/property/value.
                    // Available roles remain actual native states with full properties.
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
    public static IBlockState decode(String stateJson) {
        IBlockState cached = PALETTE.get().inputs.get(stateJson);
        if (cached != null) return cached;
        JsonObject json = parse(stateJson);
        IBlockState state = decode(json);
        cacheInput(stateJson, json, state);
        return state;
    }

    private static void cacheInput(String input, JsonObject json, IBlockState state) {
        if (!NativeBlockEntityLifecycle.hasEntity(state) && (!json.has("blockEntity") || json.get("blockEntity").isJsonNull()))
            PALETTE.get().inputs.put(input, state);
    }

    /** Returns a new compound, or null when blockEntity is absent/null. */
    public static NBTTagCompound blockEntity(String stateJson) {
        return blockEntity(parse(stateJson));
    }

    /**
     * Mirrors first, then rotates clockwise around Y, using native state behavior.
     * x/front_back negates X; z/left_right negates Z; none leaves axes unchanged.
     * Vanilla chest, trapped chest, barrel, and shulker-box inventories with known
     * fields can retain their SNBT: facing belongs to IBlockState and preview rebases
     * metadata coordinates. Other opaque BE data may contain internal positions or
     * orientation not covered by IBlockState.mirror/rotate, so nonidentity transforms
     * fail explicitly. Identity transforms preserve the original SNBT string.
     */
    public static String transform(String stateJson, int degrees, String mirror) {
        JsonObject json = parse(stateJson);
        IBlockState state = decode(json);
        NBTTagCompound tag = blockEntity(json);
        if (degrees % 90 != 0) {
            throw new IllegalArgumentException("Rotation must be a multiple of 90 degrees");
        }
        net.minecraft.util.Rotation $oaSwitch1_exit_result;
$oaSwitch1_exit: {
switch ((Math.floorMod(degrees, 360))) {
case 0:
{
$oaSwitch1_exit_result = Rotation.NONE; break $oaSwitch1_exit;
}
case 90:
{
$oaSwitch1_exit_result = Rotation.CLOCKWISE_90; break $oaSwitch1_exit;
}
case 180:
{
$oaSwitch1_exit_result = Rotation.CLOCKWISE_180; break $oaSwitch1_exit;
}
case 270:
{
$oaSwitch1_exit_result = Rotation.COUNTERCLOCKWISE_90; break $oaSwitch1_exit;
}
default:
{
throw new IllegalArgumentException("Invalid rotation: " + degrees);
}
}
}
Rotation rotation = $oaSwitch1_exit_result;
        net.minecraft.util.Mirror $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((Objects.requireNonNull(mirror, "mirror"))) {
case "none":
{
$oaSwitch0_exit_result = Mirror.NONE; break $oaSwitch0_exit;
}
case "x":
case "front_back":
{
$oaSwitch0_exit_result = Mirror.FRONT_BACK; break $oaSwitch0_exit;
}
case "z":
case "left_right":
{
$oaSwitch0_exit_result = Mirror.LEFT_RIGHT; break $oaSwitch0_exit;
}
default:
{
throw new IllegalArgumentException("Unknown mirror: " + mirror);
}
}
}
Mirror nativeMirror = $oaSwitch0_exit_result;
        if (tag != null && !tag.hasNoTags() && (rotation != Rotation.NONE || nativeMirror != Mirror.NONE)
                && !canTransformContainer(state, tag)) {
            throw new ExtensionException("unsupported_opaque_block_entity_transform",
                    "Native block-state transforms do not transform opaque block-entity data");
        }
        JsonObject result = encodeState(state.getBlock().withRotation(state.getBlock().withMirror(state,nativeMirror),rotation));
        if (json.has("blockEntity")) result.add("blockEntity", json.get("blockEntity"));
        return result.toString();
    }

    /**
     * Produces the normalized intended state without placing anything in the level.
     * Full default properties and native BE data are included. BE x/y/z are rebased to
     * pos, and an omitted BE creates the block's fresh default entity, not old contents.
     * Native BE decoding uses the level's registries, but the entity stays detached.
     */
    public static String preview(WorldServer level, BlockPos pos, String stateJson) {
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
    public static boolean write(WorldServer level, BlockPos pos, String stateJson) {
        return write(level, pos, stateJson, () -> {});
    }

    /**
     * Java-owned invocation validation; no guest-language callback crosses this API.
     * Cancellation gates the whole synchronous commit once, after all preparation.
     * Once admitted, BE removal/replacement and readback finish without another
     * cancellation boundary. The next owner action rejects a cancelled invocation.
     */
    static boolean write(WorldServer level, BlockPos pos, String stateJson, Runnable requireActive) {
        return writeVerified(level, pos, stateJson, requireActive).changed();
    }

    /** Reuses the exact native verification readback; callers must not serialize it again. */
    @dev.openallay.value.ValueType(VerifiedWrite.ValueSchemaProvider.class)
static final class VerifiedWrite {
    private final String actual;
    private final boolean changed;
    VerifiedWrite(String actual, boolean changed) {
        this.actual = actual;
        this.changed = changed;
    }
    public String actual() { return actual; }
    public boolean changed() { return changed; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof VerifiedWrite)) return false;
        VerifiedWrite that = (VerifiedWrite) other;
        return java.util.Objects.equals(actual, that.actual) && changed == that.changed;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(actual);
        hash = 31 * hash + Boolean.hashCode(changed);
        return hash;
    }
    @Override public String toString() { return "VerifiedWrite[actual=" + actual + ", changed=" + changed + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<VerifiedWrite> schema() {
            return new dev.openallay.value.ValueSchema<>(VerifiedWrite.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<VerifiedWrite>>asList(new dev.openallay.value.ValueSchema.Component<>(VerifiedWrite.class, "actual", VerifiedWrite::actual), new dev.openallay.value.ValueSchema.Component<>(VerifiedWrite.class, "changed", VerifiedWrite::changed)), arguments -> new VerifiedWrite((String) arguments[0], (Boolean) arguments[1]));
        }
    }
}
    static VerifiedWrite writeVerified(WorldServer level, BlockPos pos, String stateJson, Runnable requireActive) {
        checkOwnerAndPosition(level, pos);
        Objects.requireNonNull(requireActive, "requireActive");
        Prepared prepared = prepare(level, pos, stateJson);
        IBlockState before = level.getBlockState(pos);
        TileEntity previousEntity = NativeBlockEntityLifecycle.live(level,pos);
        NBTTagCompound previousTag = previousEntity == null ? null : save(level, previousEntity);
        if (before.equals(prepared.state()) && Objects.equals(previousTag, prepared.tag()))
            return new VerifiedWrite(encode(before, previousTag), false);

        // One admission point for the synchronous owner-thread commit. Rechecking
        // between BE removal and insertion could leave a block with missing contents.
        requireActive.run();
        // Conservatively admit native persistence before any mutating hook. A
        // remove/install/onLoad failure can leave a changed image that must be saved.
        NativeBlockEntityLifecycle.markDirty(level, pos);
        if (!before.equals(prepared.state())) {
            if (!level.setBlockState(pos, prepared.state(), WRITE_FLAGS)) {
                throw placementFailed(pos, "Native setBlock refused the placement");
            }
        }
        if (!level.getBlockState(pos).equals(prepared.state())) {
            throw placementFailed(pos, "Native placement did not retain the requested block state");
        }
        if (prepared.entity() != null) {
            // Unregister the old listener/ticker before installing the validated entity.
            level.removeTileEntity(pos);
            NativeBlockEntityLifecycle.install(level, pos, prepared.entity());
            level.notifyBlockUpdate(pos, prepared.state(), prepared.state(), WRITE_FLAGS);
        }
        String actual = read(level, pos);
        if (!actual.equals(encode(prepared.state(), prepared.tag()))) {
            throw placementFailed(pos, "Native readback differs from the validated intended state");
        }
        return new VerifiedWrite(actual, true);
    }

    private static boolean canTransformContainer(IBlockState state,NBTTagCompound tag) {
        net.minecraft.util.ResourceLocation id=NativeWorldRegistries.blockId(state.getBlock());
        if(id==null || !"minecraft".equals(id.getResourceDomain())) return false;
        Class<?> blockClass=state.getBlock().getClass();
        Class<? extends TileEntity> expected;
        if(blockClass==BlockChest.class) expected=net.minecraft.tileentity.TileEntityChest.class;
        else if(blockClass==BlockShulkerBox.class) expected=net.minecraft.tileentity.TileEntityShulkerBox.class;
        else return false;
        net.minecraft.util.ResourceLocation expectedId=TileEntity.getKey(expected);
        return expectedId!=null && expectedId.toString().equals(NativeBlockEntityTags.requiredId(tag))
                && NativeBlockEntityTags.hasOnlyContainerFields(tag,CONTAINER_FIELDS);
    }

    private static Prepared prepare(WorldServer level, BlockPos pos, String stateJson) {
        IBlockState cached = PALETTE.get().inputs.get(stateJson);
        if (cached != null) return new Prepared(cached, null, null);
        JsonObject json = parse(stateJson);
        IBlockState state = decode(json);
        NBTTagCompound tag = blockEntity(json);
        if (!NativeBlockEntityLifecycle.hasEntity(state)) {
            if (tag != null) throw new IllegalArgumentException("Block does not support blockEntity: " + json.get("id"));
            cacheInput(stateJson, json, state);
            return new Prepared(state, null, null);
        }
        TileEntity entity = NativeBlockEntityLifecycle.createDetached(level, pos, state);
        Class<? extends TileEntity> expectedType=entity.getClass();
        net.minecraft.util.ResourceLocation expectedId=TileEntity.getKey(expectedType);
        if(tag!=null) {
            String rawId=NativeBlockEntityTags.requiredId(tag);
            net.minecraft.util.ResourceLocation id=NativeWorldResourceIds.parse(rawId,"blockEntity id");
            if(expectedId==null || !id.equals(expectedId))
                throw new IllegalArgumentException("blockEntity id "+rawId+" does not match block "+json.get("id"));
            tag.setString("id",id.toString());
            tag.setInteger("x",pos.getX()); tag.setInteger("y",pos.getY()); tag.setInteger("z",pos.getZ());
            NativeBlockEntityData.load(level,entity,tag);
            NativeBlockEntityLifecycle.afterLoad(entity,pos,state);
        }
        NativeBlockEntityLifecycle.validateDetached(entity,pos,state);
        if(entity.getClass()!=expectedType) throw new ExtensionException("invalid_block_entity","Native load changed the prepared entity class");
        NBTTagCompound image=save(level,entity);
        NativeBlockEntityLifecycle.validateDetached(entity,pos,state);
        if(entity.getClass()!=expectedType || !expectedId.toString().equals(NativeBlockEntityTags.requiredId(image)))
            throw new ExtensionException("invalid_block_entity","Native save changed the prepared entity identity");
        return new Prepared(state, entity, image);
    }

    private static NBTTagCompound save(WorldServer level, TileEntity entity) {
        return NativeBlockEntityData.save(level, entity);
    }

    private static IBlockState decode(JsonObject json) {
        String rawId = string(json.get("id"), "id");
        net.minecraft.util.ResourceLocation id = NativeWorldResourceIds.parse(rawId, "block id");
        Block block = NativeWorldRegistries.block(id.toString())
                .orElseThrow(() -> new IllegalArgumentException("Unknown block id: " + rawId));
        IBlockState state = block.getDefaultState();
        JsonElement properties = json.get("properties");
        if (properties != null) {
            if (!properties.isJsonObject()) throw new IllegalArgumentException("properties must be an object");
            for (Map.Entry<String, JsonElement> entry : properties.getAsJsonObject().entrySet()) {
                IProperty<?> property = block.getBlockState().getProperty(entry.getKey());
                if (property == null) {
                    throw new IllegalArgumentException("Unknown property " + entry.getKey() + " for " + id);
                }
                state = setProperty(state, property, string(entry.getValue(), "property " + entry.getKey()));
            }
        }
        // 1.12 persists block metadata, not every derived/extended property.
        // Reject an unrepresentable supplied state before mutation; never drop it.
        if(!block.getStateFromMeta(block.getMetaFromState(state)).equals(state))
            throw new IllegalArgumentException("Block state cannot be retained by native metadata: "+id);
        return state;
    }

    private static <T extends Comparable<T>> IBlockState setProperty(IBlockState state, IProperty<T> property, String name) {
        T value = property.parseValue(name).orNull();
        if(value==null) throw new IllegalArgumentException("Invalid value "+name+" for property "+property.getName());
        return state.withProperty(property, value);
    }

    private static NBTTagCompound blockEntity(JsonObject json) {
        JsonElement value = json.get("blockEntity");
        if (value == null || value.isJsonNull()) return null;
        NBTTagCompound tag;
        try {
            tag = NativeBlockEntityTags.parseCompound(string(value, "blockEntity"));
        } catch (NBTException failure) {
            throw new IllegalArgumentException("blockEntity must be a complete SNBT compound", failure);
        }
        for (String coordinate : new String[] {"x", "y", "z"}) {
            if (tag.hasKey(coordinate) && !(tag.getTag(coordinate) instanceof NBTTagInt)) {
                throw new IllegalArgumentException("blockEntity " + coordinate + " must be an integer tag");
            }
        }
        return tag;
    }

    private static JsonObject encodeState(IBlockState state) {
        JsonObject json = new JsonObject();
        net.minecraft.util.ResourceLocation id = NativeWorldRegistries.blockId(state.getBlock());
        if (id == null) throw new IllegalArgumentException("Cannot encode an unregistered block");
        json.addProperty("id", id.toString());
        json.add("properties", NativeBlockStateProperties.encode(state));
        return json;
    }

    private static String encode(IBlockState state, NBTTagCompound tag) {
        if (tag == null) return stateJson(state);
        JsonObject json = encodeState(state);
        json.addProperty("blockEntity", tag.toString());
        return json.toString();
    }

    static String stateJson(IBlockState state) {
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
                switch ((name)) {
case "id":
{
json.addProperty(name, readString(reader, name));
break;
}
case "properties":
{
{
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
break;
}
case "blockEntity":
{
{
                        if (reader.peek() == JsonToken.NULL) {
                            reader.nextNull();
                            json.add(name, com.google.gson.JsonNull.INSTANCE);
                        } else json.addProperty(name, readString(reader, name));
                    }
break;
}
default:
{
throw new IllegalArgumentException("Unknown block-state field: " + name);
}
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
        final class $oaPattern0_Holder { com.google.gson.JsonElement value; JsonPrimitive bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if (!((($oaPattern0_holder.value = value) instanceof com.google.gson.JsonPrimitive && (($oaPattern0_holder.bound = (JsonPrimitive) $oaPattern0_holder.value) != null))) || !$oaPattern0_holder.bound.isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return $oaPattern0_holder.bound.getAsString();
    }

    private static void checkOwnerAndPosition(WorldServer level, BlockPos pos) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        if (!level.isCallingFromMinecraftThread()) {
            throw new ExtensionException("wrong_owner", "Native block operations require the server owner thread");
        }
        if (!NativeWorldBounds.contains(level, pos)) {
            throw new IllegalArgumentException("Block position is outside the level's native bounds: " + pos);
        }
    }

    private static ExtensionException placementFailed(BlockPos pos, String reason) {
        return new ExtensionException("placement_failed", reason + " at " + pos);
    }

    @dev.openallay.value.ValueType(Prepared.ValueSchemaProvider.class)
private static final class Prepared {
    private final IBlockState state;
    private final TileEntity entity;
    private final NBTTagCompound tag;
    private Prepared(IBlockState state, TileEntity entity, NBTTagCompound tag) {
        this.state = state;
        this.entity = entity;
        this.tag = tag;
    }
    public IBlockState state() { return state; }
    public TileEntity entity() { return entity; }
    public NBTTagCompound tag() { return tag; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Prepared)) return false;
        Prepared that = (Prepared) other;
        return java.util.Objects.equals(state, that.state) && java.util.Objects.equals(entity, that.entity) && java.util.Objects.equals(tag, that.tag);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + java.util.Objects.hashCode(entity);
        hash = 31 * hash + java.util.Objects.hashCode(tag);
        return hash;
    }
    @Override public String toString() { return "Prepared[state=" + state + ", entity=" + entity + ", tag=" + tag + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Prepared> schema() {
            return new dev.openallay.value.ValueSchema<>(Prepared.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Prepared>>asList(new dev.openallay.value.ValueSchema.Component<>(Prepared.class, "state", Prepared::state), new dev.openallay.value.ValueSchema.Component<>(Prepared.class, "entity", Prepared::entity), new dev.openallay.value.ValueSchema.Component<>(Prepared.class, "tag", Prepared::tag)), arguments -> new Prepared((IBlockState) arguments[0], (TileEntity) arguments[1], (NBTTagCompound) arguments[2]));
        }
    }
}
}
