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

This mode bypasses OpenAllay JavaScript execution and result budgets.
Select roots only for detached data you need; Java class lookup does not require
a special root. Return explicit JSON-friendly summaries, not raw Java objects.
Use `references/java-jvm.md` for Java collections, arrays, files, and JVM usage.

Side effects are irreversible: cancellation, a later error, or closing the
request does not roll back files, processes, network calls, or game changes.
Cancellation and request lifetime cleanup still apply; blocking Java calls may
not stop promptly.

Scripts run on a worker, not the Minecraft render/server thread. A live object
is not thread-safe just because it is reachable. Use the owning thread's supported
scheduler for game operations; do not block that thread or mutate live game state
from the script worker. Class names, mapped members, and mod APIs depend on this
installation; verify them rather than inventing them.

Java observations are not automatically detached game evidence.
Distinguish observed results from
attempted side effects, and verify changes before claiming success.
