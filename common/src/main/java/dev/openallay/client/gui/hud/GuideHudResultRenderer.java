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
import dev.openallay.platform.minecraft.MinecraftComponents;
import net.minecraft.network.chat.Component;
import dev.openallay.client.gui.GuideTextLine;
import dev.openallay.client.gui.GuideNativeFont;

/** Shared native result viewport for passive HUD pages and explicit compact interaction. */
public final class GuideHudResultRenderer implements AutoCloseable {
    public sealed interface Action permits Action.Tool, Action.Semantic, Action.Sources {
        @dev.openallay.value.ValueType(Tool.ValueSchemaProvider.class)
public static final class Tool implements Action {
    private final String rowId;
    public Tool(String rowId) {
        this.rowId = rowId;
    }
    public String rowId() { return rowId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Tool)) return false;
        Tool that = (Tool) other;
        return java.util.Objects.equals(rowId, that.rowId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(rowId);
        return hash;
    }
    @Override public String toString() { return "Tool[rowId=" + rowId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Tool> schema() {
            return new dev.openallay.value.ValueSchema<>(Tool.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Tool>>asList(new dev.openallay.value.ValueSchema.Component<>(Tool.class, "rowId", Tool::rowId)), arguments -> new Tool((String) arguments[0]));
        }
    }
}
        @dev.openallay.value.ValueType(Semantic.ValueSchemaProvider.class)
public static final class Semantic implements Action {
    private final MinecraftSemanticRenderer.Intent intent;
    public Semantic(MinecraftSemanticRenderer.Intent intent) {
        this.intent = intent;
    }
    public MinecraftSemanticRenderer.Intent intent() { return intent; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Semantic)) return false;
        Semantic that = (Semantic) other;
        return java.util.Objects.equals(intent, that.intent);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(intent);
        return hash;
    }
    @Override public String toString() { return "Semantic[intent=" + intent + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Semantic> schema() {
            return new dev.openallay.value.ValueSchema<>(Semantic.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Semantic>>asList(new dev.openallay.value.ValueSchema.Component<>(Semantic.class, "intent", Semantic::intent)), arguments -> new Semantic((MinecraftSemanticRenderer.Intent) arguments[0]));
        }
    }
}
        @dev.openallay.value.ValueType(Sources.ValueSchemaProvider.class)
public static final class Sources implements Action {
    private final List<GuideSource> sources;
    public Sources(List<GuideSource> sources) {
 sources = List.copyOf(sources);
        this.sources = sources;
    }
    public List<GuideSource> sources() { return sources; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Sources)) return false;
        Sources that = (Sources) other;
        return java.util.Objects.equals(sources, that.sources);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sources);
        return hash;
    }
    @Override public String toString() { return "Sources[sources=" + sources + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Sources> schema() {
            return new dev.openallay.value.ValueSchema<>(Sources.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Sources>>asList(new dev.openallay.value.ValueSchema.Component<>(Sources.class, "sources", Sources::sources)), arguments -> new Sources((List) arguments[0]));
        }
    }
}
    }
    @dev.openallay.value.ValueType(Hit.ValueSchemaProvider.class)
