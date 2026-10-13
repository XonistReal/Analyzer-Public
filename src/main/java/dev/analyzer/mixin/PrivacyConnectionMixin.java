package dev.analyzer.mixin;

import dev.analyzer.privacy.ServerPrivacy;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Covers login, configuration, play and reconfiguration using the same connection pipeline. */
@Mixin(ClientConnection.class)
public abstract class PrivacyConnectionMixin {
    @Inject(method = "channelActive", at = @At("HEAD"))
    private void analyzer$privacyPipeline(ChannelHandlerContext context, CallbackInfo callback) {
        ServerPrivacy.attach(context.channel(), (ClientConnection) (Object) this);
    }
}
