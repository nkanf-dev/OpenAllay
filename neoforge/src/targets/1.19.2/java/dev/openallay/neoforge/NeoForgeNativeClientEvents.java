package dev.openallay.neoforge;

import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;

/** Native chat/key/end-tick callbacks; feature behavior stays in the shared bootstrap. */
final class NeoForgeNativeClientEvents {
    private NeoForgeNativeClientEvents() {}
    static void onSystemChat(BiConsumer<UUID, String> feedback) {
        // Forge 43.5.0 posts signed player chat as the base event, System as its subclass.
        // One listener avoids duplicate feedback and excludes native overlay-only messages.
        MinecraftForge.EVENT_BUS.addListener((ClientChatReceivedEvent event) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null
                    && (!(event instanceof ClientChatReceivedEvent.System system) || !system.isOverlay())) {
                feedback.accept(client.player.getUUID(), event.getMessage().getString());
            }
        });
    }
    static void registerKeys() {
        NeoForgeNativeModBus.get().addListener((RegisterKeyMappingsEvent event) -> NeoForgeNativeKeyRegistration.register(event));
    }
    static void onEndTick(Runnable tick) {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) tick.run();
        });
    }
}
