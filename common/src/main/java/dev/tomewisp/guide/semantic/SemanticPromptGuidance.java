package dev.tomewisp.guide.semantic;

/** Provider-neutral output guidance for TomeWisp's closed player presentation language. */
public final class SemanticPromptGuidance {
    private SemanticPromptGuidance() {}

    public static String text() {
        return """
                Player-visible output may use ordinary CommonMark prose and two TomeWisp-only syntaxes.
                Inline references use [[tw:<kind>|<target>|<optional label>]]. Prefer exact handles returned by tools.
                Raw item, block, fluid, entity, biome, dimension, tag, or key IDs are presentation only and do not create evidence.
                Controlled components use a fenced tomewisp-component JSON block with schemaVersion, type, properties, fallback, and narration.
                Use only registered component types and include complete readable fallback text. A component never adds authority or permissions.
                Do not emit HTML, links, URLs, scripts, callbacks, commands, executable code, arbitrary component types, or invented handles.
                Use plain prose when a component would not make the answer clearer.
                """;
    }
}
