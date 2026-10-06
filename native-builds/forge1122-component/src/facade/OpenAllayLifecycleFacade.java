package dev.openallay.forge1122.facade;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;

/** Java8 annotation-discovery facade; direct typed delegation to the canonical Java17 native owner. */
@Mod(modid="openallay", name="OpenAllay", useMetadata=true, acceptedMinecraftVersions="[1.12.2]", acceptableRemoteVersions="*")
public final class OpenAllayLifecycleFacade {
    private final dev.openallay.neoforge.OpenAllayNeoForge owner = new dev.openallay.neoforge.OpenAllayNeoForge();
    @Mod.EventHandler public void preInitialize(FMLPreInitializationEvent event) { owner.preInitialize(event); }
    @Mod.EventHandler public void serverStarting(FMLServerStartingEvent event) { owner.serverStarting(event); }
    @Mod.EventHandler public void serverStarted(FMLServerStartedEvent event) { owner.serverStarted(event); }
    @Mod.EventHandler public void serverStopped(FMLServerStoppedEvent event) { owner.serverStopped(event); }
}
