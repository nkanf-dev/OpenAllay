package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.client.gui.settings.ModelProfileDraft;
import dev.openallay.client.gui.settings.SettingsSection;
import dev.openallay.client.gui.settings.UiSettingsDraft;
import dev.openallay.client.voice.AudioCapture;
import dev.openallay.client.voice.VoiceConfig;
import dev.openallay.client.voice.VoiceSettingsActions;
import dev.openallay.client.voice.VoiceSettingsView;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideUiConfig;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.config.CredentialReference;
import dev.openallay.model.config.ModelProfileDefinition;
import dev.openallay.model.config.ModelProfilesConfig;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.ResolvedModelProfile;
import dev.openallay.model.config.SecretValue;
import dev.openallay.model.metadata.ModelMetadata;
import dev.openallay.settings.ClientSettingsService;
import dev.openallay.settings.SettingsOperation;
import dev.openallay.settings.capability.CapabilitySettingsView;
import dev.openallay.settings.capability.RecipeSettingsView;
import dev.openallay.settings.model.ModelConnectionResult;
import dev.openallay.settings.model.ModelProfileSettingsView;
import dev.openallay.tool.ToolResult;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Explicit dev-only native Screen scenarios with manually acknowledged synthetic settings workers.
 * Not a JUnit suite. Requires a real initialized graphical client; never add this to a packaged mod.
 */
public final class OpenAllaySettingsScreenDoneScenarios {

    public enum Status { PASS, FAIL }
    public record ScenarioResult(String name, Status status, String detail) {}

