package dev.openallay.skill;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates real, retained load_skill plaintext against one captured document snapshot. */
public final class SkillInstructionContext {
    private static final Pattern HEADER = Pattern.compile(
            "\\Askill_instructions\\n"
                    + "skill: ([^\\n]+)\\n"
                    + "document: ([^\\n]+)\\n"
                    + "fingerprint: ([a-f0-9]{64})\\n"
                    + "state: (content|complete|already_loaded|rehydrated)\\n"
                    + "complete: (true|false)\\n"
                    + "range: ([0-9]+)\\.\\.([0-9]+)\\n"
                    + "content_length: ([0-9]+)\\n");
    private static final String INVALIDATED = "skill_instructions: invalidated\n"
            + "note: retained Skill instructions or receipt are no longer valid for the current "
            + "catalog/context. Call load_skill for current instructions.";
    private final Map<DocumentKey, Document> documents;

    public SkillInstructionContext(SkillCatalog catalog) {
        java.util.Objects.requireNonNull(catalog, "catalog");
        Map<DocumentKey, Document> captured = new HashMap<>();
        for (SkillMetadata metadata : catalog.metadata()) {
            SkillDocument skill = catalog.find(metadata.name()).orElse(null);
            if (skill == null || !metadata.name().equals(skill.metadata().name())) {
                continue;
            }
            capture(captured, skill, "SKILL.md", skill.instructions());
            skill.references().forEach((name, text) -> capture(captured, skill, name, text));
        }
        documents = Map.copyOf(captured);
    }

