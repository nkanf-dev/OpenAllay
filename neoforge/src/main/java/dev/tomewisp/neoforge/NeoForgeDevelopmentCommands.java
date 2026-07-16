package dev.tomewisp.neoforge;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import dev.tomewisp.TomeWispRuntime;
import dev.tomewisp.devmode.DevelopmentCommandHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class NeoForgeDevelopmentCommands {
    private NeoForgeDevelopmentCommands() {}

    public static void register(TomeWispRuntime runtime) {
        DevelopmentCommandHandler handler =
                new DevelopmentCommandHandler(runtime.developmentTools());
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                event.getDispatcher()
                        .register(literal("tomewisp")
                                .then(literal("dev")
                                        .requires(source -> source.permissions()
                                                .hasPermission(
                                                        Permissions.COMMANDS_GAMEMASTER))
                                        .then(literal("tools").executes(context -> {
                                            handler.listTools().forEach(line -> context.getSource()
                                                    .sendSuccess(
                                                            () -> Component.literal(line), false));
                                            return 1;
                                        }))
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
}
