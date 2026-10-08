package dev.latvian.mods.rhino;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class InterpreterScannerLanguageTest {
    static String evaluate(String source) {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        return ScriptRuntime.toString(cx, cx.evaluateString(scope, source, "interpreter.js", 1, null));
    }
    @Test void bitAndArithmeticStackResultsPreserveOperandOrder() {
        assertEquals("1|7|6|20|-2|5|21|3|1|49", evaluate(
            "[5&3,5|3,5^3,5<<2,-8>>2,8-3,7*3,9/3,7%3,7**2].join('|');"));
        assertEquals("3|2|1", evaluate("var n=0;function next(){return ++n;}[next()+next(),n,next()-2].join('|');"));
    }
    @Test void interpretedApplyCallConstructorAndConstPathsKeepIdentityAndArguments() {
        assertEquals("5|6|7|3|true", evaluate(
            "function f(a,b){return this.n+a+b;}var o={n:1};function C(x){this.x=x;}const k=3;var bad=false;try{var notCtor=1;new notCtor();}catch(e){bad=e instanceof TypeError;}[f.call(o,2,2),f.apply(o,[2,3]),new C(7).x,k,bad].join('|');"));
    }
    @Test void keywordScannerRetainsAllKnownNamesAndOrdinaryIdentifiers() {
        String[] keywords = {"break","case","catch","const","continue","default","delete","do","else","finally","for","function","if","in","instanceof","new","return","switch","this","throw","try","typeof","var","void","while","with","yield","false","null","true","let","class","export","static","public","protected","private","package","interface","implements","enum","await","super","import","extends"};
        for (String keyword : keywords) { assertTrue(TokenStream.isKeyword(keyword, false), keyword); assertTrue(TokenStream.isKeyword(keyword, true), keyword); }
        assertFalse(TokenStream.isKeyword("ordinary", false)); assertFalse(TokenStream.isKeyword("Function", true));
    }
}
