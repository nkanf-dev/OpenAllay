package dev.latvian.mods.rhino;
public final class ParserMalformedBaselineOracle {
    static void vector(String name, String source) {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        try {
            Object value = cx.evaluateString(scope, source, "bad.js", 1, null);
            System.out.println(name + "\tvalue\t" + String.valueOf(value));
        } catch (Throwable failure) {
            System.out.println(name + "\tthrow\t" + failure.getClass().getName() + "\t" + failure.getMessage());
        }
    }
    public static void main(String[] args) {
        vector("duplicate_let", "let x=1;let x=2;");
        vector("duplicate_const", "const x=1;const x=2;");
        vector("strict_eval_assignment", "'use strict';eval=1;");
        vector("declared_two_parameter_setter", "var o={set x(a,b){}};");
        vector("two_parameter_setter_value", "var observed;var o={set x(a,b){observed=[a,String(b)].join('|');}};o.x=7;observed;");
        vector("zero_parameter_setter", "var o={set x(){}};o.x=7;typeof o;");
        vector("one_parameter_setter", "var observed;var o={set x(a){observed=a;}};o.x=7;observed;");
        vector("zero_parameter_getter", "var o={get x(){return 7;}};o.x;");
        vector("one_parameter_getter", "var o={get x(a){return a;}};String(o.x);");
        vector("malformed_setter_identifier", "var o={set (a){}};");
        vector("duplicate_strict_getter", "'use strict';var o={get x(){return 1;},get x(){return 2;}};");
        vector("incomplete_function", "function f( {");
        vector("invalid_assignment_target", "(1+2)=3;");
    }
}
