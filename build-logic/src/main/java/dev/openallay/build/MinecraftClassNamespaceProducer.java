package dev.openallay.build;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import com.sun.source.util.SourcePositions;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.Elements;
import javax.tools.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/** Build-only, class-identity-only compile view. It does not attribute canonical game source. */
public final class MinecraftClassNamespaceProducer {
    public enum InputMode { MOJANG_CANONICAL, CANONICAL_SEMANTIC, ACTUAL_MCP }
    public record Unit(String owner, String logicalPath, Path file, InputMode mode) {}
    public record MappingInput(Path file, String sha256) {}
    public record Request(List<Unit> outputUnits, List<Unit> symbolUnits,
            MappingInput client, MappingInput server, MappingInput tsrg,
            List<Path> metadataClasspath, String syntaxLevel, boolean preview,
            Path metadataAcceptance, List<Path> provenanceInputs, Path output, Path receipt,
            MappingInput curatedClasses) {
        public Request(List<Unit> outputUnits, List<Unit> symbolUnits,
                MappingInput client, MappingInput server, MappingInput tsrg,
                List<Path> metadataClasspath, String syntaxLevel, boolean preview,
                Path metadataAcceptance, List<Path> provenanceInputs, Path output, Path receipt) {
            this(outputUnits, symbolUnits, client, server, tsrg, metadataClasspath, syntaxLevel,
                    preview, metadataAcceptance, provenanceInputs, output, receipt, null);
        }
    }
    private record Edit(int start, int end, String oldText, String replacement, String role) {}
    private record NativeType(String official, String actualBinary, String actualSource) {}
    private record Parsed(Unit unit, String text, CompilationUnitTree tree, SourcePositions positions) {}
    private record NativeIdentity(String role, String originalBinary, String actualBinary) {}
    private record Result(Unit unit, String originalHash, String text, List<Edit> edits, List<NativeIdentity> identities) {}

    private MinecraftClassNamespaceProducer() {}
    public static void produce(Request request) throws Exception {

        Path output = request.output().toAbsolutePath().normalize();
        Path receipt = request.receipt().toAbsolutePath().normalize();
        if (receipt.startsWith(output)) fail("Receipt must be outside generated Java root");
        List<Path> immutableInputs = new ArrayList<>(request.metadataClasspath());
        immutableInputs.addAll(request.provenanceInputs());
        if (request.metadataAcceptance() != null) immutableInputs.add(request.metadataAcceptance());
        for (Unit unit : concat(request.outputUnits(), request.symbolUnits())) immutableInputs.add(unit.file());
        if (request.curatedClasses() == null) {
            immutableInputs.add(request.client().file()); immutableInputs.add(request.server().file()); immutableInputs.add(request.tsrg().file());
        } else {
            if (request.client() != null || request.server() != null || request.tsrg() != null)
                fail("Curated classes cannot carry official/obfuscated mapping inputs");
            immutableInputs.add(request.curatedClasses().file());
            if (concat(request.outputUnits(), request.symbolUnits()).stream().anyMatch(u -> u.mode() == InputMode.MOJANG_CANONICAL))
                fail("Curated classes require CANONICAL_SEMANTIC or ACTUAL_MCP unit mode");
        }
        preflightPaths(output, receipt, immutableInputs);
        Files.deleteIfExists(receipt); // Safe preflight precedes deletion; failures retain no success receipt.
        if (request.metadataAcceptance() == null) fail("Actual-classpath metadata acceptance receipt is required");
        NamespaceMetadataAcceptance.verifyReceipt(request.metadataAcceptance(), request.metadataClasspath());
        if (request.outputUnits().isEmpty()) fail("No selected output units");
        List<Unit> universe = mergeUnits(request.outputUnits(), request.symbolUnits());
        Mapping mapping = request.curatedClasses() == null
                ? Mapping.load(request.client(), request.server(), request.tsrg())
                : Mapping.loadCurated(request.curatedClasses());
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) fail("A full tooling JDK with jdk.compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8);
             Metadata metadata = new Metadata(request.metadataClasspath())) {
            if (request.curatedClasses() != null) {
                for (String actual : mapping.actualToOfficial.keySet()) metadata.binaryType(actual);
            }
            List<JavaFileObject> files = new ArrayList<>();
            Map<String, Unit> byUri = new HashMap<>();
            for (Unit unit : universe) {
                String text = Files.readString(unit.file(), StandardCharsets.UTF_8);
                JavaFileObject object = new SimpleJavaFileObject(unit.file().toUri(), JavaFileObject.Kind.SOURCE) {
                    @Override public CharSequence getCharContent(boolean ignore) { return text; }
                };
                files.add(object);
                if (byUri.put(object.toUri().toString(), unit) != null) fail("Duplicate physical source input");
            }
            List<String> options = new ArrayList<>(List.of("-proc:none", "--source", request.syntaxLevel()));
            if (request.preview()) options.add("--enable-preview");
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics, options, null, files);
            Trees trees = Trees.instance(task);
            List<Parsed> parsed = new ArrayList<>();
            for (CompilationUnitTree tree : task.parse()) {
                Unit unit = byUri.get(tree.getSourceFile().toUri().toString());
                parsed.add(new Parsed(unit, Files.readString(unit.file(), StandardCharsets.UTF_8), tree, trees.getSourcePositions()));
            }
            rejectErrors(diagnostics, "canonical parse");
            Index index = new Index(parsed, mapping, metadata);
            List<Result> results = new ArrayList<>();
            List<Parsed> generated = new ArrayList<>();
            Map<String, List<NativeIdentity>> originalBindings = new HashMap<>();
            Set<Path> outputs = request.outputUnits().stream().map(u -> u.file().toAbsolutePath().normalize()).collect(Collectors.toSet());
            List<String> unitFailures = new ArrayList<>();
            for (Parsed unit : parsed) {
                try {
                Binder binder = new Binder(unit, index, mapping, metadata);
                binder.scan(unit.tree(), null);
                String generatedText = binder.apply();
                originalBindings.put(unit.unit().logicalPath(), List.copyOf(binder.nativeIdentities));
                Unit actualUnit = new Unit(unit.unit().owner(), unit.unit().logicalPath(), unit.unit().file(), InputMode.ACTUAL_MCP);
                generated.add(reparse(compiler, manager, actualUnit, generatedText, options));
                if (outputs.contains(unit.unit().file().toAbsolutePath().normalize())) {
                    results.add(new Result(unit.unit(), hash(unit.text()), generatedText, binder.edits(), List.copyOf(binder.nativeIdentities)));
                }
                } catch (IllegalStateException failure) {
                    unitFailures.add(unit.unit().logicalPath() + ": " + failure.getMessage());
                }
            }
            if (!unitFailures.isEmpty()) fail("Selected native source failures (no output emitted):\n" + String.join("\n", unitFailures));
            Index destinationIndex = new Index(generated, mapping, metadata);
            if (!index.classes.keySet().equals(destinationIndex.classes.keySet())) fail("Generated declaration ownership changed");
            for (int i = 0; i < parsed.size(); i++) {
                if (!declarationNames(parsed.get(i).tree()).equals(declarationNames(generated.get(i).tree()))) fail("Declared class/member/local names changed");
            }
            for (Parsed actual : generated) {
                Binder destinationAudit = new Binder(actual, destinationIndex, mapping, metadata);
                destinationAudit.scan(actual.tree(), null);
                destinationAudit.apply();
                if (!destinationAudit.edits().isEmpty()) fail("Destination-side audit would alter actual native identities: " + actual.unit().logicalPath());
                Map<String, Long> before = identityCounts(originalBindings.get(actual.unit().logicalPath()));
                Map<String, Long> after = identityCounts(destinationAudit.nativeIdentities);
                if (!before.equals(after)) fail("Native reference role/identity inventory changed: " + actual.unit().logicalPath());
            }
            // Validate every unit before touching the old tree. Compile tasks depend on this invocation.
            Path stage = output.resolveSibling(output.getFileName() + ".stage");
            clearOwnedDirectory(stage);
            Files.createDirectories(stage);
            try {
                for (Result result : results) {
                    Path destination = stage.resolve(result.unit().logicalPath()).normalize();
                    if (!destination.startsWith(stage)) fail("Generated path escapes owner root");
                    Files.createDirectories(destination.getParent());
                    Files.writeString(destination, result.text(), StandardCharsets.UTF_8);
                }
                clearOwnedDirectory(output);
                Files.createDirectories(output.getParent());
                try { Files.move(stage, output, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException unsupported) { Files.move(stage, output); }
                Files.createDirectories(receipt.getParent());
                Path receiptStage = receipt.resolveSibling(receipt.getFileName() + ".stage");
                Files.deleteIfExists(receiptStage);
                Files.writeString(receiptStage, receipt(request, mapping, results), StandardCharsets.UTF_8);
                try { Files.move(receiptStage, receipt, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException unsupported) { Files.move(receiptStage, receipt); }
            } catch (Exception failure) {
                Files.deleteIfExists(receipt);
                Files.deleteIfExists(receipt.resolveSibling(receipt.getFileName() + ".stage"));
                clearOwnedDirectory(stage);
                throw failure;
            }
        }
    }

