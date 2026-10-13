package dev.analyzer.privacy;

import java.util.Set;

/** Original, version-independent rules for voluntary client metadata. */
public final class PrivacyPolicy {
    private static final Set<String> STOCK_CLIENT_PAYLOADS = Set.of("minecraft:brand");
    private static final Set<String> ANNOUNCEMENT_CHANNELS = Set.of(
            "minecraft:register", "minecraft:unregister", "c:register", "c:version");
    private static final Set<String> LOADER_NAMESPACES = Set.of(
            "fabric", "fabric-networking-api-v1", "forge", "fml", "neoforge", "quilt", "analyzer");

    public record Options(boolean enabled, boolean vanillaBrand, boolean blockModChannels,
                          boolean vanillaOnly, boolean hideKnownPacks, boolean blockLoginQueries) {
        public static final Options DEFAULT = new Options(true, true, true, true, true, true);
    }

    private PrivacyPolicy() { }

    public static boolean active(Options options, boolean local, boolean clientbound) {
        return options.enabled() && !local && clientbound;
    }

    /** Exact stock IDs avoid allowing a mod that happens to use the minecraft namespace. */
    public static boolean allowsPayload(String channel, Options options) {
        if (!options.enabled()) return true;
        if (options.vanillaOnly()) return channel != null && STOCK_CLIENT_PAYLOADS.contains(channel);
        if (!options.blockModChannels()) return true;
        if (channel == null || ANNOUNCEMENT_CHANNELS.contains(channel)) return false;
        int separator = channel.indexOf(':');
        return separator > 0 && !LOADER_NAMESPACES.contains(channel.substring(0, separator));
    }

    /** Only Minecraft's real built-in core pack is a stock known-pack declaration in 1.21.4. */
    public static boolean allowsKnownPack(String namespace, String id, String version, String vanillaVersion, Options options) {
        return !options.enabled() || !options.hideKnownPacks()
                || "minecraft".equals(namespace) && "core".equals(id) && vanillaVersion.equals(version);
    }
}
