# TomeWisp Foundation Design

Status: Approved for specification  
Date: 2026-07-16  
Repository: `/Users/nkanf/projs/TomeWisp`  
Primary target: Minecraft 26.2, NeoForge and Fabric

## 1. Purpose

TomeWisp is an independent Minecraft Java mod that lets players ask questions about the currently running modpack and receive answers grounded in live game data, quest books, guide books, recipes, registries, and mod-provided documentation.

The product is not a general autonomous agent. It is a constrained teaching assistant whose main differentiators are:

- strongly typed Minecraft tools;
- reusable Agent Skills that describe reliable workflows;
- adapters for public and private mod documentation APIs;
- a future path from structures to generated Ponder tutorials.

The repository is independent from the `NeoMCServer` monorepo. It has its own history, releases, CI, issue tracking, and compatibility branches.

## 2. Confirmed Decisions

| Area | Decision |
|---|---|
| Repository | One independent Git repository named `TomeWisp` |
| Primary Minecraft version | 26.2 |
| Primary Java version | Java 25, required by the 26.2 toolchain |
| Loaders | NeoForge and Fabric are equal first-class targets |
| Source language | Java-first; Kotlin is not used for product source code |
| Agent | One bounded agent using an existing JVM loop; no custom agent framework |
| Version strategy | `main` targets 26.2; 1.21.1 is a later compatibility branch |
| 1.21.1 Java version | Java 21 |
| Cross-loader structure | `common`, `fabric`, and `neoforge` modules |
| Initial build basis | The 26.2 branch of `jaredlll08/MultiLoader-Template` |
| Tool extensions | Typed Java SPI first; controlled reflection as a development escape hatch |
| Runtime compilation | No JIT in the first release |
| Search | Deferred to phase 2 |
| Ponder | Optional adapter; must not block the 26.2 foundation |

## 3. Goals and Non-goals

### 3.1 Foundation goals

- Build and launch on both NeoForge 26.2 and Fabric 26.2.
- Keep loader-specific code out of the domain and agent layers.
- Establish stable tool, knowledge-source, Skill, and platform interfaces.
- Let first-party integrations expose FTB Quests, Patchouli, recipes, registries, and mod guide APIs as tools.
- Let private mods provide tools through a small Java SPI.
- Provide a development mode for inspecting, invoking, validating, and reloading tools.
- Support simple read-only reflective adapters for private APIs without compiling code at runtime.
- Preserve boundaries that can be backported to a future 1.21.1 branch.

### 3.2 Explicit non-goals

- No shell execution, arbitrary scripts, or arbitrary bytecode loading.
- No MCP layer around in-process Minecraft APIs.
- No Node or Python sidecar.
- No LangChain, LangGraph, Spring AI, multi-agent orchestration, planner graph, or agent handoff system.
- No general vector database in phase 1.
- No semantic/full-text search in phase 1.
- No Java/Kotlin JIT compiler in phase 1.
- No direct world mutation, item granting, quest completion, or server command execution by the agent.
- No hard dependency on a Ponder implementation that is unavailable for 26.2.

## 4. Version and Branch Strategy

### 4.1 Mainline

`main` is the only implementation target during the initial milestone:

- Minecraft 26.2;
- Java 25;
- Fabric Loader and Fabric API versions pinned by the 26.2 template;
- NeoForge version pinned by the 26.2 template;
- `common`, `fabric`, and `neoforge` are built in every CI run.

### 4.2 Backward-compatible line

After the 26.2 foundation is stable, create `mc/1.21.1` from a deliberate backport point. That branch uses:

- Minecraft 1.21.1;
- Java 21;
- its own pinned Fabric and NeoForge toolchains;
- the same package names, domain DTOs, Tool SPI, Skill format, and platform interface names where technically possible.

The project does not promise every intermediate Minecraft version. New compatibility lines are added only for versions with meaningful modpack demand.

### 4.3 Backport discipline

- Domain, Skill, and provider changes should be small commits that can be cherry-picked.
- Minecraft API calls must stay behind platform or integration boundaries.
- Version-specific DTO fields must be normalized before entering the agent context.
- CI and releases are branch-specific; artifacts include Minecraft version and loader.

