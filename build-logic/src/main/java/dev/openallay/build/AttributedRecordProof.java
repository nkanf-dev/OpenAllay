package dev.openallay.build;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import javax.tools.*;

/** Exact public attributed constructor/override evidence feeding the single record converter. */
public final class AttributedRecordProof {
    private AttributedRecordProof() {}
    private record Owner(String name, Path file, Set<String> records, byte[] bytes) {}
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static int offset(long value) { if(value<0||value>Integer.MAX_VALUE)throw new IllegalArgumentException("Missing attributed source offset");return(int)value; }
    public static void main(String[] args) throws Exception {
        if(args.length!=4)throw new IllegalArgumentException("completeActualSourceRoot actualProductionClasspath exactRequestTSV freshExternalOutput");
        Path root=Paths.get(args[0]).toAbsolutePath().normalize(),output=Paths.get(args[3]).toAbsolutePath().normalize();
        if(Files.exists(output)||output.startsWith(root)||root.startsWith(output))throw new IllegalArgumentException("Fresh external output only");
        Map<String,Owner> selected=new TreeMap<>();
        for(String line:Files.readAllLines(Paths.get(args[2]),StandardCharsets.UTF_8)){
            String[] row=line.split("\t",-1);if(row.length!=3)throw new IllegalArgumentException("path rawSHA exactRecordPaths required");
            Path file=root.resolve(row[0]).normalize();if(!file.startsWith(root))throw new IllegalArgumentException("Escaping owner");
            byte[] bytes=Files.readAllBytes(file);if(!sha(bytes).equals(row[1]))throw new IllegalArgumentException("Original owner differs");
            if(selected.put(row[0],new Owner(row[0],file,new TreeSet<>(Arrays.asList(row[2].split(","))),bytes))!=null)throw new IllegalArgumentException("Duplicate owner");
        }
        List<File> files=new ArrayList<>();try(var stream=Files.walk(root)){stream.filter(p->p.toString().endsWith(".java")).sorted().forEach(p->files.add(p.toFile()));}
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();if(compiler==null)throw new IllegalStateException("Full tooling JDK required");
        DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();Map<String,String> products=new TreeMap<>();List<String> decisions=new ArrayList<>();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,StandardCharsets.UTF_8)){
            JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("--release","17","-proc:none","-encoding","UTF-8","-classpath",args[1]),null,manager.getJavaFileObjectsFromFiles(files));
            List<CompilationUnitTree> units=new ArrayList<>();task.parse().forEach(units::add);Trees trees=Trees.instance(task);SourcePositions positions=trees.getSourcePositions();
            task.analyze();for(Diagnostic<?> d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR)throw new IllegalStateException("Actual complete source attribution failed: "+d);
            Elements elements=task.getElements();Types types=task.getTypes();
            for(CompilationUnitTree unit:units){
                String name=root.relativize(Paths.get(unit.getSourceFile().toUri())).toString().replace(File.separatorChar,'/');Owner owner=selected.get(name);if(owner==null)continue;
                Map<Integer,Boolean> constructors=new HashMap<>(),overrides=new HashMap<>();Map<Integer,String> headers=new HashMap<>();Set<String> found=new TreeSet<>();
                new TreePathScanner<Void,String>(){
                    @Override public Void visitClass(ClassTree tree,String parent){
                        String path=parent==null?tree.getSimpleName().toString():parent+"."+tree.getSimpleName();
                        if(tree.getKind()!=Tree.Kind.RECORD||!owner.records().contains(path))return super.visitClass(tree,path);
                        found.add(path);TypeElement record=(TypeElement)trees.getElement(getCurrentPath());List<? extends RecordComponentElement> components=record.getRecordComponents();
                        for(Tree member:tree.getMembers())if(member instanceof MethodTree method){
                            TreePath methodPath=new TreePath(getCurrentPath(),method);Element resolved=trees.getElement(methodPath);
                            long raw=positions.getStartPosition(unit,method);if(raw<0)continue;int start=offset(raw);
                            if(!(resolved instanceof ExecutableElement executable))throw new IllegalStateException("Missing attributed method");
                            if(method.getBody()!=null)headers.put(start,new String(owner.bytes(),StandardCharsets.UTF_8).substring(start,offset(positions.getStartPosition(unit,method.getBody()))));
                            if(method.getName().contentEquals("<init>")){
                                if(method.getParameters().size()!=components.size())continue;
                                boolean names=true;for(int i=0;i<components.size();i++)names&=method.getParameters().get(i).getName().contentEquals(components.get(i).getSimpleName());
                                if(!names)continue;
                                boolean canonical=true;for(int i=0;i<components.size();i++)canonical&=types.isSameType(executable.getParameters().get(i).asType(),components.get(i).asType());
                                constructors.put(start,canonical);decisions.add(name+"\t"+path+"\tconstructor\t"+start+"\tcanonical="+canonical+"\t"+executable);
                            }else if(method.getParameters().isEmpty()){
                                RecordComponentElement component=components.stream().filter(c->c.getSimpleName().contentEquals(method.getName())).findFirst().orElse(null);if(component==null)continue;
                                if(!executable.getModifiers().contains(Modifier.PUBLIC)||!types.isSameType(executable.getReturnType(),component.asType())||!executable.getTypeParameters().isEmpty()||!executable.getThrownTypes().isEmpty()||method.getBody()==null)throw new IllegalArgumentException("Unsupported attributed component accessor signature");
                                boolean genuine=false;List<TypeMirror> pending=new ArrayList<>(record.getInterfaces());Set<String> seen=new HashSet<>();
                                while(!pending.isEmpty()){
                                    TypeMirror contract=pending.remove(pending.size()-1);if(!seen.add(contract.toString()))continue;Element target=types.asElement(contract);if(!(target instanceof TypeElement iface)||iface.getKind()!=ElementKind.INTERFACE)throw new IllegalArgumentException("Unresolved interface contract");
                                    for(Element entry:elements.getAllMembers(iface))if(entry instanceof ExecutableElement candidate&&candidate.getKind()==ElementKind.METHOD&&!candidate.getModifiers().contains(Modifier.STATIC)&&elements.overrides(executable,candidate,record))genuine=true;
                                    pending.addAll(iface.getInterfaces());
                                }
                                overrides.put(start,genuine);decisions.add(name+"\t"+path+"\tcomponent-accessor\t"+start+"\tgenuine-interface-override="+genuine+"\t"+executable);
                            }
                        }
                        return super.visitClass(tree,path);
                    }
                }.scan(unit,null);
                if(!found.equals(owner.records()))throw new IllegalArgumentException("Selected attributed records differ: "+name);
                String text=new String(owner.bytes(),StandardCharsets.UTF_8);RecordValueSourceConverter.AttributedProof proof=new RecordValueSourceConverter.AttributedProof(sha(owner.bytes()),constructors,overrides,headers);
                products.put(name,RecordValueSourceConverter.convert(owner.file(),text,owner.records(),proof));
            }
        }
        if(!products.keySet().equals(selected.keySet()))throw new IllegalArgumentException("Attributed owner frontier differs");
        for(Owner owner:selected.values())if(!Arrays.equals(owner.bytes(),Files.readAllBytes(owner.file())))throw new IllegalStateException("Actual source drift");
        Files.createDirectories(output);for(var entry:products.entrySet())for(String side:List.of("pre","post")){
            Path file=output.resolve(side).resolve(entry.getKey());Files.createDirectories(file.getParent());Files.write(file,side.equals("pre")?selected.get(entry.getKey()).bytes():entry.getValue().getBytes(StandardCharsets.UTF_8));
        }
        Files.write(output.resolve("attributed-decisions.tsv"),decisions,StandardCharsets.UTF_8);System.out.println("PASS exact public attributed record decisions owners="+products.size());
    }
}
