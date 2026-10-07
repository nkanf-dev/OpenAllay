# Native binary release artifacts

## Release 0.4.4 files

OpenAllay 0.4.4 adds stock Forge **1.16.5**. Its catalog contains **34 JARs**
for GitHub and Modrinth, covering **26 exact Minecraft versions and 49
version/loader pairs**, from 1.16.5 through 26.3.
Choose the file for your exact version and loader. Fabric requires matching
Fabric API.

| Exact Minecraft versions | Loader | Java | Download files |
| --- | --- | --- | --- |
| `1.16.5` | Forge 36.2.42 | 17 | `openallay-forge-1.16.5-0.4.4.jar` |
| `1.18.2` | Forge | 17 | `openallay-forge-1.18.2-0.4.4.jar` |
| `1.19.2` | Forge | 17 | `openallay-forge-1.19.2-0.4.4.jar` |
| `1.20.1` | Fabric + NeoForge | 17 | `openallay-fabric-1.20.1-0.4.4.jar`<br>`openallay-neoforge-1.20.1-0.4.4.jar` |
| `1.20.2` | Fabric + NeoForge | 17 | `openallay-fabric-1.20.2-0.4.4.jar`<br>`openallay-neoforge-1.20.2-0.4.4.jar` |
| `1.20.3`, `1.20.4` | Fabric | 17 | `openallay-fabric-1.20.3-through-1.20.4-0.4.4.jar` |
| `1.20.3` | NeoForge | 17 | `openallay-neoforge-1.20.3-0.4.4.jar` |
| `1.20.4` | NeoForge | 17 | `openallay-neoforge-1.20.4-0.4.4.jar` |
| `1.20.5`, `1.20.6` | Fabric + NeoForge | 21 | `openallay-fabric-1.20.5-through-1.20.6-0.4.4.jar`<br>`openallay-neoforge-1.20.5-through-1.20.6-0.4.4.jar` |
| `1.21`, `1.21.1` | Fabric + NeoForge | 21 | `openallay-fabric-1.21-through-1.21.1-0.4.4.jar`<br>`openallay-neoforge-1.21-through-1.21.1-0.4.4.jar` |
| `1.21.2`, `1.21.3` | Fabric + NeoForge | 21 | `openallay-fabric-1.21.2-through-1.21.3-0.4.4.jar`<br>`openallay-neoforge-1.21.2-through-1.21.3-0.4.4.jar` |
| `1.21.4` | Fabric + NeoForge | 21 | `openallay-fabric-1.21.4-0.4.4.jar`<br>`openallay-neoforge-1.21.4-0.4.4.jar` |
| `1.21.5` | Fabric + NeoForge | 21 | `openallay-fabric-1.21.5-0.4.4.jar`<br>`openallay-neoforge-1.21.5-0.4.4.jar` |
| `1.21.6` | Fabric + NeoForge | 21 | `openallay-fabric-1.21.6-0.4.4.jar`<br>`openallay-neoforge-1.21.6-0.4.4.jar` |
| `1.21.7`, `1.21.8` | Fabric + NeoForge | 21 | `openallay-fabric-1.21.7-through-1.21.8-0.4.4.jar`<br>`openallay-neoforge-1.21.7-through-1.21.8-0.4.4.jar` |
| `1.21.9`, `1.21.10` | Fabric + NeoForge | 21 | `openallay-fabric-1.21.9-through-1.21.10-0.4.4.jar`<br>`openallay-neoforge-1.21.9-through-1.21.10-0.4.4.jar` |
| `1.21.11` | Fabric + NeoForge | 21 | `openallay-fabric-1.21.11-0.4.4.jar`<br>`openallay-neoforge-1.21.11-0.4.4.jar` |
| `26.1`, `26.1.1`, `26.1.2` | Fabric + NeoForge | 25 | `openallay-fabric-26.1-through-26.1.2-0.4.4.jar`<br>`openallay-neoforge-26.1-through-26.1.2-0.4.4.jar` |
| `26.2` | Fabric + NeoForge | 25 | `openallay-fabric-26.2-0.4.4.jar`<br>`openallay-neoforge-26.2-0.4.4.jar` |
| `26.3` | Fabric + NeoForge | 25 | `openallay-fabric-26.3-0.4.4.jar`<br>`openallay-neoforge-26.3-0.4.4.jar` |


