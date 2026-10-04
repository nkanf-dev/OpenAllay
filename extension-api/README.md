# Native-neutral Extension API 0.3.0

This plain `java-library` module is an additive SDK boundary. It does not replace
legacy `dev.openallay.extension` API 0.2.2 or change the product release version.
The artifact is `dev.openallay:openallay-extension-api:0.3.0`. Production has no
external dependencies. The package is `dev.openallay.api.extension`.

The module compiles with an installed Java 25 toolchain and `--release 8`. Select
an installed Java 17 compiler with `-PextensionApiJavaVersion=17` if needed. The
same production and fixture classes target class-file major version 52. JUnit
runs under the selected modern toolchain, not an SDK runtime requirement.

## Boundary and authority

- An entrypoint implements `OpenAllayExtension` with a public no-argument
  constructor. `descriptor()` reads metadata only.
- `contribution(ExtensionHost)` declares JS modules, Skills, semantic result
  views, lifecycle participants, exact JSON host methods and capabilities.
  It does not capture native state or grant capabilities.
- The core owns discovery, package validation, compatibility matching, frozen
  grants, cancellation, lifecycle cleanup, evidence storage and strict JSON
  parse/detach/type checks. SDK strings are not a JSON parser or permission engine.
- `MinecraftWorldAccess.open(invocation)` captures a native session only when
  authorized world work actually begins. An unavailable adapter throws
  `ExtensionException`. Pure-JS/Skill Extensions do not need world access.
- `WorldSession` uses primitive XYZ coordinates and detached canonical state
  JSON. `call(Callable)` accepts trusted Java work, never an Agent/JS callback.
  Each adapter rechecks scope, capability and exact bound world/connection before
  admitting queued owner work. `close()` revokes the session and is idempotent.
- Extension packages must not embed SDK, core, Minecraft or loader classes. The
  host supplies one SDK class identity. The fixture JAR includes only its own
  classes/resources, with the SDK on the host classpath.

## Compatibility syntax and validation facts

A `SupportTarget` declares loader, Minecraft, core and public API selectors.
Loader IDs are lowercase string identifiers; unknown future loaders are accepted.
Selectors accept exact version tokens, `[exact]`, or one Maven-style interval
such as `[1.12.2,1.13)` or `[0.4.1,)`. Missing bounds must be open. Selector
syntax validation does not compare/order versions or establish actual support.
The core uses its runtime-specific matching implementation, including endpoint
ordering and runtime-specific version interpretation. Declare separate
`SupportTarget` entries for noncontiguous eras, not one invented continuous range.

`SupportDeclaration.targets()` is the explicit union. `minimumJavaVersion()`
is at least 8. `requiredHostFeatures()` are compatibility facts, not grants.
`validatedTargetIds()` is a separately declared set of validation coordinate
IDs. It is never filled from ranges. Host-feature and validated-coordinate IDs
use `[a-z0-9][a-z0-9_.-]*(?::[a-z0-9_][a-z0-9_./-]*)?`; their meaning is core-owned.
Namespaced Extension/contribution/capability IDs use
`[a-z0-9_.-]+:[a-z0-9_./-]+`. Advisory Skill IDs use lowercase hyphenated names
with a maximum of 64 characters. Collections are copied and unmodifiable;
null elements and duplicate contribution identities are rejected. JS modules
and host bindings share the `require(id)` namespace. Source text is not trimmed.

`ExtensionException` carries a stable lowercase failure code (underscores,
dots and hyphens accepted), a player-safe summary, and an optional diagnostic
cause. Summary safety is the trusted producer's responsibility. The core maps
these failures without depending on an Extension-owned exception class.
`WorldSession.WriteOutcome` preserves changed/failure accounting; `actual` may
be null when readback fails. Exception objects use their normal identity semantics.

## Source fixtures and coordinator verification

Run from the repository root with the checked-in wrapper:

```
./gradlew :extension-api:test :extension-api:jar :extension-api:fixtureJar
```

Compile the fixture once. Run the same fixture JAR and SDK JAR with actual
installed JVM 8, 11, 17, 21 and 25 commands, for example:

```
/path/to/java -cp extension-api/build/libs/openallay-hello-extension-fixture-0.3.0.jar:extension-api/build/libs/openallay-extension-api-0.3.0.jar dev.openallay.fixture.HelloExtension
```

Record JAR SHA-256 values once and use those same bytes in every cell. This
fixture exercises the detached SDK ABI. It does not claim loader discovery or
native Minecraft support. SDK source delivery alone is **not Minecraft 1.12.2
support**. Native Forge 1.12.2 and every advertised target still need separate
core/adapter boot, client, Tool and Builder acceptance evidence. The existing
0.4.1 distribution and legacy API stay untouched in this batch.

## Current framework integration

The current development core, after the published 0.4.1 release, supplies API 0.3.0 alongside legacy 0.2.2 and discovers exact schema-2
universal packages at startup from `config/openallay/extensions/`. See
[`docs/universal-extensions.md`](../docs/universal-extensions.md) for the package,
support and lifetime contract. Current actual game-host acceptance covers 26.2 Fabric
and NeoForge with the same Java-8 fixture JAR. Native world-port and older/newer game
adapters remain separate implementation work; the SDK itself has no game-version link.
