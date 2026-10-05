# Rolling Minecraft support policy

OpenAllay develops one shared feature implementation against one mainline Minecraft version. Compatibility and forward adaptation reuse that implementation and the native-neutral Extension SDK.

## Three tracks

| Track | Role | Verification |
| --- | --- | --- |
| Mainline | Feature development and complete automated acceptance | Full shared feature tests, both supported loader packages and real packaged-client feature scenarios |
| Older compatibility | Keep the mainline features usable on supported older targets | Exact native compile/package checks, startup and key regression scenarios at actual API/loader boundaries; no copied feature engines |
| New-version adaptation | Begin compatibility work promptly after a new game release | Native API, mappings, Mixin and loader/dependency checks, then focused runtime acceptance; candidate support is separate from release acceptance |

The current development mainline remains **26.2**. **26.3** is the migration candidate, not the new default yet. This phase's older compatibility floor remains **1.20.1**.

## Rolling the mainline

Move to the next mainline after the intended loaders and required integrations are available and sufficiently stable, the native/package checks pass, and complete mainline client acceptance passes. Assess Fabric, Forge and NeoForge against their actual publications and APIs; naming a loader does not establish that its target release exists or is supported. Keep existing support claims tied to evidence.

When the mainline moves to 26.3, 26.2 enters the older compatibility track. A later 26.4 release enters the new-version adaptation track promptly. New-version compatibility does not itself move the mainline or modify immutable earlier releases.

## Shared API and artifacts

The Extension API and player-access semantics apply to every prepared supported host, not only the mainline. Keep one native-neutral SDK and universal Builder implementation. Native source families cover real API changes only.

Exact target profiles are verification inputs. Release one artifact per loader and verified binary-compatible Minecraft interval, not one artifact per minor version. Source reuse, compilation and a successful mainline client test do not establish same-JAR compatibility across an interval.

Run complete feature acceptance once against the mainline. Use targeted compatibility checks and key runtime regressions elsewhere. Add a target-specific test when a real native difference requires it, rather than duplicating the complete feature suite for every version.
