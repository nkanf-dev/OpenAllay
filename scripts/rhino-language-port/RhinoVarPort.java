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
        return dev.openallay.build.AttributedVarTypes.denotable(type);
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