    /** Leaves valid exact documents untouched; stale text becomes an explicit invalidation note. */
    public List<ModelMessage> refresh(List<ModelMessage> messages) {
        Scan scan = scan(messages);
        List<ModelMessage> refreshed = new ArrayList<>(messages.size());
        for (ModelMessage message : messages) {
            List<ModelContent> content = new ArrayList<>(message.content().size());
            boolean changed = false;
            for (ModelContent item : message.content()) {
                if (item instanceof ModelContent.ToolResult result
                        && scan.loadResults().contains(result)) {
                    LoadSkillTool.Output output = scan.valid().get(result);
                    if (output == null
                            || (output.state() == LoadSkillTool.LoadState.ALREADY_LOADED
                                    && !scan.presentRanges().contains(Range.of(output)))) {
                        content.add(new ModelContent.ToolResult(
                                result.toolUseId(), new JsonPrimitive(INVALIDATED), true));
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

    /** Only actual instruction-bearing results establish a delivered range. */
    List<LoadSkillTool.Output> deliveredRanges(List<ModelMessage> messages) {
        return scan(messages).valid().values().stream()
                .filter(output -> output.state() != LoadSkillTool.LoadState.ALREADY_LOADED)
                .toList();
    }

    private Scan scan(List<ModelMessage> messages) {
        Map<String, ModelContent.ToolUse> uses = new HashMap<>();
        Set<ModelContent.ToolResult> loadResults = java.util.Collections.newSetFromMap(
                new IdentityHashMap<>());
        Map<ModelContent.ToolResult, LoadSkillTool.Output> valid = new IdentityHashMap<>();
        Set<Range> presentRanges = new HashSet<>();
        for (ModelMessage message : List.copyOf(messages)) {
            for (ModelContent item : message.content()) {
                if (item instanceof ModelContent.ToolUse use
                        && message.role() == ModelRole.ASSISTANT) {
                    uses.put(use.id(), use);
                } else if (item instanceof ModelContent.ToolResult result
                        && message.role() == ModelRole.USER
                        && !result.error()) {
                    ModelContent.ToolUse use = uses.get(result.toolUseId());
                    if (use == null || !isLoadSkill(use.name())) {
                        continue;
                    }
                    loadResults.add(result);
                    LoadSkillTool.Output output = validate(use, result.value());
                    if (output != null) {
                        valid.put(result, output);
                        if (output.state() != LoadSkillTool.LoadState.ALREADY_LOADED) {
                            presentRanges.add(Range.of(output));
                        }
                    }
                }
            }
        }
        return new Scan(loadResults, valid, presentRanges);
    }

    private LoadSkillTool.Output validate(ModelContent.ToolUse use, JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return null;
        }
        String text = value.getAsString();
        Matcher header = HEADER.matcher(text);
        if (!header.find()) {
            return null;
        }
        String name = header.group(1);
        String documentName = header.group(2);
        String fingerprint = header.group(3);
        Document document = documents.get(new DocumentKey(name, documentName));
        if (document == null || !document.fingerprint().equals(fingerprint)) {
            return null;
        }
        try {
            LoadSkillTool.LoadState state = LoadSkillTool.LoadState.valueOf(
                    header.group(4).toUpperCase(Locale.ROOT));
            boolean complete = Boolean.parseBoolean(header.group(5));
            int offset = Integer.parseInt(header.group(6));
            int nextOffset = Integer.parseInt(header.group(7));
            int contentLength = Integer.parseInt(header.group(8));
            String contents = document.contents();
            if (offset > contents.length()
                    || nextOffset != LoadSkillTool.chunkEnd(contents, offset)
                    || complete != (nextOffset == contents.length())
                    || (state == LoadSkillTool.LoadState.CONTENT && complete)
                    || (state == LoadSkillTool.LoadState.COMPLETE && !complete)) {
                return null;
            }
            JsonObject input = use.input();
            String reference = optionalString(input, "reference");
            String requestedDocument = reference == null || reference.isBlank()
                    ? "SKILL.md" : reference.strip();
            String cursor = optionalString(input, "cursor");
            if (!name.equals(optionalString(input, "name"))
                    || !documentName.equals(requestedDocument)
                    || LoadSkillTool.decodeCursor(cursor, name, documentName, fingerprint) != offset) {
                return null;
            }
            JsonElement rehydrate = input.get("rehydrate");
            if (rehydrate != null && !rehydrate.isJsonNull()
                    && (!rehydrate.isJsonPrimitive()
                            || !rehydrate.getAsJsonPrimitive().isBoolean())) {
                return null;
            }
            boolean requestedRehydration = rehydrate != null && !rehydrate.isJsonNull()
                    && rehydrate.getAsBoolean();
            if (requestedRehydration != (state == LoadSkillTool.LoadState.REHYDRATED)) {
                return null;
            }
            String nextCursor = complete ? "" : LoadSkillTool.encodeCursor(
                    name, documentName, fingerprint, nextOffset);
            String body = text.substring(header.end());
            String content;
            if (state == LoadSkillTool.LoadState.ALREADY_LOADED) {
                String expected = (complete ? "" : "next_cursor: " + nextCursor + "\n")
                        + "note: this document range is already present in the current model context";
                if (contentLength != 0 || !body.equals(expected)) {
                    return null;
                }
                content = "";
            } else {
                if (contentLength != nextOffset - offset) {
                    return null;
                }
                // Reference metadata may change independently of this exact document's content.
                if (body.startsWith("references: ")) {
                    int newline = body.indexOf('\n');
                    if (newline < 0) {
                        return null;
                    }
                    body = body.substring(newline + 1);
                }
                content = contents.substring(offset, nextOffset);
                String expected = "content:\n" + content
                        + (complete ? "" : "\nnext: call load_skill with the same name/reference and cursor "
                                + nextCursor);
                if (!body.equals(expected)) {
                    return null;
                }
            }
            SkillDocument skill = document.skill();
            return new LoadSkillTool.Output(
                    name, documentName, fingerprint, state, content, offset, nextOffset,
                    complete, nextCursor, skill.references().keySet().stream().sorted().toList(),
                    skill.metadata().allowedTools().stream().sorted().toList(),
                    skill.metadata().provenance());
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static String optionalString(JsonObject input, String field) {
        JsonElement value = input.get(field);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Skill argument is not a string");
        }
        return value.getAsString();
    }

    private static boolean isLoadSkill(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.equals("load_skill") || normalized.endsWith(":load_skill")
                || normalized.endsWith("__load_skill");
    }

    private static void capture(
            Map<DocumentKey, Document> documents, SkillDocument skill, String name, String contents) {
        documents.put(new DocumentKey(skill.metadata().name(), name),
                new Document(skill, contents, LoadSkillTool.fingerprint(contents)));
    }

    private record DocumentKey(String name, String document) {}

    private record Document(SkillDocument skill, String contents, String fingerprint) {}

    private record Range(String name, String document, String fingerprint, int offset, int nextOffset) {
        static Range of(LoadSkillTool.Output output) {
            return new Range(output.name(), output.document(), output.fingerprint(),
                    output.offset(), output.nextOffset());
        }
    }

    private record Scan(
            Set<ModelContent.ToolResult> loadResults,
            Map<ModelContent.ToolResult, LoadSkillTool.Output> valid,
            Set<Range> presentRanges) {}
}
