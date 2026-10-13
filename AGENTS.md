# Analyzer project instructions

## Standing user preference: Prism delivery

The user explicitly requested on October 11, 2026 that every Analyzer update also update their Prism Launcher instance. Treat publishing, packaging and installing as part of completing future project updates. This is already authorized; do not ask for the same permission again.

- Run the build and checks appropriate to the changes with Java 21, then run `gradlew.bat publishLocal` to publish the verified remapped JAR. Same-version updates are supported through SHA-256 comparison.
- Rebuild the drag-and-drop native Prism ZIP with `tools/prism/package_prism.py`, using a new empty staging directory. The ZIP should contain the current build, matching dependencies, updater, documentation and dependency sources/notices. Verify archive integrity and the Analyzer hash.
- With Minecraft closed, update the user's existing **Analyzer** instance through `tools/prism/Update-Analyzer.ps1 -InstanceDir <instance> -Strict`. Verify that exactly one installed Analyzer JAR matches the feed and build hashes. Preserve worlds, settings, profiles, friends and unrelated mods; the updater backs up replaced Analyzer JARs.
- Keep the instance connected to the local feed through `analyzer-updater.json` and its Prism pre-launch command. If Minecraft is running, publish/package first and report that installation awaits shutdown and the next pre-launch update; do not overwrite a running game's mod files.
- On another computer, locate the actual Prism data directory and Analyzer instance before using the paths below. Reuse the user's instance instead of creating duplicates or changing unrelated instances. If this project or feed moves, reconnect the updater to the correct local manifest.

Current computer paths:

- Prism instance: `C:\Users\sould\AppData\Roaming\PrismLauncher\instances\Analyzer-0.5.2-Prism-1.21.4`
- Local manifest: `C:\Users\sould\Desktop\analyzer-updates\release.json`
- Import ZIP: `C:\Users\sould\Desktop\Analyzer-<version>-Prism-1.21.4.zip`
- Java 21 JDK: `C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot`

The portable ZIP inherits Prism's Java settings. The current imported instance retains the user's Prism-managed Java selection; use the JDK path above for project builds without overwriting launcher preferences. Do not include launcher accounts or credentials in packages.

## User-selected UI direction

The latest user request supersedes the earlier Focused workspace default. **Studio** follows reference 3: a centered black/gray menu, top category tabs, three columns of rounded cards, inline expanded settings, pink **Rose** controls and a wider bold sans serif font. Use original native GLSL for surfaces, shadows and glow, with Minecraft screens handling text and input; do not introduce ImGui. Keep **Workspace / Classic / Compact** as alternatives in **HUD Studio → Menu layout**. Keep independent HUD/menu **Glass / Classic / Minimal / Neon** themes and **Rose / Mint / Violet / Gold** accents. Fresh defaults select **Studio / Rose**. The reference board is `UI-REFERENCES.md`; the acceptance plan is `UI-OVERHAUL-PLAN.md`.

The active instance was located in the imported folder above; `instances\Analyzer` is no longer present. The folder name retains `0.5.2` after updating its mod. Verify the current mod JAR and embedded version instead of inferring the installed version from that folder name. This release's delivery selects **Studio / Rose / Glass theme / Solid material** in the active configuration, preserving all other fields, saved profiles, worlds and unrelated mods. Future updates preserve deliberate user appearance changes unless the user asks to change them.
