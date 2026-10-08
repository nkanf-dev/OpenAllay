package dev.openallay.skill;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Indexes only exact instruction plaintext retained in the actual active projection. */
public final class SkillInstructionContext {
    private static final Pattern HEADER = Pattern.compile(
            "\\Askill_instructions\\n"
                    + "skill: ([^\\n]+)\\n"
                    + "document: ([^\\n]+)\\n"
                    + "source: ([A-Za-z0-9_-]+)\\n"
                    + "fingerprint: ([a-f0-9]{64})\\n"
                    + "state: (content|complete|already_loaded)\\n"
                    + "complete: (true|false)\\n"
                    + "range: ([0-9]+)\\.\\.([0-9]+)\\n"
                    + "content_length: ([0-9]+)\\n");
    private static final String INVALIDATED = "skill_instructions: invalidated\n"
            + "projection_note: prior Skill plaintext is unavailable in the current catalog/context; "
            + "the historical Tool result status is unchanged.";
    private final Map<String, SkillCatalogManifest.Document> documents;

    public SkillInstructionContext(SkillCatalog catalog) {
        this(SkillCatalogManifest.capture(catalog, "local"));
    }

    public SkillInstructionContext(SkillCatalogManifest manifest) {
        Map<String, SkillCatalogManifest.Document> captured = new HashMap<>();
        for (dev.openallay.skill.SkillCatalogManifest.Document document : manifest.documents()) {
            captured.put(id(document.name(), document.document()), document);
        }
        documents = dev.openallay.util.Java8Collections.mapCopyOf(captured);
    }

    public List<ModelMessage> refresh(List<ModelMessage> messages) {
        return refresh(messages, new RetainedSkillContext());
    }

    /** Projection changes never rewrite the original transcript or a real success/error flag. */
    public List<ModelMessage> refresh(List<ModelMessage> messages, RetainedSkillContext retained) {
        Scan scan = scan(messages, retained);
        List<RetainedSkillContext.Range> system = retained.systemRanges();
        RetainedSkillContext.Coverage present = new RetainedSkillContext.Coverage(system);
        present.addAll(scan.ranges());
        RetainedSkillContext.Coverage owned = new RetainedSkillContext.Coverage(system);
        List<ModelMessage> refreshed = new ArrayList<>(messages.size());
        for (ModelMessage message : messages) {
            List<ModelContent> content = new ArrayList<>(message.content().size());
            boolean changed = false;
            for (ModelContent item : message.content()) {
                ModelContent.ToolResult result = item instanceof ModelContent.ToolResult ? (ModelContent.ToolResult) item : null;
                Parsed parsed = result == null ? null : scan.loads().get(result);
                if (result != null && scan.loads().containsKey(result)) {
                    String replacement = null;
                    if (parsed == null || (parsed.state() == LoadSkillTool.LoadState.ALREADY_LOADED
                            && !present.contains(parsed.range()))) {
                        replacement = INVALIDATED;
                    } else if (parsed.state() != LoadSkillTool.LoadState.ALREADY_LOADED) {
                        if (owned.contains(parsed.range())) {
                            replacement = receipt(parsed.document(), parsed.range().offset()).modelText();
                        } else {
                            owned.add(parsed.range());
                        }
                    }
                    if (replacement != null && !result.value().equals(new JsonPrimitive(replacement))) {
                        content.add(new ModelContent.ToolResult(result.toolUseId(),
                                new JsonPrimitive(replacement), result.error(), result.images()));
                        changed = true;
                        continue;
                    }
                }
                content.add(item);
            }
            refreshed.add(changed ? new ModelMessage(message.role(), content, message.inputObservation()) : message);
        }
        return dev.openallay.util.Java8Collections.listCopyOf(refreshed);
    }

    /** The session index is reconciled at complete model-message boundaries only. */
    public void reconcile(List<ModelMessage> messages, RetainedSkillContext retained) {
        Scan scan = scan(messages, retained);
        List<RetainedSkillContext.Range> actual = new ArrayList<>(retained.systemRanges());
        actual.addAll(scan.ranges());
        retained.reconcile(actual);
        retained.retainValidations(scan.loads().keySet());
    }

