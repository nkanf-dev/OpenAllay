# OpenAllay

[简体中文](README.zh-CN.md)

OpenAllay is a modern Minecraft Agent built for modpacks. Ask naturally: it can
understand the task, explore the game data available in your current instance,
work through several steps, and turn the result into a useful in-game answer.

![OpenAllay brings Skills, Extensions, shared models, rich interfaces, and visual guidance into Minecraft](docs/media/openallay-banner.png)

*One companion for the recipes, systems, settings, and knowledge spread across
an entire modpack.*

## A modern Agent inside Minecraft

OpenAllay is more than a chat box with a collection of fixed look-up buttons.
It combines model context, tool use, Skills, and an embedded JavaScript analysis
runtime. When a question needs real game data, the Agent can write a focused
analysis for that question, compare a whole dataset at once, and continue from
the result.

That makes requests such as “rank every sword by damage” or “find the container
with the cheapest recipe” one coherent analysis instead of dozens of repeated
single-item searches.

### Skills that teach workflows

Skills give the Agent progressively disclosed instructions for a mod, activity,
or type of problem. OpenAllay loads the relevant Skill when a task needs it,
while simple questions can go straight to the available game data. Bundled
Skills already cover missing recipes, modded machines, guide books, progression,
multi-part game diagnosis, and the optional command workflow. Browse, install,
and update more workflows from the
[OpenAllay Skills community](https://github.com/nkanf-dev/OpenAllay-Skills), or
import a local Skill package from the in-game settings.

### Extensions that grow with your modpack

OpenAllay Extensions provide typed game data, reusable JavaScript modules, mod
integrations, and native result views. The in-game Extensions page shows what
is currently connected, which data roots are available, and compatible packages
from the
[OpenAllay Extensions community](https://github.com/nkanf-dev/OpenAllay-Extensions).
Community Extensions install as normal mod packages and become active after a
restart. You can also import a compatible local Extension JAR without waiting
for it to appear in the community catalog. This lets new mod integrations grow
without turning every capability into another one-purpose Agent tool.

### Online construction as an independent Extension

OpenAllay 0.2.4 includes the Minecraft Builder Extension in both Fabric and
NeoForge downloads. It comes from
[OpenAllay-Extensions](https://github.com/nkanf-dev/OpenAllay-Extensions/tree/main/extensions/minecraft-builder)
and supplies geometry, terrain tools, six building presets, saved structure
templates, rotation, mirroring, and conflict-aware block undo through its
JavaScript module and Skill.

To build in your active single-player world, choose a local model and explicitly
enable **unrestricted JavaScript** in **Settings → Extensions**. This setting is
off by default and stays separate from Extension installation. Builder works in
survival and creative worlds. Its undo checks for intervening block edits.

Skill and Extension details list useful capabilities and dependencies. During
installation, you can enable available requirements, cancel, or choose
**Continue anyway** to install the package with your current permissions.

### Results made for Minecraft

Answers can include item icons, ingredient slots, recipe layouts, tables,
progress steps, and expandable details. Typed JavaScript results can become
native recipe or item presentations automatically. JavaScript tool cards show
a short planned-action title and description alongside execution status and
results, and saved conversations keep those descriptions. In Debug mode, tool
details also show the submitted JavaScript and the input and output behind the
answer.

### Your model, your choice

Connect an OpenAI-compatible Chat Completions or Anthropic Messages provider,
save several model profiles, and switch between them from the conversation.
OpenAllay works in single-player and on ordinary multiplayer servers without
requiring the server to install it. A server that does install OpenAllay may
offer a shared model and additional server-side capabilities. Its shared model
appears automatically in **Models** while connected, clearly marked as
server-provided and separate from your local profiles.

A built-in offline model table fills in the context window for matching models,
including services whose model list contains only IDs. You can edit the value
at any time. The Models page also shows published token-price estimates, price
tiers, sources, and dates as a reference for choosing a model.

### Conversations that stay useful

Keep topics in separate sessions, return to saved conversations, copy useful
messages, or export a complete session. Live status explains whether the Agent
is loading guidance, analysing game data, waiting for the model, or completing
an action.

## What OpenAllay 0.2 can do

- Analyse and connect items, recipes, effects, tags, registries, guides,
  settings, and player-visible game state with one JavaScript program.
- Filter, group, sort, rank, aggregate, and compare whole collections instead
  of querying one candidate per model round.
- Discover the available data schema progressively, including typed fields
  contributed by compatible Extensions.
- Reuse bundled JavaScript modules for deterministic domain work such as recipe
  and inventory calculations.
- Inspect installed mods, video and gameplay settings, resource packs,
  coordinates, dimension, and F3-style diagnostics.
- Inspect a focused region of blocks or entities, then filter, compare, and
  verify the observed result with JavaScript instead of flooding the
  conversation.
- Search supported guide-book content and use recipe information from the game,
  JEI, REI, and recipe-rich mods such as Farmer's Delight.
- Present trusted recipes, items, tables, compact values, and generic results
  through data-driven native views.
- Keep multiple conversations, durable history, model profiles, copy, export,
  cancellation, and retry in the native OpenAllay screen.
- Optionally expose the current Minecraft command set—including commands added
  by mods—to the Agent, and let it read Minecraft's feedback before reporting
  what happened. This experimental capability is disabled by default and can
  be enabled from **Settings → Extensions**.

## Quick start

Download the latest OpenAllay **0.2.x** build for **Fabric** or **NeoForge**
from [Modrinth](https://modrinth.com/mod/openallay/versions) or
[GitHub Releases](https://github.com/nkanf-dev/OpenAllay/releases).
The 0.2 line targets Minecraft **26.2** and requires Java **25**.
Fabric players also need the matching Fabric API.

Place the downloaded JAR in your instance's `mods` folder, start Minecraft, and
connect a model:

1. Enter a world and press **K**, or run `/guide`.
2. Select the gear button and open **Models**.
3. Add an **OpenAI-compatible Chat Completions** or **Anthropic Messages**
   profile.
4. Enter the provider URL, model ID, and API key.
5. Review the matched context window, or enter it manually, then save.
6. Select the profile from the conversation header and start asking questions.

For Fabric modpacks that include Architectury, use **21.0.4** for working text
input in the OpenAllay screen. Versions **21.0.2 and earlier** prevent text input.

## Try asking

- “Which mods are installed, and what versions are they?”
- “Which sword in this modpack has the highest base damage?”
- “Which craftable container needs the fewest total ingredients?”
- “How do I make apple cider? Do I already have the ingredients?”
- “Compare every food from Farmer's Delight by nutrition.”
- “Which resource packs are active?”
- “Show my coordinates, dimension, and F3 information.”
- “Search my installed guide books for magical crops.”
- “What fields are available for potion effects in this modpack?”

With experimental commands enabled, you can also ask OpenAllay to perform an
available Minecraft command, read the game's response, and distinguish
confirmed feedback from a command that produced no visible reply.

## Using the in-game screen

OpenAllay opens in a non-pausing Minecraft screen.

- **Enter** sends a message; **Shift+Enter** adds a new line.
- **Stop** cancels the current request; **Retry** starts it again.
- **Escape** closes only the screen. Reopen it to see the continuing answer.
- Select a tool card to inspect its input and actual output.
- Enable **Debug mode** when you want to inspect the JavaScript written by the
  Agent and the complete live diagnostics.
- Use separate sessions for different topics, or switch to another configured
  model whenever you like.

## Mod and content support

OpenAllay builds on content already present in your modpack:

- **JEI** recipes can use JEI's familiar layout inside OpenAllay.
- **REI** can contribute recipe information to OpenAllay's recipe experience.
- **Patchouli** guide-book content in active resources can be searched in-game.
- Recipe-rich mods such as **Farmer's Delight** work with recipe analysis,
  ingredient checks, and visual recipe pages.
- Mod-added registries, fields, and commands can be discovered through the
  typed Extension and command catalogs when their integrations are available.

These integrations are optional. OpenAllay remains useful when one of them is
not installed or is unavailable for the current setup.

## Roadmap

OpenAllay is growing into an open Agent platform for Minecraft:

- **OpenAllay Skills** — workflows and domain knowledge that players, modpack
  authors, and communities can create, improve, share, and install in-game.
- **OpenAllay Extensions** — new game-data adapters, reusable modules, mod
  integrations, Agent capabilities, and native result experiences, distributed
  as familiar mod packages.
- **OpenAllay Host** — shared models and centrally managed Agent services for
  servers and communities.
- **OpenAllay Studio** — a creative environment for rich in-game experiences,
  dynamic interfaces, and reusable visual guidance.

### Next

- A player memory system that you can review, correct, pin, or forget.
- Better workflows for creating, editing, reviewing, and publishing Skills and
  Extensions.
- More first-party data adapters, knowledge sources, and mod integrations.
- A clearer experimental-action experience with player approvals.

### Longer term

- Expand focused block and entity observation into maps, structures,
  containers, and richer nearby-environment understanding.
- Turn structures and documentation into step-by-step visual tutorials,
  including Ponder-style guidance when a compatible integration is available.
- Plan production chains across machines, intermediate materials, and large
  technology trees.
- Grow OpenAllay Host, Studio, and the wider community ecosystem.

OpenAllay is an independent project and is not affiliated with or endorsed by
Mojang Studios or Microsoft.

## Contributors and developers

Want to contribute or run the project from source? Start with the
[development guide](docs/development.md).

OpenAllay is licensed under the [MIT License](LICENSE).
