# Universal Extensions

OpenAllay **0.4.4** provides standalone **Extension API 0.4.0** alongside the
legacy **0.2.2 loader-mod API**. The standalone SDK targets Java 8 and has no
production Minecraft, loader, Rhino, Gson, or core dependencies. Universal
Extensions use stable host interfaces across their declared Minecraft and
loader targets.

## Shared SDK and player access

The current SDK and bundled **Builder 0.4.0** serve the release's supported
native hosts. The core owns Minecraft and loader differences; Builder keeps
one shared implementation for geometry, terrain, templates, journals, Skills,
and JavaScript. Exact targets and Java requirements are listed in the
[release artifact table](native-binary-artifacts.md#release-044-files).

Enable **Minecraft Builder** in **Settings → Extensions** to use its building
operations in ordinary JavaScript mode. The native world adapter binds the
active player and integrated server, with cancellation, owner-thread,
loaded-chunk, and world-lifetime checks. Presets use each game's native palette.
The [Forge installation guide](forge-runtime-installation.md) covers the
runtime profile required by older Forge hosts.

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
identity, cancellation and evidence. Player settings control exposed operations.
Enabling Builder makes its building operations available through the host world
API in ordinary JavaScript mode. The **Enable full-access JavaScript** switch
can stay off.

## One startup package

Place the self-contained universal JAR under `config/openallay/extensions/` and
restart Minecraft. Legacy loader-mod packages use `mods/` and their declared
loader registration path. Dependencies owned by the Extension may be shaded
into its JAR without embedding SDK or game classes. External
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

`targets` lists an explicit union of supported loader, Minecraft, product, and
API coordinates. Loader IDs are open strings. Exact versions, `[exact]`, and
Maven-style intervals use the core's Maven version matcher. API versions are
independent of the product version.

- `requiredHostFeatures` checks the host features needed to activate the package.
- `validatedTargetIds` records validation facts. The declared targets control
  compatibility, and player settings control access.
- Optional `requirements` describes useful host features, Extensions, and Skills
  for advisory display. Installation and permission controls operate
  independently of this metadata.

Startup retains accepted package classloaders for the framework lifetime,
including across world changes. At shutdown, invocation admission closes, native
activity is revoked, and actual worker cleanup completes before classloaders close.

## Core-bundled packages

The core distribution embeds Builder as a verified raw resource. The host loads
it through the universal Extension discovery path. Exact source and artifact SHA-256
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
palettes must describe the blocks and properties available in each supported
game version.

## Player access hierarchy

Ordinary JavaScript uses the enabled features exposed by the host and Extensions.
Enabling Minecraft Builder includes its building and world-write operations.

Full-access JavaScript includes game commands and enabled Extension operations.
Command-only access remains available when full access is off. Changing full
access preserves the stored command-only choice.

Every native world operation requires an active player connection, invocation,
and world.
Exact player/session identity, cancellation, native owner scheduling, and
Minecraft server permissions apply in every access mode.
