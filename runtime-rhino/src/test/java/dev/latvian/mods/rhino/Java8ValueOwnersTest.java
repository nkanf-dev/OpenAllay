package dev.latvian.mods.rhino;

import dev.latvian.mods.rhino.type.*;
import dev.latvian.mods.rhino.util.WrappedReflectionMethod;
import dev.latvian.mods.rhino.util.wrap.*;
import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class Java8ValueOwnersTest {
    private record ParametersOracle(int count, List<Class<?>> types, List<TypeInfo> typeInfos,
            boolean firstArgContext, TypeInfo varArgType) {}
    private record MemberOracle(String name, TypeInfo type, Object value) {}
    private record WrappedOracle(CachedMethodInfo method) {}
    private record WrapperOracle<T>(Class<T> target, TypeWrapperValidator validator, TypeWrapperFactory<T> factory) {}
    private record StringOracle(String value) {}
    private record NumberOracle(Number number) {}
    private record OptionalOracle(String name, TypeInfo type, boolean optional) {}
    private record FixedOracle(List<JSOptionalParam> types) {}

    @Test void cachedParametersRetainRecordValueAndAliasContracts() {
        List<Class<?>> classes = new ArrayList<>(List.of(String.class));
        List<TypeInfo> infos = new ArrayList<>(List.of(TypeInfo.STRING));
        CachedParameters value = new CachedParameters(1, classes, infos, true, TypeInfo.STRING);
        ParametersOracle oracle = new ParametersOracle(1, classes, infos, true, TypeInfo.STRING);
        assertSame(classes, value.types()); assertSame(infos, value.typeInfos());
        assertEquals(1, value.count()); assertTrue(value.firstArgContext());
        assertSame(TypeInfo.STRING, value.varArgType()); assertTrue(value.isVarArg());
        assertTrue(value.typesMatch(new Class<?>[]{String.class}));
        assertFalse(value.typesMatch(new Class<?>[]{Object.class}));
        assertFalse(value.typesMatch(new Class<?>[0]));
        assertEquals(value, new CachedParameters(1, new ArrayList<>(classes), new ArrayList<>(infos), true, TypeInfo.STRING));
        assertEquals(oracle.hashCode(), value.hashCode());
        assertEquals(oracle.toString().replace("ParametersOracle[", "CachedParameters["), value.toString());
        assertNotSame(CachedParameters.EMPTY, CachedParameters.EMPTY_FIRST_CX);
        assertFalse(CachedParameters.EMPTY.firstArgContext()); assertTrue(CachedParameters.EMPTY_FIRST_CX.firstArgContext());
        assertFalse(CachedParameters.EMPTY.isVarArg());
        assertThrows(UnsupportedOperationException.class, () -> CachedParameters.EMPTY.types().add(String.class));
        assertThrows(NullPointerException.class, () -> CachedParameters.EMPTY.types().contains(null));
        classes.add(Object.class); assertEquals(2, value.types().size());
    }

    @Test void customMemberUsesReferenceArrayEqualityAndRecordHash() {
        Object[] payload = {"x"};
        CustomMember value = new CustomMember("name", TypeInfo.STRING, payload);
        assertSame(payload, value.value()); assertEquals("name", value.name()); assertSame(TypeInfo.STRING, value.type());
        assertEquals(value, new CustomMember("name", TypeInfo.STRING, payload));
        assertNotEquals(value, new CustomMember("name", TypeInfo.STRING, new Object[]{"x"}));
        MemberOracle oracle = new MemberOracle("name", TypeInfo.STRING, payload);
        assertEquals(oracle.hashCode(), value.hashCode());
        assertEquals(oracle.toString().replace("MemberOracle[", "CustomMember["), value.toString());
        assertEquals(new CustomMember(null, null, null), new CustomMember(null, null, null));
    }

    @Test void wrappedReflectionNullShapeAndFactoryRemainExact() {
        assertNull(WrappedReflectionMethod.of(null));
        WrappedReflectionMethod value = new WrappedReflectionMethod(null);
        assertNull(value.method()); assertNull(value.unwrap());
        assertEquals(value, new WrappedReflectionMethod(null));
        assertEquals(new WrappedOracle(null).hashCode(), value.hashCode());
        assertEquals("WrappedReflectionMethod[method=null]", value.toString());
    }

    public static final class MethodTarget {
        public static String echo(String value) { return value; }
        public String instance(String value) { return value; }
        public static String fail() { throw new IllegalStateException("target-failure"); }
    }

    @Test void wrappedReflectionRetainsActualCachedMethodDelegation() throws Throwable {
        CachedClassInfo owner = CachedClassStorage.GLOBAL_PUBLIC.get(MethodTarget.class);
        CachedMethodInfo method = new CachedMethodInfo(owner, MethodTarget.class.getMethod("echo", String.class));
        WrappedReflectionMethod value = new WrappedReflectionMethod(method);
        assertSame(method, value.method()); assertSame(method, value.unwrap()); assertTrue(value.isStatic());
        assertEquals(TypeInfo.STRING, value.getReturnType());
        Context cx = new ContextFactory().enter();
        assertEquals("actual", value.invoke(cx, null, null, new Object[]{"actual"}));
        WrappedReflectionMethod instance = new WrappedReflectionMethod(new CachedMethodInfo(owner,
                MethodTarget.class.getMethod("instance", String.class)));
        assertFalse(instance.isStatic());
        assertEquals("instance", instance.invoke(cx, null, new MethodTarget(), new Object[]{"instance"}));
        WrappedReflectionMethod failure = new WrappedReflectionMethod(new CachedMethodInfo(owner,
                MethodTarget.class.getMethod("fail")));
        assertEquals("target-failure", assertThrows(IllegalStateException.class,
                () -> failure.invoke(cx, null, null, new Object[0])).getMessage());
    }

    @Test void typeWrapperPreservesGenericAccessorsAndIdentityOfCallbacks() {
        TypeWrapperFactory<String> factory = (cx, from, target) -> String.valueOf(from);
        TypeWrapperValidator validator = TypeWrapperValidator.ALWAYS_VALID;
        TypeWrapper<String> value = new TypeWrapper<>(String.class, validator, factory);
        assertSame(String.class, value.target()); assertSame(validator, value.validator()); assertSame(factory, value.factory());
        assertEquals("x", value.factory().wrap(null, "x", TypeInfo.STRING));
        assertEquals(value, new TypeWrapper<>(String.class, validator, factory));
        WrapperOracle<String> oracle = new WrapperOracle<>(String.class, validator, factory);
        assertEquals(oracle.hashCode(), value.hashCode());
        assertEquals(oracle.toString().replace("WrapperOracle[", "TypeWrapper["), value.toString());
    }

    @Test void constantTypeInfoPreservesCustomTextAndEmptyClassSet() {
        JSBasicConstantTypeInfo basic = new JSBasicConstantTypeInfo("custom");
        assertEquals("custom", basic.value()); assertEquals("custom", basic.toString());
        assertEquals(new StringOracle("custom").hashCode(), basic.hashCode());
        assertEquals(basic, new JSBasicConstantTypeInfo("custom"));
        assertSame(TypeInfo.class, basic.asClass());
        assertTrue(basic.getContainedComponentClasses().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> basic.getContainedComponentClasses().add(String.class));
        assertThrows(NullPointerException.class, () -> basic.getContainedComponentClasses().contains(null));
        assertEquals("null", JSBasicConstantTypeInfo.NULL.toString());
        assertEquals("undefined", JSBasicConstantTypeInfo.UNDEFINED.toString());
        JSNumberConstantTypeInfo number = new JSNumberConstantTypeInfo(Integer.valueOf(10));
        assertEquals(Integer.valueOf(10), number.number()); assertEquals("10", number.toString());
        assertEquals(new NumberOracle(10).hashCode(), number.hashCode());
        assertNotEquals(number, new JSNumberConstantTypeInfo(Long.valueOf(10)));
        StringBuilder sb = new StringBuilder(); number.append(TypeStringContext.DEFAULT, sb); assertEquals("10", sb.toString());
    }

    @Test void optionalAndFixedArrayPreserveFormattingAndContainedTypes() {
        JSOptionalParam required = new JSOptionalParam("a", TypeInfo.STRING);
        JSOptionalParam optional = new JSOptionalParam("", TypeInfo.INT, true);
        assertFalse(required.optional()); assertEquals("a", required.name()); assertSame(TypeInfo.STRING, required.type());
        assertEquals(new OptionalOracle("a", TypeInfo.STRING, false).hashCode(), required.hashCode());
        assertEquals("a: java.lang.String", required.toString());
        assertEquals("java.lang.Integer?", optional.toString());
        List<JSOptionalParam> fields = new ArrayList<>(List.of(required, optional));
        JSFixedArrayTypeInfo value = new JSFixedArrayTypeInfo(fields);
        assertSame(fields, value.types()); assertEquals(new FixedOracle(fields).hashCode(), value.hashCode());
        assertEquals(value, new JSFixedArrayTypeInfo(new ArrayList<>(fields)));
        assertEquals("[a: java.lang.String, java.lang.Integer?]", value.toString());
        Set<Class<?>> collected = new HashSet<>(); value.collectContainedComponentClasses(collected);
        assertTrue(collected.contains(String.class)); assertTrue(collected.contains(Integer.class));
        fields.clear(); assertEquals("[]", value.toString());
    }

    @Test void loweredOwnersKeepFinalClassesAndNoRecordRuntimeRequirement() {
        for (Class<?> type : List.of(CachedParameters.class, CustomMember.class, WrappedReflectionMethod.class,
                TypeWrapper.class, JSBasicConstantTypeInfo.class, JSNumberConstantTypeInfo.class,
                JSOptionalParam.class, JSFixedArrayTypeInfo.class)) {
            assertTrue(Modifier.isFinal(type.getModifiers()));
            assertFalse(type.isRecord());
            assertSame(Object.class, type.getSuperclass());
        }
    }
}
