package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source boundary checks supplement pure layout tests; they are not graphical acceptance. */
final class GuideToolSummaryIntegrationTest {
    @Test void toolRowsPaintAndMeasureTheSameCompactSummaryGeometry() throws Exception {
        String source = screen();
        String row = between(source, "    private int renderRow(", "    static Component factualRowText(");
        assertTrue(row.contains("return renderToolSummaryCard(graphics, tool, x, y, width, mouseX, mouseY)"));
        String measured = between(source, "    private int measureRow(",
                "    private dev.openallay.guide.ui.GuideToolSummaryGeometry toolSummaryGeometry(");
        assertTrue(measured.contains("toolSummaryGeometry(tool, 0, 0, width).rowHeight()"));
        String geometry = between(source,
                "    private dev.openallay.guide.ui.GuideToolSummaryGeometry toolSummaryGeometry(",
                "    private int renderToolSummaryCard(");
        assertTrue(geometry.contains("GuideToolSummaryPresenter.project(tool)"));
        assertTrue(geometry.contains("summary.capsules().stream()"));
        assertTrue(geometry.contains("GuideToolSummaryGeometry.measure(x, y, width"));
        assertTrue(geometry.contains("summary.status().translationKey()"));
        assertTrue(geometry.contains("capsules, summary.hasDescription(), rowSpacing()"));
        String card = summaryCard(source);
        assertTrue(card.contains("var geometry = toolSummaryGeometry(tool, x, y, width)"));
        assertTrue(card.contains("GuideUiLayout.Rect card = geometry.card()"));
        assertTrue(card.contains("return y + geometry.rowHeight()"));
        assertTrue(card.contains("geometry.title()"));
        assertTrue(card.contains("geometry.status()"));
        assertTrue(card.contains("geometry.description()"));
        assertTrue(card.contains("geometry.capsules().get(index)"));
    }

    @Test void summaryUsesLiteralIntentAndActualStatusWithoutInlineResultsOrFoldControls() throws Exception {
        String source = screen();
        String card = summaryCard(source);
        assertTrue(card.contains("switch (summary.status())"));
        assertTrue(card.contains("case FAILED ->"));
        assertTrue(card.contains("case SUCCEEDED ->"));
        assertTrue(card.contains("case RUNNING ->"));
        assertTrue(card.contains("case NO_RESULT_RECORDED ->"));
        assertTrue(card.contains("intentTitle(tool.detail().intent(), summary.titleKey())"));
        assertTrue(card.contains("if (summary.hasDescription())"));
        assertTrue(card.contains("Component.literal(summary.description())"));
        assertTrue(card.contains("Component.translatable(summary.status().translationKey())"));
        String title = between(source, "    private static Component intentTitle(",
                "    static Component toolDescription(");
        assertTrue(title.contains("intent.title().isEmpty()"));
        assertTrue(title.contains("Component.translatable(titleKey) : Component.literal(intent.title())"));
        assertFalse(card.contains("semanticRenderer.render("));
        assertFalse(card.contains("detailCard("));
        assertFalse(card.contains("tool.detail().cards()"));
        assertFalse(card.contains("toolResultMessages("));
        assertFalse(card.contains("normalized()"));
        assertFalse(card.contains("DEBUG_GSON"));
        assertFalse(card.contains("expandedDetails"));
        assertFalse(card.contains("toggle("));
        assertFalse(card.contains("collapse"));
        assertFalse(card.contains("() -> {}"));
    }

    @Test void nativeCapsuleActionsPrecedeWholeCardDetailAndHitsAreViewportClipped() throws Exception {
        String source = screen();
        String card = summaryCard(source);
        assertBefore(card, "renderToolSummaryCapsule(", "toolSummaryHit(card, () -> open(tool)");
        assertTrue(card.contains("toolSummaryHit(card, () -> open(tool), summary.id()"),
                "blank card space opens the actual Tool detail, not an inert hit");
        String capsule = summaryCapsule(source);
        assertTrue(capsule.contains("MinecraftSemanticRenderer.Intent intent = toolSummaryCapsuleIntent(capsule)"));
        String capsuleIntent = between(source, "    private MinecraftSemanticRenderer.Intent toolSummaryCapsuleIntent(",
                "    private Map<String, Object> toolSummaryCapsuleReceipt(");
        assertTrue(capsuleIntent.contains("MinecraftSemanticRenderer.Intent.BrowseRecipes(value.item().itemId())"));
        assertTrue(capsuleIntent.contains("MinecraftSemanticRenderer.Intent.ExactRecipe(value.recipe().references().stream()"));
        assertTrue(capsuleIntent.contains("filter(recipeClient::supportsExact)"));
        assertTrue(capsule.contains("toolSummaryHit(bounds, () -> semanticIntent(intent), capsule.id()"));
        String hit = between(source, "    private void toolSummaryHit(",
                "    private void renderToolSummaryText(");
        assertTrue(hit.contains("GuideUiLayout.Rect viewport = layout.transcript()"));
        assertTrue(hit.contains("Math.max(bounds.x(), viewport.x())"));
        assertTrue(hit.contains("Math.max(bounds.y(), viewport.y())"));
        assertTrue(hit.contains("Math.min(bounds.right(), viewport.right())"));
        assertTrue(hit.contains("Math.min(bounds.bottom(), viewport.bottom())"));
        assertTrue(hit.contains("if (right > left && bottom > top)"));
        assertTrue(hit.contains("HitKind.CONTENT, action, id, narration"));
        String mouse = between(source, "    public boolean guideMouseClicked(",
                "    protected void paintGuideScreen(");
        String contentLoop = mouse.substring(mouse.lastIndexOf("for (Hit hit : List.copyOf(hits))"));
        assertBefore(contentLoop, "hit.rect().contains(event.x(), event.y())", "hit.action().run()");
        assertBefore(contentLoop, "hit.action().run()", "return true");
        String intent = between(source, "    private void semanticIntent(",
                "    private static String semanticIntentNarration(");
        assertTrue(intent.contains("recipeClient.openRecipes(value.itemId())"));
        assertTrue(intent.contains("recipeClient.openExact(value.reference())"));
        assertFalse(capsule.contains("service.submit("));
        assertFalse(intent.contains("service.submit("));
    }

