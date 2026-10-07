package dev.openallay.json;

import dev.openallay.value.ValueSchemas;
import dev.openallay.value.ValueSchema;
import dev.openallay.agent.tool.ToolDescription;
import dev.openallay.agent.tool.ToolOptional;
import java.util.Arrays;
import java.util.List;

/** Java8 reporter runs against original17 or converted8 fixture classes. */
public final class RecordConversionReport {
    private RecordConversionReport() {}
    public static void main(String[] args) throws Exception {
        boolean converted = args.length == 1 && args[0].equals("converted");
        if (converted && !"1.8".equals(System.getProperty("java.specification.version"))) throw new AssertionError("GenuineJava8 required");
        RecordFixtureValues.Empty empty = new RecordFixtureValues.Empty();
        check(empty.equals(new RecordFixtureValues.Empty()) && empty.hashCode() == 0, "empty");
        RecordFixtureValues.Numbers numbers = new RecordFixtureValues.Numbers(true, (byte) -2, (short) 3, 'x',
                17, 1234567890123L, -0.0f, Double.NaN, null);
        RecordFixtureValues.Numbers same = new RecordFixtureValues.Numbers(true, (byte) -2, (short) 3, 'x',
                17, 1234567890123L, -0.0f, Double.NaN, null);
        check(numbers.equals(same), "primitive equality");
        check(!numbers.equals(new RecordFixtureValues.Numbers(true, (byte) -2, (short) 3, 'x',
                17, 1234567890123L, 0.0f, Double.NaN, null)), "signed zero");
        int hash = 0;
        for (int item : new int[] {Boolean.hashCode(true), Byte.hashCode((byte)-2), Short.hashCode((short)3),
                Character.hashCode('x'), Integer.hashCode(17), Long.hashCode(1234567890123L),
                Float.hashCode(-0.0f), Double.hashCode(Double.NaN), 0}) hash = 31 * hash + item;
        check(numbers.hashCode() == hash, "hash zero seed");
        RecordFixtureValues.Normalized normalized = new RecordFixtureValues.Normalized(" name ", Arrays.asList("a", "b"));
        check(normalized.name().equals("name") && normalized.values().equals(Arrays.asList("a", "b")), "compact normalize");
        check(new RecordFixtureValues.Normalized("x").values().isEmpty(), "overload");
        try { new RecordFixtureValues.Normalized(null); throw new AssertionError("null accepted"); } catch (IllegalArgumentException expected) {}
        try { new RecordFixtureValues.Normalized("x", Arrays.asList("a", null)); throw new AssertionError("null element accepted"); } catch (NullPointerException expected) {}
        try { normalized.values().add("x"); throw new AssertionError("mutable snapshot"); } catch (UnsupportedOperationException expected) {}
        List<String> backing = Arrays.asList("a", "b");
        RecordFixtureValues.OverrideValue override = new RecordFixtureValues.OverrideValue(backing);
        check(override.values() != override.values(), "accessor override retained");
        check(override.equals(new RecordFixtureValues.OverrideValue(backing)), "field equality");
        check(override.hashCode() == backing.hashCode(), "field hash");
        RecordFixtureValues.Generic<String> generic = new RecordFixtureValues.Generic<>("x", Arrays.asList("a"));
        check(generic.value().equals("x") && generic.history().equals(Arrays.asList("a")), "generic accessors");
        RecordFixtureValues.Custom custom = new RecordFixtureValues.Custom("x");
        check(custom.hashCode() == 7 && custom.toString().equals("custom:x"), "custom overrides");
        if (converted) {
            ValueSchema<RecordFixtureValues.Normalized> schema = ValueSchemas.of(RecordFixtureValues.Normalized.class);
            check(schema.components().get(0).annotation(ToolDescription.class).value().equals("Name"), "annotation description");
            check(schema.components().get(1).annotation(ToolOptional.class) != null, "annotation optional");
            check(schema.construct(new Object[] {" y ", Arrays.asList("z")}).name().equals("y"), "typed constructor");
            ValueSchema<RecordFixtureValues.Generic> genericSchema = ValueSchemas.of(RecordFixtureValues.Generic.class);
            check(genericSchema.components().get(0).genericType() instanceof java.lang.reflect.TypeVariable, "generic TypeVariable");
            check(genericSchema.components().get(1).genericType() instanceof java.lang.reflect.ParameterizedType, "generic ListType");
        }
        List<String> mutable = new java.util.ArrayList<>(Arrays.asList("first", "second"));
        RecordFixtureValues.MarkerCopy markerCopy = new RecordFixtureValues.MarkerCopy(mutable);
        mutable.add("later");
        check(markerCopy instanceof RecordFixtureValues.Marker, "marker interface retained");
        check(markerCopy.input().equals(Arrays.asList("first", "second")), "marker constructor copy");
        markerCopy.input().add("guest mutation");
        check(markerCopy.input().equals(Arrays.asList("first", "second")), "marker accessor defensive copy");
        check(markerCopy.equals(new RecordFixtureValues.MarkerCopy(Arrays.asList("first", "second"))), "marker field equality");
        check(markerCopy.hashCode() == Arrays.asList("first", "second").hashCode(), "marker field hash zero seed");
        if (converted) {
            ValueSchema<RecordFixtureValues.MarkerCopy> markerSchema = ValueSchemas.of(RecordFixtureValues.MarkerCopy.class);
            check(markerSchema.components().get(0).read(markerCopy) != markerSchema.components().get(0).read(markerCopy), "marker schema uses copy accessor");
        }
        RecordFixtureValues.InterfaceOwner.MemberValue interfaceValue =
                new RecordFixtureValues.InterfaceOwner.MemberValue(9);
        check(java.lang.reflect.Modifier.isPublic(interfaceValue.getClass().getModifiers()), "interface member public class ABI");
        check(java.lang.reflect.Modifier.isPublic(interfaceValue.getClass().getConstructor(int.class).getModifiers()), "interface member public constructor ABI");
        Object privateValue = RecordFixtureValues.privateValue("private");
        check(java.lang.reflect.Modifier.isPrivate(privateValue.getClass().getModifiers()), "private class member visibility retained");
        check(java.lang.reflect.Modifier.isPrivate(privateValue.getClass().getDeclaredConstructor(String.class).getModifiers()), "private constructor visibility retained");
        // Oracle output has no VM identity hashes, so exact bytes compare across17/8.
        System.out.println(empty.toString());
        System.out.println(numbers.toString());
        System.out.println(normalized.toString());
        System.out.println(override.toString());
        System.out.println(generic.toString());
        System.out.println(custom.toString());
        System.out.println(markerCopy.toString());
        System.out.println(interfaceValue.toString());
        System.out.println(privateValue.toString());
        System.out.println("PASS record conversion vectors");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
