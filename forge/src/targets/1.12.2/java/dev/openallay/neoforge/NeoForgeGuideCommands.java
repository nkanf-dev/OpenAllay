package dev.openallay.neoforge;

import dev.openallay.command.GuideCommandSpec;
import dev.openallay.guide.GuideCommandFacade;

/** Legacy native projection uses the canonical neutral grammar and dispatch. */
public final class NeoForgeGuideCommands {
    private NeoForgeGuideCommands() {}
    public static void register(GuideCommandFacade guide) { NeoForgeNativeGuideCommandRegistration.register(guide); }
}
