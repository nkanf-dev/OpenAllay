# Native boundaries and feature cost

OpenAllay keeps feature behavior in one Minecraft-free engine and ships one native-neutral Builder Extension. Loader integrations and selected native source families bind real game APIs.

## Ownership

- Model/provider logic, Tools, history, UI state, layout and Extension algorithms have one implementation.
- Native bindings own game objects, render callbacks, native codecs, world owner threads and loader packet registration.
- A source family covers an actual API shape. Multiple Minecraft targets can select it. A target profile records exact dependency and Java pins; it is not a runtime-support declaration.
- The public Extension SDK contains project-owned values, not Minecraft or loader types.

## Minimum adaptation surface

A feature composed from existing capabilities does not edit target directories. A new composite drawing operation belongs in the shared `GuideGraphics` facade. Client bridge request/image lifetime behavior belongs in the shared bridge session, not both loaders.

A genuinely new native primitive needs a typed operation and only the native families whose contracts differ. Group related native facts under one clear capability; do not create one wrapper per field. Do not copy Screens, capture loops, world sessions or Builder algorithms into version directories. Do not hide native differences with fake Minecraft types or general reflection dispatch.

## Approach

Architectury and MultiLoader patterns provide useful typed loader seams but their common modules remain Minecraft-dependent. Controlify demonstrates one-tree version reuse; version conditionals are useful only when confined to genuine native differences. REI demonstrates shared runtime behavior and the cost of native types in public APIs. OpenAllay uses these patterns without replacing the build framework or leaking game types into its engine.

Source adaptation proceeds newest to oldest in coherent API-family batches. Compilation, package parity and native-game acceptance are recorded separately. Preserve a passing default-target CI while expanding the source matrix. This stage stops at Minecraft 1.20.1.

## Release artifacts

Release one JAR per loader and verified binary-compatible Minecraft interval. Exact target profiles remain available for checking the same artifact bytes. A source family is only a source-reuse fact; it does not by itself prove that mappings, Mixin descriptors, native class symbols and loader interfaces permit one binary. Prefer the common API retained across an interval and split only at a real incompatibility. Do not bundle multiple version implementations or add reflective game-version dispatch to widen metadata claims.
