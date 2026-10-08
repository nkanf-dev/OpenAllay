package dev.openallay.client.context;
import net.minecraft.client.settings.GameSettings;
/** Real public 1.12 options; private credentials and last-server fields are not read. */
public final class MinecraftClientOptionsFacts {
    private MinecraftClientOptionsFacts() {}
    public static String report(GameSettings options) {
        StringBuilder report = new StringBuilder();
        report.append("mouseSensitivity:").append(options.mouseSensitivity).append("\n");
        report.append("invertMouse:").append(options.invertMouse).append("\n");
        report.append("renderDistanceChunks:").append(options.renderDistanceChunks).append("\n");
        report.append("viewBobbing:").append(options.viewBobbing).append("\n");
        report.append("anaglyph:").append(options.anaglyph).append("\n");
        report.append("fboEnable:").append(options.fboEnable).append("\n");
        report.append("limitFramerate:").append(options.limitFramerate).append("\n");
        report.append("clouds:").append(options.clouds).append("\n");
        report.append("fancyGraphics:").append(options.fancyGraphics).append("\n");
        report.append("ambientOcclusion:").append(options.ambientOcclusion).append("\n");
        report.append("chatVisibility:").append(options.chatVisibility).append("\n");
        report.append("chatColours:").append(options.chatColours).append("\n");
        report.append("chatLinks:").append(options.chatLinks).append("\n");
        report.append("chatLinksPrompt:").append(options.chatLinksPrompt).append("\n");
        report.append("chatOpacity:").append(options.chatOpacity).append("\n");
        report.append("snooperEnabled:").append(options.snooperEnabled).append("\n");
        report.append("fullScreen:").append(options.fullScreen).append("\n");
        report.append("enableVsync:").append(options.enableVsync).append("\n");
        report.append("useVbo:").append(options.useVbo).append("\n");
        report.append("reducedDebugInfo:").append(options.reducedDebugInfo).append("\n");
        report.append("hideServerAddress:").append(options.hideServerAddress).append("\n");
        report.append("advancedItemTooltips:").append(options.advancedItemTooltips).append("\n");
        report.append("pauseOnLostFocus:").append(options.pauseOnLostFocus).append("\n");
        report.append("touchscreen:").append(options.touchscreen).append("\n");
        report.append("mainHand:").append(options.mainHand).append("\n");
        report.append("overrideWidth:").append(options.overrideWidth).append("\n");
        report.append("overrideHeight:").append(options.overrideHeight).append("\n");
        report.append("heldItemTooltips:").append(options.heldItemTooltips).append("\n");
        report.append("chatScale:").append(options.chatScale).append("\n");
        report.append("chatWidth:").append(options.chatWidth).append("\n");
        report.append("chatHeightUnfocused:").append(options.chatHeightUnfocused).append("\n");
        report.append("chatHeightFocused:").append(options.chatHeightFocused).append("\n");
        report.append("mipmapLevels:").append(options.mipmapLevels).append("\n");
        report.append("useNativeTransport:").append(options.useNativeTransport).append("\n");
        report.append("entityShadows:").append(options.entityShadows).append("\n");
        report.append("attackIndicator:").append(options.attackIndicator).append("\n");
        report.append("enableWeakAttacks:").append(options.enableWeakAttacks).append("\n");
        report.append("showSubtitles:").append(options.showSubtitles).append("\n");
        report.append("realmsNotifications:").append(options.realmsNotifications).append("\n");
        report.append("autoJump:").append(options.autoJump).append("\n");
        report.append("hideGUI:").append(options.hideGUI).append("\n");
        report.append("thirdPersonView:").append(options.thirdPersonView).append("\n");
        report.append("showDebugInfo:").append(options.showDebugInfo).append("\n");
        report.append("smoothCamera:").append(options.smoothCamera).append("\n");
        report.append("debugCamEnable:").append(options.debugCamEnable).append("\n");
        report.append("fovSetting:").append(options.fovSetting).append("\n");
        report.append("gammaSetting:").append(options.gammaSetting).append("\n");
        report.append("guiScale:").append(options.guiScale).append("\n");
        report.append("particleSetting:").append(options.particleSetting).append("\n");
        report.append("narrator:").append(options.narrator).append("\n");
        report.append("language:").append(options.language).append("\n");
        report.append("forceUnicodeFont:").append(options.forceUnicodeFont).append("\n");
        return report.toString();
    }
    public static int renderDistance(GameSettings options) { return options.renderDistanceChunks; }
    public static String reportDiagnostic() { return "Public vanilla option fields and key mappings were captured; mod-owned configuration screens require explicit adapters"; }
}
