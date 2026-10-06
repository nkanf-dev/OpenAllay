# Stock Forge1.12.2 Java17 runtime prerequisite

This packet selects mainstream Forge `1.12.2-14.23.5.2864`.
The public Forge promotions response selects `14.23.5.2864` as latest and
`14.23.5.2859` as recommended on 2026-10-06.

## First remote run

```sh
xvfb-run -a -s '-screen 0 1280x960x24' build/stock1122-tools/bin/python -B \
  scripts/forge1122-runtime-prerequisite/stock-forge1122-prerequisite.py \
  --repo "$PWD" --java "$JAVA_HOME/bin/java" --java-release '17.0.18+8' \
  --minecraft-root "$PWD/build/e2e/runtime/forge1122-stock/minecraft" \
  --output "$PWD/build/e2e/forge1122-stock"
```

Dispatch `.github/workflows/minecraft-native.yml` with `mode=stock1122`.
The runner requires Linux x86_64, Temurin17.0.18+8, a fresh isolated root,
an owned display, and at least10GiB free. It uses the checked-in provisioning
and launch helpers after exact SHA256 verification.

## Ownership

- The official installer owns the Forge profile and bundled Forge bytes.
- Official Minecraft metadata owns the original client, libraries, assets,
  and native classifiers. Original client checksum is checked after install.
- LaunchWrapper1.12 and FMLTweaker own class loading and transformation.
- Forge's declared runtime ASM remains `org.ow2.asm:asm-debug-all:5.2`.
- The inherited vanilla Java8 declaration stays unchanged. Selected process
  Java is17. No game/engine Gradle build is part of this prerequisite.
- Empty `mods/` and stock `options.txt` are the only game-directory setup.
- The runner stops only its own process group after two captures at45/90seconds.

## Diagnostics and next boundary

The first command launches raw `net.minecraft.launchwrapper.Launch` without
an agent, bootstrap, custom classloader, library substitution, fake game alias,
engine, Rhino, SDK, Builder, world, or silent stub. It retains the exact command,
all classpath hashes, class loading log, client log, crash reports, and full-frame
captures. `receipt.json` distinguishes early process exit from captured frames.
A title confirmation follows inspection of the real captured client frame.

`launch.json` inventories every physical class entry. It reports class-major
counts, classes above major52, and every `META-INF/versions/` entry. This makes
the ASM5.2/class61/MR scan boundary explicit without changing library bytes.
Java17 engine and Rhino remain outside this initial stock probe. Their immutable
artifacts can be reused later. Native, SDK, and Builder stay Java8 targets.

If the raw command fails, inspect its first stack trace before changing source.
Expected hypotheses include the LaunchWrapper AppClassLoader-to-URLClassLoader
cast and Java module access. They are not recorded as observed blockers until
the remote trace establishes them. Any later launching repair must preserve
LaunchWrapper/FML ownership and target the demonstrated blocker. This packet
contains no preemptive fix or engine downgrade.

## Offline checks

```sh
python3 -B scripts/forge1122-runtime-prerequisite/stock-forge1122-prerequisite.py --metadata-only --repo "$PWD"
python3 -B scripts/forge1122-runtime-prerequisite/test-stock-boundaries.py
```

These commands parse pinned metadata and test the byte-inventory boundary.
They do not launch Java, download game data, or run Gradle.
