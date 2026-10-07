package dev.latvian.mods.rhino;
import dev.latvian.mods.rhino.ast.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class ScalarSwitchTest {
    @Test void tokenMappingsAndUniqueTagsKeepExactDebugText() {
        assertEquals("ERROR", Token.typeToName(Token.ERROR)); assertEquals("ASSIGN_POW", Token.typeToName(Token.ASSIGN_POW));
        assertEquals("DELETE", Token.typeToName(Token.DELPROP));
        assertEquals("ERROR", Token.typeToName(Token.ERROR));
        assertEquals("EOF", Token.typeToName(Token.EOF));
        assertEquals("EOL", Token.typeToName(Token.EOL));
        assertEquals("ENTERWITH", Token.typeToName(Token.ENTERWITH));
        assertEquals("LEAVEWITH", Token.typeToName(Token.LEAVEWITH));
        assertEquals("RETURN", Token.typeToName(Token.RETURN));
        assertEquals("GOTO", Token.typeToName(Token.GOTO));
        assertEquals("IFEQ", Token.typeToName(Token.IFEQ));
        assertEquals("IFNE", Token.typeToName(Token.IFNE));
        assertEquals("SETNAME", Token.typeToName(Token.SETNAME));
        assertEquals("BITOR", Token.typeToName(Token.BITOR));
        assertEquals("BITXOR", Token.typeToName(Token.BITXOR));
        assertEquals("BITAND", Token.typeToName(Token.BITAND));
        assertEquals("EQ", Token.typeToName(Token.EQ));
        assertEquals("NE", Token.typeToName(Token.NE));
        assertEquals("LT", Token.typeToName(Token.LT));
        assertEquals("LE", Token.typeToName(Token.LE));
        assertEquals("GT", Token.typeToName(Token.GT));
        assertEquals("GE", Token.typeToName(Token.GE));
        assertEquals("LSH", Token.typeToName(Token.LSH));
        assertEquals("RSH", Token.typeToName(Token.RSH));
        assertEquals("URSH", Token.typeToName(Token.URSH));
        assertEquals("ADD", Token.typeToName(Token.ADD));
        assertEquals("SUB", Token.typeToName(Token.SUB));
        assertEquals("MUL", Token.typeToName(Token.MUL));
        assertEquals("DIV", Token.typeToName(Token.DIV));
        assertEquals("MOD", Token.typeToName(Token.MOD));
        assertEquals("NOT", Token.typeToName(Token.NOT));
        assertEquals("BITNOT", Token.typeToName(Token.BITNOT));
        assertEquals("POS", Token.typeToName(Token.POS));
        assertEquals("NEG", Token.typeToName(Token.NEG));
        assertEquals("NEW", Token.typeToName(Token.NEW));
        assertEquals("DELETE", Token.typeToName(Token.DELPROP));
        assertEquals("TYPEOF", Token.typeToName(Token.TYPEOF));
        assertEquals("GETPROP", Token.typeToName(Token.GETPROP));
        assertEquals("GETPROPNOWARN", Token.typeToName(Token.GETPROPNOWARN));
        assertEquals("SETPROP", Token.typeToName(Token.SETPROP));
        assertEquals("GETELEM", Token.typeToName(Token.GETELEM));
        assertEquals("SETELEM", Token.typeToName(Token.SETELEM));
        assertEquals("CALL", Token.typeToName(Token.CALL));
        assertEquals("NAME", Token.typeToName(Token.NAME));
        assertEquals("NUMBER", Token.typeToName(Token.NUMBER));
        assertEquals("STRING", Token.typeToName(Token.STRING));
        assertEquals("NULL", Token.typeToName(Token.NULL));
        assertEquals("THIS", Token.typeToName(Token.THIS));
        assertEquals("FALSE", Token.typeToName(Token.FALSE));
        assertEquals("TRUE", Token.typeToName(Token.TRUE));
        assertEquals("SHEQ", Token.typeToName(Token.SHEQ));
        assertEquals("SHNE", Token.typeToName(Token.SHNE));
        assertEquals("REGEXP", Token.typeToName(Token.REGEXP));
        assertEquals("BINDNAME", Token.typeToName(Token.BINDNAME));
        assertEquals("THROW", Token.typeToName(Token.THROW));
        assertEquals("RETHROW", Token.typeToName(Token.RETHROW));
        assertEquals("IN", Token.typeToName(Token.IN));
        assertEquals("INSTANCEOF", Token.typeToName(Token.INSTANCEOF));
        assertEquals("LOCAL_LOAD", Token.typeToName(Token.LOCAL_LOAD));
        assertEquals("GETVAR", Token.typeToName(Token.GETVAR));
        assertEquals("SETVAR", Token.typeToName(Token.SETVAR));
        assertEquals("CATCH_SCOPE", Token.typeToName(Token.CATCH_SCOPE));
        assertEquals("ENUM_INIT_KEYS", Token.typeToName(Token.ENUM_INIT_KEYS));
        assertEquals("ENUM_INIT_VALUES", Token.typeToName(Token.ENUM_INIT_VALUES));
        assertEquals("ENUM_INIT_ARRAY", Token.typeToName(Token.ENUM_INIT_ARRAY));
        assertEquals("ENUM_INIT_VALUES_IN_ORDER", Token.typeToName(Token.ENUM_INIT_VALUES_IN_ORDER));
        assertEquals("ENUM_NEXT", Token.typeToName(Token.ENUM_NEXT));
        assertEquals("ENUM_ID", Token.typeToName(Token.ENUM_ID));
        assertEquals("THISFN", Token.typeToName(Token.THISFN));
        assertEquals("RETURN_RESULT", Token.typeToName(Token.RETURN_RESULT));
        assertEquals("ARRAYLIT", Token.typeToName(Token.ARRAYLIT));
        assertEquals("OBJECTLIT", Token.typeToName(Token.OBJECTLIT));
        assertEquals("GET_REF", Token.typeToName(Token.GET_REF));
        assertEquals("SET_REF", Token.typeToName(Token.SET_REF));
        assertEquals("DEL_REF", Token.typeToName(Token.DEL_REF));
        assertEquals("REF_CALL", Token.typeToName(Token.REF_CALL));
        assertEquals("REF_SPECIAL", Token.typeToName(Token.REF_SPECIAL));
        assertEquals("TRY", Token.typeToName(Token.TRY));
        assertEquals("SEMI", Token.typeToName(Token.SEMI));
        assertEquals("LB", Token.typeToName(Token.LB));
        assertEquals("RB", Token.typeToName(Token.RB));
        assertEquals("LC", Token.typeToName(Token.LC));
        assertEquals("RC", Token.typeToName(Token.RC));
        assertEquals("LP", Token.typeToName(Token.LP));
        assertEquals("RP", Token.typeToName(Token.RP));
        assertEquals("COMMA", Token.typeToName(Token.COMMA));
        assertEquals("ASSIGN", Token.typeToName(Token.ASSIGN));
        assertEquals("ASSIGN_BITOR", Token.typeToName(Token.ASSIGN_BITOR));
        assertEquals("ASSIGN_BITXOR", Token.typeToName(Token.ASSIGN_BITXOR));
        assertEquals("ASSIGN_BITAND", Token.typeToName(Token.ASSIGN_BITAND));
        assertEquals("ASSIGN_LSH", Token.typeToName(Token.ASSIGN_LSH));
        assertEquals("ASSIGN_RSH", Token.typeToName(Token.ASSIGN_RSH));
        assertEquals("ASSIGN_URSH", Token.typeToName(Token.ASSIGN_URSH));
        assertEquals("ASSIGN_ADD", Token.typeToName(Token.ASSIGN_ADD));
        assertEquals("ASSIGN_SUB", Token.typeToName(Token.ASSIGN_SUB));
        assertEquals("ASSIGN_MUL", Token.typeToName(Token.ASSIGN_MUL));
        assertEquals("ASSIGN_DIV", Token.typeToName(Token.ASSIGN_DIV));
        assertEquals("ASSIGN_MOD", Token.typeToName(Token.ASSIGN_MOD));
        assertEquals("ASSIGN_NULLISH", Token.typeToName(Token.ASSIGN_NULLISH));
        assertEquals("HOOK", Token.typeToName(Token.HOOK));
        assertEquals("COLON", Token.typeToName(Token.COLON));
        assertEquals("OR", Token.typeToName(Token.OR));
        assertEquals("AND", Token.typeToName(Token.AND));
        assertEquals("INC", Token.typeToName(Token.INC));
        assertEquals("DEC", Token.typeToName(Token.DEC));
        assertEquals("DOT", Token.typeToName(Token.DOT));
        assertEquals("FUNCTION", Token.typeToName(Token.FUNCTION));
        assertEquals("EXPORT", Token.typeToName(Token.EXPORT));
        assertEquals("IMPORT", Token.typeToName(Token.IMPORT));
        assertEquals("IF", Token.typeToName(Token.IF));
        assertEquals("ELSE", Token.typeToName(Token.ELSE));
        assertEquals("SWITCH", Token.typeToName(Token.SWITCH));
        assertEquals("CASE", Token.typeToName(Token.CASE));
        assertEquals("DEFAULT", Token.typeToName(Token.DEFAULT));
        assertEquals("WHILE", Token.typeToName(Token.WHILE));
        assertEquals("DO", Token.typeToName(Token.DO));
        assertEquals("FOR", Token.typeToName(Token.FOR));
        assertEquals("BREAK", Token.typeToName(Token.BREAK));
        assertEquals("CONTINUE", Token.typeToName(Token.CONTINUE));
        assertEquals("VAR", Token.typeToName(Token.VAR));
        assertEquals("WITH", Token.typeToName(Token.WITH));
        assertEquals("CATCH", Token.typeToName(Token.CATCH));
        assertEquals("FINALLY", Token.typeToName(Token.FINALLY));
        assertEquals("VOID", Token.typeToName(Token.VOID));
        assertEquals("RESERVED", Token.typeToName(Token.RESERVED));
        assertEquals("EMPTY", Token.typeToName(Token.EMPTY));
        assertEquals("COMPUTED_PROPERTY", Token.typeToName(Token.COMPUTED_PROPERTY));
        assertEquals("BLOCK", Token.typeToName(Token.BLOCK));
        assertEquals("LABEL", Token.typeToName(Token.LABEL));
        assertEquals("TARGET", Token.typeToName(Token.TARGET));
        assertEquals("LOOP", Token.typeToName(Token.LOOP));
        assertEquals("EXPR_VOID", Token.typeToName(Token.EXPR_VOID));
        assertEquals("EXPR_RESULT", Token.typeToName(Token.EXPR_RESULT));
        assertEquals("JSR", Token.typeToName(Token.JSR));
        assertEquals("SCRIPT", Token.typeToName(Token.SCRIPT));
        assertEquals("TYPEOFNAME", Token.typeToName(Token.TYPEOFNAME));
        assertEquals("USE_STACK", Token.typeToName(Token.USE_STACK));
        assertEquals("SETPROP_OP", Token.typeToName(Token.SETPROP_OP));
        assertEquals("SETELEM_OP", Token.typeToName(Token.SETELEM_OP));
        assertEquals("LOCAL_BLOCK", Token.typeToName(Token.LOCAL_BLOCK));
        assertEquals("SET_REF_OP", Token.typeToName(Token.SET_REF_OP));
        assertEquals("TO_OBJECT", Token.typeToName(Token.TO_OBJECT));
        assertEquals("TO_DOUBLE", Token.typeToName(Token.TO_DOUBLE));
        assertEquals("GET", Token.typeToName(Token.GET));
        assertEquals("SET", Token.typeToName(Token.SET));
        assertEquals("LET", Token.typeToName(Token.LET));
        assertEquals("YIELD", Token.typeToName(Token.YIELD));
        assertEquals("CONST", Token.typeToName(Token.CONST));
        assertEquals("SETCONST", Token.typeToName(Token.SETCONST));
        assertEquals("ARRAYCOMP", Token.typeToName(Token.ARRAYCOMP));
        assertEquals("WITHEXPR", Token.typeToName(Token.WITHEXPR));
        assertEquals("LETEXPR", Token.typeToName(Token.LETEXPR));
        assertEquals("COMMENT", Token.typeToName(Token.COMMENT));
        assertEquals("GENEXPR", Token.typeToName(Token.GENEXPR));
        assertEquals("METHOD", Token.typeToName(Token.METHOD));
        assertEquals("ARROW", Token.typeToName(Token.ARROW));
        assertEquals("YIELD_STAR", Token.typeToName(Token.YIELD_STAR));
        assertEquals("TEMPLATE_LITERAL", Token.typeToName(Token.TEMPLATE_LITERAL));
        assertEquals("TEMPLATE_CHARS", Token.typeToName(Token.TEMPLATE_CHARS));
        assertEquals("TEMPLATE_LITERAL_SUBST", Token.typeToName(Token.TEMPLATE_LITERAL_SUBST));
        assertEquals("TAGGED_TEMPLATE_LITERAL", Token.typeToName(Token.TAGGED_TEMPLATE_LITERAL));
        assertEquals("NULLISH_COALESCING", Token.typeToName(Token.NULLISH_COALESCING));
        assertEquals("POW", Token.typeToName(Token.POW));
        assertEquals("QUESTION_DOT", Token.typeToName(Token.QUESTION_DOT));
        assertEquals("DOTDOTDOT", Token.typeToName(Token.DOTDOTDOT));
        assertEquals("ASSIGN_POW", Token.typeToName(Token.ASSIGN_POW));
        assertThrows(IllegalStateException.class, () -> Token.typeToName(99999));
        assertTrue(UniqueTag.NOT_FOUND.toString().endsWith(": NOT_FOUND"));
        assertTrue(UniqueTag.NULL_VALUE.toString().endsWith(": NULL_VALUE"));
        assertTrue(UniqueTag.DOUBLE_MARK.toString().endsWith(": DOUBLE_MARK"));
    }
    @Test void objectArrayFieldAndBackingStoreBranchesPreserveStackAndMutation() {
        ObjArray values = new ObjArray();
        for (int i = 0; i < 12; i++) values.push(i);
        for (int i = 0; i < 12; i++) { assertEquals(i, values.get(i)); values.set(i, i + 20); }
        for (int i = 11; i >= 0; i--) { assertEquals(i + 20, values.peek()); assertEquals(i + 20, values.pop()); }
        assertEquals(0, values.size()); assertThrows(RuntimeException.class, values::pop);
        values.push("x"); values.seal(); assertThrows(RuntimeException.class, values::pop);
        assertThrows(RuntimeException.class, () -> values.set(0, "changed")); assertEquals("x", values.peek());
    }
    @Test void collectionIteratorPreservesKeyValuePairAndOneNext() {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        for (NativeCollectionIterator.Type type : NativeCollectionIterator.Type.values()) {
            Hashtable table = new Hashtable(cx); table.put(cx, "key", "value");
            NativeCollectionIterator iterator = new NativeCollectionIterator(scope, "Map", type, table.iterator(), cx);
            assertFalse(iterator.isDone(cx, scope)); Object value = iterator.nextValue(cx, scope);
            if (type == NativeCollectionIterator.Type.KEYS) assertEquals("key", value);
            if (type == NativeCollectionIterator.Type.VALUES) assertEquals("value", value);
            if (type == NativeCollectionIterator.Type.BOTH) {
                NativeArray pair = (NativeArray) value; assertEquals("key", pair.get(cx, 0, pair)); assertEquals("value", pair.get(cx, 1, pair));
            }
            assertTrue(iterator.isDone(cx, scope)); assertThrows(NoSuchElementException.class, () -> iterator.nextValue(cx, scope));
        }
    }
    static final class CountingNode extends Name {
        final boolean effect; int calls;
        CountingNode(boolean effect) { this.effect = effect; }
        @Override public boolean hasSideEffects() { calls++; return effect; }
    }
    @Test void infixSideEffectsKeepCommaAndBooleanShortCircuit() {
        for (int operator : new int[]{Token.AND, Token.OR, Token.NULLISH_COALESCING}) {
            CountingNode left = new CountingNode(true), right = new CountingNode(true);
            InfixExpression expression = new InfixExpression(operator, left, right, 0);
            assertTrue(expression.hasSideEffects()); assertEquals(1, left.calls); assertEquals(0, right.calls);
        }
        CountingNode left = new CountingNode(true), right = new CountingNode(false);
        InfixExpression comma = new InfixExpression(Token.COMMA, left, right, 0);
        assertFalse(comma.hasSideEffects()); assertEquals(0, left.calls); assertEquals(1, right.calls);
    }
}
