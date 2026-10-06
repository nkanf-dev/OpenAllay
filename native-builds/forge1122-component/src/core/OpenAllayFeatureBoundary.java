package dev.openallay.forge1122.bootstrap;
import java.util.Map;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

/** Normal coremod-only loader facade; no feature algorithm or custom classloader. */
@IFMLLoadingPlugin.Name("OpenAllayFeatureBoundary")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(-10000)
@IFMLLoadingPlugin.TransformerExclusions({"dev.openallay.forge1122.bootstrap."})
public final class OpenAllayFeatureBoundary implements IFMLLoadingPlugin {
    public OpenAllayFeatureBoundary() {
        // Feature/native61 ownership stays intact; only Forge's Java8 parser excludes these own packages.
        Launch.classLoader.addTransformerExclusion("dev.openallay.");
        Launch.classLoader.addTransformerExclusion("dev.latvian.mods.rhino.");
        Launch.classLoader.addTransformerExclusion("org.commonmark.");
        Launch.classLoader.addTransformerExclusion("com.knuddels.jtokkit.");
        Launch.classLoader.addTransformerExclusion("org.sqlite.");
    }
    public String[] getASMTransformerClass() { return new String[0]; }
    public String getModContainerClass() { return null; }
    public String getSetupClass() { return null; }
    public String getAccessTransformerClass() { return null; }
    public void injectData(Map<String,Object> data) {
        if (!net.minecraftforge.fml.relauncher.CoreModManager.getIgnoredMods().contains("openallay-private-mixin.jar"))
            net.minecraftforge.fml.relauncher.CoreModManager.getIgnoredMods().add("openallay-private-mixin.jar");
        org.spongepowered.asm.mixin.Mixins.addConfiguration("openallay.client.mixins.json");
        org.spongepowered.asm.mixin.Mixins.addConfiguration("openallay.forge.mixins.json");
        org.spongepowered.asm.mixin.Mixins.addConfiguration("openallay.world.mixins.json");
        if (!"17".equals(System.getProperty("java.specification.version")))
            throw new IllegalStateException("OpenAllay runtime requires Java17");
        if (!"net.minecraft.launchwrapper.LaunchClassLoader".equals(getClass().getClassLoader().getClass().getName()))
            throw new IllegalStateException("Genuine stock LaunchClassLoader must own the feature boundary");
    }
}
