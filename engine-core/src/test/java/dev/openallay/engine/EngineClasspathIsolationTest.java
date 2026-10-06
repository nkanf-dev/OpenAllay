package dev.openallay.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import dev.openallay.agent.tool.ModelToolResultProjection;
import dev.openallay.model.metadata.BuiltinModelCatalog;
import dev.openallay.script.JavascriptModuleCatalog;
import dev.openallay.skill.BundledSkillLoader;
import dev.openallay.skill.LoadSkillTool;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;

final class EngineClasspathIsolationTest {
    @Test
    void productionFeaturesLoadWithoutNativeGameClasses() throws Exception {
        ClassLoader loader = BuiltinModelCatalog.class.getClassLoader();
        for (String name : new String[] {
                "net.minecraft.client.Minecraft", "net.fabricmc.loader.api.FabricLoader",
                "net.neoforged.fml.ModList", "org.lwjgl.glfw.GLFW" }) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(name, false, loader));
        }
        assertNotNull(BuiltinModelCatalog.bundled().catalog());
        assertEquals(7, new BundledSkillLoader().load().size());
        assertFalse(JavascriptModuleCatalog.bundled().source("openallay:crafting").isBlank());
        assertNotNull(dev.latvian.mods.rhino.Context.class);
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            assertTrue(connection.isValid(1));
        }
    }

    @Test
    void currentReflectedSkillOutputContractKeepsExactInstructions() {
        JsonObject normalized = dev.openallay.json.JsonTrees.parse("""
                {"status":"success","value":{},"modelText":"retain these instructions exactly"}
                """).getAsJsonObject();
        normalized.addProperty("outputType", LoadSkillTool.Output.class.getName());
        assertEquals("retain these instructions exactly",
                ModelToolResultProjection.project("openallay:load_skill", normalized, 1).getAsString());
    }
}
