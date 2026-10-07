# Forge 1.12.2 Java8 port

Status: in progress. Target: Minecraft 1.12.2, Forge 14.23.5.2864, genuine Java8.
The artifact catalog keeps this target as a nonpublishing candidate while the
Java8 runtime and its release recipe are developed.

## Source and evidence custody

The successful Java17 source is archived at
`origin/legacy/forge1122-java17-20261007`, commit
`66f010368bbba90acd223fcd3b1eb325604fcbe9`.
The `mc/forge1122-continuation` source is preserved at commit
`c165d29a94c84fe7c594ef24ae98131ab6774b8c`.
Its native validation, provider identities, receipts and gameplay evidence keep
their original source and runtime identities.

The current release catalog contains 33 modern families plus Forge 1.16.5:
34 JARs, 26 Minecraft versions and 49 version/loader pairs. Forge 1.16.5 retains
its real FG6 producer, single-mod JAR format and complete custody checks.
The earlier 35-file catalog, including the Java17 Forge 1.12.2 ZIP, is historical.

## Retirement boundary

The public Python installer, Java17 premain package helpers and Forge 1.12.2 ZIP
producer/consumer branches have been retired. The shared feature engine and
Forge 1.12.2 native typed block operations remain source owners for the Java8 port.
The runtime-json work is separate from this packaging retirement.

Diagnostic component boot runners still use
`scripts/forge1122-runtime-prerequisite`. These sources are held temporarily for
that dependency, not used by the accepted release recipes. Retire the dependent
Java17 component runners and their dedicated prerequisites in a follow-up batch
when the Java8 runner is ready. Preserve the archived source and durable evidence
before removing those active-source paths.