public static final class Hit {
    private final GuideUiLayout.Rect bounds;
    private final Action action;
    private final String narration;
    public Hit(GuideUiLayout.Rect bounds, Action action, String narration) {
        this.bounds = bounds;
        this.action = action;
        this.narration = narration;
    }
    public GuideUiLayout.Rect bounds() { return bounds; }
    public Action action() { return action; }
    public String narration() { return narration; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Hit)) return false;
        Hit that = (Hit) other;
        return java.util.Objects.equals(bounds, that.bounds) && java.util.Objects.equals(action, that.action) && java.util.Objects.equals(narration, that.narration);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(bounds);
        hash = 31 * hash + java.util.Objects.hashCode(action);
        hash = 31 * hash + java.util.Objects.hashCode(narration);
        return hash;
    }
    @Override public String toString() { return "Hit[bounds=" + bounds + ", action=" + action + ", narration=" + narration + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Hit> schema() {
            return new dev.openallay.value.ValueSchema<>(Hit.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Hit>>asList(new dev.openallay.value.ValueSchema.Component<>(Hit.class, "bounds", Hit::bounds), new dev.openallay.value.ValueSchema.Component<>(Hit.class, "action", Hit::action), new dev.openallay.value.ValueSchema.Component<>(Hit.class, "narration", Hit::narration)), arguments -> new Hit((GuideUiLayout.Rect) arguments[0], (Action) arguments[1], (String) arguments[2]));
        }
    }
}
    /** Last extracted frame only. Read-only probes do not cause game or service operations. */
    @dev.openallay.value.ValueType(Receipt.ValueSchemaProvider.class)
