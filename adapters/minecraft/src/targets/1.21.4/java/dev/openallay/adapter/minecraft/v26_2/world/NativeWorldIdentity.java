package dev.openallay.adapter.minecraft.v26_2.world;

import com.mojang.serialization.Codec;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Save incarnation persisted by Minecraft 1.21.4's native Factory and CompoundTag save. */
final class NativeWorldIdentity extends SavedData {
    private static final String STORAGE_ID = "openallay_builder_world_identity";
    static final Codec<NativeWorldIdentity> CODEC = Codec.STRING.fieldOf("uuid")
            .xmap(id -> new NativeWorldIdentity(UUID.fromString(id)), value -> value.id.toString()).codec();
    private static final SavedData.Factory<NativeWorldIdentity> FACTORY = new SavedData.Factory<>(
            NativeWorldIdentity::new, (tag, registries) -> CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow(),
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

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

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("uuid", id());
        return tag;
    }
}
