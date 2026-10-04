package dev.openallay.client.gui;

import dev.openallay.client.gui.hud.GuideChatLiteScreen;
import dev.openallay.client.gui.hud.GuideHudEditorScreen;
import dev.openallay.client.gui.hud.GuideHudRenderer;
import dev.openallay.client.presentation.GuidePresentationHost;
import dev.openallay.client.voice.VoiceSettingsActions;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideDisplayRuntime;
import dev.openallay.guide.ui.GuideUiConfig;
import dev.openallay.guide.ui.hud.GuideHudView;
import dev.openallay.recipe.config.RecipeClientRuntime;
import dev.openallay.settings.ClientSettingsService;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Native facts, key draining, and view assembly. Draft and delivery decisions live in the engine. */
public final class NativeGuidePresentationHost implements GuidePresentationHost {
    private final Minecraft minecraft;
    private final RecipeClientRuntime recipes;
    private final GuideDisplayRuntime display;
    private final ClientSettingsService settings;
    private final GuideHudRenderer renderer;
    private Supplier<GuideHudView> hudView;
    private Object connectionWorld;
    private UUID worldConnection;

    public NativeGuidePresentationHost(Minecraft minecraft, RecipeClientRuntime recipes,
            GuideDisplayRuntime display, ClientSettingsService settings, GuideHudRenderer renderer) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.recipes = Objects.requireNonNull(recipes, "recipes");
        this.display = Objects.requireNonNull(display, "display");
        this.settings = settings;
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    public void bindHudView(Supplier<GuideHudView> hudView) {
        this.hudView = Objects.requireNonNull(hudView, "hudView");
    }

    @Override public Facts facts() {
        Object level = minecraft.level;
        if (connectionWorld != level) {
            connectionWorld = level;
            worldConnection = level == null ? null : UUID.randomUUID();
        }
        Screen screen = minecraft.gui.screen();
        Surface surface = screen == null ? Surface.GAMEPLAY
                : screen instanceof OpenAllayScreen ? Surface.GUIDE
                : screen instanceof GuideChatLiteScreen ? Surface.HUD_INPUT : Surface.OTHER;
        return new Facts(minecraft.player == null ? null : minecraft.player.getUUID(), worldConnection, surface,
                minecraft.gui.overlay() != null, minecraft.gui.hud.isHidden(), minecraft.isWindowActive());
    }

    @Override public Input pollInput() {
        int toggle = 0;
        while (OpenAllayKeyMappings.TOGGLE_HUD.consumeClick()) toggle++;
        int edit = 0;
        while (OpenAllayKeyMappings.EDIT_HUD.consumeClick()) edit++;
        int interact = 0;
        while (OpenAllayKeyMappings.INTERACT_HUD.consumeClick()) interact++;
        return new Input(toggle, edit, interact, OpenAllayKeyMappings.VOICE_PTT.isDown());
    }

    @Override public BooleanSupplier captureFence(FenceScope scope) {
        Object level = minecraft.level;
        UUID actor = minecraft.player == null ? null : minecraft.player.getUUID();
        Screen sourceScreen = minecraft.gui.screen();
        return scope == FenceScope.VIEW
                ? () -> minecraft.level == level && minecraft.gui.screen() == sourceScreen
                : () -> minecraft.level == level && minecraft.player != null
                        && actor != null && actor.equals(minecraft.player.getUUID());
    }

    @Override public void showGuide(View view, Runnable openSettings) {
        OpenAllayScreen screen = new OpenAllayScreen(view.service(), recipes, display, openSettings, view.state())
                .withNotifications(view.notifications()).withVoice(view.voice());
        if (view.observationInput() != null) screen.withObservationInput(view.observationInput());
        if (view.observationSubmission() != null) screen.withObservationSubmission(view.observationSubmission());
        if (view.observationImages() != null) screen.withObservationImages(view.observationImages());
        minecraft.gui.setScreen(screen);
    }

    @Override public void showSettings(Runnable returnToGuide, BooleanSupplier ownerValid, VoiceSettingsActions voice,
            Consumer<GuideUiConfig.Notifications> previewNotification) {
        OpenAllaySettingsScreen screen = new OpenAllaySettingsScreen(settings, returnToGuide)
                .withUiActions(new OpenAllaySettingsScreen.UiActions() {
                    @Override public void editHud(OpenAllaySettingsScreen returnScreen, GuideDisplayConfig draft,
                            Consumer<GuideDisplayConfig> applied) {
                        if (!ownerValid.getAsBoolean()) return;
                        minecraft.gui.setScreen(new GuideHudEditorScreen(draft, applied, returnScreen,
                                ownerValid, renderer, hudView));
                    }
                    @Override public void previewNotification(GuideUiConfig.Notifications config) {
                        previewNotification.accept(config);
                    }
                }).withVoiceActions(voice);
        minecraft.gui.setScreen(screen);
    }

    @Override public void showHudEditor(GuideDisplayConfig draft, Consumer<GuideDisplayConfig> applied,
            BooleanSupplier ownerValid) {
        minecraft.gui.setScreen(new GuideHudEditorScreen(draft, applied, null, ownerValid, renderer, hudView));
    }

    @Override public void showHudInput(View view, Runnable openGuide) {
        GuideChatLiteScreen screen = new GuideChatLiteScreen(view.service(), view.state(), display, openGuide,
                view.voice()).withRecipes(recipes);
        if (view.observationInput() != null) screen.withObservationInput(view.observationInput());
        if (view.observationSubmission() != null) screen.withObservationSubmission(view.observationSubmission());
        minecraft.gui.setScreen(screen);
    }

    @Override public void focusComposerAfterVoiceDraft() {
        if (minecraft.gui.screen() instanceof OpenAllayScreen screen) screen.focusComposerAfterVoiceDraft();
    }
}
