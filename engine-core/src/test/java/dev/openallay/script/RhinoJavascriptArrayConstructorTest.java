package dev.openallay.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import dev.openallay.model.CancellationSignal;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class RhinoJavascriptArrayConstructorTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservesNativeConstructionCallsPrototypeIdentityAndStaticMethods(boolean unrestricted) {
        String source = """
                const a = Array(3);
                const b = new Array(3);
                const c = Array.of(1,2,3);
                const d = Array.call(null,2);
                const e = Array.apply(null,[2]);
                const Bound = Array.bind(null,2);
                const f = Bound();
                const g = new Bound();
                const values = [a,b,c,d,e,f,g];
                return {
                  values: [a.fill("x"),b.fill("x"),c,d.fill(1),e.fill(1),f.fill(1),g.fill(1)],
                  prototype: values.map(value => Object.getPrototypeOf(value) === Array.prototype),
                  instance: values.map(value => value instanceof Array),
                  arrays: values.map(value => Array.isArray(value)),
                  constructor: a.constructor === Array,
                  species: Array[Symbol.species] === Array,
                  name: Array.name, length: Array.length,
                  staticOwn: ["of","from","isArray"].map(key => Object.prototype.hasOwnProperty.call(Array,key)),
                  staticValues: Array.from([1,2,3]),
                  plain: Array(1,2,3), zero: Array(), single: Array("x"),
                  numbers: typeof Number(2), strings: typeof String(2),
                  nativeKeys: Object.keys(Array),
                  classAccess: typeof Array.getClass,
                  packages: typeof Packages,
                  java: typeof Java
                };
                """;
        var actual = new RhinoJavascriptRuntime().execute(source, Map.of(), Map.of(), Map.of(),
                new CancellationSignal(), null, null, unrestricted).value().getAsJsonObject();
        assertEquals(dev.openallay.json.JsonTrees.parse("""
                [["x","x","x"],["x","x","x"],[1,2,3],[1,1],[1,1],[1,1],[1,1]]
                """), actual.get("values"));
        for (String key : new String[]{"prototype","instance","arrays"}) {
            assertEquals(dev.openallay.json.JsonTrees.parse("[true,true,true,true,true,true,true]"), actual.get(key), key);
        }
        assertEquals(true, actual.get("constructor").getAsBoolean());
        assertEquals(true, actual.get("species").getAsBoolean());
        assertEquals("Array", actual.get("name").getAsString());
        assertEquals(1, actual.get("length").getAsInt());
        assertEquals(dev.openallay.json.JsonTrees.parse("[true,true,true]"), actual.get("staticOwn"));
        assertEquals(dev.openallay.json.JsonTrees.parse("[1,2,3]"), actual.get("staticValues"));
        assertEquals(dev.openallay.json.JsonTrees.parse("[1,2,3]"), actual.get("plain"));
        assertEquals(dev.openallay.json.JsonTrees.parse("[]"), actual.get("zero"));
        assertEquals(dev.openallay.json.JsonTrees.parse("[\"x\"]"), actual.get("single"));
        assertEquals("number", actual.get("numbers").getAsString());
        assertEquals("string", actual.get("strings").getAsString());
        assertEquals(dev.openallay.json.JsonTrees.parse("[]"), actual.get("nativeKeys"));
        assertEquals("undefined", actual.get("classAccess").getAsString());
        if (!unrestricted) {
            assertEquals("undefined", actual.get("packages").getAsString());
            assertEquals("undefined", actual.get("java").getAsString());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void leavesInvalidLengthValidationToTheNativeConstructor(boolean unrestricted) {
        String source = """
                const errors = [];
                try { Array(-1); } catch (error) { errors.push(error.name); }
                try { Array(1.5); } catch (error) { errors.push(error.name); }
                try { new Array(Infinity); } catch (error) { errors.push(error.name); }
                try { Array.call(null, NaN); } catch (error) { errors.push(error.name); }
                return errors;
                """;
        assertEquals(dev.openallay.json.JsonTrees.parse("[\"RangeError\",\"RangeError\",\"RangeError\",\"RangeError\"]"),
                new RhinoJavascriptRuntime().execute(source, Map.of(), Map.of(), Map.of(),
                        new CancellationSignal(), null, null, unrestricted).value());
    }
}
