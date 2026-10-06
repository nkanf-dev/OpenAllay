package dev.openallay.neoforge;

import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraft.network.chat.ChatType;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.common.MinecraftForge;

/** Native chat/key/end-tick callbacks; feature behavior stays in the shared bootstrap. */
final class NeoForgeNativeClientEvents {
    private NeoForgeNativeClientEvents() {}
    static void onSystemChat(BiConsumer<UUID, String> feedback) {
        // Forge 40.3.0 uses one base event for chat, system messages and overlays.
        // Keep player and command feedback; omit GAME_INFO action-bar messages.
        MinecraftForge.EVENT_BUS.addListener((ClientChatReceivedEvent event) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && event.getType() != ChatType.GAME_INFO) {
                feedback.accept(client.player.getUUID(), event.getMessage().getString());
            }
        });
    }
    static void registerKeys() {
        NeoForgeNativeModBus.get().addListener((FMLClientSetupEvent event) -> NeoForgeNativeKeyRegistration.register(event));
    }
    static void onEndTick(Runnable tick) {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) tick.run();
        });
    }
}
