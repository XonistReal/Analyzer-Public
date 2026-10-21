# Analyzer on another PC

The public repository is [XonistReal/Analyzer](https://github.com/XonistReal/Analyzer-Public). **Main contains the complete current 0.16.0-beta.1 project**, including tests, shaders, supplied branding, tools and documentation. [Version history](VERSION-HISTORY.md) distinguishes preserved original snapshots, recovered runtime-source snapshots and binaries whose exact sources were not saved.

## Play through Prism

1. Open [the 0.15.0 release](https://github.com/XonistReal/Analyzer-Public/releases/tag/v0.15.0) and download `Analyzer-0.15.0-Prism-1.21.4.zip`.
2. Drag it onto Prism, or use **Add Instance → Import**. Configure your Minecraft account in Prism and use Java 21.
3. This is a clean client instance. Transfer your existing saves and personal configurations separately if you want them; they are not in the repository or release.

The optional updater reads a local development feed. Public 0.15.0 packages leave it disabled, with no author-specific manifest path. GitHub releases require no token stored in Minecraft. Reconnect a local feed below only if developing on that PC.

For older Windows-oriented packages on macOS/Linux, open the imported instance’s **Edit → Settings → Custom commands** and clear its Windows PowerShell **Pre-launch command** before launching. Public 0.15.0 packages already have no such command. The bundled Windows updater is optional; clearing that command does not remove Analyzer or affect Minecraft account setup.

## Continue development

Clone using GitHub Desktop, or use GitHub CLI after signing in:

```powershell
gh repo clone XonistReal/Analyzer
cd Analyzer
.\gradlew.bat build
```

Use a Java 21 JDK for the build. The Gradle wrapper downloads the pinned build dependencies on the first run. On macOS/Linux use `./gradlew`.

To inspect an earlier saved version:

```powershell
git tag --list
git switch --detach v0.8.0
git switch main
```

Create a branch before editing an older version. Native screenshots and validation summaries describe the scenarios tested in each release. Versions through the original v0.5.2 retain their original source/build/test snapshots. Later historical tags through v0.8.0 recover the exact saved main Java/resources with a documented build scaffold; their original tests/build scripts were not saved. The later same-version 0.5.2 UI update is **v0.5.2-ui**, leaving the original v0.5.2 untouched. Main/v0.15.0 retain the complete current source and tests.

Each release includes a Git source ZIP and `SHA256SUMS.txt`. Where available, the original named source JAR, runtime JAR and Prism ZIP are separate assets; same-version alternate binaries are explicitly labeled. The source ZIP is a new export of its Git tag, not a claim that a missing original source export was recovered.

## Reconnect the Windows local updater

From the source checkout:

```powershell
.\gradlew.bat publishLocal
```

This creates `analyzer-updates` beside the checkout. With Minecraft closed, edit the Prism instance's `analyzer-updater.json`: set `manifestPath` to the absolute path of that folder's `release.json`, and set `enabled` to `true`. Set the Windows pre-launch command shown in [PRISM.md](PRISM.md). Later local builds published with `publishLocal` install before the next launch, with backups kept outside `mods`.

The [Prism guide](PRISM.md), [safety guide](SAFETY.md), and [UI overhaul plan](UI-OVERHAUL-PLAN.md) are included in the source. Keep account tokens, saves, runtime configuration, caches and local update feeds out of Git.

External custom sky images are not embedded in the JAR or portable instance. Copy your `minecraft/skyboxes` directory and use **World → Skybox → Browse custom skyboxes** to select the new path. Four approved wing looks are now included; other cosmetic candidates remain previews in `design/cosmetics-review` and `design/hats-review`.
