package dev.tomewisp.tool;

import dev.tomewisp.context.ToolInvocationContext;

public interface Tool<I, O> {
    ToolDescriptor<I, O> descriptor();

    ToolResult<O> invoke(ToolInvocationContext context, I input);
}
