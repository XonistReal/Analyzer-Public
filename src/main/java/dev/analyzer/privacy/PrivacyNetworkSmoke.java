package dev.analyzer.privacy;

import dev.analyzer.AnalyzerClient;
import dev.analyzer.core.AnalyzerCore;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.buffer.Unpooled;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.BrandCustomPayload;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.c2s.config.SelectKnownPacksC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginQueryResponseC2SPacket;
import net.minecraft.registry.VersionedIdentifier;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Native packet, codec and transformed-connection checks. No remote server or account is contacted. */
public final class PrivacyNetworkSmoke {
    private static int checks;
    private PrivacyNetworkSmoke() { }

    public static int run() {
        checks = 0;
        AnalyzerCore fixture = new AnalyzerCore(MinecraftClient.getInstance().runDirectory.toPath().resolve("privacy-fixture-unused"));
        ServerPrivacy.initialize(fixture);
        try {
            check(ServerPrivacy.options().equals(PrivacyPolicy.Options.DEFAULT), "Default module uses all network protections");
            check(ServerPrivacy.enabled(), "Privacy enabled by default");
            for (String setting : List.of("vanilla-brand", "block-mod-channels", "vanilla-only", "hide-known-packs", "block-login-queries")) {
                check(ServerPrivacy.option(setting), "Default network option enabled: " + setting);
            }
            metadataPackets();
            isolatedConnections(fixture);
            localConnection();
            fixture.setValue(ServerPrivacy.MODULE_ID, "vanilla-only", false);
            check(ServerPrivacy.filter(payload("voicechat:secret"), false, true) != null, "Live compatibility edit takes effect immediately");
            check(ServerPrivacy.filter(payload("minecraft:register"), false, true) == null, "Compatibility still blocks channel inventory");
            fixture.setValue(ServerPrivacy.MODULE_ID, "block-mod-channels", false);
            check(ServerPrivacy.filter(payload("fabric:hello"), false, true) != null, "Intentional loader-channel compatibility applies");
            fixture.reset(ServerPrivacy.MODULE_ID);
            check(ServerPrivacy.filter(payload("voicechat:secret"), false, true) == null, "Reset immediately restores strict filtering");
            fixture.setEnabled(ServerPrivacy.MODULE_ID, false);
            check(!ServerPrivacy.enabled(), "Disabled module reports disabled");
            check(!ServerPrivacy.option("vanilla-brand"), "Shared option gate follows module state");
            var unfiltered = payload("fabric:hello");
            check(ServerPrivacy.filter(unfiltered, false, true) == unfiltered, "Disabled module preserves actual packet identity");
            AnalyzerClient.LOGGER.info("ANALYZER_PRIVACY_NETWORK_SMOKE_COMPLETE {} assertions", checks);
            return checks;
        } finally {
            ServerPrivacy.initialize(AnalyzerClient.CORE);
        }
    }

