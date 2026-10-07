package dev.openallay.build;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.*;

/** Review-only canonical CORE var attribution using the public compiler API. */
public final class CanonicalVarTypePort {
    public record Site(String path, long start, String variable, String type) {}
    public record Result(List<Site> sites, Map<String, Integer> parsedCounts,
                         int sources, int allParsedSites, Map<String, Integer> rejected) {}
    private record Candidate(CompilationUnitTree unit, TreePath path, long start,
                             String relative, String variable) {}

    private CanonicalVarTypePort() {}

    /** javac clears the parsed inferred type; implicit lambda parameters are not VAR locals. */
    private static boolean inferredLocal(VariableTree variable, TreePath path) {
        Tree type = variable.getType();
        if (type != null && !(type instanceof IdentifierTree id && id.getName().contentEquals("var"))) return false;
        Tree parent = path.getParentPath().getLeaf();
        return parent instanceof BlockTree || parent instanceof ForLoopTree
                || parent instanceof EnhancedForLoopTree || parent instanceof TryTree
                || parent instanceof CaseTree;
    }

    /** Locate only an AST-selected declaration token. Offsets are Java UTF16 units. */
    private static long tokenStart(String source, long begin, long end, String variable) {
        if (begin < 0 || end < begin || end > source.length()) throw new IllegalArgumentException("Invalid AST span");
        List<String> tokens = new ArrayList<>();
        List<Integer> offsets = new ArrayList<>();
        for (int i = (int) begin; i < end;) {
            char ch = source.charAt(i);
            if (Character.isWhitespace(ch)) { i++; continue; }
            if (ch == '/' && i + 1 < end && source.charAt(i + 1) == '/') {
                i += 2; while (i < end && source.charAt(i) != '\n' && source.charAt(i) != '\r') i++;
                continue;
            }
            if (ch == '/' && i + 1 < end && source.charAt(i + 1) == '*') {
                int close = source.indexOf("*/", i + 2);
                if (close < 0 || close + 2 > end) throw new IllegalArgumentException("Unclosed comment in declaration");
                i = close + 2; continue;
            }
            if (ch == '\\') throw new IllegalArgumentException("Unicode-escaped declaration token is not materialized");
            if (ch == '"' || ch == '\'') {
                char quote = ch; i++;
                while (i < end) { char next = source.charAt(i++); if (next == '\\') i++; else if (next == quote) break; }
                tokens.add("<literal>"); offsets.add(i); continue;
            }
            int start = i;
            if (Character.isJavaIdentifierStart(ch)) {
                i++; while (i < end && Character.isJavaIdentifierPart(source.charAt(i))) i++;
            } else i++;
            tokens.add(source.substring(start, i)); offsets.add(start);
            int n = tokens.size();
            if (n > 1 && tokens.get(n - 2).equals("var") && tokens.get(n - 1).equals(variable)) {
                return offsets.get(n - 2);
            }
        }
        throw new IllegalArgumentException("Parsed VAR has no exact source token for " + variable);
    }

