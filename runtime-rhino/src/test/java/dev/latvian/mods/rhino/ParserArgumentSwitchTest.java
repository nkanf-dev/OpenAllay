package dev.latvian.mods.rhino;
import dev.latvian.mods.rhino.json.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class ParserArgumentSwitchTest {
    @Test void jsonParserKeepsTokenDispatchCommasEscapesAndNestedValues() throws Exception {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects(); JsonParser parser = new JsonParser(scope);
        assertNull(parser.parseValue(cx, "null")); assertEquals(Boolean.TRUE, parser.parseValue(cx, "true"));
        assertEquals(Boolean.FALSE, parser.parseValue(cx, "false"));
        assertEquals(-12.5, ((Number) parser.parseValue(cx, "-12.5")).doubleValue());
        assertEquals("a\n\t\b\f\r/\\\"A", parser.parseValue(cx, "\"a\\n\\t\\b\\f\\r\\/\\\\\\\"\\u0041\""));
        Scriptable object = (Scriptable) parser.parseValue(cx, "{\"x\":[1,true,null],\"0\":\"zero\"}");
        assertEquals("zero", object.get(cx, 0, object)); NativeArray array = (NativeArray) object.get(cx, "x", object);
        assertEquals(3L, array.getLength()); assertEquals(Boolean.TRUE, array.get(cx, 1, array)); assertNull(array.get(cx, 2, array));
    }
    @Test void jsonParserKeepsMalformedCommaEscapeAndTrailingInputErrors() {
        Context cx = new ContextFactory().enter(); JsonParser parser = new JsonParser(cx.initStandardObjects());
        for (String input : new String[]{"", "{,}", "{\"a\":1,}", "{\"a\":1 \"b\":2}", "[,1]", "[1,]", "[1 2]", "true false", "\"\\q\"", "\"\\u00xx\""}) {
            assertThrows(JsonParser.ParseException.class, () -> parser.parseValue(cx, input), input);
        }
    }
    @Test void argumentsPropertiesAndStrictModeKeepMappedAndUnmappedViews() {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        Object result = cx.evaluateString(scope,
            "(function(a){arguments.length=7;arguments.caller=null;arguments[0]=5;return [a,arguments.length,arguments.caller,typeof arguments.callee].join('|');})(1)", "test.js", 1, null);
        assertEquals("5|7||function", ScriptRuntime.toString(cx, result));
        Object strict = cx.evaluateString(scope,
            "(function(a){'use strict';arguments[0]=5;var denied=false;try{arguments.callee;}catch(e){denied=e instanceof TypeError;}return [a,denied].join('|');})(1)", "strict.js", 1, null);
        assertEquals("1|true", ScriptRuntime.toString(cx, strict));
    }
    @Test void specialReferencesPreservePrototypeParentAndCycleChecks() {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        Scriptable child = cx.newObject(scope), parent = cx.newObject(scope);
        Ref proto = SpecialRef.createSpecial(cx, scope, child, "__proto__");
        proto.set(cx, scope, parent); assertSame(parent, proto.get(cx));
        assertThrows(RuntimeException.class, () -> proto.set(cx, scope, child));
        Ref scopeRef = SpecialRef.createSpecial(cx, scope, child, "__parent__");
        scopeRef.set(cx, scope, parent); assertSame(parent, scopeRef.get(cx));
        assertThrows(IllegalArgumentException.class, () -> SpecialRef.createSpecial(cx, scope, child, "other"));
    }
    @Test void callSitePrototypeNamesAndDispatchKeepKnownFrameFields() {
        Context cx = new ContextFactory().enter(); ScriptableObject scope = cx.initStandardObjects();
        NativeCallSite.init(scope, false, cx);
        Scriptable constructor = (Scriptable) ScriptableObject.getProperty(scope, "CallSite", cx);
        NativeCallSite site = NativeCallSite.make(scope, constructor, cx);
        site.setElement(new ScriptStackElement("frame.js", "functionName", 12));
        for (String name : new String[]{"getFunctionName", "getFileName", "getLineNumber", "getThis", "getTypeName", "getFunction", "getColumnNumber", "getMethodName", "getEvalOrigin", "isEval", "isConstructor", "isNative", "isToplevel", "toString"}) {
            Object function = ScriptableObject.getProperty(site, name, cx); assertTrue(function instanceof Callable, name);
            Object value = ((Callable) function).call(cx, scope, site, ScriptRuntime.EMPTY_OBJECTS);
            if (name.equals("getFunctionName")) assertEquals("functionName", value);
            if (name.equals("getFileName")) assertEquals("frame.js", value);
            if (name.equals("getLineNumber")) assertEquals(12, value);
            if (name.equals("getMethodName")) assertNull(value);
            if (name.startsWith("is") || name.equals("getEvalOrigin")) assertEquals(Boolean.FALSE, value);
        }
    }
}
