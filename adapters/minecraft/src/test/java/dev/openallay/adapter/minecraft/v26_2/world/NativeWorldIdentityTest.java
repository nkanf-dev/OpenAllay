package dev.openallay.adapter.minecraft.v26_2.world;
import static org.junit.jupiter.api.Assertions.*;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
class NativeWorldIdentityTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @org.junit.jupiter.api.BeforeAll static void boot() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    @Test void nativeSavedDataStorageReloadPreservesIdentity() {
        String original;
        try(var storage=new net.minecraft.world.level.storage.SavedDataStorage(directory,
                net.minecraft.util.datafix.DataFixers.getDataFixer(),net.minecraft.core.RegistryAccess.EMPTY)) {
            original=storage.computeIfAbsent(NativeWorldIdentity.TYPE).id();
        }
        try(var storage=new net.minecraft.world.level.storage.SavedDataStorage(directory,
                net.minecraft.util.datafix.DataFixers.getDataFixer(),net.minecraft.core.RegistryAccess.EMPTY)) {
            assertEquals(original,storage.computeIfAbsent(NativeWorldIdentity.TYPE).id());
        }
    }

    @Test void readingMissingNativeSavedDataIdentityDoesNotCreateOrPersistIt() throws Exception {
        try(var storage=new net.minecraft.world.level.storage.SavedDataStorage(directory,
                net.minecraft.util.datafix.DataFixers.getDataFixer(),net.minecraft.core.RegistryAccess.EMPTY)) {
            assertNull(storage.get(NativeWorldIdentity.TYPE));
        }
        try(var files=java.nio.file.Files.walk(directory)) {
            assertTrue(files.noneMatch(java.nio.file.Files::isRegularFile));
        }
    }

    @Test void readingExistingNativeSavedDataIdentityDoesNotMarkItDirty() {
        String original;
        try(var storage=new net.minecraft.world.level.storage.SavedDataStorage(directory,
                net.minecraft.util.datafix.DataFixers.getDataFixer(),net.minecraft.core.RegistryAccess.EMPTY)) {
            original=storage.computeIfAbsent(NativeWorldIdentity.TYPE).id();
        }
        try(var storage=new net.minecraft.world.level.storage.SavedDataStorage(directory,
                net.minecraft.util.datafix.DataFixers.getDataFixer(),net.minecraft.core.RegistryAccess.EMPTY)) {
            NativeWorldIdentity existing=storage.get(NativeWorldIdentity.TYPE);
            assertNotNull(existing);
            assertEquals(original,existing.id());
            assertFalse(existing.isDirty());
        }
    }

    @Test void recreatedWorldGetsDifferentIdentityEvenAtSamePathAndSeed() {
        assertNotEquals(new NativeWorldIdentity().id(),new NativeWorldIdentity().id());
    }
    @Test void minecraftCodecReloadPreservesIncarnation() {
        NativeWorldIdentity original=new NativeWorldIdentity();
        var encoded=NativeWorldIdentity.CODEC.encodeStart(JsonOps.INSTANCE,original).getOrThrow();
        var restored=NativeWorldIdentity.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow();
        assertEquals(original.id(),restored.id());
        assertTrue(original.isDirty());
    }
}
