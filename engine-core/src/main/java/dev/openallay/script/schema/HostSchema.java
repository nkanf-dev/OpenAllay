package dev.openallay.script.schema;

import java.util.List;
import java.util.Map;

/** Closed, script-visible description of values accepted by the Rhino host adapter. */
public sealed interface HostSchema
        permits HostSchema.Scalar,
                HostSchema.Enumeration,
                HostSchema.Sequence,
                HostSchema.OptionalValue,
                HostSchema.Dictionary,
                HostSchema.RecordValue,
                HostSchema.DynamicJson,
                HostSchema.DynamicDetached {
    String kind();

    record Scalar(String kind) implements HostSchema {}

    record Enumeration(String kind, List<String> values) implements HostSchema {
        public Enumeration {
            values = List.copyOf(values);
        }
    }

    record Sequence(String kind, HostSchema elements) implements HostSchema {
        public Sequence {
            java.util.Objects.requireNonNull(elements, "elements");
        }
    }

    record OptionalValue(String kind, HostSchema value) implements HostSchema {
        public OptionalValue {
            java.util.Objects.requireNonNull(value, "value");
        }
    }

    record Dictionary(String kind, HostSchema values, boolean dynamicKeys)
            implements HostSchema {
        public Dictionary {
            java.util.Objects.requireNonNull(values, "values");
        }
    }

    record RecordValue(String kind, String javaType, Map<String, HostSchema> fields)
            implements HostSchema {
        public RecordValue {
            if (javaType == null || javaType.isBlank()) {
                throw new IllegalArgumentException("javaType must not be blank");
            }
            fields = Map.copyOf(fields);
        }
    }

    record DynamicJson(String kind) implements HostSchema {}

    /**
     * Heterogeneous values whose individual schemas are declared in a sibling catalog.
     * This is reserved for the extension aggregate and does not authorize arbitrary Java access.
     */
    record DynamicDetached(String kind) implements HostSchema {}
}
