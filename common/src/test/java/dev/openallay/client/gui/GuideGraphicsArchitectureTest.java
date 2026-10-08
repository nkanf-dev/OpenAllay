package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Source ownership and actual routes, without fake native graphics classes. */
final class GuideGraphicsArchitectureTest {
    private static final String GUI = "dev/openallay/client/gui/";
    private static final List<String> BASES = List.of(
            "common/src/main/java/" + GUI + "GuideNativeGraphics.java",
            "common/src/targets/1.21.11/java/" + GUI + "GuideNativeGraphics.java",
            "common/src/targets/1.21.5/java/" + GUI + "GuideNativeGraphics.java");

    @Test void featureApiAndCompositeOperationHaveOneSourceOwner() throws IOException {
        Path root = root();
        String facade = read(root, "common/src/main/java/" + GUI + "GuideGraphics.java");
        assertTrue(facade.contains("public final class GuideGraphics extends GuideNativeGraphics"));
        assertTrue(facade.contains("GuideGraphics(GuideNativeGraphics binding) { super(binding); }"));
        for (String rawCanvas : List.of("GuiGraphicsExtractor", "import net.minecraft.client.gui.GuiGraphics;",
                "nativeGraphics()", "Class.forName", "java.lang.reflect", "Object graphics")) {
            assertFalse(facade.contains(rawCanvas), rawCanvas);
        }
        try (var sources = Files.walk(root.resolve("common/src/targets"))) {
            assertEquals(0, sources.filter(path -> path.getFileName().toString().equals("GuideGraphics.java")).count(),
                    "Adding a Guide operation must not edit copied public facades");
        }
        String outline = facade.substring(facade.indexOf("public void outline("), facade.indexOf("public void text("));
        assertEquals(4, occurrences(outline, "fill("));
        assertFalse(outline.contains("native"), "Compositions use the one public operation set");
        for (String path : BASES) {
            String base = read(root, path);
            assertFalse(base.contains("outline("), path);
            assertFalse(Pattern.compile("public\\s+(?:final\\s+)?(?:void|int)\\s+").matcher(base).find(),
                    "Public feature operations belong only to GuideGraphics: " + path);
            assertEquals(primitiveSignatures(base), facadeRouteSignatures(facade), path);
            assertTrue(facadeRouteSignatures(facade).size() > 20, "Actual typed primitive routes are checked");
        }
    }

    @Test void basesOwnActualTypedRoutesAndOptionalViewerAbi() throws IOException {
        Path root = root();
        String extractor = read(root, BASES.get(0));
        String callback = read(root, BASES.get(1));
        String immediate = read(root, BASES.get(2));
        for (String fragment : List.of("private final GuiGraphicsExtractor graphics;",
                "public final GuiGraphicsExtractor nativeGraphics()", "wrap(GuiGraphicsExtractor graphics)",
                "graphics.text(font, text, x, y, color)", "graphics.item(stack, x, y)",
                "graphics.pose().pushMatrix()", "graphics.blit(RenderPipelines.GUI_TEXTURED")) {
            assertTrue(extractor.contains(fragment), fragment);
        }
        for (String fragment : List.of("private final GuiGraphics graphics;", "public final GuiGraphics nativeGraphics()",
                "wrap(GuiGraphics graphics)", "graphics.drawString(font, text, x, y, color)",
                "graphics.renderItem(stack, x, y)", "graphics.pose().pushMatrix()",
                "GuideNativeCursor.requestResize(graphics)")) {
            assertTrue(callback.contains(fragment), fragment);
        }
        for (String base : List.of(extractor, callback, immediate)) {
            assertTrue(base.contains("return new GuideGraphics(new GuideNativeGraphics(graphics));"));
            assertTrue(base.contains("this.graphics = Objects.requireNonNull(binding, \"binding\").graphics;"));
            for (String forbidden : List.of("Object graphics", "Class.forName", "java.lang.reflect", "minecraftTarget")) {
                assertFalse(base.contains(forbidden), forbidden);
            }
        }
        assertFalse(Files.exists(root.resolve("common/src/targets/1.21.8/java/" + GUI + "GuideNativeGraphics.java")),
                "The cursor-only difference must not copy the whole binding");
        assertTrue(read(root, "common/src/targets/1.21.11/java/" + GUI + "GuideNativeCursor.java")
                .contains("graphics.requestCursor(CursorTypes.RESIZE_ALL)"));
        assertTrue(read(root, "common/src/targets/1.21.8/java/" + GUI + "GuideNativeCursor.java")
                .contains("GuideLegacyCursor.requestResize()"));
        assertTrue(read(root, "common/src/main/java/dev/openallay/integration/jei/JeiNativeRecipeViewProvider.java")
                .contains("layout.drawRecipe(context.graphics(), context.mouseX(), context.mouseY())"));
        assertTrue(read(root, "common/src/main/java/dev/openallay/integration/jei/JeiNativeRecipeViewProvider.java")
                .contains("layout.drawOverlays(context.graphics(), context.mouseX(), context.mouseY())"));
        assertFalse(read(root, "common/src/main/java/dev/openallay/integration/jei/JeiNativeRecipeViewProvider.java")
                .contains("context.graphics().nativeGraphics()"), "Optional native ABI stays in its typed layout adapter");
    }

