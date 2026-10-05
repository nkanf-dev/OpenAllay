# Universal Extensions

The current development core provides the standalone **Extension API 0.4.0** alongside
the existing 0.2.2 loader-mod API. This integration follows the published 0.4.1 release;
the published 0.4.1 downloads still expose only API 0.2.2. The new SDK compiles to Java 8 bytecode and has no production
Minecraft, loader, Rhino, Gson or core dependencies. It lets one Extension payload
use stable host interfaces instead of linking to a specific game's native classes.

## Current validation boundary

The current SDK 0.4.0 source removes Extension-private permission declarations and
grants. Its coordinated Builder 0.4.0 source uses host world APIs without an extra
write approval. This source change is being verified through the normal Java,
loader packaging and packaged-client CI suites. Earlier SDK/Builder runtime
receipts remain historical evidence for their exact earlier source and JARs, not
acceptance of this new public ABI.

Default development remains Minecraft 26.2. SDK 0.4.0, player access semantics and
the same universal Builder source belong to all prepared target profiles from 26.3
through 1.20.1. Native source, compilation, packaging and real-client results are
tracked separately for each actual API family and loader. Candidate declarations
do not establish a published runtime interval; only accepted artifact families
are released. Java 8 SDK bytecode alone is not game-runtime evidence.
Published v0.4.1 downloads and tags remain unchanged.

## Build against the SDK

Use `dev.openallay:openallay-extension-api:0.4.0` or the locally built SDK JAR as a
compile-only dependency. Target Java 8 when your Extension needs the full supported
JVM range. Do not put SDK classes, core classes, Minecraft classes or loader entrypoints
inside the Extension JAR. The core supplies one shared SDK class identity.

Implement `dev.openallay.api.extension.OpenAllayExtension` with a public no-argument
constructor. `descriptor()` declares identity and support without accessing a world.
`contribution(ExtensionHost)` returns JavaScript modules, Skills, semantic result-view
labels, lifecycle participants and controlled host methods.
Pure JavaScript/Skill Extensions do not need native world access.

`JavascriptHostMethod.Invoker` receives each detached JSON argument as a String and
returns one JSON value as a String. Core validates declared types and detaches results;
Gson and Rhino values do not cross the public ABI. `ExtensionInvocation` keeps scoped
identity, cancellation and evidence. Extensions do not declare private permission
names or ask the player to approve extra operation scopes. An enabled Builder can
build through the host world API in ordinary JavaScript mode; Java/JVM access is
not required for that function.

## One startup package

Place the self-contained universal JAR under `config/openallay/extensions/` and restart.
It is not a loader mod and does not belong in `mods/`. Dependencies owned by the
Extension may be shaded into its JAR without embedding SDK or game classes. External
manifest Class-Path, nested dependency JARs and multi-release game selectors are not
part of this first package contract.

The package contains `META-INF/openallay-extension.json` with the exact schema below.
Its declarations must match the entrypoint's descriptor. The framework checks support
before class initialization and does not load rejected packages. Released schema-1
loader-mod Extensions keep their existing installation and registration path.

```json
{
  "schemaVersion": 2,
  "id": "example:shared",
  "name": "Shared Example",
  "version": "1.0.0",
  "provider": "Example",
  "summary": "One Extension with a stable host interface.",
  "source": "https://example.com/extension",
  "entrypoint": "example.extension.SharedExtension",
  "support": {
    "targets": [
      {
        "loader": "fabric",
        "minecraftVersionRange": "26.2",
        "openAllayVersionRange": "[0.4.1,)",
        "openAllayApiVersionRange": "[0.4.0,0.5.0)"
      },
      {
        "loader": "neoforge",
        "minecraftVersionRange": "26.2",
        "openAllayVersionRange": "[0.4.1,)",
        "openAllayApiVersionRange": "[0.4.0,0.5.0)"
      }
    ],
    "minimumJavaVersion": 8,
    "requiredHostFeatures": [],
    "validatedTargetIds": []
  }
}
```

`targets` is an explicit union, so different game eras need not be forced into one
continuous range. Loader IDs are open strings, not a closed Fabric/NeoForge enum.
Exact versions, `[exact]` and Maven-style intervals use the core's mature Maven
version matcher. API versions are independent of the product version.
`requiredHostFeatures` checks actual host availability, not grants.
`validatedTargetIds` records independent validation facts and never authorizes or
expands compatibility. Optional `requirements` describes host feature, Extension and Skill availability.
It is advisory, not an installation or permission gate. Do not use it to invent an
Extension-private authorization system.

Startup retains accepted package classloaders for the framework lifetime. Closing a
world is not framework shutdown. At shutdown, invocation admission closes, native
activity is revoked, and actual worker cleanup completes before classloaders close.

## Core-bundled packages

Both current loader artifacts embed the same Builder JAR as a raw resource, not a
loader mod or JarJar Builder dependency. Exact source and artifact SHA-256
provenance is stored at `META-INF/openallay/distribution.json`.
The host admits community packages first. An ACTIVE same-ID package wins without
reading or extracting the bundled payload. Otherwise, the verified package is
staged under `config/openallay/.bundled-extensions/<sha256>/` and admitted through
the normal universal discovery implementation. Invalid or corrupt cache files
are preserved and reported, not overwritten. No remote download or source build
runs inside Minecraft. The host closes bundled and community package classloaders
only after admitted workers release their invocation hooks.

## Shared Builder and game adapters

The fixed `MinecraftWorldAccess` / `WorldSession` interfaces use primitive coordinates,
detached canonical states and invocation-bound operations, not native game handles.
Native scheduling, registries/NBT/components, GUI/rendering, network and loader events
belong to the core's game adapters. Builder's geometry, terrain, templates, journals,
Skills and JavaScript should remain one Extension implementation. Native material
palettes must describe real available blocks and properties; matching JAR bytes alone
does not make modern blocks exist in an older game.

## Player access hierarchy

Ordinary JavaScript uses the enabled features exposed by the host and Extensions.
Enabling Minecraft Builder includes its building operations. There is no separate
Builder write switch.

Unrestricted JavaScript is full OpenAllay access. It includes the command capability
and must not be blocked by a smaller OpenAllay authorization toggle. Command-only
access remains available when full access is off. The stored command-only choice is
not rewritten when full access changes.

Full access does not create a disconnected world, restore a closed invocation, or
change Minecraft's remote server/operator permissions. Exact player/session identity,
cancellation, native owner scheduling and world validity still apply to real operations.
These checks are execution validity, not extra permission approvals.
