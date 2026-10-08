package dev.openallay.rhino.fixture;
import dev.latvian.mods.rhino.*;
public final class FunctionLengthBaselineOracle {
    public static void main(String[] args) {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        String source = "function f(a){}Object.defineProperty(f,'name',{value:'changed',configurable:true});Object.defineProperty(f,'length',{value:3,configurable:true});[f.name,f.length,typeof f.prototype==='object'].join('|');";
        String actual = ScriptRuntime.toString(cx, cx.evaluateString(scope, source, "test.js", 1, null));
        System.out.println("BASELINE_FUNCTION_LENGTH=" + actual);
        if (!"changed|1|true".equals(actual)) throw new AssertionError("Baseline source behaved unexpectedly: " + actual);
    }
}
