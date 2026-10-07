package dev.openallay.build;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;

/** Genuine public compiler flow/evaluation oracle; only JDK dependencies. */
public final class CanonicalPatternPortFixture {
    private static final String SOURCE="""
        import java.util.*;
        public class PatternFlow {
            static int calls;
            interface Result<T> {}
            static final class Failure<T> implements Result<T>{final T value;Failure(T value){this.value=value;}}
            static <T> Result<T> generic(Result<T> value){calls++;return value;}
            static Object value(Object value){calls++;return value;}
            static String guard(Object input){
                if(!(value(input) instanceof String text)) return "negative";
                return text.toUpperCase(java.util.Locale.ROOT);
            }
            public static void main(String[] args){
                StringBuilder out=new StringBuilder();
                Object input="a";
                if(value(input) instanceof String text && text.length()==1){
                    out.append(text);
                    if(value(Integer.valueOf(3)) instanceof Integer integer) out.append(integer);
                } else {out.append("bad");}
                if(value(null) instanceof String nullText && nullText.length()==0)out.append("bad");
                else if(value("z") instanceof String other){out.append(other);}
                out.append(guard("b")).append(guard(Integer.valueOf(2)));
                for(Object item:Arrays.asList("x",Integer.valueOf(1),"y")){
                    if(value(item) instanceof String local){
                        java.util.function.Supplier<String> capture=()->local;
                        out.append(capture.get());
                    }
                }
                if(value("write") instanceof String reassigned){reassigned="changed";out.append(reassigned);}
                Object array=new String[]{"q"};
                if(value(array) instanceof String[] strings){out.append(strings[0]);}
                Result<String> result=new Failure<String>("generic");
                if(generic(result) instanceof Failure<String> failure){out.append(failure.value);}
                System.out.println(out+":"+calls);
            }
        }
        """;
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    private static void compile(Path source,Path classes,String release)throws Exception{
        Files.createDirectory(classes);JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,StandardCharsets.UTF_8)){
            boolean ok=compiler.getTask(null,manager,diagnostics,List.of("--release",release,"-proc:none","-encoding","UTF-8","-d",classes.toString()),null,manager.getJavaFileObjects(source.toFile())).call();check(ok,"Actual compiler original/converted flow failed: "+diagnostics.getDiagnostics());
        }
    }
    private static String process(String executable,List<String> arguments)throws Exception{
        List<String> command=new ArrayList<>();command.add(executable);command.addAll(arguments);Process child=new ProcessBuilder(command).redirectErrorStream(true).start();String result=new String(child.getInputStream().readAllBytes(),StandardCharsets.UTF_8);check(child.waitFor()==0,"Process failed: "+command+" "+result);return result.replace("\r\n","\n");
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=4)throw new IllegalArgumentException("freshFixtureRoot modernJava genuineJavac8 genuineJava8");Path root=Paths.get(args[0]);Files.createDirectory(root);
        check(process(args[2],List.of("-version")).startsWith("javac 1.8."),"Require genuinejavac8");check(process(args[3],List.of("-version")).contains("version \"1.8."),"Require genuinejava8");
        Path original=root.resolve("original");Files.createDirectory(original);Path source=original.resolve("PatternFlow.java");Files.writeString(source,SOURCE);
        Path selected=root.resolve("selected.txt");Files.writeString(selected,"PatternFlow.java\n");Path converted=root.resolve("converted");CanonicalPatternPort.main(new String[]{original.toString(),"",selected.toString(),converted.toString()});
        check(Files.readString(converted.resolve("owner-status.tsv")).equals("PatternFlow.java\tSUPPORTED\t9\t0\n"),"Exact parsed pattern count/status");
        Path explicit=converted.resolve("post/PatternFlow.java");Path modern=root.resolve("modern-classes"),release8=root.resolve("release8-classes"),javac8=root.resolve("javac8-classes");compile(source,modern,"17");compile(explicit,release8,"8");Files.createDirectory(javac8);
        process(args[2],List.of("-source","8","-target","8","-encoding","UTF-8","-d",javac8.toString(),explicit.toString()));
        String wanted="a3zBnegativexychangedqgeneric:12\n";
        check(process(args[1],List.of("-cp",modern.toString(),"PatternFlow")).equals(wanted),"Original evaluation order/output");
        check(process(args[3],List.of("-cp",release8.toString(),"PatternFlow")).equals(wanted),"Converted release8 flow/evaluation");
        check(process(args[3],List.of("-cp",javac8.toString(),"PatternFlow")).equals(wanted),"Truejavac8 flow/evaluation");
        Path unsupported=root.resolve("unsupported");Files.createDirectory(unsupported);Files.writeString(unsupported.resolve("Unsupported.java"),"class Unsupported { boolean test(Object a,boolean enabled){return enabled && a instanceof String text && text.length()>0;} }");
        Path unsupportedSelected=root.resolve("unsupported.txt");Files.writeString(unsupportedSelected,"Unsupported.java\n");Path rejected=root.resolve("rejected");CanonicalPatternPort.main(new String[]{unsupported.toString(),"",unsupportedSelected.toString(),rejected.toString()});
        check(Files.readString(rejected.resolve("owner-status.tsv")).contains("\tREJECTED\t1\t"),"Unsupported flow remains exactnamed rejection");check(!Files.exists(rejected.resolve("post/Unsupported.java")),"No partialunsupported owner");
        System.out.println("PASS genuine attributed pattern oracle: original17=release8=truejavac8, exactoneevaluation/null/&&/nestedelse/guardscope/lambda/reassignment/array; unsupportedflow failclosed");
    }
}
