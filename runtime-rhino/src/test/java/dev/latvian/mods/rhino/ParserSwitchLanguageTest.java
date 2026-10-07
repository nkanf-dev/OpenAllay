package dev.latvian.mods.rhino;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class ParserSwitchLanguageTest {
    static String evaluate(String source) {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        return ScriptRuntime.toString(cx, cx.evaluateString(scope, source, "parser.js", 1, null));
    }
    @Test void equalityShiftMulUnaryPrecedenceAndPostfixKeepGrammarAndEffects() {
        assertEquals("true|4|14|-3|false|number|undefined|2|4", evaluate(
            "var n=2,old=n++;var next=++n;[1===1,1<<2,2+3*4,-3,!1,typeof 1,typeof(void 1),old,next].join('|');"));
    }
    @Test void objectGetterSetterGeneratorComputedAndKeywordPropertiesKeepKinds() {
        assertEquals("4|value|2|keyword", evaluate(
            "var n=0,key='k',o={get x(){return n;},set x(v){n=v;},[key]:'value',*g(){yield 2;},if:'keyword'};o.x=4;[o.x,o.k,o.g().next().value,o.if].join('|');"));
    }
    @Test void destructuringPropertyAssignmentAndDefaultPatternsKeepTargets() {
        assertEquals("3|4|9|2", evaluate(
            "var a,b;[a,b]=[3,4];var o={p:0};[o.p]=[9];var {x=2}={};[a,b,o.p,x].join('|');"));
    }
    @Test void duplicateDeclarationsAndStrictInvalidTargetsStillFailParsing() {
        for (String source : new String[]{"let x=1;let x=2;", "const x=1;const x=2;", "'use strict';eval=1;",
                "'use strict';var o={get x(){return 1;},get x(){return 2;}};", "function f( {", "(1+2)=3;"}) {
            Context errorContext = new ContextFactory().enter();
            Scriptable errorScope = errorContext.initStandardObjects();
            assertThrows(EvaluatorException.class, () -> errorContext.evaluateString(errorScope, source, "bad.js", 1, null), source);
        }
        assertEquals("undefined", evaluate("var o={set x(a,b){}};"));
        assertEquals("7|undefined", evaluate("var observed;var o={set x(a,b){observed=[a,String(b)].join('|');}};o.x=7;observed;"));
        assertEquals("object", evaluate("var o={set x(){}};o.x=7;typeof o;"));
        assertEquals("7", evaluate("var observed;var o={set x(a){observed=a;}};o.x=7;observed;"));
        assertEquals("7", evaluate("var o={get x(){return 7;}};o.x;"));
        assertEquals("undefined", evaluate("var o={get x(a){return a;}};String(o.x);"));
        assertEquals("undefined", evaluate("var o={set (a){}};"));
    }
}
