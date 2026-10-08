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
        {
boolean $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((this)) {
case STRING:
{
$oaSwitch0_exit_result = primitive.isString(); break $oaSwitch0_exit;
}
case BOOLEAN:
{
$oaSwitch0_exit_result = primitive.isBoolean(); break $oaSwitch0_exit;
}
case INTEGER:
case NUMBER:
{
{
                if (!primitive.isNumber()) { $oaSwitch0_exit_result = false; break $oaSwitch0_exit; }
                double number = primitive.getAsDouble();
                if (!Double.isFinite(number)) { $oaSwitch0_exit_result = false; break $oaSwitch0_exit; }
                if (this == NUMBER) { $oaSwitch0_exit_result = true; break $oaSwitch0_exit; }
                try {
                    java.math.BigInteger integer = primitive.getAsBigDecimal().toBigIntegerExact();
                    { $oaSwitch0_exit_result = integer.abs().compareTo(java.math.BigInteger.valueOf(9_007_199_254_740_991L)) <= 0; break $oaSwitch0_exit; }
                } catch (ArithmeticException | NumberFormatException invalid) {
                    { $oaSwitch0_exit_result = false; break $oaSwitch0_exit; }
                }
            }
}
case JSON:
case NULL:
{
$oaSwitch0_exit_result = false; break $oaSwitch0_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
return $oaSwitch0_exit_result;
}
    }
}
