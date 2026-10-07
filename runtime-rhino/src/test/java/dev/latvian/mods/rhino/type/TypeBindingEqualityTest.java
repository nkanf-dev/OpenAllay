package dev.latvian.mods.rhino.type;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class TypeBindingEqualityTest {
    @Test void arrayAndClassTypesRetainIdentityShortCircuitAndWrongTypeGuards() {
        TypeInfo array = TypeInfo.STRING.asArray();
        assertEquals(array, array); assertEquals(array, new ArrayTypeInfo(TypeInfo.STRING));
        assertNotEquals(array, new ArrayTypeInfo(TypeInfo.INT)); assertFalse(array.equals(null)); assertFalse(array.equals("x"));
        assertEquals(TypeInfo.STRING.hashCode(), array.hashCode());
        assertEquals(TypeInfo.STRING, new BasicClassTypeInfo(String.class));
        assertNotEquals(TypeInfo.STRING, new BasicClassTypeInfo(Integer.class));
        assertFalse(TypeInfo.STRING.equals(null)); assertFalse(TypeInfo.STRING.equals("x"));
    }
    @Test void parameterizedTypesRetainRawParameterLengthAndDeepValueChecks() {
        ParameterizedTypeInfo value = new ParameterizedTypeInfo(TypeInfo.RAW_LIST, new TypeInfo[]{TypeInfo.STRING});
        assertEquals(value, value); assertEquals(value, new ParameterizedTypeInfo(TypeInfo.RAW_LIST, new TypeInfo[]{TypeInfo.STRING}));
        assertNotEquals(value, new ParameterizedTypeInfo(TypeInfo.RAW_LIST, new TypeInfo[]{TypeInfo.INT}));
        assertNotEquals(value, new ParameterizedTypeInfo(TypeInfo.RAW_LIST, new TypeInfo[0]));
        assertNotEquals(value, new ParameterizedTypeInfo(TypeInfo.RAW_SET, new TypeInfo[]{TypeInfo.STRING}));
        assertFalse(value.equals(null)); assertFalse(value.equals("x"));
        assertEquals(new ParameterizedTypeInfo(TypeInfo.RAW_LIST, new TypeInfo[]{TypeInfo.STRING}).hashCode(), value.hashCode());
    }
}
