package dev.openallay.guide.ui;

import dev.openallay.guide.semantic.RichComponent;
import dev.openallay.guide.semantic.SemanticBlock;
import dev.openallay.guide.semantic.SemanticDocument;
import dev.openallay.guide.semantic.SemanticInline;
import java.util.ArrayList;
import java.util.List;

/** Pure semantic block flattening and width-aware wrapping. */
public final class SemanticLayoutEngine {
    private static final int TABLE_BORDER = 1;
    private static final int TABLE_PADDING_X = 4;
    private static final int TABLE_PADDING_Y = 3;
    private static final int MIN_GRID_COLUMN_WIDTH = 48;
    private static final int CARD_GAP = 4;

    public interface Measurer {
        int width(String text, SemanticLayout.Style style);
        int lineHeight(SemanticLayout.Kind kind);
    }

    public SemanticLayout layout(SemanticDocument document, int width, Measurer measurer) {
        java.util.Objects.requireNonNull(document, "document");
        java.util.Objects.requireNonNull(measurer, "measurer");
        if (width <= 0) throw new IllegalArgumentException("semantic layout width must be positive");
        ArrayList<SemanticLayout.Line> lines = new ArrayList<>();
        for (SemanticBlock block : document.blocks()) flatten(block, 0, width, measurer, lines);
        return new SemanticLayout(
                width,
                lines.stream().mapToInt(SemanticLayout.Line::height).sum(),
                lines,
                document.fallbackText());
    }

