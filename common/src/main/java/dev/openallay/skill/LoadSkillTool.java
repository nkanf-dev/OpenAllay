package dev.openallay.skill;

import dev.openallay.context.ToolInvocationContext;
import dev.openallay.agent.tool.ToolOptional;
import dev.openallay.tool.ModelFacingToolOutput;
import dev.openallay.tool.Tool;
import dev.openallay.tool.ToolAccess;
import dev.openallay.tool.ToolDescriptor;
import dev.openallay.tool.ToolResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

public final class LoadSkillTool implements Tool<LoadSkillTool.Input, LoadSkillTool.Output> {
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

    public record Output(
            String name,
            String document,
            String content,
            int offset,
            int nextOffset,
            boolean complete,
            String nextCursor,
            List<String> availableReferences,
            List<String> allowedTools,
            String provenance)
            implements ModelFacingToolOutput {
        public Output {
            availableReferences = List.copyOf(availableReferences);
            allowedTools = List.copyOf(allowedTools);
        }

        @Override
        public String modelText() {
            StringBuilder text = new StringBuilder()
                    .append("skill: ").append(name).append('\n')
                    .append("document: ").append(document).append('\n')
                    .append("range: ").append(offset).append("..").append(nextOffset).append('\n')
                    .append("complete: ").append(complete).append('\n');
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
            "Progressively load one available Skill's instructions, or one exact declared reference after the Skill is loaded. "
                    + "When complete is false, continue with the returned opaque cursor and the same name/reference. "
                    + "This is mandatory before a matching workflow: collection-wide ranking, highest/lowest, "
                    + "comparison, grouping, aggregation, joins, and batch recipe analysis require "
                    + "analyze-game-data before run_javascript.",
            Input.class,
            Output.class,
            ToolAccess.READ_ONLY);

    private final SkillCatalog catalog;

    public LoadSkillTool(SkillCatalog catalog) {
        this.catalog = java.util.Objects.requireNonNull(catalog, "catalog");
    }

    @Override
    public ToolDescriptor<Input, Output> descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolResult<Output> invoke(ToolInvocationContext context, Input input) {
        if (input == null || input.name() == null || input.name().isBlank()) {
            return new ToolResult.Failure<>("invalid_skill_name", "Skill name must not be blank");
        }
        SkillDocument document = catalog.find(input.name()).orElse(null);
        if (document == null) {
            return new ToolResult.Failure<>(
                    "skill_not_found", "No available Skill named " + input.name());
        }
        List<String> availableReferences = document.references().keySet().stream().sorted().toList();
        String reference = input.reference() == null ? "" : input.reference().strip();
        String documentName = reference.isEmpty() ? "SKILL.md" : reference;
        String contents = reference.isEmpty()
                ? document.instructions()
                : document.references().get(reference);
        if (!reference.isEmpty() && contents == null) {
            return new ToolResult.Failure<>(
                    "skill_reference_not_found",
                    "Skill " + input.name() + " has no declared reference " + reference);
        }
        String fingerprint = fingerprint(contents);
        int offset;
        try {
            offset = decodeCursor(
                    input.cursor(),
                    document.metadata().name(),
                    documentName,
                    fingerprint);
        } catch (IllegalArgumentException failure) {
            return new ToolResult.Failure<>("skill_cursor_invalid", failure.getMessage());
        }
        int end = chunkEnd(contents, offset);
        boolean complete = end == contents.length();
        String nextCursor = complete
                ? ""
                : encodeCursor(
                        document.metadata().name(), documentName, fingerprint, end);
        return new ToolResult.Success<>(new Output(
                document.metadata().name(),
                documentName,
                contents.substring(offset, end),
                offset,
                end,
                complete,
                nextCursor,
                availableReferences,
                document.metadata().allowedTools().stream().sorted().toList(),
                document.metadata().provenance()));
    }

    private static int chunkEnd(String contents, int offset) {
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

    private static String encodeCursor(
            String name, String document, String fingerprint, int offset) {
        String payload = String.join("\u0000", "1", name, document, fingerprint, Integer.toString(offset));
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static int decodeCursor(
            String cursor, String name, String document, String fingerprint) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] fields = decoded.split("\u0000", -1);
            if (fields.length != 5
                    || !"1".equals(fields[0])
                    || !name.equals(fields[1])
                    || !document.equals(fields[2])
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

    private static String fingerprint(String contents) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(contents.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
