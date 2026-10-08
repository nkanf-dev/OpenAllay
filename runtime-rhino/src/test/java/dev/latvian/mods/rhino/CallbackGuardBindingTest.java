package dev.latvian.mods.rhino;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class CallbackGuardBindingTest {
    static String evaluate(String source) {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        return ScriptRuntime.toString(cx, cx.evaluateString(scope, source, "test.js", 1, null));
    }
    @Test void arrayCallbackGuardsReduceAndBindPreserveErrorsAndSingleCalls() {
        assertEquals("3|2|7|true|true", evaluate(
            "var calls=0;var sum=[1,2].reduce(function(a,b){calls++;return a+b;},0);var failed=false;try{[1].map(null);}catch(e){failed=e instanceof TypeError;}var bindFailed=false;try{Function.prototype.bind.call({});}catch(e){bindFailed=e instanceof TypeError;}var f=function(x){return this.n+x;};[sum,calls,f.bind({n:2})(5),failed,bindFailed].join('|');"));
    }
    @Test void mapSetForeachGuardsMaintainArgumentOrderAndReceiverIdentity() {
        assertEquals("a=1:true,b=2:true|3|true|true", evaluate(
            "var m=new Map([['a',1],['b',2]]), seen=[];m.forEach(function(v,k,self){seen.push(k+'='+v+':'+(self===m));});var s=new Set([1,2]), sum=0;s.forEach(function(v,k,self){if(v!==k||self!==s)throw Error('identity');sum+=v;});var badM=false,badS=false;try{m.forEach(1);}catch(e){badM=e instanceof TypeError;}try{s.forEach(null);}catch(e){badS=e instanceof TypeError;}[seen.join(','),sum,badM,badS].join('|');"));
    }
    @Test void errorCauseSymbolAndRegexArgsPreserveCapturedValuesAndGuards() {
        assertEquals("true|key|true|true", evaluate(
            "var cause={x:1},error=new Error('message',{cause:cause});var key=Symbol.for('key'),bad=false;try{Symbol.keyFor(1);}catch(e){bad=e instanceof TypeError;}var regex=false;try{'abc'.startsWith(/a/);}catch(e){regex=e instanceof TypeError;}[error.cause===cause,Symbol.keyFor(key),bad,regex].join('|');"));
    }
    @Test void iteratorGeneratorEnumerationAndSymbolDescriptorsKeepGuestPaths() {
        assertEquals("1,2,3|a,b|value", evaluate(
            "function* values(){yield 1;yield* [2,3];}var names=[],o={a:1,b:2};for(var k in o)names.push(k);var symbol=Symbol('s');Object.defineProperty(o,symbol,{value:'value'});[Array.from(values()).join(','),names.sort().join(','),o[symbol]].join('|');"));
    }
}
