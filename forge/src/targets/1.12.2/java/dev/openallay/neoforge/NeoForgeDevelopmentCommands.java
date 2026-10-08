package dev.openallay.neoforge;

import dev.openallay.OpenAllayRuntime;
import dev.openallay.command.CommandArgument;
import dev.openallay.command.DevelopmentCommandSpec;
import dev.openallay.devmode.DevelopmentCommandHandler;
import dev.openallay.tool.ToolResult;
import dev.openallay.trace.replay.ReplayReport;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

/** Native CommandBase projection; actions and trace result handling stay canonical. */
public final class NeoForgeDevelopmentCommands {
    private NeoForgeDevelopmentCommands() {}
    public static void register(OpenAllayRuntime runtime) {
        DevelopmentCommandHandler handler = new DevelopmentCommandHandler(runtime.developmentTools());
        var routes = DevelopmentCommandSpec.routes().stream().map(route -> {
            List<String> literals = new ArrayList<>();
            literals.add(DevelopmentCommandSpec.GROUP);
            literals.addAll(route.literals());
            return new NeoForgeNativeCommandProjection.Route<>(List.copyOf(literals), route.argument(), route.action());
        }).toList();
        NeoForgeNativeCommandRegistration.server(new NeoForgeNativeCommandProjection<>(
                DevelopmentCommandSpec.ROOT, routes,
                (sender, invocation) -> DevelopmentCommandSpec.dispatch(handler, invocation.action(),
                        invocation.value(), new Source(runtime, sender)),
                (server, sender) -> sender.canUseCommand(2, DevelopmentCommandSpec.ROOT),
                (sender, argument) -> argument == CommandArgument.TRACE_WORD
                        ? DevelopmentCommandSpec.traceSuggestions(new Source(runtime, sender)) : List.of()));
    }
    @dev.openallay.value.ValueType(Source.ValueSchemaProvider.class)
private static final class Source implements DevelopmentCommandSpec.Source {
    private final OpenAllayRuntime runtime;
    private final ICommandSender sender;
    private Source(OpenAllayRuntime runtime, ICommandSender sender) {
        this.runtime = runtime;
        this.sender = sender;
    }
    public OpenAllayRuntime runtime() { return runtime; }
    public ICommandSender sender() { return sender; }
@Override public void success(String text) { sender.sendMessage(new TextComponentString(text)); }
@Override public void failure(String text) {
            TextComponentString message = new TextComponentString(text);
            message.getStyle().setColor(TextFormatting.RED);
            sender.sendMessage(message);
        }
@Override public ToolResult<List<String>> traceIds() { return runtime.traceReplay().traceIds(sender); }
@Override public ToolResult<ReplayReport> replay(String id) { return runtime.traceReplay().replay(sender, id); }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Source)) return false;
        Source that = (Source) other;
        return java.util.Objects.equals(runtime, that.runtime) && java.util.Objects.equals(sender, that.sender);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(runtime);
        hash = 31 * hash + java.util.Objects.hashCode(sender);
        return hash;
    }
    @Override public String toString() { return "Source[runtime=" + runtime + ", sender=" + sender + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Source> schema() {
            return new dev.openallay.value.ValueSchema<>(Source.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Source>>asList(new dev.openallay.value.ValueSchema.Component<>(Source.class, "runtime", Source::runtime), new dev.openallay.value.ValueSchema.Component<>(Source.class, "sender", Source::sender)), arguments -> new Source((OpenAllayRuntime) arguments[0], (ICommandSender) arguments[1]));
        }
    }
}
}