    /** Prove local names against the original AST, not re-attributed scope symbols. */
    private static void localVisible(TypeElement element, TreePath use, Trees trees) {
        TreePath declaration = trees.getPath(element);
        if (declaration == null || !(declaration.getLeaf() instanceof ClassTree)) {
            throw new IllegalArgumentException("Local declaration has no source tree: " + element);
        }
        Tree wanted = declaration.getLeaf();
        String name = element.getSimpleName().toString();
        Tree child = use.getLeaf();
        for (TreePath cursor = use.getParentPath(); cursor != null; cursor = cursor.getParentPath()) {
            Tree enclosing = cursor.getLeaf();
            if (enclosing instanceof BlockTree block) {
                ClassTree matching = null;
                boolean reached = false;
                for (StatementTree statement : block.getStatements()) {
                    if (statement instanceof ClassTree local && local.getSimpleName().contentEquals(name)) matching = local;
                    if (statement == child) { reached = true; break; }
                }
                if (!reached) throw new IllegalArgumentException("Local use has no containing block statement: " + element);
                if (matching != null) {
                    if (matching == wanted) return;
                    throw new IllegalArgumentException("Local type name is shadowed at declaration: " + element);
                }
            } else if (enclosing instanceof ClassTree klass) {
                if (klass.getSimpleName().contentEquals(name)) {
                    if (klass == wanted) return;
                    throw new IllegalArgumentException("Local type name is shadowed by enclosing class: " + element);
                }
                for (TypeParameterTree parameter : klass.getTypeParameters()) {
                    if (parameter.getName().contentEquals(name)) throw new IllegalArgumentException("Local type name is shadowed by type parameter: " + element);
                }
                for (Tree member : klass.getMembers()) {
                    if (member instanceof ClassTree nested && nested.getSimpleName().contentEquals(name))
                        throw new IllegalArgumentException("Local type name is shadowed by member class: " + element);
                }
            } else if (enclosing instanceof MethodTree method) {
                for (TypeParameterTree parameter : method.getTypeParameters()) {
                    if (parameter.getName().contentEquals(name)) throw new IllegalArgumentException("Local type name is shadowed by method parameter: " + element);
                }
            }
            child = enclosing;
        }
        throw new IllegalArgumentException("Local type not visible at declaration: " + element);
    }

    private static void visible(TypeMirror type, Scope scope, TreePath use, Trees trees) {
        switch (type.getKind()) {
            case ARRAY -> visible(((ArrayType) type).getComponentType(), scope, use, trees);
            case DECLARED -> {
                DeclaredType declaration = (DeclaredType) type;
                TypeElement element = (TypeElement) declaration.asElement();
                if (element.getNestingKind() == NestingKind.LOCAL) {
                    localVisible(element, use, trees);
                } else if (element.getNestingKind() != NestingKind.ANONYMOUS && !trees.isAccessible(scope, element)) {
                    throw new IllegalArgumentException("Inaccessible inferred declaration: " + element);
                }
                if (declaration.getEnclosingType().getKind() == TypeKind.DECLARED) visible(declaration.getEnclosingType(), scope, use, trees);
                for (TypeMirror argument : declaration.getTypeArguments()) visible(argument, scope, use, trees);
            }
            case WILDCARD -> {
                WildcardType wildcard = (WildcardType) type;
                if (wildcard.getExtendsBound() != null) visible(wildcard.getExtendsBound(), scope, use, trees);
                if (wildcard.getSuperBound() != null) visible(wildcard.getSuperBound(), scope, use, trees);
            }
            default -> { /* The shared renderer rejects other non-denotable forms. */ }
        }
    }

