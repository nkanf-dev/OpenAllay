# Model metadata genuine Java8 verification

This verification compiles the complete five changed model value owners with
the eleven real accepted support owners, including `RecordMetadata`. It has
five explicit schemas and a sixteen-owner production compilation frontier.

Run from the repository root after canonical source application:

```text
python3 engine-core/src/modelMetadataJava8Test/verify.py \
  --source-root . --jdk17 JDK17 --jdk8 JDK8 \
  --gson AUTHENTIC_GSON_JAR \
  --junit-console AUTHENTIC_JUNIT_CONSOLE_1_11_4_JAR \
  --output FRESH_OUTPUT
```

Run once with each authentic published `gson-2.8.0.jar` and
`gson-2.13.2.jar`. The official Maven Central digest and byte length are pinned
in `source-contract.json`. Gson 2.8.0 publishes SHA1, not SHA256. Its exact
published SHA1 plus byte length is checked. The other two JARs publish SHA256.
No dependencies are downloaded by this runner.

The runner stages the original complete owners from immutable real Git blob
IDs. Those blob identities, original SHA256 values, and lengths are pinned.
There are no duplicated production owner source files in this verification
folder. The original-modern oracle and current actual8 implementations use the
same canonical Java8-compatible fixture and the same real existing
`ModelMetadataResolutionTest`. Each implementation must pass all three existing
test cases. Exact behavior vectors must match between the two fixture runs.

The accepted public `JavacTask` converter performs staged nested `Key`, then
outer `ModelMetadata` conversion. Expected output hashes and lengths are pinned
before execution. Recorded helper substitutions and the explicit blank-line
whitespace cleanup must reproduce the exact current canonical owner bytes.
The real Java8 compiler and VM are mandatory. Every generated actual8 class
must have major 52. The authentic JUnit 1.11.4 console must execute on actual8,
which checks its runtime compatibility rather than assuming it from a version.

The ten prior support source hashes remain unchanged except for the explicit
`ValueSchemas` source-custody update at commit
`a33bff5b38fde7737dda20898195d943e8493ba3`. That update adds `RecordMetadata`
as the eleventh required real support owner. The standalone fixture stays at
its already-applied canonical path under `engine-core/src/java8Test/java`.

The strict catalog parser and network resolver are outside this closed value
batch. `ModelContextResolution`, `ModelOutputResolution` and
`ModelImageCapabilityResolution` stay unchanged. No missing catalog stubs,
extracted-record sources or accepted proof JARs enter this compilation.
