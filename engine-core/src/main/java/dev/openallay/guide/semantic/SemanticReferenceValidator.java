package dev.openallay.guide.semantic;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the closed inline syntax and enforces same-request stable-handle authority. */
public final class SemanticReferenceValidator {
    private static final Pattern TOKEN = Pattern.compile(
            "\\[\\[tw:([a-z_]+)\\|([^|\\]\\r\\n]+)(?:\\|([^\\]\\r\\n]+))?\\]\\]");
    private static final Pattern RESOURCE = Pattern.compile(
            "[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern KEY = Pattern.compile("[a-zA-Z0-9_.-]+");

    public Validation validate(String token, SemanticReferenceIndex index) {
        Objects.requireNonNull(index, "index");
        Matcher matcher = TOKEN.matcher(Objects.requireNonNullElse(token, ""));
        if (!matcher.matches()) {
            return Validation.failure("semantic_content_invalid");
        }
        SemanticReferenceKind kind;
        try {
            kind = SemanticReferenceKind.fromToken(matcher.group(1));
        } catch (IllegalArgumentException unknown) {
            return Validation.failure("semantic_reference_unsupported");
        }
        String target = dev.openallay.util.Java8Strings.strip(matcher.group(2));
        String label = matcher.group(3) == null ? "" : dev.openallay.util.Java8Strings.strip(matcher.group(3));
        if (dev.openallay.util.Java8Strings.isBlank(target) || (matcher.group(3) != null && dev.openallay.util.Java8Strings.isBlank(label))) {
            return Validation.failure("semantic_content_invalid");
        }
        if (!syntax(kind, target)) {
            return Validation.failure("semantic_reference_unresolved");
        }
        Optional<String> origin = index.origin(kind, target);
        if (origin.isPresent()) {
            return Validation.success(new SemanticReference(
                    kind, target, label, true, origin.orElseThrow()));
        }
        if (!kind.permitsUngroundedPresentation()) {
            return Validation.failure("semantic_reference_unresolved");
        }
        return Validation.success(new SemanticReference(kind, target, label, false, null));
    }

    Matcher matcher(String source) {
        return TOKEN.matcher(Objects.requireNonNullElse(source, ""));
    }

    public static boolean isResourceId(String value) {
        return value != null && RESOURCE.matcher(value).matches();
    }

    private static boolean syntax(SemanticReferenceKind kind, String target) {
        {
boolean $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((kind)) {
case ITEM:
case BLOCK:
case FLUID:
case ENTITY:
case BIOME:
case DIMENSION:
{
$oaSwitch0_exit_result = isResourceId(target); break $oaSwitch0_exit;
}
case TAG:
{
$oaSwitch0_exit_result = target.startsWith("#") && isResourceId(target.substring(1)); break $oaSwitch0_exit;
}
case KEY:
{
$oaSwitch0_exit_result = KEY.matcher(target).matches(); break $oaSwitch0_exit;
}
case RECIPE:
{
$oaSwitch0_exit_result = recipe(target); break $oaSwitch0_exit;
}
case SOURCE:
case EVIDENCE:
{
$oaSwitch0_exit_result = isResourceId(target); break $oaSwitch0_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
return $oaSwitch0_exit_result;
}
    }

    private static boolean recipe(String target) {
        try {
            RecipeSemanticHandle.decode(target);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    @dev.openallay.value.ValueType(Validation.ValueSchemaProvider.class)
public static final class Validation {
    private final SemanticReference reference;
    private final String failureCode;
    public Validation(SemanticReference reference, String failureCode) {

            if ((reference == null) == (failureCode == null)) {
                throw new IllegalArgumentException("reference validation must succeed or fail");
            }

        this.reference = reference;
        this.failureCode = failureCode;
    }
    public SemanticReference reference() { return reference; }
    public String failureCode() { return failureCode; }
public static Validation success(SemanticReference reference) {
            return new Validation(Objects.requireNonNull(reference, "reference"), null);
        }
public static Validation failure(String code) {
            return new Validation(null, Objects.requireNonNull(code, "code"));
        }
public boolean successful() {
            return reference != null;
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Validation)) return false;
        Validation that = (Validation) other;
        return java.util.Objects.equals(reference, that.reference) && java.util.Objects.equals(failureCode, that.failureCode);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(reference);
        hash = 31 * hash + java.util.Objects.hashCode(failureCode);
        return hash;
    }
    @Override public String toString() { return "Validation[reference=" + reference + ", failureCode=" + failureCode + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Validation> schema() {
            return new dev.openallay.value.ValueSchema<>(Validation.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Validation>>asList(new dev.openallay.value.ValueSchema.Component<>(Validation.class, "reference", Validation::reference), new dev.openallay.value.ValueSchema.Component<>(Validation.class, "failureCode", Validation::failureCode)), arguments -> new Validation((SemanticReference) arguments[0], (String) arguments[1]));
        }
    }
}
}
