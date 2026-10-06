package dev.openallay.client.gui.hud;

import dev.openallay.client.gui.GuideGraphics;
import dev.openallay.client.gui.MinecraftSemanticRenderer;
import dev.openallay.client.gui.MinecraftSemanticResolver;
import dev.openallay.client.gui.OpenAllayWidgetTheme;
import dev.openallay.client.gui.nativeview.NativeDomainView;
import dev.openallay.client.gui.nativeview.NativeDomainViewBinding;
import dev.openallay.client.gui.nativeview.NativeDomainViewRegistry;
import dev.openallay.guide.GuideSource;
import dev.openallay.guide.semantic.RichComponent;
import dev.openallay.guide.semantic.SemanticDocument;
import dev.openallay.guide.ui.GuideEvidencePresentation;
import dev.openallay.guide.ui.GuideRecipeCard;
import dev.openallay.guide.ui.GuideTranscriptVirtualizer;
import dev.openallay.guide.ui.GuideToolSummaryPresenter;
import dev.openallay.guide.ui.GuideUiConfig;
import dev.openallay.guide.ui.GuideUiLayout;
import dev.openallay.guide.ui.GuideUiRow;
import dev.openallay.guide.ui.SemanticLayout;
import dev.openallay.guide.ui.SemanticLayoutCache;
import dev.openallay.guide.ui.SemanticLayoutEngine;
import dev.openallay.guide.ui.hud.GuideHudScrollState;
import dev.openallay.guide.ui.hud.GuideHudToolCards;
import dev.openallay.guide.ui.hud.GuideHudView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import dev.openallay.platform.minecraft.MinecraftComponents;
import net.minecraft.network.chat.Component;
import dev.openallay.client.gui.GuideTextLine;
import dev.openallay.client.gui.GuideNativeFont;

