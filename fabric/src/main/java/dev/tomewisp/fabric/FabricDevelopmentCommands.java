package dev.tomewisp.fabric;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import dev.tomewisp.TomeWispRuntime;
import dev.tomewisp.devmode.DevelopmentCommandHandler;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

public final class FabricDevelopmentCommands {
    private FabricDevelopmentCommands() {}

    public static void register(TomeWispRuntime runtime) {
        DevelopmentCommandHandler handler =
                new DevelopmentCommandHandler(runtime.developmentTools());
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(literal("tomewisp")
                        .then(literal("dev")
                                .requires(source -> source.permissions()
                                        .hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .then(literal("tools").executes(context -> {
                                    handler.listTools().forEach(line -> context.getSource()
                                            .sendSuccess(() -> Component.literal(line), false));
                                    return 1;
                                }))
                                .then(literal("invoke")
                                        .then(argument("tool", greedyString())
                                                .executes(context -> {
                                                    String id = getString(context, "tool");
                                                    context.getSource().sendSuccess(
                                                            () -> Component.literal(
                                                                    handler.invoke(id)),
                                                            false);
                                                    return 1;
                                                }))))));
    }
}
