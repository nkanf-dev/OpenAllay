package dev.openallay.client.gui.settings;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class VoiceSettingsScreenArchitectureTest {
    @Test
    void realVoiceActionsAreSeparateFromDisplaySaveAndAgentSubmission() throws Exception {
        String source = Files.readString(Path.of("src/main/java/dev/openallay/client/gui/OpenAllaySettingsScreen.java"));
        String voice = source.substring(source.indexOf("private void resetVoiceDraft"),
                source.indexOf("private void addModelsPage"));
        assertTrue(voice.contains("voiceActions.importModel(java.nio.file.Paths.get(voiceModelPath))"));
        assertTrue(voice.contains("voiceActions.importRuntime(java.nio.file.Paths.get(voiceRuntimePath))"));
        assertTrue(voice.contains("voiceActions.downloadDefaultModel()"));
        assertTrue(voice.contains("voiceActions::cancelDownload"));
        assertTrue(voice.contains("voiceActions.refreshDevices()"));
        assertTrue(voice.contains("voiceActions.update(submitted, dev.openallay.util.Java8Strings.isBlank(voiceApiKeyDraft)"));
        assertTrue(voice.contains("voiceApiKeyDraft.toCharArray()"));
        assertTrue(voice.contains("voiceButton(\"store_api_key\", MinecraftComponents.empty(), x, y, w, this::applyVoice)"));
        assertTrue(voice.contains("GuideNativeDialogs.openDirectory(voiceActions.runtimeNoticesDirectory())"));
        String dialogs = Files.readString(Path.of("src/main/java/dev/openallay/client/gui/GuideNativeDialogs.java"));
        assertTrue(dialogs.contains("public static void openDirectory(Path path)"));
        assertTrue(dialogs.contains("net.minecraft.util.Util.getPlatform().openPath(path)"));
        assertFalse(voice.contains("saveDisplay"));
        assertFalse(voice.contains("ProcessBuilder"));
        assertFalse(voice.contains("Runtime.getRuntime().exec"));
        assertFalse(voice.contains(".ask("));
        assertFalse(voice.contains("System.getenv"));
        assertFalse(voice.contains("voiceModelPath).open"));
        assertTrue(source.contains("void previewNotification(GuideUiConfig.Notifications config)"));
        assertTrue(source.contains("GuideUiConfig.Notifications notifications = uiDraft.ui().notifications()"));
    }

    @Test
    void gameplayActionControlOnlyChangesTheLocalVoiceDraft() throws Exception {
        String source = Files.readString(Path.of("src/main/java/dev/openallay/client/gui/OpenAllaySettingsScreen.java"));
        String control = source.substring(source.indexOf("voiceButton(\"gameplay_action\""),
                source.indexOf("voiceButton(\"backend\""));
        assertTrue(control.contains("screen.openallay.settings.voice.gameplay_action."));
        assertTrue(control.contains("voiceDraft.gameplayAction().name()"));
        assertTrue(control.contains("voiceDraft = voiceDraft.withGameplayAction("));
        assertTrue(control.contains("VoiceConfig.GameplayAction.SEND"));
        assertTrue(control.contains("VoiceConfig.GameplayAction.DRAFT"));
        assertTrue(control.contains("guideRebuildWidgets()"));
        assertFalse(control.contains("voiceActions"));
        assertFalse(control.contains("withEnabled"));
        assertFalse(control.contains("saveVoice"));
        assertFalse(control.contains("applyVoice"));
        assertFalse(control.contains(".ask("));
    }

    @Test
    void gameplayActionFollowsPublishedDraftResetAndCandidateReceiptOwnership() throws Exception {
        String source = Files.readString(Path.of("src/main/java/dev/openallay/client/gui/OpenAllaySettingsScreen.java"));
        String load = source.substring(source.indexOf("private void resetVoiceDraft("),
                source.indexOf("private void addVoicePage("));
        assertTrue(load.contains("voiceDraft = voiceView.config()"));
        String reset = source.substring(source.indexOf("private void resetVoiceDefaults("),
                source.indexOf("private void addSectionNavigation("));
        assertTrue(reset.contains("voiceDraft.credential(),\n                defaults.gameplayAction()"));
        assertTrue(reset.contains("voiceDraft.nativeModelDirectory()"));
        assertFalse(reset.contains("voiceActions.update"));
        assertFalse(reset.contains("setApiKey"));
        String save = source.substring(source.indexOf("private void saveVoice("),
                source.indexOf("private String voiceFieldValue("));
        assertTrue(save.contains("candidate.credential(), candidate.gameplayAction()"));
        assertFalse(save.contains("VoiceConfig.defaults()"));
        assertTrue(save.contains("saveEditor(() -> voiceActions.update(submitted"));
        assertTrue(save.contains("result -> resetVoiceDraft()"));
        String refresh = source.substring(source.indexOf("private void acceptVoice("),
                source.indexOf("private net.minecraft.network.chat.Component voiceSettingsStatus("));
        assertTrue(refresh.contains(".withGameplayAction(previous.gameplayAction())"));
        String receipt = source.substring(source.indexOf("private void saveEditor("),
                source.indexOf("private void updateEditorSaveControls("));
        assertTrue(receipt.contains("= result) instanceof dev.openallay.tool.ToolResult.Failure"));
        assertTrue(receipt.contains("} else {\n                localNotice = \"\";\n                committed.accept(result)"));
        String render = source.substring(source.indexOf("private void renderVoice("),
                source.indexOf("private void addModelsPage("));
        assertTrue(render.contains("8 * 26"), "The added control must move the field labels too");
        assertTrue(render.contains("? 474 : 386"), "Description must stay below both backend forms");
        assertTrue(render.contains("screen.openallay.settings.voice.gameplay_action.description"));
        assertFalse(render.contains("screen.openallay.settings.voice.description"), "Do not show stale draft-only guidance");
    }
}
