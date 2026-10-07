package dev.latvian.mods.rhino;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class RegExpLanguageClosureTest {
    static String evaluate(String source) {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        return ScriptRuntime.toString(cx, cx.evaluateString(scope, source, "regex.js", 1, null));
    }
    @Test void regexCompilerAnchorsClassesGroupsQuantifiersAndEscapesKeepMatches() {
        assertEquals("true|true|true|true|true|false", evaluate(
            "[/^abc$/.test('abc'),/[a-c]+/.test('abcc'),/(a)\\1/.test('aa'),/a{2,3}?/.test('aaa'),/\\d+\\s\\w/.test('12 x'),/^a$/.test('ba')].join('|');"));
    }
    @Test void actionSearchMatchReplaceDollarTokensAndCallbackCountsRemainExact() {
        assertEquals("1|a,a|<a><a>|2|z<a>b|zabc", evaluate(
            "var n=0;var replaced='aa'.replace(/a/g,function(m){n++;return '<'+m+'>';});['ba'.search(/a/),'aa'.match(/a/g).join(','),replaced,n,'zab'.replace(/(a)/,'<$1>'),'zabc'.replace(/(a)/,'$&')].join('|');"));
    }
    @Test void regexCompileClonesFlagsAndPropertyMappingsKeepReadonlyAndLastIndex() {
        assertEquals("a|gi|true|true|2|true", evaluate(
            "var original=/a/gi,clone=new RegExp(original);clone.lastIndex=2;[clone.source,clone.flags,clone.global,clone.ignoreCase,clone.lastIndex,clone.test('zza')].join('|');"));
        assertEquals("b|i|false|true", evaluate("var r=/a/g;r.compile(/b/,'i');[r.source,r.flags,r.global,r.ignoreCase].join('|');"));
    }
    @Test void invalidRegexAndReplaceAllGlobalGuardKeepActualErrors() {
        assertEquals("true|true|true", evaluate(
            "var a=false,b=false,c=false;try{new RegExp('(');}catch(e){a=e instanceof SyntaxError;}try{new RegExp('*');}catch(e){b=e instanceof SyntaxError;}try{'aa'.replaceAll(/a/,'x');}catch(e){c=e instanceof TypeError;}[a,b,c].join('|');"));
    }
}
