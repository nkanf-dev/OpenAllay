package dev.openallay.tool.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

final class NaturalModelViewTest {
    @Test
    void retainsEveryScalarFindingIncludingMixedTypesAndNestedGroups() {
        var canonical = JsonParser.parseString("""
                {"ids":["a","b","c"],"counts":[1,2,3],"mixed":[null,true,"3",3],
                 "groups":{"first":[1,2],"second":["x","y"]}}
                """).getAsJsonObject();
        String exact = canonical.toString();
        var view = NaturalModelView.artifact(canonical);
        assertTrue(view.complete());
        assertEquals(0, view.omittedRows());
        assertEquals(canonical, view.value());
        assertSame(canonical.get("ids"), view.value().getAsJsonObject().get("ids"));
        assertEquals(exact, canonical.toString());
    }

    @Test
    void samplesContainerRowsAndRetainsScalarFindingsInsideTheRealSample() {
        var canonical = JsonParser.parseString("""
                {"rows":[{"id":"a","counts":[1,2]},{"id":"b","counts":[3,4]}],
                 "answerIds":["a","b"],"total":2}
                """).getAsJsonObject();
        String exact = canonical.toString();
        var view = NaturalModelView.artifact(canonical);
        assertFalse(view.complete());
        assertEquals(1, view.omittedRows());
        assertEquals(JsonParser.parseString("[\"a\",\"b\"]"),
                view.value().getAsJsonObject().get("answerIds"));
        assertEquals(JsonParser.parseString("[1,2]"), view.value().getAsJsonObject()
                .getAsJsonArray("rows").get(0).getAsJsonObject().get("counts"));
        assertEquals(exact, canonical.toString());
    }

    @Test
    void leavesALargePrimitiveVectorForTheActualCallerBudgetRatherThanAnElementCap() {
        JsonArray canonical = new JsonArray();
        for (int index = 0; index < 50_000; index++) canonical.add(index);
        var chosen = NaturalModelView.artifact(canonical);
        assertTrue(chosen.complete());
        assertSame(canonical, chosen.value());
        assertEquals(0, chosen.omittedRows());
        var bounded = JsonResultProjection.project(chosen.value(), "r_vector", "", 1_024);
        assertFalse(bounded.complete());
        assertTrue(JsonResultProjection.encodedBytes(bounded.modelText()) <= 1_024);
        assertTrue(bounded.modelText().contains("cardinality: 50000"));
        assertTrue(bounded.omittedRows() > 0);
        assertEquals(50_000, canonical.size());
        assertEquals(49_999, canonical.get(49_999).getAsInt());
    }

    @Test
    void emptyAndSingleContainerArraysAreCompleteWhenTheirNestedValuesAreComplete() {
        for (String value : new String[] {"[]", "[{}]", "[{\"counts\":[1,2]}]"}) {
            var canonical = JsonParser.parseString(value);
            var chosen = NaturalModelView.artifact(canonical);
            assertTrue(chosen.complete());
            assertEquals(0, chosen.omittedRows());
            assertEquals(canonical, chosen.value());
        }
    }
}
