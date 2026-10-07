package dev.latvian.mods.rhino;
import dev.latvian.mods.rhino.ast.Name;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class RegexpErrorAstSwitchTest {
    @Test void regexpLegacyAliasesRetainNamesValuesAndMutableAttributes() {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        Object result = cx.evaluateString(scope,
            "/(a)(b)/.exec('zabq');[RegExp.lastMatch,RegExp['$&'],RegExp.lastParen,RegExp['$+'],RegExp.leftContext,RegExp['$`'],RegExp.rightContext,RegExp[\"$'\"],RegExp.$1,RegExp.$2].join('|');",
            "regex.js", 1, null);
        assertEquals("ab|ab|b|b|z|z|q|q|a|b", ScriptRuntime.toString(cx, result));
        Object writable = cx.evaluateString(scope,
            "RegExp.input='input';RegExp.multiline=true;[RegExp.input,RegExp.$_,RegExp.multiline,RegExp['$*']].join('|');",
            "regex.js", 1, null);
        assertEquals("input|input|true|true", ScriptRuntime.toString(cx, writable));
    }
    @Test void errorPrototypeNamesArityAndStringFormattingStayExact() {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        Object result = cx.evaluateString(scope,
            "[Error.prototype.constructor.length,Error.prototype.toString.length,Error.prototype.toSource.length,new TypeError('message').toString(),new Error().toString()].join('|');",
            "error.js", 1, null);
        assertEquals("1|0|0|TypeError: message|Error", ScriptRuntime.toString(cx, result));
    }
    @Test void baseAstAllOriginalSideEffectLabelsAndDefaultKeepMapping() {
        Set<Integer> effectful = new HashSet<>(Arrays.asList(Token.ASSIGN, Token.ASSIGN_ADD, Token.ASSIGN_BITAND, Token.ASSIGN_BITOR, Token.ASSIGN_BITXOR, Token.ASSIGN_DIV, Token.ASSIGN_LSH, Token.ASSIGN_MOD, Token.ASSIGN_MUL, Token.ASSIGN_RSH, Token.ASSIGN_SUB, Token.ASSIGN_URSH, Token.ASSIGN_NULLISH, Token.ASSIGN_POW, Token.BLOCK, Token.BREAK, Token.CALL, Token.CATCH, Token.CATCH_SCOPE, Token.CONST, Token.CONTINUE, Token.DEC, Token.DELPROP, Token.DEL_REF, Token.DO, Token.ELSE, Token.ENTERWITH, Token.ERROR, Token.EXPORT, Token.EXPR_RESULT, Token.FINALLY, Token.FUNCTION, Token.FOR, Token.GOTO, Token.IF, Token.IFEQ, Token.IFNE, Token.IMPORT, Token.INC, Token.JSR, Token.LABEL, Token.LEAVEWITH, Token.LET, Token.LETEXPR, Token.LOCAL_BLOCK, Token.LOOP, Token.NEW, Token.REF_CALL, Token.RETHROW, Token.RETURN, Token.RETURN_RESULT, Token.SEMI, Token.SETELEM, Token.SETELEM_OP, Token.SETNAME, Token.SETPROP, Token.SETPROP_OP, Token.SETVAR, Token.SET_REF, Token.SET_REF_OP, Token.SWITCH, Token.TARGET, Token.THROW, Token.TRY, Token.VAR, Token.WHILE, Token.WITH, Token.WITHEXPR, Token.YIELD, Token.YIELD_STAR));
        for (int token = Token.ERROR; token <= Token.LAST_TOKEN; token++) {
            dev.latvian.mods.rhino.ast.AstNode base = new dev.latvian.mods.rhino.ast.AstNode() {
                public String toSource(int depth) { return ""; }
                public void visit(dev.latvian.mods.rhino.ast.NodeVisitor visitor) { visitor.visit(this); }
            };
            base.setType(token); assertEquals(effectful.contains(token), base.hasSideEffects(), "token=" + token);
        }
    }
    @Test void nodeReturnUsageRetainsBlockAndChildDispatch() {
        Node block = new Node(Token.BLOCK); assertTrue(block.hasConsistentReturnUsage());
        block.addChildToBack(new Node(Token.RETURN)); assertTrue(block.hasConsistentReturnUsage());
        Node valued = new Node(Token.BLOCK, new Node(Token.RETURN, new Node(Token.NUMBER)));
        assertTrue(valued.hasConsistentReturnUsage());
        Node direct = new Node(Token.RETURN, new Node(Token.NUMBER)); assertTrue(direct.hasConsistentReturnUsage());
    }
}
