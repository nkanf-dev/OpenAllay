# Java/JVM usage in authorized requests

These are Rhino Java interop examples, not Node.js or browser APIs. `Java.type`
is OpenAllay's explicit bridge to a class visible to the JVM application loader.
A missing class produces `javascript_class_unavailable`; it is not proof that a
mod's whole capability is missing. No generic `Java.from` helper is promised.

## Static and instance methods

```javascript
var System = Java.type("java.lang.System");
var StringJoiner = Java.type("java.util.StringJoiner");
var builder = new StringJoiner(" ");
builder.add("Java");
builder.add(System.getProperty("java.version"));
return String(builder.toString());
```

## Collections and arrays

Copy Java values into explicit JavaScript values. Do not assume a Java List has
JavaScript `map`, or that returning an arbitrary Java object yields useful JSON.

```javascript
var ArrayList = Java.type("java.util.ArrayList");
var list = new ArrayList();
list.add("stone");
list.add("dirt");
var array = list.toArray();
var copy = [];
for (var i = 0; i < array.length; i++) copy.push(String(array[i]));
return {size: Number(list.size()), values: copy};
```

## Paths and files

Use paths needed for the player's task and return only relevant content.
Relative paths are relative to the JVM working directory, not the managed Skill store. Java file
APIs are available independently of Skill management's path restrictions.

```javascript
var File = Java.type("java.io.File");
var file = new File("config");
return {name: String(file.getName()), exists: Boolean(file.exists()),
        directory: Boolean(file.isDirectory())};
```

For requested file reads/writes, use available `java.nio.file` or `java.io` APIs
and close streams in `finally`. Java overload
resolution is Rhino-specific; supply the required Java argument types and inspect
the installed API when a call is ambiguous.

## Network, processes, reflection, and Minecraft

JVM classes such as `java.net.URI`, `java.lang.ProcessBuilder`, and
`java.lang.Class` can support these operations. They are real capabilities, not
a sandbox simulation.
Bound external waits, close resources, and do not print secrets.
Avoid spawning background work that outlives the request. Do not assume rollback
or that cancellation can interrupt every Java call.

Minecraft and mod classes must actually exist with the runtime's mapped names.
Discover available classes/members using supported mod APIs or reflection when
needed. Do not guess a universal `Minecraft.getInstance()` spelling across
loaders/mappings. Schedule live game reads/mutations on their owning thread and
report only outcomes you observed. Detached `mc` results keep their ordinary
evidence semantics; direct Java observations do not acquire evidence handles.
