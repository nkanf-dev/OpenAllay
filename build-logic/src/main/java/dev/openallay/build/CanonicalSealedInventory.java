package dev.openallay.build;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.*;

/** Read-only exact permitted union and source ingress inventory using public compiler elements. */
public final class CanonicalSealedInventory {
    private static String quote(String value){return "\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";}
    private static String array(List<String> values){return "["+String.join(",",values.stream().map(CanonicalSealedInventory::quote).toList())+"]";}
    private static String pathName(Path root,CompilationUnitTree unit){return root.relativize(Paths.get(unit.getSourceFile().toUri())).toString().replace(java.io.File.separatorChar,'/');}
    private static Set<String> types(TypeMirror mirror){Set<String> names=new TreeSet<>();switch(mirror.getKind()){
        case ARRAY -> names.addAll(types(((ArrayType)mirror).getComponentType()));
        case DECLARED -> {DeclaredType declared=(DeclaredType)mirror;names.add(((TypeElement)declared.asElement()).getQualifiedName().toString());for(TypeMirror argument:declared.getTypeArguments())names.addAll(types(argument));}
        case WILDCARD -> {WildcardType wildcard=(WildcardType)mirror;if(wildcard.getExtendsBound()!=null)names.addAll(types(wildcard.getExtendsBound()));if(wildcard.getSuperBound()!=null)names.addAll(types(wildcard.getSuperBound()));}
        default -> {}
    }return names;}
    public static void main(String[] args)throws Exception{
        if(args.length!=3)throw new IllegalArgumentException("actualCompleteCoreRoot genuineProductionClasspath freshOutputJSON");Path root=Paths.get(args[0]).toAbsolutePath().normalize();List<Path> files;try(var stream=Files.walk(root)){files=stream.filter(p->p.toString().endsWith(".java")).sorted().toList();}JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();List<String> unionRows=new ArrayList<>(),ingressRows=new ArrayList<>();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,StandardCharsets.UTF_8)){
            JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("--release","17","-proc:none","-encoding","UTF-8","-classpath",args[1]),null,manager.getJavaFileObjectsFromPaths(files));List<CompilationUnitTree> units=new ArrayList<>();task.parse().forEach(units::add);Trees trees=Trees.instance(task);SourcePositions positions=trees.getSourcePositions();task.analyze();for(Diagnostic<?> d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR)throw new IllegalStateException("Actualcomplete source attribution failed: "+d);
            Set<String> unions=new TreeSet<>();
            for(CompilationUnitTree unit:units)new TreePathScanner<Void,Void>(){
                @Override public Void visitClass(ClassTree tree,Void unused){Element element=trees.getElement(getCurrentPath());if(element instanceof TypeElement type&&type.getModifiers().contains(Modifier.SEALED)&&type.getQualifiedName().toString().startsWith("dev.openallay.guide.")){
                    String name=type.getQualifiedName().toString();unions.add(name);List<String> permitted=new ArrayList<>();for(TypeMirror variant:type.getPermittedSubclasses()){TypeElement subtype=(TypeElement)task.getTypes().asElement(variant);permitted.add(subtype.getQualifiedName()+":"+subtype.getModifiers());}
                    unionRows.add("{\"union\":"+quote(name)+",\"source\":"+quote(pathName(root,unit))+",\"offset\":"+positions.getStartPosition(unit,tree)+",\"permitted\":"+array(permitted)+"}");
                }return super.visitClass(tree,unused);}
            }.scan(unit,null);
            for(CompilationUnitTree unit:units)new TreePathScanner<Void,Void>(){
                @Override public Void visitMethod(MethodTree method,Void unused){Element element=trees.getElement(getCurrentPath());if(element instanceof ExecutableElement executable){
                    for(VariableElement parameter:executable.getParameters()){Set<String> matching=types(parameter.asType());matching.retainAll(unions);if(!matching.isEmpty())ingressRows.add("{\"source\":"+quote(pathName(root,unit))+",\"method\":"+quote(executable.getEnclosingElement()+"."+executable.getSimpleName())+",\"kind\":"+quote(executable.getKind().toString())+",\"parameter\":"+quote(parameter.getSimpleName().toString())+",\"actualType\":"+quote(parameter.asType().toString())+",\"unions\":"+array(new ArrayList<>(matching))+",\"offset\":"+positions.getStartPosition(unit,method)+"}");}
                }return super.visitMethod(method,unused);}
            }.scan(unit,null);
            Files.writeString(Paths.get(args[2]),"{\"actualProductionSourceCount\":"+files.size()+",\"unionCount\":"+unions.size()+",\"unions\":["+String.join(",",unionRows)+"],\"sourceIngressCandidates\":["+String.join(",",ingressRows)+"],\"sourceEditsEmitted\":false}\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            System.out.println("PASS genuine permitted-union/source-ingress inventory: guideUnions="+unions.size()+" ingressCandidates="+ingressRows.size());
        }
    }
}
