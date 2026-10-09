package dev.analyzer.render;

import java.util.ArrayList;
import java.util.List;

/** Behaviour checks for dense ESP scenes, distance cutoffs and projected subpixel boxes. */
public final class EspLayoutSelfTest {
    private static int assertions;

    public static void main(String[] args) {
        var near = appearance(8);
        equal(1, near.size(), "Near labels retain selected scale");
        equal(1, near.opacity(), "Near labels remain opaque");
        var middle = appearance(28);
        equal(0.725, middle.size(), "Middle distance shrinks smoothly");
        equal(0.675, middle.opacity(), "Middle distance fades smoothly");
        var far = appearance(48);
        equal(0.45, far.size(), "Far size uses configured endpoint");
        equal(0.35, far.opacity(), "Far labels fade without disappearing prematurely");
        check(!appearance(48.01).visible(), "Label range hides tags without changing geometry range");
        check(!appearance(-1).visible(), "Negative distances rejected");
        check(!appearance(Double.NaN).visible(), "Nonfinite distance rejected");
        check(!EspLayout.appearance(1, 8, 0, .45, true, true).visible(), "Invalid range rejected");
        equal(1, EspLayout.appearance(48, 8, 48, .45, false, false).size(), "Fixed size opt-out");
        equal(1, EspLayout.appearance(48, 8, 48, .45, false, false).opacity(), "Fade opt-out");
        equal(1, EspLayout.appearance(40, 80, 48, .45, true, true).size(), "Full-size threshold beyond range is well-defined");
        double priorSize = 2, priorAlpha = 2;
        for (int distance = 0; distance <= 48; distance += 4) {
            var value = appearance(distance);
            check(value.size() <= priorSize && value.opacity() <= priorAlpha, "Monotonic distance attenuation at " + distance);
            priorSize = value.size(); priorAlpha = value.opacity();
        }
        check(!EspLayout.markerVisible(1, 2, 3), "Tiny distant marker omitted");
        check(EspLayout.markerVisible(1, 3, 3), "Tall narrow target retained");
        check(EspLayout.markerVisible(3, 1, 3), "Wide short target retained");
        check(!EspLayout.markerVisible(0, 8, 0), "Degenerate projected box omitted");
        check(!EspLayout.markerVisible(Double.NaN, 8, 0), "Invalid projected box omitted");
        check(EspLayout.markerVisible(.5, .5, 0), "Tiny marker culling can be disabled");
        // Simulate nearest-first placement: a crowd of 71 tags over the same box cannot produce 71 overlapping tags.
        List<EspLayout.Rect> occupied = new ArrayList<>();
        int accepted = 0;
        for (int i = 0; i < 71; i++) {
            var candidate = new EspLayout.Rect(100 + i % 2, 100 + i % 3, 90, 20);
            if (EspLayout.free(candidate, occupied, 2)) { occupied.add(candidate); accepted++; }
        }
        check(accepted == 1, "Dense stack keeps nearest tag");
        check(EspLayout.free(new EspLayout.Rect(200, 100, 90, 20), occupied, 2), "Separate target can show its tag");
        check(!EspLayout.free(new EspLayout.Rect(191, 100, 90, 20), occupied, 2), "Collision gap enforced");
        check(new EspLayout.Rect(0, 0, 1920, 1080).inside(1920, 1080), "Viewport edges are valid");
        check(!new EspLayout.Rect(-1, 0, 20, 20).inside(1920, 1080), "Offscreen label omitted");
        check(!new EspLayout.Rect(1910, 1070, 20, 20).inside(1920, 1080), "Overflow label omitted");
        System.out.println("Analyzer ESP layout checks passed: " + assertions + " assertions");
    }
    private static EspLayout.Appearance appearance(double distance) {
        return EspLayout.appearance(distance, 8, 48, .45, true, true);
    }
    private static void equal(double expected, double actual, String message) {
        check(Math.abs(expected - actual) < .000001, message);
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
