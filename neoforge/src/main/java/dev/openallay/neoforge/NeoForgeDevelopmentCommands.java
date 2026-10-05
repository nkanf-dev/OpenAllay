package dev.openallay.neoforge;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import dev.openallay.OpenAllayRuntime;
import dev.openallay.devmode.DevelopmentCommandHandler;
import dev.openallay.tool.ToolResult;
import dev.openallay.trace.replay.ReplayReport;
import java.util.concurrent.CompletableFuture;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public final class NeoForgeDevelopmentCommands {
    private NeoForgeDevelopmentCommands() {}

    public static void register(OpenAllayRuntime runtime) {
        DevelopmentCommandHandler handler =
                new DevelopmentCommandHandler(runtime.developmentTools());
        NeoForgeNativeCommandRegistration.server(dispatcher ->
                dispatcher.register(literal("openallay")
                                .then(literal("dev")
                                        .requires(source -> dev.openallay.context.minecraft.MinecraftCommandPermissions.canReadWorld(source))
                                        .then(literal("tools").executes(context -> {
                                            handler.listTools().forEach(line -> context.getSource()
                                                    .sendSuccess(
                                                            () -> Component.literal(line), false));
                                            return 1;
                                        }))
                                        .then(literal("replay")
                                                .then(argument("trace", word())
                                                        .suggests((context, builder) -> suggestTraces(
                                                                runtime,
                                                                context.getSource(),
                                                                builder))
                                                        .executes(context -> replay(
                                                                runtime,
                                                                context.getSource(),
                                                                getString(context, "trace")))))
                                        .then(literal("invoke")
                                                .then(argument("tool", greedyString())
                                                        .executes(context -> {
                                                            String id =
                                                                    getString(context, "tool");
                                                            context.getSource().sendSuccess(
                                                                    () -> Component.literal(
                                                                            handler.invoke(id)),
                                                                    false);
                                                            return 1;
                                                        }))))));
    }

    private static CompletableFuture<Suggestions> suggestTraces(
            OpenAllayRuntime runtime, CommandSourceStack source, SuggestionsBuilder builder) {
        ToolResult<java.util.List<String>> result = runtime.traceReplay().traceIds(source);
        if (result instanceof ToolResult.Success<java.util.List<String>> success) {
            return SharedSuggestionProvider.suggest(success.value(), builder);
        }
        return builder.buildFuture();
    }

    private static int replay(
            OpenAllayRuntime runtime, CommandSourceStack source, String traceId) {
        ToolResult<ReplayReport> result = runtime.traceReplay().replay(source, traceId);
        if (result instanceof ToolResult.Success<ReplayReport> success) {
            success.value().chatLines().forEach(line ->
                    source.sendSuccess(() -> Component.literal(line), false));
            return success.value().passed() ? 1 : 0;
        }
        ToolResult.Failure<ReplayReport> failure = (ToolResult.Failure<ReplayReport>) result;
        source.sendFailure(Component.literal(
                "FAILURE " + failure.code() + ": " + failure.message()));
        return 0;
    }
}
