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
            static StringBuilder order=new StringBuilder();
            static int before(String mark){order.append(mark);return 1;}
            static class Pair{final int a,b;Pair(int a,int b){this.a=a;this.b=b;}}
            static class Target{int call(int first,int second){return first+second;}}
            static Target target(){order.append("R");return new Target();}
            static int embedded(int input){return target().call(before("A"),switch(select(input)){case 0->2;default->3;});}
            static Pair constructed(int input){return new Pair(before("C"),switch(select(input)){case 0->4;default->5;});}
            static String joined(int input){return "prefix"+before("B")+switch(select(input)){case 0->"x";default->"y";};}
            static String lambda(int input){java.util.function.Function<Integer,String> function=value->switch(select(value)){case 0->"l";default->"m";};return function.apply(input);}
            static StringBuilder failures=new StringBuilder();
            static Target absent(){failures.append("R");return null;}
            static int argument(boolean fail){failures.append("A");if(fail)throw new IllegalArgumentException("arg");return 1;}
            static int selected(boolean fail){failures.append("S");if(fail)throw new IllegalStateException("selector");return 0;}
            static String failure(boolean nullReceiver,boolean failArgument,boolean failSelector){
                try {return Integer.toString((nullReceiver?absent():new Target()).call(argument(failArgument),switch(selected(failSelector)){case 0->2;default->3;}));}
                catch(RuntimeException failed){return failed.getClass().getSimpleName()+":"+failures;}
            }
            static String overloaded(Object value,int number){return "object";}
            static String overloaded(String value,int number){return value+number;}
            static <T> T generic(T value,int number){return value;}
            static String varargs(String value,Integer... numbers){return value+numbers.length+numbers[0];}
            static String dispatch(){
                String overload=overloaded("typed",switch(select(0)){case 0->3;default->4;});
                String generic=generic("generic",switch(select(0)){case 0->1;default->2;});
                String variable=varargs("var",switch(select(0)){case 0->5;default->6;});
                return overload+generic+variable;
            }
            static int conditionReads;
            static boolean condition(boolean value){conditionReads++;return value;}
            static int field;
            static void assignedCall(int value){field=overloaded("typed",switch(select(value)){case 0->1;default->2;}).length();}
            static String conditional(boolean value){return condition(value)?"skip":switch(select(0)){case 0->"taken";default->"other";};}
            static String conditionalTrue(boolean value){String text=condition(value)?switch(select(0)){case 0->"yes";default->"no";}:"skip";return text;}
            static final class Codes{static final int KEY=7;static final char LETTER='q';static final String TEXT="key";}
            static String constantInt(int value){return switch(value){case Codes.KEY->"I";default->"i";};}
            static String constantChar(char value){return switch(value){case Codes.LETTER->"C";default->"c";};}
            static String constantString(String value){return switch(value){case Codes.TEXT->"S";default->"s";};}
            static boolean lazyOr(boolean skip){return condition(skip)||switch(select(0)){case 0->true;default->false;};}
            static boolean lambdaOr(boolean skip){java.util.function.Function<Boolean,Boolean> function=value->value||switch(select(0)){case 0->true;default->false;};return function.apply(skip);}
            static StringBuilder throwOrder=new StringBuilder();
            static String reason(){throwOrder.append("A");return "reason";}
            static int thrownSelector(){throwOrder.append("S");return select(0);}
            static final class Failure extends RuntimeException {Failure(String before,String chosen){super(before+":"+chosen);}}
            static void thrown(){throw new Failure(reason(),switch(thrownSelector()){case 0->"chosen";default->"other";});}
            static String throwing(){try{thrown();return "none";}catch(Failure failed){failed.addSuppressed(new IllegalStateException("suppressed"));return failed.getClass().getSimpleName()+":"+failed.getMessage()+":"+failed.getSuppressed()[0].getMessage()+":"+throwOrder;}}
            public static void main(String[] args){
                String text=direct(1)+direct(2)+enumValue(Mode.B)+blocked(0)+blocked(1)+assigned(0)+assigned(1)+rules();
                boolean nullThrown=false;try{nullString(null);}catch(NullPointerException expected){nullThrown=true;}
                boolean exception=false;try{blocked(-1);}catch(IllegalArgumentException expected){exception=true;}
                int argument=embedded(0);Pair pair=constructed(0);String concat=joined(0);String mapped=lambda(0);
                String dispatch=dispatch();
                String nullFailure=failure(true,false,false);failures.setLength(0);
                String argumentFailure=failure(true,true,false);failures.setLength(0);
                String selectorFailure=failure(true,false,true);
                assignedCall(0);String branches=conditional(true)+conditional(false)+conditionalTrue(false)+conditionalTrue(true);
                String constants=constantInt(select(7))+constantChar('q')+constantString("key");
                boolean skipped=lazyOr(true),taken=lazyOr(false),lambdaTaken=lambdaOr(false);
                String thrown=throwing();
                System.out.println(text+":"+selectors+":"+nullThrown+":"+exception+":"+argument+":"+pair.a+pair.b+":"+concat+":"+mapped+":"+order+":"+dispatch+":"+nullFailure+":"+argumentFailure+":"+selectorFailure+":"+field+":"+branches+":"+conditionReads+":"+constants+":"+skipped+":"+taken+":"+lambdaTaken+":"+thrown);
            }
        }
        """;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static String process(String program,List<String> args)throws Exception{List<String> command=new ArrayList<>();command.add(program);command.addAll(args);Process p=new ProcessBuilder(command).redirectErrorStream(true).start();String text=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);check(p.waitFor()==0,"Actual process failed: "+command+" "+text);return text.replace("\r\n","\n");}
    private static void compile(Path source,Path classes,String release)throws Exception{Files.createDirectory(classes);JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,StandardCharsets.UTF_8)){check(compiler.getTask(null,manager,diagnostics,List.of("--release",release,"-proc:none","-encoding","UTF-8","-d",classes.toString()),null,manager.getJavaFileObjects(source.toFile())).call(),"Realcompiler original/converted switch failed "+diagnostics.getDiagnostics());}}
    public static void main(String[] args)throws Exception{
        if(args.length!=4)throw new IllegalArgumentException("freshRoot modernJava trueJavac8 trueJava8");Path root=Paths.get(args[0]);Files.createDirectory(root);check(process(args[2],List.of("-version")).startsWith("javac 1.8."),"Truejavac8 required");check(process(args[3],List.of("-version")).contains("version \"1.8."),"Truejava8 required");
        Path original=root.resolve("original");Files.createDirectory(original);Path source=original.resolve("SwitchFlow.java");Files.writeString(source,SOURCE);Path selected=root.resolve("selected.txt");Files.writeString(selected,"SwitchFlow.java\n");Path converted=root.resolve("converted");CanonicalSwitchPort.main(new String[]{original.toString(),"",selected.toString(),converted.toString()});check(Files.readString(converted.resolve("owner-status.tsv")).equals("SwitchFlow.java\tSUPPORTED\t23\t0\n"),"Actual6switch classification");Path after=converted.resolve("post/SwitchFlow.java");
        String lowered=Files.readString(after);check(lowered.contains("default: throw new java.lang.IncompatibleClassChangeError();"),"Exhaustiveenum syntheticICCE default retained");
        Path modern=root.resolve("modern"),release8=root.resolve("release8"),true8=root.resolve("true8");compile(source,modern,"17");compile(after,release8,"8");Files.createDirectory(true8);process(args[2],List.of("-source","8","-target","8","-encoding","UTF-8","-d",true8.toString(),after.toString()));String expected="lowhighb27343:24:true:true:3:14:prefix1x:l:RACB:typed3genericvar15:NullPointerException:RAS:IllegalArgumentException:RA:IllegalStateException:RAS:6:skiptakenskipyes:6:ICS:true:true:true:Failure:reason:chosen:suppressed:AS\n";
        check(process(args[1],List.of("-cp",modern.toString(),"SwitchFlow")).equals(expected),"Originalswitch selector/control flow");check(process(args[3],List.of("-cp",release8.toString(),"SwitchFlow")).equals(expected),"Release8 selector/yield/control flow");check(process(args[3],List.of("-cp",true8.toString(),"SwitchFlow")).equals(expected),"Truejavac8 selector/yield/control flow");
        Path unsupported=root.resolve("unsupported");Files.createDirectory(unsupported);Files.writeString(unsupported.resolve("Unsupported.java"),"class Unsupported {static int calls;static int before(){return calls++;}int test(int value){return (value>0&&(switch(value){case 0->true;default->false;}))?1:before();}}");Path unsupportedSelected=root.resolve("unsupported.txt");Files.writeString(unsupportedSelected,"Unsupported.java\n");Path refused=root.resolve("refused");CanonicalSwitchPort.main(new String[]{unsupported.toString(),"",unsupportedSelected.toString(),refused.toString()});check(Files.readString(refused.resolve("owner-status.tsv")).contains("\tREJECTED\t1\t"),"Embeddedleftoperand flow failclosed");check(!Files.exists(refused.resolve("post/Unsupported.java")),"No partialunsupportedowner");
        Path objectConcat=root.resolve("objectconcat");Files.createDirectory(objectConcat);
        Files.writeString(objectConcat.resolve("ObjectConcat.java"),"class ObjectConcat { Object left(){return new Object(){public String toString(){throw new AssertionError();}};}String test(int value){return left()+(switch(value){case 0->\"x\";default->\"y\";});}}");
        Path objectSelected=root.resolve("objectconcat.txt");Files.writeString(objectSelected,"ObjectConcat.java\n");Path objectRefused=root.resolve("objectconcat-refused");
        CanonicalSwitchPort.main(new String[]{objectConcat.toString(),"",objectSelected.toString(),objectRefused.toString()});check(Files.readString(objectRefused.resolve("owner-status.tsv")).contains("\tREJECTED\t1\t"),"ObjecttoString order failclosed");
        Path fields=root.resolve("fields");Files.createDirectory(fields);
        Files.writeString(fields.resolve("FieldSwitch.java"),"class FieldSwitch {static class Value{int field;}Value call(int argument){return null;}int test(int selector){return call(switch(selector){case 0->1;default->2;}).field;}}");
        Path fieldSelection=root.resolve("fields.txt");Files.writeString(fieldSelection,"FieldSwitch.java\n");Path fieldRefused=root.resolve("fields-refused");
        CanonicalSwitchPort.main(new String[]{fields.toString(),"",fieldSelection.toString(),fieldRefused.toString()});
        String fieldStatus=Files.readString(fieldRefused.resolve("owner-status.tsv"));check(fieldStatus.contains("\tREJECTED\t1\t"),"Actualfield dereference flow remains failclosed");check(!Files.exists(fieldRefused.resolve("post/FieldSwitch.java")),"No partialfieldowner");
        Path siblings=root.resolve("siblings");Files.createDirectory(siblings);
        Files.writeString(siblings.resolve("SiblingSwitch.java"),"class SiblingSwitch {int pair(int a,int b){return a+b;}int test(int selector){return pair(switch(selector){case 0->1;default->2;},switch(selector){case 0->3;default->4;});}}");
        Path siblingSelection=root.resolve("siblings.txt");Files.writeString(siblingSelection,"SiblingSwitch.java\n");Path siblingRefused=root.resolve("siblings-refused");
        CanonicalSwitchPort.main(new String[]{siblings.toString(),"",siblingSelection.toString(),siblingRefused.toString()});
        check(Files.readString(siblingRefused.resolve("owner-status.tsv")).contains("\tREJECTED\t2\t"),"Unproved sibling composition must not report supported owner");check(!Files.exists(siblingRefused.resolve("post/SiblingSwitch.java")),"No incomplete sibling postsource");
        System.out.println("PASS publicswitch genuinecompiler original17=release8=truejavac8;6switches selectoronce/yield/throw/null/labeledbreak/continue/enumICCE;embeddedflow failclosed");
    }
}
