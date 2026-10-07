package dev.openallay.guide.semantic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

/** Converts CommonMark into OpenAllay's closed, non-actionable semantic AST. */
public final class SemanticMessageParser {
    private static final java.util.UUID EMPTY_REQUEST = new java.util.UUID(0, 0);
    private final Parser parser = Parser.builder()
            .extensions(dev.openallay.util.Java8Collections.listOf(TablesExtension.create()))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build();
    private final SemanticReferenceValidator references = new SemanticReferenceValidator();
    private final RichComponentRegistry components;

    public SemanticMessageParser() {
        this(RichComponentRegistry.builtins());
    }

    public SemanticMessageParser(RichComponentRegistry components) {
        this.components = Objects.requireNonNull(components, "components");
    }

    public SemanticDocument parse(String source) {
        return parse(source, SemanticReferenceIndex.empty(EMPTY_REQUEST));
    }

    public SemanticDocument parse(String source, SemanticReferenceIndex referenceIndex) {
        return parseFragment(source, 0, referenceIndex);
    }

    SemanticDocument parseFragment(String source, int firstBlockOrdinal) {
        return parseFragment(source, firstBlockOrdinal,
                SemanticReferenceIndex.empty(EMPTY_REQUEST));
    }

    SemanticDocument parseFragment(
            String source,
            int firstBlockOrdinal,
            SemanticReferenceIndex referenceIndex) {
        source = source == null ? "" : source;
        if (firstBlockOrdinal < 0) {
            throw new IllegalArgumentException("first block ordinal must not be negative");
        }
        Conversion conversion = new Conversion(
                source,
                firstBlockOrdinal,
                Objects.requireNonNull(referenceIndex, "referenceIndex"),
                references,
                components);
        Node document = parser.parse(source);
        List<SemanticBlock> blocks = conversion.blocks(document, "root");
        return SemanticDocument.of(blocks, conversion.diagnostics);
    }

    private static final class Conversion {
        private final String source;
        private final int firstBlockOrdinal;
        private final SemanticReferenceIndex referenceIndex;
        private final SemanticReferenceValidator referenceValidator;
        private final RichComponentRegistry componentRegistry;
        private final List<SemanticDiagnostic> diagnostics = new ArrayList<>();

        private Conversion(
                String source,
                int firstBlockOrdinal,
                SemanticReferenceIndex referenceIndex,
                SemanticReferenceValidator referenceValidator,
                RichComponentRegistry componentRegistry) {
            this.source = source;
            this.firstBlockOrdinal = firstBlockOrdinal;
            this.referenceIndex = referenceIndex;
            this.referenceValidator = referenceValidator;
            this.componentRegistry = componentRegistry;
        }

        private List<SemanticBlock> blocks(Node parent, String parentPath) {
            List<SemanticBlock> result = new ArrayList<>();
            int index = 0;
            for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
                String path = parentPath.equals("root")
                        ? "b" + (firstBlockOrdinal + index)
                        : parentPath + ".b" + index;
                SemanticBlock converted = block(node, path);
                if (converted != null) {
                    result.add(converted);
                    index++;
                }
            }
            return dev.openallay.util.Java8Collections.listCopyOf(result);
        }

