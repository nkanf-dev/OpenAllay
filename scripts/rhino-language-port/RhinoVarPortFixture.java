package dev.openallay.tools.rhino;

import java.net.URI;
import java.util.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.*;
import com.sun.source.util.*;

/** Real compiler type mirrors, never fake project TypeInfo declarations. */
public final class RhinoVarPortFixture {
    static final class Source extends SimpleJavaFileObject {
        final String text;
        Source(String name, String text) { super(URI.create("string:///" + name + ".java"), Kind.SOURCE); this.text = text; }
        @Override public CharSequence getCharContent(boolean ignore) { return text; }
    }
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        JavacTask task = (JavacTask) compiler.getTask(null, null, null, List.of("--release", "17", "-proc:none"), null,
            List.of(new Source("MirrorFixture", "class MirrorFixture<T> { T value; java.util.List<? extends Number> wildcard; Object[] array; void test(){ var n=1; var a=new Runnable(){public void run(){}}; } }")));
        Iterable<? extends com.sun.source.tree.CompilationUnitTree> units = task.parse(); task.analyze();
        TypeElement fixture = task.getElements().getTypeElement("MirrorFixture");
        Map<String, String> expected = Map.of("value", "T", "wildcard", "java.util.List<? extends java.lang.Number>", "array", "java.lang.Object[]");
        for (Element member : fixture.getEnclosedElements()) {
            if (expected.containsKey(member.getSimpleName().toString())) {
                Object result = RhinoVarPort.denotable(member.asType());
                if (!expected.get(member.getSimpleName().toString()).equals(result)) throw new AssertionError(result);
            }
        }
        Trees trees = Trees.instance(task);
        final boolean[] rejected = {false};
        for (com.sun.source.tree.CompilationUnitTree unit : units) new TreePathScanner<Void, Void>() {
            @Override public Void visitVariable(com.sun.source.tree.VariableTree variable, Void unused) {
                if (variable.getName().contentEquals("a")) {
                    try { RhinoVarPort.denotable(trees.getElement(getCurrentPath()).asType()); }
                    catch (IllegalArgumentException failure) { rejected[0] = true; }
                }
                return super.visitVariable(variable, unused);
            }
        }.scan(unit, null);
        if (!rejected[0]) throw new AssertionError("Anonymous inferred type was not rejected");
        System.out.println("PASS real javac TypeMirror rendering and anonymous fail-closed fixture");
    }
}
