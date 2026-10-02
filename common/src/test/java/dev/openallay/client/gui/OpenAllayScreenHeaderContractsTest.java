package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.guide.ui.GuideUiLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

/** Native source routes supplement geometry tests; they do not claim screenshot or game evidence. */
final class OpenAllayScreenHeaderContractsTest {
    private static String screenSource() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("settings.gradle"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("repository root unavailable");
        return Files.readString(root.resolve("common/src/main/java/dev/openallay/client/gui/OpenAllayScreen.java"));
    }

    @Test
    void boldNativeTitleIsTheSameComponentThatGetsMeasuredAndRendered() throws Exception {
        Component title = OpenAllayScreen.headerTitle();
        assertTrue(title.getStyle().isBold());
        assertEquals("screen.openallay.guide", ((TranslatableContents) title.getContents()).getKey());
        // The fake font makes the old unstyled-120 / styled-130 mismatch deterministic.
        AtomicInteger glyphs = new AtomicInteger();
        ToIntFunction<Component> fakeFont = component -> {
            glyphs.set(0);
            component.getVisualOrderText().accept((index, style, codePoint) -> {
                glyphs.addAndGet(style.isBold() ? 13 : 12);
                return true;
            });
            return glyphs.get();
        };
        Component declaredName = Component.literal("abcdefghij");
        Component visibleName = declaredName.copy().withStyle(title.getStyle());
        assertEquals(120, fakeFont.applyAsInt(declaredName));
        assertEquals(130, fakeFont.applyAsInt(visibleName));
        for (int[] size : new int[][] {{240, 180}, {320, 240}, {427, 320}, {900, 500}}) {
            GuideUiLayout layout = GuideUiLayout.calculate(size[0], size[1], false,
                    fakeFont.applyAsInt(visibleName), 60, 48, 54, true, true, true, 4);
            assertEquals(130, layout.header().title().width());
        }
        String screen = screenSource();
        int initStart = screen.indexOf("protected void init()");
        int initEnd = screen.indexOf("public void resize(", initStart);
        String init = screen.substring(initStart, initEnd);
        assertTrue(init.contains("Component title = headerTitle()"));
        assertTrue(init.contains("font.width(title.getVisualOrderText())"));
        assertTrue(init.contains("new HeaderTitle(title, header.title())"));
        assertFalse(init.contains("font.width(Component.translatable(\"screen.openallay.guide\"))"));
    }

    @Test
    void chineseAndLongAssistantFixturesUseStyledWidthsRatherThanCharacterCounts() {
        for (String name : new String[] {"OpenAllay", "小羽", "建筑与探险助手小羽", "OpenAllay · session-123456789", "小羽".repeat(80)}) {
            Component full = Component.literal(name).withStyle(OpenAllayScreen.headerTitle().getStyle());
            AtomicInteger measured = new AtomicInteger();
            full.getVisualOrderText().accept((index, style, codePoint) -> {
                measured.addAndGet((codePoint > 127 ? 9 : 6) + (style.isBold() ? 1 : 0));
                return true;
            });
            int styledWidth = measured.get();
            for (int[] size : new int[][] {{240, 180}, {320, 240}, {427, 320}, {900, 500}}) {
                GuideUiLayout layout = GuideUiLayout.calculate(size[0], size[1], false,
                        styledWidth, 180, 48, 54, true, true, true, 4);
                assertTrue(layout.header().title().width() > 0, name);
                if (styledWidth <= 130 || size[1] >= 240 && styledWidth <= size[0] - 24) {
                    assertEquals(styledWidth, layout.header().title().width(), name);
                }
                assertTrue(layout.transcript().height() >= 54, name);
                assertTrue(layout.composerExtras(true, true, 4).input().height() >= 24, name);
            }
        }
    }