    private void flatten(
            SemanticBlock block,
            int indent,
            int width,
            Measurer measurer,
            List<SemanticLayout.Line> output) {
        java.util.Objects.requireNonNull(block);
        final class $oaPattern0_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Paragraph bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.Paragraph && (($oaPattern0_holder.bound = (SemanticBlock.Paragraph) $oaPattern0_holder.value) != null))) {
            addWrapped(
                    $oaPattern0_holder.bound.nodeId(), SemanticLayout.Kind.TEXT, indent,
                    runs($oaPattern0_holder.bound.content(), SemanticLayout.Style.NORMAL), width, measurer, output);
        } else {
final class $oaPattern1_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Heading bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.Heading && (($oaPattern1_holder.bound = (SemanticBlock.Heading) $oaPattern1_holder.value) != null))) {
            addWrapped(
                    $oaPattern1_holder.bound.nodeId(), SemanticLayout.Kind.HEADING, indent,
                    runs($oaPattern1_holder.bound.content(), SemanticLayout.Style.STRONG), width, measurer, output);
        } else {
final class $oaPattern2_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Quote bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.Quote && (($oaPattern2_holder.bound = (SemanticBlock.Quote) $oaPattern2_holder.value) != null))) {
            int from = output.size();
            $oaPattern2_holder.bound.content().forEach(child -> flatten(child, indent + 8, width, measurer, output));
            for (int index = from; index < output.size(); index++) {
                SemanticLayout.Line line = output.get(index);
                if (line.kind() != SemanticLayout.Kind.COMPONENT) {
                    output.set(index, new SemanticLayout.Line(
                            line.nodeId(), SemanticLayout.Kind.QUOTE, line.indent(),
                            line.height(), line.runs(), null));
                }
            }
        } else {
final class $oaPattern3_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.CodeBlock bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if ((($oaPattern3_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.CodeBlock && (($oaPattern3_holder.bound = (SemanticBlock.CodeBlock) $oaPattern3_holder.value) != null))) {
            String[] codeLines = $oaPattern3_holder.bound.code().split("\\R", -1);
            for (int index = 0; index < codeLines.length; index++) {
                addWrapped($oaPattern3_holder.bound.nodeId() + "-" + index, SemanticLayout.Kind.CODE, indent + 4,
                        List.of(new SemanticLayout.Run(
                                codeLines[index], SemanticLayout.Style.CODE, null)),
                        width, measurer, output);
            }
        } else {
final class $oaPattern4_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.ListBlock bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.ListBlock && (($oaPattern4_holder.bound = (SemanticBlock.ListBlock) $oaPattern4_holder.value) != null))) {
            int number = $oaPattern4_holder.bound.start();
            int itemIndex = 0;
            for (List<SemanticBlock> item : $oaPattern4_holder.bound.items()) {
                String marker = $oaPattern4_holder.bound.ordered() ? number++ + ". " : "• ";
                int contentIndent = indent + Math.max(
                        1, measurer.width(marker, SemanticLayout.Style.STRONG));
                int firstParagraph = firstParagraph(item);
                if (firstParagraph < 0) {
                    output.add(new SemanticLayout.Line(
                            $oaPattern4_holder.bound.nodeId() + "-marker-" + itemIndex,
                            SemanticLayout.Kind.TEXT,
                            indent,
                            measurer.lineHeight(SemanticLayout.Kind.TEXT),
                            List.of(new SemanticLayout.Run(
                                    marker, SemanticLayout.Style.STRONG, null)),
                            null));
                }
                for (int childIndex = 0; childIndex < item.size(); childIndex++) {
                    SemanticBlock child = item.get(childIndex);
                    if (childIndex == firstParagraph) {
                        SemanticBlock.Paragraph paragraph = (SemanticBlock.Paragraph) child;
                        ArrayList<SemanticLayout.Run> marked = new ArrayList<>();
                        marked.add(new SemanticLayout.Run(
                                marker, SemanticLayout.Style.STRONG, null));
                        marked.addAll(runs(paragraph.content(), SemanticLayout.Style.NORMAL));
                        addWrapped(
                                paragraph.nodeId(), SemanticLayout.Kind.TEXT,
                                indent, contentIndent, marked, width, measurer, output);
                    } else {
                        flatten(child, contentIndent, width, measurer, output);
                    }
                }
                itemIndex++;
            }
        } else {
final class $oaPattern5_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Table bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
if ((($oaPattern5_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.Table && (($oaPattern5_holder.bound = (SemanticBlock.Table) $oaPattern5_holder.value) != null))) {
            SemanticLayout.TableBox table = table($oaPattern5_holder.bound, Math.max(1, width - indent), measurer);
            output.add(new SemanticLayout.Line(
                    $oaPattern5_holder.bound.nodeId(), SemanticLayout.Kind.TABLE, indent,
                    table.height(), List.of(), null, table));
        } else {
final class $oaPattern6_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.ThematicBreak bound; }
final $oaPattern6_Holder $oaPattern6_holder = new $oaPattern6_Holder();
if ((($oaPattern6_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.ThematicBreak && (($oaPattern6_holder.bound = (SemanticBlock.ThematicBreak) $oaPattern6_holder.value) != null))) {
            output.add(new SemanticLayout.Line(
                    $oaPattern6_holder.bound.nodeId(), SemanticLayout.Kind.RULE, indent,
                    measurer.lineHeight(SemanticLayout.Kind.RULE), List.of(), null));
        } else {
final class $oaPattern7_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Component bound; }
final $oaPattern7_Holder $oaPattern7_holder = new $oaPattern7_Holder();
if ((($oaPattern7_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.Component && (($oaPattern7_holder.bound = (SemanticBlock.Component) $oaPattern7_holder.value) != null))) {
            output.add(new SemanticLayout.Line(
                    $oaPattern7_holder.bound.nodeId(), SemanticLayout.Kind.COMPONENT, indent,
                    componentHeight($oaPattern7_holder.bound.component(), measurer), List.of(), $oaPattern7_holder.bound.component()));
        } else {
            throw new IncompatibleClassChangeError();
        }
}
}
}
}
}
}
}
    }

    private SemanticLayout.TableBox table(
            SemanticBlock.Table table,
            int width,
            Measurer measurer) {
        int columns = Math.max(
                table.header().cells().size(),
                table.rows().stream().mapToInt(row -> row.cells().size()).max().orElse(0));
        if (columns == 0) {
            throw new IllegalArgumentException("semantic table has no columns");
        }
        return width >= columns * MIN_GRID_COLUMN_WIDTH
                ? gridTable(table, columns, width, measurer)
                : cardTable(table, columns, width, measurer);
    }

    private SemanticLayout.TableBox gridTable(
            SemanticBlock.Table table,
            int columns,
            int availableWidth,
            Measurer measurer) {
        List<SemanticBlock.TableRow> sourceRows = new ArrayList<>();
        sourceRows.add(table.header());
        sourceRows.addAll(table.rows());
        int[] desired = new int[columns];
        java.util.Arrays.fill(desired, MIN_GRID_COLUMN_WIDTH);
        for (SemanticBlock.TableRow row : sourceRows) {
            for (int column = 0; column < row.cells().size(); column++) {
                List<SemanticLayout.Run> values = runs(
                        row.cells().get(column).content(), SemanticLayout.Style.NORMAL);
                desired[column] = Math.max(desired[column],
                        runWidth(values, measurer) + TABLE_PADDING_X * 2 + TABLE_BORDER);
            }
        }
        int[] widths = distributeColumns(desired, availableWidth);
        int lineHeight = measurer.lineHeight(SemanticLayout.Kind.TABLE);
        ArrayList<SemanticLayout.TableRow> rows = new ArrayList<>();
        int rowY = 0;
        for (int rowIndex = 0; rowIndex < sourceRows.size(); rowIndex++) {
            SemanticBlock.TableRow source = sourceRows.get(rowIndex);
            boolean header = rowIndex == 0;
            ArrayList<List<SemanticLayout.CellLine>> wrapped = new ArrayList<>();
            int rowHeight = lineHeight + TABLE_PADDING_Y * 2 + TABLE_BORDER;
            for (int column = 0; column < columns; column++) {
                SemanticBlock.TableCell cell = cell(source, column);
                List<SemanticLayout.CellLine> lines = cellLines(
                        cell.content(),
                        header ? SemanticLayout.Style.STRONG : SemanticLayout.Style.NORMAL,
                        Math.max(1, widths[column] - TABLE_PADDING_X * 2 - TABLE_BORDER),
                        lineHeight,
                        measurer);
                wrapped.add(lines);
                rowHeight = Math.max(rowHeight,
                        lines.size() * lineHeight + TABLE_PADDING_Y * 2 + TABLE_BORDER);
            }
            ArrayList<SemanticLayout.TableCell> cells = new ArrayList<>();
            int cellX = 0;
            for (int column = 0; column < columns; column++) {
                SemanticBlock.TableCell sourceCell = cell(source, column);
                cells.add(new SemanticLayout.TableCell(
                        column, cellX, rowY, widths[column], rowHeight,
                        sourceCell.alignment(), List.of(), wrapped.get(column)));
                cellX += widths[column];
            }
            rows.add(new SemanticLayout.TableRow(header, rowY, rowHeight, cells));
            rowY += rowHeight;
        }
        return new SemanticLayout.TableBox(
                SemanticLayout.TableBox.Mode.GRID,
                java.util.Arrays.stream(widths).sum(), rowY, lineHeight, rows);
    }

    private SemanticLayout.TableBox cardTable(
            SemanticBlock.Table table,
            int columns,
            int width,
            Measurer measurer) {
        int lineHeight = measurer.lineHeight(SemanticLayout.Kind.TABLE);
        List<SemanticBlock.TableRow> dataRows = table.rows().isEmpty()
                ? List.of(table.header()) : table.rows();
        ArrayList<SemanticLayout.TableRow> rows = new ArrayList<>();
        int cardY = 0;
        for (int rowIndex = 0; rowIndex < dataRows.size(); rowIndex++) {
            SemanticBlock.TableRow source = dataRows.get(rowIndex);
            ArrayList<SemanticLayout.TableCell> cells = new ArrayList<>();
            int cellY = cardY + TABLE_PADDING_Y + TABLE_BORDER;
            for (int column = 0; column < columns; column++) {
                SemanticBlock.TableCell value = cell(source, column);
                SemanticBlock.TableCell header = cell(table.header(), column);
                int innerWidth = Math.max(1, width - TABLE_PADDING_X * 2 - TABLE_BORDER * 2);
                List<SemanticLayout.CellLine> labels = cellLines(
                        header.content(), SemanticLayout.Style.STRONG,
                        innerWidth, lineHeight, measurer);
                List<SemanticLayout.CellLine> values = cellLines(
                        value.content(), SemanticLayout.Style.NORMAL,
                        innerWidth, lineHeight, measurer);
                int cellHeight = (labels.size() + values.size()) * lineHeight + TABLE_PADDING_Y;
                cells.add(new SemanticLayout.TableCell(
                        column,
                        TABLE_BORDER + TABLE_PADDING_X,
                        cellY,
                        innerWidth,
                        cellHeight,
                        value.alignment(),
                        labels,
                        values));
                cellY += cellHeight;
            }
            int cardHeight = cellY - cardY + TABLE_PADDING_Y;
            rows.add(new SemanticLayout.TableRow(false, cardY, cardHeight, cells));
            cardY += cardHeight + (rowIndex + 1 < dataRows.size() ? CARD_GAP : 0);
        }
        return new SemanticLayout.TableBox(
                SemanticLayout.TableBox.Mode.KEY_VALUE_CARDS,
                width, cardY, lineHeight, rows);
    }

    private static SemanticBlock.TableCell cell(SemanticBlock.TableRow row, int column) {
        return column < row.cells().size()
                ? row.cells().get(column)
                : new SemanticBlock.TableCell(SemanticBlock.Alignment.NONE, List.of());
    }

    private static int[] distributeColumns(int[] desired, int available) {
        int[] widths = new int[desired.length];
        java.util.Arrays.fill(widths, MIN_GRID_COLUMN_WIDTH);
        int remaining = available - MIN_GRID_COLUMN_WIDTH * desired.length;
        int totalExtra = java.util.Arrays.stream(desired)
                .map(value -> Math.max(0, value - MIN_GRID_COLUMN_WIDTH)).sum();
        for (int index = 0; index < widths.length && remaining > 0; index++) {
            int extra = totalExtra == 0
                    ? remaining / (widths.length - index)
                    : (int) ((long) remaining
                            * Math.max(0, desired[index] - MIN_GRID_COLUMN_WIDTH)
                            / totalExtra);
            extra = Math.min(remaining, extra);
            widths[index] += extra;
            remaining -= extra;
            totalExtra -= Math.max(0, desired[index] - MIN_GRID_COLUMN_WIDTH);
        }
        if (remaining > 0) widths[widths.length - 1] += remaining;
        return widths;
    }

    private static List<SemanticLayout.CellLine> cellLines(
            List<SemanticInline> content,
            SemanticLayout.Style inherited,
            int width,
            int lineHeight,
            Measurer measurer) {
        List<List<SemanticLayout.Run>> wrapped = wrapRuns(runs(content, inherited), width, measurer);
        ArrayList<SemanticLayout.CellLine> result = new ArrayList<>();
        for (int index = 0; index < wrapped.size(); index++) {
            List<SemanticLayout.Run> line = wrapped.get(index);
            result.add(new SemanticLayout.CellLine(
                    index * lineHeight, runWidth(line, measurer), line));
        }
        return List.copyOf(result);
    }

    private static int runWidth(List<SemanticLayout.Run> runs, Measurer measurer) {
        return runs.stream().mapToInt(run -> measurer.width(run.text(), run.style())).sum();
    }

    private static List<List<SemanticLayout.Run>> wrapRuns(
            List<SemanticLayout.Run> runs, int width, Measurer measurer) {
        ArrayList<List<SemanticLayout.Run>> output = new ArrayList<>();
        ArrayList<SemanticLayout.Run> line = new ArrayList<>();
        int used = 0;
        for (SemanticLayout.Run run : runs) {
            StringBuilder chunk = new StringBuilder();
            for (int offset = 0; offset < run.text().length();) {
                int codePoint = run.text().codePointAt(offset);
                String value = new String(Character.toChars(codePoint));
                if (codePoint == '\n') {
                    flushChunk(line, chunk, run);
                    output.add(List.copyOf(line));
                    line = new ArrayList<>();
                    used = 0;
                    offset += Character.charCount(codePoint);
                    continue;
                }
                int valueWidth = Math.max(1, measurer.width(value, run.style()));
                if (used + valueWidth > width && (!line.isEmpty() || !chunk.isEmpty())) {
                    flushChunk(line, chunk, run);
                    output.add(List.copyOf(line));
                    line = new ArrayList<>();
                    used = 0;
                }
                chunk.append(value);
                used += valueWidth;
                offset += Character.charCount(codePoint);
            }
            flushChunk(line, chunk, run);
        }
        if (!line.isEmpty() || output.isEmpty()) output.add(List.copyOf(line));
        return List.copyOf(output);
    }

    private static void flushChunk(
            List<SemanticLayout.Run> line,
            StringBuilder chunk,
            SemanticLayout.Run source) {
        if (chunk.isEmpty()) return;
        line.add(new SemanticLayout.Run(
                chunk.toString(), source.style(), source.reference()));
        chunk.setLength(0);
    }

    private void addWrapped(
            String nodeId,
            SemanticLayout.Kind kind,
            int indent,
            List<SemanticLayout.Run> runs,
            int width,
            Measurer measurer,
            List<SemanticLayout.Line> output) {
        addWrapped(nodeId, kind, indent, indent, runs, width, measurer, output);
    }

    private void addWrapped(
            String nodeId,
            SemanticLayout.Kind kind,
            int firstIndent,
            int continuationIndent,
            List<SemanticLayout.Run> runs,
            int width,
            Measurer measurer,
            List<SemanticLayout.Line> output) {
        int indent = firstIndent;
        int available = Math.max(1, width - indent);
        int initialOutputSize = output.size();
        ArrayList<SemanticLayout.Run> line = new ArrayList<>();
        int used = 0;
        int lineIndex = 0;
        for (SemanticLayout.Run run : runs) {
            StringBuilder chunk = new StringBuilder();
            for (int offset = 0; offset < run.text().length();) {
                int codePoint = run.text().codePointAt(offset);
                String value = new String(Character.toChars(codePoint));
                if (codePoint == '\n') {
                    if (!chunk.isEmpty()) {
                        line.add(new SemanticLayout.Run(
                                chunk.toString(), run.style(), run.reference()));
                        chunk.setLength(0);
                    }
                    output.add(line(nodeId, lineIndex++, kind, indent, measurer, line));
                    line = new ArrayList<>();
                    used = 0;
                    indent = continuationIndent;
                    available = Math.max(1, width - indent);
                    offset += Character.charCount(codePoint);
                    continue;
                }
                int valueWidth = Math.max(1, measurer.width(value, run.style()));
                if (used + valueWidth > available && (!line.isEmpty() || !chunk.isEmpty())) {
                    if (!chunk.isEmpty()) {
                        line.add(new SemanticLayout.Run(
                                chunk.toString(), run.style(), run.reference()));
                        chunk.setLength(0);
                    }
                    output.add(line(nodeId, lineIndex++, kind, indent, measurer, line));
                    line = new ArrayList<>();
                    used = 0;
                    indent = continuationIndent;
                    available = Math.max(1, width - indent);
                }
                chunk.append(value);
                used += valueWidth;
                offset += Character.charCount(codePoint);
            }
            if (!chunk.isEmpty()) line.add(new SemanticLayout.Run(
                    chunk.toString(), run.style(), run.reference()));
        }
        if (!line.isEmpty() || output.size() == initialOutputSize) {
            output.add(line(nodeId, lineIndex, kind, indent, measurer, line));
        }
    }

    private static int firstParagraph(List<SemanticBlock> item) {
        for (int index = 0; index < item.size(); index++) {
            if (item.get(index) instanceof SemanticBlock.Paragraph) return index;
        }
        return -1;
    }

    private static SemanticLayout.Line line(
            String nodeId,
            int lineIndex,
            SemanticLayout.Kind kind,
            int indent,
            Measurer measurer,
            List<SemanticLayout.Run> runs) {
        return new SemanticLayout.Line(
                nodeId + "-line-" + lineIndex, kind, indent,
                measurer.lineHeight(kind), runs, null);
    }

    private static List<SemanticLayout.Run> runs(
            List<SemanticInline> inlines, SemanticLayout.Style inherited) {
        ArrayList<SemanticLayout.Run> result = new ArrayList<>();
        for (SemanticInline inline : inlines) {
            java.util.Objects.requireNonNull(inline);
            final class $oaPattern8_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Text bound; }
final $oaPattern8_Holder $oaPattern8_holder = new $oaPattern8_Holder();
if ((($oaPattern8_holder.value = inline) instanceof dev.openallay.guide.semantic.SemanticInline.Text && (($oaPattern8_holder.bound = (SemanticInline.Text) $oaPattern8_holder.value) != null))) {
                result.add(new SemanticLayout.Run($oaPattern8_holder.bound.text(), inherited, null));
            } else {
final class $oaPattern9_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Code bound; }
final $oaPattern9_Holder $oaPattern9_holder = new $oaPattern9_Holder();
if ((($oaPattern9_holder.value = inline) instanceof dev.openallay.guide.semantic.SemanticInline.Code && (($oaPattern9_holder.bound = (SemanticInline.Code) $oaPattern9_holder.value) != null))) {
                result.add(new SemanticLayout.Run($oaPattern9_holder.bound.text(), SemanticLayout.Style.CODE, null));
            } else {
final class $oaPattern10_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Break bound; }
final $oaPattern10_Holder $oaPattern10_holder = new $oaPattern10_Holder();
if ((($oaPattern10_holder.value = inline) instanceof dev.openallay.guide.semantic.SemanticInline.Break && (($oaPattern10_holder.bound = (SemanticInline.Break) $oaPattern10_holder.value) != null))) {
                result.add(new SemanticLayout.Run("\n", inherited, null));
            } else {
final class $oaPattern11_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Emphasis bound; }
final $oaPattern11_Holder $oaPattern11_holder = new $oaPattern11_Holder();
if ((($oaPattern11_holder.value = inline) instanceof dev.openallay.guide.semantic.SemanticInline.Emphasis && (($oaPattern11_holder.bound = (SemanticInline.Emphasis) $oaPattern11_holder.value) != null))) {
                result.addAll(runs($oaPattern11_holder.bound.children(), SemanticLayout.Style.EMPHASIS));
            } else {
final class $oaPattern12_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Strong bound; }
final $oaPattern12_Holder $oaPattern12_holder = new $oaPattern12_Holder();
if ((($oaPattern12_holder.value = inline) instanceof dev.openallay.guide.semantic.SemanticInline.Strong && (($oaPattern12_holder.bound = (SemanticInline.Strong) $oaPattern12_holder.value) != null))) {
                result.addAll(runs($oaPattern12_holder.bound.children(), SemanticLayout.Style.STRONG));
            } else {
final class $oaPattern13_Holder { dev.openallay.guide.semantic.SemanticInline value; SemanticInline.Reference bound; }
final $oaPattern13_Holder $oaPattern13_holder = new $oaPattern13_Holder();
if ((($oaPattern13_holder.value = inline) instanceof dev.openallay.guide.semantic.SemanticInline.Reference && (($oaPattern13_holder.bound = (SemanticInline.Reference) $oaPattern13_holder.value) != null))) {
                result.add(new SemanticLayout.Run(
                        $oaPattern13_holder.bound.reference().displayText(), SemanticLayout.Style.REFERENCE,
                        $oaPattern13_holder.bound.reference()));
            } else {
                throw new IncompatibleClassChangeError();
            }
}
}
}
}
}
        }
        return List.copyOf(result);
    }

    private static int componentHeight(RichComponent component, Measurer measurer) {
        int line = measurer.lineHeight(SemanticLayout.Kind.COMPONENT);
        java.util.Objects.requireNonNull(component);
        final class $oaPattern14_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.ItemRow bound; }
