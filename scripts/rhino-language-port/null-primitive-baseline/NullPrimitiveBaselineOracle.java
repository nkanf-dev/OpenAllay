package dev.latvian.mods.rhino;
import dev.latvian.mods.rhino.type.TypeInfo;
public final class NullPrimitiveBaselineOracle {
    public static void main(String[] args) {
        Context cx = new ContextFactory().enter();
        Object actual = cx.jsToJava(null, TypeInfo.PRIMITIVE_INT);
        if (actual != null) throw new AssertionError("Public null changed: " + actual);
        boolean internalRejected = false;
        try { cx.internalJsToJava(null, TypeInfo.PRIMITIVE_INT); }
        catch (EvaluatorException expected) { internalRejected = true; }
        if (!internalRejected) throw new AssertionError("Internal null primitive was not rejected");
        if (cx.canConvert(null, TypeInfo.PRIMITIVE_INT)) throw new AssertionError("Null primitive conversion weight changed");
        System.out.println("BASELINE_NULL_PRIMITIVE=public:null|internal:EvaluatorException|canConvert:false");
    }
}
