package dev.openallay.guide.semantic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.openallay.context.RecipeReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Strict durable codec for the OpenAllay-owned semantic AST. */
public final class SemanticDocumentCodec {
    private static final Set<String> DOCUMENT_FIELDS =
            Set.of("blocks", "fallbackText", "diagnostics");
    private static final Set<String> DIAGNOSTIC_FIELDS = Set.of("code", "nodeId");
    private static final Set<String> REFERENCE_FIELDS =
            Set.of("kind", "target", "label", "grounded", "originInvocationId");
    private static final Set<String> COMPONENT_FIELDS =
            Set.of("type", "nodeId", "properties", "fallbackText", "narration");

    public String encode(SemanticDocument document) {
        return encodeObject(document).toString();
    }

    public JsonObject encodeObject(SemanticDocument document) {
        java.util.Objects.requireNonNull(document, "document");
        requireUniqueNodeIds(document);
        JsonObject object = new JsonObject();
        object.add("blocks", blocks(document.blocks()));
        object.addProperty("fallbackText", document.fallbackText());
        JsonArray diagnostics = new JsonArray();
        for (SemanticDiagnostic diagnostic : document.diagnostics()) {
            JsonObject encoded = new JsonObject();
            encoded.addProperty("code", diagnostic.code());
            encoded.addProperty("nodeId", diagnostic.nodeId());
            diagnostics.add(encoded);
        }
        object.add("diagnostics", diagnostics);
        return object;
    }

    public SemanticDocument decode(String json) {
        return decodeObject(object(dev.openallay.json.JsonTrees.parse(json), "semantic document"));
    }

    public SemanticDocument decodeObject(JsonObject object) {
        exact(object, DOCUMENT_FIELDS, "semantic document");
        List<SemanticDiagnostic> diagnostics = new ArrayList<>();
        for (JsonElement value : array(object, "diagnostics")) {
            JsonObject encoded = object(value, "semantic diagnostic");
            exact(encoded, DIAGNOSTIC_FIELDS, "semantic diagnostic");
            diagnostics.add(new SemanticDiagnostic(
                    string(encoded, "code"), string(encoded, "nodeId")));
        }
        SemanticDocument document = new SemanticDocument(
                decodeBlocks(array(object, "blocks")),
                string(object, "fallbackText"),
                diagnostics);
        requireUniqueNodeIds(document);
        return document;
    }

    private static JsonArray blocks(List<SemanticBlock> values) {
        JsonArray encoded = new JsonArray();
        values.forEach(value -> encoded.add(block(value)));
        return encoded;
    }