    @Test void capsuleReceiptRequiresActualNativeIconPaintAndVisibleBounds() throws Exception {
        String source = screen();
        String capsule = summaryCapsule(source);
        String nativeIcon = between(capsule, "        if (!stack.isEmpty()) {",
                "        renderToolSummaryText(");
        assertTrue(nativeIcon.contains("graphics.item(stack, bounds.x() + 1, bounds.y())"));
        assertTrue(nativeIcon.contains("graphics.itemDecorations(font, stack, bounds.x() + 1, bounds.y())"));
        assertBefore(capsule, "graphics.item(stack", "boolean painted =");
        assertTrue(capsule.contains("boolean painted = !stack.isEmpty() && intersects(bounds, layout.transcript())"));
        assertTrue(capsule.contains("if (painted && Boolean.getBoolean(\"openallay.e2e.enabled\")) renderedSummaryCapsuleIds.add(capsule.id())"));
        assertTrue(capsule.contains("return painted"));
        String card = summaryCard(source);
        String paintedIds = between(card, "        List<String> paintedCapsules = new ArrayList<>();",
                "        // Child semantic actions");
        assertBefore(paintedIds, "if (renderToolSummaryCapsule(", "paintedCapsules.add(capsule.id())");
        assertTrue(card.contains("intersects(card, layout.transcript())"));
        assertBefore(card, "intersects(card, layout.transcript())", "renderedToolIds.add(summary.id())");
        String transcript = between(source, "    private void renderTranscript(", "    private int renderRow(");
        assertFalse(transcript.contains("renderedToolIds.add("),
                "nominal virtual-row spacing is not actual summary paint");
        assertTrue(card.contains("receipt.put(\"capsuleIds\", List.copyOf(paintedCapsules))"));
        assertTrue(card.contains("receipt.put(\"capsules\", List.copyOf(capsuleReceipts))"));
        assertTrue(card.contains("receipt.put(\"bounds\", toolPaintBounds(card))"));
        assertTrue(card.contains("receipt.put(\"titleBounds\", toolPaintBounds(geometry.title()))"));
        assertTrue(card.contains("receipt.put(\"blankClickX\", card.x() + 2)"));
        assertTrue(card.contains("receipt.put(\"blankClickY\", visibleTop + (visibleBottom - visibleTop) / 2)"));
        assertTrue(card.contains("renderedToolSummaries.add(Map.copyOf(receipt))"));
        assertBefore(paintedIds, "if (renderToolSummaryCapsule(",
                "capsuleReceipts.add(toolSummaryCapsuleReceipt(capsule, capsuleBounds))");
        String capsuleReceipt = between(source, "    private Map<String, Object> toolSummaryCapsuleReceipt(",
                "    private static Map<String, Integer> toolPaintBounds(");
        assertTrue(capsuleReceipt.contains("MinecraftSemanticRenderer.Intent intent = toolSummaryCapsuleIntent(capsule)"),
                "receipt action is the same native intent used by the hit target");
        assertTrue(capsuleReceipt.contains("receipt.put(\"originInvocationId\", capsule.originInvocationId())"));
        assertTrue(capsuleReceipt.contains("receipt.put(\"reference\", exact.reference())"));
        assertTrue(source.contains("receipt.put(\"summaryCapsuleIds\", List.copyOf(renderedSummaryCapsuleIds))"));
    }

