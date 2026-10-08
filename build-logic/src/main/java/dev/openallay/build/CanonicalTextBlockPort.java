package dev.openallay.build;
import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
/** Exact public LiteralTree.getValue text block port; no indentation or escape guesses. */
public final class CanonicalTextBlockPort {
    private record Edit(int start,int end,String value){}
    private static String quoted(String value){StringBuilder out=new StringBuilder("\"");for(char c:value.toCharArray())switch(c){case '\n'->out.append("\\n");case '\r'->out.append("\\r");case '\t'->out.append("\\t");case '\b'->out.append("\\b");case '\f'->out.append("\\f");case '"'->out.append("\\\"");case '\\'->out.append("\\\\");default->{if(c<32||c==127)out.append(String.format("\\%03o",(int)c));else out.append(c);}}return out.append('"').toString();}
    public static void main(String[]args)throws Exception{if(args.length!=3)throw new IllegalArgumentException("actualSourceRoot exactSelectedPaths freshOutput");Path root=Paths.get(args[0]).toAbsolutePath().normalize(),out=Paths.get(args[2]).toAbsolutePath().normalize();if(Files.exists(out)||out.startsWith(root)||root.startsWith(out))throw new IllegalArgumentException("Fresh externaloutput");List<String> selected=Files.readAllLines(Paths.get(args[1]),StandardCharsets.UTF_8);JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();Map<String,byte[]> originals=new TreeMap<>(),products=new TreeMap<>();List<String> counts=new ArrayList<>();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,StandardCharsets.UTF_8)){
            for(String name:selected){Path path=root.resolve(name).normalize();if(!path.startsWith(root)||!Files.isRegularFile(path)||originals.containsKey(name))throw new IllegalArgumentException("Exact unique owner required");byte[] before=Files.readAllBytes(path);originals.put(name,before);String source=new String(before,StandardCharsets.UTF_8);JavaFileObject input=new SimpleJavaFileObject(path.toUri(),JavaFileObject.Kind.SOURCE){@Override public CharSequence getCharContent(boolean ignore){return source;}};JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("--source","17","-proc:none","-encoding","UTF-8"),null,List.of(input));CompilationUnitTree unit=task.parse().iterator().next();SourcePositions positions=Trees.instance(task).getSourcePositions();List<Edit> edits=new ArrayList<>();
                new TreeScanner<Void,Void>(){@Override public Void visitLiteral(LiteralTree tree,Void unused){long start=positions.getStartPosition(unit,tree),end=positions.getEndPosition(unit,tree);if(start>=0&&end>=start&&end<=source.length()&&source.startsWith("\"\"\"",(int)start)){if(!(tree.getValue()instanceof String value))throw new IllegalArgumentException("Textblock value not genuineString");edits.add(new Edit((int)start,(int)end,quoted(value)));}return super.visitLiteral(tree,unused);}}.scan(unit,null);
                for(Diagnostic<?>d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR)throw new IllegalArgumentException("Actualsource parse error: "+d);edits.sort(Comparator.comparingInt(Edit::start).reversed());StringBuilder post=new StringBuilder(source);for(Edit edit:edits)post.replace(edit.start(),edit.end(),edit.value());products.put(name,post.toString().getBytes(StandardCharsets.UTF_8));counts.add(name+"\t"+edits.size());
            }
        }
        for(var row:originals.entrySet())if(!Arrays.equals(row.getValue(),Files.readAllBytes(root.resolve(row.getKey()))))throw new IllegalStateException("Source drift");Files.createDirectories(out);for(var row:products.entrySet())for(String side:List.of("pre","post")){Path path=out.resolve(side).resolve(row.getKey());Files.createDirectories(path.getParent());Files.write(path,side.equals("pre")?originals.get(row.getKey()):row.getValue());}Files.write(out.resolve("literal-counts.tsv"),counts,StandardCharsets.UTF_8);System.out.println("PASS exactpublicAST textblock values owners="+products.size());
    }
}
