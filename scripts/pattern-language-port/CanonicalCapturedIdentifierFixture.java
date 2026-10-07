package dev.openallay.build;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.*;

/** Separate public compiler proof for declared wildcard identifiers; prior 17/18 oracle stays frozen. */
public final class CanonicalCapturedIdentifierFixture {
    private static final String SOURCE="""
        import java.util.*;
        public class CaptureFlow {
            interface Result<T> {}
            static int reads;
            static final class Box<T> implements Result<T> {final T data;Box(T data){this.data=data;}Object value(){reads++;return data;}}
            static final class Failure<T> implements Result<T> {}
            static boolean accepted(boolean editing,Result<?> result){
                return result instanceof Box<?> success && (editing ? Boolean.TRUE.equals(success.value()) : success.value() instanceof UUID);
            }
            static boolean local(boolean enabled,Result<?> input){
                Result<?> receiver=input;
                return enabled && receiver instanceof Box<?> success && success.value()!=null;
            }
            static boolean upper(Result<? extends Number> result){return result instanceof Box<?> success && success.value() instanceof Number;}
            static boolean lower(Result<? super Integer> result){return result instanceof Box<?> success && success.value() instanceof Integer;}
            static void emit(StringBuilder out,boolean value){out.append(value?'1':'0');}
            public static void main(String[] args){
                StringBuilder out=new StringBuilder();
                emit(out,accepted(true,new Box<Boolean>(true)));emit(out,accepted(true,new Box<Boolean>(false)));
                emit(out,accepted(false,new Box<UUID>(UUID.fromString("10000000-0000-0000-0000-000000000001"))));
                emit(out,accepted(false,new Box<Boolean>(true)));emit(out,accepted(true,new Box<UUID>(UUID.fromString("10000000-0000-0000-0000-000000000001"))));
                emit(out,accepted(false,new Box<String>("wrong")));emit(out,accepted(true,null));emit(out,accepted(false,new Failure<Object>()));
                emit(out,local(false,new Box<String>("not-read")));emit(out,local(true,new Box<String>("read")));emit(out,local(true,null));
                emit(out,upper(new Box<Integer>(4)));emit(out,lower(new Box<Integer>(4)));
                System.out.println(out+":"+reads);
            }
        }
        """;
    private static void check(boolean pass,String name){if(!pass)throw new AssertionError(name);}
    private static void publicTypes(Path source)throws Exception{
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,StandardCharsets.UTF_8)){
            JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("--release","17","-proc:none"),null,manager.getJavaFileObjects(source.toFile()));
            List<CompilationUnitTree> units=new ArrayList<>();task.parse().forEach(units::add);task.analyze();
            check(diagnostics.getDiagnostics().stream().noneMatch(d->d.getKind()==Diagnostic.Kind.ERROR),"Actual complete fixture attribution failed");
            Trees trees=Trees.instance(task);int[] captured={0},proved={0};
            for(CompilationUnitTree unit:units)new TreePathScanner<Void,Void>(){
                @Override public Void visitInstanceOf(InstanceOfTree tree,Void ignored){
                    if(tree.getPattern() instanceof BindingPatternTree){
                        TreePath expressionPath=new TreePath(getCurrentPath(),tree.getExpression());TypeMirror expression=trees.getTypeMirror(expressionPath);Element symbol=trees.getElement(expressionPath);
                        check(tree.getExpression() instanceof IdentifierTree&&symbol instanceof VariableElement,"Direct actual variable identifier required");
                        TypeMirror declared=symbol.asType();String text=AttributedVarTypes.denotable(declared,false);
                        check(text.contains("?"),"Original declared wildcard denotation absent");
                        check(task.getTypes().isAssignable(expression,declared),"Captured expression not assignable to declared identifier type");
                        check(task.getTypes().isSameType(task.getTypes().erasure(expression),task.getTypes().erasure(declared)),"Declared receiver erasure differs");
                        try{AttributedVarTypes.denotable(expression,false);}catch(IllegalArgumentException rejection){check(rejection.getMessage().startsWith("Captured variable:"),"Unexpected renderer rejection");captured[0]++;}
                        proved[0]++;
                    }
                    return super.visitInstanceOf(tree,ignored);
                }
            }.scan(unit,null);
            check(proved[0]==4&&captured[0]==4,"Exact public capture/declaration proof count differs: "+proved[0]+"/"+captured[0]);
        }
    }
    private static void compile(Path source,Path classes,String release)throws Exception{
        Files.createDirectory(classes);JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,StandardCharsets.UTF_8)){
            check(compiler.getTask(null,manager,diagnostics,List.of("--release",release,"-proc:none","-encoding","UTF-8","-d",classes.toString()),null,manager.getJavaFileObjects(source.toFile())).call(),"Original/converted compiler failed: "+diagnostics.getDiagnostics());
        }
    }
    private static String process(String binary,List<String> arguments)throws Exception{
        List<String> command=new ArrayList<>();command.add(binary);command.addAll(arguments);Process child=new ProcessBuilder(command).redirectErrorStream(true).start();String output=new String(child.getInputStream().readAllBytes(),StandardCharsets.UTF_8);check(child.waitFor()==0,"Command failed: "+command+" "+output);return output.replace("\r\n","\n");
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=4)throw new IllegalArgumentException("freshFixtureRoot modernJava genuineJavac8 genuineJava8");Path root=Paths.get(args[0]);Files.createDirectory(root);
        check(process(args[2],List.of("-version")).startsWith("javac 1.8."),"Genuine javac8 required");check(process(args[3],List.of("-version")).contains("version \"1.8."),"Genuine java8 required");
        Path originals=root.resolve("original");Files.createDirectory(originals);Path source=originals.resolve("CaptureFlow.java");Files.writeString(source,SOURCE);publicTypes(source);
        Path selected=root.resolve("selected.txt");Files.writeString(selected,"CaptureFlow.java\n");Path converted=root.resolve("converted");CanonicalPatternPort.main(new String[]{originals.toString(),"",selected.toString(),converted.toString()});
        check(Files.readString(converted.resolve("owner-status.tsv")).equals("CaptureFlow.java\tSUPPORTED\t4\t0\n"),"Exact4 wildcard identifier owner patterns required");
        String post=Files.readString(converted.resolve("post/CaptureFlow.java"));check(post.contains("CaptureFlow.Result<?> value;")&&post.contains("CaptureFlow.Result<? extends java.lang.Number> value;")&&post.contains("CaptureFlow.Result<? super java.lang.Integer> value;"),"Actual declared wildcard types not preserved");check(!post.contains("java.lang.Object value;"),"No Object operand fallback");
        Path modern=root.resolve("modern"),release8=root.resolve("release8"),genuine=root.resolve("genuine8");compile(source,modern,"17");Path explicit=converted.resolve("post/CaptureFlow.java");compile(explicit,release8,"8");Files.createDirectory(genuine);process(args[2],List.of("-source","8","-target","8","-encoding","UTF-8","-d",genuine.toString(),explicit.toString()));
        String expected="1010000001011:9\n";check(process(args[1],List.of("-cp",modern.toString(),"CaptureFlow")).equals(expected),"Original wildcard admission/lazy value semantics");check(process(args[3],List.of("-cp",release8.toString(),"CaptureFlow")).equals(expected),"Release8 wildcard admission/lazy value semantics");check(process(args[3],List.of("-cp",genuine.toString(),"CaptureFlow")).equals(expected),"Genuine8 wildcard admission/lazy value semantics");
        for(Path classes:List.of(release8,genuine))try(var files=Files.walk(classes)){for(Path file:files.filter(p->p.toString().endsWith(".class")).toList()){byte[] bytes=Files.readAllBytes(file);check(bytes[6]==0&&bytes[7]==52,"Actual Java8 class major differs");}}
        Path calls=root.resolve("calls");Files.createDirectory(calls);Files.writeString(calls.resolve("Calls.java"),"class Calls { interface Result<T>{} static class Box<T> implements Result<T>{} Result<?> source(){return new Box<String>();} boolean value(){return source() instanceof Box<?> success && success!=null;} }");Path callSelected=root.resolve("calls.txt");Files.writeString(callSelected,"Calls.java\n");Path callOutput=root.resolve("calls-output");CanonicalPatternPort.main(new String[]{calls.toString(),"",callSelected.toString(),callOutput.toString()});check(Files.readString(callOutput.resolve("owner-status.tsv")).contains("\tREJECTED\t1\t"),"Captured methodcall must remain rejected");check(!Files.exists(callOutput.resolve("post/Calls.java")),"Rejected complete methodcall owner had partial output");
        System.out.println("PASS public capture/declaration4proofs; wildcard parameter/local/extends/super true17=release8=genuine8 13cases9lazyreads; capturedcall rejected, no Object fallback");
    }
}
