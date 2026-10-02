# Unrestricted Java access

The client-local unrestricted setting adds native access methods to the existing
Rhino `Java` object. It does not require a second private-access setting.
Normal scripts and scoped Extension callbacks do not gain this surface.
The system prompt stays capability-neutral; the bundled
[unrestricted JavaScript Skill](../../common/src/main/resources/assets/openallay/openallay_skills/unrestricted-javascript/SKILL.md)
and its [Java reference](../../common/src/main/resources/assets/openallay/openallay_skills/unrestricted-javascript/references/java-jvm.md)
carry the usage contract.

## Native entrypoints

| Entry point | Contract |
| --- | --- |
| `Java.type(name)` | Keeps the existing application-loader class lookup and public Rhino wrapper. |
| `Java.classOf(value)` | Unwraps the value and returns its actual class wrapper. Class targets keep their represented class. |
| `Java.inspect(target)` | Returns detached metadata; no field values or reflection objects. |
| `Java.get(target, fieldSelector)` | Reads the selected field. |
| `Java.set(target, fieldSelector, value)` | Writes the selected field; returns `undefined`. |
| `Java.invoke(target, methodSelector, parameterTypes, arguments)` | Invokes the exact signature; `void` returns `undefined`. |
| `Java.construct(type, parameterTypes, arguments)` | Invokes the exact declared constructor. |

A class wrapper targets static members. An instance targets its bound object.
`construct` accepts a class wrapper or a class-name string. Other target strings
are string instances, not class lookup requests. `classOf` avoids hidden guest
`getClass()` members, the unsupported `.class` property, and caller-sensitive
guest `Class.forName` calls.

Selectors are names or detached descriptors from `inspect`. Names choose the
nearest matching declaration. A descriptor supplies `name` and `declaringClass`
to select a shadowed field or an exact method owner. A method descriptor can
also supply `parameterTypes` and `returnType` to distinguish bridge methods;
the `invoke` parameter-type argument remains required.

Each parameter type accepts a class wrapper or a name: a primitive such as
`int`, a binary class name such as `java.lang.String`, an array name such as
`int[]` or `java.lang.Object[]`, or a JVM array descriptor such as `[I`.
Type names resolve through the actual target loader; an explicit descriptor uses
the selected declaration's loader for its parameter and return types. Invocation
arguments use the resolved parameter classes and Rhino's Java conversion.
Signatures are exact, not another overload resolver.
Varargs require their declared array type and one array argument in that slot;
see the runnable `String.format` example in the Java reference.

## Runnable discovery and invocation

This example constructs a JDK object, discovers its class without a guest
reflection call, selects one exact method descriptor, and returns a small result.
It runs through `openallay:run_javascript` in an unrestricted request.

```javascript
var builder = Java.construct(Java.type("java.lang.StringBuilder"), ["int"], [16]);
var info = Java.inspect(Java.classOf(builder));
var append = info.methods.filter(function (method) {
  return method.declaringClass === "java.lang.StringBuilder" &&
    method.name === "append" && method.returnType === "java.lang.StringBuilder" &&
    method.parameterTypes.length === 1 && method.parameterTypes[0] === "java.lang.String";
})[0];
Java.invoke(builder, append, ["java.lang.String"], ["Java access"]);
return {
  name: info.name,
  text: String(Java.invoke(builder, "toString", [], []))
};
```

The expected result is `{"name":"java.lang.StringBuilder","text":"Java access"}`.
For a non-public member, use the same descriptor selection on the actual
runtime target. Access depends on that member and its module; the example does
not assume that private JDK internals are open.

## Metadata and result ownership

`inspect` includes declared fields and methods through the target hierarchy,
interface methods, and only the target class's constructors. Each descriptor
names its declaring class, types, modifiers, and relevant static/final/varargs/
bridge/synthetic flags. Class metadata includes `name`, `typeName`, `superclass`,
`interfaces`, `primitive`, `array`, `componentType`, `modifiers`, and `modifierBits`.

`module` contains `name`, `named`, `automatic`, `packageName`, and the actual
`packageOpenToBridge` value. `classLoader` is bootstrap `null` or detached
`{name,type,identity}` metadata. Loader identity is diagnostic text, not an
object handle. Fabric and NeoForge can place classes in different loaders and
modules; use observed metadata rather than assuming an unnamed module.

Filter metadata in JavaScript before returning it. Live Java values stay in the
current script; return explicit JSON-friendly data. The existing workspace
result storage and model-context projection remain unchanged.

## Reflection and bytecode hooks

Access uses standard reflection and `trySetAccessible`. A named module's closed
package can produce `javascript_java_inaccessible`; the error identifies the
actual member, module, and package. Metadata discovery does not open a package.
For a development launch that needs such access, configure
`--add-opens=<target-module>/<package>=<bridge-module>` before JVM startup, using
the actual bridge module or `ALL-UNNAMED` when the bridge is unnamed. Final-field
rules and target exceptions still apply.

Reflection reads, writes, constructs, and invokes existing members. Startup
Mixin hooks or loader access widening address bytecode/access changes; they are
not dynamic reflection calls. This facade does not transform classes or install
a dynamic Mixin. Choose a startup hook when the task needs changed method
execution, not merely member access.

All calls run on the calling script worker; live game operations remain on their
actual owning thread, and this facade does not reroute them.

## Failure contract and verification

Native failures retain a short diagnostic cause and use
`javascript_java_invalid`, `javascript_class_unavailable`,
`javascript_java_member_unavailable`, `javascript_java_inaccessible`,
`javascript_java_conversion_error`, or `javascript_java_target_error`.
Cancellation remains cancellation rather than a target failure.

Native tests exercise synthetic non-public members, exact primitive/array/
varargs signatures, hierarchy and descriptor selection, module denial, target
exceptions, cancellation, and unrestricted/safe execution isolation. Bundled
JavaScript examples also run in the actual Rhino runtime. Test fixtures are
not application classes and are not needed by the JDK examples above.
