package dev.tomewisp.fabric;

import dev.tomewisp.TomeWispBootstrap;
import dev.tomewisp.TomeWispRuntime;
import net.fabricmc.api.ModInitializer;

public final class TomeWispFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        TomeWispRuntime runtime = TomeWispBootstrap.initialize();
        FabricDevelopmentCommands.register(runtime);
    }
}
