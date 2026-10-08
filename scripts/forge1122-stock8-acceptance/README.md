# Stock Forge 1.12.2 Java 8 acceptance

This workflow runs the packaged OpenAllay mod on Minecraft 1.12.2, Forge 14.23.5.2864 and Java 8.
It uses one dependency-embedded JAR in `mods` and the official game libraries.

## Product input

Set `native-builds/forge1122-census/accepted-stock8-product.json` to the verified product.
The exact fields are `provider`, `jarSha256`, `jarName` and `productSource`.
`provider` contains `artifactId`, `runId`, `sourceRevision` and `sha256`.
The runner checks every input hash before launch.

## Run

```sh
gh workflow run forge1122-stock8-acceptance.yml --ref <branch> -f scenario=builder-restricted
```

Available scenarios: `boot`, `world`, `persistence`, `builder-restricted`,
`builder-partial`, `builder-cancel`, `builder-undo`, `builder-legacy-shapes` and `menus`.

Each run uses a fresh isolated game profile.
The persistence scenario reopens its own world in a second process.
The cache contains official immutable libraries and assets.
Player worlds, settings and profiles stay in place.

The runner checks the manifest bootstrap owner, Java 8 class files, stock library
origins, Guava method links and required injected interfaces.
Scenario acceptance requires the functional report and a natural process exit with code 0.
Reports, logs and UI frames are retained as artifacts.
