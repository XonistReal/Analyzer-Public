package dev.analyzer.automation;

import java.util.Arrays;

/** Reusable nearest-first bounded observations; nearby blocks do not wait for distant corner scans. */
public final class MiningSurveyPolicy {
    public record Offset(int x, int y, int z, int distanceSquared) { }
    private final int radius, vertical, width, layers;
    private final long[] order;
    public MiningSurveyPolicy(int radius, int vertical) {
        if (radius < 1 || radius > 48 || vertical < 1 || vertical > 48) throw new IllegalArgumentException("Bounded survey dimensions required");
        this.radius = radius; this.vertical = vertical; width = radius * 2 + 1; layers = vertical * 2 + 1;
        order = new long[width * width * layers]; int index = 0;
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) for (int y = -vertical; y <= vertical; y++) {
            int encoded = ((x + radius) * width + z + radius) * layers + y + vertical;
            order[index++] = (long)(x * x + y * y + z * z) << 32 | Integer.toUnsignedLong(encoded);
        }
        Arrays.sort(order);
    }
    public int size() {return order.length;}
    public Offset offset(int index) {
        long entry = order[index]; int encoded = (int)entry;
        int y = encoded % layers - vertical; encoded /= layers;
        int z = encoded % width - radius, x = encoded / width - radius;
        return new Offset(x, y, z, (int)(entry >>> 32));
    }
}
