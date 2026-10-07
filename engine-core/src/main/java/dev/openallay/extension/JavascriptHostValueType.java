package dev.openallay.extension;

import com.google.gson.JsonElement;

/** Closed JSON value algebra for controlled host methods. No Java wrappers or callbacks. */
public enum JavascriptHostValueType {
    STRING, BOOLEAN, INTEGER, NUMBER, JSON, NULL;

    public boolean accepts(JsonElement value) {
        if (value == null) return false;
        if (this == JSON) return true;
        if (this == NULL) return value.isJsonNull();
        if (!value.isJsonPrimitive()) return false;
        com.google.gson.JsonPrimitive primitive = value.getAsJsonPrimitive();
        return switch (this) {
            case STRING -> primitive.isString();
            case BOOLEAN -> primitive.isBoolean();
            case INTEGER, NUMBER -> {
                if (!primitive.isNumber()) yield false;
                double number = primitive.getAsDouble();
                if (!Double.isFinite(number)) yield false;
                if (this == NUMBER) yield true;
                try {
                    java.math.BigInteger integer = primitive.getAsBigDecimal().toBigIntegerExact();
                    yield integer.abs().compareTo(java.math.BigInteger.valueOf(9_007_199_254_740_991L)) <= 0;
                } catch (ArithmeticException | NumberFormatException invalid) {
                    yield false;
                }
            }
            case JSON, NULL -> false;
        };
    }
}