    private static void metadataPackets() {
        var stock = new CustomPayloadC2SPacket(new BrandCustomPayload("vanilla"));
        check(ServerPrivacy.filter(stock, false, true) == stock, "Already-stock brand packet keeps identity");
        var modded = new CustomPayloadC2SPacket(new BrandCustomPayload("fabric+analyzer"));
        var replaced = (CustomPayloadC2SPacket) ServerPrivacy.filter(modded, false, true);
        check(((BrandCustomPayload) replaced.payload()).brand().equals("vanilla"), "Native brand payload rewritten");
        check(((BrandCustomPayload) modded.payload()).brand().equals("fabric+analyzer"), "Original brand object unmodified");
        var buffer = new PacketByteBuf(Unpooled.buffer());
        try {
            CustomPayloadC2SPacket.CODEC.encode(buffer, replaced);
            var decoded = CustomPayloadC2SPacket.CODEC.decode(buffer);
            check(((BrandCustomPayload) decoded.payload()).brand().equals("vanilla"), "Actual packet codec transmits only vanilla brand");
            check(buffer.readableBytes() == 0, "Brand codec contains no appended mod bytes");
        } finally { buffer.release(); }
        for (String channel : List.of("minecraft:register", "minecraft:unregister", "c:register", "c:version",
                "fabric:registry/sync", "forge:handshake", "quilt:hello", "analyzer:hello", "unknownmod:hello", "minecraft:modlist")) {
            var modPacket = payload(channel);
            check(ServerPrivacy.filter(modPacket, false, true) == null, "Remote native payload suppressed: " + channel);
            check(ServerPrivacy.filter(modPacket, true, true) == modPacket, "Local native payload preserved: " + channel);
            check(ServerPrivacy.filter(modPacket, false, false) == modPacket, "Server side packet preserved: " + channel);
        }
        var keepAlive = new KeepAliveC2SPacket(0xCAFE1234L);
        check(ServerPrivacy.filter(keepAlive, false, true) == keepAlive, "Keepalive retains exact native packet");
        var packStatus = new ResourcePackStatusC2SPacket(UUID.randomUUID(), ResourcePackStatusC2SPacket.Status.DECLINED);
        check(ServerPrivacy.filter(packStatus, false, true) == packStatus, "Normal resource-pack status remains intact");
        var core = new VersionedIdentifier("minecraft", "core", "1.21.4");
        var modPack = new VersionedIdentifier("testmod", "client_assets", "secret-version");
        var namespaceSpoof = new VersionedIdentifier("minecraft", "client_mod", "secret-version");
        var versionSpoof = new VersionedIdentifier("minecraft", "core", "secret-version");
        var knownPacks = new SelectKnownPacksC2SPacket(List.of(modPack, core, namespaceSpoof, versionSpoof));
        var filtered = (SelectKnownPacksC2SPacket) ServerPrivacy.filter(knownPacks, false, true);
        check(filtered.knownPacks().equals(List.of(core)), "Actual known-pack response excludes mod names and versions");
        check(knownPacks.knownPacks().size() == 4, "Original known-pack response unmodified");
        check(!filtered.knownPacks().contains(versionSpoof), "Stock pack ID with a mod version cannot leak through");
        var knownPackBuffer = Unpooled.buffer();
        try {
            SelectKnownPacksC2SPacket.CODEC.encode(knownPackBuffer, filtered);
            var decodedPacks = SelectKnownPacksC2SPacket.CODEC.decode(knownPackBuffer);
            check(decodedPacks.knownPacks().equals(List.of(core)), "Actual known-pack codec transmits only the genuine core tuple");
            check(knownPackBuffer.readableBytes() == 0, "Known-pack codec has no appended mod metadata");
        } finally { knownPackBuffer.release(); }
        check(ServerPrivacy.filter(filtered, false, true) == filtered, "Filtering stock known pack is stable");
        var onlyMods = new SelectKnownPacksC2SPacket(List.of(modPack));
        check(((SelectKnownPacksC2SPacket) ServerPrivacy.filter(onlyMods, false, true)).knownPacks().isEmpty(),
                "Empty known-pack answer is sent instead of dropping required configuration response");
        var query = new LoginQueryResponseC2SPacket(938, data -> data.writeString("client_mod_inventory"));
        var declined = (LoginQueryResponseC2SPacket) ServerPrivacy.filter(query, false, true);
        check(declined.queryId() == 938 && declined.response() == null, "Native login reply preserves transaction and declines data");
        check(query.response() != null, "Original login reply unmodified");
        check(ServerPrivacy.filter(declined, false, true) == declined, "Vanilla unsupported login reply retained");
        var queryBuffer = new PacketByteBuf(Unpooled.buffer());
        try {
            LoginQueryResponseC2SPacket.CODEC.encode(queryBuffer, declined);
            var decoded = LoginQueryResponseC2SPacket.CODEC.decode(queryBuffer);
            check(decoded.queryId() == 938 && decoded.response() == null, "Actual login codec omits plugin response bytes");
            check(queryBuffer.readableBytes() == 0, "No query content remains after codec round trip");
        } finally { queryBuffer.release(); }
        var withoutBrand = new PrivacyPolicy.Options(true, false, true, true, true, true);
        check(ServerPrivacy.filter(modded, false, true, withoutBrand) == modded, "Brand switch independently preserves brand");
        var withoutPackFilter = new PrivacyPolicy.Options(true, true, true, true, false, true);
        check(ServerPrivacy.filter(knownPacks, false, true, withoutPackFilter) == knownPacks, "Known-pack switch independently preserves declarations");
        var withoutQueryFilter = new PrivacyPolicy.Options(true, true, true, true, true, false);
        check(ServerPrivacy.filter(query, false, true, withoutQueryFilter) == query, "Login-query switch independently preserves response");
    }

