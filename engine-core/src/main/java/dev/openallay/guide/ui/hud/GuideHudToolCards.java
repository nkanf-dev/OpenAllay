package dev.openallay.guide.ui.hud;

import dev.openallay.guide.semantic.RichComponent;
import dev.openallay.guide.semantic.SemanticBlock;
import dev.openallay.guide.semantic.SemanticDocument;
import dev.openallay.guide.semantic.SemanticInline;
import dev.openallay.guide.ui.GuideDetailCard;
import dev.openallay.guide.ui.GuideItemView;
import dev.openallay.guide.ui.GuideRecipeCard;
import dev.openallay.guide.ui.GuideUiRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Native semantic adapter of already validated player cards; source cards stay unchanged. */
public final class GuideHudToolCards {
    @dev.openallay.value.ValueType(Projection.ValueSchemaProvider.class)
public static final class Projection {
    private final SemanticDocument document;
    private final Map<String, GuideRecipeCard> recipes;
    public Projection(SemanticDocument document, Map<String, GuideRecipeCard> recipes) {
 recipes = dev.openallay.util.Java8Collections.mapCopyOf(recipes);
        this.document = document;
        this.recipes = recipes;
    }
    public SemanticDocument document() { return document; }
    public Map<String, GuideRecipeCard> recipes() { return recipes; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Projection)) return false;
        Projection that = (Projection) other;
        return java.util.Objects.equals(document, that.document) && java.util.Objects.equals(recipes, that.recipes);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(document);
        hash = 31 * hash + java.util.Objects.hashCode(recipes);
        return hash;
    }
    @Override public String toString() { return "Projection[document=" + document + ", recipes=" + recipes + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Projection> schema() {
            return new dev.openallay.value.ValueSchema<>(Projection.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Projection>>asList(new dev.openallay.value.ValueSchema.Component<>(Projection.class, "document", Projection::document), new dev.openallay.value.ValueSchema.Component<>(Projection.class, "recipes", Projection::recipes)), arguments -> new Projection((SemanticDocument) arguments[0], (Map) arguments[1]));
        }
    }
}
    private GuideHudToolCards() {}

    public static Projection project(GuideUiRow.Tool tool, Function<String, String> translate) {
        ArrayList<SemanticBlock> blocks = new ArrayList<>();
        Map<String, GuideRecipeCard> recipes = new LinkedHashMap<>();
        String origin = tool.activity().invocationId();
        String row = "tool:" + tool.requestId() + ":" + origin;
        int index = 0;
        for (GuideDetailCard card : tool.detail().cards()) {
            String key = row + ":card:" + index++;
            java.util.Objects.requireNonNull(card);
            {
final java.lang.Object $oaPattern0_value = card;
final boolean $oaPattern0_match = $oaPattern0_value instanceof GuideDetailCard.ItemGrid;
GuideDetailCard.ItemGrid $oaPattern0_bound = $oaPattern0_match ? (GuideDetailCard.ItemGrid) $oaPattern0_value : null;
if ($oaPattern0_match) {
                paragraph(blocks, key + ":title", translate.apply($oaPattern0_bound.titleKey()));
                for (int i = 0; i < $oaPattern0_bound.items().size(); i++) {
                    GuideItemView item = $oaPattern0_bound.items().get(i);
                    String id = id(key + ":item:" + i);
                    RichComponent.ItemRow component = new RichComponent.ItemRow(id,
                            dev.openallay.util.Java8Collections.listOf(item(item, origin)), item.displayName() + " ×" + item.count(),
                            item.displayName() + " ×" + item.count());
                    blocks.add(new SemanticBlock.Component(id, component));
                }
            } else {
final java.lang.Object $oaPattern1_value = card;
final boolean $oaPattern1_match = $oaPattern1_value instanceof GuideDetailCard.Recipe;
GuideDetailCard.Recipe $oaPattern1_bound = $oaPattern1_match ? (GuideDetailCard.Recipe) $oaPattern1_value : null;
if ($oaPattern1_match) {
                String id = id(key);
                GuideRecipeCard value = $oaPattern1_bound.recipe();
                String label = value.outputs().isEmpty() ? value.id() : value.outputs().get(0).displayName();
                RichComponent.RecipeGrid component = new RichComponent.RecipeGrid(
                        id, value.reference(), origin, label, label, label);
                blocks.add(new SemanticBlock.Component(id, component));
                recipes.put(id, value);
            } else {
final java.lang.Object $oaPattern2_value = card;
final boolean $oaPattern2_match = $oaPattern2_value instanceof GuideDetailCard.Table;
GuideDetailCard.Table $oaPattern2_bound = $oaPattern2_match ? (GuideDetailCard.Table) $oaPattern2_value : null;
if ($oaPattern2_match) {
                paragraph(blocks, key + ":title", translate.apply($oaPattern2_bound.titleKey()));
                blocks.add(new SemanticBlock.Table(id(key), tableRow(key + ":header", $oaPattern2_bound.columns()),
                        dev.openallay.util.Java8Collections.toList(java.util.stream.IntStream.range(0, $oaPattern2_bound.rows().size())
                                .mapToObj(i -> tableRow(key + ":row:" + i, $oaPattern2_bound.rows().get(i))))));
            } else {
final java.lang.Object $oaPattern3_value = card;
final boolean $oaPattern3_match = $oaPattern3_value instanceof GuideDetailCard.KeyValue;
GuideDetailCard.KeyValue $oaPattern3_bound = $oaPattern3_match ? (GuideDetailCard.KeyValue) $oaPattern3_value : null;
if ($oaPattern3_match) {
                paragraph(blocks, key + ":title", translate.apply($oaPattern3_bound.titleKey()));
                int entry = 0;
                for (GuideDetailCard.DataCell cell : $oaPattern3_bound.entries()) {
                    paragraph(blocks, key + ":entry:" + entry++, cell.key() + ": " + cell.value());
                }
            } else {
final java.lang.Object $oaPattern4_value = card;
final boolean $oaPattern4_match = $oaPattern4_value instanceof GuideDetailCard.DataPreview;
GuideDetailCard.DataPreview $oaPattern4_bound = $oaPattern4_match ? (GuideDetailCard.DataPreview) $oaPattern4_value : null;
if ($oaPattern4_match) {
                paragraph(blocks, key + ":title", translate.apply($oaPattern4_bound.titleKey()));
                int entry = 0;
                for (GuideDetailCard.DataRow data : $oaPattern4_bound.rows()) {
                    for (GuideDetailCard.DataCell cell : data.cells()) {
                        paragraph(blocks, key + ":entry:" + entry++, cell.key() + ": " + cell.value());
                    }
                }
            } else {
final java.lang.Object $oaPattern5_value = card;
final boolean $oaPattern5_match = $oaPattern5_value instanceof GuideDetailCard.Text;
GuideDetailCard.Text $oaPattern5_bound = $oaPattern5_match ? (GuideDetailCard.Text) $oaPattern5_value : null;
if ($oaPattern5_match) {
                paragraph(blocks, key + ":title", translate.apply($oaPattern5_bound.titleKey()));
                for (int i = 0; i < $oaPattern5_bound.lines().size(); i++) paragraph(blocks, key + ":line:" + i, $oaPattern5_bound.lines().get(i));
            } else {
final java.lang.Object $oaPattern6_value = card;
final boolean $oaPattern6_match = $oaPattern6_value instanceof GuideDetailCard.Error;
GuideDetailCard.Error $oaPattern6_bound = $oaPattern6_match ? (GuideDetailCard.Error) $oaPattern6_value : null;
if ($oaPattern6_match) {
                paragraph(blocks, key, $oaPattern6_bound.message());
            } else {
final java.lang.Object $oaPattern7_value = card;
final boolean $oaPattern7_match = $oaPattern7_value instanceof GuideDetailCard.Requirements;
GuideDetailCard.Requirements $oaPattern7_bound = $oaPattern7_match ? (GuideDetailCard.Requirements) $oaPattern7_value : null;
if ($oaPattern7_match) {
                paragraph(blocks, key, translate.apply($oaPattern7_bound.craftable()
                        ? "screen.openallay.craftability.ready" : "screen.openallay.craftability.missing"));
                int entry = 0;
                for (GuideDetailCard.Requirement requirement : $oaPattern7_bound.requirements()) {
                    String requirementKey = key + ":requirement:" + entry++;
                    paragraph(blocks, requirementKey, requirement.key() + " "
                            + requirement.allocated() + "/" + requirement.required());
                    List<GuideItemView> items = new ArrayList<>(requirement.allocatedItems());
                    items.addAll(requirement.alternatives());
                    if (!items.isEmpty()) {
                        String id = id(requirementKey + ":items");
                        blocks.add(new SemanticBlock.Component(id, new RichComponent.ItemRow(id,
                                dev.openallay.util.Java8Collections.toList(items.stream().map(item -> item(item, origin))),
                                items.toString(), requirement.key())));
                    }
                }
            } else {
                throw new IncompatibleClassChangeError();
            }
}
}
}
}
}
}
}
}
        }
        return new Projection(SemanticDocument.of(blocks, dev.openallay.util.Java8Collections.listOf()), recipes);
    }

    private static RichComponent.Item item(GuideItemView item, String origin) {
        return new RichComponent.Item(item.itemId(), item.count(), item.displayName(), origin);
    }
    private static SemanticBlock.TableRow tableRow(String key, List<String> values) {
        return new SemanticBlock.TableRow(dev.openallay.util.Java8Collections.toList(java.util.stream.IntStream.range(0, values.size())
                .mapToObj(i -> new SemanticBlock.TableCell(SemanticBlock.Alignment.LEFT,
                        dev.openallay.util.Java8Collections.listOf(new SemanticInline.Text(id(key + ":" + i), values.get(i)))))));
    }
    private static void paragraph(List<SemanticBlock> blocks, String key, String text) {
        blocks.add(new SemanticBlock.Paragraph(id(key),
                dev.openallay.util.Java8Collections.listOf(new SemanticInline.Text(id(key + ":text"), text))));
    }
    private static String id(String value) {
        try {
            return dev.openallay.util.Java8Hex.formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
