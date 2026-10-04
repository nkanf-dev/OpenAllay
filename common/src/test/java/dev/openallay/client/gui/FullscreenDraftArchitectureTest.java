package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Native/source guards supplement, not replace, Root's loader and transformation gates. */
final class FullscreenDraftArchitectureTest {
    private static Path root() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("settings.gradle"))) path = path.getParent();
        if (path == null) throw new IllegalStateException("repository root unavailable");
        return path;
    }
    private static String source(String relative) throws Exception {
        return Files.readString(root().resolve(relative));
    }
    @Test void screensKeepTheConnectionDraftAndPendingIntentWhenSettingsTemporarilyOpens() throws Exception {
        String coordinator = source("engine-core/src/main/java/dev/openallay/client/presentation/GuidePresentationCoordinator.java");
        String host = source("common/src/main/java/dev/openallay/client/gui/NativeGuidePresentationHost.java");
        String facade = source("common/src/main/java/dev/openallay/client/gui/GuideClientUiCoordinator.java");
        String screen = source("common/src/main/java/dev/openallay/client/gui/OpenAllayScreen.java");
        assertTrue(coordinator.contains("host.showSettings(() -> openGuide(service), ownerValid, voice.settings(), config ->"));
        assertTrue(host.contains("new OpenAllaySettingsScreen(settings, returnToGuide)"));
        assertTrue(coordinator.contains("host.showGuide(view(service, owner), openSettings)"));
        assertTrue(coordinator.contains("new GuidePresentationHost.View(service, owner, notifications, voice.input()"));
        assertTrue(host.contains("new OpenAllayScreen(view.service(), recipes, display, openSettings, view.state())"));
        assertTrue(coordinator.contains("binding = services.listenPresentation(new GuidePresentationListener()"),
                "the shared draft owner binds before live request admission");
        assertTrue(coordinator.contains("public void bound(GuideService next)"));
        assertTrue(coordinator.contains("if (closed || bound == next) return;"));
        assertTrue(coordinator.contains("state = Objects.requireNonNull(states.apply(next), \"state\");"));
        assertTrue(facade.contains("service -> GuideClientUiStates.create(service, dispatcher)"));
        assertFalse(host.contains("new GuideClientUiState("));
        assertFalse(facade.contains("new GuideClientUiState("));
        assertTrue(screen.contains("uiState.captureIntent("));
        assertTrue(screen.contains("uiState.beginIntentSubmission("));
        assertTrue(screen.contains("uiState.completeIntentSubmission("));
        assertTrue(screen.contains("uiState.clearAcceptedIntent("));
        assertTrue(screen.contains("uiState.invalidatePendingEdit("));
        String removed = screen.substring(screen.indexOf("public void removed()"),
                screen.indexOf("protected void repositionElements()"));
        assertTrue(removed.contains("attachment.close()"));
        assertTrue(removed.contains("uiState.setText("));
        assertFalse(removed.contains("uiState.close()"));
        String closeState = coordinator.substring(coordinator.indexOf("private void closeState()"));
        assertTrue(closeState.indexOf("state.close()") < closeState.indexOf("state = null"));
    }
    @Test void nativeComposerGeometryAndBothClientRegistrationsShipTogether() throws Exception {
        String geometry = source("common/src/main/java/dev/openallay/client/gui/GuideComposerGeometry.java");
        String widgetMixin = source("common/src/main/java/dev/openallay/client/gui/mixin/MultiLineEditBoxAccessor.java");
        String fieldMixin = source("common/src/main/java/dev/openallay/client/gui/mixin/MultilineTextFieldAccessor.java");
        String mixins = source("common/src/main/resources/openallay.client.mixins.json");
        String fabric = source("fabric/src/main/resources/fabric.mod.json");
        String neo = source("neoforge/src/main/resources/META-INF/neoforge.mods.toml");
        assertTrue(geometry.contains("NativeAccess widget = (NativeAccess) composer;"));
        assertFalse(geometry.contains("(MultiLineEditBoxAccessor)"));
        assertTrue(geometry.contains("void openallay$refreshScrollAmount();"));
        int widthChange = geometry.indexOf("if (field.openallay$width() != width) {");
        int widthChangeEnd = geometry.indexOf("\n        }", widthChange);
        assertTrue(widthChange >= 0 && widthChangeEnd > widthChange);
        String reflow = geometry.substring(widthChange, widthChangeEnd);
        assertTrue(reflow.contains("field.openallay$width(width);"));
        assertTrue(reflow.contains("field.openallay$reflowDisplayLines();"));
        assertTrue(reflow.contains("widget.openallay$refreshScrollAmount();"));
        assertTrue(reflow.indexOf("field.openallay$width(width);") < reflow.indexOf("field.openallay$reflowDisplayLines();"));
        assertTrue(reflow.indexOf("field.openallay$reflowDisplayLines();") < reflow.indexOf("widget.openallay$refreshScrollAmount();"));
        assertEquals(1, geometry.split("widget\\.openallay\\$refreshScrollAmount\\(\\);", -1).length - 1);
        for (String forbidden : new String[]{"new MultiLineEditBox", "new MultilineTextField", ".setValue(",
                "seekCursor", "setSelecting", "setScrollAmount", "composer.refreshScrollAmount()"}) {
            assertFalse(geometry.contains(forbidden), forbidden);
        }
        assertTrue(widgetMixin.contains("implements GuideComposerGeometry.NativeAccess"));
        assertTrue(widgetMixin.contains("void openallay$refreshScrollAmount()"));
        assertTrue(widgetMixin.contains("refreshScrollAmount();"));
        String legacyWidgetMixin = source("common/src/targets/1.21.11/java/dev/openallay/client/gui/mixin/MultiLineEditBoxAccessor.java");
        assertTrue(legacyWidgetMixin.contains("void openallay$refreshScrollAmount()"));
        assertTrue(legacyWidgetMixin.contains("refreshScrollAmount();"));
        assertTrue(widgetMixin.contains("@Mixin(MultiLineEditBox.class)"));
        assertTrue(fieldMixin.contains("@Invoker(\"reflowDisplayLines\")"));
        assertTrue(fieldMixin.contains("@Mutable"));
        assertTrue(fieldMixin.contains("@Accessor(\"width\")"));
        for (String forbidden : new String[]{"@Accessor(\"cursor\")", "@Accessor(\"selectCursor\")",
                "@Accessor(\"selecting\")", "@Accessor(\"displayLines\")"}) {
            assertFalse(fieldMixin.contains(forbidden), forbidden);
        }
        assertTrue(mixins.contains("MultiLineEditBoxAccessor"));
        assertTrue(mixins.contains("MultilineTextFieldAccessor"));
        assertTrue(fabric.contains("\"config\": \"openallay.client.mixins.json\""));
        assertTrue(fabric.contains("\"environment\": \"client\""));
        assertTrue(neo.contains("[[mixins]]"));
        assertTrue(neo.contains("config = \"openallay.client.mixins.json\""));
    }
    @Test void minecraft1213UsesItsActualScrollSuperclassAndReusesNativeConstruction() throws Exception {
        String widgetMixin = source("common/src/targets/1.21.3/java/dev/openallay/client/gui/mixin/MultiLineEditBoxAccessor.java");
        assertTrue(widgetMixin.contains("@Mixin(MultiLineEditBox.class)"));
        assertTrue(widgetMixin.contains("extends AbstractScrollWidget"));
        assertTrue(widgetMixin.contains("implements GuideComposerGeometry.NativeAccess"));
        assertTrue(widgetMixin.contains("super(x, y, width, height, message)"));
        assertTrue(widgetMixin.contains("@Accessor(\"textField\")"));
        assertTrue(widgetMixin.contains("return totalInnerPadding();"));
        assertTrue(widgetMixin.contains("setScrollAmount(scrollAmount());"));
        for (String forbidden : new String[]{"AbstractTextAreaWidget", "new MultiLineEditBox", "new MultilineTextField",
                "setScrollAmount(0", "scrollToCursor", "seekCursor", "setValue(", "renderContents("}) {
            assertFalse(widgetMixin.contains(forbidden), forbidden);
        }
        String editor = source("common/src/targets/1.21.5/java/dev/openallay/client/gui/GuideNativeMultilineText.java");
        assertTrue(editor.contains("new MultiLineEditBox(font, x, y, width, height, placeholder, narration)"));
        assertTrue(editor.contains("editor.setValue(value);"));
        assertTrue(editor.contains("return 8;"));
        String lite = source("common/src/main/java/dev/openallay/client/gui/hud/GuideChatLiteScreen.java");
        assertFalse(lite.contains("AbstractTextAreaWidget"));
        assertFalse(Files.exists(root().resolve("common/src/targets/1.21.3/java/dev/openallay/client/gui/GuideComposerGeometry.java")));
        assertFalse(Files.exists(root().resolve("common/src/targets/1.21.3/java/dev/openallay/client/gui/GuideNativeMultilineText.java")));
    }
}
