package dev.latvian.mods.rhino;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class ScriptRuntimeLanguageTest {
    @Test void whitespaceAndEscapeMappingsKeepAllCodeUnits() {
        for (int c : new int[]{' ', '\n', '\r', '\t', 0xa0, 0xc, 0xb, 0x2028, 0x2029, 0xfeff}) assertTrue(ScriptRuntime.isStrWhiteSpaceChar(c));
        assertFalse(ScriptRuntime.isStrWhiteSpaceChar('x'));
        assertEquals("\\b\\f\\n\\r\\t\\v\\\\\\'", ScriptRuntime.escapeString("\b\f\n\r\t" + (char) 0xb + "\\'", '\''));
        assertEquals("'text'", ScriptRuntime.escapeAndWrapString("text"));
        assertEquals("\"a'b\"", ScriptRuntime.escapeAndWrapString("a'b"));
    }
    @Test void compareKeepsLexicalNumericAndLeftBeforeRightPrimitiveCallbacks() {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        assertTrue(ScriptRuntime.compare(cx, "a", "b", Token.LT)); assertTrue(ScriptRuntime.compare(cx, 3, 2, Token.GT));
        assertFalse(ScriptRuntime.compare(cx, Double.NaN, 2, Token.LE));
        Object value = cx.evaluateString(scope,
            "var calls=[];var a={valueOf:function(){calls.push('a');return 1;}},b={valueOf:function(){calls.push('b');return 2;}};var result=a<b;[result,calls.join(',')].join('|');", "compare.js", 1, null);
        assertEquals("true|a,b", ScriptRuntime.toString(cx, value));
    }
    @Test void optionalCallableAndIteratorPropertyDispatchKeepCallbackCountsAndErrors() {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        Object value = cx.evaluateString(scope,
            "var calls=0,o={f:function(){calls++;return 4;}},none=null;var got=o.f?.();var skipped=none?.();var bad=false;try{var x=1;x();}catch(e){bad=e instanceof TypeError;}[got,String(skipped),calls,bad,Array.from([1,2]).join(',')].join('|');", "call.js", 1, null);
        assertEquals("4|undefined|1|true|1,2", ScriptRuntime.toString(cx, value));
    }
    @Test void catchThrowableAndLiteralKeySourcePathsKeepGuestValues() {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        Object value = cx.evaluateString(scope,
            "var kind='',message='';try{throw new TypeError('bad');}catch(e){kind=e.name;message=e.message;}var key='k',o={[key]:3,0:4};[kind,message,o.k,o[0]].join('|');", "catch.js", 1, null);
        assertEquals("TypeError|bad|3|4", ScriptRuntime.toString(cx, value));
    }
}
