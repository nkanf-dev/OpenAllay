# Forge1122 concrete component candidate

Run remote only with repository wrapper:

`python3 -B scripts/build-forge1122-component.py --inputs /remote/component-inputs.json`

Request exact keys: `forge`, `launchwrapper`, `stockAsm`, `engine`, `native`, `sdk`,
`rhino`, `featureDependencies`, `output`, `java17`. Each archive record has only
absolute `path` and exact `sha256`; `featureDependencies` is a list of those records.
Use the retained Forge16 effective closure plus actual latest native reobf output.
Never rebuild engine/SDK/Rhino/Builder here. `java17` is executable absolute path.
Run the Gradle tool on JDK21 for repository Gradle9.5; Java compile toolchain17.

Normal Shadow9.6.1 relocates genuine Mixin0.8.5's ASM references and whole real
ASM9.6 dependency jars into `dev.openallay.internal.forge1122.asm`. Host stock
`org.objectweb.asm`5.2 remains present and unchanged. Genuine Mixin's own bootstrap,
services and transformer algorithms are not reimplemented. Exact dependency pins
are checked before shade. Signed upstream inputs stay intact; shaded outputs strip
invalid upstream signatures, preserve legal resources and merge normal services.

Three outputs: core-only feature jar (normal FMLCorePlugin and no ContainsFMLMod),
Java8 typed lifecycle facade jar, and private Mixin dependency jar. The facade
calls current native owner directly. CoreModManager normally ignores its core-only
archive for @Mod scan. Pure dependency Mixin filename is explicitly registered in
normal `getIgnoredMods` list. The complete class census uses genuine privateASM9.6
on every physical class entry, including class61/MR classes, records actual internal
names/class major/annotations, and proves stockASM5 rejects >52. No fabricated class,
wholefeature downgrade or customloader. Normal transformer exclusions prevent host
ASM5 rewriting own feature/Rhino/runtime dependency classes.

Install feature-core and lifecycle-facade in isolated mods/. Put privateMixin
explicitly on JVM classpath; retain stock Forge/FMLTweaker primary, append ordinary
`--tweakClass org.spongepowered.asm.launch.MixinTweaker` before bootstrap starts.
Core injectData registers current real mixin configs after genuine tweaker startup.
This avoids Forge's removed AppClassLoader cascading URL reflection.

Packaging runner/title integration follows successful native jar/census inputs.
This packet is a concrete remote build candidate, not an accepted product startup.
