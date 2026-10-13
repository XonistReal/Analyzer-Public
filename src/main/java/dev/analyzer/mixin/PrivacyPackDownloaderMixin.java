package dev.analyzer.mixin;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.analyzer.privacy.PackPrivacy;
import dev.analyzer.privacy.PackPrivacyDownloadSource;
import dev.analyzer.privacy.PackPrivacyConnection;
import dev.analyzer.privacy.ServerPrivacy;
import net.minecraft.SharedConstants;
import net.minecraft.util.Downloader;
import net.minecraft.util.NetworkUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import java.net.Proxy;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;

@Mixin(Downloader.class)
public abstract class PrivacyPackDownloaderMixin implements PackPrivacyDownloadSource {
    @Unique private boolean analyzer$serverPackDownloader;
    @Unique private PackPrivacyConnection.Resolver analyzer$packResolver;
    @Override public void analyzer$markServerPackDownloader() { analyzer$serverPackDownloader = true; }
    @Override public boolean analyzer$isServerPackDownloader() { return analyzer$serverPackDownloader; }
    @Override public void analyzer$packFixtureResolver(PackPrivacyConnection.Resolver resolver) { analyzer$packResolver = resolver; }

    @WrapOperation(method = "method_55485", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/NetworkUtils;download(Ljava/nio/file/Path;Ljava/net/URL;Ljava/util/Map;Lcom/google/common/hash/HashFunction;Lcom/google/common/hash/HashCode;ILjava/net/Proxy;Lnet/minecraft/util/NetworkUtils$DownloadListener;)Ljava/nio/file/Path;"))
    private Path analyzer$packDownload(Path directory, URL url, Map<String, String> headers, HashFunction hashFunction,
                                       HashCode hash, int maxSize, Proxy proxy, NetworkUtils.DownloadListener listener,
                                       Operation<Path> original) {
        if (!analyzer$serverPackDownloader) return original.call(directory, url, headers, hashFunction, hash, maxSize, proxy, listener);
        boolean scrub = ServerPrivacy.option("scrub-pack-headers");
        try (var ignored = PackPrivacy.enter(ServerPrivacy.option("block-local-pack-urls"), scrub, analyzer$packResolver)) {
            Map<String, String> safe = scrub ? PackPrivacy.scrub(headers, SharedConstants.getGameVersion().getName()) : headers;
            return original.call(directory, url, safe, hashFunction, hash, maxSize, proxy, listener);
        }
    }
}