    private static JsonObject block(SemanticBlock value) {
        JsonObject object = typed(value.nodeId());
        java.util.Objects.requireNonNull(value);
        final class $oaPattern0_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Paragraph bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticBlock.Paragraph && (($oaPattern0_holder.bound = (SemanticBlock.Paragraph) $oaPattern0_holder.value) != null))) {
            object.addProperty("type", "paragraph");
            object.add("content", inlines($oaPattern0_holder.bound.content()));
        } else {
final class $oaPattern1_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Heading bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticBlock.Heading && (($oaPattern1_holder.bound = (SemanticBlock.Heading) $oaPattern1_holder.value) != null))) {
            object.addProperty("type", "heading");
            object.addProperty("level", $oaPattern1_holder.bound.level());
            object.add("content", inlines($oaPattern1_holder.bound.content()));
        } else {
final class $oaPattern2_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.ListBlock bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticBlock.ListBlock && (($oaPattern2_holder.bound = (SemanticBlock.ListBlock) $oaPattern2_holder.value) != null))) {
            object.addProperty("type", "list");
            object.addProperty("ordered", $oaPattern2_holder.bound.ordered());
            object.addProperty("start", $oaPattern2_holder.bound.start());
            JsonArray items = new JsonArray();
            $oaPattern2_holder.bound.items().forEach(item -> items.add(blocks(item)));
            object.add("items", items);
        } else {
final class $oaPattern3_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Quote bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if ((($oaPattern3_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticBlock.Quote && (($oaPattern3_holder.bound = (SemanticBlock.Quote) $oaPattern3_holder.value) != null))) {
            object.addProperty("type", "quote");
            object.add("content", blocks($oaPattern3_holder.bound.content()));
        } else {
final class $oaPattern4_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.CodeBlock bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticBlock.CodeBlock && (($oaPattern4_holder.bound = (SemanticBlock.CodeBlock) $oaPattern4_holder.value) != null))) {
            object.addProperty("type", "code");
            object.addProperty("info", $oaPattern4_holder.bound.info());
            object.addProperty("code", $oaPattern4_holder.bound.code());
        } else {
final class $oaPattern5_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Table bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
if ((($oaPattern5_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticBlock.Table && (($oaPattern5_holder.bound = (SemanticBlock.Table) $oaPattern5_holder.value) != null))) {
            object.addProperty("type", "table");
            object.add("header", row($oaPattern5_holder.bound.header()));
            JsonArray rows = new JsonArray();
            $oaPattern5_holder.bound.rows().forEach(valueRow -> rows.add(row(valueRow)));
            object.add("rows", rows);
        } else {
final class $oaPattern6_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.ThematicBreak bound; }
final $oaPattern6_Holder $oaPattern6_holder = new $oaPattern6_Holder();
if ((($oaPattern6_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticBlock.ThematicBreak && (($oaPattern6_holder.bound = (SemanticBlock.ThematicBreak) $oaPattern6_holder.value) != null))) {
            object.addProperty("type", "thematic_break");
        } else {
final class $oaPattern7_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Component bound; }
final $oaPattern7_Holder $oaPattern7_holder = new $oaPattern7_Holder();
if ((($oaPattern7_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticBlock.Component && (($oaPattern7_holder.bound = (SemanticBlock.Component) $oaPattern7_holder.value) != null))) {
            object.addProperty("type", "component");
            object.add("component", component($oaPattern7_holder.bound.component()));
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
        return object;
    }

    private static List<SemanticBlock> decodeBlocks(JsonArray values) {
        List<SemanticBlock> decoded = new ArrayList<>();
        for (JsonElement value : values) {
            JsonObject object = object(value, "semantic block");
            String type = string(object, "type");
            String nodeId = string(object, "nodeId");
            decoded.add(switch (type) {
                case "paragraph" -> {
                    exact(object, Set.of("type", "nodeId", "content"), "paragraph");
                    yield new SemanticBlock.Paragraph(
                            nodeId, decodeInlines(array(object, "content")));
                }
                case "heading" -> {
                    exact(object, Set.of("type", "nodeId", "level", "content"), "heading");
                    yield new SemanticBlock.Heading(
                            nodeId, integer(object, "level"),
                            decodeInlines(array(object, "content")));
                }
                case "list" -> {
                    exact(object, Set.of("type", "nodeId", "ordered", "start", "items"), "list");
                    List<List<SemanticBlock>> items = new ArrayList<>();
                    for (JsonElement item : array(object, "items")) {
                        if (!item.isJsonArray()) {
                            throw new IllegalArgumentException("semantic list item must be an array");
                        }
                        items.add(decodeBlocks(item.getAsJsonArray()));
                    }
                    yield new SemanticBlock.ListBlock(
                            nodeId, bool(object, "ordered"), integer(object, "start"), items);
                }
                case "quote" -> {
                    exact(object, Set.of("type", "nodeId", "content"), "quote");
                    yield new SemanticBlock.Quote(nodeId, decodeBlocks(array(object, "content")));
                }
                case "code" -> {
                    exact(object, Set.of("type", "nodeId", "info", "code"), "code block");
                    yield new SemanticBlock.CodeBlock(
                            nodeId, string(object, "info"), string(object, "code"));
                }
                case "table" -> {
                    exact(object, Set.of("type", "nodeId", "header", "rows"), "table");
                    List<SemanticBlock.TableRow> rows = new ArrayList<>();
                    for (JsonElement row : array(object, "rows")) {
                        rows.add(decodeRow(object(row, "table row")));
                    }
                    yield new SemanticBlock.Table(
                            nodeId,
                            decodeRow(object(object.get("header"), "table header")),
                            rows);
                }
                case "thematic_break" -> {
                    exact(object, Set.of("type", "nodeId"), "thematic break");
                    yield new SemanticBlock.ThematicBreak(nodeId);
                }
                case "component" -> {
                    exact(object, Set.of("type", "nodeId", "component"), "component block");
                    yield new SemanticBlock.Component(
                            nodeId, decodeComponent(object(object.get("component"), "component")));
                }
                default -> throw new IllegalArgumentException("unknown semantic block type " + type);
            });
        }
        return List.copyOf(decoded);
    }

    private static JsonArray inlines(List<SemanticInline> values) {
        JsonArray encoded = new JsonArray();
        for (SemanticInline value : values) {
            JsonObject object = typed(value.nodeId());
            java.util.Objects.requireNonNull(value);
            final class $oaPattern8_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Text bound; }
final $oaPattern8_Holder $oaPattern8_holder = new $oaPattern8_Holder();
if ((($oaPattern8_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticInline.Text && (($oaPattern8_holder.bound = (SemanticInline.Text) $oaPattern8_holder.value) != null))) {
                object.addProperty("type", "text");
                object.addProperty("text", $oaPattern8_holder.bound.text());
            } else {
final class $oaPattern9_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Emphasis bound; }
final $oaPattern9_Holder $oaPattern9_holder = new $oaPattern9_Holder();
if ((($oaPattern9_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticInline.Emphasis && (($oaPattern9_holder.bound = (SemanticInline.Emphasis) $oaPattern9_holder.value) != null))) {
                object.addProperty("type", "emphasis");
                object.add("children", inlines($oaPattern9_holder.bound.children()));
            } else {
final class $oaPattern10_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Strong bound; }
final $oaPattern10_Holder $oaPattern10_holder = new $oaPattern10_Holder();
if ((($oaPattern10_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticInline.Strong && (($oaPattern10_holder.bound = (SemanticInline.Strong) $oaPattern10_holder.value) != null))) {
                object.addProperty("type", "strong");
                object.add("children", inlines($oaPattern10_holder.bound.children()));
            } else {
final class $oaPattern11_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Code bound; }
final $oaPattern11_Holder $oaPattern11_holder = new $oaPattern11_Holder();
if ((($oaPattern11_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticInline.Code && (($oaPattern11_holder.bound = (SemanticInline.Code) $oaPattern11_holder.value) != null))) {
                object.addProperty("type", "code");
                object.addProperty("text", $oaPattern11_holder.bound.text());
            } else {
final class $oaPattern12_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Break bound; }
final $oaPattern12_Holder $oaPattern12_holder = new $oaPattern12_Holder();
if ((($oaPattern12_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticInline.Break && (($oaPattern12_holder.bound = (SemanticInline.Break) $oaPattern12_holder.value) != null))) {
                object.addProperty("type", "break");
                object.addProperty("hard", $oaPattern12_holder.bound.hard());
            } else {
final class $oaPattern13_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Reference bound; }
final $oaPattern13_Holder $oaPattern13_holder = new $oaPattern13_Holder();
if ((($oaPattern13_holder.value = value) instanceof dev.openallay.guide.semantic.SemanticInline.Reference && (($oaPattern13_holder.bound = (SemanticInline.Reference) $oaPattern13_holder.value) != null))) {
                object.addProperty("type", "reference");
                object.add("reference", reference($oaPattern13_holder.bound.reference()));
            } else {
                throw new IncompatibleClassChangeError();
            }
}
}
}
}
}
            encoded.add(object);
        }
        return encoded;
    }

    private static List<SemanticInline> decodeInlines(JsonArray values) {
        List<SemanticInline> decoded = new ArrayList<>();
        for (JsonElement value : values) {
            JsonObject object = object(value, "semantic inline");
            String type = string(object, "type");
            String nodeId = string(object, "nodeId");
            decoded.add(switch (type) {
                case "text" -> {
                    exact(object, Set.of("type", "nodeId", "text"), "text inline");
                    yield new SemanticInline.Text(nodeId, string(object, "text"));
                }
                case "emphasis" -> {
                    exact(object, Set.of("type", "nodeId", "children"), "emphasis inline");
                    yield new SemanticInline.Emphasis(
                            nodeId, decodeInlines(array(object, "children")));
                }
                case "strong" -> {
                    exact(object, Set.of("type", "nodeId", "children"), "strong inline");
                    yield new SemanticInline.Strong(
                            nodeId, decodeInlines(array(object, "children")));
                }
                case "code" -> {
                    exact(object, Set.of("type", "nodeId", "text"), "code inline");
                    yield new SemanticInline.Code(nodeId, string(object, "text"));
                }
                case "break" -> {
                    exact(object, Set.of("type", "nodeId", "hard"), "break inline");
                    yield new SemanticInline.Break(nodeId, bool(object, "hard"));
                }
                case "reference" -> {
                    exact(object, Set.of("type", "nodeId", "reference"), "reference inline");
                    yield new SemanticInline.Reference(
                            nodeId, decodeReference(object(object.get("reference"), "reference")));
                }
                default -> throw new IllegalArgumentException("unknown semantic inline type " + type);
            });
        }
        return List.copyOf(decoded);
    }

    private static JsonObject reference(SemanticReference value) {
        JsonObject object = new JsonObject();
        object.addProperty("kind", value.kind().name());
        object.addProperty("target", value.target());
        object.addProperty("label", value.label());
        object.addProperty("grounded", value.grounded());
        if (value.originInvocationId() == null) {
            object.add("originInvocationId", JsonNull.INSTANCE);
        } else {
            object.addProperty("originInvocationId", value.originInvocationId());
        }
        return object;
    }

    private static SemanticReference decodeReference(JsonObject object) {
        exact(object, REFERENCE_FIELDS, "semantic reference");
        return new SemanticReference(
                enumValue(SemanticReferenceKind.class, string(object, "kind"), "reference kind"),
                string(object, "target"),
                string(object, "label"),
                bool(object, "grounded"),
                nullableString(object, "originInvocationId"));
    }

    private static JsonObject row(SemanticBlock.TableRow value) {
        JsonObject object = new JsonObject();
        JsonArray cells = new JsonArray();
        for (SemanticBlock.TableCell cell : value.cells()) {
            JsonObject encoded = new JsonObject();
            encoded.addProperty("alignment", cell.alignment().name());
            encoded.add("content", inlines(cell.content()));
            cells.add(encoded);
        }
        object.add("cells", cells);
        return object;
    }

    private static SemanticBlock.TableRow decodeRow(JsonObject object) {
        exact(object, Set.of("cells"), "table row");
        List<SemanticBlock.TableCell> cells = new ArrayList<>();
        for (JsonElement value : array(object, "cells")) {
            JsonObject cell = object(value, "table cell");
            exact(cell, Set.of("alignment", "content"), "table cell");
            cells.add(new SemanticBlock.TableCell(
                    enumValue(SemanticBlock.Alignment.class,
                            string(cell, "alignment"), "table alignment"),
                    decodeInlines(array(cell, "content"))));
        }
        return new SemanticBlock.TableRow(cells);
    }

    private static JsonObject component(RichComponent value) {
        JsonObject object = new JsonObject();
        object.addProperty("type", componentType(value));
        object.addProperty("nodeId", value.nodeId());
        object.add("properties", componentProperties(value));
        object.addProperty("fallbackText", value.fallbackText());
        object.addProperty("narration", value.narration());
        return object;
    }

    private static String componentType(RichComponent value) {
        java.util.Objects.requireNonNull(value);
        final class $oaPattern14_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.ItemRow bound; }
final $oaPattern14_Holder $oaPattern14_holder = new $oaPattern14_Holder();
if ((($oaPattern14_holder.value = value) instanceof dev.openallay.guide.semantic.RichComponent.ItemRow && (($oaPattern14_holder.bound = (RichComponent.ItemRow) $oaPattern14_holder.value) != null))) {
            return "item_row";
        } else {
final class $oaPattern15_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.RecipeGrid bound; }
final $oaPattern15_Holder $oaPattern15_holder = new $oaPattern15_Holder();
if ((($oaPattern15_holder.value = value) instanceof dev.openallay.guide.semantic.RichComponent.RecipeGrid && (($oaPattern15_holder.bound = (RichComponent.RecipeGrid) $oaPattern15_holder.value) != null))) {
            return "recipe_grid";
        } else {
final class $oaPattern16_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.IngredientCheck bound; }
final $oaPattern16_Holder $oaPattern16_holder = new $oaPattern16_Holder();
if ((($oaPattern16_holder.value = value) instanceof dev.openallay.guide.semantic.RichComponent.IngredientCheck && (($oaPattern16_holder.bound = (RichComponent.IngredientCheck) $oaPattern16_holder.value) != null))) {
            return "ingredient_check";
        } else {
final class $oaPattern17_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.CraftabilitySummary bound; }
final $oaPattern17_Holder $oaPattern17_holder = new $oaPattern17_Holder();
if ((($oaPattern17_holder.value = value) instanceof dev.openallay.guide.semantic.RichComponent.CraftabilitySummary && (($oaPattern17_holder.bound = (RichComponent.CraftabilitySummary) $oaPattern17_holder.value) != null))) {
            return "craftability_summary";
        } else {
final class $oaPattern18_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.ProgressSteps bound; }
final $oaPattern18_Holder $oaPattern18_holder = new $oaPattern18_Holder();
if ((($oaPattern18_holder.value = value) instanceof dev.openallay.guide.semantic.RichComponent.ProgressSteps && (($oaPattern18_holder.bound = (RichComponent.ProgressSteps) $oaPattern18_holder.value) != null))) {
            return "progress_steps";
        } else {
final class $oaPattern19_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.SourceSummary bound; }
final $oaPattern19_Holder $oaPattern19_holder = new $oaPattern19_Holder();
if ((($oaPattern19_holder.value = value) instanceof dev.openallay.guide.semantic.RichComponent.SourceSummary && (($oaPattern19_holder.bound = (RichComponent.SourceSummary) $oaPattern19_holder.value) != null))) {
            return "source_summary";
        } else {
final class $oaPattern20_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.StatusBadge bound; }
final $oaPattern20_Holder $oaPattern20_holder = new $oaPattern20_Holder();
if ((($oaPattern20_holder.value = value) instanceof dev.openallay.guide.semantic.RichComponent.StatusBadge && (($oaPattern20_holder.bound = (RichComponent.StatusBadge) $oaPattern20_holder.value) != null))) {
            return "status_badge";
        } else {
final class $oaPattern21_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.ChoiceGroup bound; }
final $oaPattern21_Holder $oaPattern21_holder = new $oaPattern21_Holder();
if ((($oaPattern21_holder.value = value) instanceof dev.openallay.guide.semantic.RichComponent.ChoiceGroup && (($oaPattern21_holder.bound = (RichComponent.ChoiceGroup) $oaPattern21_holder.value) != null))) {
            return "choice_group";
        }
}
}
}
}
}
}
}
        throw new IncompatibleClassChangeError();
    }

    private static JsonObject componentProperties(RichComponent value) {
        JsonObject properties = new JsonObject();
        java.util.Objects.requireNonNull(value);
        final class $oaPattern22_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.ItemRow bound; }
