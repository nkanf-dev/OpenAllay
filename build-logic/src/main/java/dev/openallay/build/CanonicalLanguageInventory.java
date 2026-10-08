package dev.openallay.build;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;

/** Build-only read-only full canonical production frontier. No source patch is emitted. */
public final class CanonicalLanguageInventory {
    private static String quote(String text) {
        StringBuilder out=new StringBuilder("\"");
        for(char c:text.toCharArray())switch(c){
            case '"' -> out.append("\\\"");case '\\' -> out.append("\\\\");case '\n' -> out.append("\\n");case '\r' -> out.append("\\r");case '\t' -> out.append("\\t");
            default -> {if(c<32)out.append(String.format("\\u%04x",(int)c));else out.append(c);}
        }
        return out.append('"').toString();
    }
    private static String counts(Map<String,Integer> map){
        List<String> items=new ArrayList<>();map.forEach((k,v)->items.add(quote(k)+":"+v));return "{"+String.join(",",items)+"}";
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=3)throw new IllegalArgumentException("actualCanonicalRoot genuineProductionClasspath freshOutputJSON");
        Path root=Paths.get(args[0]).toAbsolutePath().normalize();List<Path> files;
        try(var stream=Files.walk(root)){files=stream.filter(p->p.toString().endsWith(".java")).sorted().toList();}
        Set<String> owners=new TreeSet<>();Map<String,String> raw=new TreeMap<>();
        for(Path path:files){String name=root.relativize(path).toString().replace(java.io.File.separatorChar,'/');owners.add(name);raw.put(name,Files.readString(path,StandardCharsets.UTF_8));}
        CanonicalVarTypePort.Result attributed=CanonicalVarTypePort.attribute(root,args[1],owners);
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
        List<String> rows=new ArrayList<>();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,StandardCharsets.UTF_8)){
            JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("--release","17","-proc:none","-encoding","UTF-8","-classpath",args[1]),null,manager.getJavaFileObjectsFromPaths(files));
            for(CompilationUnitTree unit:task.parse()){
                String name=root.relativize(Paths.get(unit.getSourceFile().toUri())).toString().replace(java.io.File.separatorChar,'/');Map<String,Integer> count=new TreeMap<>();List<String> records=new ArrayList<>();
                new TreePathScanner<Void,String>(){
                    void add(String key){count.merge(key,1,Integer::sum);}
                    @Override public Void visitClass(ClassTree tree,String parent){String path=parent==null?tree.getSimpleName().toString():parent+"."+tree.getSimpleName();if(tree.getKind()==Tree.Kind.RECORD){add("records");records.add(path);}if(tree.getModifiers().getFlags().contains(javax.lang.model.element.Modifier.SEALED))add("sealedDeclarations");return super.visitClass(tree,path);}
                    @Override public Void visitBindingPattern(BindingPatternTree tree,String parent){add("bindingPatterns");return super.visitBindingPattern(tree,parent);}
                    @Override public Void visitSwitch(SwitchTree tree,String parent){add("switchStatements");return super.visitSwitch(tree,parent);}
                    @Override public Void visitSwitchExpression(SwitchExpressionTree tree,String parent){add("switchExpressions");return super.visitSwitchExpression(tree,parent);}
                    @Override public Void visitCase(CaseTree tree,String parent){if(tree.getCaseKind()==CaseTree.CaseKind.RULE)add("arrowCases");return super.visitCase(tree,parent);}
                }.scan(unit,null);
                count.put("parsedVarSites",attributed.parsedCounts().get(name));
                long rendered=attributed.sites().stream().filter(site->site.path().equals(name)).count();count.put("denotableVarSites",Math.toIntExact(rendered));count.put("rejectedVarSites",count.get("parsedVarSites")-Math.toIntExact(rendered));
                byte[] bytes=raw.get(name).getBytes(StandardCharsets.UTF_8);String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
                List<String> recordNames=records.stream().map(CanonicalLanguageInventory::quote).toList();
                rows.add("{\"path\":"+quote(name)+",\"rawSha256\":"+quote(hash)+",\"rawBytes\":"+bytes.length+",\"counts\":"+counts(count)+",\"recordPaths\":["+String.join(",",recordNames)+"],\"rejectedForms\":"+counts(attributed.rejectedByOwner().getOrDefault(name,Collections.emptyMap()))+"}");
            }
            for(Diagnostic<?> d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR)throw new IllegalStateException("Parse failed: "+d);
        }
        for(Path path:files){String name=root.relativize(path).toString().replace(java.io.File.separatorChar,'/');if(!Files.readString(path,StandardCharsets.UTF_8).equals(raw.get(name)))throw new IllegalStateException("Source changed during inventory");}
        String report="{\"sourceCount\":"+attributed.sources()+",\"allParsedVarSites\":"+attributed.allParsedSites()+",\"denotableVarSites\":"+attributed.sites().size()+",\"rejectedForms\":"+counts(attributed.rejected())+",\"owners\":["+String.join(",",rows)+"],\"sourceEditsEmitted\":false}\n";
        Files.writeString(Paths.get(args[2]),report,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);System.out.println("PASS actual fullsource public compiler inventory: owners="+owners.size()+" var="+attributed.allParsedSites()+" rejected="+attributed.rejected());
    }
}
