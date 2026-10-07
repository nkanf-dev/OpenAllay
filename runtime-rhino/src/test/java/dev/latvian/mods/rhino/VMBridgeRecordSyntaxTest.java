package dev.latvian.mods.rhino;
import dev.latvian.mods.rhino.type.TypeInfo;
import java.lang.reflect.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
public final class VMBridgeRecordSyntaxTest {
    public interface Defaults {
        default String text() { return "default"; }
        default String failure() { throw new IllegalStateException("default-failure"); }
    }
    private record HandlerOracle(Context cx, InterfaceAdapter adapter, Object target, Scriptable topScope) {}
    @Test void invocationHandlerComponentContractAndObjectMethodsRemainExact() throws Throwable {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects(); Object target = new Object();
        VMBridge.AdapterInvocationHandler handler = new VMBridge.AdapterInvocationHandler(cx, null, target, scope);
        assertSame(cx, handler.cx()); assertNull(handler.adapter()); assertSame(target, handler.target()); assertSame(scope, handler.topScope());
        assertEquals(new HandlerOracle(cx, null, target, scope).hashCode(), handler.hashCode());
        assertEquals(handler, new VMBridge.AdapterInvocationHandler(cx, null, target, scope));
        Object proxyIdentity = new Object();
        assertEquals(Boolean.TRUE, handler.invoke(proxyIdentity, Object.class.getMethod("equals", Object.class), new Object[]{proxyIdentity}));
        assertEquals(target.hashCode(), handler.invoke(proxyIdentity, Object.class.getMethod("hashCode"), null));
        assertEquals("Proxy[" + target + "]", handler.invoke(proxyIdentity, Object.class.getMethod("toString"), null));
    }
    @Test void modernPublicForeignInterfaceDefaultsReturnAndThrowRemainSupported() {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        ScriptableObject guest = (ScriptableObject) cx.newObject(scope);
        Defaults defaults = (Defaults) Proxy.newProxyInstance(Defaults.class.getClassLoader(), new Class<?>[]{Defaults.class},
            new VMBridge.AdapterInvocationHandler(cx, null, guest, scope));
        assertEquals("default", defaults.text());
        assertEquals("default-failure", assertThrows(IllegalStateException.class, defaults::failure).getMessage());

    }
}