final $oaPattern22_Holder $oaPattern22_holder = new $oaPattern22_Holder();
if ((($oaPattern22_holder.value = value) instanceof dev.openallay.guide.semantic.RichComponent.ItemRow && (($oaPattern22_holder.bound = (RichComponent.ItemRow) $oaPattern22_holder.value) != null))) {
            JsonArray items = new JsonArray();
            $oaPattern22_holder.bound.items().forEach(item -> items.add(item(item)));
            properties.add("items", items);
        } else {
final class $oaPattern23_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.RecipeGrid bound; }
final $oaPattern23_Holder $oaPattern23_holder = new $oaPattern23_Holder();
if ((($oaPattern23_holder.value = value) instanceof dev.openallay.guide.semantic.RichComponent.RecipeGrid && (($oaPattern23_holder.bound = (RichComponent.RecipeGrid) $oaPattern23_holder.value) != null))) {
            recipe(properties, $oaPattern23_holder.bound.recipe(), $oaPattern23_holder.bound.originInvocationId());
            properties.addProperty("label", $oaPattern23_holder.bound.label());
        } else {
final class $oaPattern24_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.IngredientCheck bound; }
final $oaPattern24_Holder $oaPattern24_holder = new $oaPattern24_Holder();
if ((($oaPattern24_holder.value = value) instanceof dev.openallay.guide.semantic.RichComponent.IngredientCheck && (($oaPattern24_holder.bound = (RichComponent.IngredientCheck) $oaPattern24_holder.value) != null))) {
            JsonArray ingredients = new JsonArray();
            for (RichComponent.Ingredient ingredient : $oaPattern24_holder.bound.ingredients()) {
                JsonObject item = new JsonObject();
                item.addProperty("itemId", ingredient.itemId());
                item.addProperty("required", ingredient.required());
                item.addProperty("available", ingredient.available());
                item.addProperty("label", ingredient.label());
                item.addProperty("originInvocationId", ingredient.originInvocationId());
                ingredients.add(item);
            }
            properties.add("ingredients", ingredients);
        } else {
final class $oaPattern25_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.CraftabilitySummary bound; }
final $oaPattern25_Holder $oaPattern25_holder = new $oaPattern25_Holder();
if ((($oaPattern25_holder.value = value) instanceof dev.openallay.guide.semantic.RichComponent.CraftabilitySummary && (($oaPattern25_holder.bound = (RichComponent.CraftabilitySummary) $oaPattern25_holder.value) != null))) {
            recipe(properties, $oaPattern25_holder.bound.recipe(), $oaPattern25_holder.bound.originInvocationId());
            properties.addProperty("craftable", $oaPattern25_holder.bound.craftable());
            properties.addProperty("conclusive", $oaPattern25_holder.bound.conclusive());
            properties.addProperty("requestedCrafts", $oaPattern25_holder.bound.requestedCrafts());
            properties.addProperty("maximumCrafts", $oaPattern25_holder.bound.maximumCrafts());
        } else {
final class $oaPattern26_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.ProgressSteps bound; }
final $oaPattern26_Holder $oaPattern26_holder = new $oaPattern26_Holder();
if ((($oaPattern26_holder.value = value) instanceof dev.openallay.guide.semantic.RichComponent.ProgressSteps && (($oaPattern26_holder.bound = (RichComponent.ProgressSteps) $oaPattern26_holder.value) != null))) {
            JsonArray steps = new JsonArray();
            for (RichComponent.Step step : $oaPattern26_holder.bound.steps()) {
                JsonObject encoded = new JsonObject();
                encoded.addProperty("id", step.id());
                encoded.addProperty("label", step.label());
                encoded.addProperty("state", step.state().name());
                steps.add(encoded);
            }
            properties.add("steps", steps);
        } else {
final class $oaPattern27_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.SourceSummary bound; }
final $oaPattern27_Holder $oaPattern27_holder = new $oaPattern27_Holder();
if ((($oaPattern27_holder.value = value) instanceof dev.openallay.guide.semantic.RichComponent.SourceSummary && (($oaPattern27_holder.bound = (RichComponent.SourceSummary) $oaPattern27_holder.value) != null))) {
            JsonArray sources = new JsonArray();
            for (RichComponent.Source source : $oaPattern27_holder.bound.sources()) {
                JsonObject encoded = new JsonObject();
                encoded.addProperty("sourceId", source.sourceId());
                encoded.addProperty("label", source.label());
                encoded.addProperty("originInvocationId", source.originInvocationId());
                sources.add(encoded);
            }
            properties.add("sources", sources);
        } else {
final class $oaPattern28_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.StatusBadge bound; }
final $oaPattern28_Holder $oaPattern28_holder = new $oaPattern28_Holder();
if ((($oaPattern28_holder.value = value) instanceof dev.openallay.guide.semantic.RichComponent.StatusBadge && (($oaPattern28_holder.bound = (RichComponent.StatusBadge) $oaPattern28_holder.value) != null))) {
            properties.addProperty("state", $oaPattern28_holder.bound.state().name());
            properties.addProperty("label", $oaPattern28_holder.bound.label());
        } else {
final class $oaPattern29_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.ChoiceGroup bound; }
final $oaPattern29_Holder $oaPattern29_holder = new $oaPattern29_Holder();
if ((($oaPattern29_holder.value = value) instanceof dev.openallay.guide.semantic.RichComponent.ChoiceGroup && (($oaPattern29_holder.bound = (RichComponent.ChoiceGroup) $oaPattern29_holder.value) != null))) {
            properties.addProperty("prompt", $oaPattern29_holder.bound.prompt());
            JsonArray choices = new JsonArray();
            for (RichComponent.Choice choice : $oaPattern29_holder.bound.choices()) {
                JsonObject encoded = new JsonObject();
                encoded.addProperty("id", choice.id());
                encoded.addProperty("label", choice.label());
                choices.add(encoded);
            }
            properties.add("choices", choices);
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
        return properties;
    }

    private static RichComponent decodeComponent(JsonObject object) {
        exact(object, COMPONENT_FIELDS, "rich component");
        String type = string(object, "type");
        String nodeId = string(object, "nodeId");
        String fallback = string(object, "fallbackText");
        String narration = string(object, "narration");
        JsonObject properties = object(object.get("properties"), "component properties");
        return switch (type) {
            case "item_row" -> {
                exact(properties, Set.of("items"), "item row properties");
                List<RichComponent.Item> items = new ArrayList<>();
                for (JsonElement value : array(properties, "items")) {
                    JsonObject item = object(value, "component item");
                    exact(item, Set.of("itemId", "count", "label", "originInvocationId"), "component item");
                    items.add(new RichComponent.Item(
                            string(item, "itemId"), longValue(item, "count"),
                            string(item, "label"), string(item, "originInvocationId")));
                }
                yield new RichComponent.ItemRow(nodeId, items, fallback, narration);
            }
            case "recipe_grid" -> {
                exact(properties, Set.of("sourceId", "generation", "recipeId", "originInvocationId", "label"), "recipe grid properties");
                yield new RichComponent.RecipeGrid(
                        nodeId, recipe(properties), string(properties, "originInvocationId"),
                        string(properties, "label"), fallback, narration);
            }
            case "ingredient_check" -> {
                exact(properties, Set.of("ingredients"), "ingredient check properties");
                List<RichComponent.Ingredient> ingredients = new ArrayList<>();
                for (JsonElement value : array(properties, "ingredients")) {
                    JsonObject item = object(value, "component ingredient");
                    exact(item, Set.of("itemId", "required", "available", "label", "originInvocationId"), "component ingredient");
                    ingredients.add(new RichComponent.Ingredient(
                            string(item, "itemId"), longValue(item, "required"),
                            longValue(item, "available"), string(item, "label"),
                            string(item, "originInvocationId")));
                }
                yield new RichComponent.IngredientCheck(nodeId, ingredients, fallback, narration);
            }
            case "craftability_summary" -> {
                exact(properties, Set.of("sourceId", "generation", "recipeId", "originInvocationId", "craftable", "conclusive", "requestedCrafts", "maximumCrafts"), "craftability properties");
                yield new RichComponent.CraftabilitySummary(
                        nodeId, recipe(properties), string(properties, "originInvocationId"),
                        bool(properties, "craftable"), bool(properties, "conclusive"),
                        longValue(properties, "requestedCrafts"),
                        longValue(properties, "maximumCrafts"), fallback, narration);
            }
            case "progress_steps" -> {
                exact(properties, Set.of("steps"), "progress properties");
                List<RichComponent.Step> steps = new ArrayList<>();
                for (JsonElement value : array(properties, "steps")) {
                    JsonObject step = object(value, "component step");
                    exact(step, Set.of("id", "label", "state"), "component step");
                    steps.add(new RichComponent.Step(
                            string(step, "id"), string(step, "label"),
                            enumValue(RichComponent.StepState.class,
                                    string(step, "state"), "step state")));
                }
                yield new RichComponent.ProgressSteps(nodeId, steps, fallback, narration);
            }
            case "source_summary" -> {
                exact(properties, Set.of("sources"), "source summary properties");
                List<RichComponent.Source> sources = new ArrayList<>();
                for (JsonElement value : array(properties, "sources")) {
                    JsonObject source = object(value, "component source");
                    exact(source, Set.of("sourceId", "label", "originInvocationId"), "component source");
                    sources.add(new RichComponent.Source(
                            string(source, "sourceId"), string(source, "label"),
                            string(source, "originInvocationId")));
                }
                yield new RichComponent.SourceSummary(nodeId, sources, fallback, narration);
            }
            case "status_badge" -> {
                exact(properties, Set.of("state", "label"), "status badge properties");
                yield new RichComponent.StatusBadge(
                        nodeId,
                        enumValue(RichComponent.BadgeState.class,
                                string(properties, "state"), "badge state"),
                        string(properties, "label"), fallback, narration);
            }
            case "choice_group" -> {
                exact(properties, Set.of("prompt", "choices"), "choice group properties");
                List<RichComponent.Choice> choices = new ArrayList<>();
                for (JsonElement value : array(properties, "choices")) {
                    JsonObject choice = object(value, "component choice");
                    exact(choice, Set.of("id", "label"), "component choice");
                    choices.add(new RichComponent.Choice(
                            string(choice, "id"), string(choice, "label")));
                }
                yield new RichComponent.ChoiceGroup(
                        nodeId, string(properties, "prompt"), choices, fallback, narration);
            }
            default -> throw new IllegalArgumentException("unknown rich component type " + type);
        };
    }

    private static JsonObject item(RichComponent.Item value) {
        JsonObject item = new JsonObject();
        item.addProperty("itemId", value.itemId());
        item.addProperty("count", value.count());
        item.addProperty("label", value.label());
        item.addProperty("originInvocationId", value.originInvocationId());
        return item;
    }

    private static void recipe(JsonObject object, RecipeReference recipe, String origin) {
        object.addProperty("sourceId", recipe.sourceId());
        object.addProperty("generation", recipe.generation());
        object.addProperty("recipeId", recipe.recipeId());
        object.addProperty("originInvocationId", origin);
    }

    private static RecipeReference recipe(JsonObject object) {
        return new RecipeReference(
                string(object, "sourceId"), string(object, "generation"),
                string(object, "recipeId"));
    }

    private static JsonObject typed(String nodeId) {
        JsonObject object = new JsonObject();
        object.addProperty("nodeId", nodeId);
        return object;
    }

    private static void exact(JsonObject object, Set<String> expected, String label) {
        if (!dev.openallay.json.JsonTrees.keys(object).equals(expected)) {
            Set<String> missing = new java.util.TreeSet<>(expected);
            missing.removeAll(dev.openallay.json.JsonTrees.keys(object));
            Set<String> extra = new java.util.TreeSet<>(dev.openallay.json.JsonTrees.keys(object));
            extra.removeAll(expected);
            throw new IllegalArgumentException(
                    label + " schema mismatch; missing=" + missing + ", extra=" + extra);
        }
    }

    private static JsonObject object(JsonElement value, String label) {
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(label + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return value.getAsJsonArray();
    }

    private static String string(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(field + " must be text");
        }
        return value.getAsString();
    }

    private static String nullableString(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.isJsonNull() ? null : string(object, field);
    }

    private static int integer(JsonObject object, String field) {
        long value = longValue(object, field);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return (int) value;
    }

    private static long longValue(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        try {
            return value.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException | NumberFormatException failure) {
            throw new IllegalArgumentException(field + " must be an integer", failure);
        }
    }

    private static boolean bool(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(field + " must be boolean");
        }
        return value.getAsBoolean();
    }

    private static <E extends Enum<E>> E enumValue(
            Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("unknown semantic " + label + " " + value, failure);
        }
    }

    private static void requireUniqueNodeIds(SemanticDocument document) {
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        document.blocks().forEach(block -> collect(block, ids));
        for (SemanticDiagnostic diagnostic : document.diagnostics()) {
            if (!ids.contains(diagnostic.nodeId())) {
                throw new IllegalArgumentException(
                        "semantic diagnostic refers to an unknown node");
            }
        }
    }

    private static void collect(SemanticBlock block, Set<String> ids) {
        add(block.nodeId(), ids);
        java.util.Objects.requireNonNull(block);
        final class $oaPattern30_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Paragraph bound; }