    @Test void immediatePaintAndWidgetTooltipCustodyStayInTheirSingleAlgorithm() throws IOException {
        Path root = root();
        String base = read(root, BASES.get(2));
        for (String fragment : List.of("ThreadLocal<PaintScope> CURRENT_PAINT", "previous.graphics == graphics",
                "paint.run();\n            if (scope.tooltip != null)", "graphics.flush();\n                scope.tooltip.run();\n                graphics.flush();",
                "finally {\n            scope.tooltip = null;", "if (previous == null) CURRENT_PAINT.remove();",
                "scope.tooltip == null || replaceExisting", "graphics.pose().pushPose()",
                "GuideImmediateGraphicsPrimitives.itemTooltip", "GuideImmediateGraphicsPrimitives.blit")) {
            assertTrue(base.contains(fragment), fragment);
        }
        assertTrue(base.contains("void clearTooltipForNextFrame()"));
        String screen = read(root, "common/src/targets/1.21.5/java/" + GUI + "GuideNativeScreen.java");
        for (String fragment : List.of("GuideGraphics.wrap(graphics)", "guide.paint(() -> {",
                "pending.positioner(), mouseX, mouseY, false", "paintGraphics.clearTooltipForNextFrame()",
                "clearGuideTooltipForNextRenderPass();")) assertTrue(screen.contains(fragment), fragment);
        String callbacks = read(root, "common/src/targets/1.21.5/java/" + GUI + "GuideNativeScreenCallbacks.java");
        assertTrue(callbacks.contains("clearTooltipForNextRenderPass()"));
        assertTrue(screen.contains("clearNativeTooltipForNextRenderPass()"));
        for (String family : List.of("1.21.5", "1.21.1")) {
            String helper = read(root, "common/src/targets/" + family + "/java/" + GUI + "GuideImmediateGraphicsPrimitives.java");
            assertTrue(helper.contains("static void itemTooltip(GuiGraphics graphics"));
            assertEquals(2, occurrences(helper, "static void blit(GuiGraphics graphics"));
        }
        assertFalse(Files.exists(root.resolve("common/src/targets/1.21.1/java/" + GUI + "GuideNativeGraphics.java")),
                "The older primitive helper must not replace the immediate base");
    }

