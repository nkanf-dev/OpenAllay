# Canonical CORE attributed VAR tool

This build-only tool changes only inferred `var` declaration type tokens. It keeps the canonical source owners, class names, and feature algorithms. No Forge-specific engine or generated production source variant is created.

## Real compiler inputs

Run `:engine-core:exportCanonicalVarCompileClasspath` with the normal modern target and `-PcanonicalVarClasspathOutput=/external/fresh/core-classpath.json`. The task depends on the real current `compileJava` and private Maven merge. It exports all production source paths and the current production compile classpath, including actual runtime project classes and relocated dependencies. The Python tool rejects missing classpath entries and records exact artifact/class hashes. It does not use the test runtime classpath or an old engine JAR.

## Review-only materialization

Run `scripts/port-core-var-sources.py --project PROJECT --classpath-metadata METADATA --javac MODERN_JAVAC --java MODERN_JAVA --java8 REAL_JAVA8 --output FRESH_EXTERNAL_OUTPUT`.

The tool snapshots every current complete CORE production source outside the checkout. Public `JavacTask` parses all sources. It records only inferred local `VariableTree` sites before attribution changes parsed trees. The selected 15-owner request has exact source preimage hashes. Its lexical counts are inventory only. Actual selected parsed counts are the compiler result.

Public `Trees` attributes the complete source closure. `VariableElement.asType()` supplies each real inferred `TypeMirror`. The shared renderer supports primitives, arrays, named generic types, wildcards, named type parameters, and lexically visible named local classes. CORE rejects error, capture, anonymous, intersection, union, and inaccessible types. An unsupported selected declaration blocks the whole packet and writes exact rejection counts.

Only the AST-selected type token is replaced. Source offsets use UTF16 units. Comments, supplementary characters, and raw line endings are preserved. Unicode-escaped declaration tokens fail closed. A second all-source modern attribution verifies converted canonical bytes. The source checkout and dependency hashes must remain unchanged throughout the run.

The resulting `source-packet/` has one `source.patch`, raw `pre/` and `post/` files, and a SHA256/byte-count manifest. Root reviews and applies this canonical patch. Root then runs the full modern CORE tests and affected bridge/control fixtures.

## Genuine compiler fixture

The fixture uses only JDK dependencies. The original source is compiled with release17. Its explicit-type conversion is compiled with release8. Separate real modern and Java8 processes must produce the same expected output. Assertions cover exact generic/wildcard/array/local/nested types, overload dispatch, alias mutation, loops, UTF16/comments, and lambda exclusion. Real public type mirrors test capture, anonymous, error, intersection, and union rejection. An inaccessible inferred private class and escaped source token are rejected.

The accepted `RhinoVarPort` renderer is extracted into `AttributedVarTypes`. Its original no-option entry keeps the Rhino policy. Rhino delegates that method without changing its source selection, parser, or attribution. The existing `RhinoVarPortFixture` runs before the CORE fixture. Apply that small wrapper only after the active Rhino job has completed.

The production conversion addresses inferred local syntax only. Other modern language forms and JDK APIs remain separate Java8 port work.