final $oaPattern30_Holder $oaPattern30_holder = new $oaPattern30_Holder();
if ((($oaPattern30_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.Paragraph && (($oaPattern30_holder.bound = (SemanticBlock.Paragraph) $oaPattern30_holder.value) != null))) {
            $oaPattern30_holder.bound.content().forEach(value -> collect(value, ids));
        } else {
final class $oaPattern31_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Heading bound; }
final $oaPattern31_Holder $oaPattern31_holder = new $oaPattern31_Holder();
if ((($oaPattern31_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.Heading && (($oaPattern31_holder.bound = (SemanticBlock.Heading) $oaPattern31_holder.value) != null))) {
            $oaPattern31_holder.bound.content().forEach(value -> collect(value, ids));
        } else {
final class $oaPattern32_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.ListBlock bound; }
final $oaPattern32_Holder $oaPattern32_holder = new $oaPattern32_Holder();
if ((($oaPattern32_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.ListBlock && (($oaPattern32_holder.bound = (SemanticBlock.ListBlock) $oaPattern32_holder.value) != null))) {
            $oaPattern32_holder.bound.items().forEach(item -> item.forEach(value -> collect(value, ids)));
        } else {
final class $oaPattern33_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Quote bound; }
final $oaPattern33_Holder $oaPattern33_holder = new $oaPattern33_Holder();
if ((($oaPattern33_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.Quote && (($oaPattern33_holder.bound = (SemanticBlock.Quote) $oaPattern33_holder.value) != null))) {
            $oaPattern33_holder.bound.content().forEach(value -> collect(value, ids));
        } else {
final class $oaPattern34_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Table bound; }
final $oaPattern34_Holder $oaPattern34_holder = new $oaPattern34_Holder();
if ((($oaPattern34_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.Table && (($oaPattern34_holder.bound = (SemanticBlock.Table) $oaPattern34_holder.value) != null))) {
            collect($oaPattern34_holder.bound.header(), ids);
            $oaPattern34_holder.bound.rows().forEach(row -> collect(row, ids));
        } else {
final class $oaPattern35_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.CodeBlock bound; }
final $oaPattern35_Holder $oaPattern35_holder = new $oaPattern35_Holder();
if ((($oaPattern35_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.CodeBlock && (($oaPattern35_holder.bound = (SemanticBlock.CodeBlock) $oaPattern35_holder.value) != null))) {
        } else {
final class $oaPattern36_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.ThematicBreak bound; }
final $oaPattern36_Holder $oaPattern36_holder = new $oaPattern36_Holder();
if ((($oaPattern36_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.ThematicBreak && (($oaPattern36_holder.bound = (SemanticBlock.ThematicBreak) $oaPattern36_holder.value) != null))) {
        } else {
final class $oaPattern37_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Component bound; }
final $oaPattern37_Holder $oaPattern37_holder = new $oaPattern37_Holder();
if ((($oaPattern37_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.Component && (($oaPattern37_holder.bound = (SemanticBlock.Component) $oaPattern37_holder.value) != null))) {
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

    private static void collect(SemanticBlock.TableRow row, Set<String> ids) {
        row.cells().forEach(cell -> cell.content().forEach(value -> collect(value, ids)));
    }

    private static void collect(SemanticInline inline, Set<String> ids) {
        add(inline.nodeId(), ids);
        java.util.Objects.requireNonNull(inline);
        final class $oaPattern38_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Emphasis bound; }
final $oaPattern38_Holder $oaPattern38_holder = new $oaPattern38_Holder();
if ((($oaPattern38_holder.value = inline) instanceof dev.openallay.guide.semantic.SemanticInline.Emphasis && (($oaPattern38_holder.bound = (SemanticInline.Emphasis) $oaPattern38_holder.value) != null))) {
            $oaPattern38_holder.bound.children().forEach(value -> collect(value, ids));
        } else {
final class $oaPattern39_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Strong bound; }
final $oaPattern39_Holder $oaPattern39_holder = new $oaPattern39_Holder();
if ((($oaPattern39_holder.value = inline) instanceof dev.openallay.guide.semantic.SemanticInline.Strong && (($oaPattern39_holder.bound = (SemanticInline.Strong) $oaPattern39_holder.value) != null))) {
            $oaPattern39_holder.bound.children().forEach(value -> collect(value, ids));
        } else {
final class $oaPattern40_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Text bound; }
final $oaPattern40_Holder $oaPattern40_holder = new $oaPattern40_Holder();
if ((($oaPattern40_holder.value = inline) instanceof dev.openallay.guide.semantic.SemanticInline.Text && (($oaPattern40_holder.bound = (SemanticInline.Text) $oaPattern40_holder.value) != null))) {
        } else {
final class $oaPattern41_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Code bound; }
final $oaPattern41_Holder $oaPattern41_holder = new $oaPattern41_Holder();
if ((($oaPattern41_holder.value = inline) instanceof dev.openallay.guide.semantic.SemanticInline.Code && (($oaPattern41_holder.bound = (SemanticInline.Code) $oaPattern41_holder.value) != null))) {
        } else {
final class $oaPattern42_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Break bound; }
final $oaPattern42_Holder $oaPattern42_holder = new $oaPattern42_Holder();
if ((($oaPattern42_holder.value = inline) instanceof dev.openallay.guide.semantic.SemanticInline.Break && (($oaPattern42_holder.bound = (SemanticInline.Break) $oaPattern42_holder.value) != null))) {
        } else {
final class $oaPattern43_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Reference bound; }
final $oaPattern43_Holder $oaPattern43_holder = new $oaPattern43_Holder();
if ((($oaPattern43_holder.value = inline) instanceof dev.openallay.guide.semantic.SemanticInline.Reference && (($oaPattern43_holder.bound = (SemanticInline.Reference) $oaPattern43_holder.value) != null))) {
        } else {
            throw new IncompatibleClassChangeError();
        }
}
}
}
}
}
    }

    private static void add(String nodeId, Set<String> ids) {
        if (!ids.add(nodeId)) {
            throw new IllegalArgumentException("semantic node IDs must be unique");
        }
    }
}
