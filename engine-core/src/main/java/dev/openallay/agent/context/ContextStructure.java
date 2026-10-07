package dev.openallay.agent.context;

import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Validates and groups model messages without provider-specific assumptions. */
public final class ContextStructure {
    private ContextStructure() {}

    @dev.openallay.value.ValueType(Unit.ValueSchemaProvider.class)
public static final class Unit {
    private final int fromIndex;
    private final int toIndexExclusive;
    private final boolean toolExchange;
    private final List<ModelMessage> messages;
    public Unit(int fromIndex, int toIndexExclusive, boolean toolExchange, List<ModelMessage> messages) {

            if (fromIndex < 0 || toIndexExclusive <= fromIndex) {
                throw new IllegalArgumentException("context unit range is invalid");
            }
            messages = dev.openallay.util.Java8Collections.listCopyOf(messages);
            if (messages.size() != toIndexExclusive - fromIndex) {
                throw new IllegalArgumentException("context unit range does not match messages");
            }

        this.fromIndex = fromIndex;
        this.toIndexExclusive = toIndexExclusive;
        this.toolExchange = toolExchange;
        this.messages = messages;
    }
    public int fromIndex() { return fromIndex; }
    public int toIndexExclusive() { return toIndexExclusive; }
    public boolean toolExchange() { return toolExchange; }
    public List<ModelMessage> messages() { return messages; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Unit)) return false;
        Unit that = (Unit) other;
        return fromIndex == that.fromIndex && toIndexExclusive == that.toIndexExclusive && toolExchange == that.toolExchange && java.util.Objects.equals(messages, that.messages);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(fromIndex);
        hash = 31 * hash + Integer.hashCode(toIndexExclusive);
        hash = 31 * hash + Boolean.hashCode(toolExchange);
        hash = 31 * hash + java.util.Objects.hashCode(messages);
        return hash;
    }
    @Override public String toString() { return "Unit[fromIndex=" + fromIndex + ", toIndexExclusive=" + toIndexExclusive + ", toolExchange=" + toolExchange + ", messages=" + messages + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Unit> schema() {
            return new dev.openallay.value.ValueSchema<>(Unit.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Unit>>asList(new dev.openallay.value.ValueSchema.Component<>(Unit.class, "fromIndex", Unit::fromIndex), new dev.openallay.value.ValueSchema.Component<>(Unit.class, "toIndexExclusive", Unit::toIndexExclusive), new dev.openallay.value.ValueSchema.Component<>(Unit.class, "toolExchange", Unit::toolExchange), new dev.openallay.value.ValueSchema.Component<>(Unit.class, "messages", Unit::messages)), arguments -> new Unit((Integer) arguments[0], (Integer) arguments[1], (Boolean) arguments[2], (List) arguments[3]));
        }
    }
}

    public static List<Unit> units(List<ModelMessage> messages) {
        messages = dev.openallay.util.Java8Collections.listCopyOf(messages);
        ArrayList<Unit> units = new ArrayList<>();
        Set<String> seenToolUses = new HashSet<>();
        int index = 0;
        while (index < messages.size()) {
            ModelMessage message = messages.get(index);
            List<ModelContent.ToolUse> uses = toolUses(message);
            List<ModelContent.ToolResult> standaloneResults = toolResults(message);
            if (!standaloneResults.isEmpty()) {
                throw new IllegalArgumentException("orphan tool result at message " + index);
            }
            if (uses.isEmpty()) {
                units.add(new Unit(index, index + 1, false, dev.openallay.util.Java8Collections.listOf(message)));
                index++;
                continue;
            }
            if (message.role() != ModelRole.ASSISTANT) {
                throw new IllegalArgumentException("tool use must belong to an assistant message");
            }
            if (index + 1 >= messages.size()) {
                throw new IllegalArgumentException("tool use has no following result message");
            }
            for (ModelContent.ToolUse use : uses) {
                if (!seenToolUses.add(use.id())) {
                    throw new IllegalArgumentException("duplicate tool-use id " + use.id());
                }
            }
            ModelMessage resultMessage = messages.get(index + 1);
            List<ModelContent.ToolResult> results = toolResults(resultMessage);
            if (resultMessage.role() != ModelRole.USER
                    || results.size() != resultMessage.content().size()
                    || results.size() != uses.size()) {
                throw new IllegalArgumentException("tool result message does not match tool uses");
            }
            for (int resultIndex = 0; resultIndex < uses.size(); resultIndex++) {
                if (!uses.get(resultIndex).id().equals(results.get(resultIndex).toolUseId())) {
                    throw new IllegalArgumentException("tool results are missing or out of order");
                }
            }
            units.add(new Unit(index, index + 2, true, dev.openallay.util.Java8Collections.listOf(message, resultMessage)));
            index += 2;
        }
        return dev.openallay.util.Java8Collections.listCopyOf(units);
    }

    public static void requireBoundary(List<Unit> units, int boundary, int messageCount) {
        if (boundary < 0 || boundary > messageCount) {
            throw new IllegalArgumentException("protected context boundary is out of range");
        }
        if (boundary == 0 || boundary == messageCount) {
            return;
        }
        boolean found = units.stream().anyMatch(unit -> unit.fromIndex() == boundary);
        if (!found) {
            throw new IllegalArgumentException("protected context boundary splits a structural unit");
        }
    }

    /** Removes provider-private reasoning before content enters a summary prompt. */
    public static List<ModelMessage> summarySafe(List<ModelMessage> messages) {
        ArrayList<ModelMessage> safe = new ArrayList<>();
        for (ModelMessage message : messages) {
            List<ModelContent> content = dev.openallay.util.Java8Collections.toList(message.content().stream()
                    .filter(item -> !(item instanceof ModelContent.Reasoning)));
            if (!content.isEmpty()) {
                safe.add(content.size() == message.content().size()
                        ? message : new ModelMessage(message.role(), content, message.inputObservation()));
            }
        }
        return dev.openallay.util.Java8Collections.listCopyOf(safe);
    }

    private static List<ModelContent.ToolUse> toolUses(ModelMessage message) {
        return dev.openallay.util.Java8Collections.toList(message.content().stream()
                .filter(ModelContent.ToolUse.class::isInstance)
                .map(ModelContent.ToolUse.class::cast));
    }

    private static List<ModelContent.ToolResult> toolResults(ModelMessage message) {
        return dev.openallay.util.Java8Collections.toList(message.content().stream()
                .filter(ModelContent.ToolResult.class::isInstance)
                .map(ModelContent.ToolResult.class::cast));
    }
}
