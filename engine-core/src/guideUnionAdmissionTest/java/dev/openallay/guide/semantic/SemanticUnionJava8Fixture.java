package dev.openallay.guide.semantic;
import java.util.*;
/** Actual closed semantic source families on genuine Java8, no domain stubs. */
public final class SemanticUnionJava8Fixture {
    private static int checks;
    static final class ForeignBlock implements SemanticBlock {public String nodeId(){return "foreign";}}
    static final class ForeignInline implements SemanticInline {public String nodeId(){return "foreign";}}
    static final class ForeignComponent implements RichComponent {public String nodeId(){return "foreign";}public String fallbackText(){return "foreign";}public String narration(){return "foreign";}}
    private static void reject(Runnable action){try{action.run();throw new AssertionError("Unknowncanonical variant admitted");}catch(IncompatibleClassChangeError expected){checks++;}}
    private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("Knownvariant/copy changed");}
    public static void main(String[] args){ForeignBlock block=new ForeignBlock();ForeignInline inline=new ForeignInline();ForeignComponent component=new ForeignComponent();
        reject(()->new SemanticDocument(Collections.<SemanticBlock>singletonList(block),"foreign",Collections.<SemanticDiagnostic>emptyList()));
        reject(()->new SemanticBlock.Quote("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",Collections.<SemanticBlock>singletonList(block)));reject(()->new SemanticBlock.ListBlock("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",false,1,Collections.singletonList(Collections.<SemanticBlock>singletonList(block))));
        reject(()->new SemanticBlock.Paragraph("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",Collections.<SemanticInline>singletonList(inline)));reject(()->new SemanticBlock.Heading("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",1,Collections.<SemanticInline>singletonList(inline)));reject(()->new SemanticBlock.TableCell(SemanticBlock.Alignment.LEFT,Collections.<SemanticInline>singletonList(inline)));
        reject(()->new SemanticInline.Emphasis("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",Collections.<SemanticInline>singletonList(inline)));reject(()->new SemanticInline.Strong("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",Collections.<SemanticInline>singletonList(inline)));reject(()->new SemanticBlock.Component("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",component));
        RichComponentRegistry registry=new RichComponentRegistry(Collections.singletonMap("foreign",(node,envelope,references)->component));reject(()->registry.decode("{\"type\":\"foreign\",\"properties\":{},\"fallback\":\"foreign\",\"narration\":\"foreign\"}","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",SemanticReferenceIndex.empty(UUID.fromString("00000000-0000-0000-0000-000000000001"))));
        SemanticInline.Text text=new SemanticInline.Text("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","known");SemanticBlock.Paragraph paragraph=new SemanticBlock.Paragraph("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",Collections.<SemanticInline>singletonList(text));SemanticDocument known=SemanticDocument.of(Collections.<SemanticBlock>singletonList(paragraph),Collections.<SemanticDiagnostic>emptyList());check(SemanticInline.requireKnown(text)==text);check(SemanticBlock.requireKnown(paragraph)==paragraph);check(known.fallbackText().equals("known"));
        try{new SemanticBlock.Paragraph("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",Arrays.<SemanticInline>asList((SemanticInline)null));throw new AssertionError("Nullaccepted");}catch(NullPointerException expected){checks++;}
        try{new SemanticBlock.Paragraph("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",Arrays.<SemanticInline>asList(inline,null));throw new AssertionError("Nullorderchanged");}catch(NullPointerException expected){checks++;}
        System.out.println("checks="+checks);System.out.println("PASS actualsemanticJava8 unioncapture families/nullcopyorder");
    }
}
