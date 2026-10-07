package dev.openallay.build;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;

public final class CanonicalSwitchPortFixture {
    private static final String SOURCE="""
        public class SwitchFlow {
            enum Mode { A, B }
            static int selectors;
            static int select(int value){selectors++;return value;}
            static String direct(int value){return switch(select(value)){case 0,1 -> "low";default -> "high";};}
            static String enumValue(Mode mode){return switch(mode){case A -> "a";case B -> "b";};}
            static int blocked(int value){
                final int result=switch(select(value)){
                    case 0 -> {int sum=0;for(int i=0;i<3;i++){if(i==1)continue;sum+=i;}yield sum;}
                    default -> {if(value<0)throw new IllegalArgumentException("negative");yield 7;}
                };
                return result;
            }
            static int assigned(int value){int result;result=switch(select(value)){case 0 -> 3;default -> 4;};return result;}
            static int rules(){int sum=0;outer:for(int i=0;i<4;i++){switch(select(i)){case 0 -> sum+=1;case 1 -> {continue outer;}case 2 -> {sum+=2;break outer;}default -> throw new AssertionError();}}return sum;}
            static String nullString(String value){return switch(value){case "a" -> "A";default -> "other";};}
            public static void main(String[] args){
                String text=direct(1)+direct(2)+enumValue(Mode.B)+blocked(0)+blocked(1)+assigned(0)+assigned(1)+rules();
                boolean nullThrown=false;try{nullString(null);}catch(NullPointerException expected){nullThrown=true;}
                boolean exception=false;try{blocked(-1);}catch(IllegalArgumentException expected){exception=true;}
                System.out.println(text+":"+selectors+":"+nullThrown+":"+exception);
            }
        }
        """;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static String process(String program,List<String> args)throws Exception{List<String> command=new ArrayList<>();command.add(program);command.addAll(args);Process p=new ProcessBuilder(command).redirectErrorStream(true).start();String text=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);check(p.waitFor()==0,"Actual process failed: "+command+" "+text);return text.replace("\r\n","\n");}
    private static void compile(Path source,Path classes,String release)throws Exception{Files.createDirectory(classes);JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,StandardCharsets.UTF_8)){check(compiler.getTask(null,manager,diagnostics,List.of("--release",release,"-proc:none","-encoding","UTF-8","-d",classes.toString()),null,manager.getJavaFileObjects(source.toFile())).call(),"Realcompiler original/converted switch failed "+diagnostics.getDiagnostics());}}
    public static void main(String[] args)throws Exception{
        if(args.length!=4)throw new IllegalArgumentException("freshRoot modernJava trueJavac8 trueJava8");Path root=Paths.get(args[0]);Files.createDirectory(root);check(process(args[2],List.of("-version")).startsWith("javac 1.8."),"Truejavac8 required");check(process(args[3],List.of("-version")).contains("version \"1.8."),"Truejava8 required");
        Path original=root.resolve("original");Files.createDirectory(original);Path source=original.resolve("SwitchFlow.java");Files.writeString(source,SOURCE);Path selected=root.resolve("selected.txt");Files.writeString(selected,"SwitchFlow.java\n");Path converted=root.resolve("converted");CanonicalSwitchPort.main(new String[]{original.toString(),"",selected.toString(),converted.toString()});check(Files.readString(converted.resolve("owner-status.tsv")).equals("SwitchFlow.java\tSUPPORTED\t6\t0\n"),"Actual6switch classification");Path after=converted.resolve("post/SwitchFlow.java");
        String lowered=Files.readString(after);check(lowered.contains("default: throw new java.lang.IncompatibleClassChangeError();"),"Exhaustiveenum syntheticICCE default retained");
        Path modern=root.resolve("modern"),release8=root.resolve("release8"),true8=root.resolve("true8");compile(source,modern,"17");compile(after,release8,"8");Files.createDirectory(true8);process(args[2],List.of("-source","8","-target","8","-encoding","UTF-8","-d",true8.toString(),after.toString()));String expected="lowhighb27343:10:true:true\n";
        check(process(args[1],List.of("-cp",modern.toString(),"SwitchFlow")).equals(expected),"Originalswitch selector/control flow");check(process(args[3],List.of("-cp",release8.toString(),"SwitchFlow")).equals(expected),"Release8 selector/yield/control flow");check(process(args[3],List.of("-cp",true8.toString(),"SwitchFlow")).equals(expected),"Truejavac8 selector/yield/control flow");
        Path unsupported=root.resolve("unsupported");Files.createDirectory(unsupported);Files.writeString(unsupported.resolve("Unsupported.java"),"class Unsupported {static int calls;static int before(){return calls++;}int test(int value){return before()+(switch(value){case 0->1;default->2;});}}");Path unsupportedSelected=root.resolve("unsupported.txt");Files.writeString(unsupportedSelected,"Unsupported.java\n");Path refused=root.resolve("refused");CanonicalSwitchPort.main(new String[]{unsupported.toString(),"",unsupportedSelected.toString(),refused.toString()});check(Files.readString(refused.resolve("owner-status.tsv")).contains("\tREJECTED\t1\t"),"Embeddedleftoperand flow failclosed");check(!Files.exists(refused.resolve("post/Unsupported.java")),"No partialunsupportedowner");
        System.out.println("PASS publicswitch genuinecompiler original17=release8=truejavac8;6switches selectoronce/yield/throw/null/labeledbreak/continue/enumICCE;embeddedflow failclosed");
    }
}
