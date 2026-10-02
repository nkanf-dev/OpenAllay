---
name: unrestricted-javascript
description: Use when the current client-local request enables unrestricted JavaScript and the player needs Java/JVM APIs, files, network, processes, or live Minecraft objects.
metadata:
  openallay/version: "0.2.2"
allowed-tools: "openallay:run_javascript"
---
Use `Java.type("fully.qualified.ClassName")` for an available class.
Direct public calls and `new` still work. For exact overloads, constructors, or
non-public members, use the native access methods:

```javascript
var builder = Java.construct(Java.type("java.lang.StringBuilder"), ["int"], [16]);
Java.invoke(builder, "append", ["java.lang.String"], ["Java "]);
Java.invoke(builder, "append", ["int"], [2]);
return {
  text: String(Java.invoke(builder, "toString", [], [])),
  length: Number(Java.invoke(builder, "length", [], []))
};
```

Use `Java.classOf(value)` for the actual class, not `.class` or a guest
`getClass()` call. `Java.inspect(target)` returns detached member metadata;
select the needed members before returning it. `Java.get` and `Java.set`
access fields. `Java.invoke` and `Java.construct` take exact parameter types and
an argument array. See `references/java-jvm.md` for selectors, arrays, and examples.

The existing unrestricted setting enables this surface; no extra private-access
setting is needed. This native surface does not change the
model-facing result view. Return explicit JSON-friendly values rather than raw
Java objects.

Scripts run on a worker; live game operations must run on their actual
owning thread. This bridge does not schedule them. Class names and members depend on
the installed runtime. Cancellation does not roll back side effects or guarantee
that blocking Java calls stop promptly.
