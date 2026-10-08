package dev.openallay.client.context;

import net.minecraft.client.Options;

/** Direct actual Forge36 public settings; no native report method exists in this family. */
public final class MinecraftClientOptionsFacts {
    private MinecraftClientOptionsFacts() {}
    public static String report(Options options) {
        StringBuilder report = new StringBuilder();
        report.append("sensitivity:").append(options.sensitivity).append("\n");
        report.append("renderDistance:").append(options.renderDistance).append("\n");
        report.append("entityDistanceScaling:").append(options.entityDistanceScaling).append("\n");
        report.append("framerateLimit:").append(options.framerateLimit).append("\n");
        report.append("renderClouds:").append(options.renderClouds).append("\n");
        report.append("graphicsMode:").append(options.graphicsMode).append("\n");
        report.append("ambientOcclusion:").append(options.ambientOcclusion).append("\n");
        report.append("chatVisibility:").append(options.chatVisibility).append("\n");
        report.append("chatOpacity:").append(options.chatOpacity).append("\n");
        report.append("chatLineSpacing:").append(options.chatLineSpacing).append("\n");
        report.append("textBackgroundOpacity:").append(options.textBackgroundOpacity).append("\n");
        report.append("hideServerAddress:").append(options.hideServerAddress).append("\n");
        report.append("advancedItemTooltips:").append(options.advancedItemTooltips).append("\n");
        report.append("pauseOnLostFocus:").append(options.pauseOnLostFocus).append("\n");
        report.append("mainHand:").append(options.mainHand).append("\n");
        report.append("heldItemTooltips:").append(options.heldItemTooltips).append("\n");
        report.append("chatScale:").append(options.chatScale).append("\n");
        report.append("chatWidth:").append(options.chatWidth).append("\n");
        report.append("chatHeightUnfocused:").append(options.chatHeightUnfocused).append("\n");
        report.append("chatHeightFocused:").append(options.chatHeightFocused).append("\n");
        report.append("chatDelay:").append(options.chatDelay).append("\n");
        report.append("mipmapLevels:").append(options.mipmapLevels).append("\n");
        report.append("useNativeTransport:").append(options.useNativeTransport).append("\n");
        report.append("attackIndicator:").append(options.attackIndicator).append("\n");
        report.append("biomeBlendRadius:").append(options.biomeBlendRadius).append("\n");
        report.append("mouseWheelSensitivity:").append(options.mouseWheelSensitivity).append("\n");
        report.append("rawMouseInput:").append(options.rawMouseInput).append("\n");
        report.append("autoJump:").append(options.autoJump).append("\n");
        report.append("autoSuggestions:").append(options.autoSuggestions).append("\n");
        report.append("chatColors:").append(options.chatColors).append("\n");
        report.append("chatLinks:").append(options.chatLinks).append("\n");
        report.append("chatLinksPrompt:").append(options.chatLinksPrompt).append("\n");
        report.append("enableVsync:").append(options.enableVsync).append("\n");
        report.append("entityShadows:").append(options.entityShadows).append("\n");
        report.append("forceUnicodeFont:").append(options.forceUnicodeFont).append("\n");
        report.append("invertYMouse:").append(options.invertYMouse).append("\n");
        report.append("discreteMouseScroll:").append(options.discreteMouseScroll).append("\n");
        report.append("showSubtitles:").append(options.showSubtitles).append("\n");
        report.append("backgroundForChatOnly:").append(options.backgroundForChatOnly).append("\n");
        report.append("touchscreen:").append(options.touchscreen).append("\n");
        report.append("fullscreen:").append(options.fullscreen).append("\n");
        report.append("bobView:").append(options.bobView).append("\n");
        report.append("toggleCrouch:").append(options.toggleCrouch).append("\n");
        report.append("toggleSprint:").append(options.toggleSprint).append("\n");
        report.append("smoothCamera:").append(options.smoothCamera).append("\n");
        report.append("fov:").append(options.fov).append("\n");
        report.append("screenEffectScale:").append(options.screenEffectScale).append("\n");
        report.append("fovEffectScale:").append(options.fovEffectScale).append("\n");
        report.append("gamma:").append(options.gamma).append("\n");
        report.append("guiScale:").append(options.guiScale).append("\n");
        report.append("particles:").append(options.particles).append("\n");
        report.append("narratorStatus:").append(options.narratorStatus).append("\n");
        report.append("languageCode:").append(options.languageCode).append("\n");
        report.append("syncWrites:").append(options.syncWrites).append("\n");
        return report.toString();
    }
    public static int renderDistance(Options options) { return options.renderDistance; }
    public static String reportDiagnostic() { return "Public vanilla option fields and key mappings were captured; this native family has no complete options-report API and mod-owned screens require explicit adapters"; }
}
