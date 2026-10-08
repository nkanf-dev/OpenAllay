package dev.openallay.skill;

import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.ModelMessage;
import dev.openallay.agent.tool.ToolOptional;
import dev.openallay.tool.InstructionModelFacingToolOutput;
import dev.openallay.tool.RequestScopeParticipant;
import dev.openallay.tool.Tool;
import dev.openallay.tool.ToolAccess;
import dev.openallay.tool.ToolDescriptor;
import dev.openallay.tool.ToolResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LoadSkillTool
        implements Tool<LoadSkillTool.Input, LoadSkillTool.Output>, RequestScopeParticipant {
    private static final int CHUNK_CHARACTERS = 8_192;

    @dev.openallay.value.ValueType(Input.ValueSchemaProvider.class)
public static final class Input {
    private final String name;
    @ToolOptional private final String reference;
    @ToolOptional private final String cursor;
    public Input(String name, String reference, String cursor) {
        this.name = name;
        this.reference = reference;
        this.cursor = cursor;
    }
    public String name() { return name; }
    public String reference() { return reference; }
    public String cursor() { return cursor; }
public Input(String name) {
            this(name, null, null);
        }
public Input(String name, String reference) {
            this(name, reference, null);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Input)) return false;
        Input that = (Input) other;
        return java.util.Objects.equals(name, that.name) && java.util.Objects.equals(reference, that.reference) && java.util.Objects.equals(cursor, that.cursor);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(reference);
        hash = 31 * hash + java.util.Objects.hashCode(cursor);
        return hash;
    }
    @Override public String toString() { return "Input[name=" + name + ", reference=" + reference + ", cursor=" + cursor + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Input> schema() {
            return new dev.openallay.value.ValueSchema<>(Input.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Input>>asList(new dev.openallay.value.ValueSchema.Component<>(Input.class, "name", Input::name), new dev.openallay.value.ValueSchema.Component<>(Input.class, "reference", Input::reference), new dev.openallay.value.ValueSchema.Component<>(Input.class, "cursor", Input::cursor)), arguments -> new Input((String) arguments[0], (String) arguments[1], (String) arguments[2]));
        }
    }
}

    public enum LoadState {
        CONTENT,
        COMPLETE,
        ALREADY_LOADED
    }

    @dev.openallay.value.ValueType(Output.ValueSchemaProvider.class)
