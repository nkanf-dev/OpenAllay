---
name: run-game-commands
description: Use when a player explicitly asks to discover or execute a Minecraft command through the enabled experimental command capability.
metadata:
  openallay/version: "0.2.2"
allowed-tools: "openallay:run_javascript"
---
Use this Skill when the player explicitly asks OpenAllay to execute a Minecraft
command, or when the task must discover the exact syntax of an installed
mod's command before executing it.

The `commands` object exists only because the player enabled the experimental
command capability for this request:

- `commands.list()` returns the complete Brigadier tree visible to the current
  player. It contains vanilla, server, loader, and mod-registered nodes.
- `commands.describe(path)` returns one exact literal/argument path from that
  detached tree.
- `commands.run(command)` submits the exact command as the requesting player,
  waits off the render thread for the associated client-visible feedback window,
  and returns the messages that Minecraft produced. Input whitespace is stripped
  and a single optional leading `/` is removed before submission.

Select the `commands` root for `run_javascript`; the binding is `commands`, not
`mc.commands`.

The canonical execution form is:

```javascript
var command = "time set day";
return commands.run(command);
```

`commands.run(...)` is synchronous from JavaScript's point of view. Its returned
object contains `state`, `messages`, and timing. Call its methods on `commands`;
it is not itself a function.

Use `references/commands.md` for detailed syntax or examples when they are
relevant. When exploring an unknown command, focus the returned command-tree
data on the player's request.

Use the returned `commands.run` result to report observable feedback accurately. `feedback` contains Minecraft messages; `no_feedback` means no message was observed during the feedback window, not that the command failed. Do not claim an outcome beyond what the feedback states. A resulting structure, inventory, or attribute is independently verified only when the feedback says so or a separate world/data observation observes it.

Minecraft remains authoritative for parsing and permissions. Submissions are
not transactional: if a later statement fails or the Agent is cancelled,
commands already submitted stay submitted and are never rolled back.

Treat parser and permission errors in `messages` as rejection, not success.
Use actual feedback and the described path to understand syntax errors. If the
requested player-visible path cannot be found, report that it is unavailable.