### Install the selected file

- **Forge 1.16.5:** use Forge **36.2.42**, select **Java 17**, and put the matching
  OpenAllay JAR in `mods/`.
- **Other JAR downloads:** install the selected JAR in `mods/` and use the Java
  version listed above. Some files cover multiple exact Minecraft versions.

The [Forge runtime guide](forge-runtime-installation.md) lists the Forge 1.16.5
installation steps. The [Forge 1.12.2 Java8 port](verification/forge1122-java8-port.md)
is an in-progress, nonpublishing candidate. Minecraft **26.2 / Java 25** remains the development mainline.
Public **Extension API 0.4.0**, bundled **Builder 0.4.0**, and **Skill API 0.2**
are independent of the product patch version.

## Release 0.4.3 files

OpenAllay 0.4.3 adds Forge 1.18.2 and 1.19.2 and includes the model setup,
text input and HUD fixes. The release contains **33 JARs** covering **25 Minecraft
versions** and **48 version/loader pairs**. Choose the exact version and loader.
Fabric requires the matching Fabric API.

| Exact Minecraft versions | Loader | Java | Published JARs |
| --- | --- | --- | --- |
| `1.18.2` | Forge | 17 | `openallay-forge-1.18.2-0.4.3.jar` |
| `1.19.2` | Forge | 17 | `openallay-forge-1.19.2-0.4.3.jar` |
| `1.20.1` | Fabric + NeoForge | 17 | `openallay-fabric-1.20.1-0.4.3.jar`<br>`openallay-neoforge-1.20.1-0.4.3.jar` |
| `1.20.2` | Fabric + NeoForge | 17 | `openallay-fabric-1.20.2-0.4.3.jar`<br>`openallay-neoforge-1.20.2-0.4.3.jar` |
| `1.20.3`, `1.20.4` | Fabric | 17 | `openallay-fabric-1.20.3-through-1.20.4-0.4.3.jar` |
| `1.20.3` | NeoForge | 17 | `openallay-neoforge-1.20.3-0.4.3.jar` |
| `1.20.4` | NeoForge | 17 | `openallay-neoforge-1.20.4-0.4.3.jar` |
| `1.20.5`, `1.20.6` | Fabric + NeoForge | 21 | `openallay-fabric-1.20.5-through-1.20.6-0.4.3.jar`<br>`openallay-neoforge-1.20.5-through-1.20.6-0.4.3.jar` |
| `1.21`, `1.21.1` | Fabric + NeoForge | 21 | `openallay-fabric-1.21-through-1.21.1-0.4.3.jar`<br>`openallay-neoforge-1.21-through-1.21.1-0.4.3.jar` |
| `1.21.2`, `1.21.3` | Fabric + NeoForge | 21 | `openallay-fabric-1.21.2-through-1.21.3-0.4.3.jar`<br>`openallay-neoforge-1.21.2-through-1.21.3-0.4.3.jar` |
| `1.21.4` | Fabric + NeoForge | 21 | `openallay-fabric-1.21.4-0.4.3.jar`<br>`openallay-neoforge-1.21.4-0.4.3.jar` |
| `1.21.5` | Fabric + NeoForge | 21 | `openallay-fabric-1.21.5-0.4.3.jar`<br>`openallay-neoforge-1.21.5-0.4.3.jar` |
| `1.21.6` | Fabric + NeoForge | 21 | `openallay-fabric-1.21.6-0.4.3.jar`<br>`openallay-neoforge-1.21.6-0.4.3.jar` |
| `1.21.7`, `1.21.8` | Fabric + NeoForge | 21 | `openallay-fabric-1.21.7-through-1.21.8-0.4.3.jar`<br>`openallay-neoforge-1.21.7-through-1.21.8-0.4.3.jar` |
| `1.21.9`, `1.21.10` | Fabric + NeoForge | 21 | `openallay-fabric-1.21.9-through-1.21.10-0.4.3.jar`<br>`openallay-neoforge-1.21.9-through-1.21.10-0.4.3.jar` |
| `1.21.11` | Fabric + NeoForge | 21 | `openallay-fabric-1.21.11-0.4.3.jar`<br>`openallay-neoforge-1.21.11-0.4.3.jar` |
| `26.1`, `26.1.1`, `26.1.2` | Fabric + NeoForge | 25 | `openallay-fabric-26.1-through-26.1.2-0.4.3.jar`<br>`openallay-neoforge-26.1-through-26.1.2-0.4.3.jar` |
| `26.2` | Fabric + NeoForge | 25 | `openallay-fabric-26.2-0.4.3.jar`<br>`openallay-neoforge-26.2-0.4.3.jar` |
| `26.3` | Fabric + NeoForge | 25 | `openallay-fabric-26.3-0.4.3.jar`<br>`openallay-neoforge-26.3-0.4.3.jar` |

