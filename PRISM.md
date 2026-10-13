# Analyzer in Prism Launcher

Analyzer **0.7.1** targets **Minecraft 1.21.4**, **Fabric Loader 0.16.14**, **Fabric API 0.119.4+1.21.4** and **Java 21**. The native Prism ZIP includes Analyzer, Fabric API and official Baritone API, instance definitions, corresponding Baritone sources/notices and a Windows local updater. Minecraft and account credentials are not included.

## Import and launch

Drag `Analyzer-0.7.1-Prism-1.21.4.zip` onto Prism's main window and confirm the new instance. Alternatively use **Add Instance → Import → Browse**, select the ZIP, and press **OK**. The native archive has `instance.cfg`, `mmc-pack.json` and `minecraft/mods/` at its root.

This computer's existing instance is in `C:\Users\sould\AppData\Roaming\PrismLauncher\instances\Analyzer-0.7.0-Prism-1.21.4`. The user imported the 0.7.0 package and removed the prior 0.5.2 folder; this folder name records that import and does not identify the currently installed mod version. Reuse this instance; importing another ZIP creates an additional instance. The previously recorded `instances\Analyzer` directory is absent. See [VALIDATION.md](VALIDATION.md) for the installed version and hash.

For an existing **Fabric 1.21.4** instance without the updater, close Minecraft, open **Edit → Mods**, and replace the old Analyzer JAR with `analyzer-0.7.1.jar`. Install `fabric-api-0.119.4+1.21.4.jar` if needed. Add the separately bundled `baritone-api-fabric-1.13.1.jar` for Automation; it is optional for other Analyzer features. Keep one Analyzer JAR. The ZIP/feed uses `analyzer-0.7.1-<hash>.jar`; this is the same build with a checksum suffix. Other game versions require compatible builds.

Use Java 21: enable Prism's automatic Java selection/download or select Java 21 under **Settings → Java**. The existing imported instance on this computer retains Prism's managed Java selection; project builds use the verified Java 21 JDK. The portable ZIP inherits Prism's Java settings and has no fixed Java path.

Launch opens **Analyzer Home** over Minecraft's panorama. The top-right control switches to the original Orbit background. **HUD Studio → Home screen** exposes the saved enable/background/animation/blur preferences; **Vanilla menu** restores the original title. [Home controls](HOME-SCREEN.md) links the native pages and captures. Press **Right Shift** in game. Fresh settings select **Studio** and **Rose**. Existing configurations retain a saved menu preference; an existing Classic selection therefore continues to open Classic until it is deliberately changed. Use **HUD Studio → Menu layout → Studio**, **Accent colour → Rose**, **Menu theme → Glass**, **Panel material → Solid** for the black-and-charcoal reference appearance. The preceding Studio delivery applied those four appearance selections. This Home update preserves the current configuration and saved profiles; missing Home preferences receive the Panorama/custom-Home defaults.

Open **HUD Studio** using Studio's **Appearance** action or a legacy appearance shortcut. Choose **Menu layout → Studio / Workspace / Classic / Compact**, **Panel material → Glass / Solid**, **Menu theme → Glass / Classic / Minimal / Neon**, and **Accent colour → Rose / Mint / Violet / Gold**. HUD and menu themes remain independent. Studio has top category tabs and three columns of gray cards with inline settings; its shapes, shadows and glow are rendered by original GLSL. Native Minecraft screens handle input and TTF text without ImGui. Selecting a card inspects it; its separate switch toggles it. Click a numeric value or right-click its slider row for exact entry. [UI-OVERHAUL.md](UI-OVERHAUL.md) explains navigation and links actual native captures.

Review and start tasks through **Automation**; [AUTOMATION.md](AUTOMATION.md) explains supported phrases and material/XP requirements. Fresh settings use a **60% HUD scale**. Updating preserves an existing saved scale, widget positions and profile files; use **HUD Studio → HUD scale** or the editor controls to change your current setup. Studio, its auxiliary screens and the HUD use the bundled Lato Regular/Bold fonts; explicitly retained alternative module layouts continue to use Rajdhani. Both have their own smooth font atlases, and HUD size remains independent of menu size.

