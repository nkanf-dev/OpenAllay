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
    public record Input(
            AgentSkillManager.Operation operation,
            String name,
            @ToolDescription("Complete SKILL.md including Agent Skills frontmatter.")
                    @ToolOptional String markdown,
            @ToolDescription("Optional Markdown references keyed as references/name.md.")
                    @ToolOptional Map<String, String> references) {
        public Input {
            references = references == null ? Map.of() : Map.copyOf(references);
        }
    }

    public record Output(
            String operation,
            String name,
            String origin,
            List<String> availableReferences,
            String activation) {
        public Output {
            availableReferences = List.copyOf(availableReferences);
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
