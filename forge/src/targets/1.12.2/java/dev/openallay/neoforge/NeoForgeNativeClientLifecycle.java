package dev.openallay.neoforge;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** First native tick is startup; actual Minecraft shutdown is the stopping boundary. */
public final class NeoForgeNativeClientLifecycle {
    private static final java.util.List<Runnable> stopping = new java.util.ArrayList<>();
    private static final AtomicBoolean stopped = new AtomicBoolean();
    private NeoForgeNativeClientLifecycle() {}
    static void onStarted(Consumer<Minecraft> started) {
        AtomicBoolean first = new AtomicBoolean(true);
        MinecraftForge.EVENT_BUS.register(new Object() {
            @SubscribeEvent public void ticked(TickEvent.ClientTickEvent event) {
                if (event.phase == TickEvent.Phase.START && first.compareAndSet(true, false)) {
                    started.accept(Minecraft.getMinecraft());
                }
            }
        });
    }
    static void onStopping(Runnable callback) { stopping.add(java.util.Objects.requireNonNull(callback)); }
    public static void stopping() {
        if (stopped.compareAndSet(false, true)) {
            dev.openallay.server.NativeServerActorHandoffs.cleanup(stopping.toArray(Runnable[]::new));
        }
    }
}