    private static void isolatedConnections(AnalyzerCore fixture) {
        ClientConnection connection = new ClientConnection(NetworkSide.CLIENTBOUND);
        var pending = payload("queuedmod:inventory");
        AtomicInteger callbacks = new AtomicInteger();
        connection.send(pending, PacketCallbacks.always(callbacks::incrementAndGet));
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            check(channel.pipeline().get(ServerPrivacy.HANDLER_NAME) != null, "Actual transformed connection installs privacy handler");
            ServerPrivacy.attach(channel, connection);
            check(channel.pipeline().names().stream().filter(ServerPrivacy.HANDLER_NAME::equals).count() == 1,
                    "Repeated attachment has exactly one interceptor");
            connection.tick();
            channel.runPendingTasks();
            check(channel.readOutbound() == null, "Queued pre-activation packet also filtered");
            check(callbacks.get() == 1, "Suppressed queued write completes native packet callback once");
            var promise = channel.newPromise();
            channel.pipeline().writeAndFlush(payload("directmod:hello"), promise);
            channel.runPendingTasks();
            check(promise.isSuccess(), "Direct Netty packet write completes promise successfully");
            check(channel.readOutbound() == null, "Direct Netty write cannot bypass privacy policy");
            connection.send(new CustomPayloadC2SPacket(new BrandCustomPayload("fabric")), PacketCallbacks.always(callbacks::incrementAndGet), false);
            connection.flush();
            channel.runPendingTasks();
            var brand = (CustomPayloadC2SPacket) channel.readOutbound();
            check(brand != null && ((BrandCustomPayload) brand.payload()).brand().equals("vanilla"), "Queued/send/flush flow rewrites brand before encoding");
            check(callbacks.get() == 2, "Rewritten write invokes callback once");
            var alive = new KeepAliveC2SPacket(9876);
            check(channel.writeOutbound(alive), "Normal packet produces outbound traffic");
            check(channel.readOutbound() == alive, "Normal packet preserved through live Netty handler");
            fixture.setEnabled(ServerPrivacy.MODULE_ID, false);
            var compatible = payload("voicechat:hello");
            check(channel.writeOutbound(compatible), "Module disable applies to already-connected channel");
            check(channel.readOutbound() == compatible, "Disabled channel preserves packet identity");
            fixture.setEnabled(ServerPrivacy.MODULE_ID, true);
            check(!channel.writeOutbound(compatible), "Module enable immediately protects existing channel");
        } finally { channel.finishAndReleaseAll(); }
        EmbeddedChannel serverSide = new EmbeddedChannel(new ClientConnection(NetworkSide.SERVERBOUND));
        try {
            var serverPacket = payload("servermod:announcement");
            check(serverSide.writeOutbound(serverPacket), "Server-bound connection side is outside client privacy filter");
            check(serverSide.readOutbound() == serverPacket, "Server traffic identity retained");
        } finally { serverSide.finishAndReleaseAll(); }
    }

    private static CustomPayloadC2SPacket payload(String channel) {
        return new CustomPayloadC2SPacket(new DiagnosticPayload(Identifier.of(channel)));
    }

    /** Exercises the same Netty transport class used by an integrated server, without a TCP socket. */
    private static void localConnection() {
        var incoming = new LinkedBlockingQueue<Packet<?>>();
        var group = new DefaultEventLoopGroup(1, (java.util.concurrent.ThreadFactory) task -> {
            Thread thread = new Thread(task, "analyzer-privacy-local-test");
            thread.setDaemon(true);
            return thread;
        });
        Channel server = null, client = null;
        try {
            LocalAddress address = new LocalAddress("analyzer-privacy-" + UUID.randomUUID());
            server = new ServerBootstrap().group(group).channel(LocalServerChannel.class)
                    .childHandler(new ChannelInitializer<LocalChannel>() {
                        @Override protected void initChannel(LocalChannel channel) {
                            channel.pipeline().addLast(new SimpleChannelInboundHandler<Packet<?>>() {
                                @Override protected void channelRead0(ChannelHandlerContext context, Packet<?> packet) {
                                    incoming.add(packet);
                                }
                            });
                        }
                    }).bind(address).sync().channel();
            ClientConnection connection = new ClientConnection(NetworkSide.CLIENTBOUND);
            client = new Bootstrap().group(group).channel(LocalChannel.class).handler(connection).connect(address).sync().channel();
            // A connect future can complete before its channelActive callback returns.
            // Wait for that event-loop turn before checking the native channel field.
            client.eventLoop().submit(() -> { }).sync();
            check(connection.isLocal(), "Actual integrated-server transport is local");
            check(client.pipeline().get(ServerPrivacy.HANDLER_NAME) != null, "Local connection has the same native interception hook");
            var moddedBrand = new CustomPayloadC2SPacket(new BrandCustomPayload("fabric"));
            client.writeAndFlush(moddedBrand).sync();
            check(incoming.poll(2, TimeUnit.SECONDS) == moddedBrand, "Native LocalChannel keeps modded brand unchanged");
            var registration = payload("minecraft:register");
            client.writeAndFlush(registration).sync();
            check(incoming.poll(2, TimeUnit.SECONDS) == registration, "Native LocalChannel keeps channel registration unchanged");
            var modPayload = payload("integrated_mod:data");
            client.writeAndFlush(modPayload).sync();
            check(incoming.poll(2, TimeUnit.SECONDS) == modPayload, "Native LocalChannel keeps mod-specific gameplay traffic unchanged");
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new AssertionError("Privacy native LocalChannel fixture failed", failure);
        } finally {
            if (client != null) client.close().syncUninterruptibly();
            if (server != null) server.close().syncUninterruptibly();
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    private record DiagnosticPayload(Identifier channel) implements CustomPayload {
        @Override public Id<? extends CustomPayload> getId() { return new Id<>(channel); }
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError("Privacy network: " + message);
    }
}
