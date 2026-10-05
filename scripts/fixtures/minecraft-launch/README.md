# Official launch metadata fixtures

These are offline unit-test contracts, not installed runtimes or game binaries.
The `arguments`, `id`, `type`, `inheritsFrom`, `mainClass`, Java pins, asset index
ID, library coordinates/paths/rules and installer client coordinates come from
the actual official producers below. The tests consume them through `prepare`,
including its runtime receipt, package, Java-pin, classpath and command checks.

Binary download URLs and hashes are omitted. The test creates tiny non-executable
library/client files and binds their exact bytes to a test provision receipt.
It never represents those files as the official binary artifacts. Assets also
use a tiny local index. No account credentials, Java VM or Minecraft process is
used by this fixture. Existing launcher supervision tests use their own shell
stubs and native-report fixtures.

Vanilla JSON was obtained from Mojang's version manifest for each exact target.
Its full source hashes match the receipts from the failed CI installations:

| Target | Official vanilla JSON SHA256 |
| --- | --- |
| 1.20.1 | a7a179c602c505b0f1bafa1402130e7d1966a1674b13f547c466196860bae7f8 |
| 1.21.1 | 458f2fbabc75a1cf79e5853c067f5ad95fca2163941c343b56c5c55b9127c8df |

Fabric profiles are from these official API URLs. Their time fields are omitted:

- https://meta.fabricmc.net/v2/versions/loader/1.20.1/0.18.2/profile/json
- https://meta.fabricmc.net/v2/versions/loader/1.21.1/0.18.2/profile/json

NeoForge profiles and installer data are the original `version.json` and
`install_profile.json` members of the SHA-verified official installers:

- `net.neoforged:forge:1.20.1-47.1.106:installer`
- `net.neoforged:neoforge:21.1.255:installer`

Both NeoForge `version.json` source hashes match the failed CI receipt records.
Each fixture's `provenance` contains the SHA256 of each full source JSON.
The source JSON hashes are distinct from the fixture hashes pinned in the test.

The synthetic harness session uses Minecraft's `legacy` user type. The saved
1.20.1 client `Main` parser defaults `userType` to `User.Type.LEGACY.getName()`;
`User.Type` maps that value to `legacy`. This does not prove account authentication.
The fixture verifies the existing offline UUID, token `0`, and empty clientId/xuid.
Demo and Quick Play stay disabled. The disposable native world bootstrap is not
replaced with a Quick Play shortcut.
