package dev.openallay.adapter.minecraft.v26_2.world;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Save incarnation persisted by Minecraft 1.20.4/1.20.3/1.20.2's Function Factory and CompoundTag save. */
final class NativeWorldIdentity extends SavedData {
    private static final String STORAGE_ID = "openallay_builder_world_identity";

    private static final SavedData.Factory<NativeWorldIdentity> FACTORY = new SavedData.Factory<>(
            NativeWorldIdentity::new, NativeWorldIdentity::load,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private static NativeWorldIdentity load(CompoundTag tag) {
        if (!(tag.get("uuid") instanceof StringTag))
            throw new IllegalArgumentException("World identity requires a string uuid");
        return new NativeWorldIdentity(UUID.fromString(tag.getString("uuid")));
    }

    static NativeWorldIdentity getOrCreate(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, STORAGE_ID);
    }
    static NativeWorldIdentity getExisting(ServerLevel level) {
        return level.getDataStorage().get(FACTORY, STORAGE_ID);
    }

    private final UUID id;
    NativeWorldIdentity() { this(UUID.randomUUID()); setDirty(); }
    private NativeWorldIdentity(UUID id) { this.id = id; }
    String id() { return id.toString(); }

    @Override public CompoundTag save(CompoundTag tag) {
        tag.putString("uuid", id());
        return tag;
    }
}