public static final class Output implements InstructionModelFacingToolOutput {
    private final String name;
    private final String document;
    private final String source;
    private final String fingerprint;
    private final LoadState state;
    private final String content;
    private final int offset;
    private final int nextOffset;
    private final boolean complete;
    private final String nextCursor;
    private final List<String> availableReferences;
    private final List<String> allowedTools;
    private final String provenance;
    public Output(String name, String document, String source, String fingerprint, LoadState state, String content, int offset, int nextOffset, boolean complete, String nextCursor, List<String> availableReferences, List<String> allowedTools, String provenance) {

            java.util.Objects.requireNonNull(state, "state");
            availableReferences = dev.openallay.util.Java8Collections.listCopyOf(availableReferences);
            allowedTools = dev.openallay.util.Java8Collections.listCopyOf(allowedTools);

        this.name = name;
        this.document = document;
        this.source = source;
        this.fingerprint = fingerprint;
        this.state = state;
        this.content = content;
        this.offset = offset;
        this.nextOffset = nextOffset;
        this.complete = complete;
        this.nextCursor = nextCursor;
        this.availableReferences = availableReferences;
        this.allowedTools = allowedTools;
        this.provenance = provenance;
    }
    public String name() { return name; }
    public String document() { return document; }
    public String source() { return source; }
    public String fingerprint() { return fingerprint; }
    public LoadState state() { return state; }
    public String content() { return content; }
    public int offset() { return offset; }
    public int nextOffset() { return nextOffset; }
    public boolean complete() { return complete; }
    public String nextCursor() { return nextCursor; }
    public List<String> availableReferences() { return availableReferences; }
    public List<String> allowedTools() { return allowedTools; }
    public String provenance() { return provenance; }
@Override
        public String modelText() {
            StringBuilder text = new StringBuilder()
                    .append("skill_instructions\n")
                    .append("skill: ").append(name).append('\n')
                    .append("document: ").append(document).append('\n')
                    .append("source: ").append(source).append('\n')
                    .append("fingerprint: ").append(fingerprint).append('\n')
                    .append("state: ")
                    .append(state.name().toLowerCase(java.util.Locale.ROOT))
                    .append('\n')
                    .append("complete: ").append(complete).append('\n')
                    .append("range: ").append(offset).append("..").append(nextOffset).append('\n')
                    .append("content_length: ").append(content.length()).append('\n');
            if (state == LoadState.ALREADY_LOADED) {
                if (!complete) {
                    text.append("next_cursor: ").append(nextCursor).append('\n');
                }
                return text.append(
                                "note: this document range is already present in the current model context")
                        .toString();
            }
            if (!availableReferences.isEmpty()) {
                text.append("references: ")
                        .append(String.join(", ", availableReferences))
                        .append('\n');
            }
            text.append("content:\n").append(content);
            if (!complete) {
                text.append("\nnext: call load_skill with the same name/reference and cursor ")
                        .append(nextCursor);
            }
            return text.toString();
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Output)) return false;
        Output that = (Output) other;
        return java.util.Objects.equals(name, that.name) && java.util.Objects.equals(document, that.document) && java.util.Objects.equals(source, that.source) && java.util.Objects.equals(fingerprint, that.fingerprint) && java.util.Objects.equals(state, that.state) && java.util.Objects.equals(content, that.content) && offset == that.offset && nextOffset == that.nextOffset && complete == that.complete && java.util.Objects.equals(nextCursor, that.nextCursor) && java.util.Objects.equals(availableReferences, that.availableReferences) && java.util.Objects.equals(allowedTools, that.allowedTools) && java.util.Objects.equals(provenance, that.provenance);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(document);
        hash = 31 * hash + java.util.Objects.hashCode(source);
        hash = 31 * hash + java.util.Objects.hashCode(fingerprint);
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + java.util.Objects.hashCode(content);
        hash = 31 * hash + Integer.hashCode(offset);
        hash = 31 * hash + Integer.hashCode(nextOffset);
        hash = 31 * hash + Boolean.hashCode(complete);
        hash = 31 * hash + java.util.Objects.hashCode(nextCursor);
        hash = 31 * hash + java.util.Objects.hashCode(availableReferences);
        hash = 31 * hash + java.util.Objects.hashCode(allowedTools);
        hash = 31 * hash + java.util.Objects.hashCode(provenance);
        return hash;
    }
    @Override public String toString() { return "Output[name=" + name + ", document=" + document + ", source=" + source + ", fingerprint=" + fingerprint + ", state=" + state + ", content=" + content + ", offset=" + offset + ", nextOffset=" + nextOffset + ", complete=" + complete + ", nextCursor=" + nextCursor + ", availableReferences=" + availableReferences + ", allowedTools=" + allowedTools + ", provenance=" + provenance + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Output> schema() {
            return new dev.openallay.value.ValueSchema<>(Output.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Output>>asList(new dev.openallay.value.ValueSchema.Component<>(Output.class, "name", Output::name), new dev.openallay.value.ValueSchema.Component<>(Output.class, "document", Output::document), new dev.openallay.value.ValueSchema.Component<>(Output.class, "source", Output::source), new dev.openallay.value.ValueSchema.Component<>(Output.class, "fingerprint", Output::fingerprint), new dev.openallay.value.ValueSchema.Component<>(Output.class, "state", Output::state), new dev.openallay.value.ValueSchema.Component<>(Output.class, "content", Output::content), new dev.openallay.value.ValueSchema.Component<>(Output.class, "offset", Output::offset), new dev.openallay.value.ValueSchema.Component<>(Output.class, "nextOffset", Output::nextOffset), new dev.openallay.value.ValueSchema.Component<>(Output.class, "complete", Output::complete), new dev.openallay.value.ValueSchema.Component<>(Output.class, "nextCursor", Output::nextCursor), new dev.openallay.value.ValueSchema.Component<>(Output.class, "availableReferences", Output::availableReferences), new dev.openallay.value.ValueSchema.Component<>(Output.class, "allowedTools", Output::allowedTools), new dev.openallay.value.ValueSchema.Component<>(Output.class, "provenance", Output::provenance)), arguments -> new Output((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (LoadState) arguments[4], (String) arguments[5], (Integer) arguments[6], (Integer) arguments[7], (Boolean) arguments[8], (String) arguments[9], (List) arguments[10], (List) arguments[11], (String) arguments[12]));
        }
    }
}

    private static final ToolDescriptor<Input, Output> DESCRIPTOR = new ToolDescriptor<>(
            "openallay:load_skill",
            "Progressively load missing instructions for a matching Skill, or one exact declared reference. "
                    + "Reuse Skill text already in the current model context, including system-delivered instructions. "
                    + "Continue a needed incomplete document with its returned opaque cursor and the same name/reference. "
                    + "Core JavaScript host syntax is in the system contract. Already-present document ranges return compact reuse receipts.",
            Input.class,
            Output.class,
            ToolAccess.READ_ONLY);

    private final SkillCatalog catalog;
    private final String owner;
    private final SkillCatalogManifest manifest;
    private final SkillInstructionContext instructionContext;
    private final Map<String, Binding> requests = new ConcurrentHashMap<>();

    @dev.openallay.value.ValueType(Binding.ValueSchemaProvider.class)
