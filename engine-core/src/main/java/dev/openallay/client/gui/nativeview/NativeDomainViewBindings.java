package dev.openallay.client.gui.nativeview;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.openallay.guide.semantic.RichComponent;
import dev.openallay.guide.ui.GuideRecipeCard;
import dev.openallay.guide.ui.GuideRecipePresenter;
import dev.openallay.guide.ui.GuideUiRow;
import dev.openallay.guide.ui.GuideUiView;
import java.util.Optional;

/** Same-request binding from a validated component to its complete normalized Tool result. */
public final class NativeDomainViewBindings {
    private NativeDomainViewBindings() {}

    public static Optional<NativeDomainViewBinding.Recipe> recipe(
            GuideUiView view,
            GuideUiRow.Assistant assistant,
            RichComponent.RecipeGrid component) {
        java.util.Objects.requireNonNull(view, "view");
        java.util.Objects.requireNonNull(assistant, "assistant");
        java.util.Objects.requireNonNull(component, "component");
        return view.rows().stream()
                .filter(GuideUiRow.Tool.class::isInstance)
                .map(GuideUiRow.Tool.class::cast)
                .filter(tool -> tool.requestId().equals(assistant.requestId()))
                .filter(tool -> tool.activity().invocationId().equals(component.originInvocationId()))
                .filter(tool -> "run_javascript".equals(toolName(tool.activity().toolId())))
                .flatMap(tool -> recipeCards(tool.activity().normalized()).stream())
                .filter(card -> card.references().contains(component.recipe()))
                .findFirst()
                .map(card -> new NativeDomainViewBinding.Recipe(
                        "assistant:" + assistant.requestId() + ":" + assistant.ordinal()
                                + ":component:" + component.nodeId(),
                        component,
                        card));
    }

    private static java.util.List<GuideRecipeCard> recipeCards(JsonObject normalized) {
        if (normalized == null
                || !"success".equals(string(normalized, "status"))
                || !normalized.has("value")
                || !normalized.get("value").isJsonObject()) {
            return dev.openallay.util.Java8Collections.listOf();
        }
        JsonObject value = normalized.getAsJsonObject("value");
        if (!"RECIPE".equals(string(value, "viewKind"))) {
            return dev.openallay.util.Java8Collections.listOf();
        }
        JsonElement preview = value.get("preview");
        JsonArray recipes = new JsonArray();
        if (preview != null && preview.isJsonArray()) {
            preview.getAsJsonArray().forEach(recipes::add);
        } else if (preview != null && preview.isJsonObject()) {
            recipes.add(preview);
        }
        return GuideRecipePresenter.cards(recipes);
    }

    private static String string(JsonObject object, String field) {
        return object.has(field) && object.get(field).isJsonPrimitive()
                ? object.get(field).getAsString()
                : "";
    }

    private static String toolName(String toolId) {
        int separator = toolId.indexOf(':');
        return separator < 0 ? toolId : toolId.substring(separator + 1);
    }
}