    @Test
    void longNamesHaveTheirOwnBoundedNativeTooltipAndKeyboardNarration() throws Exception {
        String screen = screenSource();
        int start = screen.indexOf("private final class HeaderTitle extends AbstractWidget");
        int end = screen.indexOf("public Map<String, Object> e2eHeaderReceipt()", start);
        assertTrue(start >= 0 && end > start);
        String title = screen.substring(start, end);
        assertTrue(title.contains("super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), title)"));
        assertTrue(title.contains("setTooltip(Tooltip.create(title))"));
        assertTrue(title.contains("getMessage()"));
        assertTrue(title.contains("full.getVisualOrderText()"));
        assertTrue(title.contains("plainHeadByWidth(full.getString()"));
        assertTrue(title.contains("full.getStyle()"));
        assertTrue(title.contains("prefix + \"…\""));
        assertTrue(title.contains("boundedHeaderText(graphics, visible"));
        assertTrue(title.contains("isFocused()"));
        assertTrue(title.contains("output.add(NarratedElementType.TITLE, getMessage())"));
        assertFalse(title.contains("onPress"));
        assertFalse(title.contains("active = false"));
        assertFalse(title.contains("nextFocusPath"), "keep native AbstractWidget Tab navigation");
        assertFalse(title.contains("mouseClicked"), "keep native label focus without an invented action");
    }

    @Test
    void compactHeaderControlsKeepSemanticTooltipsAndNativeButtonNarration() throws Exception {
        String screen = screenSource();
        assertTrue(screen.contains("Component.literal(\"≡\")"));
        assertTrue(screen.contains(".tooltip(Tooltip.create(sessionsLabel))"));
        assertTrue(screen.contains(".createNarration(ignored -> sessionsLabel.copy())"));
        assertTrue(screen.contains(".createNarration(ignored -> Component.translatable(\"screen.openallay.action.more\"))"));
        assertTrue(screen.contains(".createNarration(ignored -> Component.translatable(\"screen.openallay.settings.title\"))"));
        assertTrue(screen.contains(".createNarration(ignored -> modelButtonDescription())"));
        assertTrue(screen.contains("model.setTooltip(Tooltip.create(modelButtonDescription()))"));
        assertTrue(screen.contains("Component.translatable(\"screen.openallay.action.models\")"));
        assertTrue(screen.contains("if (available < 24) return Component.literal(\"▾\")"));
        assertTrue(screen.contains(".append(modelStatus()).append(\" · \").append(modelLabel())"));
    }

    @Test
    void developmentReceiptsReadNativeWidgetsAndLastExtractionWithoutChangingTheScreen() throws Exception {
        String screen = screenSource();
        int start = screen.indexOf("public Map<String, Object> e2eHeaderReceipt()");
        int end = screen.indexOf("public Map<String, Object> e2eExportReceipt()", start);
        assertTrue(start >= 0 && end > start);
        String receipts = screen.substring(start, end);
        assertEquals(2, receipts.split("requireDevelopmentProbe\\(\\)", -1).length - 1);
        assertTrue(receipts.contains("headerTitleWidget.getMessage()"));
        assertTrue(receipts.contains("headerTitleWidget.getWidth()"));
        assertTrue(receipts.contains("headerTitleWidget.paintedTitle != null"));
        assertTrue(receipts.contains("styledWidth <= headerTitleWidget.getWidth()"));
        assertTrue(receipts.contains("area.equals(renderedTelemetryBounds) ? renderedTelemetryRows : 0"));
        assertTrue(receipts.contains("telemetryContext.getString()"));
        assertTrue(receipts.contains("telemetryInput.getString()"));
        assertTrue(receipts.contains("telemetryCost.getString()"));
        for (String forbidden : new String[] {"service.", "setScreen(", "setMessage(", "init()", "rebuild", "onPress("}) {
            assertFalse(receipts.contains(forbidden), forbidden);
        }
    }

