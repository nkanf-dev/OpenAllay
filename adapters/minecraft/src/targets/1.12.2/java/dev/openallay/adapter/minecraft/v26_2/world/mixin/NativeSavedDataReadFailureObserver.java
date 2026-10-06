package dev.openallay.adapter.minecraft.v26_2.world.mixin;

import dev.openallay.adapter.minecraft.v26_2.world.NativeSavedDataReadObservation;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** Observe the exact caught exception; retain the real native printStackTrace. */
@Mixin(MapStorage.class)
abstract class NativeSavedDataReadFailureObserver {
    @Redirect(method="getOrLoadData(Ljava/lang/Class;Ljava/lang/String;)Lnet/minecraft/world/storage/WorldSavedData;",
        at=@At(value="INVOKE",target="Ljava/lang/Exception;printStackTrace()V",remap=false),
        require=1,expect=1,allow=1)
    private void openallay$observeFailure(Exception failure,Class<? extends WorldSavedData> type,String id) {
        failure.printStackTrace();
        NativeSavedDataReadObservation.observe((MapStorage)(Object)this,id,failure);
    }
}
