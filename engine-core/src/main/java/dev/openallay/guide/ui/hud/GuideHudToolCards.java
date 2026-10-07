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
 recipes = Map.copyOf(recipes);
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
            if (card instanceof GuideDetailCard.ItemGrid grid) {
                paragraph(blocks, key + ":title", translate.apply(grid.titleKey()));
                for (int i = 0; i < grid.items().size(); i++) {
                    GuideItemView item = grid.items().get(i);
                    String id = id(key + ":item:" + i);
                    RichComponent.ItemRow component = new RichComponent.ItemRow(id,
                            List.of(item(item, origin)), item.displayName() + " ×" + item.count(),
                            item.displayName() + " ×" + item.count());
                    blocks.add(new SemanticBlock.Component(id, component));
                }
            } else if (card instanceof GuideDetailCard.Recipe recipe) {
                String id = id(key);
                GuideRecipeCard value = recipe.recipe();
                String label = value.outputs().isEmpty() ? value.id() : value.outputs().get(0).displayName();
                RichComponent.RecipeGrid component = new RichComponent.RecipeGrid(
                        id, value.reference(), origin, label, label, label);
                blocks.add(new SemanticBlock.Component(id, component));
                recipes.put(id, value);
            } else if (card instanceof GuideDetailCard.Table table) {
                paragraph(blocks, key + ":title", translate.apply(table.titleKey()));
                blocks.add(new SemanticBlock.Table(id(key), tableRow(key + ":header", table.columns()),
                        java.util.stream.IntStream.range(0, table.rows().size())
                                .mapToObj(i -> tableRow(key + ":row:" + i, table.rows().get(i))).toList()));
            } else if (card instanceof GuideDetailCard.KeyValue values) {
                paragraph(blocks, key + ":title", translate.apply(values.titleKey()));
                int entry = 0;
                for (GuideDetailCard.DataCell cell : values.entries()) {
                    paragraph(blocks, key + ":entry:" + entry++, cell.key() + ": " + cell.value());
                }
            } else if (card instanceof GuideDetailCard.DataPreview preview) {
                paragraph(blocks, key + ":title", translate.apply(preview.titleKey()));
                int entry = 0;
                for (GuideDetailCard.DataRow data : preview.rows()) {
                    for (GuideDetailCard.DataCell cell : data.cells()) {
                        paragraph(blocks, key + ":entry:" + entry++, cell.key() + ": " + cell.value());
                    }
                }
            } else if (card instanceof GuideDetailCard.Text text) {
                paragraph(blocks, key + ":title", translate.apply(text.titleKey()));
                for (int i = 0; i < text.lines().size(); i++) paragraph(blocks, key + ":line:" + i, text.lines().get(i));
            } else if (card instanceof GuideDetailCard.Error error) {
                paragraph(blocks, key, error.message());
            } else if (card instanceof GuideDetailCard.Requirements requirements) {
                paragraph(blocks, key, translate.apply(requirements.craftable()
                        ? "screen.openallay.craftability.ready" : "screen.openallay.craftability.missing"));
                int entry = 0;
                for (GuideDetailCard.Requirement requirement : requirements.requirements()) {
                    String requirementKey = key + ":requirement:" + entry++;
                    paragraph(blocks, requirementKey, requirement.key() + " "
                            + requirement.allocated() + "/" + requirement.required());
                    List<GuideItemView> items = new ArrayList<>(requirement.allocatedItems());
                    items.addAll(requirement.alternatives());
                    if (!items.isEmpty()) {
                        String id = id(requirementKey + ":items");
                        blocks.add(new SemanticBlock.Component(id, new RichComponent.ItemRow(id,
                                items.stream().map(item -> item(item, origin)).toList(),
                                items.toString(), requirement.key())));
                    }
                }
            } else {
                throw new IncompatibleClassChangeError();
            }
        }
        return new Projection(SemanticDocument.of(blocks, List.of()), recipes);
    }

    private static RichComponent.Item item(GuideItemView item, String origin) {
        return new RichComponent.Item(item.itemId(), item.count(), item.displayName(), origin);
    }
    private static SemanticBlock.TableRow tableRow(String key, List<String> values) {
        return new SemanticBlock.TableRow(java.util.stream.IntStream.range(0, values.size())
                .mapToObj(i -> new SemanticBlock.TableCell(SemanticBlock.Alignment.LEFT,
                        List.of(new SemanticInline.Text(id(key + ":" + i), values.get(i))))).toList());
    }
    private static void paragraph(List<SemanticBlock> blocks, String key, String text) {
        blocks.add(new SemanticBlock.Paragraph(id(key),
                List.of(new SemanticInline.Text(id(key + ":text"), text))));
    }
    private static String id(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
