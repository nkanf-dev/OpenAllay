package dev.openallay.build;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;

public final class CanonicalJava8ApiPortFixture {
    private static final String SOURCE="""
        import java.util.*;
        import java.util.stream.*;
        public class ApiFlow {
            static StringBuilder order=new StringBuilder();
            static String value(String mark,String value){order.append(mark);return value;}
            static String receiver(){order.append("R");return "x";}
            static int number(){order.append("N");return 2;}
            static class Custom {String strip(){return "custom";}}
            static String thrown(Runnable action){try{action.run();return "missing";}catch(RuntimeException failure){return failure.getClass().getSimpleName();}}
            public static void main(String[] args){
                List<String> list=List.<String>of(value("a","a"),value("b","b"));
                String repeat=receiver().repeat(number());
                List<String> nullStream=Stream.<String>of("x",null).toList();
                String nullOf=thrown(()->List.of(value("c",null),value("d","d")));
                String duplicateSet=thrown(()->Set.of("a","a"));
                String duplicateMap=thrown(()->Map.of("a",1,"a",2));
                Map<String,Integer> map=Map.of("a",1,"b",2);
                Map<String,Integer> snapshot=Map.copyOf(map);
                Map.Entry<String,Integer> entry=Map.entry("c",3);
                Map<String,Integer> entries=Map.ofEntries(entry);
                String immutable=thrown(()->nullStream.add("bad"));
                ArrayList<String> original=new ArrayList<>();original.add("snap");List<String> copy=List.copyOf(original);original.set(0,"changed");
                String whitespace="\\u2003 a \\u2003".strip()+":"+"\\u2003".isBlank()+":"+"  b".stripLeading()+":"+"c  ".stripTrailing();
                String lines="a\\r\\nb\\nc".lines().collect(java.util.stream.Collectors.joining());
                String hex=java.util.HexFormat.of().formatHex(new byte[]{0,15,(byte)255});
                long seconds=java.time.Duration.ofMillis(1500).toSeconds();
                String path=java.nio.file.Path.of("a","b").toString().replace(java.io.File.separatorChar,'/');
                boolean empty=new StringBuilder().isEmpty();
                java.util.concurrent.CompletableFuture<String> failed=java.util.concurrent.CompletableFuture.failedFuture(new IllegalArgumentException("x"));
                java.util.function.Function<java.util.List<String>,java.util.List<String>> function=List::<String>copyOf;
                List<List<String>> nested=Stream.of(list).map(List::copyOf).toList();
                List<String> copied=function.apply(list);
                String referenceNull=thrown(()->function.apply(Arrays.asList("x",null)));
                String referenceImmutable=thrown(()->copied.add("bad"));
                java.util.function.Supplier<List<String>> emptySupplier=List::<String>of;
                List<String> optional=Optional.<List<String>>empty().orElseGet(List::of);
                java.util.function.Supplier<List<Integer>> integerEmpty=List::of;
                int[] lazyCalls={0};
                java.util.function.Supplier<List<String>> lazy=()->{lazyCalls[0]++;return emptySupplier.get();};
                List<String> present=Optional.of(list).orElseGet(lazy);
                String supplierImmutable=thrown(()->emptySupplier.get().add("bad"));
                System.out.println(list+":"+repeat+":"+order+":"+nullStream+":"+nullOf+":"+duplicateSet+":"+duplicateMap+":"+immutable+":"+copy+":"+snapshot.get("b")+":"+entries.get("c")+":"+whitespace+":"+lines+":"+hex+":"+seconds+":"+path+":"+empty+":"+failed.isCompletedExceptionally()+":"+new Custom().strip()+":"+nested+":"+copied+":"+referenceNull+":"+referenceImmutable+":"+emptySupplier.get()+":"+optional+":"+integerEmpty.get()+":"+(present==list)+":"+lazyCalls[0]+":"+supplierImmutable);
            }
        }
        """;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static String process(String program,List<String> args)throws Exception{List<String> command=new ArrayList<>();command.add(program);command.addAll(args);Process child=new ProcessBuilder(command).redirectErrorStream(true).start();String output=new String(child.getInputStream().readAllBytes(),StandardCharsets.UTF_8);check(child.waitFor()==0,"Actualcompiler/runtime failed "+command+" "+output);return output.replace("\r\n","\n");}
    private static void compile(Path source,List<Path> helpers,Path classes,String release)throws Exception{Files.createDirectory(classes);List<java.io.File> sources=new ArrayList<>();sources.add(source.toFile());for(Path helper:helpers)sources.add(helper.toFile());JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,StandardCharsets.UTF_8)){check(compiler.getTask(null,manager,diagnostics,List.of("--release",release,"-proc:none","-encoding","UTF-8","-d",classes.toString()),null,manager.getJavaFileObjectsFromFiles(sources)).call(),"Truepublic compiler failed "+diagnostics.getDiagnostics());}}
    public static void main(String[] args)throws Exception{
        if(args.length!=5)throw new IllegalArgumentException("freshRoot project modernJava trueJavac8 trueJava8");Path root=Paths.get(args[0]);Files.createDirectory(root);Path project=Paths.get(args[1]);check(process(args[3],List.of("-version")).startsWith("javac 1.8."),"Truejavac8");check(process(args[4],List.of("-version")).contains("version \"1.8."),"Truejava8");
        Path original=root.resolve("original");Files.createDirectory(original);Path source=original.resolve("ApiFlow.java");Files.writeString(source,SOURCE);Path selected=root.resolve("selected.txt");Files.writeString(selected,"ApiFlow.java\n");Path converted=root.resolve("converted");CanonicalJava8ApiPort.main(new String[]{original.toString(),"",selected.toString(),converted.toString()});String status=Files.readString(converted.resolve("owner-status.tsv"));check(status.startsWith("ApiFlow.java\tSUPPORTED\t"),"Actualmethod resolution supported status "+status);
        List<Path> helpers=List.of(project.resolve("engine-core/src/main/java/dev/openallay/util/Java8Collections.java"),project.resolve("engine-core/src/main/java/dev/openallay/util/Java8Strings.java"),project.resolve("engine-core/src/main/java/dev/openallay/util/Java8Hex.java"),project.resolve("engine-core/src/main/java/dev/openallay/util/Java8Futures.java"));Path after=converted.resolve("post/ApiFlow.java");Path modern=root.resolve("modern"),release8=root.resolve("release8"),true8=root.resolve("true8");compile(source,List.of(),modern,"17");compile(after,helpers,release8,"8");Files.createDirectory(true8);List<String> command=new ArrayList<>(List.of("-source","8","-target","8","-encoding","UTF-8","-d",true8.toString(),after.toString()));helpers.forEach(path->command.add(path.toString()));process(args[3],command);
        String wanted="[a, b]:xx:abRNcd:[x, null]:NullPointerException:IllegalArgumentException:IllegalArgumentException:UnsupportedOperationException:[snap]:2:3:a:true:b:c:abc:000fff:1:a/b:true:true:custom:[[a, b]]:[a, b]:NullPointerException:UnsupportedOperationException:[]:[]:[]:true:0:UnsupportedOperationException\n";
        check(process(args[2],List.of("-cp",modern.toString(),"ApiFlow")).equals(wanted),"Originalsemantic output");check(process(args[4],List.of("-cp",release8.toString(),"ApiFlow")).equals(wanted),"Convertedrelease8 output");check(process(args[4],List.of("-cp",true8.toString(),"ApiFlow")).equals(wanted),"Truejavac8 output");
        Path unsupported=root.resolve("unsupported");Files.createDirectory(unsupported);Files.writeString(unsupported.resolve("Unsupported.java"),"import java.util.concurrent.*;class Unsupported {static CompletableFuture<String> receiver(){throw new AssertionError();}Object test(){return receiver().failedFuture(new IllegalArgumentException(\"x\"));} }");Path rejectedSelected=root.resolve("rejected.txt");Files.writeString(rejectedSelected,"Unsupported.java\n");Path refused=root.resolve("refused");CanonicalJava8ApiPort.main(new String[]{unsupported.toString(),"",rejectedSelected.toString(),refused.toString()});check(Files.readString(refused.resolve("owner-status.tsv")).contains("\tREJECTED\t1\t"),"Evaluatedstaticreceiver rejected");check(!Files.exists(refused.resolve("post/Unsupported.java")),"No partialowner");
        Map<String,String> shadows=new LinkedHashMap<>();

        shadows.put("JavaAndPaths","import java.nio.file.Path;class JavaAndPaths {static Object Paths; Object test(){Path java=Path.of(\"a\");return java;}}");
        shadows.put("FieldDev","import java.util.List;class FieldDev {static Object dev;Object test(){return List.of(\"a\");}}");
        shadows.put("NestedDev","import java.util.List;class NestedDev {static class dev{} Object test(){return List.of(\"a\");}}");
        shadows.put("LocalDev","import java.util.List;class LocalDev {Object test(){Object dev=List.of(\"a\");return dev;}}");
        int index=0;for(var shadow:shadows.entrySet()){
            Path directory=root.resolve("shadow"+index++);Files.createDirectory(directory);Path input=directory.resolve(shadow.getKey()+".java");Files.writeString(input,shadow.getValue());
            Path proof=root.resolve("shadow-original-"+shadow.getKey());compile(input,List.of(),proof,"17");
            Path selection=root.resolve("shadow-"+shadow.getKey()+".txt");Files.writeString(selection,input.getFileName()+"\n");Path rejected=root.resolve("shadow-output-"+shadow.getKey());
            CanonicalJava8ApiPort.main(new String[]{directory.toString(),"",selection.toString(),rejected.toString()});
            String report=Files.readString(rejected.resolve("owner-status.tsv"));check(report.contains("\tREJECTED\t1\t"),"Actual package root shadow must reject completeowner: "+report);
            check(!Files.exists(rejected.resolve("post").resolve(input.getFileName())),"No shadowed postimage emitted");
        }
        Path pathScope=root.resolve("pathscope");Files.createDirectory(pathScope);Path pathSource=pathScope.resolve("PathScope.java");
        Files.writeString(pathSource,"import java.nio.file.Path;public class PathScope {public static void main(String[]args){Path java=Path.of(\"a\",\"b\");System.out.println(java.toString().replace('\\\\','/'));}}");
        Path pathSelection=root.resolve("pathscope.txt");Files.writeString(pathSelection,"PathScope.java\n");Path pathOutput=root.resolve("path-output");CanonicalJava8ApiPort.main(new String[]{pathScope.toString(),"",pathSelection.toString(),pathOutput.toString()});
        check(Files.readString(pathOutput.resolve("owner-status.tsv")).contains("\tSUPPORTED\t1\t"),"Resolved safe Paths import generation");
        Path pathAfter=pathOutput.resolve("post/PathScope.java"),pathOriginal=root.resolve("path-original"),pathRelease8=root.resolve("path-release8"),pathTrue8=root.resolve("path-true8");
        compile(pathSource,List.of(),pathOriginal,"17");compile(pathAfter,List.of(),pathRelease8,"8");Files.createDirectory(pathTrue8);process(args[3],List.of("-source","8","-target","8","-d",pathTrue8.toString(),pathAfter.toString()));
        check(process(args[2],List.of("-cp",pathOriginal.toString(),"PathScope")).equals("a/b\n"),"Original localjava Paths behavior");check(process(args[4],List.of("-cp",pathRelease8.toString(),"PathScope")).equals("a/b\n"),"Release8 safe Paths qualifier");check(process(args[4],List.of("-cp",pathTrue8.toString(),"PathScope")).equals("a/b\n"),"True8 safe Paths qualifier");
        Path nonzero=root.resolve("nonzero-ref");Files.createDirectory(nonzero);
        Files.writeString(nonzero.resolve("Nonzero.java"),"import java.util.List;import java.util.function.Function;class Nonzero {Function<String,List<String>> factory=List::of;}");
        Path nonzeroSelection=root.resolve("nonzero-ref.txt");Files.writeString(nonzeroSelection,"Nonzero.java\n");Path nonzeroRefused=root.resolve("nonzero-ref-refused");CanonicalJava8ApiPort.main(new String[]{nonzero.toString(),"",nonzeroSelection.toString(),nonzeroRefused.toString()});check(Files.readString(nonzeroRefused.resolve("owner-status.tsv")).contains("\tREJECTED\t"),"Nonzero Listof descriptor remains unadmitted");check(!Files.exists(nonzeroRefused.resolve("post/Nonzero.java")),"No partialnonzero owner");
        Path refShadow=root.resolve("supplier-shadow");Files.createDirectory(refShadow);
        Files.writeString(refShadow.resolve("SupplierShadow.java"),"import java.util.List;import java.util.function.Supplier;class SupplierShadow {static Object dev;Supplier<List<String>> factory=List::of;}");
        Path refSelection=root.resolve("supplier-shadow.txt");Files.writeString(refSelection,"SupplierShadow.java\n");Path refRefused=root.resolve("supplier-shadow-refused");CanonicalJava8ApiPort.main(new String[]{refShadow.toString(),"",refSelection.toString(),refRefused.toString()});check(Files.readString(refRefused.resolve("owner-status.tsv")).contains("\tREJECTED\t"),"Supplier qualifier root shadow rejected");check(!Files.exists(refRefused.resolve("post/SupplierShadow.java")),"No partialsupplier shadow");
        System.out.println("PASS genuineattributed API oracle original17=release8=truejavac8 evaluationorder/null/duplicates/varargs/generic/snapshot/immutable/whitespace/hex/future/path/duration;custommethod untouched, evaluatedstaticreceiver failclosed");
    }
}
