# Unrestricted Java access verification

Source update only. No product version change, tag or release publication is part of this task.

## Confirmed problem

The latest player export and subsequently retained request-15 trace show Rhino-specific failures: `.class` is not the pinned class wrapper property; guest reflection through `Class.forName` encounters a caller-sensitive restricted lookup; the ordinary wrapped Minecraft instance does not expose inherited `Object.getClass()`. No private-member access attempt or named-module rejection was confirmed in that player trace. A later JavaScript callback submitted to the server executor timed out, but the trace does not establish deadlock or pause as its cause. That separate threading issue is not solved by this member facade.

## Selected implementation

The existing unrestricted request installs `Java.type`, `classOf`, `inspect`, `get`, `set`, `invoke` and `construct`. It uses standard reflection, `trySetAccessible`, actual class/declaring loaders, exact parameter types, and the existing Rhino conversion/wrapping APIs. It does not change Rhino shared member caches, use Unsafe, attach an agent, inject dynamic Mixin classes or add another authorization setting. Ordinary script/scoped-Extension execution still has no `Java` surface.

`inspect` returns detached metadata and no automatic private field values. Exact descriptors disambiguate inherited/shadowed members and bridge return types. Final-field/module access follows the JVM's rules and errors retain useful target/module/package information. Calls run on the script worker; live Minecraft operations require their owning thread and cancellation does not promise interruption of every arbitrary native/blocking operation.

## Native focused verification

- First focused run: 168 tests, 2 failures. A character result was wrapped by Rhino as numeric code 98; the facade now returns actual `Character.toString()`. A void property expected omission, but the existing fork's JSON Map view records null; the test preserves the `typeof === undefined` assertion and now matches the existing JSON behavior. No normalizer behavior was changed.
- Final focused run: 171 tests, zero failures/errors/skips. It includes 55 Java-access cases plus the existing runtime/schema/Array/String/host/scoped-interface tests.
- The cases cover private/protected/package/inherited fields, exact static/instance calls and private constructors, primitive/null/Class/array/varargs conversions, long input precision, isolated class-loader identity and explicit declaring-loader selection, bridge returns, JDK closed modules and record/static-final refusal, target initializer/cause chains, cancellation and safe/unrestricted/safe isolation.
- The first complete common gate had 1,494 tests, one document line-wrap assertion failure, and six opt-in skips. The documented owning-thread fact was retained while moving the newline; no assertion or behavior was weakened. The full retry passed 1494 common tests with zero failures/errors and 6 opt-in skips; both loader builds passed.

## Limits

No fresh gameplay operation or paid-provider request was run by this task, and the current player client was not restarted or overwritten. Existing worlds/configuration/history/journals/exports were preserved. This is a reliable member-access surface, not a promise of universal module bypass, all final-field mutation, correct game-thread routing or runtime behavior rewriting. Research reports are preserved under `~/docs/OpenAllay/2026-10-02-java-access/`.

## Final source package checks

Distribution, Phase4, nested SQLite and published tokenizer resource/package checks passed.
All 118 offline script tests passed. Existing deprecation/Javadoc/native-access warnings
did not fail compilation. These files are local source builds, not replacement v0.3.0
release assets. No tag or published version was changed.

| Local source artifact | SHA-256 |
| --- | --- |
| fabric | `e95622dd74272f359abcdfb79705995bd021a0c724cf53c70bf68a17954ed7ea` |
| neoforge | `3422d6d00a1ce744077a4bc924bbe05f5f194008117552f43a5b2c590e8057e4` |
