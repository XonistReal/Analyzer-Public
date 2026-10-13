package dev.analyzer.privacy;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Resource-pack-only request scopes; unrelated downloads never enter this context. */
public final class PackPrivacy {
    private record Options(boolean blockUrls, boolean scrubHeaders, PackPrivacyConnection.Resolver resolver) { }
    private static final ThreadLocal<Options> scope = new ThreadLocal<>();
    private static final AtomicLong scopes = new AtomicLong(), connections = new AtomicLong(), blocked = new AtomicLong(), headers = new AtomicLong();
    private PackPrivacy() { }

    public static Scope enter(boolean blockUrls, boolean scrubHeaders) {
        return enter(blockUrls, scrubHeaders, null);
    }
    public static Scope enter(boolean blockUrls, boolean scrubHeaders, PackPrivacyConnection.Resolver resolver) {
        Options old = scope.get();
        scope.set(new Options(blockUrls, scrubHeaders, resolver));
        scopes.incrementAndGet();
        return new Scope(old);
    }

    public static final class Scope implements AutoCloseable {
        private final Options old;
        private boolean closed;
        private Scope(Options old) { this.old = old; }
        @Override public void close() {
            if (closed) return;
            closed = true;
            if (old == null) scope.remove(); else scope.set(old);
        }
    }

    public static boolean scoped() { return scope.get() != null; }
    public static boolean guardingUrls() { Options options = scope.get(); return options != null && options.blockUrls; }
    public static URLConnection open(URL url, Proxy proxy) throws IOException {
        connections.incrementAndGet();
        Options options = scope.get();
        return options != null && options.resolver != null
                ? new PackPrivacyConnection(url, proxy, blocked::incrementAndGet, options.resolver)
                : new PackPrivacyConnection(url, proxy, blocked::incrementAndGet);
    }
    public static Map<String, String> scrub(Map<String, String> original, String version) {
        headers.incrementAndGet();
        return PackPrivacyPolicy.scrubHeaders(original, version);
    }
    public record Counters(long scopes, long connections, long blockedUrls, long scrubbedHeaders) { }
    public static Counters counters() { return new Counters(scopes.get(), connections.get(), blocked.get(), headers.get()); }
}
