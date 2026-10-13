# Server Privacy — Analyzer 0.9.0

**Client → Server Privacy** reduces explicit mod-identifying information exposed through Minecraft's network metadata and remote text resolution. The module and its eight protections default on, including when older saved configs/profiles do not contain it. Every toggle saves through the existing typed settings and profile system. Existing explicit opt-outs remain valid. This is separate from Automation's Survival protection.

The implementation is original Analyzer code, informed by the verified [OpSec source](https://github.com/aurickk/OpSec) and its documented fingerprinting mechanisms. OpSec is GPL-3.0; its code and binaries are not copied or bundled. The Discord-only project called Autism Defender could not be verified, so this release makes no claim to reproduce its private implementation. [Wurst's documented sign translation vulnerability](https://wurst.wiki/sign_translation_vulnerability) describes one attack this release tests.

## Settings

| Setting | Default | Actual behavior |
| --- | --- | --- |
| Report vanilla client brand | On | Replaces the actual outgoing `minecraft:brand` payload with `vanilla`. The client's local mod list remains available to its own tools and crash diagnostics. |
| Block mod channel announcements | On | Suppresses channel registration/version announcements and known loader/Analyzer custom payloads. |
| Vanilla-only channels (strict) | On | Permits only the exact stock `minecraft:brand` client custom payload; ordinary Minecraft packets continue normally. Unknown custom channels are blocked even if they use the `minecraft` namespace. |
| Hide non-vanilla known packs | On | Keeps only the actual Minecraft core namespace/id/version tuple in the response. Required response packets are sent even if the filtered list is empty. |
| Decline mod login queries | On | Answers with Minecraft's normal unsupported-query response and the original transaction ID, without plugin response data. |
| Protect remote translation/key probes | On | Marks packet-decoded text and sign-editor NBT lines before they are flattened. Remote mod/unknown translation keys use their explicit fallback or raw key; mod/unknown keybind components retain the raw key instead of the client's binding. |
| Block local/private pack URLs | On | Checks HTTP(S) URLs, credentials, local/private and selected reserved address ranges, plus every DNS answer before each resource-pack HTTP hop. Automatic redirects are replaced by checked hops. |
| Hide identifying pack download headers | On | Removes username/UUID headers and uses the stock Minecraft user agent. Game version/pack-format headers and normal hashing, content and status callbacks remain intact. |

These controls appear in both Studio and Click GUI. **Vanilla-only channels** takes priority over the announcement toggle. Turning strict mode off permits other non-loader mod/plugin channels; those channels can identify their mod. Servers that require mod networking or login handshakes may need additional individual opt-outs or the master module disabled. Disabling the module affects existing connections immediately, but cannot withdraw information already sent; choose settings before connecting.

## What is protected

The outbound interceptor is installed on the native connection before its channel-active callbacks run. Packet objects are filtered before encoding, including queued writes, direct pipeline writes and ordinary callback/flush flows. Suppressed writes complete their existing promises so normal callbacks do not send failure-fallback metadata. The integrated server's LocalChannel transport and server-side traffic are preserved.

Remote origin follows nested translation arguments, siblings and supported hover text/entity/item names and lore. Ordinary locally created text and generic disk/config decoding remain unmarked. Explicit translation fallbacks preserve Minecraft's formatting. Genuine vanilla translation keys, actual vanilla keybindings and winning accepted server-pack language entries remain usable. Cache epochs reflect policy toggles and language reload; ordinary text copies keep the origin of shared content. Sign NBT bypasses packet text codecs, so the editor's line read is guarded before its native editable-string snapshot.

Only the server resource-pack loader's Downloader enters the guarded HTTP scope. Other downloads do not inherit it, and exception paths restore the previous thread-local scope. Every checked redirect validates its next destination, and connections close after failures and stream closure. No existing pack cache is moved or deleted.

## Limits

**This reduces specific leaks; it does not make every mod undetectable.** The server still sees normal account identity, source IP, client settings and gameplay packets/behavior. Mods can communicate through chat, ordinary actions, other sockets or their own download code outside these native paths. A required handshake can make a server incompatible with strict settings. Local crash reports still honestly list installed mods; disguising those does not improve server privacy.

The known-pack response filter is defensive: ordinary Fabric 1.21.4 does not have the newer Fabric mod-known-pack advertisement hook described by OpSec. It is not evidence that Minecraft normally sends every installed JAR name.

The text guard hides remote mod/unknown translation and keybinding resolutions. It does not hide user-remapped vanilla bindings, all custom content/registries, or every modification to vanilla strings. Accepted server-pack translations remain allowed so legitimate server UI functions.

DNS validation occurs before the native HTTP connection's own lookup, leaving a **lookup/connect race**. This is not address-pinned transport or complete SSRF isolation. The request guard does not cover another mod's private HTTP stack, and header scrubbing cannot hide the download connection's IP. Per-account cache isolation, automatic server-list ping suppression, client-settings spoofing and server-pack shader override stripping are not implemented in this release.

## Verification and delivery

The pure policy/config checks cover strict/compatible modes, exact channel and pack tuples, deliberate opt-outs, old-profile defaults, unchanged profile bytes, URL/header cases, all-answer DNS checks, redirect failures, connection cleanup and scope restoration. Native acceptance runs actual Minecraft packet codecs, transformed ClientConnection pipelines and in-process LocalChannel transport. Native text codecs and a real SignEditScreen constructor verify protected editable strings; resource download tests use transformed Minecraft classes, injected DNS and stub HTTP only. No public server, user world or real resource-pack host is contacted by these tests.

[VALIDATION.md](VALIDATION.md) records final counts and tested limits. [PRISM.md](PRISM.md) documents the build/feed/install/package workflow. Future updates preserve saved privacy choices; the active instance update does not rewrite user config or profile files.
