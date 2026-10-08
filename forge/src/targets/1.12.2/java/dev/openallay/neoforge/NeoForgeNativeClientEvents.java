package dev.openallay.neoforge;

import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ChatType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** FML14 typed callback listeners; no feature behavior belongs in this leaf. */
public final class NeoForgeNativeClientEvents {
    private NeoForgeNativeClientEvents() {}
    static void onSystemChat(BiConsumer<UUID, String> feedback) {
        MinecraftForge.EVENT_BUS.register(new ChatListener(feedback));
    }
    static void registerKeys() { NeoForgeNativeKeyRegistration.register(); }
    static void onEndTick(Runnable tick) { MinecraftForge.EVENT_BUS.register(new TickListener(tick)); }
    public static final class ChatListener {
        private final BiConsumer<UUID, String> feedback;
        ChatListener(BiConsumer<UUID, String> feedback) { this.feedback = feedback; }
        @SubscribeEvent public void received(ClientChatReceivedEvent event) {
            Minecraft client = Minecraft.getMinecraft();
            if (event.getType() != ChatType.GAME_INFO && client.player != null) {
                feedback.accept(client.player.getUniqueID(), event.getMessage().getUnformattedText());
            }
        }
    }
    public static final class TickListener {
        private final Runnable tick;
        TickListener(Runnable tick) { this.tick = tick; }
        @SubscribeEvent public void ticked(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) tick.run();
        }
    }
}
