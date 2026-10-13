package dev.analyzer.privacy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** No network requests: literal URL policy, immutable headers and redirect decisions use stub connections. */
public final class PackPrivacySelfTest {
    private static int assertions;
    private static final PackPrivacyConnection.Resolver PUBLIC = host -> new InetAddress[] {InetAddress.getByAddress(new byte[] {8, 8, 8, 8})};
    public static void main(String[] args) throws Exception {
        String[] blocked = {"http://localhost/pack", "https://LOCALHOST./pack", "http://a.localhost/", "https://router.local/",
                "http://router/", "http://127.0.0.1/", "http://127.10.1.2/", "http://0.0.0.0/", "http://10.2.3.4/",
                "http://172.16.0.1/", "http://172.31.255.255/", "http://192.168.1.1/", "http://169.254.169.254/",
                "http://100.64.0.1/", "http://198.18.0.1/", "http://224.0.0.1/", "http://255.255.255.255/",
                "http://2130706433/", "http://127.1/", "http://0177.0.0.1/", "http://0x7f000001/", "http://0x7f.0.0.1/",
                "http://[::1]/", "http://[::]/", "http://[::ffff:127.0.0.1]/", "http://[::ffff:7f00:1]/",
                "http://[::192.168.1.1]/", "http://[fc00::1]/", "http://[fd11::1]/", "http://[fe80::1]/", "http://[fec0::1]/",
                "http://[ff02::1]/", "http://[fe80::1%25eth0]/", "ftp://example.com/", "file:///tmp/pack.zip",
                "https://192.0.2.1/", "https://198.51.100.1/", "https://203.0.113.1/", "https://[2001:db8::1]/", "https://[3fff::1]/",
                "https://user:secret@example.com/pack", "http://example.com:0/", "http://example.com:65536/", "http://example..com/"};
        for (String value : blocked) check(!PackPrivacyPolicy.allowed(URI.create(value)), "Blocked destination " + value);
        for (String value : List.of("https://cdn.example.com/pack.zip?signature=opaque", "http://example.com:8080/pack",
                "https://8.8.8.8/pack", "https://[2606:4700:4700::1111]/pack", "https://example.com./pack"))
            check(PackPrivacyPolicy.allowed(URI.create(value)), "Public URL allowed " + value);
        check(!PackPrivacyPolicy.allowed((URI) null), "Null URL rejected");

        Map<String, String> original = Map.of("x-minecraft-username", "PrivateName", "X-Minecraft-UUID", "private-id",
                "USER-AGENT", "Analyzer/private", "X-Minecraft-Version", "1.21.4", "Accept", "application/zip");
        Map<String, String> safe = PackPrivacyPolicy.scrubHeaders(original, "1.21.4");
        check(safe.size() == 3 && "Minecraft Java/1.21.4".equals(safe.get("User-Agent")), "Stock version UA replaces mod UA");
        check("application/zip".equals(safe.get("Accept")) && "1.21.4".equals(safe.get("X-Minecraft-Version")), "Compatibility headers retained");
        check(original.size() == 5 && "PrivateName".equals(original.get("x-minecraft-username")), "Input headers never mutated");
        try { safe.put("X-Minecraft-Username", "bad"); throw new AssertionError("Mutable headers"); }
        catch (UnsupportedOperationException expected) { assertions++; }

        AtomicInteger refused = new AtomicInteger();
        List<String> opened = new ArrayList<>();
        List<Stub> stubs = new ArrayList<>();
        PackPrivacyConnection.Opener redirect = (url, proxy) -> {
            opened.add(url.toString());
            Stub stub = new Stub(url, opened.size() == 1 ? 302 : 200, opened.size() == 1 ? "/final.zip" : null);
            stubs.add(stub);
            return stub;
        };
        var connection = new PackPrivacyConnection(new URL("https://cdn.example.com/start"), Proxy.NO_PROXY, refused::incrementAndGet, redirect, PUBLIC);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/zip");
        check(connection.getInputStream().read() == 42, "Allowed redirected body returned");
        check(opened.equals(List.of("https://cdn.example.com/start", "https://cdn.example.com/final.zip")), "Relative redirect checked and resolved");
        check(stubs.getFirst().closed && stubs.stream().noneMatch(Stub::getInstanceFollowRedirects), "Automatic redirects disabled and old connection closed");
        check("application/zip".equals(stubs.getLast().getRequestProperty("Accept")), "Headers forwarded through validated hop");
        check(connection.getContentLengthLong() == 1 && refused.get() == 0, "Final content-length and allowed accounting preserved");
        connection.getInputStream().close();
        check(stubs.getLast().closed, "Closing successful body closes its delegate connection");

        for (String destination : List.of("http://127.0.0.1/secrets", "http://[::ffff:127.0.0.1]/", "file:///private", "https://user@cdn.example.com/")) {
            opened.clear();
            var guarded = new PackPrivacyConnection(new URL("https://cdn.example.com/start"), Proxy.NO_PROXY, refused::incrementAndGet,
                    (url, proxy) -> { opened.add(url.toString()); return new Stub(url, 302, destination); }, PUBLIC);
            expectBlocked(guarded);
            check(opened.size() == 1, "Blocked redirected destination never opened: " + destination);
        }
        opened.clear();
        try {
            new PackPrivacyConnection(new URL("http://192.168.1.1/start"), Proxy.NO_PROXY, refused::incrementAndGet,
                    (url, proxy) -> { opened.add(url.toString()); return new Stub(url, 200, null); }, PUBLIC);
            throw new AssertionError("Initial private URL allowed");
        } catch (IOException expected) { check(opened.isEmpty(), "Initial private destination never opened"); }

        AtomicInteger loops = new AtomicInteger();
        var looping = new PackPrivacyConnection(new URL("https://cdn.example.com/start"), Proxy.NO_PROXY, refused::incrementAndGet,
                (url, proxy) -> { loops.incrementAndGet(); return new Stub(url, 307, "/start"); }, PUBLIC);
        expectBlocked(looping);
        check(loops.get() == 11, "Redirect loop is bounded");

        for (byte[] privateIp : List.of(new byte[] {127, 0, 0, 1}, new byte[] {10, 0, 0, 1}, new byte[] {(byte) 192, (byte) 168, 1, 1})) {
            AtomicInteger requests = new AtomicInteger();
            var resolvedPrivate = new PackPrivacyConnection(new URL("https://public.example.com/pack"), Proxy.NO_PROXY, refused::incrementAndGet,
                    (url, proxy) -> { requests.incrementAndGet(); return new Stub(url, 200, null); },
                    host -> new InetAddress[] {InetAddress.getByAddress(new byte[] {8, 8, 8, 8}), InetAddress.getByAddress(privateIp)});
            expectBlocked(resolvedPrivate);
            check(requests.get() == 0, "All DNS answers checked before any connect");
        }
        AtomicInteger dnsCalls = new AtomicInteger();
        opened.clear();
        var redirectPrivateDns = new PackPrivacyConnection(new URL("https://public.example.com/start"), Proxy.NO_PROXY, refused::incrementAndGet,
                (url, proxy) -> { opened.add(url.toString()); return new Stub(url, 302, "https://another.example.com/pack"); },
                host -> { dnsCalls.incrementAndGet(); return new InetAddress[] {InetAddress.getByAddress(dnsCalls.get() == 1
                        ? new byte[] {8, 8, 8, 8} : new byte[] {127, 0, 0, 1})}; });
        expectBlocked(redirectPrivateDns);
        check(opened.size() == 1 && dnsCalls.get() == 2, "Redirect DNS rechecked before second destination opens");
        for (String phase : List.of("missing-location", "response-code", "input-stream")) {
            Stub broken = new Stub(new URL("https://cdn.example.com/start"), phase.equals("missing-location") ? 302 : 200, null) {
                @Override public int getResponseCode() throws IOException { if (phase.equals("response-code")) throw new IOException("Injected response failure"); return super.getResponseCode(); }
                @Override public InputStream getInputStream() throws IOException { if (phase.equals("input-stream")) throw new IOException("Injected body failure"); return super.getInputStream(); }
            };
            var request = new PackPrivacyConnection(broken.getURL(), Proxy.NO_PROXY, refused::incrementAndGet, (url, proxy) -> broken, PUBLIC);
            expectBlocked(request);
            check(broken.closed, "Delegate closes after failure in " + phase);
        }
        check(!PackPrivacy.scoped(), "No request scope before test");
        try (var outer = PackPrivacy.enter(true, true)) {
            check(PackPrivacy.guardingUrls(), "Owning request scope guards URL");
            try (var inner = PackPrivacy.enter(false, false)) { check(!PackPrivacy.guardingUrls(), "Nested disabled scope bypasses protection"); }
            check(PackPrivacy.guardingUrls(), "Parent scope restored after nesting");
        }
        check(!PackPrivacy.scoped(), "Request scope removed after success");
        try (var scope = PackPrivacy.enter(true, true)) { throw new IOException("Injected request failure"); }
        catch (IOException expected) { check(!PackPrivacy.scoped(), "Request scope removed after exception"); }
        System.out.println("Pack privacy policy passed " + assertions + " assertions.");
    }

    private static void expectBlocked(PackPrivacyConnection connection) throws Exception {
        try { connection.getInputStream(); throw new AssertionError("Expected rejected request"); }
        catch (IOException expected) { assertions++; }
    }
    private static void check(boolean pass, String label) { if (!pass) throw new AssertionError(label); assertions++; }
    private static class Stub extends HttpURLConnection {
        private final int code;
        private final String location;
        private boolean closed;
        Stub(URL url, int code, String location) { super(url); this.code = code; this.location = location; }
        @Override public int getResponseCode() throws IOException { return code; }
        @Override public String getHeaderField(String key) { return "Location".equals(key) ? location : null; }
        @Override public InputStream getInputStream() throws IOException { return new ByteArrayInputStream(new byte[] {42}); }
        @Override public long getContentLengthLong() { return 1; }
        @Override public void connect() { }
        @Override public void disconnect() { closed = true; }
        @Override public boolean usingProxy() { return false; }
    }
}
