package dev.openallay.neoforge;

import dev.openallay.guide.GuideCommandFacade;
import dev.openallay.guide.GuideNotice;
import dev.openallay.platform.minecraft.MinecraftComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import java.util.UUID;

/** Current native command-source adapter; feature grammar is single-source. */
final class NeoForgeNativeGuideCommandRegistration {
    private NeoForgeNativeGuideCommandRegistration() {}
    static void register(GuideCommandFacade guide) {
        NeoForgeNativeCommandRegistration.client(dispatcher -> dispatcher.register(
                NeoForgeGuideCommands.tree(guide, Source::new)));
    }
    private record Source(CommandSourceStack nativeSource) implements GuideCommandSource {
        @Override public UUID actor() {
            return java.util.Objects.requireNonNull(Minecraft.getInstance().player).getUUID();
        }
        @Override public void publish(GuideNotice notice) {
            var message = MinecraftComponents.literal("[OpenAllay] " + notice.message());
            if (notice.level() == GuideNotice.Level.ERROR) nativeSource.sendFailure(message);
            else dev.openallay.context.minecraft.MinecraftCommandFeedback.success(nativeSource, () -> message, false);
        }
    }
}