private static final class Binding {
    private final RetainedSkillContext retained;
    private final Map<RetainedSkillContext.Key,
            Map<Integer, RetainedSkillContext.Range>> pending;
    private Binding(RetainedSkillContext retained, Map<RetainedSkillContext.Key,
            Map<Integer, RetainedSkillContext.Range>> pending) {
        this.retained = retained;
        this.pending = pending;
    }
    public RetainedSkillContext retained() { return retained; }
    public Map<RetainedSkillContext.Key,
            Map<Integer, RetainedSkillContext.Range>> pending() { return pending; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Binding)) return false;
        Binding that = (Binding) other;
        return java.util.Objects.equals(retained, that.retained) && java.util.Objects.equals(pending, that.pending);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(retained);
        hash = 31 * hash + java.util.Objects.hashCode(pending);
        return hash;
    }
    @Override public String toString() { return "Binding[retained=" + retained + ", pending=" + pending + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Binding> schema() {
            return new dev.openallay.value.ValueSchema<>(Binding.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Binding>>asList(new dev.openallay.value.ValueSchema.Component<>(Binding.class, "retained", Binding::retained), new dev.openallay.value.ValueSchema.Component<>(Binding.class, "pending", Binding::pending)), arguments -> new Binding((RetainedSkillContext) arguments[0], (Map) arguments[1]));
        }
    }
}

    public LoadSkillTool(SkillCatalog catalog) { this(catalog, "local"); }

    public LoadSkillTool(SkillCatalog catalog, String owner) {
        this.catalog = java.util.Objects.requireNonNull(catalog, "catalog");
        this.owner = java.util.Objects.requireNonNull(owner, "owner");
        this.manifest = SkillCatalogManifest.capture(catalog, owner);
        this.instructionContext = new SkillInstructionContext(manifest);
    }

    public LoadSkillTool withOwner(String replacement) { return new LoadSkillTool(catalog, replacement); }

    /** Captures optional guidance for the request, preserving eligible documents and explicit denies. */
    public LoadSkillTool forRequest(boolean unrestrictedJavascript, boolean commandsAvailable, String owner) {
        SkillCatalogSnapshot captured;
        if (catalog instanceof SkillCatalogSnapshot) {
            captured = (SkillCatalogSnapshot) catalog;
        } else {
            Map<String, SkillDocument> documents = new java.util.TreeMap<>();
            for (SkillMetadata metadata : catalog.metadata()) {
                catalog.find(metadata.name()).ifPresent(document -> documents.put(metadata.name(), document));
            }
            captured = new SkillCatalogSnapshot(documents);
        }
        return new LoadSkillTool(captured.forRequest(unrestrictedJavascript, commandsAvailable), owner);
    }

    public SkillCatalogManifest catalogManifest() { return manifest; }

    /** Validates retained instruction results against this Tool's captured Skill catalog. */
    public List<ModelMessage> refreshContext(List<ModelMessage> messages) {
        return instructionContext.refresh(messages);
    }

    /** Standalone executors still reconcile from actual messages, not a lifetime loaded flag. */
    public void prepareContext(String correlationId, List<ModelMessage> messages) {
        Binding binding = requests.computeIfAbsent(correlationId,
                ignored -> new Binding(new RetainedSkillContext(), new ConcurrentHashMap<>()));
        prepareContext(correlationId, messages, binding.retained());
    }

    public void prepareContext(
            String correlationId, List<ModelMessage> messages, RetainedSkillContext retained) {
        java.util.Objects.requireNonNull(correlationId, "correlationId");
        instructionContext.reconcile(messages, retained);
        requests.put(correlationId, new Binding(retained, new ConcurrentHashMap<>()));
    }

    public List<ModelMessage> refreshContext(
            List<ModelMessage> messages, RetainedSkillContext retained) {
        return instructionContext.refresh(messages, retained);
    }

    public String systemPrompt(String prompt) {
        String marker = "## UNRESTRICTED JAVASCRIPT GUIDANCE\n";
        int start = prompt.indexOf(marker);
        SkillDocument skill = catalog.find(SkillCatalogSnapshot.UNRESTRICTED_JAVASCRIPT).orElse(null);
        if (start < 0 || skill == null) return prompt;
        start += marker.length();
        // This following heading is owned by AgentSystemPrompt, not by the Skill markdown body.
        int end = prompt.lastIndexOf("\n\n## EXECUTION\n");
        if (end < start) return prompt;
        return prompt.substring(0, start) + skill.instructions() + prompt.substring(end);
    }

    public void prepareSystem(String systemPrompt, RetainedSkillContext retained) {
        instructionContext.prepareSystem(systemPrompt, retained);
    }

    public String manifest(String correlationId) {
        Binding binding = requests.get(correlationId);
        return binding == null ? "" : instructionContext.manifest(binding.retained());
    }

    @Override
    public ToolDescriptor<Input, Output> descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolResult<Output> invoke(ToolInvocationContext context, Input input) {
        return invoke(context, input, false);
    }

    /** Remote endpoints do not own the Agent projection. Reuse belongs to that Agent only. */
    public ToolResult<Output> invokeFresh(ToolInvocationContext context, Input input) {
        return invoke(context, input, true);
    }

    private ToolResult<Output> invoke(ToolInvocationContext context, Input input, boolean fresh) {
        if (input == null || input.name() == null || dev.openallay.util.Java8Strings.isBlank(input.name())) {
            return new ToolResult.Failure<>("invalid_skill_name", "Skill name must not be blank");
        }
        if (SkillCatalogSnapshot.UNRESTRICTED_JAVASCRIPT.equals(input.name())
                && !context.unrestrictedJavascript()) {
            return new ToolResult.Failure<>("skill_not_found", "No available Skill named " + input.name());
        }
        SkillDocument document = catalog.find(input.name()).orElse(null);
        if (document == null) {
            return new ToolResult.Failure<>(
                    "skill_not_found", "No available Skill named " + input.name());
        }
        String reference = input.reference() == null ? "" : dev.openallay.util.Java8Strings.strip(input.reference());
        String documentName = reference.isEmpty() ? "SKILL.md" : reference;
        SkillDocument.Text captured = document.documents().get(documentName);
        String contents = captured == null ? null : captured.contents();
        if (!reference.isEmpty() && contents == null) {
            return new ToolResult.Failure<>(
                    "skill_reference_not_found",
                    "Skill " + input.name() + " has no declared reference " + reference);
        }
        String fingerprint = captured.fingerprint();
        int offset;
        try {
            offset = decodeCursor(
                    input.cursor(),
                    document.metadata().name(),
                    documentName,
                    SkillCatalogManifest.source(owner, document),
                    fingerprint);
        } catch (IllegalArgumentException failure) {
            return new ToolResult.Failure<>("skill_cursor_invalid", failure.getMessage());
        }
        if (offset < 0 || offset > contents.length()) {
            return new ToolResult.Failure<>("skill_cursor_invalid", "Skill cursor offset is outside the document");
        }
        RetainedSkillContext.Key documentKey = new RetainedSkillContext.Key(
                document.metadata().name(), documentName, SkillCatalogManifest.source(owner, document), fingerprint);
        SkillCatalogManifest.Chunk chunk = captured.chunks().stream()
                .filter(part -> part.offset() == offset).findFirst().orElse(null);
        if (chunk == null) {
            return new ToolResult.Failure<>("skill_cursor_invalid", "Skill cursor is not a document chunk boundary");
        }
        int end = chunk.end();
        // Stage only range facts for another call in the same pending exchange. The session index
        // is updated only when a complete, actual model-context projection is reconciled.
        if (!fresh) {
            Binding binding = requests.computeIfAbsent(context.correlationId(),
                    ignored -> new Binding(new RetainedSkillContext(), new ConcurrentHashMap<>()));
            synchronized (binding) {
                Map<Integer, RetainedSkillContext.Range> pending = binding.pending()
                        .computeIfAbsent(documentKey, ignored -> new ConcurrentHashMap<>());
                if (binding.retained().contains(documentKey, offset, end) || pending.containsKey(offset)) {
                    return new ToolResult.Success<>(output(document, documentName, captured,
                            LoadState.ALREADY_LOADED, offset, end, "", owner));
                }
                pending.put(offset, new RetainedSkillContext.Range(documentKey, offset, end, contents.length()));
            }
        }
        return new ToolResult.Success<>(output(document, documentName, captured,
                end == contents.length() ? LoadState.COMPLETE : LoadState.CONTENT,
                offset, end, contents.substring(offset, end), owner));
    }

    static Output output(SkillDocument skill, String name, SkillDocument.Text document,
            LoadState state, int offset, int end, String content, String owner) {
        boolean complete = end == document.contents().length();
        return new Output(skill.metadata().name(), name, SkillCatalogManifest.source(owner, skill), document.fingerprint(), state, content,
                offset, end, complete,
                complete ? "" : encodeCursor(skill.metadata().name(), name, SkillCatalogManifest.source(owner, skill), document.fingerprint(), end),
                dev.openallay.util.Java8Collections.toList(skill.references().keySet().stream().sorted()),
                dev.openallay.util.Java8Collections.toList(skill.metadata().allowedTools().stream().sorted()), skill.metadata().provenance());
    }

    @Override
    public void closeRequestScope(String correlationId) {
        requests.remove(correlationId);
    }

    static int chunkEnd(String contents, int offset) {
        if (offset < 0 || offset > contents.length()) {
            throw new IllegalArgumentException("Skill cursor offset is outside the document");
        }
        int hardEnd = Math.min(contents.length(), offset + CHUNK_CHARACTERS);
        if (hardEnd < contents.length()
                && hardEnd > offset
                && Character.isHighSurrogate(contents.charAt(hardEnd - 1))
                && Character.isLowSurrogate(contents.charAt(hardEnd))) {
            hardEnd--;
        }
        if (hardEnd == contents.length()) {
            return hardEnd;
        }
        int preferredFloor = offset + CHUNK_CHARACTERS / 2;
        int paragraph = contents.lastIndexOf("\n\n", hardEnd);
        if (paragraph >= preferredFloor) {
            return paragraph + 2;
        }
        int line = contents.lastIndexOf('\n', hardEnd);
        return line >= preferredFloor ? line + 1 : hardEnd;
    }

    static String encodeCursor(
            String name, String document, String source, String fingerprint, int offset) {
        String payload = String.join("\u0000", name, document, source, fingerprint, Integer.toString(offset));
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    static int decodeCursor(
            String cursor, String name, String document, String source, String fingerprint) {
        if (cursor == null || dev.openallay.util.Java8Strings.isBlank(cursor)) {
            return 0;
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] fields = decoded.split("\u0000", -1);
            if (fields.length != 5
                    || !name.equals(fields[0])
                    || !document.equals(fields[1])
                    || !source.equals(fields[2])
                    || !fingerprint.equals(fields[3])) {
                throw new IllegalArgumentException(
                        "Skill cursor does not belong to this document snapshot");
            }
            return Integer.parseInt(fields[4]);
        } catch (IllegalArgumentException failure) {
            if ("Skill cursor does not belong to this document snapshot"
                    .equals(failure.getMessage())) {
                throw failure;
            }
            throw new IllegalArgumentException("Skill cursor is malformed", failure);
        }
    }

    static String fingerprint(String contents) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(contents.getBytes(StandardCharsets.UTF_8));
            return dev.openallay.util.Java8Hex.formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

}
