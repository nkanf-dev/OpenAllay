package dev.openallay.adapter.minecraft.v26_2.world.mixin;

import dev.openallay.adapter.minecraft.v26_2.world.NativeSavedDataReadObservation;
import java.util.function.Supplier;
import net.minecraft.world.storage.DimensionSavedDataManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Observe the exact swallowed native Exception without changing its logging or parser. */
@Mixin(DimensionSavedDataManager.class)
abstract class NativeSavedDataReadFailureObserver {
    @Redirect(
            method = "readSavedData(Ljava/util/function/Supplier;Ljava/lang/String;)Lnet/minecraft/world/storage/WorldSavedData;",
            at = @At(value = "INVOKE", target = "Lorg/apache/logging/log4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V", remap = false),
            require = 1, expect = 1, allow = 1)
    private void openallay$observeFailure(Logger logger, String message, Object nativeId, Object nativeFailure,
            Supplier<?> factory, String id) {
        // The objects are this external logger's real overload arguments, not a native dispatch API.
        logger.error(message, nativeId, nativeFailure);
        if (!(nativeFailure instanceof Exception failure))
            throw new IllegalStateException("Native SavedData catch did not supply its declared Exception");
        NativeSavedDataReadObservation.observe((DimensionSavedDataManager) (Object) this, id, failure);
    }
}
