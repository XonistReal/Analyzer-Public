# Analyzer in Prism Launcher

Analyzer **0.3.0** targets **Minecraft 1.21.4**, **Fabric Loader 0.16.14**, **Fabric API 0.119.4+1.21.4** and **Java 21**. The native Prism ZIP includes both mods, instance definitions and a Windows local updater. Minecraft and account credentials are not included.

## Import and launch

Drag `Analyzer-0.3.0-Prism-1.21.4.zip` onto Prism's main window and confirm the new instance. Alternatively use **Add Instance → Import → Browse**, select the ZIP, and press **OK**. The native archive has `instance.cfg`, `mmc-pack.json` and `minecraft/mods/` at its root.

For an existing **Fabric 1.21.4** instance without the updater, close Minecraft, open **Edit → Mods**, and replace the old Analyzer JAR with `analyzer-0.3.0.jar`. Install `fabric-api-0.119.4+1.21.4.jar` if needed. Keep one Analyzer JAR. The ZIP/feed uses `analyzer-0.3.0-<hash>.jar`; this is the same build with a checksum suffix. Other game versions require compatible builds.

Use Java 21: enable Prism's automatic Java selection/download or select Java 21 under **Settings → Java**. The directly installed instance on this computer uses the verified project Java 21 runtime. The portable ZIP inherits Prism's Java settings and has no fixed Java path.

Press **Right Shift** after launch, choose **Workspace**, then **Profiles → cinematic → Load** for the new visual/HUD setup. **classic** and **neon** provide alternate layouts/HUD themes; these three profiles leave gameplay automation off. Loading **hvh** explicitly enables Kill Aura, Crystal Aura, Surround, Auto Weapon, Auto Totem, Auto Armor and Keep Sprint. The header opens **Workspace / Classic / Compact / Edit HUD**. Earlier starter profiles and saved files are preserved; loading a profile replaces active settings/keybinds.

Analyzer now has **107 registered modules**, including actual combat/HvH, movement, inventory and world actions. These use normal game interactions; server movement/reach/cooldown rules still apply. Fresh defaults enable sky/menu/HUD effects and local place/break/hit visuals. All gameplay automation and Entity/Storage ESP are opt-in. [README.md](README.md) explains gear, controls and rendering limits; [MODULES.md](MODULES.md) lists every module; [ROADMAP.md](ROADMAP.md) distinguishes scoped implementations from planned work. Selected runtime checks are in [VALIDATION.md](VALIDATION.md).

## Updates while we develop

The updater checks a **local release feed before each launch**. An existing Analyzer instance connected to this project's feed receives 0.3.0 on its next launch; importing a second instance is optional. Close Minecraft before updating, then restart it to load new code.

`analyzer-updater.json` points to this project's sibling `analyzer-updates/release.json`. The updater verifies SHA-256 and embedded mod metadata, backs up JARs whose mod ID is `analyzer` under `.analyzer-updater/backups/`, and installs the verified replacement. It preserves worlds, settings, Analyzer profiles, friends, Fabric API and other mods. Failed updates keep the existing installation and print the reason in Prism's launch console. A pending transaction restores an interrupted install on the next launch, even if the feed is unavailable.

If another update is still running, launch again when it finishes. Damaged recovery files that cannot safely restore Analyzer stop launch and identify the recovery directory, preserving files for inspection. Ordinary missing/invalid feeds keep the installed version playable.

This updater is **local and Windows-specific**; no internet release channel is configured. If the project moves, update `manifestPath` to its new feed. Set `"enabled": false` to pause updates. The bundled mod remains playable without the feed.

The ZIP enables this under **Edit → Settings → Custom commands**:

```text
powershell.exe -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$INST_DIR/analyzer-updater/Update-Analyzer.ps1"
```

For a manual update, close Minecraft and double-click `Update-Analyzer.cmd` in the instance folder. Backups stay outside `mods`. To restore one, close Minecraft, disable updates, and replace the current Analyzer JAR in `minecraft/mods` with the chosen backup. Keep one Analyzer JAR there.

## Publish the next build

From the source directory with a Java 21 JDK selected:

```powershell
.\gradlew.bat publishLocal
```

This builds/runs core checks, publishes the remapped JAR into `../analyzer-updates/` with its hash prefix in the filename, and atomically replaces `release.json`. Rebuilds with the same version work because the updater compares hashes. Change `mod_version` in `gradle.properties` for a numbered release. `-PanalyzerUpdateDir=...` chooses a different feed; change the instance configuration to match.

Rebuild a Prism ZIP with Python 3 and an empty staging directory:

```powershell
python tools/prism/package_prism.py --fabric-api <path-to-fabric-api-0.119.4+1.21.4.jar> --instance-folder <new-empty-staging-directory>
```

Run updater checks without touching a real Prism instance:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/prism/Test-Updater.ps1
```

Primary references: [Prism import](https://prismlauncher.org/wiki/help-pages/zip-import/), [mod drag and drop](https://prismlauncher.org/wiki/help-pages/loader-mods/), [custom commands](https://prismlauncher.org/wiki/help-pages/custom-commands/). Import/command behavior was checked against the installed version's [11.1.1 importer](https://github.com/PrismLauncher/PrismLauncher/blob/11.1.1/launcher/InstanceImportTask.cpp), [main window](https://github.com/PrismLauncher/PrismLauncher/blob/11.1.1/launcher/ui/MainWindow.cpp), [command launch](https://github.com/PrismLauncher/PrismLauncher/blob/11.1.1/launcher/launch/steps/PreLaunchCommand.cpp), and [Java selection](https://github.com/PrismLauncher/PrismLauncher/blob/11.1.1/launcher/minecraft/launch/AutoInstallJava.cpp).