    @Test void completeDetailUsesIndependentNativeRecipeRegistryAndReportsOnlyRealPaint() throws Exception {
        String source = screen();
        assertTrue(source.contains("private NativeDomainViewRegistry detailNativeViews = new NativeDomainViewRegistry()"));
        String frame = between(source, "    private void renderDetail(", "    private void renderDetailContent(");
        assertBefore(frame, "detailNativeViews.beginFrame()", "renderDetailContent(graphics, mouseX, mouseY)");
        assertTrue(frame.contains("finally"));
        assertBefore(frame, "renderDetailContent(graphics, mouseX, mouseY)", "detailNativeViews.endFrame()");
        assertFalse(frame.contains("nativeViews.beginFrame()"));
        String transcript = between(source, "    private void renderTranscript(", "    private int renderRow(");
        assertTrue(transcript.contains("nativeViews.beginFrame()"));
        assertTrue(transcript.contains("nativeViews.endFrame()"));
        assertFalse(transcript.contains("detailNativeViews.beginFrame()"));
        String detail = between(source, "    private void renderDetailContent(", "    enum DetailSection");
        assertTrue(detail.contains("for (int cardIndex = 0; cardIndex < toolDetail.cards().size(); cardIndex++)"));
        assertTrue(detail.contains("y = detailCard(graphics, card, cardId, detail, y, mouseX, mouseY)"));
        assertTrue(detail.contains("long paintBefore = detailCardPaintSerial"));
        assertBefore(detail, "long paintBefore = detailCardPaintSerial", "y = detailCard(graphics, card, cardId");
        assertBefore(detail, "y = detailCard(graphics, card, cardId", "detailCardPaintSerial > paintBefore");
        assertBefore(detail, "detailCardPaintSerial > paintBefore", "renderedDetailCardIds.add(cardId)");
        assertFalse(detail.contains("visibleDetail(cardTop, y - cardTop, detail)"),
                "nominal detail-card height is not actual paint");
        assertTrue(detail.contains("sourceGroups(graphics, selectedTool.activity().sources()"));
        String dispatch = between(source, "    private int detailCard(", "    private int tableCard(");
        assertTrue(dispatch.contains("case GuideDetailCard.Recipe recipe ->"));
        assertTrue(dispatch.contains("recipeCard(graphics, recipe.recipe(), cardId, detail, y, mouseX, mouseY)"));
        String recipe = between(source, "    private int recipeCard(", "    private int recipeAction(");
        assertTrue(recipe.contains("toolDetailRecipeNodeId(cardId), card.reference()"),
                "semantic recipe nodes need a hash, not the player-facing card identity");
        assertTrue(recipe.contains("new NativeDomainViewBinding.Recipe(cardId, component, card)"),
                "native lifecycle and receipts keep the actual Tool/card origin identity");
        String nativeNode = between(source, "    private static String toolDetailRecipeNodeId(",
                "    private int recipeAction(");
        assertTrue(nativeNode.contains("java.util.HexFormat.of().formatHex"));
        assertTrue(nativeNode.contains("MessageDigest.getInstance(\"SHA-256\")"));
        assertTrue(nativeNode.contains("cardId.getBytes(java.nio.charset.StandardCharsets.UTF_8)"));
        assertTrue(recipe.contains("boolean painted = detailNativeViews.render(binding, new NativeDomainView.RenderContext("));
        assertTrue(recipe.contains("if (painted && Boolean.getBoolean(\"openallay.e2e.enabled\"))"));
        assertTrue(recipe.contains("if (painted) detailCardPaintSerial++"));
        assertBefore(recipe, "boolean painted = detailNativeViews.render(", "renderedDetailNativeRecipeIds.add(binding.stableId())");
        assertTrue(recipe.contains("renderedResultCardIds.add(binding.stableId())"));
        assertFalse(recipe.contains("nativeViews.render("));
        assertTrue(recipe.contains("GuideRecipeDetailFacts.project(card)"));
        assertTrue(recipe.contains("for (GuideRecipeCard.Output output : card.outputs())"));
        assertTrue(recipe.contains("recipeClient.openRecipes(output.itemId())"));
        assertTrue(recipe.contains("recipeClient.openUsages(output.itemId())"));
        assertTrue(recipe.contains("recipeClient.openExact(exact.orElseThrow())"));
        assertTrue(source.contains("detailNativeViews.tick()"));
        assertTrue(source.contains("detailNativeViews.close()"));
        assertTrue(source.contains("receipt.put(\"detailNativeRecipeIds\", List.copyOf(renderedDetailNativeRecipeIds))"));
    }

    private static String summaryCard(String source) {
        return between(source, "    private int renderToolSummaryCard(", "    private boolean renderToolSummaryCapsule(");
    }

    private static String summaryCapsule(String source) {
        return between(source, "    private boolean renderToolSummaryCapsule(",
                "    private MinecraftSemanticRenderer.Intent toolSummaryCapsuleIntent(");
    }

    private static void assertBefore(String source, String first, String second) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        assertTrue(firstIndex >= 0, first);
        assertTrue(secondIndex > firstIndex, first + " must precede " + second);
    }

    private static String screen() throws Exception {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path root = current.getFileName() != null && current.getFileName().toString().equals("common")
                ? current.getParent() : current;
        return Files.readString(root.resolve("common/src/main/java/dev/openallay/client/gui/OpenAllayScreen.java"));
    }

    private static String between(String source, String from, String to) {
        int start = source.indexOf(from);
        assertTrue(start >= 0, from);
        int end = source.indexOf(to, start + from.length());
        assertTrue(end > start, to);
        return source.substring(start, end);
    }
}
