# Saved Analyzer versions

The private [XonistReal/Analyzer repository](https://github.com/XonistReal/Analyzer) preserves **16 version tags**. Existing commits/tags are retained; recovery commits use their actual backup date rather than invented development dates. **Main/v0.10.0 contain the complete current project and tests.** The original 15-tag transfer remains documented in [GITHUB-BACKUP.md](GITHUB-BACKUP.md).

| Version/update | Tag | Source provenance |
| --- | --- | --- |
| 0.1.0 | `v0.1.0` | Existing original archived source/build/tests |
| 0.1.1 | `v0.1.1` | Existing original archived source/build/tests |
| 0.2.0 | `v0.2.0` | Existing original archived source/build/tests |
| 0.3.0 | `v0.3.0` | Existing original archived source/build/tests |
| 0.4.0 | `v0.4.0` | Existing original archived source/build/tests |
| 0.5.0 | `v0.5.0` | Existing original archived source/build/tests |
| 0.5.1 | `v0.5.1` | Existing original archived source/build/tests |
| Original 0.5.2 | `v0.5.2` | Existing original archived source/build/tests; unchanged tag |
| Later 0.5.2 native UI update | `v0.5.2-ui` | Exact saved named main-source/resource JAR; recovered scaffold |
| 0.6.0 Studio | `v0.6.0` | Exact saved named main-source/resource JAR; recovered scaffold |
| 0.7.0 Home | `v0.7.0` | Exact saved named main-source/resource JAR; recovered scaffold |
| 0.7.1 complete Studio/HUD | `v0.7.1` | Exact saved named main-source/resource JAR; recovered scaffold |
| 0.7.2 watermark choice | `v0.7.2` | Exact saved named main-source/resource JAR; recovered scaffold |
| 0.8.0 liquid glass/layout retirement | `v0.8.0` | Exact saved named main-source/resource JAR; recovered scaffold |
| 0.9.0 privacy | `v0.9.0` | Complete preserved source, resources, tests and tools |
| 0.10.0 native automation | `v0.10.0` | Complete original native automation source, resources, tests, evidence and delivery tools; no runtime Baritone dependency |

The existing eight tags were originally reconstructed from saved source exports; their original-export hashes remain in their commit messages. They are not moved or rewritten by this backup. Those original source ZIPs are not available on this PC, so release source ZIPs are newly generated from the preserved Git tags.

For the six later historical snapshots, the named `build/devlibs` source JAR supplies exact main Java/resources. The original per-version tests and build scripts were not saved. **ARCHIVE-PROVENANCE.md/json** in each recovered tag records hashes and the reconstructed Java 21/Fabric build scaffold. Those snapshots are compiled to verify they can be developed again; no reconstructed test history or exact rebuild binary match is claimed. The unmodified original named source JAR is a separate release asset.

Seven saved Prism bundles cover the later 0.5.2 update and 0.6.0–0.9.0. Their runtime JARs match the corresponding saved builds. Two additional same-version binaries survive: 0.5.2 **71586c31…** and 0.7.0 **e302c044…**. They are archived as additional assets with an explicit **binary-only; exact source unavailable** label, rather than being attached to fabricated source snapshots.

The original 15 releases and the native 0.10.0 release include Git source exports and SHA-256 checksums. Available Prism/runtime/source archives are release assets; large distribution ZIPs are not committed to Git. Runtime accounts, saves, settings, local update feeds, caches and raw logs remain excluded. Historical screenshots, validation summaries, licenses and available dependency/source archives are preserved. [GITHUB-BACKUP.md](GITHUB-BACKUP.md) describes the original backup and its limits.

On another PC, clone main to develop, or import the latest Prism ZIP to play. The local updater path must be reconnected on that PC; it does not automatically fetch private GitHub releases. See [Moving PCs](MOVING-PCS.md).
