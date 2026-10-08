# Actual client syntax diagnostic frontier

The actual full 702-production-source diagnostic identified two client syntax owners: NativeDomainViewBinding's sealed declaration/private interface helper, and NativeModelInstaller's two text blocks. This build-only materializer pins the complete current source owners and emits exact complete pre/post bytes.

NativeDomainViewBinding keeps its public marker ABI and actual final Recipe variant/schema. Its sole private requireId helper moves into Recipe, its only caller, remaining private/static. Existing validation and raw-field value methods remain unchanged. Current consumers return or construct the concrete Recipe type; there is no marker-valued aggregate admission boundary to add a new runtime gate.

Installer text blocks use public JavacTask LiteralTree.getValue as the authoritative string values. The materializer escapes those actual values into Java8 literal concatenations, parses them again, and requires exact value equality. UTF8 hashes, byte sizes and UTF16 character counts are emitted. License text, Unicode, trailing newlines and all complete install/download/cancellation/atomic-file algorithms remain unchanged. No downloads or native/model/profile data are accessed.

Run remotely: `python3 scripts/materialize-client-native-syntax.py --project <project> --javac <tooling javac> --java <tooling java> --output <fresh external output>`.

Root reviews the actual source packet and runs affected real native modern tests. Later full-source Java8 diagnostics handle remaining installer Files.writeString and other post8 API/linkage boundaries. This syntax phase does not claim full client/engine/game Java8 acceptance. No local Java, source copies or target source edits were made.
