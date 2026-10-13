package dev.analyzer.privacy;

import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Pure resource-pack URL/header policy. It deliberately performs no DNS lookup. */
public final class PackPrivacyPolicy {
    private PackPrivacyPolicy() { }

    public static boolean allowed(URL url) {
        if (url == null) return false;
        try { return allowed(url.toURI()); }
        catch (Exception malformed) { return false; }
    }

    public static boolean allowed(URI uri) {
        if (uri == null || uri.isOpaque()) return false;
        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) return false;
        if (uri.getRawUserInfo() != null) return false;
        String host = uri.getHost();
        if (host == null || host.isBlank() || uri.getPort() > 65535 || uri.getPort() == 0) return false;
        host = host.toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.isEmpty() || host.contains("%") || host.equals("localhost") || host.endsWith(".localhost")
                || host.equals("local") || host.endsWith(".local")) return false;
        if (host.contains(":")) {
            // Only a syntactically literal IPv6 address reaches this branch; no hostname resolution.
            if (!host.matches("[0-9a-f:.]+")) return false;
            try { return publicAddress(InetAddress.getByName(host).getAddress()); }
            catch (Exception malformed) { return false; }
        }
        if (host.matches("[0-9.]+")) {
            String[] octets = host.split("\\.", -1);
            if (octets.length != 4) return false; // Reject decimal, shortened and ambiguous IPv4 forms.
            byte[] address = new byte[4];
            for (int i = 0; i < 4; i++) {
                if (octets[i].isEmpty() || octets[i].length() > 3 || (octets[i].length() > 1 && octets[i].startsWith("0"))) return false;
                int value = Integer.parseInt(octets[i]);
                if (value > 255) return false;
                address[i] = (byte) value;
            }
            return publicAddress(address);
        }
        // A local single-label name or hex-shaped numeric host is never a public pack CDN name.
        if (!host.contains(".") || host.startsWith("0x") || host.contains(".0x")) return false;
        return host.matches("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?") && !host.contains("..");
    }

    static boolean publicAddress(byte[] bytes) {
        if (bytes.length == 4) {
            int a = bytes[0] & 255, b = bytes[1] & 255, c = bytes[2] & 255;
            return !(a == 0 || a == 10 || a == 127 || a >= 224
                    || (a == 100 && b >= 64 && b <= 127) || (a == 169 && b == 254)
                    || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168)
                    || (a == 192 && b == 0 && (c == 0 || c == 2)) || (a == 198 && (b == 18 || b == 19))
                    || (a == 198 && b == 51 && c == 100) || (a == 203 && b == 0 && c == 113));
        }
        if (bytes.length != 16) return false;
        boolean firstTwelveZero = true;
        for (int i = 0; i < 12; i++) firstTwelveZero &= bytes[i] == 0;
        if (firstTwelveZero) return false; // Unspecified, loopback and deprecated IPv4-compatible addresses.
        int a = bytes[0] & 255, b = bytes[1] & 255;
        if (a < 0x20 || a > 0x3f) return false; // Only global unicast; excludes ULA, local, multicast and reserved ranges.
        int c = bytes[2] & 255, d = bytes[3] & 255;
        return !(a == 0x20 && b == 0x01 && ((c == 0x0d && d == 0xb8) || (c == 0 && d == 2)))
                && !(a == 0x3f && b == 0xff && (c & 0xf0) == 0); // Documentation and benchmarking prefixes.
    }

    public static Map<String, String> scrubHeaders(Map<String, String> headers, String version) {
        var result = new LinkedHashMap<String, String>();
        for (var entry : headers.entrySet()) {
            String key = entry.getKey();
            if (key.equalsIgnoreCase("X-Minecraft-Username") || key.equalsIgnoreCase("X-Minecraft-UUID")
                    || key.equalsIgnoreCase("User-Agent")) continue;
            result.put(key, entry.getValue());
        }
        result.put("User-Agent", "Minecraft Java/" + version);
        return Map.copyOf(result);
    }
}
