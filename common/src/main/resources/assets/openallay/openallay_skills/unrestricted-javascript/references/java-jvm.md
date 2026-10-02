# Java/JVM access in unrestricted requests

The existing unrestricted setting exposes these native methods on `Java`.
`Java.type` remains compatible with public calls and `new`.

## Access contract

| Call | Result |
| --- | --- |
| `Java.type(name)` | A class wrapper resolved with the application loader. |
| `Java.classOf(value)` | The object's actual class wrapper; a class target keeps its represented class. |
| `Java.inspect(target)` | Detached class, module, loader, field, method, and constructor metadata. |
| `Java.get(target, fieldSelector)` | The field value. |
| `Java.set(target, fieldSelector, value)` | Writes the field; returns `undefined`. |
| `Java.invoke(target, methodSelector, parameterTypes, arguments)` | The exact method's result; `void` returns `undefined`. |
| `Java.construct(type, parameterTypes, arguments)` | An instance from the exact constructor. |

Use a class wrapper as the target for static members and an instance for bound
members. `construct` also accepts a class name. A string passed as a target to
`inspect`, `get`, or `invoke` is a string instance, not a class name.
Use `Java.classOf(value)` rather than `.class`, guest `getClass()`, or guest
`Class.forName` to discover a wrapped object's class.

A selector is a member name or its descriptor from `inspect`.
A name selects the nearest declaration in the class hierarchy.
A descriptor's `declaringClass` selects that exact declaration, including a
shadowed superclass field. A method descriptor can also select its `returnType`
to distinguish bridge methods with the same parameter types. `invoke` still
requires an explicit `parameterTypes` array.

## Inspect only the needed metadata

`inspect` includes declared members through the class hierarchy and interface
methods; constructors belong only to the target class. It does not read field
values or make members accessible. Metadata includes parameter and return types,
modifiers, and static, final, varargs, bridge, or synthetic flags where relevant.

Filter before returning, rather than returning every member or scanning the
whole classpath:

```javascript
var info = Java.inspect(Java.type("java.util.ArrayList"));
return {
  name: info.name,
  module: info.module,
  fields: info.fields.filter(function (field) {
    return field.name === "size" || field.name === "elementData";
  }),
  methods: info.methods.filter(function (method) {
    return method.declaringClass === "java.util.ArrayList" &&
      (method.name === "add" || method.name === "get" || method.name === "size");
  })
};
```

`module` contains `name`, `named`, `automatic`, `packageName`, and
`packageOpenToBridge`. `classLoader` is `null` for the bootstrap loader; otherwise
it contains `name`, `type`, and `identity`. These are metadata, not module or
loader objects. The actual loader/module depends on the installation.

## Read and write a selected field

This runnable example uses a public mutable JDK field. The same selector
contract applies to accessible non-public fields.

```javascript
var reader = Java.construct(Java.type("java.io.StringReader"),
  ["java.lang.String"], ["2"]);
var tokenizer = Java.construct(Java.type("java.io.StreamTokenizer"),
  ["java.io.Reader"], [reader]);
var info = Java.inspect(Java.classOf(tokenizer));
var field = info.fields.filter(function (member) {
  return member.name === "nval" && member.declaringClass === "java.io.StreamTokenizer";
})[0];
Java.set(tokenizer, field, 2);
return {name: info.name, value: Number(Java.get(tokenizer, field))};
```

## Exact types, arrays, and varargs

Each parameter type can be a class wrapper or a type-name string. Use primitive
names such as `int` and `double`, binary class names such as `java.lang.String`,
and array names such as `int[]` or `java.lang.Object[]`. JVM array descriptors
such as `[I` and `[Ljava.lang.Object;` also work. Type resolution uses the actual
target loader; a descriptor uses its selected declaration's loader. Argument
conversion uses Rhino's Java conversion.

There is no overload guessing or varargs expansion. Supply one argument per
parameter. A varargs parameter is its declared array type, with an array in
that argument slot:

```javascript
return String(Java.invoke(Java.type("java.lang.String"), "format",
  ["java.lang.String", "java.lang.Object[]"], ["%s %s", ["Java", "2"]]));
```

## Access results

Non-public access uses standard Java reflection. A member can appear in metadata
without being accessible. A closed named-module package reports
`javascript_java_inaccessible` with the declaring member and actual module/package.
Final-field writes and target exceptions follow JVM rules.

Other native failures use `javascript_java_invalid`, `javascript_class_unavailable`,
`javascript_java_member_unavailable`, `javascript_java_conversion_error`, or
`javascript_java_target_error`. Use the reported signature and cause to choose
the next operation. For development module-opening and bytecode-hook details,
see `docs/development/unrestricted-java-access.md` in the source repository.
