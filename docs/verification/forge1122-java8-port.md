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

The dedicated Java17 component packaging, instrumentation prerequisites and
boot/refresh/repair drivers have been removed from active source, together with
their workflow jobs and dispatch options. Their source remains in the archived
Java17 branch above. The genuine FG3 census, native source selection and reobf
tooling remain for native adaptation. Shared Forge16 producers read the unchanged
universal Builder provider pin from `distribution/builder-candidate-provider.json`.
