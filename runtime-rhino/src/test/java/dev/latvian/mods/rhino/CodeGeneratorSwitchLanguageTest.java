package dev.latvian.mods.rhino;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class CodeGeneratorSwitchLanguageTest {
    static String evaluate(String source) {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        return ScriptRuntime.toString(cx, cx.evaluateString(scope, source, "compiler.js", 1, null));
    }
    @Test void logicalConditionalCommaAndNullishPreserveEvaluationOrder() {
        assertEquals("3|1|2|3|4|3", evaluate(
            "var n=0;function next(){return ++n;}var a=false&&next(),b=true||next(),c=null??next(),d=true?next():next(),e=(next(),4);[n,c,d,n,e,n].join('|');"));
    }
    @Test void optionalPropertyElementAndCallPreserveReceiverAndSkippedArguments() {
        assertEquals("7|8|undefined|0|true", evaluate(
            "var n=0,o={x:5,f:function(a){return this.x+a;}},nil=null;function arg(){n++;return 1;}[o?.f(2),o['f']?.(3),String(nil?.f(arg())),n,o.f?.(0)===5].join('|');"));
    }
    @Test void propertyAndElementIncrementsAssignmentsKeepSingleReceiverCalls() {
        assertEquals("2|2|4|5|5|2", evaluate(
            "var calls=0,o={x:2},a=[4];function obj(){calls++;return o;}var old=obj().x++;var now=++obj().x;a[0]+=1;[calls,old,o.x,a[0],a[0]++,calls].join('|');"));
    }
    @Test void literalsTemplatesConstructorsDeleteAndArithmeticRetainStackResults() {
        assertEquals("x3|2|true|9|undefined", evaluate(
            "function C(v){this.v=v;}var o={a:1,b:2},a=[1,,3];var text=`x${a[0]+2}`;var deleted=delete o.a;[text,o.b,deleted,new C(9).v,String(a[1])].join('|');"));
    }
}
