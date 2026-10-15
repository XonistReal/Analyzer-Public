# Analyzer in Prism Launcher

Analyzer **0.10.3** targets **Minecraft 1.21.4**, **Fabric Loader 0.16.14**, **Fabric API 0.119.4+1.21.4** and **Java 21**. The native ZIP contains Analyzer and Fabric API as its two mods. Native Automation needs no Baritone or other bot mod. Minecraft account credentials, user worlds/configuration and Minecraft itself are not included.

## Import or update

Drag `Analyzer-0.10.3-Prism-1.21.4.zip` onto Prism or use **Add Instance → Import**. The archive has `instance.cfg`, `mmc-pack.json` and `minecraft/mods/` at its root. Use your own Minecraft account and Java 21. The portable ZIP inherits Prism's Java preferences and has no fixed Java path.

On this computer, reuse `C:\Users\sould\AppData\Roaming\PrismLauncher\instances\Analyzer-0.10.0-Prism-1.21.4`. That folder is the current imported instance located on October 14; inspect the installed JAR/version/hash to identify the current build. Future updates preserve launcher Java preferences, worlds, configs, profiles, friends and unrelated mods.

With Minecraft closed, the verified updater installs the published Analyzer JAR. The native release also retires only the exact formerly bundled official Baritone 1.13.1 JAR to a recoverable `.analyzer-updater/retired-mods` backup. Unknown or modified mod files are retained with a reported reason; no unrelated mod is deleted. A running game's mod files are not overwritten. The next pre-launch update can complete a pending delivery after shutdown.

## Controls

Press **Right Shift** for Studio or shader Click GUI. Keep **HUD Studio → Menu layout → Studio / Click GUI**, independent Glass/Classic/Minimal/Neon themes and Rose/Mint/Violet/Gold accents. Retired Workspace/Classic/Compact layout values migrate to Studio; Classic remains a theme. The supplied watermark shows exactly the selected A logo or Analyzer logo.

Home defaults to Minecraft panorama, with Orbit and Vanilla menu alternatives. Profiles, HUD editing, Automation and numeric entry retain Studio shader surfaces and bundled Lato. Worlds, servers and native options retain actual Minecraft data/callbacks. Server Privacy keeps its eight default protections and preserves saved opt-outs. See [SERVER-PRIVACY.md](SERVER-PRIVACY.md).

Enable **Render → X-ray** only when desired. Its World style chooses **Fade terrain / Terrain outlines / Ores only**, with separate ore-group filters and an outline/glow range. Disabling rebuilds ordinary terrain. Read [XRAY.md](XRAY.md) for the native render scope and compatibility limits.

Open **Automation → Review plan → Start reviewed plan** for local native goals, the schematic folder browser and material checklists, adjustable turning and action pacing, tool recovery and mob protection. Saved tasks restore paused. Read [AUTOMATION.md](AUTOMATION.md) and [SAFETY.md](SAFETY.md) for implemented behavior and world prerequisites.

## Local development feed

Build with a Java 21 JDK, then publish:

```powershell
.\gradlew.bat publishLocal
```

The feed is `analyzer-updates/release.json` beside the checkout. `analyzer-updater.json` in the instance enables it and stores its local manifest path. Preserve the Prism pre-launch command. On another Windows PC, reconnect that path after cloning, or set `"enabled": false` until a local feed exists. On macOS/Linux also clear the Windows PowerShell pre-launch command in instance custom commands. [MOVING-PCS.md](MOVING-PCS.md) explains source access and setup.

Update a closed instance and check it:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/prism/Update-Analyzer.ps1 -InstanceDir 'C:\Users\sould\AppData\Roaming\PrismLauncher\instances\Analyzer-0.10.0-Prism-1.21.4' -Strict
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/prism/Update-Analyzer.ps1 -InstanceDir 'C:\Users\sould\AppData\Roaming\PrismLauncher\instances\Analyzer-0.10.0-Prism-1.21.4' -Strict -CheckOnly
```

Fresh packages use a new empty staging directory:

```powershell
python tools/prism/package_prism.py --fabric-api <fabric-api-0.119.4+1.21.4.jar> --instance-folder <new-empty-staging-directory>
```

The packager verifies the Analyzer feed hash, mod metadata, Fabric API, required docs/references/licenses and ZIP integrity. Updater integration tests use isolated disposable fixtures:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/prism/Test-Updater.ps1
```
