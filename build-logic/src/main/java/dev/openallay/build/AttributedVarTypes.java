package dev.openallay.build;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.SimpleTypeVisitor8;

/** Build-only denotable type rendering extracted from the accepted RhinoVarPort. */
public final class AttributedVarTypes {
    private AttributedVarTypes() {}
    public static String denotable(TypeMirror type) {
        return denotable(type, false);
    }

    /** Local class names are permitted only after the caller proves lexical scope. */
    public static String denotable(TypeMirror type, boolean allowNamedLocal) {
        return type.accept(new SimpleTypeVisitor8<String, Void>() {
            @Override protected String defaultAction(TypeMirror t, Void unused) {
                throw new IllegalArgumentException("Unsupported inferred type " + t.getKind() + ": " + t);
            }
            @Override public String visitError(ErrorType t, Void unused) {
                if (!allowNamedLocal) return super.visitError(t, unused);
                throw new IllegalArgumentException("Unsupported inferred type ERROR: " + t);
            }
            @Override public String visitPrimitive(PrimitiveType t, Void unused) { return t.toString(); }
            @Override public String visitArray(ArrayType t, Void unused) { return denotable(t.getComponentType(), allowNamedLocal) + "[]"; }
            @Override public String visitDeclared(DeclaredType t, Void unused) {
                TypeElement element = (TypeElement) t.asElement();
                String name = element.getQualifiedName().toString();
                if (element.getNestingKind() == NestingKind.LOCAL && allowNamedLocal) {
                    name = element.getSimpleName().toString();
                }
                if (name.isEmpty() || element.getNestingKind() == NestingKind.ANONYMOUS
                        || (element.getNestingKind() == NestingKind.LOCAL && !allowNamedLocal)) {
                    throw new IllegalArgumentException("Non-denotable declaration: " + t);
                }
                StringBuilder result = new StringBuilder();
                if (element.getNestingKind() != NestingKind.LOCAL
                        && t.getEnclosingType().getKind() == TypeKind.DECLARED
                        && !element.getModifiers().contains(Modifier.STATIC)) {
                    result.append(denotable(t.getEnclosingType(), allowNamedLocal)).append('.').append(element.getSimpleName());
                } else { result.append(name); }
                if (!t.getTypeArguments().isEmpty()) {
                    result.append('<');
                    for (int i = 0; i < t.getTypeArguments().size(); i++) {
                        if (i > 0) result.append(", ");
                        result.append(denotable(t.getTypeArguments().get(i), allowNamedLocal));
                    }
                    result.append('>');
                }
                return result.toString();
            }
            @Override public String visitTypeVariable(TypeVariable t, Void unused) {
                String name = t.asElement().getSimpleName().toString();
                boolean named = allowNamedLocal
                        ? t.asElement().getKind() == ElementKind.TYPE_PARAMETER
                            && javax.lang.model.SourceVersion.isIdentifier(name)
                            && !javax.lang.model.SourceVersion.isKeyword(name)
                        : name.matches("[A-Za-z_$][A-Za-z0-9_$]*");
                if (!named || name.startsWith("capture")) {
                    throw new IllegalArgumentException("Captured variable: " + t);
                }
                return name;
            }
            @Override public String visitWildcard(WildcardType t, Void unused) {
                if (t.getExtendsBound() != null) return "? extends " + denotable(t.getExtendsBound(), allowNamedLocal);
                if (t.getSuperBound() != null) return "? super " + denotable(t.getSuperBound(), allowNamedLocal);
                return "?";
            }
        }, null);
    }
}
