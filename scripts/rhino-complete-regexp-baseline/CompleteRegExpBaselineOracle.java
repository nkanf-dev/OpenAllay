package dev.latvian.mods.rhino;
public final class CompleteRegExpBaselineOracle {
    static void vector(String name, String source) {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        try {
            Object value = cx.evaluateString(scope, source, "regex.js", 1, null);
            System.out.println(name + "\tvalue\t" + ScriptRuntime.toString(cx, value));
        } catch (Throwable failure) {
            System.out.println(name + "\tthrow\t" + failure.getClass().getName() + "\t" + failure.getMessage());
        }
    }
    public static void main(String[] args) {
        vector("regexCompilerAnchorsClassesGroupsQuantifiersAndEscapesKeepMatches_1", "[/^abc$/.test('abc'),/[a-c]+/.test('abcc'),/(a)\\1/.test('aa'),/a{2,3}?/.test('aaa'),/\\d+\\s\\w/.test('12 x'),/^a$/.test('ba')].join('|');");
        vector("actionSearchMatchReplaceDollarTokensAndCallbackCountsRemainExact_2", "var n=0;var replaced='aa'.replace(/a/g,function(m){n++;return '<'+m+'>';});['ba'.search(/a/),'aa'.match(/a/g).join(','),replaced,n,'zab'.replace(/(a)/,'<$1>'),'zabc'.replace(/(a)/,'$&')].join('|');");
        vector("regexCompileClonesFlagsAndPropertyMappingsKeepReadonlyAndLastIndex_3", "var original=/a/gi,clone=new RegExp(original);clone.lastIndex=2;[clone.source,clone.flags,clone.global,clone.ignoreCase,clone.lastIndex,clone.test('zza')].join('|');");
        vector("regexCompileClonesFlagsAndPropertyMappingsKeepReadonlyAndLastIndex_4", "var r=/a/g;r.compile(/b/,'i');[r.source,r.flags,r.global,r.ignoreCase].join('|');");
        vector("invalidRegexAndReplaceAllGlobalGuardKeepActualErrors_5", "var a=false,b=false,c=false;try{new RegExp('(');}catch(e){a=e instanceof SyntaxError;}try{new RegExp('*');}catch(e){b=e instanceof SyntaxError;}try{'aa'.replaceAll(/a/,'x');}catch(e){c=e instanceof TypeError;}[a,b,c].join('|');");
    }
}
