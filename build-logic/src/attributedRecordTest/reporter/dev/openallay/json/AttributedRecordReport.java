package dev.openallay.json;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
public final class AttributedRecordReport {
    public static void main(String[] args) throws Exception {
        boolean java8 = args.length == 1 && args[0].equals("java8");
        if(java8 && !"1.8".equals(System.getProperty("java.specification.version")))throw new AssertionError("trueJava8 required");
        AttributedRecordFixtures.UriArtifact uri=new AttributedRecordFixtures.UriArtifact("fabric","https://example.test/artifact.jar");
        if(!uri.artifact().equals(URI.create("https://example.test/artifact.jar")))throw new AssertionError("URI overload changed");
        if(!Modifier.isPublic(uri.getClass().getConstructor(String.class,URI.class).getModifiers())||!Modifier.isPublic(uri.getClass().getConstructor(String.class,String.class).getModifiers()))throw new AssertionError("constructor ABI");
        AttributedRecordFixtures.NonGetter non=new AttributedRecordFixtures.NonGetter(Arrays.asList("a","b"),"label");
        non.input().add("later");if(!non.input().equals(Arrays.asList("a","b")))throw new AssertionError("copy accessor lost");
        if(!(non instanceof AttributedRecordFixtures.OtherContract))throw new AssertionError("interface removed");
        AttributedRecordFixtures.GenuineGetter genuine=new AttributedRecordFixtures.GenuineGetter(Arrays.asList("x"));
        genuine.values().add("later");if(!genuine.values().equals(Arrays.asList("x")))throw new AssertionError("genuine interface copy getter");
        AttributedRecordFixtures.GetterContract<List<String>> contract=genuine;if(!contract.values().equals(Arrays.asList("x")))throw new AssertionError("genuine interface contract");
        if(!non.equals(new AttributedRecordFixtures.NonGetter(Arrays.asList("a","b"),"label")))throw new AssertionError("field equality");
        if(non.hashCode()!=31*Arrays.asList("a","b").hashCode()+"label".hashCode())throw new AssertionError("record zero hash seed");
        if(new AttributedRecordFixtures.Boxed(Integer.valueOf(7)).count()!=7)throw new AssertionError("boxing overload changed");
        System.out.println(new AttributedRecordFixtures.Boxed(Integer.valueOf(7)));System.out.println(uri);System.out.println(non);System.out.println(genuine);System.out.println("PASS public attributed record signatures and real override preservation");
    }
}
