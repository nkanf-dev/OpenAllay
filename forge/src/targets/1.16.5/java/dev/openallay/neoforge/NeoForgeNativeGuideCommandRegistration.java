package dev.openallay.neoforge;

import dev.openallay.guide.GuideCommandFacade;
import dev.openallay.neoforge.command.ForgeClientGuideCommands;

/** Client-only selected registration; shared grammar does not require CommandSource. */
final class NeoForgeNativeGuideCommandRegistration {
    private NeoForgeNativeGuideCommandRegistration() {}
    static void register(GuideCommandFacade guide) { ForgeClientGuideCommands.register(guide); }
}