Analyzer now has **123 registered modules**, including actual combat/HvH, movement, inventory and world actions. These use normal game interactions; server movement/reach/cooldown rules still apply. Fresh defaults enable sky/menu/HUD effects and local place/break/hit visuals. All gameplay automation and Entity/Storage ESP are opt-in. [README.md](README.md) explains gear, controls and rendering limits; [MODULES.md](MODULES.md) lists every module; [ROADMAP.md](ROADMAP.md) distinguishes scoped implementations from planned work; [RESEARCH-COSMETICS.md](RESEARCH-COSMETICS.md) compares all 37 published cosmetic setting families with Analyzer's actual controls. Selected runtime checks are in [VALIDATION.md](VALIDATION.md).

## Automation safety in 0.5.2

The existing optional Baritone API JAR is still the dependency for Automation; the safety update adds no AI service or API key. Review and explicitly start an Analyzer task to activate its safer route profile and Survival protection. Loading a profile, reopening a plan or relaunching Minecraft does not start or resume movement. Unfinished checkpoints restore paused.

During a task, nearby hostile mobs, low health or available-food recovery can pause the route while protection acts. A readable top-center banner identifies the paused task and current response. In **0.5.2**, an unavailable escape becomes a live **SAFETY_HOLD**, so protection keeps checking after knockback, an explosion or changing terrain. Urgent creepers receive priority, and short escape checks include collision-shaped ground, one-block changes and surface water with an open route to air.

User Pause/Stop release owned inputs, and temporary Baritone settings restore when the task pauses, stops, completes, ordinarily blocks or yields to manual control. A safety hold keeps the owned route paused and monitoring active; a separately blocked stage still needs explicit Resume after its requirement is satisfied. See [SAFETY.md](SAFETY.md) for defaults and limits. Equipment, food and escapable terrain still determine whether protection can succeed.

The user's latest request replaces the previous Focused workspace default with the third reference's centered black/gray menu, inline cards and pink controls. Workspace remains an optional legacy menu. [UI-REFERENCES.md](UI-REFERENCES.md) preserves that history; [UI-OVERHAUL-PLAN.md](UI-OVERHAUL-PLAN.md) distinguishes the current Studio work from broader acceptance checks.

## Updates while we develop

Project updates include rebuilding the import ZIP, publishing the local feed and updating the user's closed Analyzer instance. This standing request is recorded in [AGENTS.md](AGENTS.md). On this computer the instance is `C:\Users\sould\AppData\Roaming\PrismLauncher\instances\Analyzer-0.7.0-Prism-1.21.4` and the import ZIP is saved on the Desktop. Future work reuses this instance and preserves its worlds and preferences.

The updater checks a **local release feed before each launch**. After a build is published, an existing Analyzer instance connected to this project's feed receives it on its next launch; importing a second instance is optional. Close Minecraft before updating, then restart it to load new code.

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

The packager includes the complete Studio/HUD guide, supplied transparent logo resources and current native captures, plus the Home guide/reference board/native captures and the all-module test report/captures, alongside the gameplay settings guide/screenshots, the native Studio UI report/captures and the reference board with its five original comparison images. Reference images are documentation assets; the native components are original Analyzer code. Font notices include `LATO-OFL.txt` for Studio and `FONT-LICENSE.txt` for the legacy font. Historical raw validation logs are included when present; source archives may omit them, and the packager reports those omissions without substituting unrelated evidence.

After publishing, update the closed instance and verify it against the manifest:

```powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File tools/prism/Update-Analyzer.ps1 -InstanceDir 'C:\Users\sould\AppData\Roaming\PrismLauncher\instances\Analyzer-0.7.0-Prism-1.21.4' -Strict
```

Run updater checks without touching a real Prism instance:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/prism/Test-Updater.ps1
```

Primary references: [Prism import](https://prismlauncher.org/wiki/help-pages/zip-import/), [mod drag and drop](https://prismlauncher.org/wiki/help-pages/loader-mods/), [custom commands](https://prismlauncher.org/wiki/help-pages/custom-commands/). Import/command behavior was checked against the installed version's [11.1.1 importer](https://github.com/PrismLauncher/PrismLauncher/blob/11.1.1/launcher/InstanceImportTask.cpp), [main window](https://github.com/PrismLauncher/PrismLauncher/blob/11.1.1/launcher/ui/MainWindow.cpp), [command launch](https://github.com/PrismLauncher/PrismLauncher/blob/11.1.1/launcher/launch/steps/PreLaunchCommand.cpp), and [Java selection](https://github.com/PrismLauncher/PrismLauncher/blob/11.1.1/launcher/minecraft/launch/AutoInstallJava.cpp).
