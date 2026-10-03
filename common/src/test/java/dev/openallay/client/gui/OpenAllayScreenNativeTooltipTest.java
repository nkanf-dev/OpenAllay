package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.agent.context.ContextBudget;
import dev.openallay.guide.GuideContextEstimate;
import dev.openallay.guide.GuideModelSelection;
import dev.openallay.guide.GuideTelemetrySnapshot;
import dev.openallay.guide.GuideUsageSnapshot;
import dev.openallay.model.tokenizer.TokenizerMetadata;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

/** Pure native text/positioner tests. No Screen constructor, rendering or screenshot claim. */
final class OpenAllayScreenNativeTooltipTest {
    private static final String PREFIX = "screen.openallay.telemetry.";

    private static Path root() {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("settings.gradle"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("repository root unavailable");
        return root;
    }

    private static GuideTelemetrySnapshot snapshot(GuideContextEstimate context,
            GuideUsageSnapshot usage, GuideUsageSnapshot inherited) {
        return new GuideTelemetrySnapshot("main", GuideModelSelection.client("default"),
                context == null ? null : context.requestId(), context, usage, usage, inherited);
    }

    private static List<Component> lines(GuideTelemetrySnapshot telemetry, String unknown) {
        return OpenAllayScreen.telemetryTooltipComponents(telemetry, unknown,
                OpenAllayScreen.telemetryCostText(telemetry.sessionUsage(), unknown),
                OpenAllayScreen.telemetryCacheText(telemetry.sessionUsage(), unknown));
    }

    private static TranslatableContents translation(Component line) {
        return assertInstanceOf(TranslatableContents.class, line.getContents());
    }

    @Test
    void knownBudgetAndSessionHaveSeparateLogicalNativeRowsWithExactCapturedValues() {
        GuideContextEstimate context = new GuideContextEstimate(UUID.randomUUID(), 321,
                new ContextBudget(10_000, 1_000), "captured-model");
        GuideUsageSnapshot usage = new GuideUsageSnapshot(500, 40, 50, 0, 2, 2,
                false, false, new BigDecimal("0.0123456"), false);
        List<Component> lines = lines(snapshot(context, usage, GuideUsageSnapshot.empty()), "Unknown");
        assertEquals(List.of("latest", "budget.input", "budget.window", "session.calls", "session.cost",
                "cache_detail", "price_note"), lines.stream().map(line -> translation(line).getKey()
                        .substring(PREFIX.length())).toList());
        assertTrue(lines.getFirst().getStyle().isBold());
        assertEquals(List.of(321L, 8_000), List.of(translation(lines.get(1)).getArgs()));
        assertEquals(List.of(10_000, 2_000, 1_000), List.of(translation(lines.get(2)).getArgs()));
        assertEquals(List.of(2), List.of(translation(lines.get(3)).getArgs()));
        assertEquals(List.of("~$0.01235"), List.of(translation(lines.get(4)).getArgs()));
        assertEquals(List.of("10.0%", 50L, 500L), List.of(translation(lines.get(5)).getArgs()));
        assertThrows(UnsupportedOperationException.class, () -> lines.add(Component.empty()));
    }

    @Test
    void unknownAndPartialAmountsStayUnknownWhileKnownPortionsKeepPlusAndInheritedNotes() {
        GuideTelemetrySnapshot unknown = GuideTelemetrySnapshot.unknown("main", GuideModelSelection.client("default"));
        assertEquals("Unknown", OpenAllayScreen.telemetryCostText(unknown.sessionUsage(), "Unknown"));
        assertEquals("Unknown", OpenAllayScreen.telemetryCacheText(unknown.sessionUsage(), "Unknown"));
        List<Component> unknownLines = lines(unknown, "Unknown");
        assertEquals(List.of("latest", "context_unknown", "session.calls", "session.cost", "cache_detail",
                "partial", "cache_unknown", "price_note"), unknownLines.stream()
                .map(line -> translation(line).getKey().substring(PREFIX.length())).toList());
        assertEquals(List.of("Unknown"), List.of(translation(unknownLines.get(3)).getArgs()));
        assertEquals(List.of("Unknown", 0L, 0L), List.of(translation(unknownLines.get(4)).getArgs()));
        GuideUsageSnapshot partial = new GuideUsageSnapshot(500, 40, 0, 0, 3, 1,
                true, true, new BigDecimal("0.1"), true);
        GuideUsageSnapshot inherited = new GuideUsageSnapshot(10, 1, 0, 0, 1, 1,
                false, true, null, true);
        GuideContextEstimate image = new GuideContextEstimate(UUID.randomUUID(), 123,
                new ContextBudget(10_000, 1_000), "captured-model", TokenizerMetadata.ImageAccounting.UNKNOWN);
        List<Component> partialLines = lines(snapshot(image, partial, inherited), "Unknown");
        Map<String, Component> keyed = new HashMap<>();
        partialLines.forEach(line -> keyed.put(translation(line).getKey(), line));
        assertTrue(keyed.containsKey(PREFIX + "image_unknown"));
        assertTrue(keyed.containsKey(PREFIX + "partial"));
        assertTrue(keyed.containsKey(PREFIX + "cache_unknown"));
        assertEquals(List.of("~$0.10000+"), List.of(translation(keyed.get(PREFIX + "session.cost")).getArgs()));
        assertEquals(List.of("Unknown"), List.of(translation(keyed.get(PREFIX + "inherited")).getArgs()));
        GuideUsageSnapshot knownInherited = new GuideUsageSnapshot(10, 1, 0, 0, 1, 1,
                false, false, new BigDecimal("0.2"), true);
        Component inheritedLine = lines(snapshot(image, partial, knownInherited), "Unknown").stream()
                .filter(line -> translation(line).getKey().equals(PREFIX + "inherited")).findFirst().orElseThrow();
        assertEquals(List.of("~$0.20000+"), List.of(translation(inheritedLine).getArgs()));
        assertEquals("~$0.00000", OpenAllayScreen.telemetryCostText(GuideUsageSnapshot.empty(), "Unknown"),
                "explicit empty cost is still a known zero");
    }

    @Test
    void englishAndChineseRowsWrapThroughNativeSplitterAtNarrowAndWideWidths() throws Exception {
        Language previous = Language.getInstance();
        try {
            for (String locale : List.of("en_us", "zh_cn")) {
                Map<String, String> labels = new HashMap<>();
                var json = com.google.gson.JsonParser.parseString(Files.readString(root().resolve(
                        "common/src/main/resources/assets/openallay/lang/" + locale + ".json"))).getAsJsonObject();
                json.entrySet().forEach(entry -> labels.put(entry.getKey(), entry.getValue().getAsString()));
                labels.putAll(locale.equals("en_us") ? Map.of(
                        PREFIX + "budget.input", "Estimated input: %s / %s tokens",
                        PREFIX + "budget.window", "Total window: %s; reserved: %s (2 × %s output)",
                        PREFIX + "session.calls", "Model calls: %s",
                        PREFIX + "session.cost", "Estimated session cost: %s") : Map.of(
                        PREFIX + "budget.input", "输入估算：%s / %s tokens",
                        PREFIX + "budget.window", "总窗口：%s；预留：%s（2 × %s 输出）",
                        PREFIX + "session.calls", "模型调用：%s 次",
                        PREFIX + "session.cost", "会话估算费用：%s"));
                Language.inject(new FakeLanguage(labels));
                GuideContextEstimate context = new GuideContextEstimate(UUID.randomUUID(), 321,
                        new ContextBudget(10_000, 1_000), "captured-model");
                GuideUsageSnapshot usage = new GuideUsageSnapshot(500, 40, 50, 0, 1, 1,
                        false, false, new BigDecimal("0.01"), false);
                List<Component> logical = lines(snapshot(context, usage, GuideUsageSnapshot.empty()),
                        labels.get(PREFIX + "unknown"));
                assertEquals(7, logical.size());
                logical.forEach(line -> assertFalse(line.getString().contains("\n"), line.getString()));
                assertTrue(logical.get(1).getString().contains("321"));
                assertTrue(logical.get(1).getString().contains("8000"));
                assertTrue(logical.get(2).getString().contains("10000"));
                StringSplitter nativeSplitter = new StringSplitter((codePoint, style) ->
                        (codePoint > 127 ? 9 : 6) + (style.isBold() ? 1 : 0));
                int wideCount = 0;
                for (int screenWidth : new int[] {320, 180}) {
                    int wrapWidth = OpenAllayScreen.nativeTooltipWidth(screenWidth);
                    AtomicInteger splitCalls = new AtomicInteger();
                    List<FormattedCharSequence> wrapped = OpenAllayScreen.wrapNativeTooltip(logical, wrapWidth,
                            (line, maxWidth) -> {
                                splitCalls.incrementAndGet();
                                return Language.getInstance().getVisualOrder(
                                        nativeSplitter.splitLines(line, maxWidth, Style.EMPTY));
                            });
                    assertEquals(logical.size(), splitCalls.get());
                    assertTrue(wrapped.size() > logical.size(), "long native notes must actually wrap");
                    wrapped.forEach(line -> assertTrue(nativeSplitter.stringWidth(line) <= wrapWidth,
                            plain(line) + " exceeds " + wrapWidth));
                    assertEquals(removeWhitespace(logical.stream().map(Component::getString)
                            .reduce("", String::concat)), removeWhitespace(wrapped.stream().map(
                            OpenAllayScreenNativeTooltipTest::plain).reduce("", String::concat)));
                    AtomicInteger titleGlyphs = new AtomicInteger();
                    wrapped.getFirst().accept((index, style, codePoint) -> {
                        assertTrue(style.isBold());
                        titleGlyphs.incrementAndGet();
                        return true;
                    });
                    assertTrue(titleGlyphs.get() > 0);
                    if (screenWidth == 320) wideCount = wrapped.size();
                    else assertTrue(wrapped.size() >= wideCount);
                }
            }
        } finally {
            Language.inject(previous);
        }
    }

    @Test
    void widthAndPositionUseTheNativeDefaultBoundaryAlgorithm() {
        assertEquals(260, OpenAllayScreen.nativeTooltipWidth(900));
        assertEquals(260, OpenAllayScreen.nativeTooltipWidth(320));
        assertEquals(156, OpenAllayScreen.nativeTooltipWidth(180));
        assertEquals(1, OpenAllayScreen.nativeTooltipWidth(24));
        var position = DefaultTooltipPositioner.INSTANCE.positionTooltip(320, 240, 310, 230, 260, 100);
        assertEquals(38, position.x());
        assertEquals(137, position.y());
    }

    @Test
    void nativeTooltipRouteUsesCachedVisualRowsAndReceiptReadsNoLiveProviderOrFont() throws Exception {
        String screen = Files.readString(root().resolve("common/src/main/java/dev/openallay/client/gui/OpenAllayScreen.java"));
        String refresh = screen.substring(screen.indexOf("private void refreshTelemetry()"),
                screen.indexOf("static String compactTokens"));
        for (String dependency : new String[] {"telemetry == next", "telemetryTooltipFont == font",
                "telemetryTooltipLanguage == language", "telemetryTooltipWidth == wrapWidth"}) {
            assertTrue(refresh.contains(dependency), dependency);
        }
        assertTrue(refresh.contains("wrapNativeTooltip(telemetryTooltip, wrapWidth, font::split)"));
        assertFalse(refresh.contains("detail.append(\"\\n\")"));
        assertFalse(refresh.contains("screen.openallay.telemetry.budget\""));
        assertFalse(refresh.contains("screen.openallay.telemetry.session\""));
        String render = screen.substring(screen.indexOf("private void renderTelemetry("),
                screen.indexOf("private boolean telemetryImageBarEligible()"));
        assertTrue(render.contains("setTooltipForNextFrame(font, telemetryTooltipWrapped,"));
        assertTrue(render.contains("DefaultTooltipPositioner.INSTANCE"));
        assertTrue(render.contains("mouseX, mouseY, false"));
        assertTrue(render.contains("requestedTelemetryTooltipLines = telemetryTooltipWrapped.size()"));
        assertFalse(render.contains("font.split"));
        assertFalse(render.contains("telemetryTooltip, mouseX"), "Component overload cannot wrap logical rows");
        String receipt = screen.substring(screen.indexOf("public Map<String, Object> e2eTelemetryTooltipReceipt()"),
                screen.indexOf("public Map<String, Object> e2eExportReceipt()"));
        assertTrue(receipt.contains("requireDevelopmentProbe()"));
        assertTrue(receipt.contains("telemetryTooltipTexts"));
        for (String forbidden : new String[] {"font.", "service.", "getString()", "Files.", "tokenizer",
                "setScreen(", "setFocused(", "split(", "ask("}) assertFalse(receipt.contains(forbidden), forbidden);
        // Immutable image-accounting metadata is not tokenization work.
        assertTrue(refresh.contains("TokenizerMetadata.ImageAccounting.UNKNOWN"));
        for (String forbidden : new String[] {"contextSpec(", "new ModelContextTokenEstimator(",
                ".estimate(", ".estimateText(", ".countTokensOrdinary(", "Files.", "readString("}) {
            assertFalse(refresh.contains(forbidden), forbidden);
            assertFalse(render.contains(forbidden), forbidden);
        }
    }

    private static String plain(FormattedCharSequence line) {
        StringBuilder text = new StringBuilder();
        line.accept((index, style, codePoint) -> { text.appendCodePoint(codePoint); return true; });
        return text.toString();
    }

    private static String removeWhitespace(String text) {
        return text.replaceAll("\\s+", "");
    }

    /** Only the process-local Language singleton is substituted; native text/split classes remain real. */
    private static final class FakeLanguage extends Language {
        private final Map<String, String> labels;
        private FakeLanguage(Map<String, String> labels) { this.labels = Map.copyOf(labels); }
        @Override public String getOrDefault(String key, String fallback) { return labels.getOrDefault(key, fallback); }
        @Override public boolean has(String key) { return labels.containsKey(key); }
        @Override public boolean isDefaultRightToLeft() { return false; }
        @Override public FormattedCharSequence getVisualOrder(FormattedText text) {
            return DEFAULT_INSTANCE.getVisualOrder(text);
        }
    }
}
