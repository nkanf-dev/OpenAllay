# Install OpenAllay on legacy Forge

## Minecraft 1.16.5

1. Install Forge **36.2.42** using the official Forge installer.
2. Set this launcher installation to **Java 17**.
3. Download `openallay-forge-1.16.5-0.4.4.jar` from the OpenAllay release.
4. Put that JAR in the installation's `mods` folder. Remove older OpenAllay JARs from that folder.
5. Start the Forge installation and sign in through your normal launcher.

This download is one mod JAR with Minecraft Builder included. Install it directly
in `mods` using the steps above.

## Minecraft 1.12.2

Download `openallay-forge-1.12.2-0.4.4.zip` from the GitHub release.
Use the included installer to add a new OpenAllay launcher version based on your
installed stock Forge version.

### Before installation

- Install stock Forge **14.23.5.2864** using the official Forge installer.
- Start this stock installation once so the launcher installs Minecraft and its libraries.
- Install **Java 17**.
- Install **Python 3.11 or later** to run the included installer.
- Choose a new game directory for OpenAllay. Locate your launcher root, which
  contains `versions` and `libraries`. You will supply both paths to the installer.

### Install the package

1. Extract the ZIP to a new folder. Open a terminal in that folder.
2. Check the installed Forge profile and package files:

   ```text
   python3 install-openallay.py --minecraft-root "<launcher-root>" --game-directory "<new-game-directory>"
   ```

   On Windows, use `py -3` instead of `python3` if that is your Python command.
   Replace both paths with the directories used by your launcher. The check writes no files.

3. Add `--install` to install:

   ```text
   python3 install-openallay.py --minecraft-root "<launcher-root>" --game-directory "<new-game-directory>" --install
   ```

   The new version is `openallay-0.4.4-forge-1.12.2`.
   To use a different name, add `--profile-id "<new-profile-id>"` to both commands.
   The installer refuses to overwrite existing files or versions.

4. In your launcher, create an installation that uses the new version.
   Set its game directory to the directory printed by the installer.
   Set its Java executable to Java 17. Sign in normally and start the game.

Use a launcher that reads the standard Minecraft `versions` layout and supports
inherited profiles with `arguments.jvm`. Sign in through your normal launcher.

### Update or remove

For an update, use a new game directory and profile ID. Back up your worlds and
player settings, then copy the ones you want to keep to the new game directory.
To remove this installation, delete the new launcher version and the selected game's OpenAllay files after backing up its worlds and settings.
Keep the stock Forge profile and shared libraries used by other installations.

<details>
<summary>Technical details for launcher integration</summary>

### Package layout

```text
mods/openallay-feature-core.jar
mods/openallay-lifecycle-facade.jar
libraries/dev/openallay/legacy/private-mixin/0.4.4/private-mixin-0.4.4.jar
libraries/dev/openallay/legacy/runtime-helper/0.4.4/runtime-helper-0.4.4.jar
openallay-runtime/agent.jar
openallay-runtime/dimension.jar
openallay-runtime/binpatches.jar
openallay-runtime/runtime.properties
profile-template.json
install-openallay.py
JVM-ARGUMENTS.txt
GAME-ARGUMENTS.txt
INSTALL.md
SHA256SUMS
```

The third product JAR is `private-mixin-0.4.4.jar`. It belongs on the launcher classpath, not in `mods`.
The runtime helper JAR also belongs on that classpath. The installer copies both to their exact library coordinates.
The agent and bootstrap helper stay in the selected game directory's `openallay-runtime` folder.
The included unpacked Forge binary patches let this installation start on Java 17.

Minecraft, the stock Forge profile, Forge's original libraries, assets,
operating-system native libraries, worlds, and player configuration stay in
their existing locations.

### Exact launch contract

The new version inherits `1.12.2-forge-14.23.5.2864` and uses:

```text
net.minecraft.launchwrapper.Launch
```

Its two additional classpath coordinates are:

```text
dev.openallay.legacy:private-mixin:0.4.4
dev.openallay.legacy:runtime-helper:0.4.4
```

The launcher combines these with the inherited stock libraries and the original
Minecraft 1.12.2 client JAR. Use the launcher's resolved classpath and
native-library rules. Keep the original client JAR and stock ASM 5.2.
The package's `JVM-ARGUMENTS.txt` and `GAME-ARGUMENTS.txt` contain one exact template
argument per line.
Paths use launcher placeholders and forward slashes. The launcher supplies operating-system paths and the classpath separator.

The added agent argument is:

```text
-javaagent:${game_directory}/openallay-runtime/agent.jar=${game_directory}/openallay-runtime
```

The game arguments retain the stock authentication placeholders and FML tweaker, then add:

```text
--tweakClass org.spongepowered.asm.launch.MixinTweaker
```

Runtime support logs are written under the selected game's `logs` folder.

</details>
