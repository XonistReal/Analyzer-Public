# Public source and release scope

[Analyzer-Public](https://github.com/XonistReal/Analyzer-Public) provides the current source, version tags and downloadable releases. The original development repository remains private, preserving its unchanged commits, tags and historical release assets.

The public history is a filtered copy. It omits exactly two archival files from every version:

- `third-party/baritone/nether-pathfinder-v1.4.1-source.zip`: no verified redistribution license accompanies this separate upstream archive.
- `third-party/baritone/baritone-api-fabric-1.13.1.jar`: the historical official binary embeds that native helper.

These are not dependencies of current Analyzer. The public copy retains source, author/date metadata and tag names, but filtered commits/tags necessarily have different object IDs. Checksums and native evidence for old private deliveries remain historical records; they do not describe rebuilt public artifacts. Historical pre-0.10 builds may require obtaining their old dependencies from the recorded upstream sources separately. No old binary release is newly republished without that provenance resolved.

Current releases include a verified remapped mod JAR, its source JAR, a complete Git-tag source ZIP, a portable Prism instance and SHA-256 checksums. The Prism ZIP contains only Analyzer and the pinned Fabric API mod, alongside dependency notices and matching source. It excludes accounts, saved worlds, personal game configuration and author-specific updater paths.

Analyzer's original code is MIT licensed. Approved wing adaptations/textures, fonts and other third-party items retain their own notices in [THIRD-PARTY.md](THIRD-PARTY.md). Reference screenshots are documentary comparisons outside runtime assets and are not relicensed as original Analyzer artwork. New hats remain selection-only previews, not installed wearables.
