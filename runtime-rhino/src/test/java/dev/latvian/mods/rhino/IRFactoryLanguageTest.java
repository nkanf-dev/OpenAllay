package dev.latvian.mods.rhino;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class IRFactoryLanguageTest {
    static String evaluate(String source) {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        return ScriptRuntime.toString(cx, cx.evaluateString(scope, source, "ir.js", 1, null));
    }
    @Test void orderedDefaultsDestructuringAndNameInferenceKeepDependencies() {
        assertEquals("1|1|3|3|named", evaluate(
            "function f(a=1,{b=a}={}){return [a,b].join('|');}var named=function(){};[f(),f(3),named.name].join('|');"));
    }
    @Test void assignmentUpdateConditionalAndComputedPropertyTransformKeepValues() {
        assertEquals("3|2|5|6|value", evaluate(
            "var o={x:2},a=[4],key='k';o.x+=1;var old=o.x--;a[0]++;var n=(false?1:6);var m={[key]:'value'};[old,o.x,a[0],n,m.k].join('|');"));
    }
    @Test void loopsLabeledContinueTryFinallyAndOptionalTransformKeepFlow() {
        assertEquals("4|3|undefined", evaluate(
            "var n=0,done=0;outer:for(var i=0;i<3;i++){for(var j=0;j<2;j++){if(j===1)continue outer;n++;} }try{n++;}finally{done=3;}var o=null;[n,done,String(o?.x)].join('|');"));
    }
    @Test void generatorYieldTemplatesRegexAndCallTransformsKeepGuestPaths() {
        assertEquals("1,2|x3|true|7", evaluate(
            "function* g(){yield 1;yield* [2];}function C(v){this.v=v;}[Array.from(g()).join(','),`x${1+2}`,/a/.test('a'),new C(7).v].join('|');"));
    }
}