    private static List<Unit> mergeUnits(List<Unit> outputs, List<Unit> symbols) throws IOException {
        Map<Path, Unit> paths = new LinkedHashMap<>();
        Map<String, Path> logical = new HashMap<>();
        for (Unit unit : concat(outputs, symbols)) {
            Path path = unit.file().toRealPath();
            if (Files.isSymbolicLink(unit.file()) || !Files.isRegularFile(path)) fail("Not an ordinary source file: " + unit.file());
            if (!unit.logicalPath().endsWith(".java") || unit.logicalPath().startsWith("/")
                    || unit.logicalPath().contains("\\") || Arrays.asList(unit.logicalPath().split("/")).contains("..")) fail("Unsafe logical path");
            Unit previous = paths.putIfAbsent(path, unit);
            if (previous != null && (!previous.owner().equals(unit.owner()) || !previous.logicalPath().equals(unit.logicalPath()) || previous.mode() != unit.mode())) fail("Competing source owner: " + path);
            String key = unit.logicalPath().toLowerCase(Locale.ROOT);
            Path old = logical.putIfAbsent(key, path);
            if (old != null && !old.equals(path)) fail("Logical path/case collision: " + unit.logicalPath());
        }
        return new ArrayList<>(paths.values());
    }
    private static <T> List<T> concat(List<T> a, List<T> b) { List<T> c = new ArrayList<>(a); c.addAll(b); return c; }

    private static final class Mapping {
        final Map<String, String> officialToActual;
        final Set<String> unmapped;
        final Map<String, String> actualToOfficial;
        final boolean curated;
        Mapping(Map<String, String> joined, Set<String> absent) { this(joined, absent, false); }
        Mapping(Map<String, String> joined, Set<String> absent, boolean curated) {
            this.curated = curated;
            officialToActual = Map.copyOf(joined); unmapped = Set.copyOf(absent);
            Map<String, String> reverse = new HashMap<>();
            joined.forEach((official, actual) -> {
                String old = reverse.putIfAbsent(actual, official);
                if (old != null && !old.equals(official)) fail("Mapping destination collision " + actual);
            });
            actualToOfficial = Map.copyOf(reverse);
        }
        // Explicit reviewed semantic class identity only. Never infer cross-version obfuscated joins.
        static Mapping loadCurated(MappingInput input) throws Exception {
            Map<String, String> classes = new TreeMap<>();
            for (String row : checked(input).split("\\R")) {
                if (row.isBlank() || row.startsWith("#")) continue;
                String[] cells = row.split("\t", -1);
                if (cells.length != 3 || cells[2].isBlank()
                        || !cells[0].matches("[A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)+")
                        || !cells[1].matches("net\\.minecraft\\.[A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)+"))
                    fail("Expected canonicalBinary TAB actualMcpBinary TAB reviewed semantic role: " + row);
                if (!nativeDomain(cells[0])) fail("Curated canonical class outside native domain " + cells[0]);
                if (classes.putIfAbsent(cells[0], cells[1]) != null) fail("Duplicate curated canonical class " + cells[0]);
            }
            if (classes.isEmpty()) fail("Empty curated class inventory");
            return new Mapping(classes, Set.of(), true);
        }
        static Mapping load(MappingInput client, MappingInput server, MappingInput tsrg) throws Exception {
            Map<String, String> classes = parseOfficial(checked(client));
            for (Map.Entry<String, String> entry : parseOfficial(checked(server)).entrySet()) {
                String old = classes.putIfAbsent(entry.getKey(), entry.getValue());
                if (old != null && !old.equals(entry.getValue())) fail("Client/server mapping identity conflict " + entry.getKey());
            }
            Map<String, String> obfuscated = new HashMap<>();
            Map<String, String> obfuscatedOwner = new HashMap<>();
            classes.forEach((name, identity) -> {
                String old = obfuscatedOwner.putIfAbsent(identity, name);
                if (old != null && !old.equals(name)) fail("Obfuscated identity collision " + identity);
            });
            for (String line : checked(tsrg).split("\\R")) {
                if (line.isBlank() || Character.isWhitespace(line.charAt(0))) continue;
                String[] columns = line.split("\\s+");
                if (columns.length != 2) fail("Expected two-column pinned TSRG class row: " + line);
                if (obfuscated.putIfAbsent(columns[0], columns[1].replace('/', '.')) != null) fail("Duplicate TSRG class");
            }
            Map<String, String> joined = new TreeMap<>(); Set<String> absent = new TreeSet<>();
            classes.forEach((name, identity) -> {
                String actual = obfuscated.get(identity);
                if (actual == null) absent.add(name); else joined.put(name, actual);
            });
            return new Mapping(joined, absent);
        }
        static Map<String, String> parseOfficial(String text) {
            Map<String, String> result = new HashMap<>();
            for (String line : text.split("\\R")) {
                if (line.isBlank() || line.charAt(0) == '#' || Character.isWhitespace(line.charAt(0))) continue;
                int arrow = line.indexOf(" -> ");
                if (arrow < 1 || !line.endsWith(":")) fail("Invalid official class row");
                String name = line.substring(0, arrow), identity = line.substring(arrow + 4, line.length() - 1).replace('.', '/');
                if (result.putIfAbsent(name, identity) != null) fail("Duplicate official class " + name);
            }
            return result;
        }
        String sourceIdentity(String spelling) {
            if (officialToActual.containsKey(spelling) || unmapped.contains(spelling)) return spelling;
            String found = null;
            for (String identity : officialToActual.keySet()) if (identity.replace('$', '.').equals(spelling)) {
                if (found != null && !found.equals(identity)) fail("Ambiguous canonical source nesting " + spelling);
                found = identity;
            }
            for (String identity : unmapped) if (identity.replace('$', '.').equals(spelling)) fail("Unmapped official native class " + identity);
            return found;
        }
        NativeType resolve(String canonicalSource, Metadata metadata) {
            String identity = sourceIdentity(canonicalSource);
            if (identity == null || unmapped.contains(identity)) fail("No exact class join for native type " + canonicalSource);
            String actual = officialToActual.get(identity);
            TypeElement element = metadata.binaryType(actual);
            if (!identity.equals(canonicalSource) && identity.contains("$") && element.getNestingKind() != NestingKind.MEMBER) fail("Source/binary nested class identity is not a real member class: " + identity);
            return new NativeType(identity, actual, element.getQualifiedName().toString());
        }
    }

