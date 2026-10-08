package dev.openallay.build;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.*;

/** Genuine native-production-shape callback and enhanced-for flow proof. */
public final class CanonicalNativeCallbackPatternFixture {
    private static final String SOURCE="""
        import java.util.*;
        import java.util.concurrent.*;
        public class NativeCallbackFlow {
            interface ToolResult<T> {}
            static final class Success<T> implements ToolResult<T> {final T value;Success(T value){this.value=value;}T value(){reads++;return value;}}
            static final class Failure<T> implements ToolResult<T> {final String message;Failure(String text){message=text;}String message(){reads++;return message;}}
            static int reads,iterations;static boolean cancelled;
            static void client(Runnable work){work.run();}
            static String receive(CompletableFuture<? extends ToolResult<?>> future){
                StringBuilder out=new StringBuilder();
                future.whenComplete((result,failure)->client(()->{
                    if(cancelled){out.append("cancelled");return;}
                    if(failure!=null || result instanceof Failure<?> rejected){
                        out.append(result instanceof Failure<?> text?text.message():"exception");
                    }else if(result instanceof Success<?> accepted && accepted.value() instanceof String){
                        out.append("accepted");
                    }else out.append("unknown");
                })).join();
                return out.toString();
            }
            static <T> CompletableFuture<T> failed(){CompletableFuture<T> future=new CompletableFuture<>();future.completeExceptionally(new IllegalArgumentException("synthetic"));return future;}
            public static void main(String[] args){
                StringBuilder out=new StringBuilder();
                out.append(receive(CompletableFuture.completedFuture(new Success<String>("payload")))).append(':');
                out.append(receive(CompletableFuture.completedFuture(new Failure<Object>("failure")))).append(':');
                out.append(receive(CompletableFuture.completedFuture((ToolResult<?>)null))).append(':');
                cancelled=true;out.append(receive(CompletableFuture.completedFuture(new Success<String>("never-read")))).append(':');cancelled=false;
                for(Object button:Arrays.<Object>asList("a",1,"b")) if(button instanceof String text){iterations++;out.append(text);}
                for(Object button:Arrays.<Object>asList(1,"c")) if(button instanceof String text) out.append(text);
                System.out.println(out+":"+reads+":"+iterations);
            }
        }
        """;
    private static void check(boolean pass,String name){if(!pass)throw new AssertionError(name);}
    private static void publicCapture(Path source)throws Exception{
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,StandardCharsets.UTF_8)){
            JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("--release","17","-proc:none"),null,manager.getJavaFileObjects(source.toFile()));List<CompilationUnitTree> units=new ArrayList<>();task.parse().forEach(units::add);task.analyze();check(diagnostics.getDiagnostics().stream().noneMatch(d->d.getKind()==Diagnostic.Kind.ERROR),"Actual fixture attribution failed");Trees trees=Trees.instance(task);int[] capture={0},loops={0};
            for(CompilationUnitTree unit:units)new TreePathScanner<Void,Void>(){
                @Override public Void visitInstanceOf(InstanceOfTree pattern,Void ignored){
                    if(pattern.getPattern() instanceof BindingPatternTree){
                        TreePath expressionPath=new TreePath(getCurrentPath(),pattern.getExpression());Element symbol=trees.getElement(expressionPath);TypeMirror expression=trees.getTypeMirror(expressionPath);
                        if(pattern.getExpression() instanceof IdentifierTree && symbol instanceof VariableElement parameter && parameter.getKind()==ElementKind.PARAMETER && parameter.asType().getKind()==TypeKind.TYPEVAR){
                            check(parameter.asType().toString().startsWith("capture#"),"Original inferredparameter isnotcapture");TypeMirror upper=((TypeVariable)parameter.asType()).getUpperBound();check(AttributedVarTypes.denotable(upper,false).equals("NativeCallbackFlow.ToolResult<?>"),"Actual capturedupper wildcard differs");check(task.getTypes().isAssignable(expression,upper)&&task.getTypes().isSameType(task.getTypes().erasure(expression),task.getTypes().erasure(upper)),"Actual capture upper proof differs");capture[0]++;
                        }
                        for(TreePath p=getCurrentPath();p!=null;p=p.getParentPath())if(p.getLeaf() instanceof IfTree value && p.getParentPath().getLeaf() instanceof EnhancedForLoopTree loop && loop.getStatement()==value){loops[0]++;break;}
                    }
                    return super.visitInstanceOf(pattern,ignored);
                }
            }.scan(unit,null);check(capture[0]==3&&loops[0]==2,"Actualcallback3/enhancedfor2shape counts differ: "+capture[0]+"/"+loops[0]);
        }
    }
    private static String process(String executable,List<String> args)throws Exception{List<String> command=new ArrayList<>();command.add(executable);command.addAll(args);Process child=new ProcessBuilder(command).redirectErrorStream(true).start();String text=new String(child.getInputStream().readAllBytes(),StandardCharsets.UTF_8);check(child.waitFor()==0,"Command failed: "+command+" "+text);return text.replace("\r\n","\n");}
    private static void compile(Path source,Path classes,String release)throws Exception{Files.createDirectory(classes);JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,StandardCharsets.UTF_8)){check(compiler.getTask(null,manager,diagnostics,List.of("--release",release,"-proc:none","-encoding","UTF-8","-d",classes.toString()),null,manager.getJavaFileObjects(source.toFile())).call(),"Compiler failed "+diagnostics.getDiagnostics());}}
    public static void main(String[] args)throws Exception{
        if(args.length!=4)throw new IllegalArgumentException("freshOutput modernJava genuineJavac8 genuineJava8");Path out=Paths.get(args[0]);Files.createDirectory(out);check(process(args[2],List.of("-version")).startsWith("javac 1.8."),"Require genuine8compiler");check(process(args[3],List.of("-version")).contains("version \"1.8."),"Require genuine8VM");Path originals=out.resolve("original");Files.createDirectory(originals);Path source=originals.resolve("NativeCallbackFlow.java");Files.writeString(source,SOURCE);publicCapture(source);
        Path selected=out.resolve("selected.txt");Files.writeString(selected,"NativeCallbackFlow.java\n");Path converted=out.resolve("converted");CanonicalPatternPort.main(new String[]{originals.toString(),"",selected.toString(),converted.toString()});check(Files.readString(converted.resolve("owner-status.tsv")).equals("NativeCallbackFlow.java\tSUPPORTED\t5\t0\n"),"Actualwhole5site supportedscope differs");Path post=converted.resolve("post/NativeCallbackFlow.java");String lowered=Files.readString(post);check(lowered.contains("NativeCallbackFlow.ToolResult<?> value;"),"Declared actualupper wildcard missing");check(!lowered.contains("java.lang.Object value; Failure<?>"),"Capturedcallback Objectfallback detected");
        Path modern=out.resolve("modern"),release8=out.resolve("release8"),true8=out.resolve("true8");compile(source,modern,"17");compile(post,release8,"8");Files.createDirectory(true8);process(args[2],List.of("-source","8","-target","8","-encoding","UTF-8","-d",true8.toString(),post.toString()));String wanted="accepted:failure:unknown:cancelled:abc:2:2\n";check(process(args[1],List.of("-cp",modern.toString(),"NativeCallbackFlow")).equals(wanted),"Original callbacklazy/nativeflow differs");check(process(args[3],List.of("-cp",release8.toString(),"NativeCallbackFlow")).equals(wanted),"Release8 callbacklazy/nativeflow differs");check(process(args[3],List.of("-cp",true8.toString(),"NativeCallbackFlow")).equals(wanted),"True8 callbacklazy/nativeflow differs");
        for(Path classes:List.of(release8,true8))try(var files=Files.walk(classes)){for(Path file:files.filter(p->p.toString().endsWith(".class")).toList()){byte[] bytes=Files.readAllBytes(file);check(bytes[6]==0&&bytes[7]==52,"Classmajor differs");}}
        Path unsupported=out.resolve("unsupported");Files.createDirectory(unsupported);Files.writeString(unsupported.resolve("Unsupported.java"),"class Unsupported { void run(Object[] values){for(Object value:values) while(value instanceof String text && text.length()>0){break;}} }");Path rejectedNames=out.resolve("unsupported.txt");Files.writeString(rejectedNames,"Unsupported.java\n");Path rejected=out.resolve("rejected");CanonicalPatternPort.main(new String[]{unsupported.toString(),"",rejectedNames.toString(),rejected.toString()});check(Files.readString(rejected.resolve("owner-status.tsv")).contains("\tREJECTED\t1\t"),"Nonadmitted whileboundary accepted");check(!Files.exists(rejected.resolve("post/Unsupported.java")),"Rejectedowner partialpost emitted");
        System.out.println("PASS publicnativecallback3capture-upperbound/enhancedfor2Ifbody proof; original17=release8=genuine8 exact2reads/periteration/lazycancel; nonadmittedloop wholeowner rejected");
    }
}
