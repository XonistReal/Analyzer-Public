# Analyzer project instructions

## Standing user preference: Prism delivery

The user explicitly requested on October 11, 2026 that every Analyzer update also update their Prism Launcher instance. Treat publishing, packaging and installing as part of completing future project updates. This is already authorized; do not ask for the same permission again.

- Run the build and checks appropriate to the changes with Java 21, then run `gradlew.bat publishLocal` to publish the verified remapped JAR. Same-version updates are supported through SHA-256 comparison.
- Rebuild the drag-and-drop native Prism ZIP with `tools/prism/package_prism.py`, using a new empty staging directory. The ZIP should contain the current build, matching dependencies, updater, documentation and dependency sources/notices. Verify archive integrity and the Analyzer hash.
- With Minecraft closed, update the user's existing **Analyzer** instance through `tools/prism/Update-Analyzer.ps1 -InstanceDir <instance> -Strict`. Verify that exactly one installed Analyzer JAR matches the feed and build hashes. Preserve worlds, settings, profiles, friends and unrelated mods; the updater backs up replaced Analyzer JARs.
- Keep the instance connected to the local feed through `analyzer-updater.json` and its Prism pre-launch command. If Minecraft is running, publish/package first and report that installation awaits shutdown and the next pre-launch update; do not overwrite a running game's mod files.
- On another computer, locate the actual Prism data directory and Analyzer instance before using the paths below. Reuse the user's instance instead of creating duplicates or changing unrelated instances. If this project or feed moves, reconnect the updater to the correct local manifest.

Current computer paths:

- Prism instance: `C:\Users\sould\AppData\Roaming\PrismLauncher\instances\Analyzer`
- Local manifest: `C:\Users\sould\Desktop\analyzer-updates\release.json`
- Import ZIP: `C:\Users\sould\Desktop\Analyzer-<version>-Prism-1.21.4.zip`
- Java 21 JDK: `C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot`

The portable ZIP inherits Prism's Java settings; only the directly installed instance uses the machine's explicit Java 21 path. Do not include launcher accounts or credentials in packages.

## User-selected UI direction

On October 11, 2026 the user chose the **Focused workspace** comparison, combined with the third reference's rounded module cards/switches and the second reference's glass appearance. Keep sidebar browsing and a substantial settings inspector, and expose menu switching from **HUD Studio** module settings. Keep existing menu alternatives and the **Glass / Classic / Minimal / Neon** theme set, with independent HUD/menu themes and **Mint / Violet / Gold** accents. The reference board is `UI-REFERENCES.md`; the detailed acceptance plan is `UI-OVERHAUL-PLAN.md`. Treat this as a selected direction rather than asking the user to choose again.