public static final class Receipt {
    private final long extractedFrame;
    private final List<String> renderedRowIds;
    private final int assistantRows;
    private final int toolRows;
    private final int toolCards;
    private final int semanticNodes;
    private final int nativeItemNodes;
    private final int recipeNodes;
    private final int contentHeight;
    private final int viewportHeight;
    private final int scroll;
    private final int maximumScroll;
    private final int cacheEntries;
    private final int nativeViews;
    private final List<String> renderedNodeIds;
    private final String lastRenderedText;
    public Receipt(long extractedFrame, List<String> renderedRowIds, int assistantRows, int toolRows, int toolCards, int semanticNodes, int nativeItemNodes, int recipeNodes, int contentHeight, int viewportHeight, int scroll, int maximumScroll, int cacheEntries, int nativeViews, List<String> renderedNodeIds, String lastRenderedText) {

            renderedRowIds = List.copyOf(renderedRowIds);
            renderedNodeIds = List.copyOf(renderedNodeIds);
            java.util.Objects.requireNonNull(lastRenderedText, "lastRenderedText");

        this.extractedFrame = extractedFrame;
        this.renderedRowIds = renderedRowIds;
        this.assistantRows = assistantRows;
        this.toolRows = toolRows;
        this.toolCards = toolCards;
        this.semanticNodes = semanticNodes;
        this.nativeItemNodes = nativeItemNodes;
        this.recipeNodes = recipeNodes;
        this.contentHeight = contentHeight;
        this.viewportHeight = viewportHeight;
        this.scroll = scroll;
        this.maximumScroll = maximumScroll;
        this.cacheEntries = cacheEntries;
        this.nativeViews = nativeViews;
        this.renderedNodeIds = renderedNodeIds;
        this.lastRenderedText = lastRenderedText;
    }
    public long extractedFrame() { return extractedFrame; }
    public List<String> renderedRowIds() { return renderedRowIds; }
    public int assistantRows() { return assistantRows; }
    public int toolRows() { return toolRows; }
    public int toolCards() { return toolCards; }
    public int semanticNodes() { return semanticNodes; }
    public int nativeItemNodes() { return nativeItemNodes; }
    public int recipeNodes() { return recipeNodes; }
    public int contentHeight() { return contentHeight; }
    public int viewportHeight() { return viewportHeight; }
    public int scroll() { return scroll; }
    public int maximumScroll() { return maximumScroll; }
    public int cacheEntries() { return cacheEntries; }
    public int nativeViews() { return nativeViews; }
    public List<String> renderedNodeIds() { return renderedNodeIds; }
    public String lastRenderedText() { return lastRenderedText; }
public Receipt(long extractedFrame, List<String> renderedRowIds, int assistantRows, int toolRows,
                int toolCards, int semanticNodes, int nativeItemNodes, int recipeNodes, int contentHeight, int viewportHeight,
                int scroll, int maximumScroll, int cacheEntries, int nativeViews) {
            this(extractedFrame, renderedRowIds, assistantRows, toolRows, toolCards, semanticNodes,
                    nativeItemNodes, recipeNodes, contentHeight, viewportHeight, scroll, maximumScroll,
                    cacheEntries, nativeViews, List.of(), "");
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Receipt)) return false;
        Receipt that = (Receipt) other;
        return extractedFrame == that.extractedFrame && java.util.Objects.equals(renderedRowIds, that.renderedRowIds) && assistantRows == that.assistantRows && toolRows == that.toolRows && toolCards == that.toolCards && semanticNodes == that.semanticNodes && nativeItemNodes == that.nativeItemNodes && recipeNodes == that.recipeNodes && contentHeight == that.contentHeight && viewportHeight == that.viewportHeight && scroll == that.scroll && maximumScroll == that.maximumScroll && cacheEntries == that.cacheEntries && nativeViews == that.nativeViews && java.util.Objects.equals(renderedNodeIds, that.renderedNodeIds) && java.util.Objects.equals(lastRenderedText, that.lastRenderedText);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(extractedFrame);
        hash = 31 * hash + java.util.Objects.hashCode(renderedRowIds);
        hash = 31 * hash + Integer.hashCode(assistantRows);
        hash = 31 * hash + Integer.hashCode(toolRows);
        hash = 31 * hash + Integer.hashCode(toolCards);
        hash = 31 * hash + Integer.hashCode(semanticNodes);
        hash = 31 * hash + Integer.hashCode(nativeItemNodes);
        hash = 31 * hash + Integer.hashCode(recipeNodes);
        hash = 31 * hash + Integer.hashCode(contentHeight);
        hash = 31 * hash + Integer.hashCode(viewportHeight);
        hash = 31 * hash + Integer.hashCode(scroll);
        hash = 31 * hash + Integer.hashCode(maximumScroll);
        hash = 31 * hash + Integer.hashCode(cacheEntries);
        hash = 31 * hash + Integer.hashCode(nativeViews);
        hash = 31 * hash + java.util.Objects.hashCode(renderedNodeIds);
        hash = 31 * hash + java.util.Objects.hashCode(lastRenderedText);
        return hash;
    }
    @Override public String toString() { return "Receipt[extractedFrame=" + extractedFrame + ", renderedRowIds=" + renderedRowIds + ", assistantRows=" + assistantRows + ", toolRows=" + toolRows + ", toolCards=" + toolCards + ", semanticNodes=" + semanticNodes + ", nativeItemNodes=" + nativeItemNodes + ", recipeNodes=" + recipeNodes + ", contentHeight=" + contentHeight + ", viewportHeight=" + viewportHeight + ", scroll=" + scroll + ", maximumScroll=" + maximumScroll + ", cacheEntries=" + cacheEntries + ", nativeViews=" + nativeViews + ", renderedNodeIds=" + renderedNodeIds + ", lastRenderedText=" + lastRenderedText + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Receipt> schema() {
            return new dev.openallay.value.ValueSchema<>(Receipt.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Receipt>>asList(new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "extractedFrame", Receipt::extractedFrame), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "renderedRowIds", Receipt::renderedRowIds), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "assistantRows", Receipt::assistantRows), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "toolRows", Receipt::toolRows), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "toolCards", Receipt::toolCards), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "semanticNodes", Receipt::semanticNodes), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "nativeItemNodes", Receipt::nativeItemNodes), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "recipeNodes", Receipt::recipeNodes), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "contentHeight", Receipt::contentHeight), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "viewportHeight", Receipt::viewportHeight), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "scroll", Receipt::scroll), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "maximumScroll", Receipt::maximumScroll), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "cacheEntries", Receipt::cacheEntries), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "nativeViews", Receipt::nativeViews), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "renderedNodeIds", Receipt::renderedNodeIds), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "lastRenderedText", Receipt::lastRenderedText)), arguments -> new Receipt((Long) arguments[0], (List) arguments[1], (Integer) arguments[2], (Integer) arguments[3], (Integer) arguments[4], (Integer) arguments[5], (Integer) arguments[6], (Integer) arguments[7], (Integer) arguments[8], (Integer) arguments[9], (Integer) arguments[10], (Integer) arguments[11], (Integer) arguments[12], (Integer) arguments[13], (List) arguments[14], (String) arguments[15]));
        }
    }
}
    @dev.openallay.value.ValueType(Row.ValueSchemaProvider.class)
