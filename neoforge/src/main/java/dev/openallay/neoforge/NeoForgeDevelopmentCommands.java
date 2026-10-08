package dev.openallay.neoforge;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.openallay.OpenAllayRuntime;
import dev.openallay.command.CommandArgument;
import dev.openallay.command.DevelopmentCommandSpec;
import dev.openallay.devmode.DevelopmentCommandHandler;
import dev.openallay.platform.minecraft.MinecraftComponents;
import dev.openallay.tool.ToolResult;
import dev.openallay.trace.replay.ReplayReport;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;

public final class NeoForgeDevelopmentCommands {
    private NeoForgeDevelopmentCommands() {}

    public static void register(OpenAllayRuntime runtime) {
        DevelopmentCommandHandler handler = new DevelopmentCommandHandler(runtime.developmentTools());
        LiteralArgumentBuilder<CommandSourceStack> devTree = literal(DevelopmentCommandSpec.GROUP)
                .requires(source -> dev.openallay.context.minecraft.MinecraftCommandPermissions.canReadWorld(source));
        for (DevelopmentCommandSpec.Route route : DevelopmentCommandSpec.routes()) {
            LiteralArgumentBuilder<CommandSourceStack> command = literal(route.literals().get(0));
            if (route.argument() == CommandArgument.NONE) {
                command.executes(context -> DevelopmentCommandSpec.dispatch(handler, route.action(), null,
                        new Source(runtime, context.getSource())));
            } else {
                String name = route.argument().argumentName();
                var value = argument(name,
                        route.argument().kind() == CommandArgument.Kind.WORD ? word() : greedyString());
                if (route.action() == DevelopmentCommandSpec.Action.REPLAY) {
                    value.suggests((context, builder) -> SharedSuggestionProvider.suggest(
                            DevelopmentCommandSpec.traceSuggestions(new Source(runtime, context.getSource())), builder));
                }
                command.then(value.executes(context -> DevelopmentCommandSpec.dispatch(handler, route.action(),
                        getString(context, name), new Source(runtime, context.getSource()))));
            }
            devTree.then(command);
        }
        NeoForgeNativeCommandRegistration.server(dispatcher ->
                dispatcher.register(literal(DevelopmentCommandSpec.ROOT).then(devTree)));
    }

    private record Source(OpenAllayRuntime runtime, CommandSourceStack nativeSource)
            implements DevelopmentCommandSpec.Source {
        @Override public void success(String line) {
            dev.openallay.context.minecraft.MinecraftCommandFeedback.success(
                    nativeSource, () -> MinecraftComponents.literal(line), false);
        }
        @Override public void failure(String line) {
            nativeSource.sendFailure(MinecraftComponents.literal(line));
        }
        @Override public ToolResult<List<String>> traceIds() {
            return runtime.traceReplay().traceIds(nativeSource);
        }
        @Override public ToolResult<ReplayReport> replay(String traceId) {
            return runtime.traceReplay().replay(nativeSource, traceId);
        }
    }
}
