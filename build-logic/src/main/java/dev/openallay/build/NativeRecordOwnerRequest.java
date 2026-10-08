package dev.openallay.build;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.tools.*;

/** Parse exact selected original native source owners; discover record paths without native type aliases. */
public final class NativeRecordOwnerRequest {
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static List<String> identities(CompilationUnitTree unit, boolean rejectRecords) {
        List<String> names=new ArrayList<>();
        String pkg=unit.getPackageName()==null?"":unit.getPackageName().toString();
        new TreePathScanner<Void,String>() {
            @Override public Void visitClass(ClassTree tree,String parent) {
                String name=parent==null?tree.getSimpleName().toString():parent+"."+tree.getSimpleName();
                if(tree.getSimpleName().length()==0)return super.visitClass(tree,parent);
                if(tree.getKind()==Tree.Kind.RECORD && rejectRecords)throw new IllegalArgumentException("Candidate still contains record "+name);
                boolean nativeOriginal=!name.endsWith(".ValueSchemaProvider");
                if(nativeOriginal) {
                    Set<String> modifiers=new TreeSet<>();
                    for(javax.lang.model.element.Modifier m:tree.getModifiers().getFlags())
                        if(m==javax.lang.model.element.Modifier.PUBLIC || m==javax.lang.model.element.Modifier.PROTECTED || m==javax.lang.model.element.Modifier.PRIVATE)modifiers.add(m.toString());
                    String kind=tree.getKind()==Tree.Kind.RECORD?"CLASS":tree.getKind().toString();
                    names.add(pkg+"\t"+name+"\t"+kind+"\t"+String.join(",",modifiers));
                }
                return super.visitClass(tree,name);
            }
        }.scan(unit,null);
        return names;
    }
    private static CompilationUnitTree parse(JavaCompiler compiler,StandardJavaFileManager manager,Path path) throws Exception {
        String text=Files.readString(path,StandardCharsets.UTF_8);
        DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
        JavaFileObject input=new SimpleJavaFileObject(path.toUri(),JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignore){return text;}
        };
        JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("--source","17","-proc:none"),null,List.of(input));
        CompilationUnitTree unit=task.parse().iterator().next();
        for(Diagnostic<?> d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR)
            throw new IllegalArgumentException("Actual candidate parse failed: "+d);
        return unit;
    }
    private static void verifyPost(Path request,Path output) throws Exception {
        if(Files.exists(output))throw new IllegalArgumentException("Fresh parse receipt required");
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();if(compiler==null)throw new IllegalStateException("Actual full JDK required");
        List<String> rows=new ArrayList<>();Set<Path> originals=new HashSet<>();Set<Path> posts=new HashSet<>();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(null,Locale.ROOT,StandardCharsets.UTF_8)) {
            for(String row:Files.readAllLines(request,StandardCharsets.UTF_8)) {
                String[] cells=row.split("\t",-1);
                if(cells.length!=5)throw new IllegalArgumentException("logicalPath originalPath originalSha postPath postSha required");
                Path before=Path.of(cells[1]).toRealPath(),after=Path.of(cells[3]).toRealPath();
                if(!originals.add(before)||!posts.add(after)||before.equals(after))throw new IllegalArgumentException("Exact distinct unique source owners required");
                if(!sha(Files.readAllBytes(before)).equals(cells[2]) || !sha(Files.readAllBytes(after)).equals(cells[4]))throw new IllegalArgumentException("Candidate parse bytes changed");
                CompilationUnitTree original=parse(compiler,manager,before),candidate=parse(compiler,manager,after);
                List<String> pre=identities(original,false),post=identities(candidate,true);
                // Record members are implicitly public/static only in interfaces. Add equivalent explicit visibility from candidate.
                for(int i=0;i<pre.size();i++) {
                    String[] a=pre.get(i).split("\t",-1),b=post.size()>i?post.get(i).split("\t",-1):new String[0];
                    if(b.length==4 && a[0].equals(b[0]) && a[1].equals(b[1]) && a[2].equals(b[2]) && a[3].isEmpty() && b[3].equals("public")) {
                        String owner=a[1].contains(".")?a[1].substring(0,a[1].lastIndexOf('.')):"";
                        boolean inInterface=pre.stream().anyMatch(n->n.startsWith(a[0]+"\t"+owner+"\tINTERFACE\t"));
                        if(inInterface)pre.set(i,post.get(i));
                    }
                }
                if(!pre.equals(post))throw new IllegalArgumentException("Package/class/privacy identity changed: "+cells[0]+" before="+pre+" after="+post);
                rows.add(cells[0]+"\t"+cells[2]+"\t"+cells[4]+"\tPARSE_PASS\t0-records\t"+pre.size());
            }
        }
        Files.write(output,rows,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
    public static void main(String[] args) throws Exception {
        if(args.length==3 && args[0].equals("--verify-post")) {
            verifyPost(Path.of(args[1]),Path.of(args[2]));return;
        }
        if(args.length!=3) throw new IllegalArgumentException("canonicalSourceRoot units.tsv fresh-record-request.tsv");
        Path root=Path.of(args[0]).toRealPath(),units=Path.of(args[1]),output=Path.of(args[2]);
        if(Files.exists(output) || output.toAbsolutePath().normalize().startsWith(root)) throw new IllegalArgumentException("Fresh external request required");
        List<String> request=new ArrayList<>();Set<Path> paths=new HashSet<>();
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        if(compiler==null)throw new IllegalStateException("Actual full compiler required");
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(null,Locale.ROOT,StandardCharsets.UTF_8)) {
            for(String row:Files.readAllLines(units,StandardCharsets.UTF_8)) {
                String[] cells=row.split("\t",-1);
                if(cells.length!=4)throw new IllegalArgumentException("Exact selected unit ownership required");
                Path path=Path.of(cells[3]).toRealPath();
                if(!path.startsWith(root) || !paths.add(path))throw new IllegalArgumentException("Foreign/duplicate selected source owner");
                byte[] bytes=Files.readAllBytes(path);String text=new String(bytes,StandardCharsets.UTF_8);
                if(!Arrays.equals(bytes,text.getBytes(StandardCharsets.UTF_8)))throw new IllegalArgumentException("Invalid original UTF8");
                DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
                JavaFileObject input=new SimpleJavaFileObject(path.toUri(),JavaFileObject.Kind.SOURCE) {
                    @Override public CharSequence getCharContent(boolean ignore){return text;}
                };
                JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("--source","17","-proc:none"),null,List.of(input));
                CompilationUnitTree unit=task.parse().iterator().next();
                List<String> records=new ArrayList<>();
                new TreePathScanner<Void,String>() {
                    @Override public Void visitClass(ClassTree tree,String parent) {
                        String name=parent==null?tree.getSimpleName().toString():parent+"."+tree.getSimpleName();
                        if(tree.getKind()==Tree.Kind.RECORD) records.add(name);
                        return super.visitClass(tree,name);
                    }
                }.scan(unit,null);
                for(Diagnostic<?> diagnostic:diagnostics.getDiagnostics())if(diagnostic.getKind()==Diagnostic.Kind.ERROR)
                    throw new IllegalArgumentException("Actual selected native source parse failed: "+diagnostic);
                if(!records.isEmpty())request.add(root.relativize(path).toString().replace(java.io.File.separatorChar,'/')+"\t"+sha(bytes)+"\t"+String.join(",",records));
            }
        }
        Files.write(output,request,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
}