private static final class Row {
    private final String id;
    private final GuideUiRow source;
    private final String narration;
    private final List<GuideTextLine> header;
    private final SemanticLayout layout;
    private final Map<String, GuideRecipeCard> recipes;
    private final List<GuideTextLine> sources;
    private final int height;
    private Row(String id, GuideUiRow source, String narration, List<GuideTextLine> header, SemanticLayout layout, Map<String, GuideRecipeCard> recipes, List<GuideTextLine> sources, int height) {
        this.id = id;
        this.source = source;
        this.narration = narration;
        this.header = header;
        this.layout = layout;
        this.recipes = recipes;
        this.sources = sources;
        this.height = height;
    }
    public String id() { return id; }
    public GuideUiRow source() { return source; }
    public String narration() { return narration; }
    public List<GuideTextLine> header() { return header; }
    public SemanticLayout layout() { return layout; }
    public Map<String, GuideRecipeCard> recipes() { return recipes; }
    public List<GuideTextLine> sources() { return sources; }
    public int height() { return height; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Row)) return false;
        Row that = (Row) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(source, that.source) && java.util.Objects.equals(narration, that.narration) && java.util.Objects.equals(header, that.header) && java.util.Objects.equals(layout, that.layout) && java.util.Objects.equals(recipes, that.recipes) && java.util.Objects.equals(sources, that.sources) && height == that.height;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(source);
        hash = 31 * hash + java.util.Objects.hashCode(narration);
        hash = 31 * hash + java.util.Objects.hashCode(header);
        hash = 31 * hash + java.util.Objects.hashCode(layout);
        hash = 31 * hash + java.util.Objects.hashCode(recipes);
        hash = 31 * hash + java.util.Objects.hashCode(sources);
        hash = 31 * hash + Integer.hashCode(height);
        return hash;
    }
    @Override public String toString() { return "Row[id=" + id + ", source=" + source + ", narration=" + narration + ", header=" + header + ", layout=" + layout + ", recipes=" + recipes + ", sources=" + sources + ", height=" + height + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Row> schema() {
            return new dev.openallay.value.ValueSchema<>(Row.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Row>>asList(new dev.openallay.value.ValueSchema.Component<>(Row.class, "id", Row::id), new dev.openallay.value.ValueSchema.Component<>(Row.class, "source", Row::source), new dev.openallay.value.ValueSchema.Component<>(Row.class, "narration", Row::narration), new dev.openallay.value.ValueSchema.Component<>(Row.class, "header", Row::header), new dev.openallay.value.ValueSchema.Component<>(Row.class, "layout", Row::layout), new dev.openallay.value.ValueSchema.Component<>(Row.class, "recipes", Row::recipes), new dev.openallay.value.ValueSchema.Component<>(Row.class, "sources", Row::sources), new dev.openallay.value.ValueSchema.Component<>(Row.class, "height", Row::height)), arguments -> new Row((String) arguments[0], (GuideUiRow) arguments[1], (String) arguments[2], (List) arguments[3], (SemanticLayout) arguments[4], (Map) arguments[5], (List) arguments[6], (Integer) arguments[7]));
        }
    }
}
    private final MinecraftSemanticRenderer semantic = new MinecraftSemanticRenderer(new MinecraftSemanticResolver());
    private final SemanticLayoutCache layouts = new SemanticLayoutCache();
    private final GuideHudScrollState scroll = new GuideHudScrollState();
    private final NativeDomainViewRegistry nativeViews = new NativeDomainViewRegistry();
    private final List<Hit> hits = new ArrayList<>();
    private long hitEpoch;
    private PaintedHits paintedHits;
    /** Identity and geometry of the actual interactive extraction, not a synthetic fresh frame. */
    @dev.openallay.value.ValueType(PaintedHits.ValueSchemaProvider.class)
