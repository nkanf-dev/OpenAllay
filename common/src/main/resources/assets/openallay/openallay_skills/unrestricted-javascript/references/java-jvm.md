# Java/JVM usage in authorized requests

`Java.type` resolves classes visible to the JVM application loader through Rhino.
A missing class reports `javascript_class_unavailable`; other APIs may still be
available.

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

Copy Java collections and arrays into JavaScript values for JSON results.

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

Relative paths use the JVM working directory.

```javascript
var File = Java.type("java.io.File");
var file = new File("config");
return {name: String(file.getName()), exists: Boolean(file.exists()),
        directory: Boolean(file.isDirectory())};
```

Use `java.nio.file` or `java.io` APIs and close streams in `finally`.
Rhino overload resolution requires matching Java argument types; inspect the
installed API when a call is ambiguous.

## Network, processes, reflection, and Minecraft

Use available JVM APIs such as `java.net.URI`, `java.lang.ProcessBuilder`, and
`java.lang.Class`. Bound external waits and close resources.

Minecraft and mod class/member names depend on the installed mappings.
Inspect APIs or reflection as needed and schedule live game operations on their
owning thread. Direct Java observations do not acquire detached `mc` evidence
handles.
