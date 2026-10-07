package dev.openallay.guide.semantic;
import java.util.*;
public final class SemanticUnionKnownFixture {
    public static void main(String[] args){SemanticInline.Text text=new SemanticInline.Text("text","known");SemanticBlock.Paragraph paragraph=new SemanticBlock.Paragraph("paragraph",Collections.<SemanticInline>singletonList(text));SemanticDocument known=SemanticDocument.of(Collections.<SemanticBlock>singletonList(paragraph),Collections.<SemanticDiagnostic>emptyList());System.out.println("known="+known);System.out.println("PASS actualsemantic original/candidate values");}
}
