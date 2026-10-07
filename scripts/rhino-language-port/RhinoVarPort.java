package dev.openallay.tools.rhino;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.tools.*;

/** Build-only public javac attribution. No runtime loading or altered engine classes. */
public final class RhinoVarPort {
    private static final class Site {
        final CompilationUnitTree unit; final VariableTree variable; final long start;
        Site(CompilationUnitTree unit, VariableTree variable, long start) {
            this.unit = unit; this.variable = variable; this.start = start;
        }
    }
    static String denotable(TypeMirror type) {
        return type.accept(new SimpleTypeVisitor8<String, Void>() {
            @Override protected String defaultAction(TypeMirror t, Void unused) {
                throw new IllegalArgumentException("Unsupported inferred type " + t.getKind() + ": " + t);
            }
            @Override public String visitPrimitive(PrimitiveType t, Void unused) { return t.toString(); }
            @Override public String visitArray(ArrayType t, Void unused) { return denotable(t.getComponentType()) + "[]"; }
            @Override public String visitDeclared(DeclaredType t, Void unused) {
                TypeElement element = (TypeElement) t.asElement();
                String name = element.getQualifiedName().toString();
                if (name.isEmpty() || element.getNestingKind() == NestingKind.ANONYMOUS || element.getNestingKind() == NestingKind.LOCAL) {
                    throw new IllegalArgumentException("Non-denotable declaration: " + t);
                }
                StringBuilder result = new StringBuilder();
                if (t.getEnclosingType().getKind() == TypeKind.DECLARED && !element.getModifiers().contains(Modifier.STATIC)) {
                    result.append(denotable(t.getEnclosingType())).append('.').append(element.getSimpleName());
                } else { result.append(name); }
                if (!t.getTypeArguments().isEmpty()) {
                    result.append('<');
                    for (int i = 0; i < t.getTypeArguments().size(); i++) {
                        if (i > 0) result.append(", ");
                        result.append(denotable(t.getTypeArguments().get(i)));
                    }
                    result.append('>');
                }
                return result.toString();
            }
            @Override public String visitTypeVariable(TypeVariable t, Void unused) {
                String name = t.asElement().getSimpleName().toString();
                if (!name.matches("[A-Za-z_$][A-Za-z0-9_$]*") || name.startsWith("capture")) {
                    throw new IllegalArgumentException("Captured variable: " + t);
                }
                return name;
            }
            @Override public String visitWildcard(WildcardType t, Void unused) {
                if (t.getExtendsBound() != null) return "? extends " + denotable(t.getExtendsBound());
                if (t.getSuperBound() != null) return "? super " + denotable(t.getSuperBound());
                return "?";
            }
        }, null);
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("generatedJavaRoot classpath selectedPaths outputTSV");
        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        Set<String> selected = new HashSet<>(Files.readAllLines(Paths.get(args[2]), StandardCharsets.UTF_8));
        List<File> files = new ArrayList<>();
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> files.add(p.toFile()));
        }
        if (files.size() != 277) throw new IllegalArgumentException("Expected277canonical sources, got " + files.size());
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("Require genuine JDK javac tool");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                Arrays.asList("--release", "17", "-proc:none", "-classpath", args[1]), null,
                manager.getJavaFileObjectsFromFiles(files));
            List<CompilationUnitTree> units = new ArrayList<>(); task.parse().forEach(units::add);
            Trees trees = Trees.instance(task); SourcePositions positions = trees.getSourcePositions();
            List<Site> sites = new ArrayList<>();
            for (CompilationUnitTree unit : units) {
                Path path = Paths.get(unit.getSourceFile().toUri());
                String relative = root.relativize(path).toString().replace(File.separatorChar, '/');
                if (!selected.contains(relative)) continue;
                String source = unit.getSourceFile().getCharContent(true).toString();
                new TreePathScanner<Void, Void>() {
                    @Override public Void visitVariable(VariableTree variable, Void unused) {
                        long start = positions.getStartPosition(unit, variable);
                        long end = positions.getEndPosition(unit, variable);
                        if (start >= 0 && end >= start) {
                            String prefix = source.substring((int) start, (int) end);
                            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(?:final\\s+)?var\\s+(?=" + java.util.regex.Pattern.quote(variable.getName().toString()) + "\\b)").matcher(prefix);
                            if (matcher.find()) {
                                int varAt = prefix.indexOf("var"); sites.add(new Site(unit, variable, start + varAt));
                            }
                        }
                        return super.visitVariable(variable, unused);
                    }
                }.scan(unit, null);
            }
            task.analyze();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) throw new IllegalStateException("Attribution failed: " + diagnostic);
            }
            List<String> output = new ArrayList<>();
            for (Site site : sites) {
                Element element = trees.getElement(TreePath.getPath(site.unit, site.variable));
                if (!(element instanceof VariableElement)) throw new IllegalStateException("Missing variable element");
                String type = denotable(element.asType());
                String path = root.relativize(Paths.get(site.unit.getSourceFile().toUri())).toString().replace(File.separatorChar, '/');
                output.add(path + "\t" + site.start + "\t" + Base64.getEncoder().encodeToString(type.getBytes(StandardCharsets.UTF_8)));
            }
            Collections.sort(output); Files.write(Paths.get(args[3]), output, StandardCharsets.UTF_8);
            System.out.println("PASS canonical javac var attribution: sites=" + sites.size() + " sources=" + files.size());
        }
    }
}