## 5. Upstream Constraints

The initial repository structure follows the 26.2 branch of [MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template/tree/26.2), which uses a common source set and does not require a third-party runtime abstraction library.

At specification time:

- [Ponder](https://github.com/Creators-of-Create/Ponder) has a public 26.1 development branch but no confirmed 26.2 branch.
- [Ponderer](https://github.com/Nobodiiiii/Ponderer) publicly maintains 1.21.1 and 1.20.1 lines, not 26.2.

Therefore Ponder integration is represented by an optional capability interface. The core mod must start and answer text questions without Ponder or Ponderer installed.

## 6. Repository Architecture

```text
TomeWisp/
├── common/
│   └── src/
│       ├── main/java/
│       ├── main/resources/
│       └── test/java/
├── fabric/
│   └── src/
├── neoforge/
│   └── src/
├── build-logic/
├── docs/
│   └── superpowers/
│       ├── specs/
│       └── plans/
├── gradle/
├── build.gradle
├── gradle.properties
├── settings.gradle
└── gradlew
```

### 6.1 Common package boundaries

```text
dev.tomewisp/
├── agent/          # bounded agent runner and response contracts
├── skill/          # Skill discovery, metadata, loading, and validation
├── tool/           # Tool definitions, registry, invocation, and audit
├── knowledge/      # guide/quest/recipe knowledge-source contracts
├── integration/    # optional-mod integration contracts and adapters
├── structure/      # version-neutral Structure IR
├── ponder/         # optional Ponder capability contract
├── platform/       # loader/Minecraft capability contracts
├── devmode/        # tool inspector, reflective adapter loader, reload
└── config/         # validated server/client configuration
```

### 6.2 Loader modules

The `fabric` and `neoforge` modules own only:

- mod entrypoints;
- lifecycle and resource-reload events;
- networking registration;
- loader-specific dependency detection;
- client/server environment separation;
- implementations of `platform` contracts.

The `common` module must not import Fabric or NeoForge APIs.

## 7. Agent Boundary

The product has one `GameGuideAgent`. The agent may:

1. load relevant Skill instructions;
2. invoke registered read-only tools;
3. synthesize a grounded answer;
4. return links/actions supported by the current client.

The agent loop is bounded by tool-round, token, time, and output-size limits. It does not create sub-agents and does not call arbitrary Java methods.

The first technical spike evaluates a minimal Koog Java API integration inside both loader environments. If Koog cannot be packaged without unacceptable conflicts, the fallback is the official Java model client plus a very small bounded function-calling loop. Tool and Skill interfaces must not depend on either runtime.

## 8. Tool Model

### 8.1 Typed tools

Every tool has:

- stable ID;
- human/model-facing description;
- input schema backed by a Java record or immutable DTO;
- output schema backed by a Java record or immutable DTO;
- source provider;
- read/write classification;
- availability predicate;
- timeout and result-size limit.

Conceptual API:

```java
public interface Tool<I, O> {
    ToolDescriptor<I, O> descriptor();

    ToolResult<O> invoke(ToolContext context, I input);
}

public interface ToolProvider {
    String providerId();

    Collection<? extends Tool<?, ?>> tools();
}
```

The final API may refine names and generic details, but it must preserve provider isolation and typed inputs/outputs.

### 8.2 Tool discovery

Providers come from:

1. TomeWisp built-ins;
2. optional first-party compatibility modules;
3. other installed mods that implement the public Java SPI;
4. development-mode reflective descriptors.

A provider can add only the tools it owns. Registration conflicts fail closed and produce a clear diagnostic.

### 8.3 Initial tool groups

- loaded mods and registry resolution;
- recipe lookup and bounded dependency expansion;
- player context snapshots;
- FTB Quests visible tasks, dependencies, and progress;
- Patchouli books, entries, pages, recipes, and multiblocks;
- generic guide source listing and entry retrieval;
- custom/private guide providers through the SPI;
- tool and provider diagnostics for development mode.

Phase 1 tools use exact IDs, relationships, current context, and bounded listing. Ranking across large corpora and semantic search belong to phase 2.

## 9. Knowledge Source SPI

Guide systems vary by mod, so tools consume a normalized knowledge-source contract instead of exposing each mod's object model to the agent.

Conceptual capabilities include:

- list available books or guide roots;
- list categories or chapters;
- fetch one entry by stable ID;
- fetch a page or section;
- return associated items, recipes, structures, and unlock state;
- report source provenance and visibility.

Hidden quests, locked entries, and server-only knowledge are filtered in the provider before data reaches the agent.

Public/private mods can implement `KnowledgeSourceProvider` directly. TomeWisp wraps providers as a consistent family of tools.

## 10. Development Mode

Development mode is opt-in and disabled by default on production servers.

### 10.1 Capabilities

- list registered providers and tools;
- inspect generated input/output schemas;
- invoke a selected tool with explicit test input;
- view raw structured output, duration, truncation, and errors;
- inspect whether an optional target mod/class/method is available;
- reload Skills, static documents, and reflective descriptors;
- validate all reflective descriptors without invoking them;
- export a successful reflective mapping as normalized configuration.

### 10.2 Interfaces

The first implementation may expose commands and structured chat output rather than a custom GUI. A graphical inspector is added only if command-based iteration proves inadequate.

### 10.3 Production separation

- Development commands require operator permission.
- Reflective providers are disabled unless explicitly enabled by server configuration.
- Tool invocation audit records provider, tool, player/operator, duration, and result status.
- Development mode never grants shell, file-system, world-write, or command-execution capabilities.

## 11. Reflective Adapter Design

Reflective adapters are a narrow compatibility escape hatch for private guide APIs. They are not a general scripting language.

Each descriptor declares:

- adapter and tool ID;
- required mod ID and optional version range;
- target class;
- instance resolution strategy from an approved set;
- method name and exact parameter types;
- mapping from tool arguments or read-only context values to parameters;
- bounded result conversion rules;
- null/error behavior;
- maximum invocation time and output size.

Startup/reload validation resolves classes and methods with `MethodHandle` where possible. Invalid mappings are disabled and shown in development diagnostics.

Allowed calls are limited to explicitly declared methods. No arbitrary method chaining, constructors, field writes, accessibility bypass, loops, imports, or expression evaluation are provided in phase 1.

When a reflective integration becomes important or complex, it should be replaced by a compiled `ToolProvider` or `KnowledgeSourceProvider`.

## 12. Why JIT Is Deferred

Runtime Java/Kotlin compilation would require shipping or locating a compiler, handling game mappings and loader classpaths, managing generated classes that cannot be cleanly unloaded, and building a substantially more complex failure and security model.

JIT may be reconsidered only if all of the following become true:

- at least three real integrations cannot be expressed through SPI or reflective descriptors;
- adapter compilation/restart time is a measured development bottleneck;
- a maintained compiler solution supports the required Java version and classpath;
- generated code can be isolated, versioned, audited, and safely invalidated.

Until then, SPI plus controlled reflection is the simpler product and developer experience.

## 13. Skills

Skills are data assets containing procedural instructions. The runtime profile supports:

```text
skill-name/
├── SKILL.md
├── references/
└── assets/
```

Only metadata is loaded eagerly. Full instructions and explicitly referenced files are loaded on demand. Runtime Skills cannot execute scripts, register Java classes, expand tool permissions, or access arbitrary paths/URLs.

Skills and static documents can be reloaded in development mode without restarting the game.

## 14. Data Flow

```text
Player question
  -> server request and PlayerContext snapshot
  -> Skill metadata selection
  -> bounded agent loop
  -> typed ToolRegistry
  -> built-in/SPI/reflective provider
  -> normalized ToolResult with provenance
  -> grounded GuideResponse
  -> client text and supported actions
```

Minecraft state is captured on the game thread into immutable snapshots. Model calls, document normalization, and other slow work run off the tick thread.

## 15. Error Handling

- Missing optional mods make their providers unavailable; they do not prevent startup.
- Provider initialization failure disables that provider and records a diagnostic.
- Tool exceptions become structured failures; Java stack traces are available only in server logs/development diagnostics.
- Oversized outputs are truncated according to the tool contract and marked as truncated.
- Repeated identical tool calls are stopped by the agent guard.
- Model unavailability does not disable local tool inspection or development diagnostics.
- Reflective resolution and invocation failures never fall back to a different undeclared method.
- Missing Ponder support produces text output rather than a broken visual action.

## 16. Security and Trust Boundaries

- Tool availability is determined by the server registry, never by prompt text.
- Skills may narrow allowed tools but cannot create or expand permissions.
- The agent receives filtered snapshots, not mutable Minecraft objects.
- Hidden quest/guide information is filtered before serialization.
- All initial product tools are read-only.
- Reflective calls cannot mutate fields or bypass Java visibility.
- Development mode is operator-only and explicitly enabled.
- API keys remain server-side.

## 17. Testing Strategy

### 17.1 Unit tests

- Tool registry conflicts and lifecycle;
- schema generation and DTO conversion;
- Skill metadata and path validation;
- provider availability and failure isolation;
- reflective descriptor validation and result conversion;
- Structure IR normalization;
- agent loop limits and repeated-call detection.

### 17.2 Integration tests

- Fabric client and dedicated-server startup;
- NeoForge client and dedicated-server startup;
- optional dependency absent/present behavior;
- resource reload with Skills and descriptors;
- mocked FTB Quests/Patchouli/private guide providers;
- game-thread snapshot followed by background tool/model work.

### 17.3 CI

Every `main` change must:

- build common, Fabric, and NeoForge artifacts with Java 25;
- run unit tests;
- verify no loader API leaks into forbidden common packages;
- validate mod metadata and artifact names;
- retain logs for failed startup smoke tests.

The future `mc/1.21.1` branch receives an equivalent Java 21 workflow.

## 18. Delivery Phases

### Phase 0: repository and feasibility foundation

- create the 26.2 MultiLoader project;
- verify Java 25, Gradle wrapper, VS Code import, both loaders, and dedicated servers;
- establish package boundaries and CI;
- spike the smallest Koog Java integration and packaging test;
- establish typed ToolRegistry and platform interfaces.

### Phase 1: grounded knowledge tools and development mode

- minimal player question/answer flow;
- recipe, registry, and player-context tools;
- FTB Quests adapter;
- Patchouli adapter;
- generic Knowledge Source SPI;
- private Mod ToolProvider example;
- development tool inspector and invocation commands;
- reflective descriptor validation, invocation, and hot reload;
- Skill discovery and reload.

### Phase 2: search and ranking

- keyword and structured indexing across guide sources;
- bounded result ranking and provenance;
- optional semantic retrieval only if evaluation proves it necessary;
- query evaluation corpus for modpack questions.

### Phase 3: visual tutorials

- optional Ponder capability adapter for a supported 26.2 upstream or port;
- Structure IR to deterministic tutorial steps;
- scene validation, distribution, reload, and client opening;
- graceful text-only fallback.

### Phase 4: 1.21.1 compatibility line

- create `mc/1.21.1` from a selected stable milestone;
- switch toolchain to Java 21 and the 1.21.1 loader dependencies;
- backport stable domain/SPI/Skill contracts;
- use available Ponderer integration where beneficial;
- maintain a branch-specific compatibility test pack.

## 19. Foundation Acceptance Criteria

The foundation milestone is complete when:

- the independent repository builds reproducibly from a clean checkout;
- Fabric 26.2 and NeoForge 26.2 clients reach the title screen;
- both dedicated servers start without client-class loading errors;
- Java 25 is enforced by the build;
- a sample typed tool can be registered and invoked on both loaders;
- common code contains no loader-specific imports;
- development mode can list and manually invoke the sample tool;
- a valid reflective sample resolves and returns a bounded structured result;
- an invalid reflective sample fails closed with a useful diagnostic;
- CI verifies both loader artifacts.

## 20. Deferred Decisions

The following choices are intentionally made only after their prerequisite evidence exists:

- the final model provider and exact Koog module set, after the packaging spike;
- the exact 26.2 FTB Quests and Patchouli integration APIs, after dependency availability is verified;
- whether a GUI is needed for the development tool inspector;
- whether Ponder is supplied by an updated upstream, an isolated port, or another renderer;
- whether semantic retrieval is justified by phase 2 evaluation;
- whether runtime compilation is ever justified by real adapter failures.

These are bounded future decisions, not requirements for the repository foundation.
