package dev.openallay.adapter.minecraft.v26_2.world;

import com.mojang.serialization.Codec;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Save incarnation owned and persisted by Minecraft's live SavedData subsystem. */
final class NativeWorldIdentity extends SavedData {
    static final Codec<NativeWorldIdentity> CODEC = Codec.STRING.fieldOf("uuid")
            .xmap(id -> new NativeWorldIdentity(UUID.fromString(id)),value -> value.id.toString()).codec();
    static final SavedDataType<NativeWorldIdentity> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("openallay_builder","world_identity"), NativeWorldIdentity::new, CODEC, net.minecraft.util.datafix.DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private final UUID id;
    NativeWorldIdentity() { this(UUID.randomUUID()); setDirty(); }
    private NativeWorldIdentity(UUID id) { this.id=id; }
    String id() { return id.toString(); }
}
