# Contributing

Start with the [source map and build guide](docs/DEVELOPING.md). Use a Java 21 JDK and the checked-in Gradle wrapper.

## Report a problem

Use the [bug report form](https://github.com/XonistReal/Analyzer-Public/issues/new?template=bug_report.yml). Include the exact Analyzer/Minecraft versions, loader, other mods, affected module and settings, and steps that reproduce the behavior. Describe what you expected and what happened. A relevant screenshot or short log excerpt is more useful than an unexplained full log. Remove account tokens, private server addresses and personal data before posting.

For a feature request, explain the desired behavior and where it belongs in the current UI. Reference the existing module/settings entry when applicable.

## Prepare a change

- Keep the change focused on its stated problem. Preserve saved IDs, explicit user choices and unrelated configuration.
- Keep action/input ownership, bounds, cancellation and resource cleanup explicit. Retain comments that explain why an invariant exists.
- Add or update a meaningful policy/regression check when behavior changes. Do not treat a deterministic check as proof of a native rendering or multiplayer outcome.
- Run `./gradlew build moduleCatalog` (or `.\gradlew.bat build moduleCatalog` on Windows), review generated changes, and include both catalogue files when definitions change.
- Update the relevant feature document if controls, supported inputs or limitations change.
- Keep license notices with third-party code/assets and record their source. Do not add incompatible or unverified reference code to the runtime.

In the pull request, describe the concrete problem, resulting behavior, checks performed and any remaining limits. Native screenshots or logs should identify the actual build and fixture. Do not commit worlds, accounts, local update feeds, user profiles, caches or raw private logs.
