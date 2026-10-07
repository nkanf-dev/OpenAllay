package dev.latvian.mods.rhino;

import dev.latvian.mods.rhino.type.*;
import dev.latvian.mods.rhino.util.ArrayValueProvider;
import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class InternalCarrierValuesTest {
    private record ObjectOracle(List<JSOptionalParam> fields) {}
    private record FunctionOracle(List<JSOptionalParam> params, TypeInfo returnType) {}
    private record UnionOracle(List<TypeInfo> types) {}
    private record StringOracle(String constant) {}
    private record SourceOracle(Object object) {}
    private record NativeOracle(NativeArray array) {}
    private record JavaArrayOracle(Object array, int length) {}
    private record PlainOracle(Object[] array) {}
    private record ListOracle(List<?> list, Object errorSource) {}
    private record IteratorOracle(int length, Iterator<?> iterator, Object errorSource) {}
    private record ComparatorOracle(Context cx) {}
    private record ElementOracle(Comparator<Object> child) {}

    @Test void typeValuesKeepCustomFormattingAliasesAndValueHashes() {
        JSOptionalParam field = new JSOptionalParam("a", TypeInfo.STRING);
        List<JSOptionalParam> params = new ArrayList<>(List.of(field));
        JSObjectTypeInfo object = new JSObjectTypeInfo(params);
        JSFunctionTypeInfo function = new JSFunctionTypeInfo(params, TypeInfo.INT);
        assertSame(params, object.fields()); assertSame(params, function.params()); assertSame(TypeInfo.INT, function.returnType());
        assertEquals(new ObjectOracle(params).hashCode(), object.hashCode());
        assertEquals(new FunctionOracle(params, TypeInfo.INT).hashCode(), function.hashCode());
        assertEquals(object, new JSObjectTypeInfo(new ArrayList<>(params)));
        assertEquals("{a: java.lang.String}", object.toString());
        assertEquals("(a: java.lang.String) => java.lang.Integer", function.toString());
        Set<Class<?>> classes = new HashSet<>(); function.collectContainedComponentClasses(classes);
        assertTrue(classes.contains(String.class)); assertTrue(classes.contains(Integer.class));
        params.clear(); assertEquals("{}", object.toString());
        JSStringConstantTypeInfo string = new JSStringConstantTypeInfo("a\n\"b");
        assertEquals(new StringOracle(string.constant()).hashCode(), string.hashCode());
        assertEquals(ScriptRuntime.escapeAndWrapString(string.constant()), string.toString());
        assertEquals(string, new JSStringConstantTypeInfo(string.constant()));
        assertEquals("\"\"", JSStringConstantTypeInfo.EMPTY.toString());
    }

    @Test void objectFactoryAndUnionKeepImmutableNullAndCopyRules() {
        JSOptionalParam field = new JSOptionalParam("a", TypeInfo.STRING);
        JSOptionalParam[] input = {field}; JSObjectTypeInfo object = JSObjectTypeInfo.of(input);
        input[0] = null; assertSame(field, object.fields().get(0));
        assertThrows(NullPointerException.class, () -> object.fields().contains(null));
        assertThrows(NullPointerException.class, () -> JSObjectTypeInfo.of((JSOptionalParam) null));
        assertThrows(UnsupportedOperationException.class, () -> object.fields().add(field));
        List<TypeInfo> types = new ArrayList<>(List.of(TypeInfo.STRING)); JSOrTypeInfo value = new JSOrTypeInfo(types);
        assertSame(types, value.types()); assertEquals(new UnionOracle(types).hashCode(), value.hashCode());
        JSOrTypeInfo combined = (JSOrTypeInfo) value.or(new JSOrTypeInfo(List.of(TypeInfo.INT, TypeInfo.STRING)));
        assertEquals(List.of(TypeInfo.STRING, TypeInfo.INT, TypeInfo.STRING), combined.types());
        types.clear(); assertEquals(3, combined.types().size());
        assertThrows(NullPointerException.class, () -> combined.types().contains(null));
        assertThrows(UnsupportedOperationException.class, () -> combined.types().add(TypeInfo.INT));
        assertEquals("java.lang.String | java.lang.Integer | java.lang.String", combined.toString());
    }

    @Test void javaProvidersRetainShallowArrayIdentityAndOneShotIteration() {
        Object[] array = {"x", "y"};
        ArrayValueProvider.FromObject object = new ArrayValueProvider.FromObject(array);
        assertSame(array, object.object()); assertSame(array, object.getArrayValue(null, 0));
        assertEquals(new SourceOracle(array).hashCode(), object.hashCode());
        assertEquals(new SourceOracle(array).toString().replace("SourceOracle[", "FromObject["), object.toString());
        assertEquals(object, new ArrayValueProvider.FromObject(array));
        assertNotEquals(object, new ArrayValueProvider.FromObject(array.clone()));
        assertSame(ArrayValueProvider.FromObject.FROM_NULL, ArrayValueProvider.FromObject.FROM_NULL);
        assertNull(ArrayValueProvider.FromObject.FROM_NULL.object());
        ArrayValueProvider.FromJavaArray java = new ArrayValueProvider.FromJavaArray(array, 2);
        ArrayValueProvider.FromPlainJavaArray plain = new ArrayValueProvider.FromPlainJavaArray(array);
        assertSame(array, java.array()); assertEquals(2, java.length()); assertEquals("y", java.getArrayValue(null, 1));
        assertSame(array, plain.array()); assertEquals(2, plain.getLength(null));
        assertEquals(new JavaArrayOracle(array, 2).hashCode(), java.hashCode());
        assertEquals(new PlainOracle(array).hashCode(), plain.hashCode());
        assertEquals(new PlainOracle(array).toString().replace("PlainOracle[", "FromPlainJavaArray["), plain.toString());
        List<String> list = new ArrayList<>(List.of("x")); Object error = new Object();
        ArrayValueProvider.FromJavaList listValue = new ArrayValueProvider.FromJavaList(list, error);
        assertSame(list, listValue.list()); assertSame(error, listValue.errorSource());
        assertEquals(new ListOracle(list, error).hashCode(), listValue.hashCode());
        assertEquals(new ListOracle(list, error).toString().replace("ListOracle[", "FromJavaList["), listValue.toString());
        list.add("y"); assertEquals(2, listValue.getLength(null));
        Iterator<String> iterator = list.iterator();
        ArrayValueProvider.FromIterator iter = new ArrayValueProvider.FromIterator(2, iterator, error);
        assertSame(iterator, iter.iterator()); assertSame(error, iter.errorSource()); assertEquals(2, iter.length());
        assertEquals(new IteratorOracle(2, iterator, error).hashCode(), iter.hashCode());
        assertEquals(new IteratorOracle(2, iterator, error).toString().replace("IteratorOracle[", "FromIterator["), iter.toString());
        assertEquals("x", iter.getArrayValue(null, 0)); assertEquals("y", iter.getArrayValue(null, 1));
        assertThrows(NoSuchElementException.class, () -> iter.getArrayValue(null, 2));
        assertEquals("FromJavaArray[array=" + array + ", length=2]", java.toString());
    }

    @Test void providerFactoriesUseActualRhinoConversionAndNativeArray() {
        Context cx = new ContextFactory().enter();
        NativeArray nativeArray = new NativeArray(cx, new Object[]{"a", "b"});
        ArrayValueProvider.FromNativeArray value = new ArrayValueProvider.FromNativeArray(nativeArray);
        assertSame(nativeArray, value.array()); assertEquals(2, value.getLength(cx));
        assertEquals("b", value.getArrayValue(cx, 1));
        assertEquals(new NativeOracle(nativeArray).hashCode(), value.hashCode());
        assertEquals(new NativeOracle(nativeArray).toString().replace("NativeOracle[", "FromNativeArray["), value.toString());
        assertSame(ArrayValueProvider.EMPTY, ArrayValueProvider.fromJavaList(List.of(), null));
        assertSame(ArrayValueProvider.EMPTY, ArrayValueProvider.fromNativeArray(new NativeArray(cx, 0)));
        ArrayValueProvider provider = ArrayValueProvider.fromJavaList(List.of("a", "b"), "error");
        assertArrayEquals(new String[]{"a", "b"}, (String[]) provider.createArray(cx, TypeInfo.STRING));
        assertEquals(List.of("a", "b"), provider.createList(cx, TypeInfo.STRING));
        ArrayValueProvider singleton = new ArrayValueProvider.FromObject("a");
        Collection<?> frozen = (Collection<?>) singleton.createList(cx, TypeInfo.STRING);
        assertThrows(NullPointerException.class, () -> frozen.contains(null));
        assertThrows(NullPointerException.class, () -> new ArrayValueProvider.FromObject(null).createList(cx, TypeInfo.STRING));
        Collection<?> emptySet = (Collection<?>) ArrayValueProvider.EMPTY.createSet(cx, TypeInfo.STRING);
        assertThrows(NullPointerException.class, () -> emptySet.contains(null));
    }

    @Test void comparatorsKeepEcmaSentinelOrderAndChildCallbacks() {
        Context cx = new ContextFactory().enter();
        ArrayLikeAbstractOperations.StringLikeComparator lexical = new ArrayLikeAbstractOperations.StringLikeComparator(cx);
        assertSame(cx, lexical.cx()); assertTrue(lexical.compare(10, 2) < 0);
        assertEquals(new ComparatorOracle(cx).hashCode(), lexical.hashCode());
        assertEquals(new ComparatorOracle(cx).toString().replace("ComparatorOracle[", "StringLikeComparator["), lexical.toString());
        Comparator<Object> child = (a, b) -> String.valueOf(a).compareTo(String.valueOf(b));
        ArrayLikeAbstractOperations.ElementComparator value = new ArrayLikeAbstractOperations.ElementComparator(child);
        assertSame(child, value.child()); assertEquals(new ElementOracle(child).hashCode(), value.hashCode());
        assertEquals(value, new ArrayLikeAbstractOperations.ElementComparator(child));
        assertTrue(value.compare("x", Undefined.INSTANCE) < 0);
        assertTrue(value.compare(Undefined.INSTANCE, Scriptable.NOT_FOUND) < 0);
        assertTrue(value.compare(Scriptable.NOT_FOUND, Undefined.INSTANCE) > 0);
        assertEquals(0, value.compare(Undefined.INSTANCE, Undefined.INSTANCE));
        assertEquals(0, value.compare(Scriptable.NOT_FOUND, Scriptable.NOT_FOUND));
        assertTrue(value.compare("a", "b") < 0);
        assertEquals("ElementComparator[child=" + child + "]", value.toString());
    }

    @Test void internalCarriersKeepInterfacesAndDoNotMasqueradeAsGuestRecords() {
        for (Class<?> type : List.of(JSObjectTypeInfo.class, JSFunctionTypeInfo.class, JSOrTypeInfo.class,
                JSStringConstantTypeInfo.class, ArrayValueProvider.FromObject.class,
                ArrayValueProvider.FromNativeArray.class, ArrayValueProvider.FromJavaArray.class,
                ArrayValueProvider.FromPlainJavaArray.class, ArrayValueProvider.FromJavaList.class,
                ArrayValueProvider.FromIterator.class, ArrayLikeAbstractOperations.StringLikeComparator.class,
                ArrayLikeAbstractOperations.ElementComparator.class)) {
            assertTrue(Modifier.isFinal(type.getModifiers())); assertFalse(type.isRecord());
            assertSame(Object.class, type.getSuperclass());
        }
        assertTrue(TypeInfo.class.isAssignableFrom(JSObjectTypeInfo.class));
        assertTrue(ArrayValueProvider.class.isAssignableFrom(ArrayValueProvider.FromObject.class));
        assertTrue(Comparator.class.isAssignableFrom(ArrayLikeAbstractOperations.ElementComparator.class));
    }
}