/** Shared native result viewport for passive HUD pages and explicit compact interaction. */
public final class GuideHudResultRenderer implements AutoCloseable {
    public sealed interface Action permits Action.Tool, Action.Semantic, Action.Sources {
        record Tool(String rowId) implements Action {}
        record Semantic(MinecraftSemanticRenderer.Intent intent) implements Action {}
        record Sources(List<GuideSource> sources) implements Action {
            public Sources { sources = List.copyOf(sources); }
        }
    }
    public record Hit(GuideUiLayout.Rect bounds, Action action, String narration) {}
    /** Last extracted frame only. Read-only probes do not cause game or service operations. */
    public record Receipt(long extractedFrame, List<String> renderedRowIds, int assistantRows, int toolRows,
            int toolCards, int semanticNodes, int nativeItemNodes, int recipeNodes, int contentHeight, int viewportHeight,
            int scroll, int maximumScroll, int cacheEntries, int nativeViews,
            List<String> renderedNodeIds, String lastRenderedText) {
        public Receipt {
            renderedRowIds = List.copyOf(renderedRowIds);
            renderedNodeIds = List.copyOf(renderedNodeIds);
            java.util.Objects.requireNonNull(lastRenderedText, "lastRenderedText");
        }
        public Receipt(long extractedFrame, List<String> renderedRowIds, int assistantRows, int toolRows,
                int toolCards, int semanticNodes, int nativeItemNodes, int recipeNodes, int contentHeight, int viewportHeight,
                int scroll, int maximumScroll, int cacheEntries, int nativeViews) {
            this(extractedFrame, renderedRowIds, assistantRows, toolRows, toolCards, semanticNodes,
                    nativeItemNodes, recipeNodes, contentHeight, viewportHeight, scroll, maximumScroll,
                    cacheEntries, nativeViews, List.of(), "");
        }
    }
    private record Row(String id, GuideUiRow source, String narration, List<GuideTextLine> header,
            SemanticLayout layout, Map<String, GuideRecipeCard> recipes,
            List<GuideTextLine> sources, int height) {}
    private final MinecraftSemanticRenderer semantic = new MinecraftSemanticRenderer(new MinecraftSemanticResolver());
    private final SemanticLayoutCache layouts = new SemanticLayoutCache();
    private final GuideHudScrollState scroll = new GuideHudScrollState();
    private final NativeDomainViewRegistry nativeViews = new NativeDomainViewRegistry();
    private final List<Hit> hits = new ArrayList<>();
    private long hitEpoch;
    private PaintedHits paintedHits;
    /** Identity and geometry of the actual interactive extraction, not a synthetic fresh frame. */
    record PaintedHits(long epoch, Object font, Object language, GuideUiLayout.Rect viewport, int offset) {
        boolean current(long currentEpoch, Object currentFont, Object currentLanguage,
                GuideUiLayout.Rect currentViewport, int currentOffset) {
            return epoch == currentEpoch && font == currentFont && language == currentLanguage
                    && viewport.equals(currentViewport) && offset == currentOffset;
        }
    }
    private List<Row> rows = List.of();
    private List<GuideUiRow> sourceRows;
    private Font cachedFont;
    private Language cachedLanguage;
    private GuideUiConfig.Fullscreen cachedPresentation;
    private String cachedAssistantName;
    private String cachedSession;
    private int cachedWidth = -1;
    private int cachedHeight = -1;
    private String selectedTool;
    private List<GuideSource> selectedSources = List.of();
    private String cachedSelection;
    private List<GuideSource> cachedSources = List.of();
    private long extractedFrame;
    private Receipt receipt = new Receipt(0, List.of(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    public GuideHudScrollState scroll() { return scroll; }
    public Receipt receipt() { return receipt; }
    public List<Hit> hits() { return hits(cachedFont, paintedHits == null ? null : paintedHits.viewport()); }
    public List<Hit> hits(Font font, GuideUiLayout.Rect viewport) {
        return paintedHits != null && paintedHits.current(hitEpoch, font, Language.getInstance(), viewport, scroll.offset())
                ? List.copyOf(hits) : List.of();
    }
    public boolean detailOpen() { return selectedTool != null || !selectedSources.isEmpty(); }
    public void openTool(String id) { selectedTool = id; selectedSources = List.of(); invalidate(); scroll.first(); }
    public void openSources(List<GuideSource> sources) { selectedSources = List.copyOf(sources); selectedTool = null; invalidate(); scroll.first(); }
    public void back() { selectedTool = null; selectedSources = List.of(); invalidate(); scroll.first(); }
    public void tick() { nativeViews.tick(); }
    public void releaseNativeViews() { invalidateHits(); nativeViews.clear(); }
    public void invalidateHits() { hitEpoch++; paintedHits = null; hits.clear(); }
    public void invalidate() { invalidateHits(); sourceRows = null; layouts.clear(); nativeViews.clear(); }

    /** Returns true only when the projected content or measured viewport changed. */
    public boolean prepare(GuideHudView view, Font font, int width, int height) {
        final int measuredWidth = Math.max(1, width);
        Language language = Language.getInstance();
        boolean rowsChanged = sourceRows != view.rows() && !java.util.Objects.equals(sourceRows, view.rows());
        // Compare a new immutable row list only once per snapshot, not once per rendered frame.
        sourceRows = view.rows();
        boolean changed = rowsChanged || cachedFont != font || cachedLanguage != language
                || !view.presentation().equals(cachedPresentation) || cachedWidth != measuredWidth
                || !view.assistantName().equals(cachedAssistantName) || !view.selectedSession().equals(cachedSession)
                || !java.util.Objects.equals(selectedTool, cachedSelection) || cachedSources != selectedSources;
        boolean viewportChanged = cachedHeight != height;
        if (changed || viewportChanged) invalidateHits();
        if (changed) {
            // Clear once on resource/content identity changes, never grow one entry per streaming delta.
            layouts.clear();
            nativeViews.clear();
            cachedFont = font;
            cachedLanguage = language;
            cachedPresentation = view.presentation();
            cachedAssistantName = view.assistantName();
            cachedSession = view.selectedSession();
            cachedWidth = measuredWidth;
            cachedSelection = selectedTool;
            cachedSources = selectedSources;
            ArrayList<Row> replacement = new ArrayList<>();
            if (!selectedSources.isEmpty()) {
                List<GuideTextLine> lines = new ArrayList<>();
                for (GuideSource source : selectedSources) {
                    var label = GuideEvidencePresentation.from(source);
                    var evidence = source.evidence();
                    for (Component component : List.of(MinecraftComponents.translatable(label.sourceKey()),
                            MinecraftComponents.translatable(label.authorityKey()), MinecraftComponents.translatable(label.coverageKey()),
                            MinecraftComponents.literal(evidence.sourceId()), MinecraftComponents.literal(evidence.provenance()),
                            MinecraftComponents.literal(evidence.gameVersion() + " · " + evidence.loader()),
                            MinecraftComponents.literal(evidence.capturedAt() + " — " + source.lastCapturedAt()))) {
                        lines.addAll(GuideNativeFont.split(font, component, measuredWidth));
                    }
                    evidence.details().forEach((key, value) -> lines.addAll(GuideNativeFont.split(font, MinecraftComponents.literal(key + ": " + value), measuredWidth)));
                }
                replacement.add(new Row("sources", null, "", List.copyOf(lines), null, Map.of(), List.of(), Math.max(10, lines.size() * 10 + 8)));
            } else {
                for (GuideUiRow source : view.rows()) {
                    String id = rowId(source);
                    if (selectedTool != null && !selectedTool.equals(id)) continue;
                    ArrayList<GuideTextLine> header = new ArrayList<>();
                    String narration = "";
                    SemanticDocument document = null;
                    Map<String, GuideRecipeCard> recipes = Map.of();
                    List<GuideTextLine> sources = List.of();
                    java.util.Objects.requireNonNull(source);
                    if (source instanceof GuideUiRow.Assistant assistant) {
                        header.addAll(GuideNativeFont.split(font, MinecraftComponents.literal(view.assistantName()), measuredWidth));
                        document = assistant.semantic();
                        if (!assistant.sources().isEmpty()) sources = GuideNativeFont.split(font, MinecraftComponents.translatable(
                                "screen.openallay.evidence.groups", GuideEvidencePresentation.groups(assistant.sources()).size()), measuredWidth);
                    } else if (source instanceof GuideUiRow.Tool tool) {
                        var summary = GuideToolSummaryPresenter.project(tool);
                        Component title = summary.title().isBlank() ? MinecraftComponents.translatable(summary.titleKey())
                                : MinecraftComponents.literal(summary.title());
                        narration = title.getString();
                        header.addAll(GuideNativeFont.split(font, title.copy().append(" · ")
                                .append(MinecraftComponents.translatable(summary.status().translationKey())), measuredWidth));
                        if (summary.hasDescription()) header.addAll(GuideNativeFont.split(font, MinecraftComponents.literal(summary.description()), measuredWidth));
                        // Keep implementation receipts in explicit detail, not the compact task summary.
                        tool.detail().narration().stream().filter(message -> selectedTool != null || switch (message.key()) {
                            case ANALYSIS_EMPTY, RESULT_DETAIL_NOT_STORED, RESULT_VALUE_UNAVAILABLE,
                                    FAILURE_STALE_REFERENCE, FAILURE_UNAVAILABLE, FAILURE_PLAYER_REQUIRED,
                                    FAILURE_INVALID_ARGUMENTS, FAILURE_FORBIDDEN, FAILURE_GENERIC -> true;
                            default -> false;
                        }).forEach(message -> header.addAll(GuideNativeFont.split(font, MinecraftComponents.translatable(
                                message.key().translationKey(), message.arguments().toArray()), measuredWidth)));
                        tool.detail().failure().ifPresent(failure -> header.addAll(GuideNativeFont.split(font, MinecraftComponents.literal(failure.message()), Math.max(1, cachedWidth))));
                        var cards = GuideHudToolCards.project(tool, key -> MinecraftComponents.translatable(key).getString());
                        document = cards.document();
                        recipes = cards.recipes();
                    } else if (source instanceof GuideUiRow.Status status) {
                        Component message = status.failure() != null
                                || !status.text().isBlank() && !status.text().equals(status.status().name())
                                ? MinecraftComponents.literal(status.text()) : MinecraftComponents.translatable(switch (status.status()) {
                                    case CANCELLED -> "screen.openallay.hud.request_stopped";
                                    case INTERRUPTED -> "screen.openallay.history.interrupted";
                                    default -> "screen.openallay.hud.request_failed";
                                });
                        header.addAll(GuideNativeFont.split(font, message, measuredWidth));
                    } else {
                        continue;
                    }
                    SemanticLayout layout = document == null ? null : layouts.get(id, document, measuredWidth,
                            "native-language", "native-font", measurer(font, view.presentation().density()));
                    int spacing = view.presentation().density() == GuideUiConfig.Density.COMPACT ? 6 : 10;
                    int heightOfRow = header.size() * 10 + (layout == null ? 0 : layout.height()) + sources.size() * 10 + spacing;
                    replacement.add(new Row(id, source, narration, List.copyOf(header), layout, recipes, List.copyOf(sources), Math.max(10, heightOfRow)));
                }
            }
            rows = List.copyOf(replacement);
        }
        if (changed || viewportChanged) {
            cachedHeight = height;
            scroll.update(rows.stream().map(row -> new GuideTranscriptVirtualizer.Row(row.id(), row.height())).toList(), height);
        }
        return changed || viewportChanged;
    }

    public void render(GuideGraphics graphics, Font font, GuideHudView view,
            GuideUiLayout.Rect viewport, int offset, int mouseX, int mouseY,
            boolean interactive, long ticks) {
        paintedHits = null;
        hits.clear();
        nativeViews.beginFrame();
        ArrayList<String> rendered = new ArrayList<>();
        var paintedNodes = new java.util.LinkedHashSet<String>();
        var paintedRecipes = new java.util.LinkedHashSet<String>();
        String lastPaintedText = "";
        int assistants = 0, tools = 0, cards = 0, nodes = 0, items = 0;
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        try {
            for (int index = 0; index < rows.size(); index++) {
                Row row = rows.get(index);
                int y = viewport.y() + scroll.rowOffset(index) - offset;
                if (y + row.height() <= viewport.y() || y >= viewport.bottom()) continue;
                rendered.add(row.id());
                if (row.source() instanceof GuideUiRow.Assistant) assistants++;
                if (row.source() instanceof GuideUiRow.Tool) tools++;
                int panel = view.presentation().theme() == GuideUiConfig.Theme.MINT ? 0xB029443F : 0xB0242933;
                if (row.source() instanceof GuideUiRow.Tool) graphics.fill(viewport.x(), y, viewport.right(), y + row.height() - 3, panel);
                int current = y + 2;
                for (GuideTextLine line : row.header()) {
                    if (current + 10 > viewport.y() && current < viewport.bottom()) graphics.text(font, line, viewport.x() + 3, current, OpenAllayWidgetTheme.MINT);
                    current += 10;
                }
                if (interactive && row.source() instanceof GuideUiRow.Tool) hits.add(new Hit(
                        new GuideUiLayout.Rect(viewport.x(), y, viewport.width(), row.header().size() * 10 + 2), new Action.Tool(row.id()),
                        row.narration()));
                if (row.layout() != null) {
                    int first = 0, last = 0, prefix = 0, visibleHeight = 0;
                    int lineY = current;
                    List<SemanticLayout.Line> lines = row.layout().lines();
                    for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                        var line = lines.get(lineIndex);
                        if (lineY + line.height() <= viewport.y()) { prefix += line.height(); first = lineIndex + 1; }
                        else if (lineY < viewport.bottom()) { last = lineIndex + 1; visibleHeight += line.height(); }
                        else break;
                        lineY += line.height();
                    }
                    if (last > first) {
                        SemanticLayout visible = new SemanticLayout(row.layout().width(), visibleHeight, lines.subList(first, last), row.layout().narration());
                        nodes += last - first;
                        if (row.source() instanceof GuideUiRow.Tool) cards += last - first;
                        var result = semantic.render(graphics, font, visible, viewport.x() + 3, current + prefix,
                                Math.max(1, viewport.width() - 6), interactive ? mouseX : Integer.MIN_VALUE,
                                interactive ? mouseY : Integer.MIN_VALUE, view.animationsEnabled(), ticks,
                                (g, f, component, bounds, mx, my, presentationTicks) -> {
                                    boolean painted = nativeRecipe(view, row, g, f, component, bounds, mx, my, presentationTicks);
                                    if (painted && bounds.x() < viewport.right() && bounds.right() > viewport.x()
                                            && visibleLine(viewport, bounds.y(), bounds.height())) {
                                        paintedRecipes.add(component.nodeId());
                                    }
                                    return painted;
                                });
                        // Only record nodes after the native paint path returns. Never read document fallback/tail.
                        int paintedY = current + prefix;
                        for (var line : visible.lines()) {
                            if (visibleLine(viewport, paintedY, line.height())) {
                                if (!(line.component() instanceof RichComponent.RecipeGrid)
                                        || paintedRecipes.contains(line.nodeId())) paintedNodes.add(paintedNodeId(line.nodeId()));
                                if (line.component() instanceof RichComponent.ItemRow itemRow) {
                                    int itemY = paintedY;
                                    for (var item : itemRow.items()) {
                                        if (visibleLine(viewport, itemY, 16)) items++;
                                        itemY += 22;
                                    }
                                }
                                if (!line.runs().isEmpty()) {
                                    StringBuilder actual = new StringBuilder();
                                    line.runs().forEach(run -> actual.append(run.text()));
                                    if (!actual.isEmpty()) lastPaintedText = actual.toString();
                                }
                            }
                            paintedY += line.height();
                        }
                        if (interactive) for (var hit : result.hits()) hits.add(new Hit(hit.bounds(), new Action.Semantic(hit.intent()), row.layout().narration()));
                    }
                    current += row.layout().height();
                }
                if (row.source() instanceof GuideUiRow.Assistant assistant && !row.sources().isEmpty()) {
                    int sourceTop = current;
                    for (var line : row.sources()) { graphics.text(font, line, viewport.x() + 3, current, OpenAllayWidgetTheme.MUTED); current += 10; }
                    if (interactive) hits.add(new Hit(new GuideUiLayout.Rect(viewport.x(), sourceTop, viewport.width(), current - sourceTop),
                            new Action.Sources(assistant.sources()), MinecraftComponents.translatable("screen.openallay.evidence.groups", assistant.sources().size()).getString()));
                }
            }
        } finally { graphics.disableScissor(); nativeViews.endFrame(); }
        if (interactive) paintedHits = new PaintedHits(hitEpoch, font, Language.getInstance(), viewport, offset);
        receipt = new Receipt(++extractedFrame, rendered, assistants, tools, cards, nodes, items, paintedRecipes.size(), scroll.totalHeight(), viewport.height(),
                offset, Math.max(0, scroll.totalHeight() - viewport.height()), layouts.stats().entries(), nativeViews.activeViewCount(),
                List.copyOf(paintedNodes), lastPaintedText);
    }

    static String paintedNodeId(String layoutNodeId) {
        int wrapped = layoutNodeId.lastIndexOf("-line-");
        if (wrapped < 0 || wrapped + 6 == layoutNodeId.length()) return layoutNodeId;
        for (int index = wrapped + 6; index < layoutNodeId.length(); index++) {
            char value = layoutNodeId.charAt(index);
            if (value < '0' || value > '9') return layoutNodeId;
        }
        return layoutNodeId.substring(0, wrapped);
    }

    static boolean visibleLine(GuideUiLayout.Rect viewport, int top, int height) {
        return viewport.width() > 0 && viewport.height() > 0 && height > 0
                && (long) top + height > viewport.y() && top < viewport.bottom();
    }

    private boolean nativeRecipe(GuideHudView view, Row row, GuideGraphics graphics, Font font,
            RichComponent.RecipeGrid component, GuideUiLayout.Rect bounds, int mouseX, int mouseY, long ticks) {
        GuideRecipeCard recipe = row.recipes().get(component.nodeId());
        if (recipe == null && row.source() instanceof GuideUiRow.Assistant assistant) {
            recipe = view.rows().stream().filter(GuideUiRow.Tool.class::isInstance).map(GuideUiRow.Tool.class::cast)
                    .filter(tool -> tool.requestId().equals(assistant.requestId()) && tool.activity().invocationId().equals(component.originInvocationId()))
                    .flatMap(tool -> tool.detail().cards().stream()).filter(dev.openallay.guide.ui.GuideDetailCard.Recipe.class::isInstance)
                    .map(dev.openallay.guide.ui.GuideDetailCard.Recipe.class::cast).map(dev.openallay.guide.ui.GuideDetailCard.Recipe::recipe)
                    .filter(card -> card.references().contains(component.recipe())).findFirst().orElse(null);
        }
        if (recipe == null) return false;
        return nativeViews.render(new NativeDomainViewBinding.Recipe(row.id() + ":component:" + component.nodeId(), component, recipe),
                new NativeDomainView.RenderContext(graphics, font, bounds, mouseX, mouseY, ticks));
    }
    public static String rowId(GuideUiRow row) {
        java.util.Objects.requireNonNull(row);
        if (row instanceof GuideUiRow.Assistant value) {
            return "assistant:" + value.requestId() + ":" + value.ordinal();
        } else if (row instanceof GuideUiRow.Tool value) {
            return "tool:" + value.requestId() + ":" + value.activity().invocationId();
        } else if (row instanceof GuideUiRow.Status value) {
            return "status:" + value.requestId();
        } else {
            throw new IllegalArgumentException("not a HUD task row");
        }
    }
    private static SemanticLayoutEngine.Measurer measurer(Font font, GuideUiConfig.Density density) {
        return new SemanticLayoutEngine.Measurer() {
            @Override public int width(String text, SemanticLayout.Style style) {
                return font.width(MinecraftComponents.literal(text).withStyle(switch (style) {
                    case EMPHASIS -> ChatFormatting.ITALIC;
                    case STRONG -> ChatFormatting.BOLD;
                    case CODE -> ChatFormatting.GRAY;
                    case REFERENCE -> ChatFormatting.AQUA;
                    case NORMAL -> ChatFormatting.WHITE;
                }));
            }
            @Override public int lineHeight(SemanticLayout.Kind kind) { return kind == SemanticLayout.Kind.HEADING ? 12 : density == GuideUiConfig.Density.COMPACT ? 10 : 11; }
        };
    }
    @Override public void close() { invalidateHits(); nativeViews.close(); layouts.clear(); rows = List.of(); sourceRows = null; }
}
