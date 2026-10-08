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
        final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<?> value; ToolResult.Success<?> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = result) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern0_holder.bound = (ToolResult.Success<?>) $oaPattern0_holder.value) != null))) {
            return "SUCCESS " + $oaPattern0_holder.bound.value();
        } else {
final class $oaPattern1_Holder { dev.openallay.tool.ToolResult<?> value; ToolResult.Failure<?> bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = result) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern1_holder.bound = (ToolResult.Failure<?>) $oaPattern1_holder.value) != null))) {
            return "FAILURE " + $oaPattern1_holder.bound.code() + ": " + $oaPattern1_holder.bound.message();
        }
}
        throw new IncompatibleClassChangeError();
    }
}
