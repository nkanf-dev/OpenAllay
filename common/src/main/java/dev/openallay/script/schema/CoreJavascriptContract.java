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
        StringBuilder text = new StringBuilder("""
                The general analysis Tool is run_javascript. Its stable globals are:
                - mc: immutable captured Minecraft data documented below. Access any available property directly, for example mc.player.position or mc.items.filter(...). The runtime resolves data lazily and records actual read origins automatically; no root declarations are needed.
                - schema.list() and schema.describe(path): descriptor-only discovery; path omits the mc. prefix.
                - workspace.open(handle): reopen one exact result from this request.
                - helpers.schema(value): inspect one genuinely dynamic JSON or Extension value.
                - require(id): load one exact bundled JavaScript module documented by the current contract or a vertical Skill.
                - world (optional captured capability): call world directly, never mc.world.
                  world.inspect({from:{x,y,z},to:{x,y,z}}, {includeAir:false}) returns blocks, coverage, and evidence.
                  world.entities({from:{x,y,z},to:{x,y,z}}, {type:"namespace:id"}) returns entity summaries with request-scoped observationId values, coverage, and evidence.
                  world.entity(observationId) returns detached detail for one entity observed in the same request.

                Host arrays support non-mutating filter, map, flatMap, slice, reduce, some, and includes.
                Copy a host array before sort, reverse, splice, push, or index assignment.
                Prefer one complete filter/join/aggregate program and return answer-sized data.
                source is the JavaScript program text, not source attribution. Ordinary computations need no Minecraft read.
                Returned data is the execution result; captured origins are automatic auxiliary metadata, not a success requirement.

                Declared mc schema:
                """);
        for (HostSchemaCatalog.RootSummary summary : catalog.list().stream()
                .sorted(Comparator.comparing(HostSchemaCatalog.RootSummary::name))
                .toList()) {
            HostRootDescriptor root = catalog.root(summary.name()).orElseThrow();
            String availability = switch (root.availability()) {
                case AVAILABLE -> "available";
                case UNAVAILABLE -> "unavailable";
                case REQUEST_SCOPED -> "request-scoped";
            };
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
        text.append("""

                Request-scoped means availability is decided by the captured request, not that the root failed.
                Dynamic JSON/map children can be inspected with schema.describe(path) or helpers.schema(value) when needed.
                """);
        return text.toString().strip();
    }

    private static void appendChildren(
            StringBuilder text, String path, HostSchema schema, int depth) {
        if (schema instanceof HostSchema.RecordValue record) {
            List<Map.Entry<String, HostSchema>> fields =
                    new ArrayList<>(record.fields().entrySet());
            fields.sort(Map.Entry.comparingByKey());
            for (Map.Entry<String, HostSchema> field : fields) {
                String childPath = path + "." + field.getKey();
                text.append("  ".repeat(Math.min(depth, 4)))
                        .append("- ")
                        .append(childPath)
                        .append(": ")
                        .append(display(field.getValue()))
                        .append('\n');
                appendChildren(text, childPath, field.getValue(), depth + 1);
            }
            return;
        }
        if (schema instanceof HostSchema.OptionalValue optional) {
            appendChildren(text, path, optional.value(), depth);
            return;
        }
        if (schema instanceof HostSchema.Sequence sequence
                && sequence.elements() instanceof HostSchema.RecordValue) {
            appendChildren(text, path + "[]", sequence.elements(), depth);
        }
    }

    private static String display(HostSchema schema) {
        return switch (schema) {
            case HostSchema.Scalar scalar -> scalar.kind();
            case HostSchema.Enumeration enumeration ->
                    "enum(" + String.join("|", enumeration.values()) + ")";
            case HostSchema.Sequence sequence -> "array<" + display(sequence.elements()) + ">";
            case HostSchema.OptionalValue optional -> display(optional.value()) + "?";
            case HostSchema.Dictionary dictionary ->
                    "map<string," + display(dictionary.values()) + ">";
            case HostSchema.RecordValue ignored -> "record";
            case HostSchema.DynamicJson ignored -> "dynamic-json";
            case HostSchema.DynamicDetached ignored -> "extension-value";
        };
    }
}
