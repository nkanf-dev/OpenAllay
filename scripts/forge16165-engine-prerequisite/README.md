# Forge 1.16.5 engine prerequisite packer

Python 3.11 or later. Standard library only. These scripts operate on existing remote artifacts. They never run Gradle, download a JAR, start a game, or select a Java toolchain.

## Remote sequence

1. Root produces the current native-free closure once with the repository wrapper and configured toolchains. Use the whole `:engine-core:jar`, `:extension-api:jar`, and `:runtime-rhino:jar` outputs. Obtain `:runtime-json:jar` and `:runtime-maven:shadowJar` as small constituent proofs from that same build invocation; their classes are compared byte-for-byte with the dedicated engine JAR and never copied twice. Gradle's normal task dependencies produce the constituents. Do not use a whole modern game mod or extract `dev/openallay/**` by prefix.
2. Root emits the actual resolved runtime coordinate inventory as small JSON beside the archives. Include the effective engine artifact coordinate and Builder coordinate in this effective packaging closure. Include each Gradle-resolved external runtime coordinate, even host-supplied Gson/Guava/SLF4J and Guava helper artifacts. Source caches and Maven POM expectations do not establish the actual resolved set. The scanner compares the exact coordinate set against provided archives. Preserve the original Gradle resolution metadata separately as producer evidence.
3. Bind each archive path to its actual SHA256 in `closure-input.json`. Include every entry by scanning the entire JAR; no inclusion allowlist can silently skip classes or resources. Artifacts use the role/coordinate pairs in `ownership.json`. Annotation residual roles are included when actually resolved. An unknown dependency or version stops this gate for source review.
4. Run `scan` before probe compilation or client launch. It writes a complete entry inventory and either `READY` or `STOP`. Exit code 2 stops packaging. The report gives exact functional MR entries and hashes. Structural archive failures stop with the path/error on stderr and produce no accepted gate.
5. The separate probe owner runs ForgeGradle's normal `reobfJar` on the probe-only project. Bind both its pre-reobf JAR and reobfuscated JAR hashes in `probe-inputs.json`. The packer verifies both archives contain only the probe package, its normal manifest, and normal mods.toml. No closure class enters reobfuscation.
6. Run `pack` against the exact hash of the `READY` report. It rescans the closure, compares the complete report, and packages unchanged byte entries. It verifies every final entry against owners before creating the output and receipt. It never overwrites an existing output. Install the one fat mod only after this gate.

```text
python3 -B scripts/forge16165-engine-prerequisite/pack.py scan \
  --spec /remote/closure-input.json --report /remote/closure-scan.json

python3 -B scripts/forge16165-engine-prerequisite/pack.py pack \
  --spec /remote/closure-input.json \
  --gate /remote/closure-scan.json --gate-sha256 <actual-scan-SHA256> \
  --probes /remote/probe-inputs.json \
  --output /remote/openallay-engine-probe-fat.jar --receipt /remote/pack-receipt.json
```

### Exact input shapes

No placeholders are accepted as archive hashes. Paths are remote files. Hashes are lowercase 64-character SHA256 values. The following illustrates the shape of one entry, not a complete execution manifest:

```json
{
  "sourceRevision": "cbcf5e66c81d11e4d219fa6cc8da04ab996c7ce0",
  "resolution": {"path": "/remote/effective-runtime-resolution.json", "sha256": "<actual-sha256>"},
  "artifacts": [
    {"role": "engine", "coordinate": "dev.openallay:openallay-engine-core:0.4.3", "path": "/remote/openallay-engine-core-0.4.3.jar", "sha256": "<actual-sha256>"}
  ]
}
```

`sourceRevision` identifies the effective engine/SDK/Rhino producer input baseline, `cbcf5e66c81d11e4d219fa6cc8da04ab996c7ce0`. It does not identify the later workflow commit or GitHub run HEAD. Root keeps the actual producer/workflow HEAD in separate producer evidence. If the remote HEAD adds only task-specific export/probe/packer scripts, root records a Git diff proving the engine, SDK, runtime-json, runtime-maven, runtime-rhino and their build/configuration input scope is unchanged from that baseline. A changed producer scope requires source review and a new bound ownership input; do not relabel changed sources as the baseline.

`effective-runtime-resolution.json` has exactly `sourceRevision` and `runtimeCoordinates`. Here `runtimeCoordinates` is the assembled deployment closure, not the literal `engine-core.runtimeClasspath`. Root forms it from the actual Gradle runtime classpath coordinates plus deployment-owned engine and Builder coordinates. Engine and Builder do not occur as self-dependencies in the engine classpath. Constituent JSON/private-Maven proof archives remain outside this non-proof deployment coordinate list. Keep the unmodified actual Gradle classpath inventory and the explicit deployment additions in separate producer metadata, with the actual run HEAD and source-scope comparison. Every supplied non-proof artifact must occur in the assembled deployment set, and every set member must have a classified archive. Required roles: engine, sdk, rhino, builder, commonmark, tables, jtokkit, sqlite, json-proof, maven-proof; also gson, guava, slf4j, failureaccess, listenablefuture as host-supplied resolved archives. An annotation role is required if its coordinate occurs in the actual resolution. JetBrains compile-only annotations are not fabricated as runtime dependencies.