    /** Queries only real classpath types. No canonical source attribution, class generation, or private APIs. */
    public static final class Metadata implements AutoCloseable {
        private final StandardJavaFileManager manager;
        private final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        private final JavacTask task;
        private final Elements elements;
        private final Map<String, TypeElement> cache = new HashMap<>();
        public Metadata(List<Path> classpath) throws IOException {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) throw new IllegalStateException("Full tooling JDK required");
            manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8);
            manager.setLocationFromPaths(StandardLocation.CLASS_PATH, classpath);
            manager.setLocationFromPaths(StandardLocation.SOURCE_PATH, List.of());
            task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("-proc:none"), null, List.of());
            elements = task.getElements();
        }
        public TypeElement binaryType(String binary) {
            TypeElement cached = cache.get(binary);
            if (cached != null) return cached;
            List<TypeElement> matches = new ArrayList<>();
            for (String candidate : new LinkedHashSet<>(List.of(binary, binary.replace('$', '.')))) {
                TypeElement type = elements.getTypeElement(candidate);
                if (type != null && elements.getBinaryName(type).contentEquals(binary)) matches.add(type);
            }
            rejectErrors(diagnostics, "classpath metadata");
            if (matches.isEmpty()) fail("Missing real classpath type " + binary);
            TypeElement type = matches.get(0);
            if (type.getNestingKind() == NestingKind.LOCAL || type.getNestingKind() == NestingKind.ANONYMOUS) fail("Not a source-addressable type " + binary);
            cache.put(binary, type);
            hierarchy(type, new HashSet<>());
            return type;
        }
        public boolean hasSourceType(String source) {
            TypeElement type = elements.getTypeElement(source);
            rejectErrors(diagnostics, "classpath metadata");
            return type != null;
        }
        public TypeElement sourceType(String source) {
            TypeElement type = elements.getTypeElement(source);
            rejectErrors(diagnostics, "classpath metadata");
            if (type == null) fail("Missing explicit external type " + source);
            hierarchy(type, new HashSet<>());
            return type;
        }
        private void hierarchy(TypeElement type, Set<String> visited) {
            String name = elements.getBinaryName(type).toString();
            if (!visited.add(name)) return;
            List<TypeMirror> edges = new ArrayList<>(type.getInterfaces());
            if (type.getSuperclass().getKind() != TypeKind.NONE) edges.add(type.getSuperclass());
            for (TypeMirror edge : edges) {
                if (edge.getKind() != TypeKind.DECLARED) fail("Incomplete external hierarchy " + name + " -> " + edge);
                Element element = ((DeclaredType) edge).asElement();
                if (!(element instanceof TypeElement)) fail("Invalid external hierarchy " + name);
                hierarchy((TypeElement) element, visited);
            }
            rejectErrors(diagnostics, "classpath hierarchy");
        }
        public List<? extends Element> members(TypeElement type) {
            hierarchy(type, new HashSet<>());
            List<? extends Element> result = elements.getAllMembers(type);
            for (Element element : result) if (element.asType().getKind() == TypeKind.ERROR) fail("Incomplete inherited member " + element);
            rejectErrors(diagnostics, "inherited members");
            return result;
        }
        public String binaryName(TypeElement type) { return elements.getBinaryName(type).toString(); }
        @Override public void close() throws IOException { manager.close(); }
    }

    private static final class Index {
        final Map<String, ClassTree> classes = new HashMap<>();
        final Map<ClassTree, Parsed> units = new IdentityHashMap<>();
        final Map<ClassTree, String> identities = new IdentityHashMap<>();
        final Map<ClassTree, Tree> anonymousBases = new IdentityHashMap<>();
        final Map<ClassTree, ClassTree> enclosingClasses = new IdentityHashMap<>();
        final Map<ClassTree, Map<String, ClassTree>> memberTypes = new IdentityHashMap<>();
        final Map<ClassTree, Map<String, ClassTree>> lexicalTypes = new IdentityHashMap<>();
        Index(List<Parsed> parsed, Mapping mapping, Metadata metadata) {
            for (Parsed unit : parsed) {
                String pkg = unit.tree().getPackageName() == null ? "" : unit.tree().getPackageName().toString();
                if (nativeDomain(pkg + ".")) fail("Source declarations under reserved game namespace " + pkg);
                String basename = unit.unit().logicalPath().substring(unit.unit().logicalPath().lastIndexOf('/') + 1);
                String expected = (pkg.isEmpty() ? "" : pkg.replace('.', '/') + "/") + basename;
                if (!expected.equals(unit.unit().logicalPath())) fail("Package/path mismatch " + unit.unit().logicalPath());
                new TreePathScanner<Void, String>() {
                    @Override public Void visitClass(ClassTree tree, String enclosing) {
                        String name = tree.getSimpleName().toString();
                        String qualified = enclosing == null ? (pkg.isEmpty() ? name : pkg + "." + name) : enclosing + "." + name;
                        units.put(tree, unit);
                        Map<String, ClassTree> visibleLocalTypes = new HashMap<>();
                        for (var scope = getCurrentPath().getParentPath(); scope != null; scope = scope.getParentPath()) {
                            Tree ancestor = scope.getLeaf();
                            if (ancestor instanceof BlockTree block) {
                                for (StatementTree statement : block.getStatements()) if (statement instanceof ClassTree local) {
                                    if (unit.positions().getStartPosition(unit.tree(), local) <= unit.positions().getStartPosition(unit.tree(), tree)) {
                                        ClassTree oldLocal = visibleLocalTypes.putIfAbsent(local.getSimpleName().toString(), local);
                                        if (oldLocal != null && oldLocal != local) fail("Ambiguous local hierarchy owner " + local.getSimpleName());
                                    }
                                }
                            }
                        }
                        lexicalTypes.put(tree, Map.copyOf(visibleLocalTypes));
                        Tree immediateParent = getCurrentPath().getParentPath().getLeaf();
                        for (var scope = getCurrentPath().getParentPath(); scope != null; scope = scope.getParentPath()) {
                            if (scope.getLeaf() instanceof ClassTree enclosingClass) { enclosingClasses.put(tree, enclosingClass); break; }
                        }
                        Map<String, ClassTree> members = new HashMap<>();
                        for (Tree member : tree.getMembers()) if (member instanceof ClassTree nestedType && !nestedType.getSimpleName().toString().isEmpty()) {
                            if (members.putIfAbsent(nestedType.getSimpleName().toString(), nestedType) != null) fail("Duplicate member type");
                        }
                        memberTypes.put(tree, Map.copyOf(members));
                        if (name.isEmpty() && immediateParent instanceof NewClassTree creation) anonymousBases.put(tree, creation.getIdentifier());
                        if (!name.isEmpty()) {
                            // Local types are collision facts, not globally addressable owners.
                            Tree parent = getCurrentPath().getParentPath().getLeaf();
                            if (parent instanceof CompilationUnitTree || parent instanceof ClassTree) {
                                if (classes.putIfAbsent(qualified, tree) != null) fail("Duplicate selected type " + qualified);
                                identities.put(tree, qualified);
                            }
                        }
                        return super.visitClass(tree, qualified);
                    }
                    @Override public Void visitErroneous(ErroneousTree tree, String ignored) { fail("Recovered source syntax"); return null; }
                }.scan(unit.tree(), null);
            }
        }
    }

    private static final class Binder extends TreePathScanner<Void, Void> {
        final Parsed unit; final Index index; final Mapping mapping; final Metadata metadata;
        final Map<String, String> imports = new HashMap<>();
        final Map<String, NativeType> nativeImports = new HashMap<>();
        final Set<String> declared = new HashSet<>();
        final Set<String> inheritedNames = new HashSet<>();
        final Set<String> inheritedFields = new HashSet<>();
        final Set<String> selectedInheritedTypeNames = new HashSet<>();
        final Map<String, NativeType> inheritedTypes = new HashMap<>();
        final Map<ClassTree, Map<String, NativeType>> contextInheritedTypes = new IdentityHashMap<>();
        final List<Edit> editList = new ArrayList<>();
        final List<NativeIdentity> nativeIdentities = new ArrayList<>();
        final Set<Tree> handled = Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<AnnotationTree, String> annotationNames = new IdentityHashMap<>();
        final String pkg;
        Binder(Parsed unit, Index index, Mapping mapping, Metadata metadata) {
            this.unit = unit; this.index = index; this.mapping = mapping; this.metadata = metadata;
            pkg = unit.tree().getPackageName() == null ? "" : unit.tree().getPackageName().toString();
            for (ImportTree imp : unit.tree().getImports()) {
                String name = imp.getQualifiedIdentifier().toString();
                if (name.endsWith(".*")) failAt(imp, "Wildcard imports are outside this binder contract");
                if (!imp.isStatic()) {
                    String simple = name.substring(name.lastIndexOf('.') + 1);
                    if (imports.putIfAbsent(simple, name) != null) failAt(imp, "Competing simple import " + simple);
                    if (nativeDomain(name)) nativeImports.put(simple, nativeType(name));
                }
            }
            new TreePathScanner<Void, Void>() {
                @Override public Void visitVariable(VariableTree tree, Void p) { declared.add(tree.getName().toString()); return super.visitVariable(tree, p); }
                @Override public Void visitClass(ClassTree tree, Void p) { declared.add(tree.getSimpleName().toString()); return super.visitClass(tree, p); }
                @Override public Void visitTypeParameter(TypeParameterTree tree, Void p) { declared.add(tree.getName().toString()); return super.visitTypeParameter(tree, p); }
                @Override public Void visitMethod(MethodTree tree, Void p) { declared.add(tree.getName().toString()); return super.visitMethod(tree, p); }
            }.scan(unit.tree(), null);
            for (ClassTree type : index.units.keySet()) if (index.units.get(type) == unit) {
                inheritedTypes.clear();
                selectedInheritedTypeNames.clear();
                collectInherited(type, new HashSet<>());
                for (String name : selectedInheritedTypeNames) if (inheritedTypes.containsKey(name)) fail("Selected inherited member type collides with native ancestor type " + name + " in " + unit.unit().logicalPath());
                contextInheritedTypes.put(type, Map.copyOf(inheritedTypes));
            }
            inheritedTypes.clear();
            for (String name : nativeImports.keySet()) {
                if (declared.contains(name) || inheritedNames.contains(name)) fail("Shadowed native import " + name + " in " + unit.unit().logicalPath());
            }
            for (ImportTree imp : unit.tree().getImports()) if (imp.isStatic()) staticImport(imp);
        }
        NativeType nativeType(String source) {
            if (unit.unit().mode() != InputMode.ACTUAL_MCP) return mapping.resolve(source, metadata);
            // Actual input is an explicit per-file contract, never auto-detected from overlapping spellings.
            TypeElement type = source.contains("$") ? metadata.binaryType(source) : metadata.sourceType(source);
            String binary = metadata.binaryName(type);
            String canonical = mapping.actualToOfficial.get(binary);
            if (canonical == null && !mapping.curated) fail("Unexpected actual-native type " + source);
            return new NativeType(canonical == null ? binary : canonical, binary, type.getQualifiedName().toString());
        }
        NativeType actualNativeType(String source) {
            TypeElement type = source.contains("$") ? metadata.binaryType(source) : metadata.sourceType(source);
            String binary = metadata.binaryName(type);
            String official = mapping.actualToOfficial.get(binary);
            if (official == null && !mapping.curated) fail("Unexpected actual-native class " + source);
            return new NativeType(official == null ? binary : official, binary, type.getQualifiedName().toString());
        }
        String externalOrSource(String spelling) {
            if (imports.containsKey(spelling)) return imports.get(spelling);
            int dot = spelling.indexOf('.');
            if (dot > 0 && imports.containsKey(spelling.substring(0, dot))) return imports.get(spelling.substring(0, dot)) + spelling.substring(dot);
            if (index.classes.containsKey(pkg + "." + spelling)) return pkg + "." + spelling;
            if (index.classes.containsKey(spelling)) return spelling;
            if (spelling.contains(".")) return spelling;
            // java.lang types are the only supported implicit external imports.
            return "java.lang." + spelling;
        }
        void collectInherited(ClassTree tree, Set<ClassTree> visiting) {
            if (!visiting.add(tree)) fail("Cyclic selected source inheritance");
            List<Tree> edges = new ArrayList<>(tree.getImplementsClause());
            if (tree.getExtendsClause() != null) edges.add(tree.getExtendsClause());
            if (index.anonymousBases.containsKey(tree)) edges.add(index.anonymousBases.get(tree));
            if (edges.isEmpty() && tree.getKind() != Tree.Kind.INTERFACE && tree.getKind() != Tree.Kind.ANNOTATION_TYPE) edges.add(null);
            for (Tree edge : edges) {
                String spelling = edge == null ? (tree.getKind() == Tree.Kind.ENUM ? "java.lang.Enum" : tree.getKind() == Tree.Kind.RECORD ? "java.lang.Record" : "java.lang.Object") : bareType(edge);
                Parsed defining = index.units.get(tree);
                ClassTree selected = lexicalSelectedType(tree, spelling);
                String source = selected == null ? resolveIn(defining, spelling, tree) : index.identities.get(selected);
                if (selected == null) selected = index.classes.get(source);
                if (selected != null) {
                    // Conservatively collect all source member names. No compiled source-project jar dependency.
                    for (Tree member : selected.getMembers()) {
                        if (member instanceof VariableTree v) { inheritedNames.add(v.getName().toString()); inheritedFields.add(v.getName().toString()); }
                        if (member instanceof ClassTree c) { inheritedNames.add(c.getSimpleName().toString()); selectedInheritedTypeNames.add(c.getSimpleName().toString()); }
                    }
                    collectInherited(selected, visiting);
                } else {
                    TypeElement actual;
                    if (nativeDomain(source)) {
                        NativeType nativeSuper = defining.unit().mode() != InputMode.ACTUAL_MCP
                                ? mapping.resolve(source, metadata) : actualNativeType(source);
                        actual = metadata.binaryType(nativeSuper.actualBinary());
                    } else actual = metadata.sourceType(source);
                    for (Element member : metadata.members(actual)) {
                        if (member.getKind().isField() || member instanceof TypeElement) inheritedNames.add(member.getSimpleName().toString());
                        if (member.getKind().isField()) inheritedFields.add(member.getSimpleName().toString());
                        if (member instanceof TypeElement nested) {
                            String canonical = mapping.actualToOfficial.get(metadata.binaryName(nested));
                            if (canonical != null) {
                                String simple = unit.unit().mode() == InputMode.ACTUAL_MCP ? nested.getSimpleName().toString()
                                        : canonical.substring(Math.max(canonical.lastIndexOf('$'), canonical.lastIndexOf('.')) + 1);
                                NativeType value = new NativeType(canonical, metadata.binaryName(nested), nested.getQualifiedName().toString());
                                NativeType old = inheritedTypes.putIfAbsent(simple, value);
                                if (old != null && !old.actualBinary().equals(value.actualBinary())) fail("Ambiguous inherited native member type " + simple);
                            }
                        }
                    }
                }
            }
            visiting.remove(tree);
        }
        ClassTree lexicalSelectedType(ClassTree context, String spelling) {
            if (spelling.contains(".")) return null; // Qualified locals are deliberately not guessed.
            ClassTree local = index.lexicalTypes.getOrDefault(context, Map.of()).get(spelling);
            if (local != null) return local;
            for (ClassTree scope = context; scope != null; scope = index.enclosingClasses.get(scope)) {
                ClassTree member = index.memberTypes.getOrDefault(scope, Map.of()).get(spelling);
                if (member != null) return member;
            }
            return null;
        }
        String resolveIn(Parsed defining, String spelling, ClassTree context) {
            // Lexical enclosing member types have precedence over imports/implicit java.lang.
            String first = spelling.contains(".") ? spelling.substring(0, spelling.indexOf('.')) : spelling;
            for (ClassTree scope = context; scope != null; scope = index.enclosingClasses.get(scope)) {
                ClassTree member = index.memberTypes.getOrDefault(scope, Map.of()).get(first);
                if (member != null) {
                    String identity = index.identities.get(member);
                    if (identity == null) fail("Local/member type owner is not globally addressable: " + spelling);
                    return identity + spelling.substring(first.length());
                }
            }
            String packageName = defining.tree().getPackageName() == null ? "" : defining.tree().getPackageName().toString();
            for (ImportTree imp : defining.tree().getImports()) if (!imp.isStatic()) {
                String fqn = imp.getQualifiedIdentifier().toString();
                if (fqn.endsWith(".*")) fail("Wildcard in inherited source owner " + defining.unit().logicalPath());
                String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
                if (spelling.equals(simple) || spelling.startsWith(simple + ".")) return fqn + spelling.substring(simple.length());
            }
            if (index.classes.containsKey(packageName + "." + spelling)) return packageName + "." + spelling;
            if (spelling.contains(".")) return spelling;
            // A same-package type can belong to the independent engine JAR rather than this source union.
            String packageType = packageName.isEmpty() ? spelling : packageName + "." + spelling;
            if (metadata.hasSourceType(packageType)) return packageType;
            return "java.lang." + spelling;
        }
        static String bareType(Tree tree) {
            if (tree instanceof ParameterizedTypeTree p) return bareType(p.getType());
            if (tree instanceof AnnotatedTypeTree a) return bareType(a.getUnderlyingType());
            if (tree instanceof IdentifierTree || tree instanceof MemberSelectTree) return tree.toString();
            fail("Unsupported superclass syntax " + tree.getKind()); return null;
        }
        void staticImport(ImportTree imp) {
            String name = imp.getQualifiedIdentifier().toString(); int dot = name.lastIndexOf('.');
            String owner = name.substring(0, dot), memberName = name.substring(dot + 1);
            ClassTree selected = index.classes.get(owner);
            if (selected != null) {
                List<Tree> selectedMatches = selectedStaticMembers(selected, memberName, new HashSet<>());
                if (selectedMatches.isEmpty()) failAt(imp, "Missing selected AST static member " + name);
                if (selectedMatches.stream().anyMatch(member -> member instanceof ClassTree)) failAt(imp, "Selected static member-type import requires explicit ordinary type import");
                if (nativeImports.containsKey(memberName) || contextInheritedTypes.values().stream().anyMatch(types -> types.containsKey(memberName))) failAt(imp, "Selected static import shadows native owner");
                declared.add(memberName);
                return;
            }
            TypeElement type = nativeDomain(owner) ? metadata.binaryType(nativeType(owner).actualBinary()) : metadata.sourceType(owner);
            List<? extends Element> matches = metadata.members(type).stream().filter(e -> e.getSimpleName().contentEquals(memberName) && e.getModifiers().contains(Modifier.STATIC)).toList();
            if (matches.isEmpty()) failAt(imp, "Missing real static import member " + name);
            boolean memberType = matches.stream().anyMatch(e -> e instanceof TypeElement);
            if (memberType && matches.size() != 1) failAt(imp, "Ambiguous static member type import");
            if (nativeImports.containsKey(memberName) || contextInheritedTypes.values().stream().anyMatch(types -> types.containsKey(memberName))) failAt(imp, "Static import shadows native owner");
            declared.add(memberName); // Conservative whole-unit shadow exclusion, including imported methods.
            if (nativeDomain(owner)) {
                if (memberType) failAt(imp, "Native static member-type imports require ordinary explicit nested type import");
                NativeType ownerType = nativeType(owner);
                recordIdentity("static-import-owner", ownerType);
                if (imp.getQualifiedIdentifier() instanceof MemberSelectTree select && unit.unit().mode() != InputMode.ACTUAL_MCP) replaceName(select.getExpression(), ownerType.actualSource(), "static-import-owner");
            }
        }
        List<Tree> selectedStaticMembers(ClassTree owner, String name, Set<ClassTree> visiting) {
            if (!visiting.add(owner)) fail("Cyclic selected static-owner hierarchy");
            List<Tree> matches = new ArrayList<>();
            for (Tree member : owner.getMembers()) {
                if (member instanceof MethodTree method && method.getName().contentEquals(name)
                        && method.getModifiers().getFlags().contains(Modifier.STATIC)) matches.add(member);
                if (member instanceof VariableTree field && field.getName().contentEquals(name)
                        && (field.getModifiers().getFlags().contains(Modifier.STATIC) || owner.getKind() == Tree.Kind.INTERFACE)) matches.add(member);
                if (member instanceof ClassTree type && type.getSimpleName().contentEquals(name)
                        && type.getModifiers().getFlags().contains(Modifier.STATIC)) matches.add(member);
            }
            if (matches.isEmpty() && owner.getExtendsClause() != null) {
                Parsed defining = index.units.get(owner);
                String parentName = resolveIn(defining, bareType(owner.getExtendsClause()), owner);
                ClassTree parent = index.classes.get(parentName);
                if (parent == null) fail("Selected static member requires unsupported external inherited-owner resolution: " + name);
                matches.addAll(selectedStaticMembers(parent, name, visiting));
            }
            visiting.remove(owner);
            return matches;
        }
        @Override public Void visitImport(ImportTree tree, Void p) {
            handled.add(tree.getQualifiedIdentifier());
            if (!tree.isStatic() && nativeDomain(tree.getQualifiedIdentifier().toString()) && unit.unit().mode() != InputMode.ACTUAL_MCP) {
                removeSyntax(tree, "native-import");
            }
            return null;
        }
        @Override public Void visitIdentifier(IdentifierTree tree, Void p) {
            if (handled.contains(tree)) return null;
            if (isMethodSelector(tree)) return null;
            String name = tree.getName().toString();
            NativeType type = nativeImports.get(name);
            if (type == null && !imports.containsKey(name)) type = contextualInheritedType(name);
            if (type != null) {
                if (inheritedFields.contains(name)) failAt(tree, "Inherited field shadows native type " + name);
                if (declared.contains(name)) failAt(tree, "Declared symbol shadows native type " + name);
                recordIdentity(javaReferenceRole(tree), type);
                if (unit.unit().mode() != InputMode.ACTUAL_MCP) replaceName(tree, type.actualSource(), "imported-or-inherited-type");
                handled.add(tree);
            }
            return null;
        }
        @Override public Void visitMemberSelect(MemberSelectTree tree, Void p) {
            if (handled.contains(tree)) return null;
            if (tree.getIdentifier().contentEquals("class") || tree.getIdentifier().contentEquals("this") || tree.getIdentifier().contentEquals("super")) {
                scan(tree.getExpression(), p); return null;
            }
            List<Tree> prefixes = namePrefixes(tree);
            if (prefixes.isEmpty()) {
                String expression = tree.getExpression().toString();
                boolean nativeRoot = nativeDomain(expression) || nativeImports.keySet().stream().anyMatch(name -> expression.startsWith(name + "<"))
                        || contextInheritedTypes.values().stream().flatMap(types -> types.keySet().stream()).anyMatch(name -> expression.startsWith(name + "<"));
                if (nativeRoot && typeContext(tree)) failAt(tree, "Qualified generic/annotated nested native name requires a typed simpler spelling");
            }
            if (!prefixes.isEmpty()) {
                String root = prefixes.get(0).toString();
                NativeType imported = nativeImports.get(root);
                if (imported == null && !imports.containsKey(root)) imported = contextualInheritedType(root);
                boolean qualified = nativeDomain(tree.toString());
                if (imported != null || qualified) {
                    if (declared.contains(root) || inheritedFields.contains(root) || (inheritedNames.contains(root) && contextualInheritedType(root) == null)) failAt(tree, "Shadowed class owner " + root);
                    Tree matched = null; NativeType target = null;
                    for (Tree prefix : prefixes) {
                        String spelling = prefix.toString();
                        String canonical = imported == null ? spelling : imported.official().replace('$', '.') + spelling.substring(root.length());
                        if (unit.unit().mode() != InputMode.ACTUAL_MCP) {
                            String identity = mapping.sourceIdentity(canonical);
                            if (identity != null) {
                                NativeType resolved = mapping.resolve(canonical, metadata);
                                if (target != null && prefix instanceof MemberSelectTree selection) {
                                    String segment = selection.getIdentifier().toString();
                                    if (metadata.members(metadata.binaryType(target.actualBinary())).stream().anyMatch(member -> member.getKind().isField() && member.getSimpleName().contentEquals(segment))) failAt(prefix, "Nested class/field selector ambiguity " + segment);
                                }
                                matched = prefix; target = resolved;
                            }
                        } else {
                            String actual = imported == null ? spelling : imported.actualSource() + spelling.substring(root.length());
                            if (mapping.curated && metadata.hasSourceType(actual)) {
                                matched = prefix; target = nativeType(actual);
                            } else for (String binary : mapping.actualToOfficial.keySet()) if (binary.equals(actual) || binary.replace('$', '.').equals(actual)) {
                                matched = prefix; target = nativeType(actual); break;
                            }
                        }
                    }
                    if (matched == null) failAt(tree, "Unresolved native qualified type/owner " + tree);
                    if (typeContext(tree) && matched != tree) failAt(tree, "Unknown native nested type " + tree);
                    recordIdentity(javaReferenceRole(tree), target);
                    if (unit.unit().mode() != InputMode.ACTUAL_MCP) replaceName(matched, target.actualSource(), "qualified-class-owner");
                    handled.add(matched);
                    // The suffix is a member selector; member names are never edited.
                    return null;
                }
            }
            return super.visitMemberSelect(tree, p);
        }
        NativeType contextualInheritedType(String name) {
            for (var path = getCurrentPath(); path != null; path = path.getParentPath()) {
                if (path.getLeaf() instanceof ClassTree enclosing) {
                    NativeType type = contextInheritedTypes.getOrDefault(enclosing, Map.of()).get(name);
                    if (type != null) return type;
                }
            }
            return null;
        }
        boolean isMethodSelector(Tree tree) {
            if (getCurrentPath().getParentPath() == null) return false;
            Tree parent = getCurrentPath().getParentPath().getLeaf();
            return parent instanceof MethodInvocationTree call && call.getMethodSelect() == tree;
        }
        boolean typeContext(Tree tree) {
            Tree parent = getCurrentPath().getParentPath() == null ? null : getCurrentPath().getParentPath().getLeaf();
            return parent instanceof MemberSelectTree literal && literal.getExpression() == tree && literal.getIdentifier().contentEquals("class")
                    || parent instanceof MemberReferenceTree reference && reference.getQualifierExpression() == tree
                    || parent instanceof VariableTree v && v.getType() == tree
                    || parent instanceof MethodTree m && (m.getReturnType() == tree || m.getThrows().contains(tree))
                    || parent instanceof ClassTree c && (c.getExtendsClause() == tree || c.getImplementsClause().contains(tree) || c.getPermitsClause().contains(tree))
                    || parent instanceof ParameterizedTypeTree || parent instanceof ArrayTypeTree || parent instanceof AnnotatedTypeTree
                    || parent instanceof UnionTypeTree || parent instanceof IntersectionTypeTree || parent instanceof TypeParameterTree
                    || parent instanceof TypeCastTree t && t.getType() == tree
                    || parent instanceof InstanceOfTree i && i.getType() == tree
                    || parent instanceof NewClassTree creation && creation.getIdentifier() == tree
                    || parent instanceof NewArrayTree array && array.getType() == tree
                    || parent instanceof AnnotationTree a && a.getAnnotationType() == tree;
        }
        @Override public Void visitAnnotation(AnnotationTree tree, Void p) {
            String name = externalOrSource(tree.getAnnotationType().toString());
            annotationNames.put(tree, name);
            scan(tree.getAnnotationType(), p);
            for (ExpressionTree argument : tree.getArguments()) {
                String key = "value"; ExpressionTree value = argument;
                if (argument instanceof AssignmentTree a) { key = a.getVariable().toString(); value = a.getExpression(); }
                String grammar = bindingGrammar(name, key);
                if (grammar != null) binding(value, grammar);
                else scan(value, p);
            }
            return null;
        }
        String bindingGrammar(String annotation, String key) {
            if (annotation.equals("org.spongepowered.asm.mixin.Mixin") && key.equals("targets")) return "class";
            if (annotation.equals("org.spongepowered.asm.mixin.injection.At") && key.equals("target")) return "selector";
            if (Set.of("org.spongepowered.asm.mixin.injection.Inject", "org.spongepowered.asm.mixin.injection.Redirect",
                    "org.spongepowered.asm.mixin.injection.ModifyVariable", "org.spongepowered.asm.mixin.injection.ModifyArg",
                    "org.spongepowered.asm.mixin.injection.ModifyArgs", "org.spongepowered.asm.mixin.injection.ModifyConstant").contains(annotation) && key.equals("method")) return "selector";
            return null;
        }
        void binding(ExpressionTree value, String grammar) {
            if (value instanceof NewArrayTree array && array.getInitializers() != null) { for (ExpressionTree child : array.getInitializers()) binding(child, grammar); return; }
            if (!(value instanceof LiteralTree literal) || !(literal.getValue() instanceof String)) failAt(value, "Nonliteral Mixin binding");
            String decoded = (String) ((LiteralTree) value).getValue();
            String raw = slice(value);
            if (!raw.equals("\"" + decoded + "\"")) failAt(value, "Escaped/text-block Mixin binding is unsupported");
            String converted = grammar.equals("class") ? bindingClass(decoded, false) : new Descriptor(decoded).convert();
            if (!converted.equals(decoded)) add(start(value) + 1, end(value) - 1, converted, "mixin-" + grammar);
            handled.add(value);
        }
        String bindingClass(String name, boolean internal) {
            String spelling = internal ? name.replace('/', '.') : name;
            if (!nativeDomain(spelling)) {
                if (spelling.contains("$") || internal) metadata.binaryType(spelling); else metadata.sourceType(spelling);
                return name;
            }
            NativeType type = nativeType(spelling);
            recordIdentity("mixin-binding-class", type);
            if (unit.unit().mode() == InputMode.ACTUAL_MCP) return name;
            return internal ? type.actualBinary().replace('.', '/') : type.actualBinary();
        }
        @Override public Void visitLiteral(LiteralTree tree, Void p) {
            if (!handled.contains(tree) && tree.getValue() instanceof String text && (text.contains("net.minecraft.") || text.contains("net/minecraft/") || text.contains("com/mojang/blaze3d/") || text.contains("com.mojang.blaze3d.") || text.contains("com/mojang/math/") || text.contains("com.mojang.math."))) failAt(tree, "Native-bearing literal outside explicit Mixin binding grammar");
            return null;
        }
        @Override public Void visitErroneous(ErroneousTree tree, Void p) { failAt(tree, "Recovered source syntax"); return null; }
        final class Descriptor {
            final String input; int cursor; final StringBuilder output = new StringBuilder();
            Descriptor(String input) { this.input = input; }
            String convert() {
                if (input.isEmpty()) fail("Empty Mixin selector");
                if (input.startsWith("L") && input.indexOf(';') > 0 && (input.indexOf('(') < 0 || input.indexOf(';') < input.indexOf('('))) object();
                int begin = cursor;
                while (cursor < input.length() && input.charAt(cursor) != '(' && input.charAt(cursor) != ':') cursor++;
                String member = input.substring(begin, cursor);
                if (!member.matches("[A-Za-z_$][A-Za-z0-9_$]*|<init>|<clinit>")) fail("Unsupported Mixin member selector " + input);
                output.append(member);
                if (cursor == input.length()) return output.toString();
                char separator = input.charAt(cursor++); output.append(separator);
                if (separator == ':') type(false);
                else {
                    while (cursor < input.length() && input.charAt(cursor) != ')') type(false);
                    require(')'); output.append(')'); type(true);
                }
                if (cursor != input.length()) fail("Trailing Mixin descriptor syntax " + input);
                return output.toString();
            }
            void type(boolean allowVoid) {
                if (cursor >= input.length()) fail("Truncated Mixin descriptor " + input);
                char c = input.charAt(cursor);
                if (c == '[') { cursor++; output.append('['); type(false); }
                else if (c == 'L') object();
                else if ("BCDFIJSZ".indexOf(c) >= 0 || c == 'V' && allowVoid) { cursor++; output.append(c); }
                else fail("Invalid Mixin descriptor type " + input);
            }
            void object() {
                require('L'); int begin = cursor;
                while (cursor < input.length() && input.charAt(cursor) != ';') cursor++;
                if (cursor == input.length()) fail("Unclosed Mixin object type " + input);
                String name = input.substring(begin, cursor);
                if (!name.matches("[A-Za-z_$][A-Za-z0-9_$/]*")) fail("Invalid Mixin object identity " + name);
                output.append('L').append(bindingClass(name, true)).append(';'); cursor++;
            }
            void require(char c) { if (cursor >= input.length() || input.charAt(cursor++) != c) fail("Malformed selector " + input); }
        }
        String javaReferenceRole(Tree nativeReferenceTree) {
            var parentPath = getCurrentPath().getParentPath();
            Tree parent = parentPath == null ? null : parentPath.getLeaf();
            if (parent instanceof VariableTree variable && variable.getType() == nativeReferenceTree) return "java-type:variable";
            if (parent instanceof MethodTree method && method.getReturnType() == nativeReferenceTree) return "java-type:return";
            if (parent instanceof MethodTree) return "java-type:method";
            if (parent instanceof ClassTree) return "java-type:hierarchy";
            if (parent instanceof MemberSelectTree select && select.getIdentifier().contentEquals("class")) return "java-class-literal";
            if (parent instanceof MemberReferenceTree) return "java-method-reference-owner";
            return "java-reference:" + (parent == null ? "ROOT" : parent.getKind().name());
        }
        void recordIdentity(String role, NativeType type) {
            nativeIdentities.add(new NativeIdentity(role, type.official(), type.actualBinary()));
        }
        List<Tree> namePrefixes(Tree tree) {
            if (tree instanceof IdentifierTree) return new ArrayList<>(List.of(tree));
            if (tree instanceof MemberSelectTree select) { List<Tree> parts = namePrefixes(select.getExpression()); if (!parts.isEmpty()) parts.add(tree); return parts; }
            return List.of();
        }
        void replaceName(Tree tree, String destination, String role) {
            List<Token> tokens = tokens(start(tree), end(tree));
            List<Token> syntax = tokens.stream().filter(t -> !t.trivia).toList();
            String source = syntax.stream().map(t -> t.text).collect(Collectors.joining());
            if (!source.equals(tree.toString()) || !source.matches("[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*(?:\\.[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*)*")) failAt(tree, "Unsupported qualified type syntax");
            // Replace syntax tokens only. Existing comments and whitespace remain byte-identical.
            for (int i = 0; i < syntax.size(); i++) {
                Token token = syntax.get(i); add(token.start, token.end, i == 0 ? destination : "", role);
            }
        }
        void removeSyntax(Tree tree, String role) {
            for (Token token : tokens(start(tree), end(tree))) if (!token.trivia) add(token.start, token.end, "", role);
        }
        record Token(int start, int end, String text, boolean trivia) {}
        List<Token> tokens(int begin, int limit) {
            List<Token> result = new ArrayList<>(); String text = unit.text(); int i = begin;
            while (i < limit) {
                int from = i; char c = text.charAt(i); boolean trivia = false;
                if (Character.isWhitespace(c)) { trivia = true; while (i < limit && Character.isWhitespace(text.charAt(i))) i++; }
                else if (c == '/' && i + 1 < limit && text.charAt(i + 1) == '*') { trivia = true; i += 2; while (i + 1 < limit && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) i++; if (i + 1 >= limit) failAt(unit.tree(), "Unclosed token-range comment"); i += 2; }
                else if (c == '/' && i + 1 < limit && text.charAt(i + 1) == '/') { trivia = true; i += 2; while (i < limit && text.charAt(i) != '\n' && text.charAt(i) != '\r') i++; }
                else if (Character.isJavaIdentifierStart(c)) { i++; while (i < limit && Character.isJavaIdentifierPart(text.charAt(i))) i++; }
                else if (".;".indexOf(c) >= 0) i++;
                else failAt(unit.tree(), "Unsupported syntax token in native edit range: " + c);
                result.add(new Token(from, i, text.substring(from, i), trivia));
            }
            return result;
        }
        void add(int begin, int limit, String replacement, String role) {
            String original = unit.text().substring(begin, limit);
            if (original.equals(replacement)) return;
            for (Edit edit : editList) if (begin < edit.end() && limit > edit.start()) {
                if (edit.start() == begin && edit.end() == limit && edit.replacement().equals(replacement)) return;
                fail("Overlapping namespace edits in " + unit.unit().logicalPath());
            }
            editList.add(new Edit(begin, limit, original, replacement, role));
        }
        String apply() {
            StringBuilder output = new StringBuilder(unit.text());
            editList.sort(Comparator.comparingInt(Edit::start).reversed());
            for (Edit edit : editList) {
                if (!output.substring(edit.start(), edit.end()).equals(edit.oldText())) fail("Source changed during edit plan");
                output.replace(edit.start(), edit.end(), edit.replacement());
            }
            return output.toString();
        }
        List<Edit> edits() { return List.copyOf(editList); }
        int start(Tree tree) { long value = unit.positions().getStartPosition(unit.tree(), tree); if (value < 0 || value > Integer.MAX_VALUE) fail("Missing AST start"); return (int) value; }
        int end(Tree tree) { long value = unit.positions().getEndPosition(unit.tree(), tree); if (value < 0 || value > Integer.MAX_VALUE) fail("Missing AST end"); return (int) value; }
        String slice(Tree tree) { return unit.text().substring(start(tree), end(tree)); }
        void failAt(Tree tree, String message) { fail(unit.unit().logicalPath() + ":" + unit.tree().getLineMap().getLineNumber(start(tree)) + ": " + message); }
    }

    private static List<String> declarationNames(CompilationUnitTree tree) {
        List<String> names = new ArrayList<>();
        new TreePathScanner<Void, Void>() {
            @Override public Void visitClass(ClassTree value, Void p) { names.add(value.getKind() + ":" + value.getSimpleName()); return super.visitClass(value, p); }
            @Override public Void visitMethod(MethodTree value, Void p) { names.add("METHOD:" + value.getName()); return super.visitMethod(value, p); }
            @Override public Void visitVariable(VariableTree value, Void p) { names.add("VARIABLE:" + value.getName()); return super.visitVariable(value, p); }
            @Override public Void visitTypeParameter(TypeParameterTree value, Void p) { names.add("TYPE_PARAMETER:" + value.getName()); return super.visitTypeParameter(value, p); }
        }.scan(tree, null);
        return names;
    }
    private static Map<String, Long> identityCounts(List<NativeIdentity> identities) {
        return identities.stream().collect(Collectors.groupingBy(identity -> identity.role() + "\t" + identity.originalBinary() + "\t" + identity.actualBinary(), TreeMap::new, Collectors.counting()));
    }
    private static boolean nativeDomain(String name) { return name.startsWith("net.minecraft.") || name.startsWith("com.mojang.blaze3d.") || name.startsWith("com.mojang.math."); }
    private static String checked(MappingInput input) throws Exception {
        byte[] bytes = Files.readAllBytes(input.file());
        if (!hex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(input.sha256())) fail("Mapping hash mismatch " + input.file());
        return new String(bytes, StandardCharsets.UTF_8);
    }
    private static Parsed reparse(JavaCompiler compiler, StandardJavaFileManager manager, Unit unit, String text, List<String> options) throws IOException {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject object = new SimpleJavaFileObject(unit.file().toUri(), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignore) { return text; }
        };
        JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics, options, null, List.of(object));
        Parsed parsed = null;
        for (CompilationUnitTree tree : task.parse()) {
            if (parsed != null) fail("Multiple generated compilation units");
            new TreePathScanner<Void, Void>() {
                @Override public Void visitErroneous(ErroneousTree erroneous, Void p) { fail("Recovered generated syntax"); return null; }
            }.scan(tree, null);
            parsed = new Parsed(unit, text, tree, Trees.instance(task).getSourcePositions());
        }
        rejectErrors(diagnostics, "generated parse " + unit.logicalPath());
        if (parsed == null) fail("No generated compilation unit");
        return parsed;
    }
    private static void rejectErrors(DiagnosticCollector<JavaFileObject> diagnostics, String phase) {
        List<String> errors = diagnostics.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR).map(Object::toString).toList();
        if (!errors.isEmpty()) fail(phase + ": " + String.join("\n", errors));
    }
    public static List<Path> planInputPaths(List<Path> plans) throws IOException {
        List<Path> inputs = new ArrayList<>();
        for (Path plan : plans) {
            if (!Files.isRegularFile(plan)) continue;
            for (String row : Files.readAllLines(plan, StandardCharsets.UTF_8)) for (String cell : row.split("\t", -1)) {
                if (cell.isEmpty()) continue;
                try { Path candidate = Path.of(cell); if (candidate.isAbsolute()) inputs.add(candidate); }
                catch (InvalidPathException malformed) { /* Strict plan validation follows safe invalidation. */ }
            }
        }
        return inputs;
    }
    public static void preflightPaths(Path outputPath, Path receiptPath, List<Path> inputs) throws IOException {
        Path output = checkedOutputPath(outputPath), receipt = checkedOutputPath(receiptPath);
        Path stage = checkedOutputPath(output.resolveSibling(output.getFileName() + ".stage"));
        Path receiptStage = checkedOutputPath(receipt.resolveSibling(receipt.getFileName() + ".stage"));
        List<Path> writes = List.of(output, receipt, stage, receiptStage);
        preflightProtectedWrites(writes, inputs);
    }
    public static void preflightProtectedWrites(List<Path> outputPaths, List<Path> inputs) throws IOException {
        List<Path> writes = new ArrayList<>();
        for (Path path : outputPaths) writes.add(checkedOutputPath(path));
        for (int i = 0; i < writes.size(); i++) for (int j = i + 1; j < writes.size(); j++) {
            if (overlap(writes.get(i), writes.get(j))) fail("Task-owned outputs overlap");
        }
        List<Path> protectedInputs = new ArrayList<>(inputs);
        // The files subsequently hashed/read as exact tooling/JDK identity are inputs too.
        protectedInputs.addAll(NamespaceMetadataAcceptance.identityInputPaths());
        for (Path input : protectedInputs) {
            Path physical = protectedInputPath(input);
            for (Path write : writes) if (overlap(write, physical)) fail("Input/output path overlap: " + input);
        }
    }
    public static Path protectedInputPath(Path input) throws IOException {
        Path absolute = input.toAbsolutePath().normalize();
        Path existing = absolute;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) existing = existing.getParent();
        if (existing == null) fail("Input has no real filesystem ancestry");
        return existing.toRealPath().resolve(existing.relativize(absolute)).normalize();
    }
    private static boolean overlap(Path a, Path b) { return a.startsWith(b) || b.startsWith(a); }
    private static Path checkedOutputPath(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.getParent() == null) fail("Filesystem root cannot be a task output");
        for (Path cursor = normalized; cursor != null; cursor = cursor.getParent()) {
            if (Files.isSymbolicLink(cursor)) fail("Symlink in task output path: " + cursor);
        }
        return normalized;
    }
    private static void clearOwnedDirectory(Path directory) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) fail("Unsafe task output directory " + directory);
        try (var walk = Files.walk(directory)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) { if (Files.isSymbolicLink(path)) fail("Symlink inside task output " + path); Files.delete(path); }
        }
    }
    private static String receipt(Request request, Mapping mapping, List<Result> results) throws Exception {
        StringBuilder json = new StringBuilder("{\n  \"inputMode\":\"explicit-per-unit\",\n  \"runtimeJdk\":").append(quote(System.getProperty("java.runtime.version")));
        json.append(",\n  \"jdkIdentity\":").append(quote(NamespaceMetadataAcceptance.jdkIdentity()));
        json.append(",\n  \"toolIdentity\":").append(quote(NamespaceMetadataAcceptance.toolIdentity()));
        json.append(",\n  \"metadataClasspathIdentity\":").append(quote(NamespaceMetadataAcceptance.classpathHash(request.metadataClasspath())));
        json.append(",\n  \"metadataArtifacts\":[");
        boolean firstArtifact = true;
        for (Path artifact : request.metadataClasspath()) {
            if (!firstArtifact) json.append(','); firstArtifact = false;
            json.append("{\"name\":").append(quote(artifact.getFileName().toString())).append(",\"sha256\":").append(quote(fileHash(artifact))).append('}');
        }
        json.append(']');
        json.append(",\n  \"metadataAcceptanceSha256\":").append(quote(fileHash(request.metadataAcceptance())));
        json.append(",\n  \"preview\":").append(request.preview());
        json.append(",\n  \"allInputUnits\":[");
        boolean firstInput = true;
        for (Unit input : mergeUnits(request.outputUnits(), request.symbolUnits())) {
            if (!firstInput) json.append(','); firstInput = false;
            json.append("{\"owner\":").append(quote(input.owner())).append(",\"path\":").append(quote(input.logicalPath())).append(",\"mode\":").append(quote(input.mode().name())).append(",\"sha256\":").append(quote(fileHash(input.file()))).append('}');
        }
        json.append(']');
        json.append(",\n  \"syntaxLevel\":").append(quote(request.syntaxLevel()));
        if (request.curatedClasses() == null) {
            json.append(",\n  \"mappingKind\":\"same-target-official-obfuscated-join\",\n  \"mappingHashes\":[")
                    .append(quote(request.client().sha256())).append(',').append(quote(request.server().sha256())).append(',').append(quote(request.tsrg().sha256())).append(']');
        } else {
            json.append(",\n  \"mappingKind\":\"reviewed-semantic-class-identities\",\n  \"curatedClassesSha256\":")
                    .append(quote(request.curatedClasses().sha256()));
        }
        json.append(",\n  \"joinedClasses\":").append(mapping.officialToActual.size()).append(",\n  \"unmappedOfficialClasses\":[").append(mapping.unmapped.stream().sorted().map(MinecraftClassNamespaceProducer::quote).collect(Collectors.joining(","))).append("],\n  \"units\":[");
        boolean first = true;
        for (Result result : results.stream().sorted(Comparator.comparing(r -> r.unit().logicalPath())).toList()) {
            if (!first) json.append(','); first = false;
            json.append("\n    {\"owner\":").append(quote(result.unit().owner())).append(",\"path\":").append(quote(result.unit().logicalPath())).append(",\"mode\":").append(quote(result.unit().mode().name())).append(",\"inputHash\":").append(quote(result.originalHash())).append(",\"outputHash\":").append(quote(hash(result.text()))).append(",\"edits\":[");
            boolean firstEdit = true;
            for (Edit edit : result.edits()) {
                if (!firstEdit) json.append(','); firstEdit = false;
                json.append("{\"start\":").append(edit.start()).append(",\"end\":").append(edit.end()).append(",\"old\":").append(quote(edit.oldText())).append(",\"new\":").append(quote(edit.replacement())).append(",\"role\":").append(quote(edit.role())).append('}');
            }
            json.append("],\"nativeIdentities\":[");
            boolean firstIdentity = true;
            for (NativeIdentity identity : result.identities()) {
                if (!firstIdentity) json.append(','); firstIdentity = false;
                json.append("{\"role\":").append(quote(identity.role())).append(",\"originalOfficialBinary\":").append(quote(identity.originalBinary())).append(",\"actualBinary\":").append(quote(identity.actualBinary())).append('}');
            }
            json.append("]}");
        }
        return json.append("\n  ]\n}\n").toString();
    }
    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) switch (c) {
            case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\"); case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
            default -> { if (c < 32) out.append(String.format(Locale.ROOT, "\\u%04x", (int)c)); else out.append(c); }
        }
        return out.append('"').toString();
    }
    private static String fileHash(Path path) throws Exception { return hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    private static String hash(String text) throws Exception { return hex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
    private static String hex(byte[] bytes) { return HexFormat.of().formatHex(bytes); }
    private static void fail(String message) { throw new IllegalStateException(message); }
}