The 0.4.3 files are rebuilt from the current source. Each build records its source,
loader, target, artifact hash and package checks. Previous native execution
records retain their original source and JAR identities. The 0.4.2 release and
its original-byte publication records remain unchanged below.

## Published 0.4.2 files

[OpenAllay 0.4.2](https://github.com/nkanf-dev/OpenAllay/releases/tag/v0.4.2)
publishes **31 JARs** covering **23 Minecraft versions** and **46 version/loader
pairs**. Choose the file for your exact Minecraft version and loader. Fabric
requires the matching Fabric API.

Minecraft 26.2 / Java 25 is the development mainline. Release files also cover
older versions and 26.3. Their native dependency and Java pins live in
`gradle/minecraft-targets/<target>.properties`.

| Exact Minecraft versions | Loader | Java | Published JARs |
| --- | --- | --- | --- |
| `1.20.1` | Fabric + NeoForge | 17 | `openallay-fabric-1.20.1-0.4.2.jar`<br>`openallay-neoforge-1.20.1-0.4.2.jar` |
| `1.20.2` | Fabric + NeoForge | 17 | `openallay-fabric-1.20.2-0.4.2.jar`<br>`openallay-neoforge-1.20.2-0.4.2.jar` |
| `1.20.3`, `1.20.4` | Fabric | 17 | `openallay-fabric-1.20.3-through-1.20.4-0.4.2.jar` |
| `1.20.3` | NeoForge | 17 | `openallay-neoforge-1.20.3-0.4.2.jar` |
| `1.20.4` | NeoForge | 17 | `openallay-neoforge-1.20.4-0.4.2.jar` |
| `1.20.5`, `1.20.6` | Fabric + NeoForge | 21 | `openallay-fabric-1.20.5-through-1.20.6-0.4.2.jar`<br>`openallay-neoforge-1.20.5-through-1.20.6-0.4.2.jar` |
| `1.21`, `1.21.1` | Fabric + NeoForge | 21 | `openallay-fabric-1.21-through-1.21.1-0.4.2.jar`<br>`openallay-neoforge-1.21-through-1.21.1-0.4.2.jar` |
| `1.21.2`, `1.21.3` | Fabric + NeoForge | 21 | `openallay-fabric-1.21.2-through-1.21.3-0.4.2.jar`<br>`openallay-neoforge-1.21.2-through-1.21.3-0.4.2.jar` |
| `1.21.4` | Fabric + NeoForge | 21 | `openallay-fabric-1.21.4-0.4.2.jar`<br>`openallay-neoforge-1.21.4-0.4.2.jar` |
| `1.21.5` | Fabric + NeoForge | 21 | `openallay-fabric-1.21.5-0.4.2.jar`<br>`openallay-neoforge-1.21.5-0.4.2.jar` |
| `1.21.6` | Fabric + NeoForge | 21 | `openallay-fabric-1.21.6-0.4.2.jar`<br>`openallay-neoforge-1.21.6-0.4.2.jar` |
| `1.21.7`, `1.21.8` | Fabric + NeoForge | 21 | `openallay-fabric-1.21.7-through-1.21.8-0.4.2.jar`<br>`openallay-neoforge-1.21.7-through-1.21.8-0.4.2.jar` |
| `1.21.9`, `1.21.10` | Fabric + NeoForge | 21 | `openallay-fabric-1.21.9-through-1.21.10-0.4.2.jar`<br>`openallay-neoforge-1.21.9-through-1.21.10-0.4.2.jar` |
| `1.21.11` | Fabric + NeoForge | 21 | `openallay-fabric-1.21.11-0.4.2.jar`<br>`openallay-neoforge-1.21.11-0.4.2.jar` |
| `26.1`, `26.1.1`, `26.1.2` | Fabric + NeoForge | 25 | `openallay-fabric-26.1-through-26.1.2-0.4.2.jar`<br>`openallay-neoforge-26.1-through-26.1.2-0.4.2.jar` |
| `26.2` | Fabric + NeoForge | 25 | `openallay-fabric-26.2-0.4.2.jar`<br>`openallay-neoforge-26.2-0.4.2.jar` |
| `26.3` | Fabric + NeoForge | 25 | `openallay-fabric-26.3-0.4.2.jar`<br>`openallay-neoforge-26.3-0.4.2.jar` |

Fabric 1.20.3–1.20.4 uses one verified file. NeoForge uses separate files for
1.20.3 and 1.20.4 because their loader SPI has a binary API boundary. The other
multi-version files in the table passed actual game checks with the same JAR
on every listed version.

`gradle/minecraft-artifacts.json` is the source catalog for these accepted
families. Each family owns its loader, build target, supported targets and
filename. `targetOrder` lists every exact accepted or candidate version. The catalog rejects
overlapping families, gaps, unsafe names and duplicate identities. Pending
candidates remain separate from the published families.

## Source builds

The feature engine, public Extension SDK and universal Builder remain shared.
`adapters/minecraft` and the loader modules select their native source bindings
through the ordered roots in `gradle/minecraft-source-family.gradle`. Each
selected profile supplies the actual Minecraft, loader and Java toolchain pins.

For example, resolve a published file from the repository root:

```bash
python3 -B scripts/minecraft-artifacts.py validate
python3 -B scripts/minecraft-artifacts.py resolve --loader fabric --target 1.20.5 --version 0.4.4
python3 -B scripts/minecraft-artifacts.py resolve --loader neoforge --target 26.2 --version 0.4.4
```

Resolution prints the exact family and supported versions, the filename and the
Minecraft predicate used by that file. Development still defaults to 26.2.

## Original-byte publication

The 0.4.2 release publishes the original JARs that passed acceptance. The release
job resolves `distribution/accepted-release-artifacts.inputs.json` against the
original GitHub artifact IDs, archive digests, source commits, jobs and attempts.
It downloads each original archive once, stages unchanged JARs, and retains the
original runtime summaries and official runtime profiles.

The publication helper binds those proofs to the final release paths. Every
accepted target must have a successful actual runtime summary for the selected
JAR hash. Minecraft 26.2 also retains the complete functional evidence
composition, including Builder's original-world reload and persistence checks.
Different file families keep their original build-source identities; the release
tag identifies the release decision.

The job generates `SHA256SUMS`, `accepted-originals.json` and
`release-publication-records.json`, then sends the same files to the existing
GitHub and Modrinth publishers. It runs no compiler or game acceptance matrix.
Future changes only rebuild or recheck affected files and native boundaries.

## Retained per-file receipts

Each final-path receipt contains `familyId`, `loader`, `artifactPath`,
`artifactSha256` and one `runs` entry for every exact supported target. Each run
binds its loader, original JAR hash and retained evidence hash. Original archive
and runtime evidence stay intact alongside the publication selection.

Published v0.4.1 tags and files remain unchanged. Product 0.4.2, public Extension
API 0.4.0 and bundled Builder 0.4.0 keep independent version coordinates.
