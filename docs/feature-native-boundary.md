# Feature engine and native game boundary

`engine-core` is a plain Java library. It owns the one shared Agent/model/session,
history, Skills, settings/domain/controller and detached protocol implementation.
Its production compile and runtime classpaths reject Minecraft, loader and LWJGL
classes. `verifySourceOwnership` rejects duplicate feature classes in native sources.

`common` now contains native game composition and bindings, not a second feature
engine. Loader modules compile only native source families. The same compiled
`engine-core` output is embedded once in each core mod, in its game mod layer. This
preserves one class identity and authorized Java access to native classes without
putting the feature engine in a parent library layer. The standalone Extension SDK
remains separately supplied. The Builder domain/Skills/JavaScript remain one native-
neutral Extension artifact.

Native APIs that remain stable are shared. Target overrides replace only changed
native bindings under `common/src/targets/<target>/`; duplicate source/resource
identities fail rather than silently win. The current 26.3 delta consists of input,
dialog, development-probe cursor and renderer-hook bindings. World/context/recipe
logic reuses the existing native implementation where actual APIs did not change.

One feature change must not require copies in several Minecraft target directories.
Put native capture/render/window/input/world/network calls at their owning adapter
boundary. Prefer existing native APIs and small typed bindings. Do not split stable
code into interfaces merely for symmetry.

The engine currently retains its existing Java25 runtime behavior. Older Java21/17
targets need one central runtime adaptation, not feature forks. In particular the
current Rhino dependency requires Java21; targeting Java17 bytecode alone cannot
make it run on Java17. This boundary does not by itself claim old-game support.

Compile the feature engine without a native game classpath:

```text
./gradlew :engine-core:compileJava :engine-core:jar
```

Select a native compile target with `-PminecraftTarget=26.2|26.3`. The default stays
26.2 until the 26.3 runtime acceptance is complete. Compilation, packaging, native
acceptance and release publication are separate facts. The published v0.4.1
artifacts remain unchanged.