`probe-inputs.json` has exactly `input` and `reobf`. Each value has exactly `role`, `coordinate`, `path`, `sha256`. Roles are `probe-input` and `probe-reobf`; coordinate is `dev.openallay:forge36-engine-probe:0.4.3`.

## Entry policy

- Every input entry, including directories and excluded metadata, receives a disposition and SHA256. Every copied entry has a sole archive owner. Resources are copied by default. Class namespaces and primary source class lists are exact ownership checks, not prefix extraction rules.
- SDK and Builder classes are major52. Engine feature/runtime-json and current Rhino classes are major61. The privately relocated upstream Maven classes retain their own bytecode majors, bounded at61. All packed classes are non-preview and major<=61. Internal class names must match their logical ZIP paths, including MR logical names.
- No game Gson, Guava, SLF4J, loader, Minecraft, raw Maven, or other listed native replacement class is packed. Host-classpath dependency entries are individually inventoried as `host-supplied-not-packed`. Annotation residuals have separate approved namespace/coordinate owners. Actual host ABI is the genuine client's later boundary.
- Directory markers, source dependency manifests/signatures/JAR indexes, Maven POM metadata and module descriptors receive explicit omission records. Embedded proof manifests are omitted as proof metadata. Signatures cannot authenticate a rewritten container. Licensing has unique `META-INF/licenses/closure/<role>/<original-path>` paths and unchanged bytes. SQLite native/resource entries are never extracted selectively.
- Service files are merged in deterministic owner order while preserving each owner's provider order and first occurrence. Each union has all source file owners/hashes. Every provider must have a packed class owner. Conflicting classes/resources stop even if bytes match.
- Normal FAT mod manifest is generated with FMLModType=MOD and current0.4.3; it adds Multi-Release:true when the accepted SQLite MR entries are retained. Probe mods.toml bytes remain unchanged. There is one normal probe mod and exact Forge36.2.42/Minecraft1.16.5 client dependencies.
- Builder's original external community descriptor is parsed for exact structural identity and copied unchanged. Its existing external `schemaVersion=2` is read, not invented as an internal format gate. Public support/range decisions remain the unchanged engine decoder and probe descriptor stage. Builder contributes no features in this prerequisite. Extract the verified original nested Builder only remotely if using accepted release receipts; do not download the large product artifact locally.

## MR policy and scanner proof

The accepted exception applies only to SQLite3.50.3.0 archive SHA256 `a3f53a2aa15ae9425a9e793bbe9c8e5288febeb4b65ef5c1a4e80d4c2045cf08`, its original Multi-Release:true manifest, and the three exact major53 functional MR entries hashed in `ownership.json`. Their original paths and bytes are retained. The module-info entry alone is omitted for ordinary classpath packaging. No other functional MR entry can pass by class major alone; it stops with the exact entry hash.

Exact Forge36.2.42 release source at `54233da14c9ae556d596c3df41a97f367b8d6e5d`:

- `AbstractJarFileLocator.scanFile` uses `Files.find` on all `.class` paths and passes them to the consumer.
- `Scanner.fileVisitor` opens each class stream, creates `ClassReader` and `ModClassVisitor`, calls `cr.accept(mcv, 0)`, and stores class/annotation data. It does not resolve the superclass or call Class.forName for each scanned type.
- `ModClassVisitor` uses ASM9. Its `visit` creates ASM `Type` objects for class/super/interface names. `buildData` stores these metadata values. Optional Graal nativeimage interfaces do not need resolution during this scan.
- `FMLJavaModLanguageProvider.getFileVisitor` selects only annotations equal to `MODANNOTATION` to construct mod targets. Its later `loadMod` Class.forName calls concern FMLModContainer and selected mod targets, not every inventoried class.

The three hash-pinned SQLite nativeimage classes contain Graal names but no Forge @Mod descriptor in their constant pools. This permits retaining their real MR behavior for the genuine prerequisite rather than deleting it. Source evidence and entry hashes are in the packet's `evidence/`. Root still runs the real FML scanner/client once after packaging; this policy does not claim a host ABI result.

## Focused source checks

`python3 -B scripts/forge16165-engine-prerequisite/test_pack.py` uses only tiny synthetic archives. It checks path traversal/control/NUL/duplicate/symlink/bounds, archive and internal-class hashes, exact MR exception and unknown-MR STOP, probe namespace/metadata, full scan/pack determinism, per-entry collisions, service union provenance, licensing, SQLite native resource copying, and embedded proof mismatch. Temporary mocks live beside the test script and are removed on completion.
