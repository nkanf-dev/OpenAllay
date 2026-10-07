package dev.latvian.mods.rhino;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class FunctionGlobalArraySwitchTest {
    private static String evaluate(String source) {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        return ScriptRuntime.toString(cx, cx.evaluateString(scope, source, "test.js", 1, null));
    }
    @Test void functionMetadataPrototypeApplyCallBindRemainExact() {
        assertEquals("f|2|2|true|5|6|9", evaluate(
            "function f(a,b){return this.n+a+b;}var o={n:1};var b=f.bind(o,3);[f.name,f.length,f.arity,f.hasOwnProperty('prototype'),f.call(o,2,2),f.apply(o,[2,3]),b(5)].join('|');"));
        assertEquals("1|0|1|2|1|1", evaluate(
            "[Function.prototype.constructor.length,Function.prototype.toString.length,Function.prototype.toSource.length,Function.prototype.apply.length,Function.prototype.call.length,Function.prototype.bind.length].join('|');"));
    }
    @Test void functionPropertyAttributesAndNamesKeepDescriptorRules() {
        assertEquals("changed|3|true", evaluate(
            "function f(a){}Object.defineProperty(f,'name',{value:'changed',configurable:true});Object.defineProperty(f,'length',{value:3,configurable:true});[f.name,f.length,typeof f.prototype==='object'].join('|');"));
    }
    @Test void globalFunctionNamesArityAndParseFloatBreakContinueRemainExact() {
        assertEquals("1|1|1|1|1|1|2|1", evaluate(
            "[decodeURI.length,decodeURIComponent.length,encodeURI.length,encodeURIComponent.length,escape.length,parseFloat.length,parseInt.length,unescape.length].join('|');"));
        assertEquals("1.2|1|1|1.2|100|NaN|NaN|Infinity|-Infinity|0.5", evaluate(
            "['1.2x','1e','1e+','1.2.3','1e2','e1','','Infinityx','-Infinity','.5'].map(parseFloat).join('|');"));
    }
    @Test void emptyAndSparseArrayAlgorithmsKeepOperationDefaultsAndCallbackCounts() {
        assertEquals("true|false|||undefined|-1|-1|0|true|false", evaluate(
            "var calls=0;function cb(){calls++;return true;}var empty=[];[empty.every(cb),empty.some(cb),empty.filter(cb).join(','),empty.map(cb).join(','),String(empty.find(cb)),empty.findIndex(cb),empty.findLastIndex(cb),calls,[,,].every(cb),[,,].some(cb)].join('|');"));
    }
}
