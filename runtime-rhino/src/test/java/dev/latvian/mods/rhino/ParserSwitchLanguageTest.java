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
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        for (String source : new String[]{"let x=1;let x=2;", "const x=1;const x=2;", "'use strict';eval=1;", "var o={set x(a,b){}};"}) {
            assertThrows(RuntimeException.class, () -> cx.evaluateString(scope, source, "bad.js", 1, null), source);
        }
    }
}
