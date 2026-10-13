package dev.analyzer.privacy;

import java.io.IOException;
import java.io.InputStream;
import java.io.FilterInputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedHashMap;
import java.util.Map;

/** A single server-pack request, with native automatic redirect following replaced by checked hops. */
public final class PackPrivacyConnection extends HttpURLConnection {
    @FunctionalInterface interface Opener { URLConnection open(URL url, Proxy proxy) throws IOException; }
    @FunctionalInterface public interface Resolver { InetAddress[] resolve(String host) throws IOException; }
    private final Proxy proxy;
    private final Runnable blocked;
    private final Opener opener;
    private final Resolver resolver;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private HttpURLConnection delegate;
    private InputStream input;

    public PackPrivacyConnection(URL url, Proxy proxy, Runnable blocked) throws IOException {
        this(url, proxy, blocked, URL::openConnection, InetAddress::getAllByName);
    }

    public PackPrivacyConnection(URL url, Proxy proxy, Runnable blocked, Resolver resolver) throws IOException {
        this(url, proxy, blocked, URL::openConnection, resolver);
    }

    PackPrivacyConnection(URL url, Proxy proxy, Runnable blocked, Opener opener, Resolver resolver) throws IOException {
        super(url);
        this.proxy = proxy;
        this.blocked = blocked;
        this.opener = opener;
        this.resolver = resolver;
        check(url);
    }

    private void checkResolved(URL destination) throws IOException {
        check(destination);
        // Checked immediately before openConnection; the native connection does its own resolution.
        // That second lookup leaves a rebinding race: this is not an address-pinned transport.
        InetAddress[] addresses = resolver.resolve(destination.getHost());
        if (addresses.length == 0) throw new IOException("Resource-pack hostname has no addresses");
        for (InetAddress address : addresses) {
            if (address != null && PackPrivacyPolicy.publicAddress(address.getAddress())) continue;
            blocked.run();
            throw new IOException("Server resource-pack hostname resolves to a blocked destination");
        }
    }

    private void check(URL destination) throws IOException {
        if (PackPrivacyPolicy.allowed(destination)) return;
        blocked.run();
        throw new IOException("Server resource-pack URL targets a blocked destination");
    }

    @Override public void setRequestProperty(String key, String value) {
        if (connected) throw new IllegalStateException("Already connected");
        headers.put(key, value);
    }

    @Override public String getRequestProperty(String key) { return headers.get(key); }

    @Override public Map<String, java.util.List<String>> getRequestProperties() {
        var properties = new LinkedHashMap<String, java.util.List<String>>();
        headers.forEach((key, value) -> properties.put(key, java.util.List.of(value)));
        return Map.copyOf(properties);
    }

    @Override public synchronized InputStream getInputStream() throws IOException {
        if (input != null) return input;
        try {
            URL destination = url;
            for (int redirects = 0; redirects <= 10; redirects++) {
                checkResolved(destination);
                URLConnection opened = opener.open(destination, proxy);
                if (!(opened instanceof HttpURLConnection http)) throw new IOException("Resource-pack HTTP connection required");
                delegate = http;
                http.setInstanceFollowRedirects(false);
                http.setConnectTimeout(getConnectTimeout());
                http.setReadTimeout(getReadTimeout());
                http.setUseCaches(getUseCaches());
                http.setRequestMethod("GET");
                headers.forEach(http::setRequestProperty);
                int status = http.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = http.getHeaderField("Location");
                    if (!getInstanceFollowRedirects()) return finish(http);
                    if (location == null || location.isBlank()) throw new IOException("Resource-pack redirect has no location");
                    URL next = new URL(destination, location);
                    http.disconnect();
                    delegate = null;
                    check(next);
                    destination = next;
                    continue;
                }
                return finish(http);
            }
            throw new IOException("Too many resource-pack redirects");
        } catch (IOException | RuntimeException | Error failure) {
            disconnect();
            throw failure;
        }
    }

    private InputStream finish(HttpURLConnection http) throws IOException {
        input = new FilterInputStream(http.getInputStream()) {
            @Override public void close() throws IOException { try { super.close(); } finally { http.disconnect(); } }
        };
        connected = true;
        return input;
    }

    @Override public long getContentLengthLong() { return delegate == null ? -1 : delegate.getContentLengthLong(); }
    @Override public InputStream getErrorStream() { return delegate == null ? null : delegate.getErrorStream(); }
    @Override public String getHeaderField(String name) { return delegate == null ? null : delegate.getHeaderField(name); }
    @Override public boolean usingProxy() { return proxy != null && proxy != Proxy.NO_PROXY; }
    @Override public void connect() throws IOException { getInputStream(); }
    @Override public void disconnect() {
        InputStream current = input;
        input = null;
        if (current != null) try { current.close(); } catch (IOException ignored) { }
        if (delegate != null) delegate.disconnect();
        delegate = null;
        connected = false;
    }
}
