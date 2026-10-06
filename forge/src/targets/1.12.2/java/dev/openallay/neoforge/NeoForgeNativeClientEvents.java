package dev.openallay.neoforge;

import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** FML14 typed callback listeners; no feature behavior belongs in this leaf. */
final class NeoForgeNativeClientEvents {
    private NeoForgeNativeClientEvents() {}
    static void onSystemChat(BiConsumer<UUID, String> feedback) {
        MinecraftForge.EVENT_BUS.register(new Object() {
            @SubscribeEvent public void received(ClientChatReceivedEvent event) {
                Minecraft client = Minecraft.getMinecraft();
                if (event.getType() != 2 && client.player != null) {
                    feedback.accept(client.player.getUniqueID(), event.getMessage().getUnformattedText());
                }
            }
        });
    }
    static void registerKeys() { NeoForgeNativeKeyRegistration.register(); }
    static void onEndTick(Runnable tick) {
        MinecraftForge.EVENT_BUS.register(new Object() {
            @SubscribeEvent public void ticked(TickEvent.ClientTickEvent event) {
                if (event.phase == TickEvent.Phase.END) tick.run();
            }
        });
    }
}
