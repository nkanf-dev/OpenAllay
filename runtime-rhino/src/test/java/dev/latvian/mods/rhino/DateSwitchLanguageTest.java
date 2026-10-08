package dev.latvian.mods.rhino;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class DateSwitchLanguageTest {
    static String evaluate(String source) {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        return ScriptRuntime.toString(cx, cx.evaluateString(scope, source, "date.js", 1, null));
    }
    @Test void isoTransitionsUtcOffsetsAndLeapDatesKeepExactTimestamps() {
        assertEquals("0|0|0|951782400000|true|true", evaluate(
            "[Date.parse('1970-01-01T00:00:00Z'),Date.parse('1970-01-01T01:00:00+01:00'),Date.parse('1970-01-01T01:00:00+0100'),Date.UTC(2000,1,29),isNaN(Date.parse('2020-01-01T')),isNaN(Date.parse('2020-01-01T00:00:00+'))].join('|');"));
    }
    @Test void legacyTimezoneMappingsKeepKnownGmtAndEstOffsets() {
        assertEquals("0|18000000|14400000", evaluate(
            "[Date.parse('Jan 1 1970 00:00:00 GMT'),Date.parse('Jan 1 1970 00:00:00 EST'),Date.parse('Jan 1 1970 00:00:00 EDT')].join('|');"));
    }
    @Test void prototypeArityIsoAndJsonInvalidBehaviorRemainExact() {
        assertEquals("7|0|1|1970-01-01T00:00:00.000Z|null|true", evaluate(
            "var invalid=new Date(NaN),bad=false;try{invalid.toISOString();}catch(e){bad=e instanceof RangeError;}[Date.prototype.constructor.length,Date.prototype.toISOString.length,Date.prototype.toJSON.length,new Date(0).toISOString(),String(invalid.toJSON()),bad].join('|');"));
    }
    @Test void utcGettersAndSettersRetainAllCalendarFields() {
        assertEquals("2001|1|3|4|5|6|7", evaluate(
            "var d=new Date(0);d.setUTCFullYear(2001,1,3);d.setUTCHours(4,5,6,7);[d.getUTCFullYear(),d.getUTCMonth(),d.getUTCDate(),d.getUTCHours(),d.getUTCMinutes(),d.getUTCSeconds(),d.getUTCMilliseconds()].join('|');"));
    }
}
