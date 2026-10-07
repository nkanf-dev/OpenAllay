import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import org.commonmark.node.*;
import org.commonmark.parser.*;
import org.commonmark.ext.gfm.tables.*;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.markdown.MarkdownRenderer;
import org.commonmark.renderer.text.TextContentRenderer;

/** Runs the real upstream/ported parser; no document or parser substitutes. */
public final class RealParserGolden {
    private static String encoded(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
    private static void node(Node node, int depth) {
        String detail = "";
        if (node instanceof Text) detail = ((Text) node).getLiteral();
        else if (node instanceof Code) detail = ((Code) node).getLiteral();
        else if (node instanceof FencedCodeBlock) detail = ((FencedCodeBlock) node).getInfo() + "\n" + ((FencedCodeBlock) node).getLiteral();
        else if (node instanceof IndentedCodeBlock) detail = ((IndentedCodeBlock) node).getLiteral();
        else if (node instanceof Heading) detail = "" + ((Heading) node).getLevel();
        else if (node instanceof OrderedList) detail = ((OrderedList) node).getMarkerStartNumber() + ":" + ((OrderedList) node).getMarkerDelimiter();
        else if (node instanceof BulletList) detail = ((BulletList) node).getMarker();
        else if (node instanceof TableCell) detail = "" + ((TableCell) node).getAlignment();
        else if (node instanceof Link) detail = ((Link) node).getDestination() + ":" + ((Link) node).getTitle();
        else if (node instanceof Image) detail = ((Image) node).getDestination() + ":" + ((Image) node).getTitle();
        else if (node instanceof HtmlInline) detail = ((HtmlInline) node).getLiteral();
        else if (node instanceof HtmlBlock) detail = ((HtmlBlock) node).getLiteral();
        StringBuilder spans = new StringBuilder();
        for (SourceSpan span : node.getSourceSpans()) {
            spans.append(span.getLineIndex()).append(',').append(span.getColumnIndex()).append(',')
                    .append(span.getInputIndex()).append(',').append(span.getLength()).append(';');
        }
        System.out.println(depth + "|" + node.getClass().getName() + "|" + encoded(detail) + "|" + spans);
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) node(child, depth + 1);
    }
    public static void main(String[] args) throws Exception {
        List<org.commonmark.Extension> extensions = Collections.singletonList(TablesExtension.create());
        Parser parser = Parser.builder().extensions(extensions).includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build();
        HtmlRenderer html = HtmlRenderer.builder().extensions(extensions).build();
        MarkdownRenderer markdown = MarkdownRenderer.builder().extensions(extensions).build();
        TextContentRenderer text = TextContentRenderer.builder().extensions(extensions).build();
        int index = 0;
        for (String vector : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            String source = new String(Base64.getDecoder().decode(vector), StandardCharsets.UTF_8);
            Node document = parser.parse(source);
            System.out.println("VECTOR " + index++);
            node(document, 0);
            System.out.println("HTML " + encoded(html.render(document)));
            System.out.println("MARKDOWN " + encoded(markdown.render(document)));
            System.out.println("TEXT " + encoded(text.render(document)));
        }
    }
}
