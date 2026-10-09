# Analyzer in Prism Launcher

Analyzer **0.2.0** targets **Minecraft 1.21.4**, **Fabric Loader 0.16.14**, **Fabric API 0.119.4+1.21.4**, and **Java 21**. The native Prism ZIP includes both mods, the instance definitions, and a Windows updater. Minecraft and account credentials are not included.

## Import

Drag `Analyzer-0.2.0-Prism-1.21.4.zip` onto Prism's main window and confirm the new instance. Alternatively, use **Add Instance → Import → Browse**, select the ZIP, and press **OK**. This is a native instance archive with `instance.cfg`, `mmc-pack.json` and `minecraft/mods/` at its root.

For an existing **Fabric 1.21.4** instance without the updater, close Minecraft and open **Edit → Mods**. Replace the old Analyzer JAR with `analyzer-0.2.0.jar`, and install `fabric-api-0.119.4+1.21.4.jar` if needed. Keep one Analyzer JAR. In the Prism ZIP and update feed, the Analyzer filename includes a checksum suffix: `analyzer-0.2.0-<hash>.jar`; the mod is the same build. Other Minecraft or loader versions require compatible builds.

Use Java 21. Enable Prism's automatic Java selection/download, or select Java 21 under the instance's **Settings → Java**. The directly installed instance on this computer uses the verified Java 21 runtime from this project. The portable ZIP inherits Prism's Java settings and contains no fixed Java path.

Launch Analyzer, press **Right Shift**, and use **Profiles → showcase → Load**. This is the recommended starting point after updating an existing instance: Aurora sky, neutral Bloom, Screen ESP/radar, glass panels and cursor-reactive particles. **arctic** and **cinder** add violet/Nebula and gold/Ember looks. Earlier profiles and your saved files are preserved; loading a profile replaces active module settings and keybinds.

Fresh defaults enable the sky and menu effects. Entity ESP and Storage ESP remain opt-in until you enable them or load a profile. The ten modules and rendering limits are described in [README.md](README.md), including Screen ESP's line-of-sight filtering and untested Iris/Sodium compatibility.

## Updates while we develop

The updater checks a **local release feed before each launch**. An existing Analyzer instance already connected to this project's feed can receive 0.2.0 on its next launch; importing another instance is optional. Future work can publish a checked build into the same feed. Close Minecraft before updating and restart to load new code.

The instance's `analyzer-updater.json` points to this project's sibling `analyzer-updates/release.json`. The updater verifies SHA-256 and embedded mod metadata, backs up existing JARs whose mod ID is `analyzer` under `.analyzer-updater/backups/`, and installs the verified replacement. It preserves worlds, settings, Analyzer profiles, Fabric API, and other mods. Failed updates keep the existing installation and print the reason in Prism's launch console. A pending transaction record restores an interrupted install on the next launch, even if the feed is unavailable.

If another update is still running, launch again when it finishes. If recovery files are damaged and cannot safely restore Analyzer, the updater stops launch and identifies the recovery directory; it preserves the files for inspection. Ordinary missing or invalid release feeds keep the installed version playable.

The updater is **local and Windows-specific**. No internet release channel is configured. If the project moves, edit `manifestPath` to point to its new local feed. If the feed is missing, the bundled mod remains playable. Set `"enabled": false` to pause updates.

The ZIP has this command enabled under **Edit → Settings → Custom commands**:

```text
powershell.exe -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$INST_DIR/analyzer-updater/Update-Analyzer.ps1"
```

For a manual update, close Minecraft and double-click `Update-Analyzer.cmd` in the instance folder. Backups stay outside `mods`, so Fabric loads only one Analyzer version. To restore one, close Minecraft, disable updates, and replace the current Analyzer JAR in `minecraft/mods` with the chosen backup. Keep only one Analyzer JAR there.

## Publish the next build

From the source directory, with a Java 21 JDK selected:

```powershell
.\gradlew.bat publishLocal
```

This runs the build and core checks, publishes the remapped JAR to `../analyzer-updates/` under a filename containing its hash prefix, and atomically replaces `release.json`. Rebuilds with the same version are supported because the updater compares hashes. Change `mod_version` in `gradle.properties` for a new numbered release. `-PanalyzerUpdateDir=...` selects another feed; update the instance configuration to match.

Rebuild a Prism ZIP with Python 3 and an empty staging directory:

```powershell
python tools/prism/package_prism.py --fabric-api <path-to-fabric-api-0.119.4+1.21.4.jar> --instance-folder <new-empty-staging-directory>
```

Run updater integration checks without touching a real Prism instance:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/prism/Test-Updater.ps1
```

See [README.md](README.md) for game features and [VALIDATION.md](VALIDATION.md) for verification.

Primary references: [Prism import](https://prismlauncher.org/wiki/help-pages/zip-import/), [mod drag and drop](https://prismlauncher.org/wiki/help-pages/loader-mods/), [custom commands](https://prismlauncher.org/wiki/help-pages/custom-commands/). Import and command settings were checked against the installed version's [11.1.1 importer](https://github.com/PrismLauncher/PrismLauncher/blob/11.1.1/launcher/InstanceImportTask.cpp), [main window](https://github.com/PrismLauncher/PrismLauncher/blob/11.1.1/launcher/ui/MainWindow.cpp), [command launch](https://github.com/PrismLauncher/PrismLauncher/blob/11.1.1/launcher/launch/steps/PreLaunchCommand.cpp), and [Java selection](https://github.com/PrismLauncher/PrismLauncher/blob/11.1.1/launcher/minecraft/launch/AutoInstallJava.cpp).
