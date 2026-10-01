---
name: unrestricted-javascript
description: Use when the current client-local request enables unrestricted JavaScript and the player needs Java/JVM APIs, files, network, processes, or live Minecraft objects.
metadata:
  openallay/version: "0.2.2"
allowed-tools: "openallay:run_javascript"
---
Use `Java.type("fully.qualified.ClassName")` to resolve an available Java class.
Call static methods on that class. Construct instances with `new`, then call
instance methods on the object. For example:

```javascript
var System = Java.type("java.lang.System");
var StringJoiner = Java.type("java.util.StringJoiner");
var text = new StringJoiner(" ");
text.add("Java");
text.add(System.getProperty("java.version"));
return String(text.toString());
```

This mode removes the default isolated execution limits; it does not change the
model-facing result view.
Return explicit JSON-friendly values rather than raw Java objects.
Use `references/java-jvm.md` when Java collection, array, file, or JVM details are needed.

Scripts run on a worker. Schedule live game operations on their owning thread.
Class names, mapped members, and mod APIs vary by installation; inspect available
classes and members as needed.

Side effects are not rolled back by cancellation or later errors.
Blocking Java calls may not stop promptly.
