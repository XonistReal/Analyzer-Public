package dev.analyzer.privacy;

import com.google.common.hash.Hashing;
import dev.analyzer.AnalyzerClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.server.ServerResourcePackLoader;
import net.minecraft.client.session.Session;
import net.minecraft.util.Downloader;
import net.minecraft.util.NetworkUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Opt-in native checks use transformed Minecraft classes and exclusively stub HTTP connections. */
public final class PackPrivacySmoke {
    private static int assertions;
    private PackPrivacySmoke() { }

    public static int run(MinecraftClient client) throws Exception {
        assertions = 0;
        var core = AnalyzerClient.CORE;
        boolean enabled = core.isEnabled(ServerPrivacy.MODULE_ID);
        boolean originalBlock = core.getBoolean(ServerPrivacy.MODULE_ID, "block-local-pack-urls");
        boolean originalScrub = core.getBoolean(ServerPrivacy.MODULE_ID, "scrub-pack-headers");
        Path directory = Files.createTempDirectory("analyzer-pack-native-").toAbsolutePath().normalize();
        try (var downloader = new Downloader(directory)) {
            core.setEnabled(ServerPrivacy.MODULE_ID, true);
            core.setValue(ServerPrivacy.MODULE_ID, "block-local-pack-urls", true);
            core.setValue(ServerPrivacy.MODULE_ID, "scrub-pack-headers", true);
            var factory = ServerResourcePackLoader.class.getDeclaredMethod("createDownloadQueuer", Downloader.class,
                    Executor.class, Session.class, Proxy.class);
            factory.setAccessible(true);
            Object queuer = factory.invoke(client.getServerResourcePackProvider(), downloader, (Executor) Runnable::run,
                    client.getSession(), Proxy.NO_PROXY);
            require(((PackPrivacyDownloadSource) downloader).analyzer$isServerPackDownloader(), "Actual pack-loader factory marks only its downloader");
            AtomicInteger resolutions = new AtomicInteger();
            ((PackPrivacyDownloadSource) downloader).analyzer$packFixtureResolver(host -> {
                resolutions.incrementAndGet();
                return new InetAddress[] {InetAddress.getByAddress(new byte[] {8, 8, 8, 8})};
            });
            var getHeaders = queuer.getClass().getDeclaredMethod("getHeaders");
            getHeaders.setAccessible(true);
            long beforeHeaders = PackPrivacy.counters().scrubbedHeaders();
            @SuppressWarnings("unchecked") var headers = (Map<String, String>) getHeaders.invoke(queuer);
            require(headers.keySet().stream().noneMatch(key -> key.equalsIgnoreCase("X-Minecraft-Username")
                    || key.equalsIgnoreCase("X-Minecraft-UUID")), "Actual native pack headers omit username and UUID");
            require(headers.containsKey("X-Minecraft-Pack-Format") && headers.containsKey("X-Minecraft-Version"), "Actual native compatibility headers remain");
            require(PackPrivacy.counters().scrubbedHeaders() == beforeHeaders + 1, "Native anonymous header mixin executed");

            var observed = new AtomicReference<Stub>();
            AtomicInteger opened = new AtomicInteger();
            URL url = new URL(null, "https://cdn.example.com/native-pack", new URLStreamHandler() {
                @Override protected URLConnection openConnection(URL request) { return open(request); }
                @Override protected URLConnection openConnection(URL request, Proxy proxy) { return open(request); }
                private URLConnection open(URL request) { opened.incrementAndGet(); Stub stub = new Stub(request); observed.set(stub); return stub; }
            });
            byte[] payload = {80, 75, 3, 4, 42};
            Map<String, String> identifying = Map.of("X-Minecraft-Username", "fixture-name", "X-Minecraft-UUID", "fixture-uuid",
                    "User-Agent", "Analyzer fixture", "Accept", "application/zip");
            AtomicInteger finishes = new AtomicInteger();
            AtomicBoolean scopedFinish = new AtomicBoolean();
            var listener = new NetworkUtils.DownloadListener() {
                public void onStart() { }
                public void onContentLength(OptionalLong length) { require(length.orElse(-1) == payload.length, "Native declared content length preserved"); }
                public void onProgress(long count) { }
                public void onFinish(boolean success) { require(success, "Native successful download status preserved"); finishes.incrementAndGet(); scopedFinish.set(PackPrivacy.scoped()); }
            };
            var config = new Downloader.Config(Hashing.sha1(), 1024, identifying, Proxy.NO_PROXY, listener);
            UUID firstId = UUID.randomUUID();
            long scopesBefore = PackPrivacy.counters().scopes(), connectionsBefore = PackPrivacy.counters().connections();
            var result = downloader.downloadAsync(config, Map.of(firstId, new Downloader.DownloadEntry(url, Hashing.sha1().hashBytes(payload))))
                    .get(5, TimeUnit.SECONDS);
            require(result.failed().isEmpty() && result.downloaded().containsKey(firstId), "Original Downloader result preserved");
            require(java.util.Arrays.equals(payload, Files.readAllBytes(result.downloaded().get(firstId))), "Original native hash/download bytes preserved");
            require(PackPrivacy.counters().scopes() == scopesBefore + 1 && PackPrivacy.counters().connections() == connectionsBefore + 1,
                    "Actual Downloader and NetworkUtils mixins both executed");
            require(opened.get() == 1 && finishes.get() == 1 && scopedFinish.get(), "Native request and callback happen once inside pack scope");
            require(resolutions.get() == 1, "Actual pack connection checks stub DNS immediately before opening without external lookup");
            require(!observed.get().getInstanceFollowRedirects(), "Actual HTTP delegate cannot auto-follow unchecked redirects");
            require(!observed.get().requests.containsKey("X-Minecraft-UUID") && !observed.get().requests.containsKey("X-Minecraft-Username"),
                    "Identifying headers absent at native HTTP boundary");
            require(observed.get().requests.get("User-Agent").startsWith("Minecraft Java/"), "Native HTTP boundary uses stock user agent");

            core.setEnabled(ServerPrivacy.MODULE_ID, false);
            @SuppressWarnings("unchecked") var originalHeaders = (Map<String, String>) getHeaders.invoke(queuer);
            require(originalHeaders.containsKey("X-Minecraft-UUID") && originalHeaders.containsKey("X-Minecraft-Username"), "Disabled privacy bypasses native header filter");
            UUID secondId = UUID.randomUUID();
            connectionsBefore = PackPrivacy.counters().connections();
            result = downloader.downloadAsync(config, Map.of(secondId, new Downloader.DownloadEntry(url, Hashing.sha1().hashBytes(payload))))
                    .get(5, TimeUnit.SECONDS);
            require(result.failed().isEmpty() && PackPrivacy.counters().connections() == connectionsBefore, "Disabled privacy bypasses native URL wrapper");
            require(resolutions.get() == 1, "Disabled privacy bypasses destination DNS check");
            require(observed.get().getInstanceFollowRedirects() && observed.get().requests.get("User-Agent").equals("Analyzer fixture"),
                    "Disabled privacy retains original redirect and header behavior");
            require(!PackPrivacy.scoped(), "Request scope never leaks into caller thread");

            core.setEnabled(ServerPrivacy.MODULE_ID, true);
            try (var unrelated = new Downloader(directory.resolve("unrelated"))) {
                require(!((PackPrivacyDownloadSource) unrelated).analyzer$isServerPackDownloader(), "Unrelated Downloader starts unmarked");
                UUID unrelatedId = UUID.randomUUID();
                connectionsBefore = PackPrivacy.counters().connections();
                scopesBefore = PackPrivacy.counters().scopes();
                result = unrelated.downloadAsync(config, Map.of(unrelatedId, new Downloader.DownloadEntry(url, Hashing.sha1().hashBytes(payload))))
                        .get(5, TimeUnit.SECONDS);
                require(result.failed().isEmpty() && PackPrivacy.counters().connections() == connectionsBefore
                        && PackPrivacy.counters().scopes() == scopesBefore && !scopedFinish.get(), "Unrelated native downloads retain original URL, headers and scope");
            }
        } finally {
            core.setEnabled(ServerPrivacy.MODULE_ID, enabled);
            core.setValue(ServerPrivacy.MODULE_ID, "block-local-pack-urls", originalBlock);
            core.setValue(ServerPrivacy.MODULE_ID, "scrub-pack-headers", originalScrub);
            // Every entry belongs to this newly-created private fixture directory.
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    if (!path.toAbsolutePath().normalize().startsWith(directory)) throw new IOException("Fixture cleanup escaped its directory");
                    Files.deleteIfExists(path);
                }
            }
        }
        return assertions;
    }

    private static synchronized void require(boolean pass, String label) { if (!pass) throw new AssertionError(label); assertions++; }
    private static final class Stub extends HttpURLConnection {
        final Map<String, String> requests = new LinkedHashMap<>();
        Stub(URL url) { super(url); }
        @Override public void setRequestProperty(String key, String value) { requests.put(key, value); }
        @Override public int getResponseCode() { return 200; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[] {80, 75, 3, 4, 42}); }
        @Override public long getContentLengthLong() { return 5; }
        @Override public void connect() { }
        @Override public void disconnect() { }
        @Override public boolean usingProxy() { return false; }
    }
}
