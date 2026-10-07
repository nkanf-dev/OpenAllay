package dev.latvian.mods.rhino.type;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class GenericBindingTypeTest {
    static class Fields<T> { List<String> list; T[] array; List<? extends Number> upper; List<? super Integer> lower; }
    static class Parent<T> {}
    static class Child extends Parent<String> {}
    @Test void actualReflectiveTypeShapesKeepRawAndComponentDispatch() throws Exception {
        Type list = Fields.class.getDeclaredField("list").getGenericType();
        Type array = Fields.class.getDeclaredField("array").getGenericType();
        assertSame(List.class, TypeUtils.getRawType(list)); assertSame(Object[].class, TypeUtils.getRawType(array));
        assertSame(String.class, TypeUtils.getRawType(String.class));
        assertSame(String.class, TypeUtils.getComponentType(String[].class, null));
        assertSame(Fields.class.getTypeParameters()[0], TypeUtils.getComponentType(array, null));
        assertSame(Integer.class, TypeUtils.getComponentType(list, Integer.class));
        assertSame(String.class, TypeInfo.of(list).param(0).asClass());
        assertTrue(TypeInfo.of(array).isArray());
        Type upper = ((ParameterizedType) Fields.class.getDeclaredField("upper").getGenericType()).getActualTypeArguments()[0];
        Type lower = ((ParameterizedType) Fields.class.getDeclaredField("lower").getGenericType()).getActualTypeArguments()[0];
        assertSame(Number.class, TypeUtils.getRawType(upper)); assertSame(Object.class, TypeUtils.getRawType(lower));
        assertSame(Number.class, TypeInfo.of(upper).asClass()); assertSame(Integer.class, TypeInfo.of(lower).asClass());
        assertSame(Object.class, TypeUtils.getRawType(Fields.class.getTypeParameters()[0]));
    }
    @Test void superGenericMappingRetainsCapturedRawParentAndArguments() {
        Map<VariableTypeInfo,TypeInfo> mapping = TypeConsolidator.getMapping(Child.class);
        assertEquals(TypeInfo.STRING, mapping.get(TypeInfo.of(Parent.class.getTypeParameters()[0])));
    }
}
