# Current engine-only producer

This packet adds a native-free producer for the current shared engine. It selects the canonical `engine-core/src/main/java` and `engine-core/src/main/resources` exactly once. It contains no product Java implementation and no second engine fork.

## Install and invoke

Copy `native-builds/engine-only/` from this packet into the canonical checkout. Commit the engine logging fix and this build recipe before invoking it. The request must identify the actual checkout HEAD.

Use the repository's checked-in wrapper with the normal supported Gradle 9.5 host environment. The host and compiler are separate: Gradle uses its normal host JDK; the Java plugin selects the installed Java 21 compiler and `--release 17` for the engine. This recipe does not download or construct another wrapper, invoke a compiler directly, or configure Minecraft.

```sh
/absolute/current-checkout/gradlew \
  --project-dir /absolute/current-checkout/native-builds/engine-only \
  -PengineOnlyInputs=/absolute/engine-only-inputs.json \
  :engine-core:exportEngineOnlyClosure
```

The request has exactly these four fields:

```json
{
  "sourceRoot": "/absolute/current-checkout",
  "sourceRevision": "actual-40-character-current-Git-HEAD",
  "retainedDirectory": "/absolute/current-checkout/build/forge36-retained-shared",
  "builderJar": "/absolute/current-checkout/build/forge36-retained-builder/builder-isolated.jar"
}
```

`retainedDirectory` contains the role-named JAR files from immutable shared artifact `11395936631`. Its archive SHA-256 is `baa4f3ef51e19ac6dc8f7a486686cb5023334a7d06ae7126ad67fdec741023bd`. The coordinating workflow verifies this provider archive identity before extraction. `builderJar` is the separate accepted candidate from artifact `11395574649`, SHA-256 `21e5456ba30f24afca87ff5d6d3c4e373742f86b8f8c572e33333de047ab490c`. The request carries paths, not unchecked hash overrides. `retained-inputs.json` contains every exact accepted constituent coordinate and hash.

The old retained `engine.jar`, `slf4j.jar`, and old `builder.jar` are not inputs. They may remain in the immutable download directory but are not selected. The output directory must differ from the retained directory.

## Real project variants and cost

The standalone settings admit only `:engine-core`, `:extension-api`, `:runtime-rhino`, `:runtime-json`, and `:runtime-maven`.

- Only `:engine-core` has the standard `java-library` plugin and `compileJava` task.
- SDK and Rhino expose the real retained JAR through standard `apiElements` and `runtimeElements`, with the original project identity and version. SDK advertises Java 8; Rhino advertises Java 17.
- Rhino exposes its real Gson API dependency and Guava runtime dependency. Normal Gradle transitive module resolution supplies Guava's exact retained annotation/helper closure. JSON exposes its real Gson API dependency.
- JSON exposes the retained constituent proof as a compile-only API variant. Maven exposes the retained private artifact through `privateRuntime`. Neither project has source sets, Java compiler tasks, a fake task alias, or Shadow tasks.
- The engine's current production dependency declarations must exactly match the canonical `engine-core/build.gradle`, with mandatory SLF4J removed by the logging repair. Current Gson, commonmark, tables, jtokkit, SQLite and Rhino/Guava dependencies resolve normally. Every actual resolved artifact is hash-matched to its retained role.
- `mergeRetainedJson` and `mergeRetainedPrivateMaven` are owned `Sync` producers. Their outputs enter `sourceSets.main.output` with explicit `builtBy` edges. All constituent entries except the standalone manifest enter the engine unchanged. Private relocation has already happened in the retained Maven artifact.

One full current engine compilation is required. SDK, Rhino, JSON, private Maven, and Builder are never recompiled. No game, loader, adapters, native build, tests, publications, or bundled-Extension fetch task runs.

## Task order and output

`verifyRetainedInputs` checks every accepted retained hash and class header, actual checkout HEAD, committed canonical inputs, and unchanged source owners for SDK/Rhino/JSON/private Maven against retained source `cbcf5e66c81d11e4d219fa6cc8da04ab996c7ce0`. It records actual current engine source and producer file hashes. The old baseline is only the provenance of reused components.

`verifyProductionDependencies` scans the actual resolved compile and runtime component sets, every selected classpath JAR's entries, and native source ownership. It rejects native/loader coordinates and class prefixes, unclassified modules, incorrect artifact bytes, and duplicate canonical engine classes in native roots. `compileJava` depends on this guard. The two constituent producers run before `classes` and `jar`.

`exportEngineOnlyClosure` depends on the standard `jar` task and all guards. It checks every compiled engine class against the compiler's actual destination directory (major 61). It checks merged constituent entries byte-for-byte, rejects shaded SDK/Rhino/SLF4J owners, rejects native classes, checks all canonical resource bytes, and requires `LICENSE_OpenAllay`. Retained SDK classes must be at most major 52; retained Rhino, Builder and merged constituents must be at most major 61. Legal resources and canonical engine JSON/resources stay intact. JAR ordering is reproducible and source file timestamps are disabled.

The producer writes `<sourceRoot>/build/forge36-shared/` once. It refuses to overwrite an existing closure. It exports:

- `engine.jar`: freshly compiled current engine, product `0.4.3`, Java 17.
- 14 actual runtime dependency JARs, copied unchanged from the hash-verified resolved inputs.
- `builder.jar`, `json-proof.jar`, `maven-proof.jar`: exact retained accepted bytes.
- `closure-input.json` and `effective-runtime-resolution.json`: actual current engine source revision and the exact new 18-artifact closure, including constituent proof records.
- `raw-gradle-resolution.json`: actual engine source, retained archive/component provenance, Builder provenance, and actual selected inputs.
- `engine-source-inputs.json`: source/resource/build-input file hashes for the current engine compilation.
- `engine-entry-manifest.json`: exact new engine entry hashes and class majors, including preserved constituents/resources.

Keep the exported JARs and metadata before any later packing/acceptance stage. The coordinating workflow can publish the new shared output immediately after this producer succeeds, without waiting for later native/game checks. A later retry consumes that exact exported engine and retained closure rather than compiling unchanged components again.

## Source-packet scope

This packet was produced by source inspection only. No Gradle, Java, compiler, tests, game, CI, or binary downloads ran during packet preparation. Cached Gradle 9.5 API class descriptors were inspected read-only for `ProviderFactory.exec`, `ProjectDescriptor.buildFileName`, `SourceSetOutput.dir`, and `JavaCompile.destinationDirectory`. The coordinating agent owns repository integration and the one remote engine compilation. Prior failed acceptance jobs retain their original status.
