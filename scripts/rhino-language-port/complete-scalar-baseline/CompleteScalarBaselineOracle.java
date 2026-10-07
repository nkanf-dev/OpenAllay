package dev.latvian.mods.rhino;
import dev.latvian.mods.rhino.type.TypeInfo;
public final class CompleteScalarBaselineOracle {
    private interface Operation { Object run(); }
    static void vector(String name, Operation operation) {
        try {
            Object value = operation.run();
            String type = value == null ? "null" : value.getClass().getName();
            System.out.println(name + "\tvalue\t" + type + "\t" + String.valueOf(value));
        } catch (Throwable failure) {
            System.out.println(name + "\tthrow\t" + failure.getClass().getName() + "\t" + String.valueOf(failure.getMessage()));
        }
    }
    public static void main(String[] args) {
        Context cx = new ContextFactory().enter();
        vector("public_null_string", () -> cx.jsToJava(null, TypeInfo.STRING));
        vector("public_null_primitive_int", () -> cx.jsToJava(null, TypeInfo.PRIMITIVE_INT));
        vector("internal_null_primitive_int", () -> cx.internalJsToJava(null, TypeInfo.PRIMITIVE_INT));
        vector("undefined_string", () -> cx.jsToJava(Undefined.INSTANCE, TypeInfo.STRING));
        vector("boolean_string", () -> cx.jsToJava(Boolean.TRUE, TypeInfo.STRING));
        vector("boolean_boxed", () -> cx.jsToJava(Boolean.TRUE, TypeInfo.BOOLEAN));
        vector("decimal_string_boxed_int", () -> cx.jsToJava("12", TypeInfo.INT));
        vector("single_character_boxed", () -> cx.jsToJava("x", TypeInfo.CHARACTER));
        vector("number_string", () -> cx.jsToJava(7, TypeInfo.STRING));
        vector("can_convert_decimal_boxed_int", () -> cx.canConvert("12", TypeInfo.INT));
        vector("can_convert_null_primitive_int", () -> cx.canConvert(null, TypeInfo.PRIMITIVE_INT));
        vector("can_convert_decimal_primitive_int", () -> cx.canConvert("12", TypeInfo.PRIMITIVE_INT));
        vector("weight_decimal_boxed_int", () -> cx.getConversionWeight("12", TypeInfo.INT));
        vector("weight_decimal_primitive_int", () -> cx.getConversionWeight("12", TypeInfo.PRIMITIVE_INT));
    }
}
