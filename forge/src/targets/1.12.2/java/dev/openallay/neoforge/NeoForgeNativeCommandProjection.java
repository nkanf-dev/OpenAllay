package dev.openallay.neoforge;

import dev.openallay.command.CommandArgument;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

/** Projects neutral routes onto native ICommand parsing and completion; contains no feature grammar. */
final class NeoForgeNativeCommandProjection<A> extends CommandBase {
    record Route<A>(List<String> literals, CommandArgument argument, A action) {}
    record Invocation<A>(A action, String value) {}
    private final String root;
    private final List<Route<A>> routes;
    private final BiConsumer<ICommandSender, Invocation<A>> dispatch;
    private final BiPredicate<MinecraftServer, ICommandSender> permission;
    private final BiFunction<ICommandSender, CommandArgument, List<String>> arguments;
    NeoForgeNativeCommandProjection(String root, List<Route<A>> routes,
            BiConsumer<ICommandSender, Invocation<A>> dispatch,
            BiPredicate<MinecraftServer, ICommandSender> permission,
            BiFunction<ICommandSender, CommandArgument, List<String>> arguments) {
        this.root = root; this.routes = List.copyOf(routes); this.dispatch = dispatch;
        this.permission = permission; this.arguments = arguments;
    }
    @Override public String getName() { return root; }
    @Override public String getUsage(ICommandSender sender) { return "/" + root; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return permission.test(server, sender);
    }
    private boolean literalFirst(String[] tokens, Route<A> candidate) {
        for (Route<A> route : routes) {
            int shared = Math.min(tokens.length, Math.min(route.literals().size(), candidate.literals().size()));
            boolean prefix = true;
            for (int i = 0; i < shared; i++) if (!tokens[i].equals(route.literals().get(i))
                    || !tokens[i].equals(candidate.literals().get(i))) { prefix = false; break; }
            if (prefix && route.literals().size() > candidate.literals().size()
                    && tokens.length > candidate.literals().size()
                    && tokens[candidate.literals().size()].equals(route.literals().get(candidate.literals().size()))) return false;
        }
        return true;
    }
    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] tokens) throws CommandException {
        for (Route<A> route : routes) {
            int count = route.literals().size();
            if (tokens.length < count || !literalFirst(tokens, route)) continue;
            boolean matches = true;
            for (int i = 0; i < count; i++) if (!tokens[i].equals(route.literals().get(i))) { matches = false; break; }
            if (!matches) continue;
            int remaining = tokens.length - count;
            if (route.argument().kind() == CommandArgument.Kind.NONE && remaining != 0) continue;
            if (route.argument().kind() == CommandArgument.Kind.WORD && remaining != 1) continue;
            if (route.argument().kind() == CommandArgument.Kind.GREEDY && remaining == 0) continue;
            String value = remaining == 0 ? "" : CommandBase.buildString(tokens, count);
            dispatch.accept(sender, new Invocation<>(route.action(), value));
            return;
        }
        throw new CommandException("commands.generic.usage", getUsage(sender));
    }
    @Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
            String[] tokens, BlockPos position) {
        if (!checkPermission(server, sender) || tokens.length == 0) return List.of();
        int cursor = tokens.length - 1;
        List<String> choices = new ArrayList<>();
        for (Route<A> route : routes) {
            int fixed = route.literals().size();
            boolean matches = true;
            for (int i = 0; i < Math.min(cursor, fixed); i++) {
                if (!tokens[i].equals(route.literals().get(i))) { matches = false; break; }
            }
            if (!matches) continue;
            if (cursor < fixed) choices.add(route.literals().get(cursor));
            else if (cursor == fixed && route.argument().kind() != CommandArgument.Kind.NONE
                    && literalFirst(tokens, route)) choices.addAll(arguments.apply(sender, route.argument()));
        }
        return getListOfStringsMatchingLastWord(tokens, choices.stream().distinct().toList());
    }
}
