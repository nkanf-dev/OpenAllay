package dev.openallay.script.schema;

import dev.latvian.mods.rhino.type.TypeInfo;
import dev.openallay.script.JavascriptExecutionException;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.function.Supplier;

/** Declares one host root without resolving its request-scoped value. */
public final class HostRootDescriptor {
    private final String name;
    private final Type declaredType;
    private final HostSchema schema;
    private final Availability availability;
    private final String providerId;
    private final String summary;
    private final String evidenceOwner;
    private final Supplier<?> supplier;

    public HostRootDescriptor(
            String name,
            Type declaredType,
            boolean available,
            String providerId,
            String summary,
            String evidenceOwner,
            Supplier<?> supplier) {
        this.name = require(name, "name");
        this.declaredType = Objects.requireNonNull(declaredType, "declaredType");
        this.schema = RhinoTypeSchema.require(TypeInfo.of(declaredType));
        this.availability = available ? Availability.AVAILABLE : Availability.UNAVAILABLE;
        this.providerId = require(providerId, "providerId");
        this.summary = require(summary, "summary");
        this.evidenceOwner = require(evidenceOwner, "evidenceOwner");
        this.supplier = supplier;
        if (available && supplier == null) {
            throw new IllegalArgumentException("Available root requires a value supplier");
        }
    }

    public static HostRootDescriptor declaredDynamic(
            String name,
            HostSchema schema,
            boolean available,
            String providerId,
            String summary,
            String evidenceOwner,
            Supplier<?> supplier) {
        return new HostRootDescriptor(
                name,
                schema,
                available,
                providerId,
                summary,
                evidenceOwner,
                supplier);
    }

    public static HostRootDescriptor requestScoped(
            String name,
            Type declaredType,
            String providerId,
            String summary,
            String evidenceOwner) {
        return new HostRootDescriptor(
                name,
                declaredType,
                RhinoTypeSchema.require(TypeInfo.of(declaredType)),
                Availability.REQUEST_SCOPED,
                providerId,
                summary,
                evidenceOwner,
                null);
    }

    public static HostRootDescriptor requestScopedDynamic(
            String name,
            HostSchema schema,
            String providerId,
            String summary,
            String evidenceOwner) {
        return new HostRootDescriptor(
                name,
                null,
                schema,
                Availability.REQUEST_SCOPED,
                providerId,
                summary,
                evidenceOwner,
                null);
    }

    private HostRootDescriptor(
            String name,
            HostSchema schema,
            boolean available,
            String providerId,
            String summary,
            String evidenceOwner,
            Supplier<?> supplier) {
        this(
                name,
                null,
                schema,
                available ? Availability.AVAILABLE : Availability.UNAVAILABLE,
                providerId,
                summary,
                evidenceOwner,
                supplier);
        if (available && supplier == null) {
            throw new IllegalArgumentException("Available root requires a value supplier");
        }
    }

    private HostRootDescriptor(
            String name,
            Type declaredType,
            HostSchema schema,
            Availability availability,
            String providerId,
            String summary,
            String evidenceOwner,
            Supplier<?> supplier) {
        this.name = require(name, "name");
        this.declaredType = declaredType;
        HostSchema.requireKnown(schema);
        this.schema = schema;
        this.availability = Objects.requireNonNull(availability, "availability");
        this.providerId = require(providerId, "providerId");
        this.summary = require(summary, "summary");
        this.evidenceOwner = require(evidenceOwner, "evidenceOwner");
        this.supplier = supplier;
    }

    public String name() {
        return name;
    }

    public Type declaredType() {
        return declaredType;
    }

    public HostSchema schema() {
        return schema;
    }

    public boolean available() {
        return availability == Availability.AVAILABLE;
    }

    public Availability availability() {
        return availability;
    }

    public String providerId() {
        return providerId;
    }

    public String summary() {
        return summary;
    }

    public String evidenceOwner() {
        return evidenceOwner;
    }

    public Object resolve() {
        if (!available() || supplier == null) {
            throw new JavascriptExecutionException(
                    "javascript_root_unavailable",
                    "Requested Minecraft data root is unavailable: " + name);
        }
        Object value = Objects.requireNonNull(supplier.get(), "host root value");
        if (declaredType != null) {
            RhinoTypeSchema.validateValue(declaredType, value);
        }
        return value;
    }

    public enum Availability {
        AVAILABLE,
        UNAVAILABLE,
        REQUEST_SCOPED
    }

    private static String require(String value, String field) {
        if (value == null || dev.openallay.util.Java8Strings.isBlank(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
