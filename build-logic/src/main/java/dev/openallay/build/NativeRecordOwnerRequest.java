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
    public static void main(String[] args) throws Exception {
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
