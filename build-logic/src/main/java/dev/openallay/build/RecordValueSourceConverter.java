package dev.openallay.build;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import javax.tools.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Review-only canonical record lowering. Never changes its input owner files. */
public final class RecordValueSourceConverter {
    private record Change(int start, int end, String replacement) {}
    private record Component(String name, String type, String annotations) {}
    private RecordValueSourceConverter() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected exact request TSV and fresh output directory");
        Path request = Paths.get(args[0]).toAbsolutePath().normalize();
        Path output = Paths.get(args[1]).toAbsolutePath().normalize();
        if (Files.exists(output)) throw new IllegalArgumentException("Fresh output required");
        List<String> patches = new ArrayList<>();
        List<String> receipts = new ArrayList<>();
        Map<String, byte[]> products = new LinkedHashMap<>();
        Set<Path> inputs = new HashSet<>();
        for (String row : Files.readAllLines(request, StandardCharsets.UTF_8)) {
            if (row.isBlank()) continue;
            String[] columns = row.split("\\t", -1);
            if (columns.length != 4) throw new IllegalArgumentException("Expected path, logical path, raw SHA256, selected record paths");
            Path input = Paths.get(columns[0]).toAbsolutePath().normalize();
            Path logical = Paths.get(columns[1]);
            if (!inputs.add(input) || logical.isAbsolute() || logical.normalize().startsWith("..")
                    || output.startsWith(input.getParent()) || input.startsWith(output)) {
                throw new IllegalArgumentException("Invalid input/output ownership");
            }
            byte[] before = Files.readAllBytes(input);
            if (!sha(before).equals(columns[2])) throw new IllegalArgumentException("Raw source preimage differs: " + input);
            String text = new String(before, StandardCharsets.UTF_8);
            if (!Arrays.equals(before, text.getBytes(StandardCharsets.UTF_8))) throw new IllegalArgumentException("Invalid UTF8 input");
            Set<String> selected = new LinkedHashSet<>(Arrays.asList(columns[3].split(",")));
            String lowered = convert(input, text, selected);
            byte[] after = lowered.getBytes(StandardCharsets.UTF_8);
            if (products.put(columns[1], after) != null) throw new IllegalArgumentException("Duplicate logical owner");
            patches.add(patch(columns[1], text, lowered));
            receipts.add("{\"path\":\"" + escape(columns[1]) + "\",\"beforeSha256\":\"" + sha(before)
                    + "\",\"afterSha256\":\"" + sha(after) + "\",\"beforeBytes\":" + before.length + ",\"afterBytes\":" + after.length + "}");
        }
        if (products.isEmpty()) throw new IllegalArgumentException("No selected owners");
        // All inputs validate before any success output is emitted.
        Files.createDirectories(output);
        for (Map.Entry<String, byte[]> product : products.entrySet()) {
            Path destination = output.resolve("candidate").resolve(product.getKey()).normalize();
            if (!destination.startsWith(output.resolve("candidate"))) throw new IllegalArgumentException("Escaping candidate");
            Files.createDirectories(destination.getParent()); Files.write(destination, product.getValue());
        }
        Files.writeString(output.resolve("canonical-record-conversion.patch"), String.join("", patches), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("conversion-receipt.json"), "{\"kind\":\"review-only-canonical-record-patch\",\"files\":[" + String.join(",", receipts) + "]}\n", StandardCharsets.UTF_8);
    }

    public static String convert(Path input, String text, Set<String> selected) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("Full tooling JDK required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            JavaFileObject source = new SimpleJavaFileObject(input.toUri(), JavaFileObject.Kind.SOURCE) {
                @Override public CharSequence getCharContent(boolean ignore) { return text; }
            };
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("-proc:none", "--source", "17"), null, List.of(source));
            CompilationUnitTree unit = task.parse().iterator().next();
            for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) throw new IllegalArgumentException("Source parse failed: " + diagnostic);
            }
            SourcePositions positions = Trees.instance(task).getSourcePositions();
            List<Change> changes = new ArrayList<>(); Set<String> found = new HashSet<>();
            String newline = text.contains("\r\n") ? "\r\n" : "\n";
            new TreePathScanner<Void, String>() {
                @Override public Void visitClass(ClassTree tree, String parent) {
                    String name = tree.getSimpleName().toString();
                    String path = parent == null ? name : parent + "." + name;
                    if (tree.getKind() == Tree.Kind.RECORD && selected.contains(path)) {
                        if (!(getCurrentPath().getParentPath().getLeaf() instanceof CompilationUnitTree)
                                && !(getCurrentPath().getParentPath().getLeaf() instanceof ClassTree)) fail("Local records unsupported");
                        found.add(path);
                        int start = position(positions.getStartPosition(unit, tree));
                        int end = position(positions.getEndPosition(unit, tree));
                        changes.add(new Change(start, end, lower(tree, start, end, unit, positions, text, newline,
                                getCurrentPath().getParentPath().getLeaf() instanceof ClassTree)));
                        return null;
                    }
                    return super.visitClass(tree, path);
                }
            }.scan(unit, null);
            if (!found.equals(selected)) throw new IllegalArgumentException("Selected record paths missing: " + selected + ", found " + found);
            changes.sort(Comparator.comparingInt(Change::start).reversed());
            StringBuilder result = new StringBuilder(text); int previous = text.length();
            for (Change change : changes) {
                if (change.end() > previous) fail("Nested selected records overlap");
                result.replace(change.start(), change.end(), change.replacement()); previous = change.start();
            }
            return result.toString();
        }
    }

    private static String lower(ClassTree tree, int start, int end, CompilationUnitTree unit,
            SourcePositions positions, String source, String newline, boolean nested) {
        String name = tree.getSimpleName().toString();
        int body = bodyStart(source, start, end);
        List<Component> components = new ArrayList<>(); List<String> members = new ArrayList<>();
        Set<String> accessors = new HashSet<>(); Set<String> overrides = new HashSet<>();
        String compact = null; boolean canonical = false;
        for (Tree member : tree.getMembers()) {
            long rawStart = positions.getStartPosition(unit, member), rawEnd = positions.getEndPosition(unit, member);
            if (rawStart < 0 || rawEnd < 0) continue;
            int from = position(rawStart), to = position(rawEnd);
            if (member instanceof VariableTree variable && from < body) {
                String type = slice(source, unit, positions, variable.getType());
                if (type.contains("...") || type.contains("@") || source.substring(from, to).contains("...")) fail("Annotated/varargs component unsupported");
                StringBuilder annotations = new StringBuilder();
                for (AnnotationTree annotation : variable.getModifiers().getAnnotations()) {
                    String annotationName = annotation.getAnnotationType().toString();
                    if (!(annotationName.endsWith("ToolDescription") || annotationName.endsWith("ToolOptional")
                            || annotationName.endsWith("SerializedName") || annotationName.endsWith("JsonAdapter"))) {
                        fail("Unrecognized component annotation target: " + annotationName);
                    }
                    annotations.append(slice(source, unit, positions, annotation)).append(' ');
                }
                components.add(new Component(variable.getName().toString(), type, annotations.toString()));
                continue;
            }
            if (member instanceof ClassTree child && child.getSimpleName().contentEquals("ValueSchemaProvider")) fail("Value schema provider collision");
            if (member instanceof MethodTree method) {
                if (method.getName().contentEquals("<init>")) {
                    String head = source.substring(from, position(positions.getStartPosition(unit, method.getBody())));
                    if (!head.contains("(")) {
                        if (compact != null || canonical) fail("Duplicate canonical constructor");
                        compact = slice(source, unit, positions, method.getBody()); continue;
                    }
                    if (method.getParameters().size() == components.size()
                            && names(method.getParameters()).equals(components.stream().map(Component::name).toList())) canonical = true;
                } else if (method.getParameters().isEmpty()) {
                    accessors.add(method.getName().toString());
                    if (components.stream().anyMatch(c -> c.name().contentEquals(method.getName()))) {
                        if (!tree.getImplementsClause().isEmpty()) fail("Explicit interface component accessor needs reviewed override policy");
                        String kept = source.substring(from, to);
                        for (AnnotationTree annotation : method.getModifiers().getAnnotations()) {
                            if (annotation.getAnnotationType().toString().equals("Override")
                                    || annotation.getAnnotationType().toString().equals("java.lang.Override")) {
                                int annotationFrom = position(positions.getStartPosition(unit, annotation)) - from;
                                int annotationTo = position(positions.getEndPosition(unit, annotation)) - from;
                                kept = kept.substring(0, annotationFrom) + kept.substring(annotationTo);
                            }
                        }
                        members.add(kept); continue;
                    }
                }
                if (method.getName().contentEquals("equals") || method.getName().contentEquals("hashCode") || method.getName().contentEquals("toString")) overrides.add(method.getName().toString());
            }
            members.add(source.substring(from, to));
        }
        if (canonical) fail("Explicit canonical constructors are outside first conversion scope");
        String typeParameters = tree.getTypeParameters().isEmpty() ? "" : "<" + String.join(", ", tree.getTypeParameters().stream().map(Object::toString).toList()) + ">";
        String generics = tree.getTypeParameters().isEmpty() ? "" : "<" + String.join(", ", tree.getTypeParameters().stream().map(v -> v.getName().toString()).toList()) + ">";
        Set<javax.lang.model.element.Modifier> modifiers = tree.getModifiers().getFlags();
        String visibility = modifiers.contains(javax.lang.model.element.Modifier.PUBLIC) ? "public "
                : modifiers.contains(javax.lang.model.element.Modifier.PRIVATE) ? "private "
                : modifiers.contains(javax.lang.model.element.Modifier.PROTECTED) ? "protected " : "";
        StringBuilder result = new StringBuilder();
        for (AnnotationTree annotation : tree.getModifiers().getAnnotations()) result.append(slice(source, unit, positions, annotation)).append('\n');
        result.append("@dev.openallay.value.ValueType(").append(name).append(".ValueSchemaProvider.class)\n")
                .append(visibility).append(nested ? "static " : "").append("final class ").append(name).append(typeParameters);
        if (!tree.getImplementsClause().isEmpty()) result.append(" implements ").append(String.join(", ", tree.getImplementsClause().stream().map(Object::toString).toList()));
        result.append(" {\n");
        for (Component component : components) result.append("    ").append(component.annotations()).append("private final ").append(component.type()).append(' ').append(component.name()).append(";\n");
        result.append("    ").append(visibility).append(name).append('(')
                .append(String.join(", ", components.stream().map(c -> c.type() + " " + c.name()).toList())).append(") {\n");
        if (compact != null) result.append(compact, 1, compact.length() - 1).append('\n');
        for (Component component : components) result.append("        this.").append(component.name()).append(" = ").append(component.name()).append(";\n");
        result.append("    }\n");
        for (Component component : components) if (!accessors.contains(component.name())) result.append("    public ").append(component.type()).append(' ').append(component.name()).append("() { return ").append(component.name()).append("; }\n");
        for (String member : members) result.append(member).append('\n');
        if (!overrides.contains("equals")) {
            result.append("    @Override public boolean equals(Object other) {\n        if (this == other) return true;\n        if (!(other instanceof ").append(name).append(")) return false;\n        ").append(name).append(" that = (").append(name).append(") other;\n        return ");
            result.append(components.isEmpty() ? "true" : String.join(" && ", components.stream().map(c -> equality(c)).toList())).append(";\n    }\n");
        }
        if (!overrides.contains("hashCode")) {
            result.append("    @Override public int hashCode() {\n        int hash = 0;\n");
            for (Component component : components) result.append("        hash = 31 * hash + ").append(hash(component)).append(";\n");
            result.append("        return hash;\n    }\n");
        }
        if (!overrides.contains("toString")) {
            result.append("    @Override public String toString() { return \"").append(name).append("[");
            for (int i = 0; i < components.size(); i++) {
                Component component = components.get(i);
                result.append(i == 0 ? "" : ", ").append(component.name()).append("=\" + ").append(component.name()).append(" + \"");
            }
            result.append("]\"; }\n");
        }
        result.append("    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {\n        public ValueSchemaProvider() {}\n        @Override public dev.openallay.value.ValueSchema<").append(name).append("> schema() {\n            return new dev.openallay.value.ValueSchema<>(").append(name).append(".class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<").append(name).append(">>asList(");
        result.append(String.join(", ", components.stream().map(c -> "new dev.openallay.value.ValueSchema.Component<>(" + name + ".class, \"" + c.name() + "\", " + name + "::" + c.name() + ")").toList()));
        result.append("), arguments -> new ").append(name).append("(");
        List<String> arguments = new ArrayList<>();
        for (int i = 0; i < components.size(); i++) arguments.add("(" + castType(components.get(i).type(), tree.getTypeParameters()) + ") arguments[" + i + "]");
        result.append(String.join(", ", arguments)).append("));\n        }\n    }\n}");
        return result.toString().replace("\n", newline);
    }

    private static String castType(String type, List<? extends TypeParameterTree> variables) {
        Map<String, String> primitives = Map.of("boolean", "Boolean", "byte", "Byte", "short", "Short", "char", "Character", "int", "Integer", "long", "Long", "float", "Float", "double", "Double");
        if (primitives.containsKey(type)) return primitives.get(type);
        for (TypeParameterTree variable : variables) if (type.equals(variable.getName().toString())) return "Object";
        if (type.contains("<")) return type.substring(0, type.indexOf('<'));
        return type;
    }
    private static String equality(Component c) {
        return switch (c.type()) {
            case "float" -> "Float.compare(" + c.name() + ", that." + c.name() + ") == 0";
            case "double" -> "Double.compare(" + c.name() + ", that." + c.name() + ") == 0";
            case "boolean", "byte", "short", "char", "int", "long" -> c.name() + " == that." + c.name();
            default -> "java.util.Objects.equals(" + c.name() + ", that." + c.name() + ")";
        };
    }
    private static String hash(Component c) {
        String wrapper = switch (c.type()) {
            case "boolean" -> "Boolean"; case "byte" -> "Byte"; case "short" -> "Short";
            case "char" -> "Character"; case "int" -> "Integer"; case "long" -> "Long";
            case "float" -> "Float"; case "double" -> "Double"; default -> "java.util.Objects";
        };
        return wrapper + ".hashCode(" + c.name() + ")";
    }
    private static List<String> names(List<? extends VariableTree> parameters) { return parameters.stream().map(v -> v.getName().toString()).toList(); }
    private static String slice(String source, CompilationUnitTree unit, SourcePositions positions, Tree tree) {
        return source.substring(position(positions.getStartPosition(unit, tree)), position(positions.getEndPosition(unit, tree)));
    }
    private static int position(long value) { if (value < 0 || value > Integer.MAX_VALUE) throw new IllegalArgumentException("Missing source span"); return (int) value; }
    private static int bodyStart(String text, int start, int end) {
        int parentheses = 0; boolean quote = false, escaped = false;
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (quote) { if (escaped) escaped = false; else if (c == '\\') escaped = true; else if (c == '"') quote = false; continue; }
            if (c == '"') quote = true; else if (c == '(') parentheses++; else if (c == ')') parentheses--; else if (c == '{' && parentheses == 0) return i;
        }
        throw new IllegalArgumentException("No record body");
    }
    private static String patch(String path, String before, String after) {
        String[] left = before.split("(?<=\\n)", -1), right = after.split("(?<=\\n)", -1);
        StringBuilder out = new StringBuilder("--- a/" + path + "\n+++ b/" + path + "\n@@ -1," + (left.length - 1) + " +1," + (right.length - 1) + " @@\n");
        for (String line : left) if (!line.isEmpty()) out.append('-').append(line);
        for (String line : right) if (!line.isEmpty()) out.append('+').append(line);
        return out.toString();
    }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static void fail(String message) { throw new IllegalArgumentException(message); }
}
