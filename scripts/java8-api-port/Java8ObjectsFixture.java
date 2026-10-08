package dev.openallay.build;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;
/** Real original public Objects API vs actual canonical Java8 helper. */
public final class Java8ObjectsFixture {
    private static final String SOURCE="""
        import java.util.*;
        public class ObjectsFlow {
            static StringBuilder order=new StringBuilder();
            static Object value(String marker,Object value){order.append(marker);return value;}
            static String failure(Runnable action){try{action.run();return "none";}catch(RuntimeException failed){return failed.getClass().getSimpleName()+":"+failed.getMessage();}}
            public static void main(String[] args){
                Object actual=new Object(),fallback=new Object();
                Object same=java.util.Objects.requireNonNullElse(value("A",actual),value("B",fallback));
                Object defaulted=java.util.Objects.requireNonNullElse(value("C",null),value("D",fallback));
                Object noDefault=java.util.Objects.requireNonNullElse(value("E",actual),value("F",null));
                String nulls=failure(()->java.util.Objects.requireNonNullElse(value("G",null),value("H",null)));
                Integer boxed=java.util.Objects.requireNonNullElse((Integer)null,1);
                String optional=failure(()->Optional.empty().orElseThrow());
                System.out.println((same==actual)+":"+(defaulted==fallback)+":"+(noDefault==actual)+":"+order+":"+nulls+":"+boxed+":"+optional);
            }
        }
        """;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static String run(String java,List<String> args)throws Exception{List<String> command=new ArrayList<>();command.add(java);command.addAll(args);Process p=new ProcessBuilder(command).redirectErrorStream(true).start();String text=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);check(p.waitFor()==0,"Real compiler/runtime failed "+command+" "+text);return text.replace("\r\n","\n");}
    static void compile(Path source,Path helper,Path classes,String release)throws Exception{Files.createDirectory(classes);JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,StandardCharsets.UTF_8)){List<java.io.File> files=new ArrayList<>();files.add(source.toFile());if(helper!=null)files.add(helper.toFile());check(compiler.getTask(null,manager,diagnostics,List.of("--release",release,"-proc:none","-encoding","UTF-8","-d",classes.toString()),null,manager.getJavaFileObjectsFromFiles(files)).call(),"Public compilerfailed "+diagnostics.getDiagnostics());}}
    public static void main(String[] args)throws Exception{if(args.length!=5)throw new IllegalArgumentException("freshRoot actualHelper modernJava trueJavac8 trueJava8");Path root=Paths.get(args[0]);Files.createDirectory(root);Path helper=Paths.get(args[1]);check(run(args[3],List.of("-version")).startsWith("javac 1.8."),"Truejavac8");check(run(args[4],List.of("-version")).contains("version \"1.8."),"Truejava8");Path original=root.resolve("original");Files.createDirectory(original);Path before=original.resolve("ObjectsFlow.java");Files.writeString(before,SOURCE);Path converted=root.resolve("converted");Files.createDirectory(converted);Path after=converted.resolve("ObjectsFlow.java");Files.writeString(after,SOURCE.replace("java.util.Objects.requireNonNullElse(","dev.openallay.util.Java8Objects.requireNonNullElse(").replace("Optional.empty().orElseThrow()","Optional.empty().orElseThrow(() -> new java.util.NoSuchElementException(\"No value present\"))"));Path modern=root.resolve("modern"),release8=root.resolve("release8"),true8=root.resolve("true8");compile(before,null,modern,"17");compile(after,helper,release8,"8");Files.createDirectory(true8);run(args[3],List.of("-source","8","-target","8","-encoding","UTF-8","-d",true8.toString(),after.toString(),helper.toString()));String expected="true:true:true:ABCDEFGH:NullPointerException:defaultObj:1:NoSuchElementException:No value present\n";check(run(args[2],List.of("-cp",modern.toString(),"ObjectsFlow")).equals(expected),"OriginalObjects output");check(run(args[4],List.of("-cp",release8.toString(),"ObjectsFlow")).equals(expected),"Convertedrelease8 output");check(run(args[4],List.of("-cp",true8.toString(),"ObjectsFlow")).equals(expected),"Truejavac8 output");System.out.println("PASS original Objects vs canonicalJava8 helper eagerargs/evaluationonce/sameidentity/nullnull/defaultmessage/boxing/Optional exactexception");}
}
