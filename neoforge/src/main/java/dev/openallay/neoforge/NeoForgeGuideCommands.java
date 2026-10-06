package dev.openallay.neoforge;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.openallay.command.CommandArgument;
import dev.openallay.command.GuideCommandSpec;
import dev.openallay.guide.GuideCommandFacade;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

public final class NeoForgeGuideCommands {
    private NeoForgeGuideCommands() {}

    public static void register(GuideCommandFacade guide) {
        NeoForgeNativeGuideCommandRegistration.register(guide);
    }

    /** Project the shared finite grammar onto the real execution source. */
    public static <S> LiteralArgumentBuilder<S> tree(
            GuideCommandFacade guide, Function<S, GuideCommandSource> source) {
        return project(guide, source, List.of(), GuideCommandSpec.ROOT);
    }

    private static <S> LiteralArgumentBuilder<S> project(
            GuideCommandFacade guide, Function<S, GuideCommandSource> source,
            List<String> prefix, String literal) {
        LiteralArgumentBuilder<S> node = LiteralArgumentBuilder.literal(literal);
        LinkedHashSet<String> children = new LinkedHashSet<>();
        for (GuideCommandSpec.Route route : GuideCommandSpec.routes()) {
            if (route.literals().size() > prefix.size()
                    && route.literals().subList(0, prefix.size()).equals(prefix)) {
                children.add(route.literals().get(prefix.size()));
            }
        }
        for (String child : children) {
            List<String> path = new ArrayList<>(prefix);
            path.add(child);
            node.then(project(guide, source, path, child));
        }
        for (GuideCommandSpec.Route route : GuideCommandSpec.routes()) {
            if (!route.literals().equals(prefix)) continue;
            if (route.argument() == CommandArgument.NONE) {
                node.executes(context -> dispatch(guide, source.apply(context.getSource()), route, null));
            } else {
                String name = route.argument().argumentName();
                node.then(RequiredArgumentBuilder.<S, String>argument(name,
                        route.argument().kind() == CommandArgument.Kind.WORD ? word() : greedyString())
                        .executes(context -> dispatch(guide, source.apply(context.getSource()), route,
                                getString(context, name))));
            }
        }
        return node;
    }

    private static int dispatch(
            GuideCommandFacade guide, GuideCommandSource source,
            GuideCommandSpec.Route route, String value) {
        return GuideCommandSpec.dispatch(guide, route.action(), value, source::actor, source::publish);
    }
}
