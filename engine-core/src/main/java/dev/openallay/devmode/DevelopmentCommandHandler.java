package dev.openallay.devmode;

import dev.openallay.context.ToolInvocationContext;
import dev.openallay.tool.ToolResult;
import java.util.List;

public final class DevelopmentCommandHandler {
    private final DevelopmentToolInspector inspector;

    public DevelopmentCommandHandler(DevelopmentToolInspector inspector) {
        this.inspector = inspector;
    }

    public List<String> listTools() {
        return inspector.listTools();
    }

    public String invoke(String id) {
        return invoke(ToolInvocationContext.developmentConsole("dev:" + id), id);
    }

    public String invoke(ToolInvocationContext context, String id) {
        ToolResult<?> result = java.util.Objects.requireNonNull(inspector.invokeNoArgument(context, id));
        if (result instanceof ToolResult.Success<?> success) {
            return "SUCCESS " + success.value();
        } else if (result instanceof ToolResult.Failure<?> failure) {
            return "FAILURE " + failure.code() + ": " + failure.message();
        }
        throw new IncompatibleClassChangeError();
    }
}
