package dev.tomewisp.guide.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

final class GuideToolPresenterTest {
    @Test
    void presentsCraftabilityAsAllocationAndMissingMaterials() {
        var normalized = JsonParser.parseString("""
                {"status":"success","value":{"result":{"craftable":false,"conclusive":true,
                "requestedCrafts":1,"maximumCrafts":0,
                "allocations":[{"requirementKey":"iron","itemId":"minecraft:iron_ingot","count":4}],
                "missing":[{"requirementKey":"iron","missing":5}]}}}
                """).getAsJsonObject();

        var lines = GuideToolPresenter.lines("tomewisp:calculate_craftability", normalized);

        assertEquals("可合成: false；结论完备: true", lines.getFirst());
        assertTrue(lines.stream().anyMatch(value -> value.contains("iron_ingot × 4")));
        assertTrue(lines.stream().anyMatch(value -> value.contains("缺少 iron × 5")));
    }

    @Test
    void failurePresentationNeverPretendsToHaveAResult() {
        var normalized = JsonParser.parseString(
                "{\"status\":\"failure\",\"code\":\"stale_reference\",\"message\":\"reload\"}")
                .getAsJsonObject();
        var lines = GuideToolPresenter.lines("tomewisp:get_recipe", normalized);
        assertEquals("失败: stale_reference", lines.getFirst());
        assertEquals("reload", lines.get(1));
    }

    @Test
    void recipePresentationExposesProviderGenerationAndDiagnostics() {
        var normalized = JsonParser.parseString("""
                {"status":"success","value":{"recipes":[],"catalog":{
                  "completeness":"PARTIAL","recipeCount":0,"semanticGroupCount":0,
                  "providers":[{"sourceId":"viewer:rei","generation":"%s",
                    "state":"UNAVAILABLE","completeness":"UNKNOWN","recipeCount":0,
                    "diagnostics":[{"sourceId":"viewer:rei","code":"mod_not_loaded","message":"REI is not installed"}]}],
                  "conflicts":[]},"evidence":[]}}
                """.formatted("0".repeat(64))).getAsJsonObject();

        var lines = GuideToolPresenter.lines("tomewisp:search_recipes", normalized);

        assertTrue(lines.stream().anyMatch(value -> value.contains("viewer:rei · UNAVAILABLE/UNKNOWN")));
        assertTrue(lines.stream().anyMatch(value -> value.contains("mod_not_loaded")));
        assertTrue(lines.stream().anyMatch(value -> value.contains("generation=" + "0".repeat(64))));
    }
}
