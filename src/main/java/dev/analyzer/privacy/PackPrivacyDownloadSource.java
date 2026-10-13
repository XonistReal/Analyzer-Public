package dev.analyzer.privacy;

/** Identifies only Minecraft's server-resource-pack Downloader instance. */
public interface PackPrivacyDownloadSource {
    void analyzer$markServerPackDownloader();
    boolean analyzer$isServerPackDownloader();
    /** Injectable only on an isolated native fixture downloader; null retains the production DNS resolver. */
    void analyzer$packFixtureResolver(PackPrivacyConnection.Resolver resolver);
}