    /** Invoke only from the actual graphical client's dev test classpath on its client thread. */
    public static List<ScenarioResult> runOnClient() {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.font == null || !client.isSameThread()) {
            throw new IllegalStateException("Native settings scenarios require an initialized graphical client thread");
        }
        var scenarios = new OpenAllaySettingsScreenDoneScenarios();
        var results = new java.util.ArrayList<ScenarioResult>();
        run(results, "escapeAndBackUseTheSameDirtySaveAndCannotCloseOnFailedWrite", scenarios::escapeAndBackUseTheSameDirtySaveAndCannotCloseOnFailedWrite);
        run(results, "uiAndVoicePrimaryFooterOnlyContainsApplyResetAndDoneAndResetDoesNotWrite", scenarios::uiAndVoicePrimaryFooterOnlyContainsApplyResetAndDoneAndResetDoesNotWrite);
        run(results, "generalDoneWaitsForWriteAndDispatchedReceiptAndDoesNotRepeatASecondClick", scenarios::generalDoneWaitsForWriteAndDispatchedReceiptAndDoesNotRepeatASecondClick);
        run(results, "invalidAndFailedGeneralDoneKeepTheDraftAndViewUntilSuccessfulRetry", scenarios::invalidAndFailedGeneralDoneKeepTheDraftAndViewUntilSuccessfulRetry);
        run(results, "eachUiGroupDonePersistsItsCandidateAndKeepsOtherGroupsAndGeneralFields", scenarios::eachUiGroupDonePersistsItsCandidateAndKeepsOtherGroupsAndGeneralFields);
        run(results, "invalidUiOffsetAndWriteFailureRemainOnUiWithAllUnsavedGroups", scenarios::invalidUiOffsetAndWriteFailureRemainOnUiWithAllUnsavedGroups);
        run(results, "failedRenamedModelAndKeyDoNotCommitSelectionOrClearKeyAndRetryDoesNotDuplicate", scenarios::failedRenamedModelAndKeyDoNotCommitSelectionOrClearKeyAndRetryDoesNotDuplicate);
        run(results, "listenerPublicationDuringSuccessfulRenameDoesNotResetToAnotherProfileBeforeTheReceipt", scenarios::listenerPublicationDuringSuccessfulRenameDoesNotResetToAnotherProfileBeforeTheReceipt);
        run(results, "invalidModelDoneDoesNotProbeOrWriteAndKeepsFields", scenarios::invalidModelDoneDoesNotProbeOrWriteAndKeepsFields);
        run(results, "explicitModelSaveThenDoneDoesNotInsertTheSameSecretOrWriteTwice", scenarios::explicitModelSaveThenDoneDoesNotInsertTheSameSecretOrWriteTwice);
        run(results, "voiceDoneCommitsCurrentDeviceHttpAndCredentialAsOneCandidateOnlyAfterAck", scenarios::voiceDoneCommitsCurrentDeviceHttpAndCredentialAsOneCandidateOnlyAfterAck);
        run(results, "invalidVoiceAndFailedCredentialSaveKeepDeviceUrlModelAndSecretForRetry", scenarios::invalidVoiceAndFailedCredentialSaveKeepDeviceUrlModelAndSecretForRetry);
        run(results, "nativeTypedDirectoryReachesSaveCandidateWithoutImportDownloadOrDeviceOpen", scenarios::nativeTypedDirectoryReachesSaveCandidateWithoutImportDownloadOrDeviceOpen);
        run(results, "cleanAndImmediateTogglePagesDoNotWriteAgainOnDone", scenarios::cleanAndImmediateTogglePagesDoNotWriteAgainOnDone);
        run(results, "returnedHudEditorDragIgnoresOldOffsetWidgetsAndRetainsFailedCandidateForDoneRetry",
                scenarios::returnedHudEditorDragIgnoresOldOffsetWidgetsAndRetainsFailedCandidateForDoneRetry);
        run(results, "hudChildReturnUsesServiceGeneralPublishedWhileParentIsDetached",
                scenarios::hudChildReturnUsesServiceGeneralPublishedWhileParentIsDetached);
        return List.copyOf(results);
    }

    @FunctionalInterface
    private interface Scenario { void run() throws Exception; }
    private static void run(List<ScenarioResult> results, String name, Scenario scenario) {
        try {
            scenario.run();
            results.add(new ScenarioResult(name, Status.PASS, "Synthetic save receipt assertions passed in native client"));
        } catch (Exception | AssertionError failure) {
            results.add(new ScenarioResult(name, Status.FAIL, failure.getClass().getSimpleName()
                    + (failure.getMessage() == null ? "" : ": " + failure.getMessage())));
        }
    }

    void escapeAndBackUseTheSameDirtySaveAndCannotCloseOnFailedWrite() throws Exception {
        for (boolean escape : new boolean[] {true, false}) {
            Fixture f = new Fixture();
            set(f.screen, "assistantNameDraft", "Draft from exit");
            f.display.fail = true;
            if (escape) assertTrue(f.screen.keyPressed(GuideNativeInput.keyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE, 0)));
            else invoke(f.screen, "backOrClose");
            assertEquals(0, f.closed);
            assertEquals(1, f.worker.tasks.size());
            f.ack();
            assertEquals(0, f.closed);
            assertEquals("Draft from exit", get(f.screen, "assistantNameDraft"));
            f.display.fail = false;
            if (escape) f.screen.keyPressed(GuideNativeInput.keyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE, 0));
            else invoke(f.screen, "backOrClose");
            f.ack();
            assertEquals(1, f.closed);
        }
    }

    void uiAndVoicePrimaryFooterOnlyContainsApplyResetAndDoneAndResetDoesNotWrite() throws Exception {
        Fixture f = new Fixture();
        set(f.screen, "section", SettingsSection.UI);
        assertEquals(List.of("screen.openallay.settings.ui.apply", "screen.openallay.settings.ui.reset",
                "screen.openallay.settings.done"), footerKeys(f.screen));
        UiSettingsDraft draft = (UiSettingsDraft) get(f.screen, "uiDraft");
        draft.preview(draft.ui().withFullscreen(new GuideUiConfig.Fullscreen(
                GuideUiConfig.Density.COMPACT, false, GuideUiConfig.Theme.MINT)));
        invoke(f.screen, "resetUiGroup");
        assertEquals(GuideUiConfig.Fullscreen.defaults(), draft.ui().fullscreen());
        assertEquals(0, f.worker.tasks.size());
        FakeVoice voice = new FakeVoice(f.worker);
        var credential = CredentialReference.local(java.util.UUID.randomUUID());
        voice.config = VoiceConfig.defaults().withCredential(credential).withModelDirectory(Path.of("saved-model"))
                .withGameplayAction(VoiceConfig.GameplayAction.DRAFT);
        f.screen.withVoiceActions(voice);
        assertEquals(VoiceConfig.GameplayAction.DRAFT, ((VoiceConfig) get(f.screen, "voiceDraft")).gameplayAction());
        set(f.screen, "section", SettingsSection.VOICE);
        set(f.screen, "voiceDraft", voice.config.withBackend(VoiceConfig.Backend.HTTP).withEnabled(true));
        assertEquals(List.of("screen.openallay.settings.voice.apply", "screen.openallay.settings.voice.reset",
                "screen.openallay.settings.done"), footerKeys(f.screen));
        invoke(f.screen, "resetVoiceDefaults");
        var reset = (VoiceConfig) get(f.screen, "voiceDraft");
        assertEquals(credential, reset.credential());
        assertEquals(voice.config.nativeModelDirectory(), reset.nativeModelDirectory());
        assertFalse(reset.enabled());
        assertEquals(VoiceConfig.GameplayAction.SEND, reset.gameplayAction());
        assertEquals(VoiceConfig.GameplayAction.DRAFT, voice.config.gameplayAction(), "Reset only edits the local draft");
        assertEquals(0, voice.updates);
        assertEquals(0, f.worker.tasks.size());
    }

    @SuppressWarnings("unchecked")
    private static List<String> footerKeys(OpenAllaySettingsScreen screen) throws Exception {
        var method = OpenAllaySettingsScreen.class.getDeclaredMethod("footerActions");
        method.setAccessible(true);
        var actions = (List<Object>) method.invoke(screen);
        var keys = new java.util.ArrayList<String>();
        for (Object action : actions) {
            var key = action.getClass().getDeclaredMethod("translationKey");
            key.setAccessible(true); keys.add((String) key.invoke(action));
        }
        return keys;
    }

    void generalDoneWaitsForWriteAndDispatchedReceiptAndDoesNotRepeatASecondClick() throws Exception {
        Fixture f = new Fixture();
        set(f.screen, "assistantNameDraft", "Player guide");
        f.screen.onClose();
        f.screen.onClose();
        assertEquals(0, f.closed);
        assertEquals(SettingsOperation.Kind.SAVING_DISPLAY, f.service.snapshot().operation().kind());
        assertEquals(1, f.worker.tasks.size());
        assertEquals(0, f.display.saves);
        f.worker.runAll();
        assertEquals(1, f.display.saves);
        assertEquals("Player guide", f.display.current.assistantName());
        assertEquals(0, f.closed, "Returning before the client receipt is dispatched loses save ownership");
        f.client.runAll();
        assertEquals(1, f.closed);
        assertEquals("Player guide", f.service.snapshot().display().assistantName());
    }

    void invalidAndFailedGeneralDoneKeepTheDraftAndViewUntilSuccessfulRetry() throws Exception {
        Fixture f = new Fixture();
        set(f.screen, "assistantNameDraft", "   ");
        f.screen.onClose();
        assertEquals(0, f.closed);
        assertEquals(0, f.worker.tasks.size());
        assertEquals("   ", get(f.screen, "assistantNameDraft"));
        set(f.screen, "assistantNameDraft", "Retry name");
        f.display.fail = true;
        f.screen.onClose(); f.ack();
        assertEquals(0, f.closed);
        assertEquals(GuideDisplayConfig.defaults(), f.service.snapshot().display());
        assertEquals("Retry name", get(f.screen, "assistantNameDraft"));
        assertEquals(SettingsSection.GENERAL, get(f.screen, "section"));
        assertEquals("Unable to save settings", get(f.screen, "localNotice"));
        f.display.fail = false;
        f.screen.onClose(); f.ack();
        assertEquals(1, f.closed);
        assertEquals(2, f.display.saves);
    }

    void eachUiGroupDonePersistsItsCandidateAndKeepsOtherGroupsAndGeneralFields() throws Exception {
        for (var group : dev.openallay.client.gui.settings.UiSettingsProjection.Group.values()) {
            Fixture f = new Fixture();
            set(f.screen, "section", SettingsSection.UI);
            UiSettingsDraft draft = (UiSettingsDraft) get(f.screen, "uiDraft");
            var ui = draft.ui();
            draft.preview(switch (group) {
                case FULLSCREEN -> ui.withFullscreen(new GuideUiConfig.Fullscreen(
                        GuideUiConfig.Density.COMPACT, false, GuideUiConfig.Theme.MINT));
                case HUD -> ui.withHud(ui.hud().withEnabled(true).withBackgroundOpacity(.2));
                case NOTIFICATIONS -> ui.withNotifications(ui.notifications().withEnabled(true));
            });
            var expected = draft.candidate(f.service.snapshot().display());
            f.screen.onClose(); f.screen.onClose();
            assertEquals(0, f.closed);
            f.ack();
            assertEquals(1, f.closed);
            assertEquals(1, f.display.saves);
            assertEquals(expected, f.service.snapshot().display());
            assertFalse(draft.dirty(), "A successful receipt commits UI preview");
        }
    }

    @SuppressWarnings("unchecked")
    void invalidUiOffsetAndWriteFailureRemainOnUiWithAllUnsavedGroups() throws Exception {
        Fixture f = new Fixture();
        set(f.screen, "section", SettingsSection.UI);
        UiSettingsDraft draft = (UiSettingsDraft) get(f.screen, "uiDraft");
        draft.preview(draft.ui().withNotifications(draft.ui().notifications().withEnabled(true)));
        Map<String, String> raw = (Map<String, String>) get(f.screen, "uiIntegerDrafts");
        raw.put("offset_x", "-");
        f.screen.onClose();
        assertEquals(0, f.worker.tasks.size());
        assertEquals("-", raw.get("offset_x"));
        assertEquals(0, f.closed);
        raw.put("offset_x", "4097");
        f.screen.onClose();
        assertEquals(0, f.worker.tasks.size());
        raw.put("offset_x", "23");
        f.display.fail = true;
        f.screen.onClose(); f.ack();
        assertEquals(0, f.closed);
        assertEquals(SettingsSection.UI, get(f.screen, "section"));
        assertEquals("23", raw.get("offset_x"));
        assertTrue(draft.ui().notifications().enabled());
        assertTrue(draft.dirty());
        assertEquals(GuideDisplayConfig.defaults(), f.service.snapshot().display());
        f.display.fail = false;
        f.screen.onClose(); f.ack();
        assertEquals(1, f.closed);
        assertEquals(23, f.service.snapshot().display().ui().hud().offsetX());
    }

    void failedRenamedModelAndKeyDoNotCommitSelectionOrClearKeyAndRetryDoesNotDuplicate() throws Exception {
        Fixture f = new Fixture();
        set(f.screen, "section", SettingsSection.MODELS);
        ModelProfileDefinition renamed = profile("renamed");
        set(f.screen, "draft", ModelProfileDraft.from(renamed));
        set(f.screen, "pendingApiKey", "uncommitted-model-key");
        f.models.fail = true;
        f.screen.onClose(); f.screen.onClose();
        assertEquals("alpha", get(f.screen, "selectedProfileId"));
        assertEquals("uncommitted-model-key", get(f.screen, "pendingApiKey"));
        f.ack();
        assertEquals(0, f.closed);
        assertEquals("alpha", get(f.screen, "selectedProfileId"));
        assertEquals("uncommitted-model-key", get(f.screen, "pendingApiKey"));
        assertEquals("renamed", ((ModelProfileDraft) get(f.screen, "draft")).id());
        assertEquals(List.of("alpha"), f.service.snapshot().models().config().profiles().stream()
                .map(ModelProfileDefinition::id).toList());
        f.models.fail = false;
        f.screen.onClose(); f.ack();
        assertEquals(1, f.closed);
        assertEquals(2, f.models.saves);
        assertEquals("renamed", get(f.screen, "selectedProfileId"));
        assertEquals("", get(f.screen, "pendingApiKey"));
        assertEquals(List.of("renamed"), f.service.snapshot().models().config().profiles().stream()
                .map(ModelProfileDefinition::id).toList());
        assertEquals("renamed", f.models.replacementId);
        assertNotNull(f.models.replacement);
    }

    void listenerPublicationDuringSuccessfulRenameDoesNotResetToAnotherProfileBeforeTheReceipt() throws Exception {
        Fixture f = new Fixture();
        f.screen.added();
        set(f.screen, "section", SettingsSection.MODELS);
        set(f.screen, "draft", ModelProfileDraft.from(profile("renamed")));
        set(f.screen, "pendingApiKey", "retain-until-ack");
        f.screen.onClose();
        f.worker.runAll();
        assertEquals("alpha", get(f.screen, "selectedProfileId"));
        assertEquals("retain-until-ack", get(f.screen, "pendingApiKey"));
        assertEquals(0, f.closed);
        f.client.runAll();
        assertEquals("renamed", get(f.screen, "selectedProfileId"));
        assertEquals("", get(f.screen, "pendingApiKey"));
        assertEquals(1, f.closed);
    }

    void invalidModelDoneDoesNotProbeOrWriteAndKeepsFields() throws Exception {
        Fixture f = new Fixture();
        set(f.screen, "section", SettingsSection.MODELS);
        var invalid = ModelProfileDraft.create("new-profile");
        set(f.screen, "draft", invalid);
        set(f.screen, "selectedProfileId", null);
        set(f.screen, "pendingApiKey", "still-editing");
        f.screen.onClose();
        assertEquals(0, f.worker.tasks.size());
        assertEquals(0, f.closed);
        assertEquals(invalid, get(f.screen, "draft"));
        assertEquals("still-editing", get(f.screen, "pendingApiKey"));
    }

    void explicitModelSaveThenDoneDoesNotInsertTheSameSecretOrWriteTwice() throws Exception {
        Fixture f = new Fixture();
        set(f.screen, "section", SettingsSection.MODELS);
        set(f.screen, "pendingApiKey", "replace-once");
        invoke(f.screen, "saveCurrent");
        invoke(f.screen, "saveCurrent");
        f.ack();
        assertEquals(0, f.closed);
        assertEquals(1, f.models.saves);
        assertEquals("", get(f.screen, "pendingApiKey"));
        f.screen.onClose();
        assertEquals(1, f.closed);
        assertEquals(1, f.models.saves);
    }

    void voiceDoneCommitsCurrentDeviceHttpAndCredentialAsOneCandidateOnlyAfterAck() throws Exception {
        Fixture f = new Fixture();
        FakeVoice voice = new FakeVoice(f.worker);
        f.screen.withVoiceActions(voice);
        set(f.screen, "section", SettingsSection.VOICE);
        set(f.screen, "voiceDraft", voice.config.withBackend(VoiceConfig.Backend.HTTP)
                .withDevice("chosen-device").withEnabled(true).withLanguage("en")
                .withGameplayAction(VoiceConfig.GameplayAction.DRAFT));
        set(f.screen, "voiceHttpUrl", "https://voice.example/v1");
        set(f.screen, "voiceHttpModel", "chosen-http-model");
        set(f.screen, "voiceApiKeyDraft", "uncommitted-voice-key");
        f.screen.onClose(); f.screen.onClose();
        assertEquals(1, voice.updates);
        assertEquals(0, f.closed);
        assertEquals("uncommitted-voice-key", get(f.screen, "voiceApiKeyDraft"));
        assertEquals(VoiceConfig.GameplayAction.DRAFT, ((VoiceConfig) get(f.screen, "voiceDraft")).gameplayAction());
        assertEquals(VoiceConfig.GameplayAction.SEND, voice.config.gameplayAction(), "No save before the worker receipt");
        f.worker.runAll();
        assertEquals(0, f.closed);
        f.client.runAll();
        assertEquals(1, f.closed);
        assertEquals("chosen-device", voice.config.deviceId());
        assertEquals(URI.create("https://voice.example/v1"), voice.config.httpBaseUrl());
        assertEquals("chosen-http-model", voice.config.httpModel());
        assertEquals(VoiceConfig.GameplayAction.DRAFT, voice.config.gameplayAction());
        assertNotNull(voice.config.credential());
        assertEquals("", get(f.screen, "voiceApiKeyDraft"));
        assertEquals(0, f.display.saves);
        assertEquals(0, f.models.saves);
    }

    void invalidVoiceAndFailedCredentialSaveKeepDeviceUrlModelAndSecretForRetry() throws Exception {
        Fixture f = new Fixture();
        FakeVoice voice = new FakeVoice(f.worker);
        f.screen.withVoiceActions(voice);
        set(f.screen, "section", SettingsSection.VOICE);
        set(f.screen, "voiceDraft", voice.config.withBackend(VoiceConfig.Backend.HTTP).withDevice("draft-device")
                .withGameplayAction(VoiceConfig.GameplayAction.DRAFT));
        set(f.screen, "voiceHttpUrl", "not-a-url");
        set(f.screen, "voiceApiKeyDraft", "retry-key");
        f.screen.onClose();
        assertEquals(0, voice.updates);
        assertEquals(0, f.closed);
        assertEquals("not-a-url", get(f.screen, "voiceHttpUrl"));
        assertEquals(VoiceConfig.GameplayAction.DRAFT, ((VoiceConfig) get(f.screen, "voiceDraft")).gameplayAction());
        set(f.screen, "voiceHttpUrl", "https://retry.example/v1");
        set(f.screen, "voiceHttpModel", "retry-model");
        voice.fail = true;
        f.screen.onClose(); f.ack();
        assertEquals(0, f.closed);
        assertEquals(SettingsSection.VOICE, get(f.screen, "section"));
        assertEquals("https://retry.example/v1", get(f.screen, "voiceHttpUrl"));
        assertEquals("retry-model", get(f.screen, "voiceHttpModel"));
        assertEquals("retry-key", get(f.screen, "voiceApiKeyDraft"));
        assertEquals("draft-device", ((VoiceConfig) get(f.screen, "voiceDraft")).deviceId());
        assertEquals(VoiceConfig.GameplayAction.DRAFT, ((VoiceConfig) get(f.screen, "voiceDraft")).gameplayAction());
        assertEquals(VoiceConfig.defaults(), voice.config);
        voice.fail = false;
        f.screen.onClose(); f.ack();
        assertEquals(1, f.closed);
        assertEquals(2, voice.updates);
        assertEquals(VoiceConfig.GameplayAction.DRAFT, voice.config.gameplayAction());
    }

    void nativeTypedDirectoryReachesSaveCandidateWithoutImportDownloadOrDeviceOpen() throws Exception {
        Fixture f = new Fixture();
        FakeVoice voice = new FakeVoice(f.worker);
        f.screen.withVoiceActions(voice);
        set(f.screen, "section", SettingsSection.VOICE);
        set(f.screen, "voiceModelPath", "typed-data-only-model");
        f.screen.onClose(); f.ack();
        assertEquals(1, voice.updates);
        assertEquals(Path.of("typed-data-only-model").toAbsolutePath().normalize().toString(),
                voice.config.nativeModelDirectory());
        assertEquals(1, f.closed);
    }

    void cleanAndImmediateTogglePagesDoNotWriteAgainOnDone() throws Exception {
        for (SettingsSection section : SettingsSection.values()) {
            Fixture f = new Fixture();
            if (section == SettingsSection.VOICE) f.screen.withVoiceActions(new FakeVoice(f.worker));
            set(f.screen, "section", section);
            f.screen.onClose();
            assertEquals(1, f.closed, section.name());
            assertEquals(0, f.worker.tasks.size(), section.name());
            assertEquals(0, f.display.saves);
            assertEquals(0, f.models.saves);
        }
    }

    void returnedHudEditorDragIgnoresOldOffsetWidgetsAndRetainsFailedCandidateForDoneRetry() throws Exception {
        Fixture f = new Fixture();
        set(f.screen, "section", SettingsSection.UI);
        set(f.screen, "uiGroup", dev.openallay.client.gui.settings.UiSettingsProjection.Group.HUD);
        UiSettingsDraft draft = (UiSettingsDraft) get(f.screen, "uiDraft");
        draft.preview(draft.ui().withFullscreen(new GuideUiConfig.Fullscreen(
                GuideUiConfig.Density.COMPACT, false, GuideUiConfig.Theme.MINT))
                .withNotifications(draft.ui().notifications().withEnabled(true)));
        var font = net.minecraft.client.Minecraft.getInstance().font;
        var oldX = new net.minecraft.client.gui.components.EditBox(font, 0, 0, 80, 20,
                net.minecraft.network.chat.Component.literal("Old offset X"));
        var oldY = new net.minecraft.client.gui.components.EditBox(font, 0, 24, 80, 20,
                net.minecraft.network.chat.Component.literal("Old offset Y"));
        oldX.setValue("17"); oldY.setValue("29");
        @SuppressWarnings("unchecked")
        Map<String, net.minecraft.client.gui.components.EditBox> fields =
                (Map<String, net.minecraft.client.gui.components.EditBox>) get(f.screen, "uiIntegerFields");
        fields.put("offset_x", oldX); fields.put("offset_y", oldY);
        @SuppressWarnings("unchecked")
        Map<String, String> raw = (Map<String, String>) get(f.screen, "uiIntegerDrafts");
        raw.put("offset_x", "17"); raw.put("offset_y", "29");
        var returned = draft.candidate(f.service.snapshot().display()).withUi(draft.ui().withHud(
                draft.ui().hud().withPlacement(GuideUiConfig.Anchor.BOTTOM_RIGHT, -144, -72, 352, 208, 1.2)));
        f.screen.added(); // Reservation publications capture the old parent fields before child return/init.
        f.display.fail = true;
        var apply = OpenAllaySettingsScreen.class.getDeclaredMethod("applyUiCandidate", GuideDisplayConfig.class);
        apply.setAccessible(true); apply.invoke(f.screen, returned);
        assertEquals("-144", oldX.getValue());
        assertEquals("-72", oldY.getValue());
        f.ack();
        assertEquals(0, f.closed);
        assertEquals(GuideDisplayConfig.defaults(), f.service.snapshot().display());
        assertEquals(returned.ui(), draft.ui());
        assertEquals("-144", raw.get("offset_x"));
        assertEquals("-72", raw.get("offset_y"));
        f.display.fail = false;
        f.screen.onClose();
        f.worker.runAll();
        assertEquals(0, f.closed);
        f.client.runAll();
        assertEquals(1, f.closed);
        assertEquals(returned.ui(), f.service.snapshot().display().ui());
        assertEquals(2, f.display.saves);
    }

    void hudChildReturnUsesServiceGeneralPublishedWhileParentIsDetached() throws Exception {
        Fixture f = new Fixture();
        set(f.screen, "section", SettingsSection.UI);
        GuideDisplayConfig parentAtOpen = f.service.snapshot().display();
        var returned = parentAtOpen.withUi(parentAtOpen.ui().withHud(parentAtOpen.ui().hud()
                .withPlacement(GuideUiConfig.Anchor.BOTTOM_RIGHT, -188, -84, 352, 208, 1.2)))
                .withAnimationsEnabled(false);
        var independent = parentAtOpen.withAssistantName("Published while child is open").withDebugMode(true);
        var otherSave = f.service.saveDisplay(independent);
        f.worker.runAll();
        assertInstanceOf(ToolResult.Success.class, otherSave.join());
        assertEquals(parentAtOpen, ((dev.openallay.settings.ClientSettingsSnapshot) get(f.screen, "snapshot")).display());
        var apply = OpenAllaySettingsScreen.class.getDeclaredMethod("applyUiCandidate", GuideDisplayConfig.class);
        apply.setAccessible(true); apply.invoke(f.screen, returned);
        f.worker.runAll();
        assertEquals(0, f.closed);
        f.client.runAll();
        assertEquals(independent.assistantName(), f.service.snapshot().display().assistantName());
        assertTrue(f.service.snapshot().display().debugMode());
        assertEquals(returned.ui(), f.service.snapshot().display().ui());
        assertFalse(f.service.snapshot().display().animationsEnabled());
        assertEquals(0, f.closed);
        assertEquals(2, f.display.saves);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static void invoke(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(target);
    }
    private static <T> T forbidden(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> { throw new AssertionError("Done must not call " + type.getSimpleName() + "." + method.getName()); }));
    }
    private static ModelProfileDefinition profile(String id) {
        return new ModelProfileDefinition(id, id, true, ModelProtocol.OPENAI_CHAT,
                URI.create("https://model.example/v1"), "unknown/model", "env:TEST_KEY", 256_000, 4096,
                Duration.ofSeconds(30), Duration.ofSeconds(300), null);
    }
    private static ClientSettingsService.ModelState state(ModelProfilesConfig config) {
        return new ClientSettingsService.ModelState(config, config.profiles().stream()
                .map(p -> new ModelProfileSettingsView.Resolution(p, true, true,
                        p.contextWindowTokens(), p.maxOutputTokens(), null)).toList());
    }
    private static final class Fixture {
        final ManualExecutor worker = new ManualExecutor();
        final ManualExecutor client = new ManualExecutor();
        final FakeDisplay display = new FakeDisplay();
        final FakeModels models = new FakeModels();
        final ClientSettingsService service = new ClientSettingsService(display.current, display, models.current,
                Set.of(), models, models, CapabilitySettingsView.defaults(),
                forbidden(ClientSettingsService.CapabilityActions.class), RecipeSettingsView.defaults(),
                forbidden(ClientSettingsService.RecipeActions.class), new ClientSettingsService.HistoryActions() {
                    public ClientSettingsService.HistoryRuntimeState state() { return ClientSettingsService.HistoryRuntimeState.disconnected(); }
                    public CompletableFuture<ToolResult<Boolean>> deleteCurrentHistory() { throw new AssertionError("history mutation"); }
                    public CompletableFuture<ToolResult<Boolean>> deleteActorHistory() { throw new AssertionError("history mutation"); }
                    public CompletableFuture<ToolResult<Boolean>> resetHistoryDatabase() { throw new AssertionError("history mutation"); }
                }, Runnable::run, worker, null);
        int closed;
        final OpenAllaySettingsScreen screen = new OpenAllaySettingsScreen(service, () -> closed++, client);
        void ack() { worker.runAll(); client.runAll(); }
    }
    private static final class FakeDisplay implements ClientSettingsService.DisplayActions {
        GuideDisplayConfig current = GuideDisplayConfig.defaults();
        boolean fail; int saves;
        public ToolResult<GuideDisplayConfig> saveDisplay(GuideDisplayConfig candidate) {
            saves++;
            if (fail) return new ToolResult.Failure<>("settings_write_failed", "Unable to save settings");
            current = candidate; return new ToolResult.Success<>(current);
        }
        public ToolResult<GuideDisplayConfig> reloadDisplay() { throw new AssertionError("reload"); }
    }
    private static final class FakeModels implements ClientSettingsService.ModelActions, ClientSettingsService.MetadataActions {
        ClientSettingsService.ModelState current = state(new ModelProfilesConfig("alpha", List.of(profile("alpha"))));
        boolean fail; int saves; String replacementId; SecretValue replacement;
        public ToolResult<ClientSettingsService.ModelState> save(ModelProfilesConfig candidate, Map<ModelMetadata.Key, ModelMetadata> metadata) {
            return save(candidate, null, null, metadata);
        }
        public ToolResult<ClientSettingsService.ModelState> save(ModelProfilesConfig candidate, String id, SecretValue key, Map<ModelMetadata.Key, ModelMetadata> metadata) {
            saves++; replacementId = id; replacement = key;
            if (fail) return new ToolResult.Failure<>("settings_write_failed", "Unable to save models");
            current = state(candidate); return new ToolResult.Success<>(current);
        }
        public ToolResult<ClientSettingsService.ModelState> reload(Map<ModelMetadata.Key, ModelMetadata> metadata) { throw new AssertionError("reload"); }
        public ToolResult<ResolvedModelProfile> resolve(ModelProfileDefinition candidate, Map<ModelMetadata.Key, ModelMetadata> metadata) { throw new AssertionError("resolve/probe"); }
        public ToolResult<ClientSettingsService.PreparedModels> prepare(ModelProfilesConfig candidate, Map<ModelMetadata.Key, ModelMetadata> metadata) { throw new AssertionError("prepare"); }
        public CompletableFuture<ModelConnectionResult> probe(ResolvedModelProfile profile, CancellationSignal cancellation) { throw new AssertionError("network probe"); }
        public CompletableFuture<Void> refresh() { throw new AssertionError("metadata refresh"); }
        public CompletableFuture<Void> closeAsync() { return CompletableFuture.completedFuture(null); }
    }
    private static final class FakeVoice implements VoiceSettingsActions {
        final ManualExecutor worker; VoiceConfig config = VoiceConfig.defaults(); boolean fail; int updates;
        FakeVoice(ManualExecutor worker) { this.worker = worker; }
        public VoiceSettingsView view() { return new VoiceSettingsView(config, List.of(), false, false, "", "idle", 0, 0); }
        public Path runtimeNoticesDirectory() { throw new AssertionError("runtime action"); }
        public CompletableFuture<ToolResult<VoiceConfig>> update(VoiceConfig candidate) { return update(candidate, null); }
        public CompletableFuture<ToolResult<VoiceConfig>> update(VoiceConfig candidate, char[] key) {
            updates++;
            boolean replacing = key != null;
            if (key != null) Arrays.fill(key, '\0');
            var receipt = new CompletableFuture<ToolResult<VoiceConfig>>();
            worker.execute(() -> {
                if (fail) receipt.complete(new ToolResult.Failure<>("voice_save_failed", "Unable to save voice settings"));
                else {
                    config = replacing ? candidate.withCredential(CredentialReference.local(java.util.UUID.randomUUID())) : candidate;
                    receipt.complete(new ToolResult.Success<>(config));
                }
            });
            return receipt;
        }
        public CompletableFuture<ToolResult<VoiceConfig>> reload() { throw new AssertionError("reload"); }
        public CompletableFuture<ToolResult<VoiceConfig>> importModel(Path path) { throw new AssertionError("import model"); }
        public CompletableFuture<ToolResult<VoiceConfig>> importRuntime(Path path) { throw new AssertionError("import runtime"); }
        public CompletableFuture<ToolResult<VoiceConfig>> downloadDefaultModel() { throw new AssertionError("download model"); }
        public void cancelDownload() { throw new AssertionError("cancel download"); }
        public CompletableFuture<ToolResult<VoiceConfig>> setApiKey(char[] key) { throw new AssertionError("old config credential save"); }
        public CompletableFuture<ToolResult<List<AudioCapture.Device>>> refreshDevices() { throw new AssertionError("microphone/device action"); }
    }
    private static final class ManualExecutor implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        public void execute(Runnable task) { tasks.add(task); }
        void runAll() { while (!tasks.isEmpty()) tasks.removeFirst().run(); }
    }
}
