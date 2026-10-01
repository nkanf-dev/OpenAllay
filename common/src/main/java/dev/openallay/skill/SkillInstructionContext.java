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
        for (var document : manifest.documents()) {
            captured.put(id(document.name(), document.document()), document);
        }
        documents = Map.copyOf(captured);
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
                Parsed parsed = item instanceof ModelContent.ToolResult result ? scan.loads().get(result) : null;
                if (item instanceof ModelContent.ToolResult result && scan.loads().containsKey(result)) {
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
                                new JsonPrimitive(replacement), result.error()));
                        changed = true;
                        continue;
                    }
                }
                content.add(item);
            }
            refreshed.add(changed ? new ModelMessage(message.role(), content) : message);
        }
        return List.copyOf(refreshed);
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
            var document = documents.get(id(SkillCatalogSnapshot.UNRESTRICTED_JAVASCRIPT, "SKILL.md"));
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
        var byDocument = retained.ranges().stream().collect(java.util.stream.Collectors.groupingBy(
                RetainedSkillContext.Range::key));
        for (var key : byDocument.keySet().stream()
                .sorted(java.util.Comparator.comparing(RetainedSkillContext.Key::skill)
                        .thenComparing(RetainedSkillContext.Key::document)).toList()) {
            var document = documents.get(id(key.skill(), key.document()));
            if (document == null || !document.key().equals(key)) continue;
            List<RetainedSkillContext.Range> ranges = byDocument.get(key).stream()
                    .sorted(java.util.Comparator.comparingInt(RetainedSkillContext.Range::offset)).toList();
            facts.append(document.name()).append(" / ").append(document.document()).append(": ");
            if (retained.contains(document.key(), 0, document.length())) {
                facts.append("full");
            } else {
                facts.append("partial [");
                facts.append(ranges.stream().map(range -> range.offset() + ".." + range.end())
                        .distinct().collect(java.util.stream.Collectors.joining(", "))).append(']');
                var missing = document.chunks().stream()
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
        return facts.isEmpty() ? "" : "## Loaded Skill documents in current model context\n" + facts;
    }

    public LoadSkillTool.Output reuse(LoadSkillTool.Input input, RetainedSkillContext retained) {
        try {
            var document = requested(input);
            if (document == null) return null;
            int offset = LoadSkillTool.decodeCursor(input.cursor(), document.name(),
                    document.document(), document.source(), document.fingerprint());
            var chunk = document.chunkAt(offset);
            if (chunk == null || !retained.contains(document.key(), offset, chunk.end())) return null;
            return receipt(document, offset);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /** Remote result validation uses the client's frozen catalog, never a server document body. */
    public boolean validate(LoadSkillTool.Input input, LoadSkillTool.Output output) {
        try {
            var document = requested(input);
            if (document == null || !document.key().equals(new RetainedSkillContext.Key(
                    output.name(), output.document(), output.source(), output.fingerprint()))) return false;
            int offset = LoadSkillTool.decodeCursor(input.cursor(), document.name(),
                    document.document(), document.source(), document.fingerprint());
            var chunk = document.chunkAt(offset);
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
        for (var entry : scan.loads().entrySet()) {
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
                    cursor(parsed.document(), parsed.range().end()), parsed.document().availableReferences(), List.of(), ""));
        }
        return List.copyOf(delivered);
    }

    private Scan scan(List<ModelMessage> messages, RetainedSkillContext retained) {
        Map<String, ModelContent.ToolUse> uses = new HashMap<>();
        Map<ModelContent.ToolResult, Parsed> loads = new IdentityHashMap<>();
        List<RetainedSkillContext.Range> ranges = new ArrayList<>();
        for (ModelMessage message : messages) {
            for (ModelContent item : message.content()) {
                if (item instanceof ModelContent.ToolUse use && message.role() == ModelRole.ASSISTANT) {
                    uses.put(use.id(), use);
                } else if (item instanceof ModelContent.ToolResult result && message.role() == ModelRole.USER
                        && !result.error()) {
                    var use = uses.get(result.toolUseId());
                    if (use == null || !isLoadSkill(use.name())) continue;
                    Parsed parsed = parse(use, result, retained);
                    loads.put(result, parsed);
                    if (parsed != null && parsed.state() != LoadSkillTool.LoadState.ALREADY_LOADED) {
                        ranges.add(parsed.range());
                    }
                }
            }
        }
        return new Scan(loads, List.copyOf(ranges));
    }

    private Parsed parse(ModelContent.ToolUse use, ModelContent.ToolResult result, RetainedSkillContext retained) {
        try {
            var input = input(use.input());
            var document = requested(input);
            if (document == null) return null;
            var cached = retained.validated(use, result, document);
            if (cached != null) return new Parsed(document, cached,
                    cached.end() == document.length() ? LoadSkillTool.LoadState.COMPLETE : LoadSkillTool.LoadState.CONTENT);
            JsonElement value = result.value();
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return null;
            String text = value.getAsString();
            Matcher header = HEADER.matcher(text);
            if (!header.find()) return null;
            var state = LoadSkillTool.LoadState.valueOf(header.group(5).toUpperCase(Locale.ROOT));
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
            var output = new LoadSkillTool.Output(header.group(1), header.group(2), header.group(3), header.group(4),
                    state, content, offset, end, Boolean.parseBoolean(header.group(6)), cursor(document, end),
                    document.availableReferences(), List.of(), "");
            if (!validate(input, output)) return null;
            var range = new RetainedSkillContext.Range(document.key(), offset, end, document.length());
            if (state != LoadSkillTool.LoadState.ALREADY_LOADED) retained.remember(use, result, range, document);
            return new Parsed(document, range, state);
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private SkillCatalogManifest.Document requested(LoadSkillTool.Input input) {
        if (input == null || input.name() == null) return null;
        String reference = input.reference() == null || input.reference().isBlank() ? "SKILL.md" : input.reference().strip();
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
        var chunk = document.chunkAt(offset);
        return new LoadSkillTool.Output(document.name(), document.document(), document.source(), document.fingerprint(),
                LoadSkillTool.LoadState.ALREADY_LOADED, "", offset, chunk.end(), chunk.end() == document.length(),
                cursor(document, chunk.end()), document.availableReferences(), List.of(), "");
    }
    private static boolean isLoadSkill(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.equals("load_skill") || normalized.endsWith(":load_skill") || normalized.endsWith("__load_skill");
    }
    private record Parsed(SkillCatalogManifest.Document document, RetainedSkillContext.Range range, LoadSkillTool.LoadState state) {}
    private record Scan(Map<ModelContent.ToolResult, Parsed> loads, List<RetainedSkillContext.Range> ranges) {}
}
