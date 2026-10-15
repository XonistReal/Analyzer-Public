# GitHub backup — October 13, 2026

This records the original 15-tag transfer through 0.9.0. The October 14 native automation update is preserved separately as **v0.10.0**, with complete source/tests and a Prism package containing Analyzer and Fabric API. The task-start correction and schematic browser are preserved as **v0.10.1** with their own source/tests and Prism package. Local request interpretation, recovery fixes and X-ray are preserved as **v0.10.2**. The camera/route update is preserved as **v0.10.3**. Main now follows 0.10.3; the historical tags and assets below remain unchanged. Current installation and acceptance details are in [PRISM.md](PRISM.md) and [VALIDATION.md](VALIDATION.md).

Repository: **[XonistReal/Analyzer](https://github.com/XonistReal/Analyzer)**, retained as private. The authenticated account is XonistReal. Main/v0.9.0 preserve the complete current working project. Existing historical commits and eight tags remain unchanged; seven additional tags identify the later native UI update and releases through 0.9.0.

The current source transfer includes Java/resources/tests, Gradle wrapper and pinned dependencies, GLSL, supplied transparent logos, build/export/updater tools, design references, screenshots and documentation. It excludes Minecraft runtime data, account files/tokens, saved worlds, user config/profiles, local feed files, caches and raw logs. The two supplied logo files retain their original bytes. Individual tracked files remain below GitHub's normal large-file limit; Prism/source distribution ZIPs are release assets.

## Historical recovery

The original v0.1.0–v0.5.2 source/build/test snapshots already existed in GitHub. They are preserved without rewriting tags. Their new release source ZIPs are generated from Git, since the original local source exports are not on this computer.

The later v0.5.2-ui, v0.6.0, v0.7.0, v0.7.1, v0.7.2 and v0.8.0 commits recover main Java/resources exactly from saved named source JARs. Per-version Prism bundles supply historical docs, references and captures. Their original build scripts/tests were not saved, so a documented minimal build scaffold uses the known Java 21/Minecraft 1.21.4/Fabric dependencies. No tests or development timestamps are invented. Original main-source bytes and release artifacts are verified separately from the scaffold's compilation check.

The original v0.5.2 tag differs from the later local 0.5.2 native UI update; **v0.5.2-ui** preserves that later update without changing the original. Extra saved binary revisions for 0.5.2 and 0.7.0 are retained as release assets with their actual hashes and no claim of exact recovered sources.

Each release has a newly generated Git source ZIP plus `SHA256SUMS.txt`. When available, it also has the original named source JAR, actual runtime JAR and native Prism ZIP. SHA-256 checksums and GitHub asset digests verify the uploaded files. The main source tree is checked against the working project before upload; the remote commit and all retained/new tags are checked afterward.

## Dependencies and data

Keep the separate Fabric API license, font OFL notices and Baritone's existing license/source/provenance files with their assets. The saved nether-pathfinder 1.4.1 source ZIP is preserved as upstream material; it does not include a standalone license notice or the contents of its Abseil submodule. This backup therefore does not independently verify complete native-helper dependency source/license coverage. It does not modify or invent an upstream license.

Historical validation applies to its recorded version and artifact. Current runtime/privacy acceptance remains documented in [VALIDATION.md](VALIDATION.md). The backup adds no gameplay feature or runtime dependency.

[VERSION-HISTORY.md](VERSION-HISTORY.md) maps every tag to its source scope. [MOVING-PCS.md](MOVING-PCS.md) gives clone/import/build and updater reconnection instructions. Access to the private repository requires the user's GitHub account; no GitHub token is stored in Minecraft or the Prism bundle.
