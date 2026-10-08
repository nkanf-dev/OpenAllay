package dev.latvian.mods.rhino;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class ObjectSlotBindingLanguageTest {
    static String evaluate(String source) {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        return ScriptRuntime.toString(cx, cx.evaluateString(scope, source, "object.js", 1, null));
    }
    @Test void assignStringIndexAndSymbolOrderKeepGetterCountsAndValues() {
        assertEquals("index,x,symbol|3|1|2|3", evaluate(
            "var calls=[],sym=Symbol('s'),source={};Object.defineProperty(source,'0',{enumerable:true,get:function(){calls.push('index');return 1;}});Object.defineProperty(source,'x',{enumerable:true,get:function(){calls.push('x');return 2;}});Object.defineProperty(source,sym,{enumerable:true,get:function(){calls.push('symbol');return 3;}});var o=Object.assign({},source);[calls.join(','),calls.length,o[0],o.x,o[sym]].join('|');"));
    }
    @Test void legacyGetterSetterAndDescriptorFreezeKeepCallbacksAndGuardErrors() {
        assertEquals("7|2|function|true|true", evaluate(
            "var calls=0,value=0,o={};o.__defineGetter__('x',function(){calls++;return value;});o.__defineSetter__('x',function(v){calls++;value=v;});o.x=7;var got=o.x;var kind=typeof o.__lookupGetter__('x');var bad=false;try{o.__defineGetter__('y',1);}catch(e){bad=e instanceof TypeError;}Object.freeze(o);[got,calls,kind,bad,Object.isFrozen(o)].join('|');"));
    }
    @Test void fromEntriesSymbolAndDescriptorMapsKeepAllKeyKinds() {
        assertEquals("1|2|3|true|false", evaluate(
            "var sym=Symbol('s'),o=Object.fromEntries([['x',1],[0,2],[sym,3]]);Object.defineProperty(o,'hidden',{value:4,enumerable:false});var d=Object.getOwnPropertyDescriptors(o);[o.x,o[0],o[sym],d.hidden.value===4,o.propertyIsEnumerable('hidden')].join('|');"));
    }
    @Test void prototypeAndPrimitiveConversionPropertyCallbacksStaySingle() {
        assertEquals("true|true|custom|1", evaluate(
            "var parent={},child=Object.create(parent),calls=0,o={toString:function(){calls++;return 'custom';}};[parent.isPrototypeOf(child),Object.getPrototypeOf(Object.setPrototypeOf(child,parent))===parent,String(o),calls].join('|');"));
    }
}
