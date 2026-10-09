# Analyzer on another PC

The private repository is [XonistReal/Analyzer](https://github.com/XonistReal/Analyzer). Sign in to that GitHub account to access the source and release downloads. Version tags preserve the archived releases; the history was reconstructed from saved source exports rather than an earlier Git repository.

## Play through Prism

1. Open [the 0.5.2 release](https://github.com/XonistReal/Analyzer/releases/tag/v0.5.2) and download `Analyzer-0.5.2-Prism-1.21.4.zip`.
2. Drag it onto Prism, or use **Add Instance → Import**. Configure your Minecraft account in Prism and use Java 21.
3. This is a clean client instance. Transfer your existing saves and personal configurations separately if you want them; they are not in the repository or release.

The bundled updater reads a local development feed. Its original machine's `manifestPath` will not exist on the new PC. The client can still launch; set `"enabled": false` in the imported instance's `analyzer-updater.json` until you reconnect a local feed below. GitHub releases do not require a token stored in Minecraft, and the updater does not automatically download private GitHub assets.

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
git switch --detach v0.5.1
git switch main
```

Create a branch before editing an older version. Native screenshots and validation summaries describe the scenarios tested in each release; archived source ZIPs on the releases preserve the original exports.

## Reconnect the Windows local updater

From the source checkout:

```powershell
.\gradlew.bat publishLocal
```

This creates `analyzer-updates` beside the checkout. With Minecraft closed, edit the Prism instance's `analyzer-updater.json`: set `manifestPath` to the absolute path of that folder's `release.json`, and set `enabled` to `true`. Keep Prism's imported pre-launch command enabled. Later local builds published with `publishLocal` install before the next launch, with backups kept outside `mods`.

The [Prism guide](PRISM.md), [safety guide](SAFETY.md), and [UI overhaul plan](UI-OVERHAUL-PLAN.md) are included in the source. Keep account tokens, saves, runtime configuration, caches and local update feeds out of Git.
