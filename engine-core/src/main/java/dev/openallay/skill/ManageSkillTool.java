package dev.openallay.skill;

import dev.openallay.agent.tool.ToolDescription;
import dev.openallay.agent.tool.ToolOptional;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.tool.Tool;
import dev.openallay.tool.ToolAccess;
import dev.openallay.tool.ToolDescriptor;
import dev.openallay.tool.ToolResult;
import java.util.List;
import java.util.Map;

/** Agent-facing CRUD for the validated OpenAllay-owned Skill directory only. */
public final class ManageSkillTool
        implements Tool<ManageSkillTool.Input, ManageSkillTool.Output> {
    @dev.openallay.value.ValueType(Input.ValueSchemaProvider.class)
public static final class Input {
    private final AgentSkillManager.Operation operation;
    private final String name;
    @ToolDescription("Complete SKILL.md including Agent Skills frontmatter.") @ToolOptional private final String markdown;
    @ToolDescription("Optional Markdown references keyed as references/name.md.") @ToolOptional private final Map<String, String> references;
    public Input(AgentSkillManager.Operation operation, String name, String markdown, Map<String, String> references) {

            references = references == null ? dev.openallay.util.Java8Collections.mapOf() : dev.openallay.util.Java8Collections.mapCopyOf(references);

        this.operation = operation;
        this.name = name;
        this.markdown = markdown;
        this.references = references;
    }
    public AgentSkillManager.Operation operation() { return operation; }
    public String name() { return name; }
    public String markdown() { return markdown; }
    public Map<String, String> references() { return references; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Input)) return false;
        Input that = (Input) other;
        return java.util.Objects.equals(operation, that.operation) && java.util.Objects.equals(name, that.name) && java.util.Objects.equals(markdown, that.markdown) && java.util.Objects.equals(references, that.references);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(operation);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(markdown);
        hash = 31 * hash + java.util.Objects.hashCode(references);
        return hash;
    }
    @Override public String toString() { return "Input[operation=" + operation + ", name=" + name + ", markdown=" + markdown + ", references=" + references + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Input> schema() {
            return new dev.openallay.value.ValueSchema<>(Input.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Input>>asList(new dev.openallay.value.ValueSchema.Component<>(Input.class, "operation", Input::operation), new dev.openallay.value.ValueSchema.Component<>(Input.class, "name", Input::name), new dev.openallay.value.ValueSchema.Component<>(Input.class, "markdown", Input::markdown), new dev.openallay.value.ValueSchema.Component<>(Input.class, "references", Input::references)), arguments -> new Input((AgentSkillManager.Operation) arguments[0], (String) arguments[1], (String) arguments[2], (Map) arguments[3]));
        }
    }
}

    @dev.openallay.value.ValueType(Output.ValueSchemaProvider.class)
public static final class Output {
    private final String operation;
    private final String name;
    private final String origin;
    private final List<String> availableReferences;
    private final String activation;
    public Output(String operation, String name, String origin, List<String> availableReferences, String activation) {

            availableReferences = dev.openallay.util.Java8Collections.listCopyOf(availableReferences);

        this.operation = operation;
        this.name = name;
        this.origin = origin;
        this.availableReferences = availableReferences;
        this.activation = activation;
    }
    public String operation() { return operation; }
    public String name() { return name; }
    public String origin() { return origin; }
    public List<String> availableReferences() { return availableReferences; }
    public String activation() { return activation; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Output)) return false;
        Output that = (Output) other;
        return java.util.Objects.equals(operation, that.operation) && java.util.Objects.equals(name, that.name) && java.util.Objects.equals(origin, that.origin) && java.util.Objects.equals(availableReferences, that.availableReferences) && java.util.Objects.equals(activation, that.activation);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(operation);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(origin);
        hash = 31 * hash + java.util.Objects.hashCode(availableReferences);
        hash = 31 * hash + java.util.Objects.hashCode(activation);
        return hash;
    }
    @Override public String toString() { return "Output[operation=" + operation + ", name=" + name + ", origin=" + origin + ", availableReferences=" + availableReferences + ", activation=" + activation + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Output> schema() {
            return new dev.openallay.value.ValueSchema<>(Output.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Output>>asList(new dev.openallay.value.ValueSchema.Component<>(Output.class, "operation", Output::operation), new dev.openallay.value.ValueSchema.Component<>(Output.class, "name", Output::name), new dev.openallay.value.ValueSchema.Component<>(Output.class, "origin", Output::origin), new dev.openallay.value.ValueSchema.Component<>(Output.class, "availableReferences", Output::availableReferences), new dev.openallay.value.ValueSchema.Component<>(Output.class, "activation", Output::activation)), arguments -> new Output((String) arguments[0], (String) arguments[1], (String) arguments[2], (List) arguments[3], (String) arguments[4]));
        }
    }
}

    private static final ToolDescriptor<Input, Output> DESCRIPTOR = new ToolDescriptor<>(
            "openallay:manage_skill",
            "Create, update, or delete an Agent Skill in OpenAllay's managed Skill store. "
                    + "This cannot access arbitrary paths; bundled Skills are immutable and changes "
                    + "become available to future requests after validation.",
            Input.class,
            Output.class,
            ToolAccess.MANAGED_WRITE);

    private final AgentSkillManager manager;

    public ManageSkillTool(AgentSkillManager manager) {
        this.manager = java.util.Objects.requireNonNull(manager, "manager");
    }

    @Override
    public ToolDescriptor<Input, Output> descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolResult<Output> invoke(ToolInvocationContext context, Input input) {
        if (input == null || input.operation() == null) {
            return new ToolResult.Failure<>(
                    "invalid_tool_arguments", "operation is required");
        }
        try {
            AgentSkillManager.Result result = switch (input.operation()) {
                case CREATE -> manager.create(input.name(), input.markdown(), input.references());
                case UPDATE -> manager.update(input.name(), input.markdown(), input.references());
                case DELETE -> manager.delete(input.name());
            };
            return new ToolResult.Success<>(new Output(
                    result.operation().name().toLowerCase(java.util.Locale.ROOT),
                    result.name(),
                    result.origin().name().toLowerCase(java.util.Locale.ROOT),
                    result.availableReferences(),
                    "available to future Agent requests"));
        } catch (SkillManagementException failure) {
            return new ToolResult.Failure<>(failure.code(), failure.getMessage());
        } catch (RuntimeException failure) {
            return new ToolResult.Failure<>(
                    "skill_management_failed", "Unable to manage the Skill");
        }
    }
}
