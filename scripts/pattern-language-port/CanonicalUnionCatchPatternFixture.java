package dev.openallay.build;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;
public final class CanonicalUnionCatchPatternFixture {
    private static final String SOURCE="""
        public class UnionFlow {
          static int callbacks;
          static void raise(int mode) throws java.io.IOException {
            if(mode==1)throw new java.io.IOException("io");
            if(mode==2)throw new IllegalStateException("state");
            if(mode==3)throw new AssertionError("error");
          }
          static String exception(int mode){
            try { raise(mode);return "normal"; }
            catch(java.io.IOException | IllegalStateException failure){
              if(failure instanceof java.io.IOException io){callbacks++;return io.getMessage();}
              return failure.getMessage();
            }
          }
          static String throwable(int mode){
            try { raise(mode);return "normal"; }
            catch(java.io.IOException | RuntimeException | Error failure){
              return failure instanceof Error error ? error.getMessage() : failure.getMessage();
            }
          }
          public static void main(String[]args){System.out.println(exception(0)+"/"+exception(1)+"/"+exception(2)+"/"+throwable(3)+":"+callbacks);}
        }
        """;
    private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
    private static String run(String binary,List<String>args)throws Exception{List<String>cmd=new ArrayList<>();cmd.add(binary);cmd.addAll(args);Process p=new ProcessBuilder(cmd).redirectErrorStream(true).start();String out=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);check(p.waitFor()==0,cmd+" "+out);return out.replace("\r\n","\n");}
    private static void compile(Path source,Path classes,String release)throws Exception{Files.createDirectory(classes);JavaCompiler c=ToolProvider.getSystemJavaCompiler();DiagnosticCollector<JavaFileObject>d=new DiagnosticCollector<>();try(StandardJavaFileManager m=c.getStandardFileManager(d,Locale.ROOT,StandardCharsets.UTF_8)){check(c.getTask(null,m,d,List.of("--release",release,"-proc:none","-d",classes.toString()),null,m.getJavaFileObjects(source.toFile())).call(),d.getDiagnostics().toString());}}
    public static void main(String[]args)throws Exception{
      if(args.length!=4)throw new IllegalArgumentException("freshroot modernJava trueJavac8 trueJava8");Path root=Paths.get(args[0]);Files.createDirectory(root);Path original=root.resolve("original");Files.createDirectory(original);Path source=original.resolve("UnionFlow.java");Files.writeString(source,SOURCE);Path selected=root.resolve("selected.txt");Files.writeString(selected,"UnionFlow.java\n");Path output=root.resolve("converted");CanonicalPatternPort.main(new String[]{original.toString(),"",selected.toString(),output.toString()});
      check(Files.readString(output.resolve("owner-status.tsv")).equals("UnionFlow.java\tSUPPORTED\t2\t0\n"),"Exact attributed UNION fixture selection");String text=Files.readString(output.resolve("post/UnionFlow.java"));check(text.contains("java.lang.Exception value")&&text.contains("java.lang.Throwable value"),"Actual declared public multi-catch erasedLUBs");
      Path modern=root.resolve("modern"),release8=root.resolve("release8"),actual8=root.resolve("actual8");compile(source,modern,"17");compile(output.resolve("post/UnionFlow.java"),release8,"8");Files.createDirectory(actual8);run(args[2],List.of("-source","8","-target","8","-d",actual8.toString(),output.resolve("post/UnionFlow.java").toString()));
      String wanted="normal/io/state/error:1\n";check(run(args[1],List.of("-cp",modern.toString(),"UnionFlow")).equals(wanted),"Original");check(run(args[3],List.of("-cp",release8.toString(),"UnionFlow")).equals(wanted),"Release8");check(run(args[3],List.of("-cp",actual8.toString(),"UnionFlow")).equals(wanted),"True8");System.out.println("PASS exact public multi-catch UNION erasure/assignability pattern oracle");
    }
}
