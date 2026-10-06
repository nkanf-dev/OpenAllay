package dev.openallay.neoforge;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

import dev.openallay.guide.GuideCommandFacade;
import dev.openallay.guide.GuideModelMode;
import dev.openallay.guide.GuideNotice;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.function.Function;
import java.util.function.Consumer;

public final class NeoForgeGuideCommands {
    private NeoForgeGuideCommands() {}

    public static void register(GuideCommandFacade guide) {
        NeoForgeNativeGuideCommandRegistration.register(guide);
    }

    /** One grammar, projected onto each real execution/completion source. */
    public static <S> LiteralArgumentBuilder<S> tree(
            GuideCommandFacade guide, Function<S, GuideCommandSource> source) {
        return LiteralArgumentBuilder.<S>literal("guide")
                        .executes(context -> invoke(source.apply(context.getSource()), sink -> guide.open(source.apply(context.getSource()).actor(), sink)))
                        .then(LiteralArgumentBuilder.<S>literal("cancel").executes(context -> invoke(
                                source.apply(context.getSource()), sink -> guide.cancel(source.apply(context.getSource()).actor(), sink))))
                        .then(LiteralArgumentBuilder.<S>literal("retry").executes(context -> invoke(
                                source.apply(context.getSource()), sink -> guide.retry(source.apply(context.getSource()).actor(), sink))))
                        .then(LiteralArgumentBuilder.<S>literal("clear").executes(context -> invoke(
                                source.apply(context.getSource()), sink -> guide.clear(source.apply(context.getSource()).actor(), sink))))
                        .then(LiteralArgumentBuilder.<S>literal("status").executes(context -> invoke(
                                source.apply(context.getSource()), sink -> guide.status(source.apply(context.getSource()).actor(), sink))))
                        .then(LiteralArgumentBuilder.<S>literal("skills").executes(context -> invoke(
                                source.apply(context.getSource()), guide::skills)))
                        .then(LiteralArgumentBuilder.<S>literal("sources").executes(context -> invoke(
                                source.apply(context.getSource()), guide::sources)))
                        .then(LiteralArgumentBuilder.<S>literal("model")
                                .then(LiteralArgumentBuilder.<S>literal("list").executes(context -> invoke(
                                        source.apply(context.getSource()), sink -> guide.models(source.apply(context.getSource()).actor(), sink))))
                                .then(LiteralArgumentBuilder.<S>literal("profile").then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, String>argument("id", word()).executes(
                                        context -> invoke(source.apply(context.getSource()), sink -> guide.modelProfile(
                                                source.apply(context.getSource()).actor(), getString(context, "id"), sink)))))
                                .then(LiteralArgumentBuilder.<S>literal("client").executes(context -> invoke(
                                        source.apply(context.getSource()), sink -> guide.model(
                                                source.apply(context.getSource()).actor(), GuideModelMode.CLIENT, sink))))
                                .then(LiteralArgumentBuilder.<S>literal("server").executes(context -> invoke(
                                        source.apply(context.getSource()), sink -> guide.model(
                                                source.apply(context.getSource()).actor(), GuideModelMode.SERVER, sink)))))
                        .then(LiteralArgumentBuilder.<S>literal("session")
                                .then(LiteralArgumentBuilder.<S>literal("list").executes(context -> invoke(
                                        source.apply(context.getSource()), sink -> guide.sessions(source.apply(context.getSource()).actor(), sink))))
                                .then(LiteralArgumentBuilder.<S>literal("new").then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, String>argument("id", word()).executes(context -> invoke(
                                        source.apply(context.getSource()), sink -> guide.select(
                                                source.apply(context.getSource()).actor(), getString(context, "id"), sink)))))
                                .then(LiteralArgumentBuilder.<S>literal("switch").then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, String>argument("id", word()).executes(context -> invoke(
                                        source.apply(context.getSource()), sink -> guide.select(
                                                source.apply(context.getSource()).actor(), getString(context, "id"), sink)))))
                                .then(LiteralArgumentBuilder.<S>literal("close").then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, String>argument("id", word()).executes(context -> invoke(
                                        source.apply(context.getSource()), sink -> guide.close(
                                                source.apply(context.getSource()).actor(), getString(context, "id"), sink))))))
                        .then(LiteralArgumentBuilder.<S>literal("ask").then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, String>argument("question", greedyString()).executes(context -> invoke(
                                source.apply(context.getSource()), sink -> guide.ask(
                                        source.apply(context.getSource()).actor(), getString(context, "question"), sink)))))
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, String>argument("question", greedyString()).executes(context -> invoke(
                                source.apply(context.getSource()), sink -> guide.ask(
                                        source.apply(context.getSource()).actor(), getString(context, "question"), sink))));
    }

    private static int invoke(GuideCommandSource source, Consumer<Consumer<GuideNotice>> operation) {
        operation.accept(source::publish);
        return 1;
    }
}
