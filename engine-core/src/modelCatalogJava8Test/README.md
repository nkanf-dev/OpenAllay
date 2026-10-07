# Model catalog Java8 closed frontier

This source contract stages complete original owners from immutable Git blobs. It does not depend on an external packet or recreate smaller record-only owners.

## Scope

- Ten changed production owners: the actual catalog, matcher, context/output/image resolution, OpenRouter resolver, CancellationSignal, ModelFailure, ModelEvent, and ModelUsage.
- Twenty-four explicit ordered schemas preserve direct constructor creation, field equality, zero-seeded hash, formatting, custom validation, and defensive-copy accessors.
- One additional modern-only production owner: `AgentEvent.java`. Its `ModelProgress` constructor calls `ModelEvent.requireKnown` before existing null checking and reasoning redaction.
- The modern compiler's sealed-interface restriction is removed for Java8. The marker interface stays an interface. All eleven actual event implementations remain final. Exact runtime admission at `AgentEvent.ModelProgress` rejects a foreign implementation with `IncompatibleClassChangeError`.
- The actual Java8 compilation includes 38 complete production owners. Existing accepted HTTP, credentials, JSON, helpers, metadata values, and thread utilities are real unchanged source, not stubs.
- Full engine and Forge Java8 acceptance are outside this batch.

## Remote verification

Run `verify.py --source-root <checkout> --jdk17 <home> --jdk8 <home> --gson <jar> --junit-console <jar> --output <fresh-directory>` on the remote acceptance host. Repeat for the accepted Gson 2.8.0 and 2.13.2 artifacts.

The runner verifies complete original-owner Git custody and unchanged support hashes. It authenticates all 24 record conversions with the accepted public `JavacTask` converter, then verifies only the recorded exact API substitutions and blank-line whitespace cleanup. It compiles the full frontier with JDK17 `--release 8` and genuine `javac8`, executes both Java8 products on genuine `java8`, and compares them with the same fixture built from complete original modern owners.

Fixtures exercise the actual sample and bundled resources, strict JSON rejection, matching and tie breaking, manual/trusted/builtin precedence, unknown limits, three-valued image evidence, constructor errors, defensive copies, event public constructor ABI, cancellation, and a loopback HTTP server through the actual accepted `JdkHttpTransport`. The loopback uses synthetic data and confirms redirects stay disabled. No external API or actual credentials are used.

The standalone suite also stages three affected existing tests with explicit test-only API/syntax lowering: `ModelOutputResolutionTest`, `ModelImageCapabilityResolutionTest`, and `CancellationSignalTest`. Production algorithms are unchanged. Config-loader tests remain complete modern Gradle tests because their real source closure is outside this Java8 subset.

## Modern affected acceptance

Run the six existing classes listed in `source-contract.json` plus `dev.openallay.agent.ModelEventAdmissionTest` through the normal remote engine-core test task. The admission test covers all eleven known variants, reasoning redaction, foreign implementation rejection before publication, and `observesUsage() == true` bypass behavior. `AgentEvent.java` remains a modern source owner in this batch; this runner does not claim to compile it on Java8.
