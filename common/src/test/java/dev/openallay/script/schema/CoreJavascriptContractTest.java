package dev.openallay.script.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.script.data.MinecraftAgentHostGraph;
import org.junit.jupiter.api.Test;

final class CoreJavascriptContractTest {
    @Test
    void rendersEveryDeclaredRootAndStableNestedPathWithoutResolvingValues() {
        HostSchemaCatalog catalog = MinecraftAgentHostGraph.declaredOnlyCatalog();

        String rendered = CoreJavascriptContract.render(catalog);

        for (HostSchemaCatalog.RootSummary root : catalog.list()) {
            assertTrue(rendered.contains("mc." + root.name()), root.name());
        }
        assertTrue(rendered.contains("mc.game.mods.installed"));
        assertTrue(rendered.contains("mc.recipeCatalog.providers"));
        assertTrue(rendered.contains("mc.registryEntries"));
        assertTrue(rendered.contains("schema.list()"));
        assertTrue(rendered.contains("schema.describe(path)"));
        assertTrue(rendered.contains("workspace.open(handle)"));
        assertTrue(rendered.contains("require(id)"));
        assertTrue(rendered.contains("world.inspect("));
        assertTrue(rendered.contains("world.entities("));
        assertTrue(rendered.contains("world.entity(observationId)"));
        assertTrue(rendered.contains("roots [\"world\"]"));
        assertTrue(rendered.contains("never mc.world"));
        assertTrue(rendered.contains("returns blocks, coverage, and evidence"));
        assertTrue(rendered.contains("request-scoped observationId values, coverage, and evidence"));
        assertTrue(rendered.contains("detached detail for one entity observed in the same request"));
        assertFalse(rendered.contains("var origin = mc.player.position"));
        assertFalse(rendered.contains("analyze-game-data"));
    }

    @Test
    void marksRequestScopedRootsWithoutClaimingTheyAreCaptured() {
        String rendered = CoreJavascriptContract.render(
                MinecraftAgentHostGraph.declaredOnlyCatalog());

        assertTrue(rendered.contains("availability=request-scoped"));
        assertFalse(rendered.contains("availability=available"));
    }
}
