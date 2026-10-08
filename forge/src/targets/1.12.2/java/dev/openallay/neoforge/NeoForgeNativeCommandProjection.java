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
    @dev.openallay.value.ValueType(Route.ValueSchemaProvider.class)
static final class Route<A> {
    private final List<String> literals;
    private final CommandArgument argument;
    private final A action;
    Route(List<String> literals, CommandArgument argument, A action) {
        this.literals = literals;
        this.argument = argument;
        this.action = action;
    }
    public List<String> literals() { return literals; }
    public CommandArgument argument() { return argument; }
    public A action() { return action; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Route)) return false;
        Route that = (Route) other;
        return java.util.Objects.equals(literals, that.literals) && java.util.Objects.equals(argument, that.argument) && java.util.Objects.equals(action, that.action);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(literals);
        hash = 31 * hash + java.util.Objects.hashCode(argument);
        hash = 31 * hash + java.util.Objects.hashCode(action);
        return hash;
    }
    @Override public String toString() { return "Route[literals=" + literals + ", argument=" + argument + ", action=" + action + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Route> schema() {
            return new dev.openallay.value.ValueSchema<>(Route.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Route>>asList(new dev.openallay.value.ValueSchema.Component<>(Route.class, "literals", Route::literals), new dev.openallay.value.ValueSchema.Component<>(Route.class, "argument", Route::argument), new dev.openallay.value.ValueSchema.Component<>(Route.class, "action", Route::action)), arguments -> new Route((List) arguments[0], (CommandArgument) arguments[1], (Object) arguments[2]));
        }
    }
}
    @dev.openallay.value.ValueType(Invocation.ValueSchemaProvider.class)
static final class Invocation<A> {
    private final A action;
    private final String value;
    Invocation(A action, String value) {
        this.action = action;
        this.value = value;
    }
    public A action() { return action; }
    public String value() { return value; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Invocation)) return false;
        Invocation that = (Invocation) other;
        return java.util.Objects.equals(action, that.action) && java.util.Objects.equals(value, that.value);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(action);
        hash = 31 * hash + java.util.Objects.hashCode(value);
        return hash;
    }
    @Override public String toString() { return "Invocation[action=" + action + ", value=" + value + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Invocation> schema() {
            return new dev.openallay.value.ValueSchema<>(Invocation.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Invocation>>asList(new dev.openallay.value.ValueSchema.Component<>(Invocation.class, "action", Invocation::action), new dev.openallay.value.ValueSchema.Component<>(Invocation.class, "value", Invocation::value)), arguments -> new Invocation((Object) arguments[0], (String) arguments[1]));
        }
    }
}
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
