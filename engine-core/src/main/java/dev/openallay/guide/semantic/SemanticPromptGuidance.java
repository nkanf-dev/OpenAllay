package dev.openallay.guide.semantic;

/** Provider-neutral output guidance for OpenAllay's closed player presentation language. */
public final class SemanticPromptGuidance {
    private SemanticPromptGuidance() {}

    public static String text() {
        StringBuilder catalog = new StringBuilder();
        for (BuiltinRichComponents.PromptCatalogEntry entry
                : BuiltinRichComponents.promptCatalog()) {
            catalog.append("- ").append(entry.type()).append(": ")
                    .append(entry.guidance()).append('\n');
        }
        return String.format("Player-visible output may use ordinary CommonMark prose and two OpenAllay-only syntaxes.\nInline references use [[tw:<kind>|<target>|<optional label>]]. Prefer exact handles returned by tools.\nRaw item, block, fluid, entity, biome, dimension, tag, or key IDs are presentation only and do not create evidence.\nControlled components use one fenced openallay-component JSON object with exactly these envelope fields:\n{\"type\":string,\"properties\":object,\"fallback\":string,\"narration\":string}.\nfallback and narration must be non-empty player-readable text. Do not add envelope or properties fields.\nReferenced items, recipes, and sources are accepted only when their exact IDs/handles came from Tool evidence in this request; never invent or repair them.\nA recipe_grid must copy the complete exact recipe handle from the same request's Tool result. Never author slots, coordinates, textures, widget names, or a recipe layout; OpenAllay binds the handle to trusted native recipe data.\nUse only a component in this closed catalog and follow its exact properties contract:\n%s\nA controlled component is presentation only: it never adds factual authority, permissions, callbacks, or execution.\nUse plain prose when a component would not make the answer clearer.\n", dev.openallay.util.Java8Strings.stripTrailing(catalog.toString()));
    }
}