final $oaPattern14_Holder $oaPattern14_holder = new $oaPattern14_Holder();
if ((($oaPattern14_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.ItemRow && (($oaPattern14_holder.bound = (RichComponent.ItemRow) $oaPattern14_holder.value) != null))) {
            return Math.max(22, 22 * $oaPattern14_holder.bound.items().size());
        } else {
final class $oaPattern15_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.RecipeGrid bound; }
final $oaPattern15_Holder $oaPattern15_holder = new $oaPattern15_Holder();
if ((($oaPattern15_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.RecipeGrid && (($oaPattern15_holder.bound = (RichComponent.RecipeGrid) $oaPattern15_holder.value) != null))) {
            return Math.max(136, line * 13);
        } else {
final class $oaPattern16_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.IngredientCheck bound; }
final $oaPattern16_Holder $oaPattern16_holder = new $oaPattern16_Holder();
if ((($oaPattern16_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.IngredientCheck && (($oaPattern16_holder.bound = (RichComponent.IngredientCheck) $oaPattern16_holder.value) != null))) {
            return Math.max(22, 22 * $oaPattern16_holder.bound.ingredients().size());
        } else {
final class $oaPattern17_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.CraftabilitySummary bound; }
final $oaPattern17_Holder $oaPattern17_holder = new $oaPattern17_Holder();
if ((($oaPattern17_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.CraftabilitySummary && (($oaPattern17_holder.bound = (RichComponent.CraftabilitySummary) $oaPattern17_holder.value) != null))) {
            return 40;
        } else {
final class $oaPattern18_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.ProgressSteps bound; }
final $oaPattern18_Holder $oaPattern18_holder = new $oaPattern18_Holder();
if ((($oaPattern18_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.ProgressSteps && (($oaPattern18_holder.bound = (RichComponent.ProgressSteps) $oaPattern18_holder.value) != null))) {
            return 12 * ($oaPattern18_holder.bound.steps().size() + 1);
        } else {
final class $oaPattern19_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.SourceSummary bound; }
final $oaPattern19_Holder $oaPattern19_holder = new $oaPattern19_Holder();
if ((($oaPattern19_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.SourceSummary && (($oaPattern19_holder.bound = (RichComponent.SourceSummary) $oaPattern19_holder.value) != null))) {
            return 12 * ($oaPattern19_holder.bound.sources().size() + 1);
        } else {
final class $oaPattern20_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.StatusBadge bound; }
final $oaPattern20_Holder $oaPattern20_holder = new $oaPattern20_Holder();
if ((($oaPattern20_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.StatusBadge && (($oaPattern20_holder.bound = (RichComponent.StatusBadge) $oaPattern20_holder.value) != null))) {
            return 16;
        } else {
final class $oaPattern21_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.ChoiceGroup bound; }
final $oaPattern21_Holder $oaPattern21_holder = new $oaPattern21_Holder();
if ((($oaPattern21_holder.value = component) instanceof dev.openallay.guide.semantic.RichComponent.ChoiceGroup && (($oaPattern21_holder.bound = (RichComponent.ChoiceGroup) $oaPattern21_holder.value) != null))) {
            return 12 * ($oaPattern21_holder.bound.choices().size() + 1);
        }
}
}
}
}
}
}
}
        throw new IncompatibleClassChangeError();
    }
}
