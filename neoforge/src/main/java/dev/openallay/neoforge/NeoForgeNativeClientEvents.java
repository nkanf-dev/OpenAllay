package dev.openallay.neoforge;

import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Native chat/key/end-tick callbacks; feature behavior stays in the shared bootstrap. */
final class NeoForgeNativeClientEvents {
    private NeoForgeNativeClientEvents() {}
    static void onSystemChat(BiConsumer<UUID, String> feedback) {
        NeoForge.EVENT_BUS.addListener((ClientChatReceivedEvent.System event) -> {
            Minecraft client = Minecraft.getInstance();
            if (!event.isOverlay() && client.player != null) {
                feedback.accept(client.player.getUUID(), event.getMessage().getString());
            }
        });
    }
    static void registerKeys() {
        NeoForgeNativeModBus.get().addListener((RegisterKeyMappingsEvent event) -> NeoForgeNativeKeyRegistration.register(event));
    }
    static void onEndTick(Runnable tick) {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick.run());
    }
}
