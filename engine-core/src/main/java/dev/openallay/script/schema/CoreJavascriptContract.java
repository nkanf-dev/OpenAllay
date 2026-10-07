package dev.openallay.script.schema;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compact model guidance generated from the same declared root graph consumed by Rhino.
 *
 * <p>The renderer never resolves a request value. It intentionally lists the complete stable
 * closed schema; dynamic JSON and Extension-owned values remain discoverable at runtime.
 */
public final class CoreJavascriptContract {
    private CoreJavascriptContract() {}

    public static String render(HostSchemaCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        StringBuilder text = new StringBuilder("The general analysis Tool is run_javascript. Its stable globals are:\n- mc: immutable captured Minecraft data documented below. Access any available property directly, for example mc.player.position or mc.items.filter(...). The runtime resolves data lazily and records actual read origins automatically; no root declarations are needed.\n- schema.list() and schema.describe(path): descriptor-only discovery; path omits the mc. prefix.\n- workspace.open(handle): reopen one complete canonical result from this active request. Include its exact handle in the run_javascript handles input. Handles expire when this request closes; past transcript handles cannot be reopened.\n- helpers.schema(value): inspect one genuinely dynamic JSON or Extension value.\n- require(id): load one exact bundled JavaScript module documented by the current contract or a vertical Skill.\n- commands (optional captured capability): commands.list(), commands.describe(path), and commands.run(text). This is a top-level binding, separate from mc and schema; its availability is stated for this request.\n- world (optional captured capability): call world directly, never mc.world.\n  world.inspect({from:{x,y,z},to:{x,y,z}}, {includeAir:false}) returns blocks, coverage, and evidence.\n  world.entities({from:{x,y,z},to:{x,y,z}}, {type:\"namespace:id\"}) returns entity summaries with request-scoped observationId values, coverage, and evidence.\n  world.entity(observationId) returns detached detail for one entity observed in the same request.\n  world.focus() captures the current target, held items, camera, screen/menu and hovered slot.\n  world.capture() captures the current native WORLD frame before 2D GUI rendering and returns metadata plus an actual image to the next model turn.\n  world.capture({target:\"GAME_UI\"}) captures the currently displayed game UI/HUD; OpenAllay's own foreground chat is not that game UI.\n  world.capture({target:\"ASSOCIATED_UI\"}) reads the retained UI source associated with this input, with its original source time.\n\nHost arrays support non-mutating filter, map, flatMap, slice, reduce, some, and includes.\nCopy a host array before sort, reverse, splice, push, or index assignment.\nPrefer one complete filter/join/aggregate program.\nExecution permission does not change model output budgets. Complete returned data stays in the request workspace; the model receives a labelled view with its size, structure, and a handle for further computation.\nsource is the JavaScript program text, not source attribution. Ordinary computations need no Minecraft read.\nReturned data is the execution result; captured origins are automatic auxiliary metadata, not a success requirement.\n\nDeclared mc schema:\n");
        for (HostSchemaCatalog.RootSummary summary : dev.openallay.util.Java8Collections.toList(catalog.list().stream()
                .sorted(Comparator.comparing(HostSchemaCatalog.RootSummary::name)))) {
            HostRootDescriptor root = catalog.root(summary.name()).orElseThrow(() -> new java.util.NoSuchElementException("No value present"));
            String availability;
            switch (root.availability()) {
                case AVAILABLE: availability = "available"; break;
                case UNAVAILABLE: availability = "unavailable"; break;
                case REQUEST_SCOPED: availability = "request-scoped"; break;
                default: throw new IncompatibleClassChangeError();
            }
            text.append("- mc.")
                    .append(root.name())
                    .append(": ")
                    .append(display(root.schema()))
                    .append(" [availability=")
                    .append(availability)
                    .append(", provider=")
                    .append(root.providerId())
                    .append("] — ")
                    .append(root.summary())
                    .append('\n');
            appendChildren(text, "mc." + root.name(), root.schema(), 1);
        }
        text.append("\nRequest-scoped means availability is decided by the captured request, not that the root failed.\nDynamic JSON/map children can be inspected with schema.describe(path) or helpers.schema(value) when needed.\n");
        return dev.openallay.util.Java8Strings.strip(text.toString());
    }

    private static void appendChildren(
            StringBuilder text, String path, HostSchema schema, int depth) {
        HostSchema.requireKnown(schema);
        if (schema instanceof HostSchema.RecordValue) {
            HostSchema.RecordValue record = (HostSchema.RecordValue) schema;
            List<Map.Entry<String, HostSchema>> fields =
                    new ArrayList<>(record.fields().entrySet());
            fields.sort(Map.Entry.comparingByKey());
            for (Map.Entry<String, HostSchema> field : fields) {
                String childPath = path + "." + field.getKey();
                text.append(dev.openallay.util.Java8Strings.repeat("  ", Math.min(depth, 4)))
                        .append("- ")
                        .append(childPath)
                        .append(": ")
                        .append(display(field.getValue()))
                        .append('\n');
                appendChildren(text, childPath, field.getValue(), depth + 1);
            }
            return;
        }
        if (schema instanceof HostSchema.OptionalValue) {
            HostSchema.OptionalValue optional = (HostSchema.OptionalValue) schema;
            appendChildren(text, path, optional.value(), depth);
            return;
        }
        if (schema instanceof HostSchema.Sequence
                && ((HostSchema.Sequence) schema).elements() instanceof HostSchema.RecordValue) {
            appendChildren(text, path + "[]", ((HostSchema.Sequence) schema).elements(), depth);
        }
    }

    private static String display(HostSchema schema) {
        HostSchema.requireKnown(schema);
        if (schema instanceof HostSchema.Scalar) {
            HostSchema.Scalar scalar = (HostSchema.Scalar) schema;
            return scalar.kind();
        } else if (schema instanceof HostSchema.Enumeration) {
            HostSchema.Enumeration enumeration = (HostSchema.Enumeration) schema;
            return "enum(" + String.join("|", enumeration.values()) + ")";
        } else if (schema instanceof HostSchema.Sequence) {
            HostSchema.Sequence sequence = (HostSchema.Sequence) schema;
            return "array<" + display(sequence.elements()) + ">";
        } else if (schema instanceof HostSchema.OptionalValue) {
            HostSchema.OptionalValue optional = (HostSchema.OptionalValue) schema;
            return display(optional.value()) + "?";
        } else if (schema instanceof HostSchema.Dictionary) {
            HostSchema.Dictionary dictionary = (HostSchema.Dictionary) schema;
            return "map<string," + display(dictionary.values()) + ">";
        } else if (schema instanceof HostSchema.RecordValue) {
            return "record";
        } else if (schema instanceof HostSchema.DynamicJson) {
            return "dynamic-json";
        } else if (schema instanceof HostSchema.DynamicDetached) {
            return "extension-value";
        } else {
            throw new IncompatibleClassChangeError();
        }
    }
}
