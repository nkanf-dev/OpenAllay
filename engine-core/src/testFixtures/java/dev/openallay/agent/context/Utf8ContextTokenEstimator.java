package dev.openallay.agent.context;

import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelToolDefinition;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Test-only deterministic byte fixture. It deliberately is not a model tokenizer.
 * Byte-sized fixtures keep structural compaction tests stable across tokenizer libraries.
 */
public final class Utf8ContextTokenEstimator implements ContextTokenEstimator {
    private static final int REQUEST_OVERHEAD = 16;
    private static final int MESSAGE_OVERHEAD = 12;
    private static final int CONTENT_OVERHEAD = 8;
    private static final int TOOL_OVERHEAD = 16;

    @Override
    public int estimate(
            String systemPrompt,
            List<ModelMessage> messages,
            List<ModelToolDefinition> tools) {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
        long estimate = REQUEST_OVERHEAD + bytes(systemPrompt);
        for (ModelMessage message : messages) {
            estimate += MESSAGE_OVERHEAD + bytes(message.role().name());
            for (ModelContent content : message.content()) {
                estimate += CONTENT_OVERHEAD + contentBytes(content);
            }
        }
        for (ModelToolDefinition tool : tools) {
            estimate += TOOL_OVERHEAD
                    + bytes(tool.name())
                    + bytes(tool.description())
                    + bytes(tool.inputSchema().toString());
        }
        return estimate >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) estimate;
    }

    @Override
    public int estimateText(String text) {
        return bytes(text);
    }

    private static long contentBytes(ModelContent content) {
        return switch (content) {
            case ModelContent.Text text -> bytes("text") + bytes(text.text());
            case ModelContent.Image image -> bytes("image")
                    + bytes(image.reference().mimeType())
                    + bytes(Integer.toString(image.reference().width()))
                    + bytes(Integer.toString(image.reference().height()))
                    + bytes(Long.toString(image.reference().byteSize()));
            case ModelContent.Reasoning reasoning -> bytes("reasoning")
                    + bytes(reasoning.text())
                    + bytes(reasoning.signature() == null ? "" : reasoning.signature());
            case ModelContent.ToolUse use -> bytes("tool_use")
                    + bytes(use.id())
                    + bytes(use.name())
                    + bytes(use.input().toString());
            case ModelContent.ToolResult result -> bytes("tool_result")
                    + bytes(result.toolUseId())
                    + 1
                    + bytes(result.value().toString());
            default -> throw new IncompatibleClassChangeError("Unknown model content subtype");
        };
    }

    private static int bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
