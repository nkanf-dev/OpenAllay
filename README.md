# OpenAllay

[简体中文](README.zh-CN.md)

**Your AI companion for exploring modpacks, finding answers, and building in Minecraft.**

Ask in plain language. OpenAllay uses the game data available in your instance
to work through the task and bring useful answers back into the game.

<img src="docs/media/openallay-banner.png" alt="OpenAllay — your AI companion in Minecraft. Explore, build, and create.">

[Download on GitHub](https://github.com/nkanf-dev/OpenAllay/releases) ·
[Quick start](#quick-start) · [0.4.2 release notes](docs/releases/0.4.2.md) ·
[Community](#community-and-development) ·
[Mature Forge backport verification](docs/verification/mature-forge-ecosystems.md)

| | Make it part of your game |
| --- | --- |
| **[Explore](#explore-your-modpack)** | Find recipes, compare gear, check ingredients, and understand your modpack. |
| **[Build](#build-in-your-world)** | Shape terrain, create structures, and reuse templates in your single-player world. |
| **[Skills](#create-your-own-skills)** | Add workflows and knowledge for the way you play. |
| **[Extensions](#add-extensions)** | Connect more mods, game data, actions, and native result views. |

## Quick start

OpenAllay **0.4.2** supports **23 Minecraft versions from 1.20.1 through 26.3**
on **Fabric and NeoForge**. Download the JAR that lists your exact Minecraft
version and loader from [GitHub Releases](https://github.com/nkanf-dev/OpenAllay/releases/tag/v0.4.2).
Some files cover multiple versions. Fabric also needs the matching **Fabric API**.

| Minecraft version | Java version |
| --- | --- |
| 1.20.1–1.20.4 | 17 |
| 1.20.5–1.20.6 and 1.21–1.21.11 | 21 |
| 26.1, 26.1.1, 26.1.2, 26.2, 26.3 | 25 |

Minecraft **26.2 / Java 25** remains the development mainline. See the
[release compatibility table](docs/native-binary-artifacts.md#published-042-files)
for the exact versions covered by each download.

1. Put the JAR in your instance's `mods` folder and start Minecraft.
2. Enter a world and press **K**, or run `/guide`.
3. Select the gear button, open **Models**, and add a model profile.
4. Choose **OpenAI-compatible Chat Completions** or **Anthropic Messages**.
   Enter your provider URL, model ID, and API key.
5. Review the context window and maximum output. Matching models fill these in
   automatically; enter them yourself if the provider or catalog has no values.
6. Save, select the profile in the conversation header, and ask your first question.

Try: **“How do I make this item, and do I have the ingredients?”**

For **Minecraft 26.2** Fabric modpacks with Architectury, use **21.0.4** for working text input.
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
expandable details. Tool cards show a compact summary of each operation.
Open a card's details to inspect complete results when datasets are large.

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
2. In **Settings → Extensions**, enable **Minecraft Builder**.
3. Ask for a build, for example: “Build a small stone tower beside me.”

Enabling Builder enables its building and world-write operations.
**You do not need full-access JavaScript or JVM access.** Builder works in
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

| Fullscreen conversation | Native recipe details |
| --- | --- |
| ![OpenAllay 0.4.0 conversation screen](docs/media/screenshots/openallay-chat.png) | ![OpenAllay 0.4.0 tool details and native recipe](docs/media/screenshots/openallay-tool-detail.png) |

| Gameplay HUD | Full HUD reader |
| --- | --- |
| ![OpenAllay 0.4.0 gameplay HUD](docs/media/screenshots/openallay-hud.png) | ![OpenAllay 0.4.0 HUD reader](docs/media/screenshots/openallay-hud-reader.png) |

*Captured from OpenAllay 0.4.0 in an isolated demonstration world. The example
conversation uses a local deterministic demo endpoint.*

Keep separate conversations for different projects, return to saved history,
copy answers, or export a session. The screen does not pause the game.

- **Enter** sends; **Shift+Enter** adds a line.
- **Stop** cancels the request; **Retry** starts it again.
- **Escape** closes the screen without stopping the answer.
- **Branch sessions (Fork):** create an independent conversation from a completed task without re-running Tools.
- **Follow-up & Steer:** choose **Follow-up** to run after the current task releases its resources,
  or **Steer** to add guidance at the next operation boundary. Steer does not interrupt a model call.
- **World observation:** the Agent can read live focus and request world or game-UI images as needed.
  Image requests need an image-capable model. Open tool details to inspect the actual capture.
- **Input references:** focus and associated frames support your question. Refresh or remove them,
  or attach a current world frame. They describe the source of that input, not a live view.
- **Image inputs:** paste images with **Ctrl/Cmd+V** when using an image-capable model.
- **Manual compaction:** when the conversation is idle, enter `/compact` to summarize older context
  with your client-configured model. Server models do not support this command yet. Use `//` to send a literal `/`.
- Open tool details to inspect results. **Debug mode** also shows the submitted
  JavaScript and full input/output.

- **HUD and appearance:** enable the gameplay HUD in **Settings → UI**. Press
  **F8** by default to open its reader; existing custom key bindings stay unchanged.
  Adjust HUD placement, text size, density, and theme to suit your screen.
- **Push-to-talk:** enable and configure **Settings → Voice**, then bind its key
  in Minecraft Controls. Native recognition needs a downloaded or imported model;
  an HTTP transcription backend is also available. Gameplay/HUD speech defaults
  to **Send**, with **Draft** available. Fullscreen dictation stays editable before sending.

| Appearance settings | Voice settings |
| --- | --- |
| ![OpenAllay 0.4.0 appearance settings](docs/media/screenshots/openallay-ui-settings.png) | ![OpenAllay 0.4.0 voice settings](docs/media/screenshots/openallay-voice-settings.png) |

| General settings | About OpenAllay |
| --- | --- |
| ![OpenAllay 0.4.0 general settings](docs/media/screenshots/openallay-general-settings.png) | ![OpenAllay 0.4.0 About screen](docs/media/screenshots/openallay-about.png) |

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
| **Single-player** | Explore your instance and build with Minecraft Builder enabled. |
| **Ordinary multiplayer server** | Install OpenAllay on your client only. Ask about the game data visible to your client; the server does not need OpenAllay. Builder world edits are not available here. |
| **Server with OpenAllay** | The server may offer a shared model and additional server-side capabilities. Shared models appear automatically in **Models**, separately from your own profiles. |

Optional **experimental game commands** can be enabled in **Settings →
Extensions**. They use your normal Minecraft identity and command permissions,
including available mod commands. Full-access JavaScript includes commands and
enabled Extension operations. Minecraft server rules and permissions still apply.

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
