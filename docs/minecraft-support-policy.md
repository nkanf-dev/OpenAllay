# Rolling Minecraft support policy

OpenAllay develops one shared feature implementation against one mainline Minecraft version. Compatibility and forward adaptation reuse that implementation and the native-neutral Extension SDK.

## Three tracks

| Track | Role | Verification |
| --- | --- | --- |
| Mainline | Feature development and complete automated acceptance | Full shared feature tests, both supported loader packages and real packaged-client feature scenarios |
| Older compatibility | Keep the mainline features usable on supported older targets | Exact native compile/package checks, startup and key regression scenarios at actual API/loader boundaries; no copied feature engines |
| New-version adaptation | Begin compatibility work promptly after a new game release | Native API, mappings, Mixin and loader/dependency checks, then focused runtime acceptance; candidate support is separate from release acceptance |

The development mainline remains **26.2**. Release **0.4.4** covers **27 exact
Minecraft versions** from **1.12.2 through 26.3**: Forge on 1.12.2, 1.16.5,
1.18.2, and 1.19.2; Fabric and NeoForge on 1.20.1 through 26.3. The release
[artifact table](native-binary-artifacts.md#release-044-files) lists every exact
version and loader. Forge 1.12.2 uses the GitHub runtime ZIP and its new-profile
installer; the other targets use JAR downloads. Native compatibility includes
26.3 while feature development stays on 26.2.

## Rolling the mainline

Move to the next mainline after the intended loaders and required integrations are available and sufficiently stable, the native/package checks pass, and complete mainline client acceptance passes. Assess Fabric, Forge and NeoForge against their published target releases and actual APIs. Publish support for the target and loader combinations that pass the required checks.

When the mainline moves to 26.3, 26.2 enters the older compatibility track. A later 26.4 release enters the new-version adaptation track promptly. Choose each mainline move separately. Earlier releases retain their original tags and files.

## Shared API and artifacts

The Extension API and player-access semantics apply to every supported host. Keep one native-neutral SDK and universal Builder implementation. Native source families cover real API changes only.

Exact target profiles are verification inputs. Release one artifact per loader and verified binary-compatible Minecraft interval. Check the same JAR on every exact target in that interval.

Run complete feature acceptance once against the mainline. Use targeted compatibility checks and key runtime regressions elsewhere. Add a target-specific test when a real native difference requires it, rather than duplicating the complete feature suite for every version.