    /** Only a real assembled system section can own system-delivered Skill text. */
    public void prepareSystem(String systemPrompt, RetainedSkillContext retained) {
        List<RetainedSkillContext.Range> system = new ArrayList<>();
        String marker = "## UNRESTRICTED JAVASCRIPT GUIDANCE\n";
        int start = systemPrompt.indexOf(marker);
        if (start >= 0) {
            start += marker.length();
            dev.openallay.skill.SkillCatalogManifest.Document document = documents.get(id(SkillCatalogSnapshot.UNRESTRICTED_JAVASCRIPT, "SKILL.md"));
            if (document != null && start + document.length() <= systemPrompt.length()) {
                int end = start + document.length();
                String text = systemPrompt.substring(start, end);
                boolean sectionEnd = end == systemPrompt.length() || systemPrompt.startsWith("\n\n## ", end)
                        || (end + 1 == systemPrompt.length() && systemPrompt.charAt(end) == '\n');
                if (sectionEnd && document.fingerprint().equals(LoadSkillTool.fingerprint(text))) {
                    system.add(new RetainedSkillContext.Range(document.key(), 0, text.length(), text.length()));
                }
            }
        }
        retained.systemRanges(system);
    }

    public String manifest(RetainedSkillContext retained) {
        StringBuilder facts = new StringBuilder();
        java.util.Map<dev.openallay.skill.RetainedSkillContext.Key, java.util.List<dev.openallay.skill.RetainedSkillContext.Range>> byDocument = retained.ranges().stream().collect(java.util.stream.Collectors.groupingBy(
                RetainedSkillContext.Range::key));
        for (dev.openallay.skill.RetainedSkillContext.Key key : dev.openallay.util.Java8Collections.toList(byDocument.keySet().stream()
                .sorted(java.util.Comparator.comparing(RetainedSkillContext.Key::skill)
                        .thenComparing(RetainedSkillContext.Key::document)))) {
            dev.openallay.skill.SkillCatalogManifest.Document document = documents.get(id(key.skill(), key.document()));
            if (document == null || !document.key().equals(key)) continue;
            List<RetainedSkillContext.Range> ranges = dev.openallay.util.Java8Collections.toList(byDocument.get(key).stream()
                    .sorted(java.util.Comparator.comparingInt(RetainedSkillContext.Range::offset)));
            facts.append(document.name()).append(" / ").append(document.document()).append(": ");
            if (retained.contains(document.key(), 0, document.length())) {
                facts.append("full");
            } else {
                facts.append("partial [");
                facts.append(ranges.stream().map(range -> range.offset() + ".." + range.end())
                        .distinct().collect(java.util.stream.Collectors.joining(", "))).append(']');
                dev.openallay.skill.SkillCatalogManifest.Chunk missing = document.chunks().stream()
                        .filter(chunk -> !retained.contains(document.key(), chunk.offset(), chunk.end()))
                        .findFirst().orElse(null);
                if (missing != null) {
                    facts.append("; missing_offset=").append(missing.offset());
                    if (missing.offset() != 0) facts.append("; cursor=").append(LoadSkillTool.encodeCursor(
                            document.name(), document.document(), document.source(), document.fingerprint(), missing.offset()));
                }
            }
            if (document.document().equals("SKILL.md") && !document.availableReferences().isEmpty()) {
                facts.append("; available_refs=[")
                        .append(String.join(", ", document.availableReferences())).append(']');
            }
            facts.append('\n');
        }
        return facts.length() == 0 ? "" : "## Loaded Skill documents in current model context\n" + facts;
    }

