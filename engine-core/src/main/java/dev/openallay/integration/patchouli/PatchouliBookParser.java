package dev.openallay.integration.patchouli;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.openallay.client.resource.ClientResource;
import dev.openallay.client.resource.ClientResourceAccess;
import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.knowledge.KnowledgeDiagnostic;
import dev.openallay.knowledge.KnowledgeDocument;
import dev.openallay.knowledge.KnowledgeKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PatchouliBookParser {
    private static final String PREFIX = "patchouli_books";
    private final PatchouliTextNormalizer text = new PatchouliTextNormalizer();

    public PatchouliParseResult parse(ClientResourceAccess resources, String activeLocale) {
        return parse(resources, activeLocale, new EvidenceMetadata(
                DataAuthority.RESOURCE_ASSET,
                DataCompleteness.COMPLETE,
                java.time.Instant.EPOCH,
                "patchouli:resources",
                "patchouli:book_parser",
                "test",
                "common-test",
                Map.of()));
    }

    public PatchouliParseResult parse(
            ClientResourceAccess resources,
            String activeLocale,
            EvidenceMetadata evidence) {
        java.util.Objects.requireNonNull(evidence, "evidence");
        List<ClientResource> selected = resources.selected(PREFIX);
        Map<String, List<EntryResource>> candidates = new LinkedHashMap<>();
        for (ClientResource resource : selected) {
            EntryResource entry = identify(resource);
            if (entry != null) {
                candidates.computeIfAbsent(entry.logicalKey(), ignored -> new ArrayList<>()).add(entry);
            }
        }
        List<String> locales = localeOrder(activeLocale);
        List<KnowledgeDocument> documents = new ArrayList<>();
        Map<String, PatchouliMultiblock> multiblocks = new LinkedHashMap<>();
        List<KnowledgeDiagnostic> diagnostics = new ArrayList<>();
        for (List<EntryResource> localized : candidates.values()) {
            EntryResource chosen = choose(localized, locales);
            if (chosen == null) {
                continue;
            }
            parseEntry(chosen, evidence, documents, multiblocks, diagnostics);
        }
        return new PatchouliParseResult(documents, multiblocks, diagnostics);
    }

    private void parseEntry(
            EntryResource entry,
            EvidenceMetadata baseEvidence,
            List<KnowledgeDocument> documents,
            Map<String, PatchouliMultiblock> multiblocks,
            List<KnowledgeDiagnostic> diagnostics) {
        String sourceId = "patchouli:" + entry.namespace + "/" + entry.book;
        EvidenceMetadata evidence = entryEvidence(baseEvidence, entry);
        try {
            JsonObject json = dev.openallay.json.JsonTrees.parse(entry.resource.content()).getAsJsonObject();
            if (!visibilityIsKnown(json)) {
                diagnostics.add(diagnostic(sourceId, "visibility_unresolved",
                        "Excluded config/advancement-gated Patchouli entry " + entry.documentId,
                        entry.resource.provenance()));
                return;
            }
            if (booleanValue(json, "hidden")) {
                return;
            }
            String title = text.normalize(string(json, "name", entry.documentId));
            List<String> body = new ArrayList<>();
            body.add(title);
            Set<String> items = new LinkedHashSet<>();
            Set<String> recipes = new LinkedHashSet<>();
            String structureRef = null;
            List<String> unknownPages = new ArrayList<>();
            collectIdentifier(json.get("icon"), items);
            JsonArray pages = array(json, "pages");
            for (int index = 0; index < pages.size(); index++) {
                JsonElement element = pages.get(index);
                if (!element.isJsonObject()) {
                    diagnostics.add(diagnostic(sourceId, "malformed_page",
                            "Page " + index + " is not an object", entry.resource.provenance()));
                    continue;
                }
                JsonObject page = element.getAsJsonObject();
                append(page, "title", body);
                append(page, "text", body);
                append(page, "description", body);
                collectIdentifier(page.get("item"), items);
                collectIdentifier(page.get("items"), items);
                collectIdentifier(page.get("recipe"), recipes);
                collectIdentifier(page.get("recipes"), recipes);
                String type = string(page, "type", "unknown");
                if (type.endsWith("multiblock") && page.has("multiblock")) {
                    String id = sourceId + ":" + entry.documentId + "#page-" + index;
                    PatchouliMultiblock parsed = parseMultiblock(id, page.get("multiblock"),
                            entry.resource.provenance(), evidence, diagnostics, sourceId);
                    if (parsed != null) {
                        multiblocks.put(id, parsed);
                        structureRef = id;
                    }
                } else if (!knownPage(type)) {
                    unknownPages.add(page.toString());
                    diagnostics.add(diagnostic(sourceId, "unknown_page_type",
                            "Preserved unsupported page type " + type, entry.resource.provenance()));
                }
            }
            String provenance = entry.resource.provenance() + ",locale=" + entry.locale;
            if (!unknownPages.isEmpty()) {
                provenance += ",unknownPages=" + unknownPages;
            }
            documents.add(new KnowledgeDocument(
                    sourceId,
                    entry.documentId,
                    structureRef == null ? KnowledgeKind.GUIDE_ENTRY : KnowledgeKind.STRUCTURE,
                    title,
                    String.join("\n\n", body.stream().filter(value -> !value.isBlank()).toList()),
                    entry.namespace,
                    items,
                    recipes,
                    structureRef,
                    true,
                    provenance,
                    evidence));
        } catch (RuntimeException failure) {
            diagnostics.add(diagnostic(sourceId, "malformed_entry",
                    "Could not parse " + entry.documentId + ": " + failure.getMessage(),
                    entry.resource.provenance()));
        }
    }

    private PatchouliMultiblock parseMultiblock(
            String id,
            JsonElement value,
            String provenance,
            EvidenceMetadata evidence,
            List<KnowledgeDiagnostic> diagnostics,
            String sourceId) {
        if (!value.isJsonObject()) {
            diagnostics.add(diagnostic(sourceId, "external_multiblock_unresolved",
                    "Multiblock reference is not embedded: " + value, provenance));
            return null;
        }
        JsonObject object = value.getAsJsonObject();
        List<PatchouliMultiblock.Block> blocks = new ArrayList<>();
        if (object.has("blocks") && object.get("blocks").isJsonArray()) {
            for (JsonElement blockElement : object.getAsJsonArray("blocks")) {
                JsonObject block = blockElement.getAsJsonObject();
                blocks.add(new PatchouliMultiblock.Block(
                        block.get("x").getAsInt(),
                        block.get("y").getAsInt(),
                        block.get("z").getAsInt(),
                        string(block, "state", string(block, "block", "minecraft:air"))));
            }
        } else if (object.has("pattern") && object.has("mapping")) {
            JsonObject mapping = object.getAsJsonObject("mapping");
            JsonArray layers = object.getAsJsonArray("pattern");
            for (int y = 0; y < layers.size(); y++) {
                JsonArray rows = layers.get(y).getAsJsonArray();
                for (int z = 0; z < rows.size(); z++) {
                    String row = rows.get(z).getAsString();
                    for (int x = 0; x < row.length(); x++) {
                        String symbol = String.valueOf(row.charAt(x));
                        if (!symbol.isBlank() && mapping.has(symbol)) {
                            blocks.add(new PatchouliMultiblock.Block(
                                    x, y, z, state(mapping.get(symbol))));
                        }
                    }
                }
            }
        }
        if (blocks.isEmpty()) {
            diagnostics.add(diagnostic(sourceId, "malformed_multiblock",
                    "Embedded multiblock contains no resolvable blocks", provenance));
            return null;
        }
        return new PatchouliMultiblock(id, blocks, provenance, evidence);
    }

    private static EvidenceMetadata entryEvidence(
            EvidenceMetadata base, EntryResource entry) {
        java.util.TreeMap<String, String> details = new java.util.TreeMap<>(base.details());
        details.put("patchouli:resource", entry.resource.resourceId());
        details.put("patchouli:resource_provenance", entry.resource.provenance());
        details.put("patchouli:locale", entry.locale);
        return new EvidenceMetadata(
                base.authority(),
                base.completeness(),
                base.capturedAt(),
                base.sourceId(),
                base.provenance(),
                base.gameVersion(),
                base.loader(),
                details);
    }

    private static String state(JsonElement value) {
        if (value.isJsonPrimitive()) {
            return value.getAsString();
        }
        JsonObject object = value.getAsJsonObject();
        return string(object, "block", object.toString());
    }

    private void append(JsonObject object, String field, List<String> output) {
        if (object.has(field) && object.get(field).isJsonPrimitive()) {
            String normalized = text.normalize(object.get(field).getAsString());
            if (!normalized.isBlank()) {
                output.add(normalized);
            }
        }
    }

    private static void collectIdentifier(JsonElement value, Set<String> output) {
        if (value == null || value.isJsonNull()) {
            return;
        }
        if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(element -> collectIdentifier(element, output));
            return;
        }
        if (!value.isJsonPrimitive()) {
            return;
        }
        String candidate = value.getAsString();
        int nbt = candidate.indexOf('{');
        if (nbt >= 0) {
            candidate = candidate.substring(0, nbt);
        }
        int count = candidate.indexOf(' ');
        if (count >= 0) {
            candidate = candidate.substring(0, count);
        }
        if (candidate.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            output.add(candidate);
        }
    }

    private static boolean visibilityIsKnown(JsonObject json) {
        return !json.has("advancement")
                && !json.has("flag")
                && !json.has("config_flag")
                && !json.has("conditions");
    }

    private static boolean knownPage(String type) {
        return type.endsWith("text")
                || type.endsWith("spotlight")
                || type.endsWith("crafting")
                || type.endsWith("smelting")
                || type.endsWith("relations")
                || type.endsWith("image")
                || type.endsWith("entity")
                || type.endsWith("empty");
    }

    private static EntryResource identify(ClientResource resource) {
        String[] segments = resource.path().split("/");
        if (segments.length < 5
                || !segments[0].equals("patchouli_books")
                || !segments[3].equals("entries")
                || !resource.path().endsWith(".json")) {
            return null;
        }
        String document = String.join("/", java.util.Arrays.copyOfRange(segments, 4, segments.length));
        document = document.substring(0, document.length() - 5);
        return new EntryResource(
                resource.namespace(), segments[1], segments[2], document, resource);
    }

    private static List<String> localeOrder(String active) {
        LinkedHashSet<String> locales = new LinkedHashSet<>();
        if (active != null && !active.isBlank()) {
            locales.add(active.toLowerCase(java.util.Locale.ROOT));
        }
        locales.add("zh_cn");
        locales.add("en_us");
        return List.copyOf(locales);
    }

    private static EntryResource choose(List<EntryResource> candidates, List<String> locales) {
        for (String locale : locales) {
            for (EntryResource candidate : candidates) {
                if (candidate.locale.equals(locale)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static JsonArray array(JsonObject object, String field) {
        return object.has(field) && object.get(field).isJsonArray()
                ? object.getAsJsonArray(field)
                : new JsonArray();
    }

    private static String string(JsonObject object, String field, String fallback) {
        return object.has(field) && object.get(field).isJsonPrimitive()
                ? object.get(field).getAsString()
                : fallback;
    }

    private static boolean booleanValue(JsonObject object, String field) {
        return object.has(field) && object.get(field).isJsonPrimitive() && object.get(field).getAsBoolean();
    }

    private static KnowledgeDiagnostic diagnostic(
            String source, String code, String message, String provenance) {
        return new KnowledgeDiagnostic(source, code, message, provenance);
    }

    @dev.openallay.value.ValueType(EntryResource.ValueSchemaProvider.class)
private static final class EntryResource {
    private final String namespace;
    private final String book;
    private final String locale;
    private final String documentId;
    private final ClientResource resource;
    private EntryResource(String namespace, String book, String locale, String documentId, ClientResource resource) {
        this.namespace = namespace;
        this.book = book;
        this.locale = locale;
        this.documentId = documentId;
        this.resource = resource;
    }
    public String namespace() { return namespace; }
    public String book() { return book; }
    public String locale() { return locale; }
    public String documentId() { return documentId; }
    public ClientResource resource() { return resource; }
private String logicalKey() {
            return namespace + ":" + book + ":" + documentId;
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EntryResource)) return false;
        EntryResource that = (EntryResource) other;
        return java.util.Objects.equals(namespace, that.namespace) && java.util.Objects.equals(book, that.book) && java.util.Objects.equals(locale, that.locale) && java.util.Objects.equals(documentId, that.documentId) && java.util.Objects.equals(resource, that.resource);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(namespace);
        hash = 31 * hash + java.util.Objects.hashCode(book);
        hash = 31 * hash + java.util.Objects.hashCode(locale);
        hash = 31 * hash + java.util.Objects.hashCode(documentId);
        hash = 31 * hash + java.util.Objects.hashCode(resource);
        return hash;
    }
    @Override public String toString() { return "EntryResource[namespace=" + namespace + ", book=" + book + ", locale=" + locale + ", documentId=" + documentId + ", resource=" + resource + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<EntryResource> schema() {
            return new dev.openallay.value.ValueSchema<>(EntryResource.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<EntryResource>>asList(new dev.openallay.value.ValueSchema.Component<>(EntryResource.class, "namespace", EntryResource::namespace), new dev.openallay.value.ValueSchema.Component<>(EntryResource.class, "book", EntryResource::book), new dev.openallay.value.ValueSchema.Component<>(EntryResource.class, "locale", EntryResource::locale), new dev.openallay.value.ValueSchema.Component<>(EntryResource.class, "documentId", EntryResource::documentId), new dev.openallay.value.ValueSchema.Component<>(EntryResource.class, "resource", EntryResource::resource)), arguments -> new EntryResource((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (ClientResource) arguments[4]));
        }
    }
}
}
