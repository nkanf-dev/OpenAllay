package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;
import java.util.UUID;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.DimensionSavedDataManager;
import net.minecraft.world.storage.WorldSavedData;

/** Native cache/save ownership with no failure-to-absence or UUID replacement. */
final class NativeWorldIdentity extends WorldSavedData {
    private static final String STORAGE_ID = "openallay_builder_world_identity";
    private UUID uuid;
    private RuntimeException failedRead;
    private NativeWorldIdentity() { super(STORAGE_ID); }
    private static void requireOwner(ServerWorld level) {
        if (!level.getServer().isSameThread())
            throw new ExtensionException("wrong_owner", "Native world identity requires the server owner thread");
    }
    static NativeWorldIdentity getExisting(ServerWorld level) {
        requireOwner(level);
        DimensionSavedDataManager storage = level.getDataStorage();
        NativeWorldIdentity[] hydration = new NativeWorldIdentity[1];
        NativeWorldIdentity result;
        try (NativeSavedDataReadObservation.Scope observation = NativeSavedDataReadObservation.begin(storage, STORAGE_ID)) {
            result = storage.get(() -> {
                NativeWorldIdentity value = new NativeWorldIdentity();
                hydration[0] = value;
                return value;
            }, STORAGE_ID);
            if (observation.failure != null || result == null && hydration[0] != null) {
                NativeWorldIdentity failed = hydration[0] != null ? hydration[0] : new NativeWorldIdentity();
                Throwable cause = observation.failure != null ? observation.failure : failed.failedRead;
                failed.failedRead = new ExtensionException("world_identity_unavailable", "Native saved world identity could not be read", cause);
                // In-memory native failure receipt only. No UUID, dirty bit, save format or file write.
                failed.uuid = null;
                failed.setDirty(false);
                storage.set(failed);
                throw failed.failedRead;
            }
        }
        if (result != null) result.id();
        return result;
    }
    static NativeWorldIdentity getOrCreate(ServerWorld level) {
        NativeWorldIdentity existing = getExisting(level);
        if (existing != null) return existing;
        NativeWorldIdentity fresh = new NativeWorldIdentity();
        fresh.uuid = UUID.randomUUID();
        fresh.setDirty();
        level.getDataStorage().set(fresh);
        return fresh;
    }
    String id() {
        if (failedRead != null) throw failedRead;
        if (uuid == null) throw new ExtensionException("world_identity_unavailable", "Native world identity is not hydrated");
        return uuid.toString();
    }
    @Override public void load(CompoundNBT tag) {
        try {
            if (!(tag.get("uuid") instanceof StringNBT))
                throw new IllegalArgumentException("World identity requires a string uuid");
            uuid = UUID.fromString(tag.getString("uuid"));
            failedRead = null;
        } catch (RuntimeException failure) {
            uuid = null;
            failedRead = failure;
            throw failure;
        }
    }
    @Override public CompoundNBT save(CompoundNBT tag) {
        tag.putString("uuid", id()); // A failure marker can never serialize a persisted marker.
        return tag;
    }
}