    public static Result attribute(Path sourceRoot, String classpath, Set<String> selected) throws Exception {
        Path root = sourceRoot.toAbsolutePath().normalize();
        List<File> files = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            paths.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> files.add(p.toFile()));
        }
        if (files.isEmpty() || selected.isEmpty()) throw new IllegalArgumentException("Empty source closure or selection");
        for (String name : selected) {
            Path path = root.resolve(name).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path) || !name.endsWith(".java"))
                throw new IllegalArgumentException("Invalid selected owner " + name);
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("Require genuine full JDK javac");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    Arrays.asList("--release", "17", "-proc:none", "-encoding", "UTF-8", "-classpath", classpath),
                    null, manager.getJavaFileObjectsFromFiles(files));
            List<CompilationUnitTree> units = new ArrayList<>();
            task.parse().forEach(units::add);
            Trees trees = Trees.instance(task);
            SourcePositions positions = trees.getSourcePositions();
            List<Candidate> candidates = new ArrayList<>();
            Map<String, Integer> counts = new TreeMap<>();
            for (String name : selected) counts.put(name, 0);
            final int[] allSites = {0};
            for (CompilationUnitTree unit : units) {
                String relative = root.relativize(Paths.get(unit.getSourceFile().toUri())).toString().replace(File.separatorChar, '/');
                String source = unit.getSourceFile().getCharContent(true).toString();
                new TreePathScanner<Void, Void>() {
                    @Override public Void visitVariable(VariableTree variable, Void unused) {
                        if (inferredLocal(variable, getCurrentPath())) {
                            allSites[0]++;
                            if (selected.contains(relative)) {
                                long start = positions.getStartPosition(unit, variable);
                                long end = positions.getEndPosition(unit, variable);
                                if (variable.getInitializer() != null) end = positions.getStartPosition(unit, variable.getInitializer());
                                long token = tokenStart(source, start, end, variable.getName().toString());
                                candidates.add(new Candidate(unit, getCurrentPath(), token, relative, variable.getName().toString()));
                                counts.merge(relative, 1, Integer::sum);
                            }
                        }
                        return super.visitVariable(variable, unused);
                    }
                }.scan(unit, null);
            }
            task.analyze();
            List<String> errors = new ArrayList<>();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) errors.add(diagnostic.toString());
            }
            if (!errors.isEmpty()) throw new IllegalStateException("All-source attribution failed (" + errors.size() + "): " + String.join("\n", errors));
            List<Site> sites = new ArrayList<>();
            Map<String, Integer> rejected = new TreeMap<>();
            for (Candidate candidate : candidates) {
                try {
                    Element element = trees.getElement(candidate.path());
                    if (!(element instanceof VariableElement)) throw new IllegalArgumentException("Missing variable element");
                    TypeMirror type = element.asType();
                    visible(type, trees.getScope(candidate.path()), candidate.path(), trees);
                    String rendered = AttributedVarTypes.denotable(type, true);
                    sites.add(new Site(candidate.relative(), candidate.start(), candidate.variable(), rendered));
                } catch (IllegalArgumentException failure) {
                    rejected.merge(failure.getMessage(), 1, Integer::sum);
                }
            }
            sites.sort(Comparator.comparing(Site::path).thenComparingLong(Site::start));
            return new Result(List.copyOf(sites), Collections.unmodifiableMap(counts), files.size(), allSites[0], Collections.unmodifiableMap(rejected));
        }
    }

    private static String json(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> out.append("\\\\"); case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
                default -> { if (c < 32) out.append(String.format("\\u%04x", (int) c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }
    private static String jsonCounts(Map<String, Integer> counts) {
        List<String> pairs = new ArrayList<>();
        counts.forEach((name, count) -> pairs.add(json(name) + ":" + count));
        return "{" + String.join(",", pairs) + "}";
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("canonicalSourceRoot classpath selectedPaths outputTSV reportJSON");
        List<String> paths = Files.readAllLines(Paths.get(args[2]), StandardCharsets.UTF_8);
        Set<String> selected = new HashSet<>(paths);
        if (selected.size() != paths.size() || selected.contains("")) throw new IllegalArgumentException("Duplicate/empty selected owner");
        Result result = attribute(Paths.get(args[0]), args[1], selected);
        String report = "{\"sources\":" + result.sources() + ",\"allParsedVarSites\":" + result.allParsedSites()
                + ",\"selectedParsedCounts\":" + jsonCounts(result.parsedCounts())
                + ",\"rejectedForms\":" + jsonCounts(result.rejected())
                + ",\"convertedSites\":" + result.sites().size() + "}\n";
        Files.writeString(Paths.get(args[4]), report, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        if (!result.rejected().isEmpty()) throw new IllegalStateException("Non-denotable selected types; see exact rejection counts in report");
        List<String> output = new ArrayList<>();
        for (Site site : result.sites()) output.add(site.path() + "\t" + site.start() + "\t"
                + Base64.getEncoder().encodeToString(site.type().getBytes(StandardCharsets.UTF_8)) + "\t" + site.variable());
        Files.write(Paths.get(args[3]), output, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        System.out.println("PASS public all-source CORE attribution: sources=" + result.sources() + " selectedSites=" + result.sites().size());
    }
}
