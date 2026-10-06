package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/** Native cache/save ownership with no failure-to-absence or UUID replacement. */
public final class NativeWorldIdentity extends WorldSavedData {
    private static final String STORAGE_ID = "openallay_builder_world_identity";
    private UUID uuid;
    private RuntimeException failedRead;
    public NativeWorldIdentity(String id) { super(id); }
    private NativeWorldIdentity() { this(STORAGE_ID); }
    private static void requireOwner(WorldServer level) {
        if (!level.isCallingFromMinecraftThread())
            throw new ExtensionException("wrong_owner", "Native world identity requires the server owner thread");
    }
    static NativeWorldIdentity getExisting(WorldServer level) {
        requireOwner(level);
        MapStorage storage = level.getMapStorage();
        NativeWorldIdentity result;
        try (NativeSavedDataReadObservation.Scope observation=NativeSavedDataReadObservation.begin(storage,STORAGE_ID)) {
            result=(NativeWorldIdentity)storage.getOrLoadData(NativeWorldIdentity.class,STORAGE_ID);
            if(observation.failure!=null) {
                NativeWorldIdentity failed=result!=null ? result : new NativeWorldIdentity();
                failed.failedRead=new ExtensionException("world_identity_unavailable","Native saved world identity could not be read",observation.failure);
                failed.uuid=null;
                failed.setDirty(false);
                storage.setData(STORAGE_ID,failed);
                throw failed.failedRead;
            }
        }
        if (result != null) result.id();
        return result;
    }
    static NativeWorldIdentity getOrCreate(WorldServer level) {
        NativeWorldIdentity existing = getExisting(level);
        if (existing != null) return existing;
        NativeWorldIdentity fresh = new NativeWorldIdentity();
        fresh.uuid = UUID.randomUUID();
        fresh.markDirty();
        level.getMapStorage().setData(STORAGE_ID,fresh);
        return fresh;
    }
    String id() {
        if (failedRead != null) throw failedRead;
        if (uuid == null) throw new ExtensionException("world_identity_unavailable", "Native world identity is not hydrated");
        return uuid.toString();
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        try {
            if (!(tag.getTag("uuid") instanceof NBTTagString))
                throw new IllegalArgumentException("World identity requires a string uuid");
            uuid = UUID.fromString(tag.getString("uuid"));
            failedRead = null;
        } catch (RuntimeException failure) {
            uuid = null;
            failedRead = failure;
            throw failure;
        }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        tag.setString("uuid", id()); // A failure marker can never serialize a persisted marker.
        return tag;
    }
}
