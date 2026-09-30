package dev.openallay.requirement;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RequirementCodecTest {
    @Test
    void absentAndEmptyDeclarationsStayEmpty() {
        assertEquals(RequirementSet.EMPTY, RequirementCodec.decode(null));
        assertEquals(RequirementSet.EMPTY, RequirementCodec.decode(JsonParser.parseString("{}")));
        assertEquals(RequirementSet.EMPTY, RequirementCodec.fromMetadata(Map.of()));
        assertEquals(RequirementSet.EMPTY, RequirementCodec.fromMetadata(Map.of(
                RequirementCodec.CAPABILITIES_KEY, " ", RequirementCodec.SKILLS_KEY, "")));
        assertEquals("{}", RequirementCodec.encode(RequirementSet.EMPTY).toString());
    }

    @Test
    void roundTripsExactUnknownIdsWithoutRequiringRegisteredCapabilities() {
        RequirementSet expected = new RequirementSet(Set.of("future:unknown/path", "9future"),
                Set.of("sample:dependency"), Set.of("guide"));
        assertEquals(expected, RequirementCodec.decode(RequirementCodec.encode(expected)));
        assertEquals(expected, RequirementCodec.fromMetadata(Map.of(
                RequirementCodec.CAPABILITIES_KEY, "future:unknown/path\t9future",
                RequirementCodec.EXTENSIONS_KEY, "sample:dependency",
                RequirementCodec.SKILLS_KEY, "guide",
                "other/field", "not interpreted")));
    }

    @Test
    void rejectsUnknownFieldsNullsNonStringsDuplicatesAndInvalidExactIds() {
        for (String invalid : new String[] {
                "null", "[]", "true", "{\"unknown\":[]}", "{\"skills\":null}",
                "{\"skills\":\"guide\"}", "{\"skills\":[1]}", "{\"skills\":[\"\"]}",
                "{\"skills\":[\"guide\",\"guide\"]}", "{\"skills\":[\"Guide\"]}",
                "{\"extensions\":[\"not-namespaced\"]}",
                "{\"capabilities\":[\" spaced\"]}", "{\"capabilities\":[\"a,b\"]}",
                "{\"capabilities\":[\"future:unknown\\n\"]}"
        }) {
            assertThrows(IllegalArgumentException.class,
                    () -> RequirementCodec.decode(JsonParser.parseString(invalid)), invalid);
        }
        assertThrows(IllegalArgumentException.class, () -> RequirementCodec.fromMetadata(
                Map.of(RequirementCodec.CAPABILITIES_KEY, "a,b")));
        assertThrows(IllegalArgumentException.class, () -> RequirementCodec.fromMetadata(
                Map.of(RequirementCodec.SKILLS_KEY, "a".repeat(65))));
    }

    @Test
    void copiesAllDeclaredIdsAndExposesImmutableSets() {
        Set<String> input = new HashSet<>(Set.of("sample:one"));
        RequirementSet requirements = new RequirementSet(input, Set.of(), Set.of());
        input.clear();
        assertEquals(Set.of("sample:one"), requirements.capabilities());
        assertThrows(UnsupportedOperationException.class,
                () -> requirements.capabilities().add("sample:two"));
    }
}
