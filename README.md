# OpenAllay

[简体中文](README.zh-CN.md)

**Your AI companion for exploring modpacks, finding answers, and building in Minecraft.**

Ask in plain language. OpenAllay uses the game data available in your instance
to work through the task and bring useful answers back into the game.

<img src="docs/media/openallay-banner.png" alt="OpenAllay — your AI companion in Minecraft. Explore, build, and create.">

[Download on GitHub](https://github.com/nkanf-dev/OpenAllay/releases) ·
[Quick start](#quick-start) · [0.3.0 release notes](docs/releases/0.3.0.md) ·
[Community](#community-and-development)

| | Make it part of your game |
| --- | --- |
| **[Explore](#explore-your-modpack)** | Find recipes, compare gear, check ingredients, and understand your modpack. |
| **[Build](#build-in-your-world)** | Shape terrain, create structures, and reuse templates in your single-player world. |
| **[Skills](#create-your-own-skills)** | Add workflows and knowledge for the way you play. |
| **[Extensions](#add-extensions)** | Connect more mods, game data, actions, and native result views. |

## Quick start

OpenAllay **0.3.0** targets **Minecraft 26.2**, **Java 25**, and **Fabric or
NeoForge**. Use [GitHub Releases](https://github.com/nkanf-dev/OpenAllay/releases)
for published downloads and choose the JAR for your loader. Fabric also needs
the matching **Fabric API**.

1. Put the JAR in your instance's `mods` folder and start Minecraft.
2. Enter a world and press **K**, or run `/guide`.
3. Select the gear button, open **Models**, and add a model profile.
4. Choose **OpenAI-compatible Chat Completions** or **Anthropic Messages**.
   Enter your provider URL, model ID, and API key.
5. Review the context window and maximum output. Matching models fill these in
   automatically; enter them yourself if the provider or catalog has no values.
6. Save, select the profile in the conversation header, and ask your first question.

Try: **“How do I make this item, and do I have the ingredients?”**

For Fabric modpacks with Architectury, use **21.0.4** for working text input.
Versions **21.0.2 and earlier** prevent text input in the OpenAllay screen.
Architectury is not required.

## Explore your modpack

Spend less time switching between wikis, recipe screens, and guide books.
OpenAllay can connect recipes, items, inventory, installed mods, settings, and
nearby game observations in one conversation. It can compare a whole collection
at once rather than looking up one item per model turn.

- “Which sword in this modpack has the highest base damage?”
- “Compare the foods from Farmer's Delight by nutrition.”
- “How do I make apple cider? Do I have enough ingredients?”
- “Search my installed guide books for magical crops.”
- “Which resource packs are active, and what are my current video settings?”

Answers can include item icons, ingredient slots, recipe layouts, tables, and
expandable details. Tool cards show the work performed and its results, with
clean expandable previews when datasets are large.

Optional integrations include **JEI**, **REI**, **Patchouli**, and recipe-rich
mods such as **Farmer's Delight**. The available data depends on your installed
mods and their integrations; none of these mods is required to use OpenAllay.

## Build in your world

The **Minecraft Builder** Extension comes with both loader downloads. Describe
what you want to build, then use geometry, terrain tools, building presets, and
saved structure templates to bring it into your active single-player world.
Templates support rotation and mirroring. Block undo checks for later edits and
reports conflicts instead of overwriting them.

To start building:

1. Open a **single-player world** and select a model profile configured on your
   client, rather than a server-provided model.
2. In **Settings → Extensions**, select **Minecraft Builder**. Under
   **Extension native actions**, enable **Builder world writes**.
3. Ask for a build, for example: “Build a small stone tower beside me.”

The world-write setting is off by default and belongs to Builder alone.
**You do not need unrestricted JavaScript or JVM access.** Builder works in
survival and creative worlds; it does not write to ordinary remote servers.
Undo covers recorded block changes, not every side effect in the world.

Builder is developed independently in
[OpenAllay Extensions](https://github.com/nkanf-dev/OpenAllay-Extensions/tree/main/extensions/minecraft-builder).

## Create your own Skills

Teach OpenAllay how you play. Skills are reusable instructions and reference
material for a mod, a task, or a play style—not another set of fixed buttons.
OpenAllay loads relevant guidance when it needs it.

Bundled Skills cover recipes, machines, guide books, progression, and game
diagnosis. In **Settings → Skills**, browse, install, and update community
workflows, or import a local Skill package. You can write and share your own
modpack guides and workflows through
[OpenAllay Skills](https://github.com/nkanf-dev/OpenAllay-Skills).

## Add Extensions

Extensions connect new game data, reusable JavaScript modules, mod integrations,
actions, and native result views. Open **Settings → Extensions** to see what is
connected, browse compatible community packages, or import a local Extension JAR.
Extensions install like normal mods and become active after a restart.

Want to connect your own mod? Start with the examples and authoring guide in
[OpenAllay Extensions](https://github.com/nkanf-dev/OpenAllay-Extensions).

## Inside Minecraft

| Conversation | Tool details |
| --- | --- |
| ![OpenAllay conversation screen](docs/media/screenshots/openallay-chat.png) | ![OpenAllay tool details screen](docs/media/screenshots/openallay-tool-detail.png) |

*Screenshots show an earlier build. Layout and labels may differ in 0.3.0.*

Keep separate conversations for different projects, return to saved history,
copy answers, or export a session. The screen does not pause the game.

- **Enter** sends; **Shift+Enter** adds a line.
- **Stop** cancels the request; **Retry** starts it again.
- **Escape** closes the screen without stopping the answer.
- **Branch sessions (Fork):** create an independent conversation from a completed task without re-running Tools.
- **Follow-up & Steer:** choose **Follow-up** to run after the current task releases its resources,
  or **Steer** to add guidance at the next operation boundary. Steer does not interrupt a model call.
- Open tool details to inspect results. **Debug mode** also shows the submitted
  JavaScript and full input/output.

## Choose your model

Use a compatible hosted service or local endpoint. Save several profiles and
switch between them in the conversation. A client-configured profile can use a
remote provider—it does not require running a model on your computer.

In **Settings → Models**:

- **Context window and maximum output:** use matched provider metadata or the
  built-in model catalog, or enter your own values. Clear a field to return to
  automatic values.
- **Reasoning effort:** leave **Auto · provider default** selected, or request an
  explicit effort (supported levels depend on the selected model and provider).
- **Reference prices:** compare published reference token rates and pricing tiers.
  Your provider's actual billing may vary.

The chat footer shows the context estimate and budget, cumulative session cost,
and cache hit rate when available. Session totals include actual model and automatic
summary calls, including restored request history. **An estimate is not a bill.**
Missing usage or pricing stays unknown or partial, not zero. Provider rates and
unreported extra charges can change the amount you pay.

OpenAllay is free and open source. Model providers may charge for API use.

## Single-player and multiplayer

| Setup | What to expect |
| --- | --- |
| **Single-player** | Explore your instance and use Builder with its world-write setting enabled. |
| **Ordinary multiplayer server** | Install OpenAllay on your client only. Ask about the game data visible to your client; the server does not need OpenAllay. Builder world edits are not available here. |
| **Server with OpenAllay** | The server may offer a shared model and additional server-side capabilities. Shared models appear automatically in **Models**, separately from your own profiles. |

Optional **experimental game commands** can be enabled in **Settings →
Extensions**. They use your normal Minecraft identity and command permissions,
including available mod commands. This is separate from Builder world writes.

## Community and development

- [OpenAllay Skills](https://github.com/nkanf-dev/OpenAllay-Skills) — share
  workflows, modpack knowledge, and reference material.
- [OpenAllay Extensions](https://github.com/nkanf-dev/OpenAllay-Extensions) —
  build integrations and new capabilities.
- [Issues](https://github.com/nkanf-dev/OpenAllay/issues) — report a bug or suggest
  an improvement. Include your Minecraft version, loader, and steps to reproduce.
- [Development guide](docs/development.md) — build from source, contribute, and
  explore architecture and development guidelines.

OpenAllay is licensed under the [MIT License](LICENSE).
It is an independent project, not affiliated with or endorsed by Mojang Studios
or Microsoft.
