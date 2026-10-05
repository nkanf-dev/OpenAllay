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

    public record Input(
            String name,
            @ToolOptional String reference,
            @ToolOptional String cursor) {
        public Input(String name) {
            this(name, null, null);
        }

        public Input(String name, String reference) {
            this(name, reference, null);
        }

    }

    public enum LoadState {
        CONTENT,
        COMPLETE,
        ALREADY_LOADED
    }

    public record Output(
            String name,
            String document,
            String source,
            String fingerprint,
            LoadState state,
            String content,
            int offset,
            int nextOffset,
            boolean complete,
            String nextCursor,
            List<String> availableReferences,
            List<String> allowedTools,
            String provenance)
            implements InstructionModelFacingToolOutput {
        public Output {
            java.util.Objects.requireNonNull(state, "state");
            availableReferences = List.copyOf(availableReferences);
            allowedTools = List.copyOf(allowedTools);
        }

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

    private record Binding(RetainedSkillContext retained, Map<RetainedSkillContext.Key,
            Map<Integer, RetainedSkillContext.Range>> pending) {}

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
        if (catalog instanceof SkillCatalogSnapshot snapshot) {
            captured = snapshot;
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
        if (input == null || input.name() == null || input.name().isBlank()) {
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
        String reference = input.reference() == null ? "" : input.reference().strip();
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
                skill.references().keySet().stream().sorted().toList(),
                skill.metadata().allowedTools().stream().sorted().toList(), skill.metadata().provenance());
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
        if (cursor == null || cursor.isBlank()) {
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
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

}
