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
        String coordinator = source("common/src/main/java/dev/openallay/client/gui/GuideClientUiCoordinator.java");
        String screen = source("common/src/main/java/dev/openallay/client/gui/OpenAllayScreen.java");
        assertTrue(coordinator.contains("new OpenAllaySettingsScreen(settings, () -> openGuide(service))"));
        assertTrue(coordinator.contains("new OpenAllayScreen(service, recipes, display, openSettings, owner)"));
        assertTrue(coordinator.contains("binding = services.listenPresentation(new GuidePresentationListener()"),
                "the shared draft owner binds before live request admission");
        assertTrue(coordinator.contains("public void bound(GuideService next)"));
        assertTrue(coordinator.contains("if (closed || bound == next) return;"));
        assertTrue(coordinator.contains("state = GuideClientUiState.create(next, GuideClientUiCoordinator.this.dispatcher);"));
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
        assertTrue(widgetMixin.contains("implements GuideComposerGeometry.NativeAccess"));
        assertTrue(widgetMixin.contains("@Mixin(MultiLineEditBox.class)"));
        assertTrue(fieldMixin.contains("@Invoker(\"reflowDisplayLines\")"));
        assertTrue(mixins.contains("MultiLineEditBoxAccessor"));
        assertTrue(mixins.contains("MultilineTextFieldAccessor"));
        assertTrue(fabric.contains("\"config\": \"openallay.client.mixins.json\""));
        assertTrue(fabric.contains("\"environment\": \"client\""));
        assertTrue(neo.contains("[[mixins]]"));
        assertTrue(neo.contains("config = \"openallay.client.mixins.json\""));
    }
}
