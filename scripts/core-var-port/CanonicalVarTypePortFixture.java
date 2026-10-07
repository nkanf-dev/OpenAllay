package dev.openallay.build;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.*;

/** Genuine public-compiler and separate-process Java8 oracle. No project stubs. */
public final class CanonicalVarTypePortFixture {
    private static final String SOURCE = """
            import java.util.*;
            public class DispatchFixture<T> {
                static String choose(Object x) { return "object"; }
                static String choose(List<String> x) { return "list"; }
                static class Outer<U> { class Inner<V> { String label(){return "inner";} } }
                static <X> X identity(X value) { var same = value; return same; }
                public static void main(String[] args) {
                    // Supplementary text precedes the declaration: 🐝
                    var /* inferred, not a lexical guess */ list = new ArrayList<String>();
                    list.add("a");
                    var alias = list;
                    alias.add("b");
                    List<? extends Number> numbers = Arrays.<Integer>asList(7, 9);
                    var wildcard = numbers;
                    var array = new String[]{"x", "y"};
                    var primitive = 2;
                    class Local { String label(){return "local";} }
                    var local = new Local();
                    var nested = new Outer<String>().new Inner<Integer>();
                    final var immutable = identity("identity");
                    int sum = 0;
                    for (var number : wildcard) sum += number.intValue();
                    for (var i = 0; i < primitive; i++) sum += i;
                    java.util.function.Function<String,String> lambda = value -> value;
                    System.out.println(choose(alias)+":"+list.size()+":"+array[1]+":"+local.label()
                        +":"+nested.label()+":"+immutable+":"+sum+":"+lambda.apply("lambda"));
                }
            }
            """;
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void compile(Path source, Path classes, String release) throws Exception {
        Files.createDirectories(classes);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            boolean success = compiler.getTask(null, manager, diagnostics,
                    List.of("--release", release, "-proc:none", "-encoding", "UTF-8", "-d", classes.toString()),
                    null, manager.getJavaFileObjects(source.toFile())).call();
            check(success, "Compiler oracle failed release" + release + ": " + diagnostics.getDiagnostics());
        }
    }
    private static String run(String java, Path classes) throws Exception {
        Process child = new ProcessBuilder(java, "-cp", classes.toString(), "DispatchFixture").redirectErrorStream(true).start();
        String output = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        check(child.waitFor() == 0, "Runtime oracle failed: " + output);
        return output.replace("\r\n", "\n");
    }
    private static final class Source extends SimpleJavaFileObject {
        final String text;
        Source(String text) { super(URI.create("string:///MirrorFixture.java"), Kind.SOURCE); this.text = text; }
        @Override public CharSequence getCharContent(boolean ignore) { return text; }
    }
    private static void reject(TypeMirror mirror, String kind) {
        try { AttributedVarTypes.denotable(mirror, true); throw new AssertionError("Accepted " + kind + ": " + mirror); }
        catch (IllegalArgumentException expected) { }
    }
    private static void mirrorOracle() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            String source = "class MirrorFixture<T extends Number & Runnable> { java.util.List<? extends Number> wildcard; Missing missing; "
                    + "void test(){ var anonymous=new Runnable(){public void run(){}}; "
                    + "try { if(System.nanoTime()==0) throw new java.io.IOException(); else throw new java.sql.SQLException(); } "
                    + "catch(java.io.IOException | java.sql.SQLException failure){} } }";
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("--release","17","-proc:none"), null, List.of(new Source(source)));
            List<CompilationUnitTree> units = new ArrayList<>(); task.parse().forEach(units::add); task.analyze();
            TypeElement fixture = task.getElements().getTypeElement("MirrorFixture");
            TypeParameterElement parameter = fixture.getTypeParameters().get(0);
            reject(((TypeVariable)parameter.asType()).getUpperBound(), "intersection");
            check(AttributedVarTypes.denotable(parameter.asType()).equals("T"), "Named type parameter differs");
            for (Element member : fixture.getEnclosedElements()) {
                if (member.getSimpleName().contentEquals("wildcard")) {
                    DeclaredType captured = (DeclaredType)task.getTypes().capture(member.asType());
                    reject(captured.getTypeArguments().get(0), "capture");
                    check(AttributedVarTypes.denotable(member.asType()).equals("java.util.List<? extends java.lang.Number>"), "Wildcard differs");
                }
                if (member.getSimpleName().contentEquals("missing")) reject(member.asType(), "error");
            }
            Trees trees = Trees.instance(task); final int[] rejected = {0};
            for (CompilationUnitTree unit : units) new TreePathScanner<Void, Void>() {
                @Override public Void visitVariable(VariableTree variable, Void unused) {
                    if (variable.getName().contentEquals("anonymous")) {
                        reject(trees.getElement(getCurrentPath()).asType(), "anonymous"); rejected[0]++;
                    }
                    if (variable.getType() instanceof UnionTypeTree) {
                        reject(trees.getTypeMirror(new TreePath(getCurrentPath(), variable.getType())), "union"); rejected[0]++;
                    }
                    return super.visitVariable(variable, unused);
                }
            }.scan(unit, null);
            check(rejected[0] == 2, "Anonymous/union mirror checks missing");
            check(diagnostics.getDiagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR), "Missing-type error oracle did not fail");
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("freshFixtureRoot modernJava realJava8");
        Process runtimeVersion = new ProcessBuilder(args[2], "-version").redirectErrorStream(true).start();
        String version = new String(runtimeVersion.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        check(runtimeVersion.waitFor() == 0 && version.contains("version \"1.8."), "Require real Java8 runtime: " + version);
        Path root = Paths.get(args[0]); Files.createDirectory(root);
        Path original = root.resolve("original"); Files.createDirectory(original);
        Path source = original.resolve("DispatchFixture.java"); Files.writeString(source, SOURCE, StandardCharsets.UTF_8);
        CanonicalVarTypePort.Result result = CanonicalVarTypePort.attribute(original, "", Set.of("DispatchFixture.java"));
        check(result.rejected().isEmpty(), "Rejected visible fixture types: " + result.rejected());
        Map<String,String> expected = new TreeMap<>();
        expected.put("same","X"); expected.put("list","java.util.ArrayList<java.lang.String>");
        expected.put("alias","java.util.ArrayList<java.lang.String>"); expected.put("wildcard","java.util.List<? extends java.lang.Number>");
        expected.put("array","java.lang.String[]"); expected.put("primitive","int"); expected.put("local","Local");
        expected.put("nested","DispatchFixture.Outer<java.lang.String>.Inner<java.lang.Integer>");
        expected.put("immutable","java.lang.String"); expected.put("number","java.lang.Number"); expected.put("i","int");
        check(result.sites().size() == expected.size(), "Parsed VAR count/lambda exclusion differs: " + result);
        String converted = SOURCE;
        List<CanonicalVarTypePort.Site> reversed = new ArrayList<>(result.sites());
        reversed.sort(Comparator.comparingLong(CanonicalVarTypePort.Site::start).reversed());
        for (CanonicalVarTypePort.Site site : reversed) {
            check(Objects.equals(expected.get(site.variable()),site.type()), "Exact inferred type differs: " + site);
            int start = Math.toIntExact(site.start());
            check(converted.substring(start, start+3).equals("var"), "UTF16 offset differs");
            converted = converted.substring(0,start) + site.type() + converted.substring(start+3);
        }
        Path explicit = root.resolve("explicit"); Files.createDirectory(explicit);
        Path after = explicit.resolve("DispatchFixture.java"); Files.writeString(after, converted, StandardCharsets.UTF_8);
        CanonicalVarTypePort.Result post = CanonicalVarTypePort.attribute(explicit, "", Set.of("DispatchFixture.java"));
        check(post.sites().isEmpty() && post.rejected().isEmpty(), "Converted source retains VAR");
        Path modern = root.resolve("modern-classes"); compile(source,modern,"17");
        Path java8 = root.resolve("java8-classes"); compile(after,java8,"8");
        String wanted = "list:2:y:local:inner:identity:17:lambda\n";
        check(run(args[1],modern).equals(wanted), "Original alias/dispatch oracle differs");
        check(run(args[2],java8).equals(wanted), "Converted Java8 alias/dispatch oracle differs");
        Path bad = root.resolve("anonymous"); Files.createDirectory(bad);
        Files.writeString(bad.resolve("Anonymous.java"), "class Anonymous { void test(){ var a = new Runnable(){public void run(){}}; } }", StandardCharsets.UTF_8);
        CanonicalVarTypePort.Result refused = CanonicalVarTypePort.attribute(bad,"",Set.of("Anonymous.java"));
        check(refused.sites().isEmpty() && refused.rejected().values().stream().mapToInt(Integer::intValue).sum() == 1, "Anonymous fail-closed count differs");
        Path hidden = root.resolve("hidden"); Files.createDirectory(hidden);
        Files.writeString(hidden.resolve("Hidden.java"), "class Owner { private static class Secret {} static Secret value(){return new Secret();} } "
                + "class Hidden { void test(){var secret=Owner.value();} }", StandardCharsets.UTF_8);
        CanonicalVarTypePort.Result inaccessible = CanonicalVarTypePort.attribute(hidden,"",Set.of("Hidden.java"));
        check(inaccessible.sites().isEmpty() && inaccessible.rejected().values().stream().mapToInt(Integer::intValue).sum() == 1,
                "Inaccessible nested type fail-closed count differs");
        Path escaped = root.resolve("escaped"); Files.createDirectory(escaped);
        Files.writeString(escaped.resolve("Escaped.java"), "class Escaped { void test(){ v"+"\\u0061"+"r value=1; } }", StandardCharsets.UTF_8);
        try { CanonicalVarTypePort.attribute(escaped,"",Set.of("Escaped.java")); throw new AssertionError("Escaped token accepted"); }
        catch (IllegalArgumentException expectedFailure) { }
        mirrorOracle();
        System.out.println("PASS genuine public compiler oracle: original17=explicit8, exact types, aliases/dispatch, wildcard/array/local/nested/generic, UTF16, rejected capture/anonymous/error/intersection/union");
    }
}
