package dev.openallay.build;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.tools.*;

/** Review-only complete client owner syntax frontier; exact public AST string values. */
public final class ClientNativeSyntaxMaterializer {
    private record Change(int start,int end,String replacement) {}
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static String quote(String value){
        StringBuilder out=new StringBuilder("\"");
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            switch(c){case '\\' -> out.append("\\\\");case '"' -> out.append("\\\"");case '\n' -> out.append("\\n");case '\r' -> out.append("\\r");case '\t' -> out.append("\\t");case '\b' -> out.append("\\b");case '\f' -> out.append("\\f");default -> {if(c<32||c==127)out.append(String.format(Locale.ROOT,"\\%03o",(int)c));else out.append(c);}}
        }
        return out.append('"').toString();
    }
    private static CompilationUnitTree parse(Path path,String text)throws Exception{
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();if(compiler==null)throw new IllegalStateException("Full tooling JDK required");DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,StandardCharsets.UTF_8)){
            JavaFileObject source=new SimpleJavaFileObject(path.toUri(),JavaFileObject.Kind.SOURCE){@Override public CharSequence getCharContent(boolean ignore){return text;}};
            JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("--release","17","-proc:none"),null,List.of(source));CompilationUnitTree unit=task.parse().iterator().next();
            for(Diagnostic<?> d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR)throw new IllegalArgumentException("Actual source parse failed: "+d);
            return unit;
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=5)throw new IllegalArgumentException("actualSourceRoot bindingRawSha installerRawSha freshExternalOutput exactHelperBase64");
        Path root=Paths.get(args[0]).toAbsolutePath().normalize(),out=Paths.get(args[3]).toAbsolutePath().normalize();if(Files.exists(out)||out.startsWith(root)||root.startsWith(out))throw new IllegalArgumentException("Fresh external output required");
        String bindingName="engine-core/src/main/java/dev/openallay/client/gui/nativeview/NativeDomainViewBinding.java",installerName="engine-core/src/main/java/dev/openallay/client/voice/NativeModelInstaller.java";
        Map<String,byte[]> before=new LinkedHashMap<>(),after=new LinkedHashMap<>();for(String name:List.of(bindingName,installerName))before.put(name,Files.readAllBytes(root.resolve(name)));
        if(!sha(before.get(bindingName)).equals(args[1])||!sha(before.get(installerName)).equals(args[2]))throw new IllegalArgumentException("Current complete owner preimage differs");
        String binding=new String(before.get(bindingName),StandardCharsets.UTF_8),installer=new String(before.get(installerName),StandardCharsets.UTF_8);
        String helper=new String(Base64.getDecoder().decode(args[4]),StandardCharsets.UTF_8);
        String header="public sealed interface NativeDomainViewBinding\n        permits NativeDomainViewBinding.Recipe {";
        if(binding.indexOf(header)!=binding.lastIndexOf(header)||!binding.contains(header)||binding.indexOf(helper)!=binding.lastIndexOf(helper)||!binding.contains(helper))throw new IllegalArgumentException("Exact sealed/helper shape changed");
        // Actual public AST verifies private static helper and one public final recipe variant.
        CompilationUnitTree bindingUnit=parse(root.resolve(bindingName),binding);int[] helpers={0},variants={0};
        new TreeScanner<Void,Void>(){
            @Override public Void visitClass(ClassTree tree,Void ignored){if(tree.getSimpleName().contentEquals("Recipe")){if(!tree.getModifiers().getFlags().contains(javax.lang.model.element.Modifier.FINAL)||!tree.getModifiers().getFlags().contains(javax.lang.model.element.Modifier.PUBLIC))throw new IllegalArgumentException("Recipe ABI differs");variants[0]++;}return super.visitClass(tree,ignored);}
            @Override public Void visitMethod(MethodTree method,Void ignored){if(method.getName().contentEquals("requireId")){if(!method.getModifiers().getFlags().containsAll(Set.of(javax.lang.model.element.Modifier.PRIVATE,javax.lang.model.element.Modifier.STATIC)))throw new IllegalArgumentException("Helper visibility differs");helpers[0]++;}return super.visitMethod(method,ignored);}
        }.scan(bindingUnit,null);if(helpers[0]!=1||variants[0]!=1)throw new IllegalArgumentException("Complete binding shape differs");
        String ctor="    public Recipe(String stableId, RichComponent.RecipeGrid component, GuideRecipeCard recipe) {";
        if(!binding.contains(ctor)||binding.indexOf(ctor)!=binding.lastIndexOf(ctor))throw new IllegalArgumentException("Recipe constructor differs");
        binding=binding.replace(header,"public interface NativeDomainViewBinding {").replace(helper,"").replace(ctor,helper+"\n"+ctor);
        after.put(bindingName,binding.getBytes(StandardCharsets.UTF_8));
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();List<Change> changes=new ArrayList<>();Map<String,String> values=new TreeMap<>();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,StandardCharsets.UTF_8)){
            JavaFileObject input=new SimpleJavaFileObject(root.resolve(installerName).toUri(),JavaFileObject.Kind.SOURCE){@Override public CharSequence getCharContent(boolean ignore){return installer;}};
            JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("--release","17","-proc:none"),null,List.of(input));CompilationUnitTree unit=task.parse().iterator().next();SourcePositions positions=Trees.instance(task).getSourcePositions();
            for(Diagnostic<?> d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR)throw new IllegalArgumentException("Actual installer parse failed: "+d);
            new TreeScanner<Void,Void>(){
                @Override public Void visitVariable(VariableTree tree,Void ignored){
                    String name=tree.getName().toString();if(Set.of("MODEL_LICENSE","ATTRIBUTION").contains(name)){
                        if(!(tree.getInitializer() instanceof LiteralTree literal)||!(literal.getValue() instanceof String value))throw new IllegalArgumentException("Pinned textblock constant differs");
                        int start=(int)positions.getStartPosition(unit,literal),end=(int)positions.getEndPosition(unit,literal);if(!installer.substring(start,end).startsWith("\"\"\""))throw new IllegalArgumentException("Original textblock absent");
                        List<String> chunks=new ArrayList<>();for(String line:value.split("(?<=\\n)",-1))if(!line.isEmpty())chunks.add(quote(line));if(chunks.isEmpty())chunks.add("\"\"");
                        changes.add(new Change(start,end,String.join("\n            + ",chunks)));if(values.put(name,value)!=null)throw new IllegalArgumentException("Duplicate literal name");
                    }return super.visitVariable(tree,ignored);
                }
            }.scan(unit,null);
        }
        if(values.size()!=2)throw new IllegalArgumentException("Complete installer textblock count differs");StringBuilder lowered=new StringBuilder(installer);changes.sort(Comparator.comparingInt(Change::start).reversed());for(Change c:changes)lowered.replace(c.start(),c.end(),c.replacement());String installerPost=lowered.toString();
        Map<String,String> postValues=new TreeMap<>();new TreeScanner<Void,Void>(){
            private String constant(ExpressionTree tree){if(tree instanceof LiteralTree literal&&literal.getValue() instanceof String text)return text;if(tree instanceof BinaryTree binary&&binary.getKind()==Tree.Kind.PLUS)return constant(binary.getLeftOperand())+constant(binary.getRightOperand());throw new IllegalArgumentException("Nonliteral string reconstruction");}
            @Override public Void visitVariable(VariableTree tree,Void ignored){if(values.containsKey(tree.getName().toString()))postValues.put(tree.getName().toString(),constant(tree.getInitializer()));return super.visitVariable(tree,ignored);}
        }.scan(parse(root.resolve(installerName),installerPost),null);
        if(!values.equals(postValues))throw new IllegalArgumentException("Original public AST literal values differ after lowering");after.put(installerName,installerPost.getBytes(StandardCharsets.UTF_8));
        for(String name:before.keySet())if(!Arrays.equals(before.get(name),Files.readAllBytes(root.resolve(name))))throw new IllegalStateException("Current source drift");Files.createDirectories(out);List<String> rows=new ArrayList<>();
        for(String name:before.keySet()){for(String side:List.of("pre","post")){Path path=out.resolve(side).resolve(name);Files.createDirectories(path.getParent());Files.write(path,side.equals("pre")?before.get(name):after.get(name));}rows.add(name+"\t"+sha(before.get(name))+"\t"+sha(after.get(name))+"\t"+after.get(name).length);}
        Files.write(out.resolve("owner-hashes.tsv"),rows,StandardCharsets.UTF_8);List<String> literals=new ArrayList<>();for(var row:values.entrySet()){byte[] b=row.getValue().getBytes(StandardCharsets.UTF_8);Path path=out.resolve("literal-values").resolve(row.getKey()+".txt");Files.createDirectories(path.getParent());Files.write(path,b);literals.add(row.getKey()+"\t"+sha(b)+"\t"+b.length+"\t"+row.getValue().length());}Files.write(out.resolve("literal-hashes.tsv"),literals,StandardCharsets.UTF_8);
        System.out.println("PASS two actual complete client syntax owners; original public AST literal values exact, private helper custody and Recipe ABI unchanged");
    }
}
