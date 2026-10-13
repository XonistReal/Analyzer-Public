package dev.analyzer.mixin;

import dev.analyzer.privacy.PackPrivacyDownloadSource;
import net.minecraft.client.resource.server.DownloadQueuer;
import net.minecraft.client.resource.server.ServerResourcePackLoader;
import net.minecraft.client.session.Session;
import net.minecraft.util.Downloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.net.Proxy;
import java.util.concurrent.Executor;

@Mixin(ServerResourcePackLoader.class)
public abstract class PrivacyPackLoaderMixin {
    @Inject(method = "createDownloadQueuer", at = @At("HEAD"))
    private void analyzer$markDownloader(Downloader downloader, Executor executor, Session session, Proxy proxy,
                                          CallbackInfoReturnable<DownloadQueuer> cir) {
        ((PackPrivacyDownloadSource) downloader).analyzer$markServerPackDownloader();
    }
}
