package dev.openallay.client.gui.nativeview;

import dev.openallay.client.gui.MinecraftSemanticRenderer;
import dev.openallay.client.gui.hud.GuideHudResultRenderer;
import java.util.Collections;

/** Actual native family constructors/frontdoors only; no copied domain stand-ins. */
public final class NativeClosedFamilyIngressFixture {
    private static final class ForeignIntent implements MinecraftSemanticRenderer.Intent { }
    private static final class ForeignAction implements GuideHudResultRenderer.Action { }
    private static final class ForeignAttempt implements NativeDomainViewProvider.Attempt { }
    private static void reject(Runnable action) {
        try { action.run(); throw new AssertionError("Foreign variant admitted"); }
        catch (IncompatibleClassChangeError expected) {
            if (!expected.getMessage().startsWith("Unknown native ") || !expected.getMessage().endsWith(" subtype"))
                throw new AssertionError("Foreign family message differs", expected);
        }
    }
    private static void same(Object expected,Object actual) {
        if (expected!=actual) throw new AssertionError("Published variant identity changed");
    }
    public static void main(String[] args) {
        MinecraftSemanticRenderer.Intent[] intents={
            new MinecraftSemanticRenderer.Intent.BrowseRecipes("minecraft:stone"),
            new MinecraftSemanticRenderer.Intent.BrowseUsages("minecraft:stone"),
            new MinecraftSemanticRenderer.Intent.ExactRecipe(null),
            new MinecraftSemanticRenderer.Intent.Source("source","owner"),
            new MinecraftSemanticRenderer.Intent.Evidence("evidence","owner"),
            new MinecraftSemanticRenderer.Intent.Choice("node","choice")};
        for(MinecraftSemanticRenderer.Intent intent:intents) {
            same(intent,new MinecraftSemanticRenderer.Hit(null,intent).intent());
            same(intent,new GuideHudResultRenderer.Action.Semantic(intent).intent());
        }
        same(null,new MinecraftSemanticRenderer.Hit(null,null).intent());
        same(null,new GuideHudResultRenderer.Action.Semantic(null).intent());
        reject(()->new MinecraftSemanticRenderer.Hit(null,new ForeignIntent()));
        reject(()->new GuideHudResultRenderer.Action.Semantic(new ForeignIntent()));
        GuideHudResultRenderer.Action[] actions={new GuideHudResultRenderer.Action.Tool("row"),
            new GuideHudResultRenderer.Action.Semantic(intents[0]),new GuideHudResultRenderer.Action.Sources(Collections.emptyList())};
        for(GuideHudResultRenderer.Action action:actions)same(action,new GuideHudResultRenderer.Hit(null,action,"label").action());
        same(null,new GuideHudResultRenderer.Hit(null,null,null).action());
        reject(()->new GuideHudResultRenderer.Hit(null,new ForeignAction(),"label"));
        NativeDomainViewProvider.Attempt unsupported=new NativeDomainViewProvider.Attempt.Unsupported("native_unavailable");
        same(unsupported,NativeDomainViewProvider.Attempt.requireKnown(unsupported));
        same(null,NativeDomainViewProvider.Attempt.requireKnown(null));
        reject(()->NativeDomainViewProvider.Attempt.requireKnown(new ForeignAttempt()));
        System.out.println("PASS exact native Intent6/Action3/Attempt foreign admission and nullable aggregate contracts");
    }
}
