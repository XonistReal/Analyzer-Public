package dev.analyzer.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.analyzer.privacy.PackPrivacy;
import net.minecraft.util.NetworkUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;

@Mixin(NetworkUtils.class)
public abstract class PrivacyPackNetworkMixin {
    @WrapOperation(method = "download", at = @At(value = "INVOKE", target = "Ljava/net/URL;openConnection(Ljava/net/Proxy;)Ljava/net/URLConnection;", remap = false))
    private static URLConnection analyzer$checkedPackConnection(URL url, Proxy proxy, Operation<URLConnection> original) throws IOException {
        return PackPrivacy.guardingUrls() ? PackPrivacy.open(url, proxy) : original.call(url, proxy);
    }
}
