package dev.openallay.tool.query;

import dev.openallay.agent.tool.ToolDescription;
import dev.openallay.agent.tool.ToolOptional;
import dev.openallay.value.ValueSchema;
import dev.openallay.value.ValueType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Closed, data-only operations over one detached OpenAllay virtual dataset. */
@ToolDescription("One closed query stage. Only fields declared by the selected dataset may be used.")
@ValueType(QueryOperation.Schema.class)
public final class QueryOperation {
    public enum Op { SEARCH, FILTER, SELECT, SORT, GROUP, AGGREGATE, EXPAND, TAKE }
    public enum Operator { EQ, NE, CONTAINS, EXISTS, GT, GTE, LT, LTE }
    public enum Direction { ASC, DESC }
    public enum Aggregate { COUNT, MIN, MAX, SUM, AVG }

    @ToolDescription("Stage operation")
    private final Op op;
    @ToolDescription("RFC 6901 JSON Pointer returned by schema discovery; used by FILTER, SORT, GROUP, AGGREGATE, or EXPAND") @ToolOptional
    private final String field;
    @ToolDescription("Comparison used by FILTER") @ToolOptional
    private final Operator operator;
    @ToolDescription("Literal value used by FILTER or SEARCH") @ToolOptional
    private final String value;
    @ToolDescription("Fields retained by SELECT") @ToolOptional
    private final List<String> fields;
    @ToolDescription("Sort direction; defaults to ASC") @ToolOptional
    private final Direction direction;
    @ToolDescription("Aggregate function") @ToolOptional
    private final Aggregate aggregate;
    @ToolDescription("Optional grouping field for AGGREGATE") @ToolOptional
    private final String groupBy;
    @ToolDescription("Number of rows retained by TAKE") @ToolOptional
    private final Integer count;

    public QueryOperation(Op op, String field, Operator operator, String value, List<String> fields, Direction direction, Aggregate aggregate, String groupBy, Integer count) {
        this.op = op;
        this.field = field;
        this.operator = operator;
        this.value = value;
        if (fields == null) this.fields = null;
        else {
            List<String> copy = new ArrayList<>();
            for (String field : fields) copy.add(Objects.requireNonNull(field, "field"));
            this.fields = Collections.unmodifiableList(copy);
        }
        this.direction = direction;
        this.aggregate = aggregate;
        this.groupBy = groupBy;
        this.count = count;
    }

    public Op op() { return op; }
    public String field() { return field; }
    public Operator operator() { return operator; }
    public String value() { return value; }
    public List<String> fields() { return fields; }
    public Direction direction() { return direction; }
    public Aggregate aggregate() { return aggregate; }
    public String groupBy() { return groupBy; }
    public Integer count() { return count; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof QueryOperation)) return false;
        QueryOperation that = (QueryOperation) other;
        return Objects.equals(op, that.op)
                && Objects.equals(field, that.field)
                && Objects.equals(operator, that.operator)
                && Objects.equals(value, that.value)
                && Objects.equals(fields, that.fields)
                && Objects.equals(direction, that.direction)
                && Objects.equals(aggregate, that.aggregate)
                && Objects.equals(groupBy, that.groupBy)
                && Objects.equals(count, that.count);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Objects.hashCode(op);
        hash = 31 * hash + Objects.hashCode(field);
        hash = 31 * hash + Objects.hashCode(operator);
        hash = 31 * hash + Objects.hashCode(value);
        hash = 31 * hash + Objects.hashCode(fields);
        hash = 31 * hash + Objects.hashCode(direction);
        hash = 31 * hash + Objects.hashCode(aggregate);
        hash = 31 * hash + Objects.hashCode(groupBy);
        hash = 31 * hash + Objects.hashCode(count);
        return hash;
    }
    @Override public String toString() {
        return "QueryOperation[" + "op=" + op + ", " + "field=" + field + ", " + "operator=" + operator + ", " + "value=" + value + ", " + "fields=" + fields + ", " + "direction=" + direction + ", " + "aggregate=" + aggregate + ", " + "groupBy=" + groupBy + ", " + "count=" + count + "]";
    }

    /** Owner-local calls preserve constructor validation without reflective field access. */
    public static final class Schema implements ValueSchema.Provider {
        public Schema() {}
        @Override public ValueSchema<QueryOperation> schema() {
            return new ValueSchema<>(QueryOperation.class, Arrays.asList(
                    new ValueSchema.Component<>(QueryOperation.class, "op", QueryOperation::op),
                    new ValueSchema.Component<>(QueryOperation.class, "field", QueryOperation::field),
                    new ValueSchema.Component<>(QueryOperation.class, "operator", QueryOperation::operator),
                    new ValueSchema.Component<>(QueryOperation.class, "value", QueryOperation::value),
                    new ValueSchema.Component<>(QueryOperation.class, "fields", QueryOperation::fields),
                    new ValueSchema.Component<>(QueryOperation.class, "direction", QueryOperation::direction),
                    new ValueSchema.Component<>(QueryOperation.class, "aggregate", QueryOperation::aggregate),
                    new ValueSchema.Component<>(QueryOperation.class, "groupBy", QueryOperation::groupBy),
                    new ValueSchema.Component<>(QueryOperation.class, "count", QueryOperation::count)), arguments -> new QueryOperation(
                    (Op) arguments[0], (String) arguments[1], (Operator) arguments[2],
                    (String) arguments[3], strings(arguments[4]), (Direction) arguments[5],
                    (Aggregate) arguments[6], (String) arguments[7], (Integer) arguments[8]));
        }
        @SuppressWarnings("unchecked") private static List<String> strings(Object value) {
            return (List<String>) value;
        }
    }
}
