package dev.openallay.build;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.lang.model.element.*;
import javax.tools.*;

/** Actual native class-symbol projection back to canonical source spellings. No member or text guessing. */
public final class NativeCanonicalSymbolProjector {
    private record Edit(int start,int end,String replacement) {}
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static int pos(long value){if(value<0||value>Integer.MAX_VALUE)throw new IllegalArgumentException("Missing AST source span");return(int)value;}
    private static boolean nativeType(String name){return name.startsWith("net.minecraft.")||name.startsWith("com.mojang.blaze3d.")||name.startsWith("com.mojang.math.");}
    public static void main(String[] args)throws Exception{
        if(args.length!=5)throw new IllegalArgumentException("completeActualRoot genuineClasspath units.tsv curatedClasses.tsv freshOutput");
        Path root=Path.of(args[0]).toRealPath(),out=Path.of(args[4]).toAbsolutePath().normalize();
        if(Files.exists(out)||out.startsWith(root)||root.startsWith(out))throw new IllegalArgumentException("Fresh external output required");
        Map<String,String> inverse=new TreeMap<>();
        for(String row:Files.readAllLines(Path.of(args[3]))){if(row.isBlank()||row.startsWith("#"))continue;String[] cells=row.split("\t",-1);
            if(cells.length!=3||inverse.putIfAbsent(cells[1],cells[0])!=null)throw new IllegalArgumentException("Unique exact curated inverse class identity required");}
        Map<String,String> modes=new TreeMap<>();Map<String,String> owners=new TreeMap<>();
        for(String row:Files.readAllLines(Path.of(args[2]))){String[] cells=row.split("\t",-1);if(cells.length!=4||modes.putIfAbsent(cells[1],cells[2])!=null)throw new IllegalArgumentException("Exact unit mode required");owners.put(cells[1],cells[3]);}
        List<java.io.File> files;try(var paths=Files.walk(root)){files=paths.filter(p->p.toString().endsWith(".java")).sorted().map(Path::toFile).toList();}
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
        Map<String,byte[]> originals=new TreeMap<>(),products=new TreeMap<>();List<String> statuses=new ArrayList<>();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,StandardCharsets.UTF_8)){
            JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("--release","17","-proc:none","-encoding","UTF-8","-classpath",args[1]),null,manager.getJavaFileObjectsFromFiles(files));
            List<CompilationUnitTree> units=new ArrayList<>();task.parse().forEach(units::add);Trees trees=Trees.instance(task);SourcePositions positions=trees.getSourcePositions();
            // Snapshot original public parse-node identities/spans before attribution inserts inferred nodes.
            Set<Tree> originalNodes=Collections.newSetFromMap(new IdentityHashMap<>());
            Map<Tree,long[]> originalSpans=new IdentityHashMap<>();
            for(CompilationUnitTree parsed:units) new TreeScanner<Void,Void>() {
                @Override public Void scan(Tree node,Void unused) {
                    if(node!=null) {
                        originalNodes.add(node);
                        originalSpans.put(node,new long[]{positions.getStartPosition(parsed,node),positions.getEndPosition(parsed,node)});
                    }
                    return super.scan(node,unused);
                }
            }.scan(parsed,null);
            task.analyze();
            for(Diagnostic<?> d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR)throw new IllegalStateException("Whole actual native source attribution failed: "+d);
            for(CompilationUnitTree unit:units){
                String name=root.relativize(Path.of(unit.getSourceFile().toUri())).toString().replace(java.io.File.separatorChar,'/');
                if(!modes.containsKey(name))throw new IllegalArgumentException("Unowned generated source "+name);
                byte[] bytes=Files.readAllBytes(root.resolve(name));String text=new String(bytes,StandardCharsets.UTF_8);originals.put(name,bytes);
                if(modes.get(name).equals("ACTUAL_MCP")){products.put(name,bytes);statuses.add(name+"\tSUPPORTED\t0\tACTUAL_MCP");continue;}
                if(!modes.get(name).equals("CANONICAL_SEMANTIC"))throw new IllegalArgumentException("Exact curated unit mode required");
                List<Edit> edits=new ArrayList<>();List<String> rejects=new ArrayList<>();
                class Binder extends TreePathScanner<Void,Void>{
                    private boolean project(Tree node){
                        // Compiler-injected inferred lambda parameter types have no original source token.
                        // Omit only nodes absent from the authenticated pre-attribution parse identity set.
                        if(!originalNodes.contains(node)) return true;
                        Element symbol=trees.getElement(getCurrentPath());
                        if(!(symbol instanceof TypeElement type))return false;
                        String actual=task.getElements().getBinaryName(type).toString();if(!nativeType(actual))return false;
                        String canonical=inverse.get(actual);
                        if(canonical==null){rejects.add("No reviewed canonical native identity for "+actual);return true;}
                        long[] span=originalSpans.get(node);
                        int start=pos(span[0]),end=pos(span[1]);
                        if(end<start || end>text.length())throw new IllegalArgumentException("Original native class span invalid in "+name+" role="+node.getKind()+" identity="+actual);
                        String spelling=text.substring(start,end);
                        String replacement=node instanceof IdentifierTree?canonical.substring(canonical.lastIndexOf('.')+1).replace('$','.'):
                            canonical.replace('$','.');
                        // A simple nested reference remains source-addressable via its enclosing mapped owner.
                        if(node instanceof IdentifierTree && canonical.contains("$"))replacement=canonical.substring(canonical.lastIndexOf('$')+1);
                        if(!spelling.equals(replacement))edits.add(new Edit(start,end,replacement));return true;
                    }
                    @Override public Void visitIdentifier(IdentifierTree node,Void unused){project(node);return null;}
                    @Override public Void visitMemberSelect(MemberSelectTree node,Void unused){if(project(node))return null;return super.visitMemberSelect(node,unused);}
                    @Override public Void visitImport(ImportTree node,Void unused){
                        if(node.getQualifiedIdentifier().toString().endsWith(".*") && nativeType(node.getQualifiedIdentifier().toString())){
                            rejects.add("Native wildcard source projection not admitted");return null;}
                        return super.visitImport(node,unused);
                    }
                }
                new Binder().scan(unit,null);
                if(!rejects.isEmpty()){statuses.add(name+"\tREJECTED\t"+Base64.getEncoder().encodeToString(String.join("\n",rejects).getBytes(StandardCharsets.UTF_8)));continue;}
                edits.sort(Comparator.comparingInt(Edit::start).reversed());StringBuilder post=new StringBuilder(text);int boundary=text.length();
                for(Edit edit:edits){if(edit.end()>boundary)throw new IllegalStateException("Overlapping native class-symbol edits "+name);post.replace(edit.start(),edit.end(),edit.replacement());boundary=edit.start();}
                products.put(name,post.toString().getBytes(StandardCharsets.UTF_8));statuses.add(name+"\tSUPPORTED\t"+edits.size()+"\tCANONICAL_SEMANTIC");
            }
        }
        if(!originals.keySet().equals(modes.keySet()))throw new IllegalArgumentException("Incomplete exact source universe");
        for(var entry:originals.entrySet())if(!Arrays.equals(entry.getValue(),Files.readAllBytes(root.resolve(entry.getKey()))))throw new IllegalStateException("Native source changed during public attribution");
        Files.createDirectories(out);
        for(var entry:products.entrySet()){Path path=out.resolve("post").resolve(entry.getKey());Files.createDirectories(path.getParent());Files.write(path,entry.getValue());}
        Files.write(out.resolve("owner-status.tsv"),statuses,StandardCharsets.UTF_8);
    }
}
