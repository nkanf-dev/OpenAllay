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
        case "java.util.List" -> method.equals("of")||method.equals("copyOf")||method.equals("toArray");
        case "java.util.Set" -> method.equals("of")||method.equals("copyOf");
        case "java.util.Map" -> method.equals("of")||method.equals("copyOf")||method.equals("entry")||method.equals("ofEntries");
        case "java.util.stream.Stream" -> method.equals("toList");
        case "java.lang.String" -> Set.of("isBlank","strip","stripLeading","stripTrailing","repeat","lines","formatted").contains(method);
        case "java.util.Optional" -> Set.of("isEmpty","stream","orElseThrow").contains(method);
        case "java.util.OptionalInt","java.util.OptionalLong","java.util.OptionalDouble" -> method.equals("isEmpty")||method.equals("orElseThrow");
        case "java.util.stream.Collectors" -> method.equals("toUnmodifiableSet")||method.equals("toUnmodifiableList");
        case "java.nio.file.Files" -> method.equals("readString")||method.equals("writeString");
        case "java.io.InputStream" -> method.equals("readAllBytes")||method.equals("readNBytes");
        case "java.util.Collection" -> method.equals("toArray");
        case "java.lang.CharSequence","java.lang.StringBuilder" -> method.equals("isEmpty");
        case "java.time.Duration" -> method.equals("toSeconds");
        case "java.nio.file.Path" -> method.equals("of");
        case "java.util.concurrent.CompletableFuture" -> method.equals("failedFuture")||method.equals("orTimeout");
        case "java.util.HexFormat" -> method.equals("formatHex");
        default -> false;
    };}
    private static boolean selectedExecutable(String owner,String method,ExecutableElement executable) {
        if(!selectedMethod(owner,method))return false;
        List<? extends VariableElement> parameters=executable.getParameters();
        if(owner.startsWith("java.util.Optional")&&method.equals("orElseThrow"))return parameters.isEmpty();
        if(owner.equals("java.io.InputStream")&&method.equals("readNBytes"))return parameters.size()==1&&parameters.get(0).asType().getKind()==javax.lang.model.type.TypeKind.INT;
        if((owner.equals("java.util.Collection")||owner.equals("java.util.List"))&&method.equals("toArray"))return parameters.size()==1&&parameters.get(0).asType().toString().startsWith("java.util.function.IntFunction<");
        return true;
    }
    private static String helper(String owner,String method,int arity){return switch(owner){
        case "java.util.List" -> method.equals("toArray")?"dev.openallay.util.Java8ApiSupport.toArray":"dev.openallay.util.Java8Collections."+(method.equals("of")?"listOf":"listCopyOf");
        case "java.util.Set" -> "dev.openallay.util.Java8Collections."+(method.equals("of")?"setOf":"setCopyOf");
        case "java.util.Map" -> switch(method){case "of" -> {if(arity < 0 || arity > 20 || arity % 2 != 0)throw new IllegalArgumentException("Map.of arity has no approved evaluation-preserving helper overload: "+arity);yield "dev.openallay.util.Java8Collections.mapOf";}case "copyOf"->"dev.openallay.util.Java8Collections.mapCopyOf";case "entry"->"dev.openallay.util.Java8Collections.entry";case "ofEntries"->"dev.openallay.util.Java8Collections.mapOfEntries";default->throw new IllegalArgumentException();};
        case "java.util.stream.Stream" -> "dev.openallay.util.Java8Collections.toList";
        case "java.lang.String" -> method.equals("formatted")?"dev.openallay.util.Java8ApiSupport.formatted":"dev.openallay.util.Java8Strings."+method;
        case "java.util.Optional" -> "dev.openallay.util.Java8ApiSupport."+method;
        case "java.util.stream.Collectors" -> "dev.openallay.util.Java8ApiSupport."+method;
        case "java.nio.file.Files" -> "dev.openallay.util.Java8Files."+method;
        case "java.io.InputStream" -> "dev.openallay.util.Java8Streams."+method;
        case "java.util.Collection" -> "dev.openallay.util.Java8ApiSupport.toArray";
        case "java.util.concurrent.CompletableFuture" -> "dev.openallay.util.Java8Futures."+method;
        case "java.util.HexFormat" -> "dev.openallay.util.Java8Hex.formatHex";
        default -> "";
    };}
    /** Qualified generated type names must not bind a local/field/type named by the package root. */
    private static void unshadowedRoot(String qualified,TreePath use,Trees trees,JavacTask task){
        String root=qualified.substring(0,qualified.indexOf('.'));
        for(Scope scope=trees.getScope(use);scope!=null;scope=scope.getEnclosingScope()){
            for(Element element:scope.getLocalElements())if(element.getSimpleName().contentEquals(root)
                    && (element instanceof VariableElement||element instanceof TypeElement))
                throw new IllegalArgumentException("Generated package root is shadowed in actual scope: "+root+" by "+element.getKind());
            TypeElement enclosing=scope.getEnclosingClass();
            if(enclosing!=null)for(Element element:task.getElements().getAllMembers(enclosing))
                if(element.getSimpleName().contentEquals(root)&&(element instanceof VariableElement||element instanceof TypeElement))
                    throw new IllegalArgumentException("Generated package root is shadowed by actual enclosing member: "+root+" by "+element.getKind());
        }
        // A declaration's own initializer cannot resolve a package root with the variable's name.
        for(TreePath path=use;path!=null;path=path.getParentPath())if(path.getLeaf() instanceof VariableTree variable
                && variable.getName().contentEquals(root))throw new IllegalArgumentException("Generated package root shadows its declaration initializer: "+root);
    }
    private static String safePathsQualifier(CompilationUnitTree unit,TreePath use,Trees trees,JavacTask task,Set<CompilationUnitTree> imported){
        try{unshadowedRoot("java.nio.file.Paths",use,trees,task);return "java.nio.file.Paths";}catch(IllegalArgumentException packageShadow){
            TypeElement paths=task.getElements().getTypeElement("java.nio.file.Paths");
            if(paths==null||!trees.isAccessible(trees.getScope(use),paths))throw new IllegalArgumentException("Actual Paths type inaccessible");
            for(Scope scope=trees.getScope(use);scope!=null;scope=scope.getEnclosingScope()){
                for(Element element:scope.getLocalElements())if(element.getSimpleName().contentEquals("Paths")&&!element.equals(paths))throw new IllegalArgumentException("Generated Paths simple qualifier shadowed by "+element.getKind());
                TypeElement enclosing=scope.getEnclosingClass();if(enclosing!=null)for(Element member:task.getElements().getAllMembers(enclosing))if(member.getSimpleName().contentEquals("Paths")&&!member.equals(paths))throw new IllegalArgumentException("Generated Paths qualifier shadowed by enclosing member");
            }
            for(TreePath path=use;path!=null;path=path.getParentPath())if(path.getLeaf() instanceof VariableTree variable&&variable.getName().contentEquals("Paths"))throw new IllegalArgumentException("Paths qualifier shadows currentinitializer");
            for(ImportTree declaration:unit.getImports()){
                String name=declaration.getQualifiedIdentifier().toString();if(name.endsWith(".Paths")&&!name.equals("java.nio.file.Paths"))throw new IllegalArgumentException("Conflicting Paths import");
            }
            if(unit.getPackageName()!=null){TypeElement sibling=task.getElements().getTypeElement(unit.getPackageName()+".Paths");if(sibling!=null&&!sibling.equals(paths))throw new IllegalArgumentException("Package declares a competing Paths type");}
            final boolean[] collision={false};new TreeScanner<Void,Void>(){@Override public Void visitClass(ClassTree tree,Void unused){if(tree.getSimpleName().contentEquals("Paths"))collision[0]=true;return super.visitClass(tree,unused);}}.scan(unit,null);
            if(collision[0])throw new IllegalArgumentException("Source declares a Paths type");
            imported.add(unit);return "Paths";
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=4)throw new IllegalArgumentException("completeActualRoot genuineProductionClasspath selectedOwners freshExternalOutput");Path root=Paths.get(args[0]).toAbsolutePath().normalize(),output=Paths.get(args[3]).toAbsolutePath().normalize();if(Files.exists(output)||output.startsWith(root)||root.startsWith(output))throw new IllegalArgumentException("Fresh externaloutput only");Set<String> selected=new TreeSet<>(Files.readAllLines(Paths.get(args[2]),StandardCharsets.UTF_8));List<File> files=new ArrayList<>();try(var stream=Files.walk(root)){stream.filter(p->p.toString().endsWith(".java")).sorted().forEach(p->files.add(p.toFile()));}
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();Map<String,byte[]> before=new TreeMap<>(),after=new TreeMap<>();List<String> statuses=new ArrayList<>();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,StandardCharsets.UTF_8)){
            JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("--release","17","-proc:none","-encoding","UTF-8","-classpath",args[1]),null,manager.getJavaFileObjectsFromFiles(files));List<CompilationUnitTree> units=new ArrayList<>();task.parse().forEach(units::add);Trees trees=Trees.instance(task);SourcePositions positions=trees.getSourcePositions();task.analyze();for(Diagnostic<?> d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR)throw new IllegalStateException("Complete genuine source attribution failed: "+d);
            for(CompilationUnitTree unit:units){String name=root.relativize(Paths.get(unit.getSourceFile().toUri())).toString().replace(File.separatorChar,'/');if(!selected.contains(name))continue;byte[] bytes=Files.readAllBytes(root.resolve(name));before.put(name,bytes);String text=new String(bytes,StandardCharsets.UTF_8);Set<CompilationUnitTree> importedPaths=new HashSet<>();List<Site> sites=new ArrayList<>();List<Edit> referenceEdits=new ArrayList<>();List<String> reasons=new ArrayList<>();
                new TreePathScanner<Void,Void>(){
                    @Override public Void visitMethodInvocation(MethodInvocationTree invocation,Void unused){Element element=trees.getElement(getCurrentPath());if(element instanceof ExecutableElement executable&&executable.getEnclosingElement() instanceof TypeElement type){String owner=type.getQualifiedName().toString(),method=executable.getSimpleName().toString();if(selectedExecutable(owner,method,executable)){String reject=null;
                        if(!(invocation.getMethodSelect() instanceof MemberSelectTree)&&!executable.getModifiers().contains(Modifier.STATIC))reject="Implicit receiver API call needs explicit qualifiedthis scope policy";
                        sites.add(new Site(unit,getCurrentPath(),invocation,owner,method,executable.getModifiers().contains(Modifier.STATIC),reject));}
                    }return super.visitMethodInvocation(invocation,unused);}
                    @Override public Void visitMemberReference(MemberReferenceTree reference,Void unused){Element element=trees.getElement(getCurrentPath());if(element instanceof ExecutableElement method&&method.getEnclosingElement() instanceof TypeElement type&&selectedExecutable(type.getQualifiedName().toString(),method.getSimpleName().toString(),method)){
                        String declared=type.getQualifiedName().toString(),methodName=method.getSimpleName().toString();
                        Element qualifier=trees.getElement(new TreePath(getCurrentPath(),reference.getQualifierExpression()));
                        if(method.getModifiers().contains(Modifier.STATIC)&&qualifier instanceof TypeElement
                                && (Set.of("java.util.List","java.util.Set","java.util.Map").contains(declared)&&methodName.equals("copyOf")
                                    || declared.equals("java.util.List")&&methodName.equals("of"))){
                            javax.lang.model.type.TypeMirror target=trees.getTypeMirror(getCurrentPath());
                            if(target.getKind()!=javax.lang.model.type.TypeKind.DECLARED)throw new IllegalArgumentException("Methodreference target has no attributed functional type");
                            TypeElement targetType=(TypeElement)((javax.lang.model.type.DeclaredType)target).asElement();
                            long abstractMethods=task.getElements().getAllMembers(targetType).stream().filter(member->member.getKind()==ElementKind.METHOD&&member.getModifiers().contains(Modifier.ABSTRACT)).count();
                            if(abstractMethods!=1)throw new IllegalArgumentException("Methodreference target is not a unique public functional descriptor");
                            if(methodName.equals("of")){
                                ExecutableElement descriptor=(ExecutableElement)task.getElements().getAllMembers(targetType).stream().filter(member->member.getKind()==ElementKind.METHOD&&member.getModifiers().contains(Modifier.ABSTRACT)).findFirst().orElseThrow();
                                javax.lang.model.type.ExecutableType signature=(javax.lang.model.type.ExecutableType)task.getTypes().asMemberOf((javax.lang.model.type.DeclaredType)target,descriptor);
                                if(!signature.getParameterTypes().isEmpty()){
                                    reasons.add("offset="+positions.getStartPosition(unit,reference)+" List.of methodreference requires provenzero-argument Supplier descriptor");return super.visitMemberReference(reference,unused);
                                }
                            }
                            String mapped=helper(declared,methodName,methodName.equals("of")?0:1);
                            try{unshadowedRoot(mapped,getCurrentPath(),trees,task);}catch(IllegalArgumentException failure){reasons.add("offset="+positions.getStartPosition(unit,reference)+" "+failure.getMessage());return super.visitMemberReference(reference,unused);}
                            int dot=mapped.lastIndexOf('.');
                            String typeArguments=reference.getTypeArguments()==null||reference.getTypeArguments().isEmpty()?"":"<"+String.join(",",reference.getTypeArguments().stream().map(Object::toString).toList())+">";
                            referenceEdits.add(new Edit(pos(positions.getStartPosition(unit,reference)),pos(positions.getEndPosition(unit,reference)),mapped.substring(0,dot)+"::"+typeArguments+mapped.substring(dot+1)));
                        }else reasons.add("offset="+positions.getStartPosition(unit,reference)+" API method reference needs functional target adaptation");
                    }return super.visitMemberReference(reference,unused);}
                }.scan(unit,null);
                sites.sort(Comparator.comparingLong(site->positions.getEndPosition(unit,site.invocation())-positions.getStartPosition(unit,site.invocation())));List<Edit> edits=new ArrayList<>(referenceEdits);
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
                    if(site.owner().equals("java.util.OptionalInt")||site.owner().equals("java.util.OptionalLong")||site.owner().equals("java.util.OptionalDouble")) {
                        if(invocation.getArguments().size()!=0||receiver==null)throw new IllegalArgumentException("Primitive Optional overload not admitted");
                        if(site.method().equals("isEmpty"))replacement="(!("+receiver+").isPresent())";
                        else replacement="("+receiver+").orElseThrow(() -> new java.util.NoSuchElementException(\"No value present\"))";
                    }
                    else if((site.owner().equals("java.lang.StringBuilder")||site.owner().equals("java.lang.CharSequence"))){if(!arguments.isEmpty()&&arguments.size()==1)replacement="("+receiver+").length() == 0";else throw new IllegalArgumentException("Unexpected StringBuilder.isEmpty signature");replacement="("+replacement+")";}
                    else if(site.owner().equals("java.time.Duration")){if(arguments.size()!=1)throw new IllegalArgumentException("Unexpected Duration.toSeconds signature");replacement="("+receiver+").getSeconds()";}
                    else if(site.owner().equals("java.nio.file.Path")){String typeArguments=invocation.getTypeArguments().isEmpty()?"":"<"+String.join(",",invocation.getTypeArguments().stream().map(Object::toString).toList())+">";String qualifier=safePathsQualifier(unit,site.path(),trees,task,importedPaths);replacement=qualifier+"."+typeArguments+"get("+String.join(", ",arguments)+")";}
                    else{String mapped=helper(site.owner(),site.method(),invocation.getArguments().size());unshadowedRoot(mapped,site.path(),trees,task);String typeArguments=invocation.getTypeArguments().isEmpty()?"":"<"+String.join(",",invocation.getTypeArguments().stream().map(Object::toString).toList())+">";int dot=mapped.lastIndexOf('.');mapped=mapped.substring(0,dot+1)+typeArguments+mapped.substring(dot+1);replacement=mapped+"("+String.join(", ",arguments)+")";}
                    edits.removeAll(inner);edits.add(new Edit(start,end,replacement));
                }catch(IllegalArgumentException failure){reasons.add("offset="+positions.getStartPosition(unit,site.invocation())+" "+site.owner()+"."+site.method()+" "+failure.getMessage());}}
                if(!reasons.isEmpty()){statuses.add(name+"\tREJECTED\t"+(sites.size()+referenceEdits.size())+"\t"+Base64.getEncoder().encodeToString(String.join("\n",reasons).getBytes(StandardCharsets.UTF_8)));continue;}
                if(importedPaths.contains(unit)&&unit.getImports().stream().noneMatch(declaration->declaration.getQualifiedIdentifier().toString().equals("java.nio.file.Paths"))){
                    int at=unit.getImports().isEmpty()?(unit.getPackage()==null?0:pos(positions.getEndPosition(unit,unit.getPackage()))):pos(positions.getStartPosition(unit,unit.getImports().get(0)));
                    edits.add(new Edit(at,at,"\nimport java.nio.file.Paths;\n"));
                }
                after.put(name,apply(text,0,text.length(),edits).getBytes(StandardCharsets.UTF_8));statuses.add(name+"\tSUPPORTED\t"+(sites.size()+referenceEdits.size())+"\t0");
            }
        }
        if(!before.keySet().equals(selected))throw new IllegalArgumentException("Selected source closure differs");for(var row:before.entrySet())if(!Arrays.equals(row.getValue(),Files.readAllBytes(root.resolve(row.getKey()))))throw new IllegalStateException("Source drift");Files.createDirectories(output);for(var row:after.entrySet())for(String side:List.of("pre","post")){Path path=output.resolve(side).resolve(row.getKey());Files.createDirectories(path.getParent());Files.write(path,side.equals("pre")?before.get(row.getKey()):row.getValue());}Files.write(output.resolve("owner-status.tsv"),statuses,StandardCharsets.UTF_8);System.out.println("PASS genuine attributed API mapping owners="+before.size()+" supported="+after.size());
    }
}