    @Test void nativeCallbacksUseTheTypedFactoryNotPerFamilyFacadeConstructors() throws IOException {
        Path root = root();
        for (String module : List.of("common", "fabric", "neoforge")) {
            try (var sources = Files.walk(root.resolve(module).resolve("src"))) {
                for (Path path : sources.filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> !p.toString().contains("/test/"))
                        .filter(p -> !p.getFileName().toString().equals("GuideNativeGraphics.java")).toList()) {
                    assertFalse(Pattern.compile("new\\s+GuideGraphics\\s*\\(").matcher(Files.readString(path)).find(), path.toString());
                }
            }
        }
    }

    @Test void extractorCallbacksKeepNativeWidgetsAndViewersInsideViewportPaint() throws IOException {
        Path root = root();
        String graphics = read(root, BASES.get(0));
        String paint = graphics.substring(graphics.indexOf("protected final void nativePaint("),
                graphics.indexOf("public static GuideGraphics wrap("));
        int identity = paint.indexOf("graphics.pose().identity();");
        int scissor = paint.indexOf("graphics.enableScissor(0, 0, width, height);");
        int restorePose = paint.indexOf("graphics.pose().popMatrix();");
        int lifetime = paint.indexOf("static final class ViewportPaint<T>");
        assertTrue(identity >= 0 && identity < scissor && scissor < restorePose && restorePose < lifetime);
        assertTrue(paint.contains("graphics::disableScissor, paint"));
        assertTrue(graphics.contains("ViewportPaint<GuiGraphicsExtractor>"));
        for (String callback : List.of("GuideNativeScreen", "GuideNativeButton", "GuideNativeWidget")) {
            String source = read(root, "common/src/main/java/" + GUI + callback + ".java");
            assertTrue(source.contains("GuideGraphics guide = GuideGraphics.wrap(graphics);"), callback);
            assertTrue(source.contains("guide.paint(() -> paintGuide"), callback);
        }
        String screen = read(root, "common/src/main/java/" + GUI + "GuideNativeScreen.java");
        assertEquals(2, occurrences(screen, "guide.paint(() -> paintGuide"), "Both screen and background extraction");
        assertTrue(screen.contains("super.extractRenderState(graphics.nativeGraphics()"));
        assertTrue(read(root, "common/src/main/java/" + GUI + "hud/GuideNativeToastBinding.java")
                .contains("guide.paint(() -> paintGuideToast(guide"));
        for (String oldBinding : BASES.subList(1, BASES.size())) {
            assertFalse(read(root, oldBinding).contains("ViewportPaint"), oldBinding);
        }
    }

    @Test void extractorTestsFollowActualSelectedGraphicsSourceOwnership() throws IOException {
        Path root = root();
        String build = read(root, "common/build.gradle");
        assertTrue(build.indexOf("apply from: rootProject.file('gradle/minecraft-source-family.gradle')")
                < build.indexOf("sourceSets.main.java.files.contains(file("));
        assertTrue(build.contains("sourceSets.main.java.files.contains(file('src/main/java/" + GUI + "GuideNativeGraphics.java'))"));
        assertTrue(build.contains("sourceSets.test.java.srcDir('src/nativeTest/extractor/java')"));
        String selector = read(root, "gradle/minecraft-targets.gradle");
        var families = nativeFamilyMap(selector, "nativeFamilies");
        var parents = nativeFamilyMap(selector, "nativeFamilyParents");
        for (String target : List.of("26.1", "26.1.1", "26.1.2", "26.2", "26.3", "1.21.11", "1.21.8", "1.21.5", "1.21.1", "1.20.1")) {
            var chain = new java.util.ArrayList<String>();
            String family = families.get(target);
            assertNotNull(family, target);
            chain.add(family);
            while (parents.containsKey(chain.get(0))) chain.add(0, parents.get(chain.get(0)));
            if (!chain.contains(target)) chain.add(target);
            Path selected = root.resolve(BASES.get(0));
            for (String candidate : chain) {
                Path override = root.resolve("common/src/targets/" + candidate + "/java/" + GUI + "GuideNativeGraphics.java");
                if (Files.isRegularFile(override)) selected = override;
            }
            boolean extractor = selected.equals(root.resolve(BASES.get(0)));
            assertEquals(java.util.Set.of("26.1", "26.1.1", "26.1.2", "26.2", "26.3").contains(target), extractor, target + " -> " + selected);
            assertEquals(extractor, Files.readString(selected).contains("GuiGraphicsExtractor"), target);
        }
        assertTrue(Files.isRegularFile(root.resolve("common/src/nativeTest/extractor/java/" + GUI + "GuideViewportPaintTest.java")));
    }

    private static java.util.Map<String, String> nativeFamilyMap(String selector, String name) {
        var declaration = Pattern.compile("def " + name + " = \\[([^\\n]+)\\]").matcher(selector);
        assertTrue(declaration.find(), name);
        var entries = Pattern.compile("'([^']+)': '([^']+)'").matcher(declaration.group(1));
        var result = new java.util.HashMap<String, String>();
        while (entries.find()) result.put(entries.group(1), entries.group(2));
        return result;
    }

    private static String normalizedTypeSignature(String signature) {
        return signature.replace("net.minecraft.client.gui.Font", "Font")
                .replace("net.minecraft.network.chat.Component", "Component")
                .replace("net.minecraft.world.item.ItemStack", "ItemStack")
                .replace("dev.openallay.client.gui.GuideTextLine", "GuideTextLine")
                .replace("java.util.List", "List")
                .replace("net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner", "ClientTooltipPositioner")
                .replaceAll("\\s+", " ");
    }
    private static List<String> primitiveSignatures(String source) {
        var matcher = Pattern.compile("protected final (void|int) (native\\w+)\\(([^)]*)\\)").matcher(source);
        var signatures = new java.util.ArrayList<String>();
        while (matcher.find()) signatures.add(normalizedTypeSignature(matcher.group(1) + " " + matcher.group(2) + "(" + matcher.group(3) + ")"));
        return signatures.stream().sorted().toList();
    }

    private static List<String> facadeRouteSignatures(String source) {
        // Match direct routes only. New composite feature methods need no binding/test-list edits.
        var matcher = Pattern.compile("public (void|int) \\w+\\(([^)]*)\\)\\s*\\{\\s*(?:return\\s+)?(native\\w+)\\(").matcher(source);
        var signatures = new java.util.ArrayList<String>();
        while (matcher.find()) {
            signatures.add(normalizedTypeSignature(matcher.group(1) + " " + matcher.group(3) + "(" + matcher.group(2) + ")"));
        }
        String cursor = source.substring(source.indexOf("public boolean requestResizeCursor()"),
                source.indexOf("public void enableScissor("));
        assertTrue(cursor.contains("if (!nativeResizeCursorAvailable()) return false"));
        assertEquals(1, occurrences(cursor, "nativeRequestResizeCursor()"));
        assertTrue(cursor.indexOf("nativeRequestResizeCursor()") > cursor.indexOf("nativeResizeCursorAvailable()"));
        signatures.add("void nativeRequestResizeCursor()");
        return signatures.stream().sorted().toList();
    }

    private static int occurrences(String source, String fragment) {
        return (source.length() - source.replace(fragment, "").length()) / fragment.length();
    }
    private static String read(Path root, String path) throws IOException { return Files.readString(root.resolve(path)); }
    private static Path root() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("common/src/main/java"))
                    && Files.isDirectory(current.resolve("engine-core/src/main/java"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("Repository root is unavailable");
    }
}