    public LoadSkillTool.Output reuse(LoadSkillTool.Input input, RetainedSkillContext retained) {
        try {
            dev.openallay.skill.SkillCatalogManifest.Document document = requested(input);
            if (document == null) return null;
            int offset = LoadSkillTool.decodeCursor(input.cursor(), document.name(),
                    document.document(), document.source(), document.fingerprint());
            dev.openallay.skill.SkillCatalogManifest.Chunk chunk = document.chunkAt(offset);
            if (chunk == null || !retained.contains(document.key(), offset, chunk.end())) return null;
            return receipt(document, offset);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /** Remote result validation uses the client's frozen catalog, never a server document body. */
    public boolean validate(LoadSkillTool.Input input, LoadSkillTool.Output output) {
        try {
            dev.openallay.skill.SkillCatalogManifest.Document document = requested(input);
            if (document == null || !document.key().equals(new RetainedSkillContext.Key(
                    output.name(), output.document(), output.source(), output.fingerprint()))) return false;
            int offset = LoadSkillTool.decodeCursor(input.cursor(), document.name(),
                    document.document(), document.source(), document.fingerprint());
            dev.openallay.skill.SkillCatalogManifest.Chunk chunk = document.chunkAt(offset);
            if (chunk == null || output.offset() != offset || output.nextOffset() != chunk.end()
                    || output.complete() != (chunk.end() == document.length())
                    || !output.nextCursor().equals(cursor(document, chunk.end()))
                    || !output.availableReferences().equals(document.availableReferences())
                    || (output.state() == LoadSkillTool.LoadState.COMPLETE && !output.complete())
                    || (output.state() == LoadSkillTool.LoadState.CONTENT && output.complete())) return false;
            if (output.state() == LoadSkillTool.LoadState.ALREADY_LOADED) return output.content().isEmpty();
            return output.content().length() == chunk.end() - offset
                    && LoadSkillTool.fingerprint(output.content()).equals(chunk.fingerprint());
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    List<LoadSkillTool.Output> deliveredRanges(List<ModelMessage> messages) {
        Scan scan = scan(messages, new RetainedSkillContext());
        List<LoadSkillTool.Output> delivered = new ArrayList<>();
        for (java.util.Map.Entry<dev.openallay.model.ModelContent.ToolResult, dev.openallay.skill.SkillInstructionContext.Parsed> entry : scan.loads().entrySet()) {
            Parsed parsed = entry.getValue();
            if (parsed == null || parsed.state() == LoadSkillTool.LoadState.ALREADY_LOADED) continue;
            String text = entry.getKey().value().getAsString();
            Matcher header = HEADER.matcher(text);
            header.find();
            String body = text.substring(header.end());
            if (body.startsWith("references: ")) body = body.substring(body.indexOf('\n') + 1);
            String content = body.substring("content:\n".length(),
                    "content:\n".length() + parsed.range().end() - parsed.range().offset());
            delivered.add(new LoadSkillTool.Output(parsed.document().name(), parsed.document().document(),
                    parsed.document().source(), parsed.document().fingerprint(), parsed.state(), content,
                    parsed.range().offset(), parsed.range().end(), parsed.range().end() == parsed.document().length(),
                    cursor(parsed.document(), parsed.range().end()), parsed.document().availableReferences(), dev.openallay.util.Java8Collections.listOf(), ""));
        }
        return dev.openallay.util.Java8Collections.listCopyOf(delivered);
    }

    private Scan scan(List<ModelMessage> messages, RetainedSkillContext retained) {
        Map<String, ModelContent.ToolUse> uses = new HashMap<>();
        Map<ModelContent.ToolResult, Parsed> loads = new IdentityHashMap<>();
        List<RetainedSkillContext.Range> ranges = new ArrayList<>();
        for (ModelMessage message : messages) {
            for (ModelContent item : message.content()) {
                if (item instanceof ModelContent.ToolUse && message.role() == ModelRole.ASSISTANT) {
                    ModelContent.ToolUse use = (ModelContent.ToolUse) item;
                    uses.put(use.id(), use);
                } else if (item instanceof ModelContent.ToolResult && message.role() == ModelRole.USER
                        && !((ModelContent.ToolResult) item).error()) {
                    ModelContent.ToolResult result = (ModelContent.ToolResult) item;
                    dev.openallay.model.ModelContent.ToolUse use = uses.get(result.toolUseId());
                    if (use == null || !isLoadSkill(use.name())) continue;
                    Parsed parsed = parse(use, result, retained);
                    loads.put(result, parsed);
                    if (parsed != null && parsed.state() != LoadSkillTool.LoadState.ALREADY_LOADED) {
                        ranges.add(parsed.range());
                    }
                }
            }
        }
        return new Scan(loads, dev.openallay.util.Java8Collections.listCopyOf(ranges));
    }

    private Parsed parse(ModelContent.ToolUse use, ModelContent.ToolResult result, RetainedSkillContext retained) {
        try {
            dev.openallay.skill.LoadSkillTool.Input input = input(use.input());
            dev.openallay.skill.SkillCatalogManifest.Document document = requested(input);
            if (document == null) return null;
            dev.openallay.skill.RetainedSkillContext.Range cached = retained.validated(use, result, document);
            if (cached != null) return new Parsed(document, cached,
                    cached.end() == document.length() ? LoadSkillTool.LoadState.COMPLETE : LoadSkillTool.LoadState.CONTENT);
            JsonElement value = result.value();
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return null;
            String text = value.getAsString();
            Matcher header = HEADER.matcher(text);
            if (!header.find()) return null;
            dev.openallay.skill.LoadSkillTool.LoadState state = LoadSkillTool.LoadState.valueOf(header.group(5).toUpperCase(Locale.ROOT));
            int offset = Integer.parseInt(header.group(7));
            int end = Integer.parseInt(header.group(8));
            int contentLength = Integer.parseInt(header.group(9));
            String body = text.substring(header.end());
            if (state == LoadSkillTool.LoadState.ALREADY_LOADED) {
                String expected = (end == document.length() ? "" : "next_cursor: " + cursor(document, end) + "\n")
                        + "note: this document range is already present in the current model context";
                if (contentLength != 0 || !body.equals(expected)) return null;
            } else {
                if (body.startsWith("references: ")) {
                    int newline = body.indexOf('\n');
                    if (newline < 0) return null;
                    // Historical metadata may change independently from this exact document.
                    // Current manifests use only the newly captured references, never this line.
                    String references = body.substring("references: ".length(), newline);
                    for (String reference : references.split(", ", -1)) {
                        if (!reference.startsWith("references/") || !reference.equals(SkillSource.normalize(reference))
                                || reference.chars().anyMatch(character -> character < 32 || character == 127)) return null;
                    }
                    body = body.substring(newline + 1);
                }
                if (!body.startsWith("content:\n") || contentLength != end - offset
                        || body.length() < "content:\n".length() + contentLength) return null;
            }
            String content = state == LoadSkillTool.LoadState.ALREADY_LOADED ? ""
                    : body.substring("content:\n".length(), "content:\n".length() + contentLength);
            if (state != LoadSkillTool.LoadState.ALREADY_LOADED) {
                String expected = "content:\n" + content + (end == document.length() ? ""
                        : "\nnext: call load_skill with the same name/reference and cursor " + cursor(document, end));
                if (!body.equals(expected)) return null;
            }
            dev.openallay.skill.LoadSkillTool.Output output = new LoadSkillTool.Output(header.group(1), header.group(2), header.group(3), header.group(4),
                    state, content, offset, end, Boolean.parseBoolean(header.group(6)), cursor(document, end),
                    document.availableReferences(), dev.openallay.util.Java8Collections.listOf(), "");
            if (!validate(input, output)) return null;
            dev.openallay.skill.RetainedSkillContext.Range range = new RetainedSkillContext.Range(document.key(), offset, end, document.length());
            if (state != LoadSkillTool.LoadState.ALREADY_LOADED) retained.remember(use, result, range, document);
            return new Parsed(document, range, state);
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private SkillCatalogManifest.Document requested(LoadSkillTool.Input input) {
        if (input == null || input.name() == null) return null;
        String reference = input.reference() == null || dev.openallay.util.Java8Strings.isBlank(input.reference()) ? "SKILL.md" : dev.openallay.util.Java8Strings.strip(input.reference());
        return documents.get(id(input.name(), reference));
    }

    private static LoadSkillTool.Input input(JsonObject input) {
        return new LoadSkillTool.Input(optionalString(input, "name"), optionalString(input, "reference"),
                optionalString(input, "cursor"));
    }

    private static String optionalString(JsonObject input, String field) {
        JsonElement value = input.get(field);
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Skill argument is not a string");
        }
        return value.getAsString();
    }

    private static String id(String name, String document) { return name + "\0" + document; }
    private static String cursor(SkillCatalogManifest.Document document, int end) {
        return end == document.length() ? "" : LoadSkillTool.encodeCursor(
                document.name(), document.document(), document.source(), document.fingerprint(), end);
    }
    private static LoadSkillTool.Output receipt(SkillCatalogManifest.Document document, int offset) {
        dev.openallay.skill.SkillCatalogManifest.Chunk chunk = document.chunkAt(offset);
        return new LoadSkillTool.Output(document.name(), document.document(), document.source(), document.fingerprint(),
                LoadSkillTool.LoadState.ALREADY_LOADED, "", offset, chunk.end(), chunk.end() == document.length(),
                cursor(document, chunk.end()), document.availableReferences(), dev.openallay.util.Java8Collections.listOf(), "");
    }
    private static boolean isLoadSkill(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.equals("load_skill") || normalized.endsWith(":load_skill") || normalized.endsWith("__load_skill");
    }
    @dev.openallay.value.ValueType(Parsed.ValueSchemaProvider.class)
private static final class Parsed {
    private final SkillCatalogManifest.Document document;
    private final RetainedSkillContext.Range range;
    private final LoadSkillTool.LoadState state;
    private Parsed(SkillCatalogManifest.Document document, RetainedSkillContext.Range range, LoadSkillTool.LoadState state) {
        this.document = document;
        this.range = range;
        this.state = state;
    }
    public SkillCatalogManifest.Document document() { return document; }
    public RetainedSkillContext.Range range() { return range; }
    public LoadSkillTool.LoadState state() { return state; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Parsed)) return false;
        Parsed that = (Parsed) other;
        return java.util.Objects.equals(document, that.document) && java.util.Objects.equals(range, that.range) && java.util.Objects.equals(state, that.state);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(document);
        hash = 31 * hash + java.util.Objects.hashCode(range);
        hash = 31 * hash + java.util.Objects.hashCode(state);
        return hash;
    }
    @Override public String toString() { return "Parsed[document=" + document + ", range=" + range + ", state=" + state + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Parsed> schema() {
            return new dev.openallay.value.ValueSchema<>(Parsed.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Parsed>>asList(new dev.openallay.value.ValueSchema.Component<>(Parsed.class, "document", Parsed::document), new dev.openallay.value.ValueSchema.Component<>(Parsed.class, "range", Parsed::range), new dev.openallay.value.ValueSchema.Component<>(Parsed.class, "state", Parsed::state)), arguments -> new Parsed((SkillCatalogManifest.Document) arguments[0], (RetainedSkillContext.Range) arguments[1], (LoadSkillTool.LoadState) arguments[2]));
        }
    }
}
    @dev.openallay.value.ValueType(Scan.ValueSchemaProvider.class)
private static final class Scan {
    private final Map<ModelContent.ToolResult, Parsed> loads;
    private final List<RetainedSkillContext.Range> ranges;
    private Scan(Map<ModelContent.ToolResult, Parsed> loads, List<RetainedSkillContext.Range> ranges) {
        this.loads = loads;
        this.ranges = ranges;
    }
    public Map<ModelContent.ToolResult, Parsed> loads() { return loads; }
    public List<RetainedSkillContext.Range> ranges() { return ranges; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Scan)) return false;
        Scan that = (Scan) other;
        return java.util.Objects.equals(loads, that.loads) && java.util.Objects.equals(ranges, that.ranges);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(loads);
        hash = 31 * hash + java.util.Objects.hashCode(ranges);
        return hash;
    }
    @Override public String toString() { return "Scan[loads=" + loads + ", ranges=" + ranges + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Scan> schema() {
            return new dev.openallay.value.ValueSchema<>(Scan.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Scan>>asList(new dev.openallay.value.ValueSchema.Component<>(Scan.class, "loads", Scan::loads), new dev.openallay.value.ValueSchema.Component<>(Scan.class, "ranges", Scan::ranges)), arguments -> new Scan((Map) arguments[0], (List) arguments[1]));
        }
    }
}
}
