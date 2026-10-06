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
    private record Source(OpenAllayRuntime runtime, ICommandSender sender) implements DevelopmentCommandSpec.Source {
        @Override public void success(String text) { sender.sendMessage(new TextComponentString(text)); }
        @Override public void failure(String text) {
            TextComponentString message = new TextComponentString(text);
            message.getStyle().setColor(TextFormatting.RED);
            sender.sendMessage(message);
        }
        @Override public ToolResult<List<String>> traceIds() { return runtime.traceReplay().traceIds(sender); }
        @Override public ToolResult<ReplayReport> replay(String id) { return runtime.traceReplay().replay(sender, id); }
    }
}
