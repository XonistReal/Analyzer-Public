# Analyzer in Prism Launcher

Analyzer **0.16.0-beta.1** uses the same **Minecraft 1.21.4**, **Fabric Loader 0.16.14**, **Fabric API 0.119.4+1.21.4** and **Java 21**. The native ZIP contains Analyzer and Fabric API as its two mods. Minecraft, accounts, saved worlds and personal configuration are not included. Native Automation requires no Baritone mod.

## Install a release

1. Download `Analyzer-0.15.0-Prism-1.21.4.zip` from the [0.15.0 release](https://github.com/XonistReal/Analyzer-Public/releases/tag/v0.15.0). The 0.16.0 beta is published as a mod JAR without its own Prism ZIP.
2. Drag it onto Prism or use **Add Instance → Import**.
3. Select your Minecraft account and Java 21 in Prism.
4. For the beta, close Minecraft, open the instance's `minecraft/mods` folder and replace `analyzer-0.15.0*.jar` with `analyzer-0.16.0-beta.1.jar` from the [latest release](https://github.com/XonistReal/Analyzer-Public/releases/latest). Keep Fabric API. Then launch.

Public ZIPs inherit Prism's Java selection, have no fixed machine paths and leave the optional Windows local-development updater disabled. They contain no platform-specific pre-launch command. To update an existing instance manually, close Minecraft and replace only its Analyzer mod JAR with the current release JAR. Keep one Analyzer JAR in `minecraft/mods` and preserve your worlds, configurations and unrelated mods.

## Controls and features

Press **Right Shift** for Studio or shader Click GUI. **HUD Studio → Menu layout** offers Studio / Click GUI, with independent Glass/Classic/Minimal/Neon themes and Rose/Mint/Violet/Gold accents. Home defaults to Minecraft panorama, with Orbit and Vanilla menu alternatives. The watermark shows exactly one selected supplied logo.

The [module catalogue](MODULES.md) lists every module and its actual settings/defaults. [Wings](WINGS.md) is an opt-in local-player cosmetic with four styles and animation/fit controls. [Custom Skybox](CUSTOM-SKYBOX.md) accepts local images and offers native sky sources. The [new hat concepts](HAT-DESIGNS.md) are previews awaiting selection, not installed wearables.

Open **Automation → Review plan → Start reviewed plan** for local tasks, schematic selection and material review. Saved tasks restore paused. [Automation](AUTOMATION.md), [construction](SCHEMATIC-CONSTRUCTION.md) and [safety](SAFETY.md) describe prerequisites and limits. [Server Privacy](SERVER-PRIVACY.md) describes compatibility opt-outs; it does not promise undetectability.

## Optional Windows development feed

From a Java 21 source checkout:

```powershell
.\gradlew.bat build moduleCatalog publishLocal
```

This creates `analyzer-updates/release.json` beside the checkout. With Minecraft closed, edit the instance's `analyzer-updater.json`: set `manifestPath` to that local manifest's absolute path and `enabled` to `true`. Set Prism's **Settings → Custom commands → Pre-launch command** to:

```text
powershell.exe -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$INST_DIR/analyzer-updater/Update-Analyzer.ps1"
```

The updater compares SHA-256 values, validates metadata, backs up replaced Analyzer JARs and preserves unrelated mods and game data. It never overwrites a running game's mods. It retires only the exact previously bundled official Baritone 1.13.1 JAR into a recoverable backup; unknown/modified files remain untouched. This is a local feed, not an automatic GitHub download service. Existing developer instances keep their deliberate enabled feed and launcher preferences.

To update/check a closed instance manually:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/prism/Update-Analyzer.ps1 -InstanceDir '<your-Prism-instance>' -Strict
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/prism/Update-Analyzer.ps1 -InstanceDir '<your-Prism-instance>' -Strict -CheckOnly
```

## Build a portable instance

After `publishLocal`, use a new empty staging directory:

```text
python tools/prism/package_prism.py --fabric-api <fabric-api-0.119.4+1.21.4.jar> --instance-folder <new-empty-directory>
```

The packager verifies metadata, source/license inputs and ZIP integrity. Add `--connect-local-feed` only to produce a Windows package deliberately tied to this computer. Public distribution uses the default portable mode. [Moving PCs](MOVING-PCS.md) explains transferring personal data and developing elsewhere.
