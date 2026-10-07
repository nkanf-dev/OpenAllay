package dev.openallay.build;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.lang.model.element.*;
import javax.tools.*;

/** Public attributed API invocation port; exact method owners, receiver/argument evaluation preserved. */
public final class CanonicalJava8ApiPort {
    private record Edit(int start,int end,String replacement) {}
    private record Site(CompilationUnitTree unit,TreePath path,MethodInvocationTree invocation,String owner,String method,boolean isStatic,String rejection) {}
    private static int pos(long value){if(value<0||value>Integer.MAX_VALUE)throw new IllegalArgumentException("Missing API sourceposition");return(int)value;}
    private static String apply(String text,int start,int end,List<Edit> edits){StringBuilder result=new StringBuilder(text.substring(start,end));List<Edit> sorted=new ArrayList<>(edits);sorted.sort(Comparator.comparingInt(Edit::start).reversed());int previous=end;for(Edit edit:sorted){if(edit.start()<start||edit.end()>end||edit.end()>previous)throw new IllegalArgumentException("Overlapping API spans");result.replace(edit.start()-start,edit.end()-start,edit.replacement());previous=edit.start();}return result.toString();}
    private static boolean selectedMethod(String owner,String method){return switch(owner){
        case "java.util.List","java.util.Set" -> method.equals("of")||method.equals("copyOf");
        case "java.util.Map" -> method.equals("of")||method.equals("copyOf")||method.equals("entry")||method.equals("ofEntries");
        case "java.util.stream.Stream" -> method.equals("toList");
        case "java.lang.String" -> Set.of("isBlank","strip","stripLeading","stripTrailing","repeat","lines").contains(method);
        case "java.lang.CharSequence","java.lang.StringBuilder" -> method.equals("isEmpty");
        case "java.time.Duration" -> method.equals("toSeconds");
        case "java.nio.file.Path" -> method.equals("of");
        case "java.util.concurrent.CompletableFuture" -> method.equals("failedFuture");
        case "java.util.HexFormat" -> method.equals("formatHex");
        default -> false;
    };}
    private static String helper(String owner,String method,int arity){return switch(owner){
        case "java.util.List" -> "dev.openallay.util.Java8Collections."+(method.equals("of")?"listOf":"listCopyOf");
        case "java.util.Set" -> "dev.openallay.util.Java8Collections."+(method.equals("of")?"setOf":"setCopyOf");
        case "java.util.Map" -> switch(method){case "of" -> {if(!Set.of(0,2,4,12).contains(arity))throw new IllegalArgumentException("Map.of arity has no approved evaluation-preserving helper overload: "+arity);yield "dev.openallay.util.Java8Collections.mapOf";}case "copyOf"->"dev.openallay.util.Java8Collections.mapCopyOf";case "entry"->"dev.openallay.util.Java8Collections.entry";case "ofEntries"->"dev.openallay.util.Java8Collections.mapOfEntries";default->throw new IllegalArgumentException();};
        case "java.util.stream.Stream" -> "dev.openallay.util.Java8Collections.toList";
        case "java.lang.String" -> "dev.openallay.util.Java8Strings."+method;
        case "java.util.concurrent.CompletableFuture" -> "dev.openallay.util.Java8Futures.failedFuture";
        case "java.util.HexFormat" -> "dev.openallay.util.Java8Hex.formatHex";
        default -> "";
    };}
    public static void main(String[] args)throws Exception{
        if(args.length!=4)throw new IllegalArgumentException("completeActualRoot genuineProductionClasspath selectedOwners freshExternalOutput");Path root=Paths.get(args[0]).toAbsolutePath().normalize(),output=Paths.get(args[3]).toAbsolutePath().normalize();if(Files.exists(output)||output.startsWith(root)||root.startsWith(output))throw new IllegalArgumentException("Fresh externaloutput only");Set<String> selected=new TreeSet<>(Files.readAllLines(Paths.get(args[2]),StandardCharsets.UTF_8));List<File> files=new ArrayList<>();try(var stream=Files.walk(root)){stream.filter(p->p.toString().endsWith(".java")).sorted().forEach(p->files.add(p.toFile()));}
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();Map<String,byte[]> before=new TreeMap<>(),after=new TreeMap<>();List<String> statuses=new ArrayList<>();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,StandardCharsets.UTF_8)){
            JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("--release","17","-proc:none","-encoding","UTF-8","-classpath",args[1]),null,manager.getJavaFileObjectsFromFiles(files));List<CompilationUnitTree> units=new ArrayList<>();task.parse().forEach(units::add);Trees trees=Trees.instance(task);SourcePositions positions=trees.getSourcePositions();task.analyze();for(Diagnostic<?> d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR)throw new IllegalStateException("Complete genuine source attribution failed: "+d);
            for(CompilationUnitTree unit:units){String name=root.relativize(Paths.get(unit.getSourceFile().toUri())).toString().replace(File.separatorChar,'/');if(!selected.contains(name))continue;byte[] bytes=Files.readAllBytes(root.resolve(name));before.put(name,bytes);String text=new String(bytes,StandardCharsets.UTF_8);List<Site> sites=new ArrayList<>();List<String> reasons=new ArrayList<>();
                new TreePathScanner<Void,Void>(){
                    @Override public Void visitMethodInvocation(MethodInvocationTree invocation,Void unused){Element element=trees.getElement(getCurrentPath());if(element instanceof ExecutableElement executable&&executable.getEnclosingElement() instanceof TypeElement type){String owner=type.getQualifiedName().toString(),method=executable.getSimpleName().toString();if(selectedMethod(owner,method)){String reject=null;
                        if(!(invocation.getMethodSelect() instanceof MemberSelectTree)&&!executable.getModifiers().contains(Modifier.STATIC))reject="Implicit receiver API call needs explicit qualifiedthis scope policy";
                        sites.add(new Site(unit,getCurrentPath(),invocation,owner,method,executable.getModifiers().contains(Modifier.STATIC),reject));}
                    }return super.visitMethodInvocation(invocation,unused);}
                    @Override public Void visitMemberReference(MemberReferenceTree reference,Void unused){Element element=trees.getElement(getCurrentPath());if(element instanceof ExecutableElement method&&method.getEnclosingElement() instanceof TypeElement type&&selectedMethod(type.getQualifiedName().toString(),method.getSimpleName().toString()))reasons.add("offset="+positions.getStartPosition(unit,reference)+" API method reference needs functional target adaptation");return super.visitMemberReference(reference,unused);}
                }.scan(unit,null);
                sites.sort(Comparator.comparingLong(site->positions.getEndPosition(unit,site.invocation())-positions.getStartPosition(unit,site.invocation())));List<Edit> edits=new ArrayList<>();
                for(Site site:sites){try{
                    if(site.rejection()!=null)throw new IllegalArgumentException(site.rejection());MethodInvocationTree invocation=site.invocation();int start=pos(positions.getStartPosition(unit,invocation)),end=pos(positions.getEndPosition(unit,invocation));List<Edit> inner=new ArrayList<>();for(Edit edit:edits)if(edit.start()>=start&&edit.end()<=end)inner.add(edit);
                    List<String> arguments=new ArrayList<>();for(ExpressionTree argument:invocation.getArguments()){int a=pos(positions.getStartPosition(unit,argument)),b=pos(positions.getEndPosition(unit,argument));List<Edit> argumentEdits=new ArrayList<>();for(Edit edit:inner)if(edit.start()>=a&&edit.end()<=b)argumentEdits.add(edit);arguments.add(apply(text,a,b,argumentEdits));}
                    String receiver=null;Tree receiverTree=null;
                    if(invocation.getMethodSelect() instanceof MemberSelectTree member){receiverTree=member.getExpression();int a=pos(positions.getStartPosition(unit,receiverTree)),b=pos(positions.getEndPosition(unit,receiverTree));List<Edit> receiverEdits=new ArrayList<>();for(Edit edit:inner)if(edit.start()>=a&&edit.end()<=b)receiverEdits.add(edit);receiver=apply(text,a,b,receiverEdits);}
                    if(site.isStatic()&&receiverTree!=null){Element qualifier=trees.getElement(new TreePath(new TreePath(site.path(),invocation.getMethodSelect()),receiverTree));if(!(qualifier instanceof TypeElement))throw new IllegalArgumentException("Static API accessed through evaluated expression cannot discard receiver side effects");}
                    if(!site.isStatic()){
                        if(receiver==null)throw new IllegalArgumentException("Missing explicit receiver");
                        if(site.owner().equals("java.util.HexFormat")){
                            if(!(receiverTree instanceof MethodInvocationTree factory)||!factory.getArguments().isEmpty()||!factory.getTypeArguments().isEmpty())throw new IllegalArgumentException("HexFormat formatting state is not proven default");
                            Element factoryElement=trees.getElement(TreePath.getPath(unit,factory));if(!(factoryElement instanceof ExecutableElement method)||!method.getSimpleName().contentEquals("of")||!((TypeElement)method.getEnclosingElement()).getQualifiedName().contentEquals("java.util.HexFormat"))throw new IllegalArgumentException("HexFormat state is not defaultof");
                        }else arguments.add(0,receiver);
                    }
                    String replacement;
                    if((site.owner().equals("java.lang.StringBuilder")||site.owner().equals("java.lang.CharSequence"))){if(!arguments.isEmpty()&&arguments.size()==1)replacement="("+receiver+").length() == 0";else throw new IllegalArgumentException("Unexpected StringBuilder.isEmpty signature");replacement="("+replacement+")";}
                    else if(site.owner().equals("java.time.Duration")){if(arguments.size()!=1)throw new IllegalArgumentException("Unexpected Duration.toSeconds signature");replacement="("+receiver+").getSeconds()";}
                    else if(site.owner().equals("java.nio.file.Path")){String typeArguments=invocation.getTypeArguments().isEmpty()?"":"<"+String.join(",",invocation.getTypeArguments().stream().map(Object::toString).toList())+">";replacement="java.nio.file.Paths."+typeArguments+"get("+String.join(", ",arguments)+")";}
                    else{String mapped=helper(site.owner(),site.method(),invocation.getArguments().size());String typeArguments=invocation.getTypeArguments().isEmpty()?"":"<"+String.join(",",invocation.getTypeArguments().stream().map(Object::toString).toList())+">";int dot=mapped.lastIndexOf('.');mapped=mapped.substring(0,dot+1)+typeArguments+mapped.substring(dot+1);replacement=mapped+"("+String.join(", ",arguments)+")";}
                    edits.removeAll(inner);edits.add(new Edit(start,end,replacement));
                }catch(IllegalArgumentException failure){reasons.add("offset="+positions.getStartPosition(unit,site.invocation())+" "+site.owner()+"."+site.method()+" "+failure.getMessage());}}
                if(!reasons.isEmpty()){statuses.add(name+"\tREJECTED\t"+sites.size()+"\t"+Base64.getEncoder().encodeToString(String.join("\n",reasons).getBytes(StandardCharsets.UTF_8)));continue;}
                after.put(name,apply(text,0,text.length(),edits).getBytes(StandardCharsets.UTF_8));statuses.add(name+"\tSUPPORTED\t"+sites.size()+"\t0");
            }
        }
        if(!before.keySet().equals(selected))throw new IllegalArgumentException("Selected source closure differs");for(var row:before.entrySet())if(!Arrays.equals(row.getValue(),Files.readAllBytes(root.resolve(row.getKey()))))throw new IllegalStateException("Source drift");Files.createDirectories(output);for(var row:after.entrySet())for(String side:List.of("pre","post")){Path path=output.resolve(side).resolve(row.getKey());Files.createDirectories(path.getParent());Files.write(path,side.equals("pre")?before.get(row.getKey()):row.getValue());}Files.write(output.resolve("owner-status.tsv"),statuses,StandardCharsets.UTF_8);System.out.println("PASS genuine attributed API mapping owners="+before.size()+" supported="+after.size());
    }
}
