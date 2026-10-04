# Minecraft 26.2 native world adapter

This core-owned module implements the fixed `MinecraftWorldAccess` and `WorldSession` SDK ports.
It is not a second Builder domain or an Extension fork.

## Boundary

- Public factory: `dev.openallay.adapter.minecraft.v26_2.world.Minecraft26WorldAccess`.
- Native implementation: package-private `NativeWorldSession`, `NativeBlockCodec`,
  `NativeWorldIdentity`, `OwnerThreadBridge`, and `SessionIdentity`.
- Production depends on the host SDK and the Minecraft 26.2 NeoForm compile artifact.
  Gson, Brigadier, Mojang codecs, and native NBT are game libraries. The adapter does not
  bundle the SDK or another Gson copy. It does not depend on `common`, old core types,
  the Builder Extension, or its storage/domain types.
- Java 25 compiles the native module. The separately built public SDK remains Java 8.
- The root project owns settings inclusion, loader packaging, and factory wiring.
  This module defines no run profiles, world creation, or loader integration.

## Invocation and owner protocol

Factory construction captures no game state. `open(invocation)` checks the active player
invocation, then queues capture on the client owner and integrated-server owner.
It binds the exact connection, client player, client level, integrated server,
server player, server level, actor UUID, and player dimension. A reconnect with the
same UUID is not the same session. A remote server has no authoritative local backend.

A non-owner invocation worker calls `session.call(javaOwnedAction)` for primitive
position reads, state preview/transform, terrain, writes, repairs, and neighbor work.
Game owner threads never wait for this bridge. Every action rechecks both owner
bindings. Individual native methods also require an admitted server-owner action.
`context`, `dimension`, `artifacts`, and world identity reads dispatch their own
owner action from the worker, including cache-hit identity checks.

`openallay_builder:world_write` is checked before writes, repair hooks, physics,
and native saved-identity creation. Capture and read-only access do not create
world identity. `existingWorldId()` does not create or dirty native SavedData.
Invocation cancellation calls the bridge's idempotent close. Queued work is revoked.
A started bounded commit finishes native replacement and actual readback before
its worker resumes, including interruption/cancellation accounting.

Close is revocation, not a world mutation. No world or block rollback is implicit.
Owner-action-local block-entity proof masks and codec palettes are cleared after
each action. The cancellation callback retains revocation state, not the captured
native session. Domain journal and template resource cleanup remains domain-owned.

## Preserved native behavior

The port uses the original Minecraft 26.2 methods, not fake game types or an offline
region codec. It preserves actual registry defaults and property validation,
opaque full block-entity SNBT through `TagValueInput` and `TagValueOutput`, detached
block-entity preview, native mirror/rotation restrictions, and flags `18` placement.
Native replacement hooks, listener/ticker removal, chunk dirtying, client updates,
actual readback, changed-write/failure accounting, neighbor repair, and comparator
updates remain native calls.

Loaded-chunk/build-height/world-border checks prohibit implicit generation.
Canonical-air proofs require one inclusive clipped section and exact canonical
air, with live/pending block-entity occupancy. Terrain state returns canonical JSON
without block-entity content. Terrain height uses a primed native heightmap only
when all actual native air states permit that shortcut. The deadline is a 4 ms
owner scheduling quantum, not a region-size or volume cap.

The SavedData namespace/path remains `openallay_builder:world_identity`, with the
original native UUID codec. Artifacts remain `config/openallay-builder`, so existing
template and journal paths do not move. No migration or internal schema version
was added.

## Context material facts

Original `topology`, `dimension`, `minY`, `maxY`, `version`, `dataVersion`, and `player`
fields remain unchanged. `materialPalette` is additive. It maps the fixed 47 preset
roles to canonical actual state JSON **objects**, not JSON strings. The checked-in
`material-palette-inputs.json` is copied directly from the universal preset consumer's
inventory. Owner-side strict native decode verifies each registered ID and every
specified property. Native encode returns all default properties. This does not
silently omit `waterlogged`, substitute unknown materials, or apply modern IDs to
another game version. An unavailable role fails with `material_unavailable`.
Custom caller materials remain exact IDs/properties and still use native validation.

## Verification handoff

The source worker did not run a compiler, Gradle, game, network call, dependency
resolution, or native acceptance. The focused JUnit sources test the real owner
bridge with JDK executors, fixed SDK lifecycle, exact object identity, source
boundaries, and compiled constant pools. They define no fake Minecraft classes.
A test is not a game-native acceptance claim.

After root-owned inclusion, the coordinator must run the checked-in wrapper's
adapter test task, inspect the actual Minecraft/Gson compile classpath, and verify
both loader integrations. Native acceptance must then cover live read/preview,
block-entity placement/readback/undo, cancellation, reconnect/dimension changes,
loaded chunks, canonical-air proofs, terrain, native repair, and physics.

## Current verified boundary

The adapter is loaded lazily through the core's `MinecraftWorldAccess` interface.
Current development Fabric and NeoForge 26.2 clients have exercised the same Java-8
SDK fixture through normal universal discovery and actual player-scoped RunJS calls.
Default-off native writes and identity creation were denied; a request frozen before
a later grant stayed denied. A fresh granted request wrote and restored stone and a
real chest containing three diamonds, with independent owner-thread readback and exact
opaque block-entity SNBT equality. All 47 current material roles came from strict native
registry/property validation. Close and worker scope release passed.

These proofs used an explicit no-legacy-Builder fixture package, not the production
Builder business implementation. Mid-action cancellation, connection replacement,
dimension switch, remote topology and old/new game versions remain separate native
acceptance work. The 18 module tests cover detached ownership and architecture; they do
not substitute for those runtime cases.

NeoForge native adapter classes/resources are included in the core mod JAR's game
classloader layer. The source/build module remains separate. Putting this plain native
adapter into a library JarJar layer cannot link Minecraft classes and is not supported.
The public SDK remains a separate shared library, and Extension payloads remain native-free.
