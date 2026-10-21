# Install and update Analyzer

[Latest downloads](https://github.com/XonistReal/Analyzer-Public/releases/latest) · [Modules and settings](../MODULES.md) · [Back to README](../README.md)

## Requirements

| Component | Supported release configuration |
| --- | --- |
| Game | Minecraft Java Edition 1.21.4 |
| Java | Java 21 |
| Loader | Fabric Loader 0.16.14; the mod metadata permits 0.16.10 or newer |
| Required mod | Fabric API 0.119.4+1.21.4 |

Use your own Minecraft account. The release does not include Minecraft, account credentials, saved worlds or personal settings. Analyzer includes its native automation; no separate bot mod is required.

## Prism Launcher

1. Download `Analyzer-<version>-Prism-1.21.4.zip` from the [latest release](https://github.com/XonistReal/Analyzer-Public/releases/latest). The 0.16.0 beta ships without a Prism ZIP; use [`Analyzer-0.15.0-Prism-1.21.4.zip`](https://github.com/XonistReal/Analyzer-Public/releases/tag/v0.15.0) and then follow step 4.
2. Drag the ZIP onto Prism, or choose **Add Instance → Import** and select it.
3. Select your Minecraft account and Java 21 in Prism, then launch the instance.
4. If the release you want has no Prism ZIP, close Minecraft, open the instance's `minecraft/mods` folder (**Folder → Minecraft folder** in Prism) and replace the Analyzer JAR with that release's `analyzer-<version>.jar`. Fabric API stays the same.

The archive contains Analyzer and Fabric API, and inherits your Prism Java selection. Public packages leave the optional local development updater disconnected. The included updater does not download GitHub releases automatically.

## Existing Fabric instance or another launcher

1. Create or select a **Minecraft 1.21.4** instance using the required Fabric Loader and Java 21.
2. Open that instance's Minecraft folder, then its `mods` folder.
3. Add the release's `analyzer-<version>.jar` and the matching Fabric API JAR. Keep exactly one Analyzer JAR installed.
4. Launch Minecraft and press **Right Shift** to open Analyzer.

The release's `-sources.jar` and source ZIP are development files. Install the ordinary runtime `.jar` in `mods`.

## Updating an existing instance

Close Minecraft. Back up your instance, replace its old Analyzer JAR with the new runtime JAR, and leave your other mods and game data in place. Do not import a second Prism instance just to update the first one. Update Fabric API only when the new release's requirements call for it.

Analyzer's own data is under the active Minecraft folder:

| Path | Contents |
| --- | --- |
| `config/analyzer/config.json` | Enabled modules, settings, keybinds and HUD appearance |
| `config/analyzer/profiles/` | Saved module profiles |
| `config/analyzer/friends.txt` | Local friend list |
| `schematics/` | Schematic library browsed from Automation |
| `skyboxes/` | Local custom skybox images and cube faces |

Keep `saves`, servers and other mods' configuration when updating or moving PCs. Custom skybox selections refer to local paths, so copy and reselect those images on the destination computer. [Source checkout and development updater](DEVELOPING.md#local-development-updates)

## Verify downloads

Each release includes `SHA256SUMS.txt`. Compare the listed value with the downloaded file's SHA-256:

```powershell
Get-FileHash -Algorithm SHA256 -LiteralPath '.\Analyzer-<version>-Prism-1.21.4.zip'
```

On Linux use `sha256sum <file>`; on macOS use `shasum -a 256 <file>`. The expected checksum is for that exact asset and release.

## If launch fails

Check the game version, Java version, Fabric API and duplicate Analyzer JARs first. The current rendering hooks target the native 1.21.4 pipeline; replacement renderers and shader packs may need separate compatibility work. If the problem persists, [report it](https://github.com/XonistReal/Analyzer-Public/issues/new?template=bug_report.yml) with the Analyzer version, mod list, reproduction steps and relevant log excerpt. Remove account tokens, server addresses and personal information before sharing logs.
