package dev.analyzer.privacy;

import java.util.List;

public final class PrivacyPolicySelfTest {
    private static int checks;
    public static void main(String[] args) {
        var strict = PrivacyPolicy.Options.DEFAULT;
        var compatible = new PrivacyPolicy.Options(true, true, true, false, true, true);
        var channelsOff = new PrivacyPolicy.Options(true, true, false, false, true, true);
        var strictWithoutAnnouncements = new PrivacyPolicy.Options(true, true, false, true, true, true);
        var disabled = new PrivacyPolicy.Options(false, true, true, true, true, true);
        check(PrivacyPolicy.allowsPayload("minecraft:brand", strict), "Native client brand remains sendable");
        for (String channel : List.of("minecraft:register", "minecraft:unregister", "c:register", "c:version",
                "fabric:registry/sync", "fabric-networking-api-v1:register", "forge:handshake", "fml:loginwrapper",
                "neoforge:network", "quilt:handshake", "analyzer:hello", "unknownclient:modlist",
                "minecraft:modlist", "minecraft:brand_extra", "minecraft:debug/custom", "voicechat:request_secret")) {
            check(!PrivacyPolicy.allowsPayload(channel, strict), "Strict policy does not announce " + channel);
            check(PrivacyPolicy.allowsPayload(channel, disabled), "Disabled module preserves " + channel);
        }
        for (String announcement : List.of("minecraft:register", "minecraft:unregister", "c:register", "c:version",
                "fabric:registry/sync", "fabric-networking-api-v1:register", "forge:handshake", "fml:loginwrapper",
                "neoforge:network", "quilt:handshake", "analyzer:hello")) {
            check(!PrivacyPolicy.allowsPayload(announcement, compatible), "Compatible mode still suppresses " + announcement);
        }
        check(PrivacyPolicy.allowsPayload("voicechat:request_secret", compatible), "Compatibility explicitly permits mod/plugin channels");
        check(PrivacyPolicy.allowsPayload("unknownclient:modlist", compatible), "Compatibility is not a blanket concealment promise");
        check(PrivacyPolicy.allowsPayload("fabric:registry/sync", channelsOff), "Announcement toggle supports intentional modded-server use");
        check(!PrivacyPolicy.allowsPayload("fabric:registry/sync", strictWithoutAnnouncements), "Strict allowlist remains authoritative");
        check(!PrivacyPolicy.allowsPayload(null, strict), "Null channel is not a stock payload");
        check(PrivacyPolicy.active(strict, false, true), "Remote client traffic receives filtering");
        check(!PrivacyPolicy.active(strict, true, true), "Integrated connection remains unchanged");
        check(!PrivacyPolicy.active(strict, false, false), "Server-to-client traffic remains unchanged");
        check(!PrivacyPolicy.active(disabled, false, true), "Module off disables interception");
        check(PrivacyPolicy.allowsKnownPack("minecraft", "core", "1.21.4", "1.21.4", strict), "Actual core pack remains declared");
        check(!PrivacyPolicy.allowsKnownPack("fabric", "mod", "1.21.4", "1.21.4", strict), "Mod pack namespace is hidden");
        check(!PrivacyPolicy.allowsKnownPack("minecraft", "mod", "1.21.4", "1.21.4", strict), "Stock namespace alone does not authorize a mod pack");
        check(!PrivacyPolicy.allowsKnownPack("minecraft", "core", "secretmodversion", "1.21.4", strict), "Stock ID with mod version is hidden");
        check(!PrivacyPolicy.allowsKnownPack("minecraft", "core", "1.21.3", "1.21.4", strict), "Wrong game version does not manufacture a cached core pack");
        check(PrivacyPolicy.allowsKnownPack("fabric", "mod", "1.21.4", "1.21.4", disabled), "Disabled module preserves known packs");
        check(PrivacyPolicy.allowsKnownPack("fabric", "mod", "1.21.4", "1.21.4", new PrivacyPolicy.Options(true, true, true, true, false, true)),
                "Known pack toggle preserves intentional declarations");
        System.out.println("Analyzer server privacy policy checks passed: " + checks + " assertions");
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