static final class PaintedHits {
    private final long epoch;
    private final Object font;
    private final Object language;
    private final GuideUiLayout.Rect viewport;
    private final int offset;
    PaintedHits(long epoch, Object font, Object language, GuideUiLayout.Rect viewport, int offset) {
        this.epoch = epoch;
        this.font = font;
        this.language = language;
        this.viewport = viewport;
        this.offset = offset;
    }
    public long epoch() { return epoch; }
    public Object font() { return font; }
    public Object language() { return language; }
    public GuideUiLayout.Rect viewport() { return viewport; }
    public int offset() { return offset; }
boolean current(long currentEpoch, Object currentFont, Object currentLanguage,
                GuideUiLayout.Rect currentViewport, int currentOffset) {
            return epoch == currentEpoch && font == currentFont && language == currentLanguage
                    && viewport.equals(currentViewport) && offset == currentOffset;
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PaintedHits)) return false;
        PaintedHits that = (PaintedHits) other;
        return epoch == that.epoch && java.util.Objects.equals(font, that.font) && java.util.Objects.equals(language, that.language) && java.util.Objects.equals(viewport, that.viewport) && offset == that.offset;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(epoch);
        hash = 31 * hash + java.util.Objects.hashCode(font);
        hash = 31 * hash + java.util.Objects.hashCode(language);
        hash = 31 * hash + java.util.Objects.hashCode(viewport);
        hash = 31 * hash + Integer.hashCode(offset);
        return hash;
    }
    @Override public String toString() { return "PaintedHits[epoch=" + epoch + ", font=" + font + ", language=" + language + ", viewport=" + viewport + ", offset=" + offset + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<PaintedHits> schema() {
            return new dev.openallay.value.ValueSchema<>(PaintedHits.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<PaintedHits>>asList(new dev.openallay.value.ValueSchema.Component<>(PaintedHits.class, "epoch", PaintedHits::epoch), new dev.openallay.value.ValueSchema.Component<>(PaintedHits.class, "font", PaintedHits::font), new dev.openallay.value.ValueSchema.Component<>(PaintedHits.class, "language", PaintedHits::language), new dev.openallay.value.ValueSchema.Component<>(PaintedHits.class, "viewport", PaintedHits::viewport), new dev.openallay.value.ValueSchema.Component<>(PaintedHits.class, "offset", PaintedHits::offset)), arguments -> new PaintedHits((Long) arguments[0], (Object) arguments[1], (Object) arguments[2], (GuideUiLayout.Rect) arguments[3], (Integer) arguments[4]));
        }
    }
}
    private List<Row> rows = List.of();
    private List<GuideUiRow> sourceRows;
    private Font cachedFont;
    private Object cachedLanguage;
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
        return paintedHits != null && paintedHits.current(hitEpoch, font, GuideNativeFont.languageIdentity(), viewport, scroll.offset())
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
        Object language = GuideNativeFont.languageIdentity();
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
                        narration = MinecraftComponents.getString(title);
                        header.addAll(GuideNativeFont.split(font, MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(title), " · "), MinecraftComponents.translatable(summary.status().translationKey())), measuredWidth));
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
                        var cards = GuideHudToolCards.project(tool, key -> MinecraftComponents.getString(MinecraftComponents.translatable(key)));
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
                            new Action.Sources(assistant.sources()), MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.evidence.groups", assistant.sources().size()))));
                }
            }
        } finally { graphics.disableScissor(); nativeViews.endFrame(); }
        if (interactive) paintedHits = new PaintedHits(hitEpoch, font, GuideNativeFont.languageIdentity(), viewport, offset);
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
                return GuideNativeFont.width(font, MinecraftComponents.style(MinecraftComponents.literal(text), switch (style) {
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