        private SemanticBlock block(Node node, String path) {
            final class $oaPattern0_Holder { org.commonmark.node.Node value; Paragraph bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = node) instanceof org.commonmark.node.Paragraph && (($oaPattern0_holder.bound = (Paragraph) $oaPattern0_holder.value) != null))) {
                List<SemanticInline> content = inlines($oaPattern0_holder.bound, path);
                return new SemanticBlock.Paragraph(id(path, "paragraph", literal(node)), content);
            }
            final class $oaPattern1_Holder { org.commonmark.node.Node value; Heading bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = node) instanceof org.commonmark.node.Heading && (($oaPattern1_holder.bound = (Heading) $oaPattern1_holder.value) != null))) {
                List<SemanticInline> content = inlines($oaPattern1_holder.bound, path);
                return new SemanticBlock.Heading(
                        id(path, "heading", literal(node)), $oaPattern1_holder.bound.getLevel(), content);
            }
            final class $oaPattern2_Holder { org.commonmark.node.Node value; BulletList bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = node) instanceof org.commonmark.node.BulletList && (($oaPattern2_holder.bound = (BulletList) $oaPattern2_holder.value) != null))) {
                return list(node, path, false, 1);
            }
            final class $oaPattern3_Holder { org.commonmark.node.Node value; OrderedList bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if ((($oaPattern3_holder.value = node) instanceof org.commonmark.node.OrderedList && (($oaPattern3_holder.bound = (OrderedList) $oaPattern3_holder.value) != null))) {
                return list(node, path, true,
                        dev.openallay.util.Java8Objects.requireNonNullElse($oaPattern3_holder.bound.getMarkerStartNumber(), 1));
            }
            final class $oaPattern4_Holder { org.commonmark.node.Node value; BlockQuote bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = node) instanceof org.commonmark.node.BlockQuote && (($oaPattern4_holder.bound = (BlockQuote) $oaPattern4_holder.value) != null))) {
                return new SemanticBlock.Quote(
                        id(path, "quote", literal(node)), blocks($oaPattern4_holder.bound, path));
            }
            final class $oaPattern5_Holder { org.commonmark.node.Node value; FencedCodeBlock bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
if ((($oaPattern5_holder.value = node) instanceof org.commonmark.node.FencedCodeBlock && (($oaPattern5_holder.bound = (FencedCodeBlock) $oaPattern5_holder.value) != null))) {
                if ("openallay-component".equals(dev.openallay.util.Java8Strings.strip($oaPattern5_holder.bound.getInfo()))) {
                    return component($oaPattern5_holder.bound, path);
                }
                return new SemanticBlock.CodeBlock(
                        id(path, "code_block", literal(node)), $oaPattern5_holder.bound.getInfo(), $oaPattern5_holder.bound.getLiteral());
            }
            final class $oaPattern6_Holder { org.commonmark.node.Node value; IndentedCodeBlock bound; }
final $oaPattern6_Holder $oaPattern6_holder = new $oaPattern6_Holder();
if ((($oaPattern6_holder.value = node) instanceof org.commonmark.node.IndentedCodeBlock && (($oaPattern6_holder.bound = (IndentedCodeBlock) $oaPattern6_holder.value) != null))) {
                return new SemanticBlock.CodeBlock(
                        id(path, "code_block", literal(node)), "", $oaPattern6_holder.bound.getLiteral());
            }
            final class $oaPattern7_Holder { org.commonmark.node.Node value; TableBlock bound; }
final $oaPattern7_Holder $oaPattern7_holder = new $oaPattern7_Holder();
if ((($oaPattern7_holder.value = node) instanceof org.commonmark.ext.gfm.tables.TableBlock && (($oaPattern7_holder.bound = (TableBlock) $oaPattern7_holder.value) != null))) {
                return table($oaPattern7_holder.bound, path);
            }
            if (node instanceof ThematicBreak) {
                return new SemanticBlock.ThematicBreak(id(path, "thematic_break", literal(node)));
            }
            return unsafeBlock(node, path);
        }

        private SemanticBlock component(FencedCodeBlock code, String path) {
            String nodeId = id(path, "component", code.getLiteral());
            RichComponentRegistry.Decode decoded = componentRegistry.decode(
                    code.getLiteral(), nodeId, referenceIndex);
            if (decoded.successful()) {
                return new SemanticBlock.Component(nodeId, decoded.component());
            }
            diagnostics.add(new SemanticDiagnostic(decoded.failureCode(), nodeId));
            SemanticInline.Text fallback = new SemanticInline.Text(
                    id(path + ".s0", "text", decoded.fallbackText()), decoded.fallbackText());
            return new SemanticBlock.Paragraph(nodeId, dev.openallay.util.Java8Collections.listOf(fallback));
        }

        private SemanticBlock list(Node node, String path, boolean ordered, int start) {
            List<List<SemanticBlock>> items = new ArrayList<>();
            int index = 0;
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                final class $oaPattern8_Holder { org.commonmark.node.Node value; ListItem bound; }
final $oaPattern8_Holder $oaPattern8_holder = new $oaPattern8_Holder();
if ((($oaPattern8_holder.value = child) instanceof org.commonmark.node.ListItem && (($oaPattern8_holder.bound = (ListItem) $oaPattern8_holder.value) != null))) {
                    items.add(blocks($oaPattern8_holder.bound, path + ".i" + index++));
                } else {
                    items.add(dev.openallay.util.Java8Collections.listOf(unsafeBlock(child, path + ".i" + index++)));
                }
            }
            return new SemanticBlock.ListBlock(
                    id(path, ordered ? "ordered_list" : "bullet_list", literal(node)),
                    ordered,
                    Math.max(1, start),
                    items);
        }

        private SemanticBlock table(TableBlock table, String path) {
            SemanticBlock.TableRow header = new SemanticBlock.TableRow(dev.openallay.util.Java8Collections.listOf());
            List<SemanticBlock.TableRow> body = new ArrayList<>();
            int rowIndex = 0;
            for (Node section = table.getFirstChild(); section != null; section = section.getNext()) {
                for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                    final class $oaPattern9_Holder { org.commonmark.node.Node value; TableRow bound; }
final $oaPattern9_Holder $oaPattern9_holder = new $oaPattern9_Holder();
if (!((($oaPattern9_holder.value = row) instanceof org.commonmark.ext.gfm.tables.TableRow && (($oaPattern9_holder.bound = (TableRow) $oaPattern9_holder.value) != null)))) {
                        continue;
                    }
                    SemanticBlock.TableRow converted = tableRow($oaPattern9_holder.bound, path + ".r" + rowIndex++);
                    if (section instanceof TableHead && header.cells().isEmpty()) {
                        header = converted;
                    } else if (section instanceof TableBody) {
                        body.add(converted);
                    }
                }
            }
            return new SemanticBlock.Table(id(path, "table", literal(table)), header, body);
        }

        private SemanticBlock.TableRow tableRow(TableRow row, String path) {
            List<SemanticBlock.TableCell> cells = new ArrayList<>();
            int index = 0;
            for (Node node = row.getFirstChild(); node != null; node = node.getNext()) {
                final class $oaPattern10_Holder { org.commonmark.node.Node value; TableCell bound; }
final $oaPattern10_Holder $oaPattern10_holder = new $oaPattern10_Holder();
if ((($oaPattern10_holder.value = node) instanceof org.commonmark.ext.gfm.tables.TableCell && (($oaPattern10_holder.bound = (TableCell) $oaPattern10_holder.value) != null))) {
                    cells.add(new SemanticBlock.TableCell(
                            alignment($oaPattern10_holder.bound.getAlignment()), inlines($oaPattern10_holder.bound, path + ".c" + index++)));
                }
            }
            return new SemanticBlock.TableRow(cells);
        }

        private static SemanticBlock.Alignment alignment(TableCell.Alignment alignment) {
            if (alignment == null) {
                return SemanticBlock.Alignment.NONE;
            }
            {
dev.openallay.guide.semantic.SemanticBlock.Alignment $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((alignment)) {
case LEFT:
{
$oaSwitch0_exit_result = SemanticBlock.Alignment.LEFT; break $oaSwitch0_exit;
}
case CENTER:
{
$oaSwitch0_exit_result = SemanticBlock.Alignment.CENTER; break $oaSwitch0_exit;
}
case RIGHT:
{
$oaSwitch0_exit_result = SemanticBlock.Alignment.RIGHT; break $oaSwitch0_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
return $oaSwitch0_exit_result;
}
        }

        private List<SemanticInline> inlines(Node parent, String path) {
            List<SemanticInline> result = new ArrayList<>();
            int index = 0;
            for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
                String childPath = path + ".s" + index++;
                final class $oaPattern11_Holder { org.commonmark.node.Node value; Text bound; }
final $oaPattern11_Holder $oaPattern11_holder = new $oaPattern11_Holder();
if ((($oaPattern11_holder.value = node) instanceof org.commonmark.node.Text && (($oaPattern11_holder.bound = (Text) $oaPattern11_holder.value) != null))) {
                    result.addAll(text($oaPattern11_holder.bound.getLiteral(), childPath));
                } else {
final class $oaPattern12_Holder { org.commonmark.node.Node value; Emphasis bound; }
final $oaPattern12_Holder $oaPattern12_holder = new $oaPattern12_Holder();
if ((($oaPattern12_holder.value = node) instanceof org.commonmark.node.Emphasis && (($oaPattern12_holder.bound = (Emphasis) $oaPattern12_holder.value) != null))) {
                    result.add(new SemanticInline.Emphasis(
                            id(childPath, "emphasis", literal(node)), inlines($oaPattern12_holder.bound, childPath)));
                } else {
final class $oaPattern13_Holder { org.commonmark.node.Node value; StrongEmphasis bound; }
final $oaPattern13_Holder $oaPattern13_holder = new $oaPattern13_Holder();
if ((($oaPattern13_holder.value = node) instanceof org.commonmark.node.StrongEmphasis && (($oaPattern13_holder.bound = (StrongEmphasis) $oaPattern13_holder.value) != null))) {
                    result.add(new SemanticInline.Strong(
                            id(childPath, "strong", literal(node)), inlines($oaPattern13_holder.bound, childPath)));
                } else {
final class $oaPattern14_Holder { org.commonmark.node.Node value; Code bound; }
final $oaPattern14_Holder $oaPattern14_holder = new $oaPattern14_Holder();
if ((($oaPattern14_holder.value = node) instanceof org.commonmark.node.Code && (($oaPattern14_holder.bound = (Code) $oaPattern14_holder.value) != null))) {
                    result.add(new SemanticInline.Code(
                            id(childPath, "code", $oaPattern14_holder.bound.getLiteral()), $oaPattern14_holder.bound.getLiteral()));
                } else if (node instanceof SoftLineBreak) {
                    result.add(new SemanticInline.Break(
                            id(childPath, "soft_break", "\n"), false));
                } else if (node instanceof HardLineBreak) {
                    result.add(new SemanticInline.Break(
                            id(childPath, "hard_break", "\n"), true));
                } else if (node instanceof Link || node instanceof Image || node instanceof HtmlInline) {
                    result.add(unsafeInline(node, childPath));
                } else {
                    result.add(unsafeInline(node, childPath));
                }
}
}
}
            }
            return dev.openallay.util.Java8Collections.listCopyOf(result);
        }

        private List<SemanticInline> text(String text, String path) {
            List<SemanticInline> result = new ArrayList<>();
            java.util.regex.Matcher matcher = referenceValidator.matcher(text);
            int offset = 0;
            int part = 0;
            while (matcher.find()) {
                if (matcher.start() > offset) {
                    String literal = text.substring(offset, matcher.start());
                    result.add(new SemanticInline.Text(
                            id(path + ".p" + part++, "text", literal), literal));
                }
                String token = matcher.group();
                SemanticReferenceValidator.Validation validated =
                        referenceValidator.validate(token, referenceIndex);
                String nodeId = id(path + ".p" + part++, "reference", token);
                if (validated.successful()) {
                    result.add(new SemanticInline.Reference(nodeId, validated.reference()));
                } else {
                    diagnostics.add(new SemanticDiagnostic(validated.failureCode(), nodeId));
                    result.add(new SemanticInline.Text(nodeId, token));
                }
                offset = matcher.end();
            }
            if (offset < text.length() || result.isEmpty()) {
                String literal = text.substring(offset);
                result.add(new SemanticInline.Text(
                        id(path + ".p" + part, "text", literal), literal));
            }
            return dev.openallay.util.Java8Collections.listCopyOf(result);
        }

        private SemanticInline unsafeInline(Node node, String path) {
            String literal = literal(node);
            String nodeId = id(path, "literal", literal);
            diagnostics.add(new SemanticDiagnostic("semantic_content_unsupported", nodeId));
            return new SemanticInline.Text(nodeId, literal);
        }

        private SemanticBlock unsafeBlock(Node node, String path) {
            String literal = literal(node);
            String nodeId = id(path, "literal_block", literal);
            diagnostics.add(new SemanticDiagnostic("semantic_content_unsupported", nodeId));
            SemanticInline.Text text = new SemanticInline.Text(
                    id(path + ".s0", "text", literal), literal);
            return new SemanticBlock.Paragraph(nodeId, dev.openallay.util.Java8Collections.listOf(text));
        }

        private String literal(Node node) {
            StringBuilder value = new StringBuilder();
            for (SourceSpan span : node.getSourceSpans()) {
                int start = Math.max(0, Math.min(source.length(), span.getInputIndex()));
                int end = Math.max(start, Math.min(source.length(), start + span.getLength()));
                value.append(source, start, end);
            }
            if (!((value).length() == 0)) {
                return value.toString();
            }
            final class $oaPattern15_Holder { org.commonmark.node.Node value; HtmlBlock bound; }
final $oaPattern15_Holder $oaPattern15_holder = new $oaPattern15_Holder();
if ((($oaPattern15_holder.value = node) instanceof org.commonmark.node.HtmlBlock && (($oaPattern15_holder.bound = (HtmlBlock) $oaPattern15_holder.value) != null))) {
                return dev.openallay.util.Java8Objects.requireNonNullElse($oaPattern15_holder.bound.getLiteral(), "");
            }
            final class $oaPattern16_Holder { org.commonmark.node.Node value; HtmlInline bound; }
final $oaPattern16_Holder $oaPattern16_holder = new $oaPattern16_Holder();
if ((($oaPattern16_holder.value = node) instanceof org.commonmark.node.HtmlInline && (($oaPattern16_holder.bound = (HtmlInline) $oaPattern16_holder.value) != null))) {
                return dev.openallay.util.Java8Objects.requireNonNullElse($oaPattern16_holder.bound.getLiteral(), "");
            }
            return plainChildren(node);
        }

        private static String plainChildren(Node parent) {
            StringBuilder text = new StringBuilder();
            for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
                final class $oaPattern17_Holder { org.commonmark.node.Node value; Text bound; }
final $oaPattern17_Holder $oaPattern17_holder = new $oaPattern17_Holder();
if ((($oaPattern17_holder.value = child) instanceof org.commonmark.node.Text && (($oaPattern17_holder.bound = (Text) $oaPattern17_holder.value) != null))) {
                    text.append($oaPattern17_holder.bound.getLiteral());
                } else {
final class $oaPattern18_Holder { org.commonmark.node.Node value; Code bound; }
final $oaPattern18_Holder $oaPattern18_holder = new $oaPattern18_Holder();
if ((($oaPattern18_holder.value = child) instanceof org.commonmark.node.Code && (($oaPattern18_holder.bound = (Code) $oaPattern18_holder.value) != null))) {
                    text.append($oaPattern18_holder.bound.getLiteral());
                } else if (child instanceof SoftLineBreak || child instanceof HardLineBreak) {
                    text.append('\n');
                } else {
                    text.append(plainChildren(child));
                }
}
            }
            return text.toString();
        }

        private static String id(String path, String kind, String content) {
            return SemanticIds.create(path, kind, dev.openallay.util.Java8Objects.requireNonNullElse(content, ""));
        }
    }
}
