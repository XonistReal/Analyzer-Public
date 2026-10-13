package dev.analyzer.privacy;

import dev.analyzer.core.AnalyzerCore;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.SharedConstants;
import net.minecraft.network.packet.BrandCustomPayload;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.network.packet.c2s.config.SelectKnownPacksC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginQueryResponseC2SPacket;

import java.util.concurrent.atomic.AtomicLong;

/** Filters explicit metadata before packet encoding; never inspects gameplay packet contents. */
public final class ServerPrivacy {
    public static final String MODULE_ID = "server-privacy";
    public static final String HANDLER_NAME = "analyzer_privacy";
    private static volatile AnalyzerCore core;
    private static final AtomicLong blockedPayloads = new AtomicLong();
    private static final AtomicLong rewrittenBrands = new AtomicLong();
    private static final AtomicLong hiddenPacks = new AtomicLong();
    private static final AtomicLong declinedQueries = new AtomicLong();

    private ServerPrivacy() { }

    public static void initialize(AnalyzerCore settings) { core = settings; }

    /** An entire profile write and a packet decision cannot observe partial option values. */
    public static PrivacyPolicy.Options options() {
        AnalyzerCore current = core;
        if (current == null) return PrivacyPolicy.Options.DEFAULT;
        synchronized (current) {
            return new PrivacyPolicy.Options(current.isEnabled(MODULE_ID),
                    current.getBoolean(MODULE_ID, "vanilla-brand"),
                    current.getBoolean(MODULE_ID, "block-mod-channels"),
                    current.getBoolean(MODULE_ID, "vanilla-only"),
                    current.getBoolean(MODULE_ID, "hide-known-packs"),
                    current.getBoolean(MODULE_ID, "block-login-queries"));
        }
    }

    public static boolean enabled() {
        AnalyzerCore current = core;
        return current == null || current.isEnabled(MODULE_ID);
    }

    /** Shared settings gate for the independent text/resource privacy hooks. */
    public static boolean option(String settingId) {
        AnalyzerCore current = core;
        if (current == null) return true;
        synchronized (current) {
            return current.isEnabled(MODULE_ID) && current.getBoolean(MODULE_ID, settingId);
        }
    }

    public record Counters(long blockedPayloads, long rewrittenBrands, long hiddenPacks,
                           long declinedQueries) { }

    public static Counters counters() {
        return new Counters(blockedPayloads.get(), rewrittenBrands.get(), hiddenPacks.get(), declinedQueries.get());
    }

    /** Null means locally suppressed. Objects are immutable and unsuppressed packets retain identity. */
    public static Packet<?> filter(Packet<?> packet, boolean local, boolean clientbound) {
        if (local || !clientbound) return packet;
        return filter(packet, local, clientbound, options());
    }

    public static Packet<?> filter(Packet<?> packet, boolean local, boolean clientbound,
                                   PrivacyPolicy.Options options) {
        if (!PrivacyPolicy.active(options, local, clientbound)) return packet;
        if (packet instanceof CustomPayloadC2SPacket custom) {
            String channel = custom.payload().getId().id().toString();
            if (!PrivacyPolicy.allowsPayload(channel, options)) {
                blockedPayloads.incrementAndGet();
                return null;
            }
            if (options.vanillaBrand() && "minecraft:brand".equals(channel)) {
                if (custom.payload() instanceof BrandCustomPayload brand && "vanilla".equals(brand.brand())) return packet;
                rewrittenBrands.incrementAndGet();
                return new CustomPayloadC2SPacket(new BrandCustomPayload("vanilla"));
            }
        } else if (packet instanceof SelectKnownPacksC2SPacket packs && options.hideKnownPacks()) {
            String vanillaVersion = SharedConstants.getGameVersion().getId();
            var filtered = packs.knownPacks().stream()
                    .filter(pack -> PrivacyPolicy.allowsKnownPack(pack.namespace(), pack.id(), pack.version(), vanillaVersion, options)).toList();
            if (filtered.size() != packs.knownPacks().size()) {
                hiddenPacks.addAndGet(packs.knownPacks().size() - filtered.size());
                return new SelectKnownPacksC2SPacket(filtered);
            }
        } else if (packet instanceof LoginQueryResponseC2SPacket query
                && options.blockLoginQueries() && query.response() != null) {
            // Vanilla's unsupported-query response completes the transaction without sending mod data.
            declinedQueries.incrementAndGet();
            return new LoginQueryResponseC2SPacket(query.queryId(), null);
        }
        return packet;
    }

    public static void attach(Channel channel, ClientConnection connection) {
        if (channel.pipeline().get(HANDLER_NAME) != null) return;
        channel.pipeline().addLast(HANDLER_NAME, new Outbound(connection));
    }

    /** Tail position intercepts all Packet writes, including queued writes and callback fallback packets. */
    private static final class Outbound extends ChannelOutboundHandlerAdapter {
        private final ClientConnection connection;
        private Outbound(ClientConnection connection) { this.connection = connection; }

        @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
            if (message instanceof Packet<?> packet) {
                // The handler is present before channelActive propagates. At that moment the
                // native connection's channel field may not yet be initialized, so use the channel itself.
                boolean local = context.channel() instanceof LocalChannel || context.channel() instanceof LocalServerChannel;
                Packet<?> filtered = filter(packet, local, connection.getSide() == NetworkSide.CLIENTBOUND);
                if (filtered == null) {
                    // Complete the existing write future: callbacks run normally and no failure packet leaks.
                    promise.setSuccess();
                    return;
                }
                message = filtered;
            }
            super.write(context, message, promise);
        }
    }
}