    @Test
    void exportAndToolsProbesReadOnlyActualCompletionAndNativeExtractionCaches() throws Exception {
        String screen = screenSource();
        int exportStart = screen.indexOf("public Map<String, Object> e2eExportReceipt()");
        int exportEnd = screen.indexOf("public void e2eExportSelectedSession()", exportStart);
        String export = screen.substring(exportStart, exportEnd);
        int toolsStart = screen.indexOf("public Map<String, Object> e2eToolsReceipt()");
        int toolsEnd = screen.indexOf("private void boundedHeaderText(", toolsStart);
        String tools = screen.substring(toolsStart, toolsEnd);
        for (String getter : List.of(export, tools)) {
            assertTrue(getter.contains("requireDevelopmentProbe()"));
            for (String forbidden : new String[] {"service.", "snapshot()", "Files.", "new GuideService", "setScreen(",
                    "exportSession()", "captureSelected", "tokenizer", "readString"}) assertFalse(getter.contains(forbidden), forbidden);
        }
        assertTrue(export.contains("notice == exportNotice ? exportNoticeKey"));
        assertTrue(export.contains("lastExportFilename"));
        assertTrue(export.contains("lastExportRequestCount"));
        String action = screen.substring(exportEnd, toolsStart);
        assertTrue(action.contains("requireDevelopmentProbe()"));
        assertTrue(action.contains("exportSession()"));
        assertFalse(action.contains("exportRunning ="));
        assertTrue(tools.contains("List.copyOf(renderedToolIds)"));
        assertTrue(tools.contains("List.copyOf(renderedResultCardIds)"));
        assertTrue(tools.contains("tools.stream().filter(this::toolExpanded).count()"));
        assertTrue(screen.contains("intersects(paintedRow, area)) renderedToolIds.add(toolFocusId(paintedTool))"));
        assertTrue(screen.contains("if (painted && Boolean.getBoolean(\"openallay.e2e.enabled\"))"));
        assertTrue(screen.contains("intersects(paintedNode, layout.transcript()) && !renderedResultCardIds.contains(cardId)"));
        assertTrue(screen.contains("line.component() instanceof dev.openallay.guide.semantic.RichComponent.RecipeGrid"));
        assertTrue(screen.contains("painted && Boolean.getBoolean(\"openallay.e2e.enabled\") && intersects(bounds, layout.transcript())"));
        assertTrue(screen.contains("visibleDetail(cardTop, y - cardTop, detail)"));
        assertTrue(screen.contains("lastExportFilename = exported.filename()"));
        assertTrue(screen.contains("lastExportRequestCount = exported.requestCount()"));
    }

    @Test
    void telemetryCardRestoresTheOriginalPalettePaddingAndThreeRowsWithoutInventingTotals() throws Exception {
        String screen = screenSource();
        int renderStart = screen.indexOf("private void renderTelemetry(");
        int renderEnd = screen.indexOf("private void renderProgress(", renderStart);
        String render = screen.substring(renderStart, renderEnd);
        assertTrue(render.contains("layout.telemetryCard()"));
        assertTrue(render.contains("panelAltColor()"));
        assertTrue(render.contains("int x = area.x() + 7"));
        assertTrue(render.contains("int y = area.y() + 7"));
        assertTrue(render.contains("telemetryContext, x, y + 12"));
        assertTrue(render.contains("telemetryInput, x, y + 39"));
        assertTrue(render.contains("telemetryCost, x, y + 55"));
        assertTrue(render.contains("/ telemetry.context().budget().contextWindowTokens()"));
        int known = render.indexOf("if (telemetryImageBarEligible())");
        int bar = render.indexOf("graphics.fill(x, y + 25");
        assertTrue(known >= 0 && bar > known, "even the total-occupancy track needs known image accounting");
        assertTrue(render.contains("!= dev.openallay.model.tokenizer.TokenizerMetadata.ImageAccounting.UNKNOWN"));
        assertTrue(render.contains("0xFF3E4753"));
        assertTrue(render.contains("ratio >= 0.9 ? 0xFFFFD479 : ACCENT"));
        assertTrue(render.contains("!modelSelectorOpen && !sessionOverlay && !overflowOpen"));
        int refreshStart = screen.indexOf("private void refreshTelemetry()");
        String refresh = screen.substring(refreshStart, renderStart);
        for (String key : new String[] {"text_estimate", "image_unknown", "context_unknown", "partial",
                "cache_unknown", "inherited", "price_note"}) {
            assertTrue(refresh.contains("screen.openallay.telemetry." + key), key);
        }
        assertTrue(refresh.contains("compactTokens(context.budget().contextWindowTokens())"));
        assertTrue(refresh.contains("context.budget().inputTokens()"));
        assertTrue(refresh.contains("context.budget().reservedTokens()"));
        assertTrue(refresh.contains("context.budget().maxOutputTokens()"));
        assertTrue(refresh.contains("rate == null ? Component.translatable(\"screen.openallay.telemetry.cache_compact_unknown\")"));
        assertTrue(refresh.contains("telemetryCost = Component.translatable(\"screen.openallay.telemetry.cost_compact\", cost)"));
        assertTrue(refresh.contains("usage.costIncomplete() && usage.estimatedUsd() != null"));
        assertTrue(refresh.contains("cost += \"+\""));
    }
}
