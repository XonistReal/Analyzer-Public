package dev.analyzer.mixin;

import dev.analyzer.privacy.PrivacyTextProtection;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.function.Function;

/** Only packet decoding adds remote origin; disk/config and locally created Text stay ordinary. */
@Mixin(TextCodecs.class)
public abstract class PrivacyTextCodecsMixin {
    @Shadow @Final @Mutable public static PacketCodec<RegistryByteBuf, Text> REGISTRY_PACKET_CODEC;
    @Shadow @Final @Mutable public static PacketCodec<RegistryByteBuf, Optional<Text>> OPTIONAL_PACKET_CODEC;
    @Shadow @Final @Mutable public static PacketCodec<RegistryByteBuf, Text> UNLIMITED_REGISTRY_PACKET_CODEC;
    @Shadow @Final @Mutable public static PacketCodec<RegistryByteBuf, Optional<Text>> OPTIONAL_UNLIMITED_REGISTRY_PACKET_CODEC;
    @Shadow @Final @Mutable public static PacketCodec<ByteBuf, Text> PACKET_CODEC;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void analyzer$remotePacketText(CallbackInfo ci) {
        REGISTRY_PACKET_CODEC = REGISTRY_PACKET_CODEC.xmap(PrivacyTextProtection::markRemote, Function.identity());
        UNLIMITED_REGISTRY_PACKET_CODEC = UNLIMITED_REGISTRY_PACKET_CODEC.xmap(PrivacyTextProtection::markRemote, Function.identity());
        PACKET_CODEC = PACKET_CODEC.xmap(PrivacyTextProtection::markRemote, Function.identity());
        OPTIONAL_PACKET_CODEC = OPTIONAL_PACKET_CODEC.xmap(value -> value.map(PrivacyTextProtection::markRemote), Function.identity());
        OPTIONAL_UNLIMITED_REGISTRY_PACKET_CODEC = OPTIONAL_UNLIMITED_REGISTRY_PACKET_CODEC.xmap(value -> value.map(PrivacyTextProtection::markRemote), Function.identity());
    }
}
