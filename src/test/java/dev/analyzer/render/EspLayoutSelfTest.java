package dev.analyzer.render;

import dev.analyzer.core.EspSettings;

import java.util.ArrayList;
import java.util.HashSet;
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
        storageAndColors();
        System.out.println("Analyzer ESP layout checks passed: " + assertions + " assertions");
    }
    private static void storageAndColors() {
        // These were previously omitted, or inherited an incorrect parent-class color.
        check(EspSettings.storageGroup("minecraft:trapped_chest") == EspSettings.StorageGroup.TRAPPED_CHESTS,
                "Trapped chests have an independent group instead of normal chest inheritance");
        check(EspSettings.storageGroup("minecraft:dropper") == EspSettings.StorageGroup.DROPPERS,
                "Droppers are independent of dispensers");
        check(EspSettings.storageGroup("minecraft:crafter") == EspSettings.StorageGroup.CRAFTERS, "Crafters included");
        check(EspSettings.storageGroup("minecraft:decorated_pot") == EspSettings.StorageGroup.POTS, "Decorated pot storage included");
        check(EspSettings.storageGroup("minecraft:chiseled_bookshelf") == EspSettings.StorageGroup.BOOKSHELVES, "Bookshelf storage included");
        check(EspSettings.storageGroup("minecraft:lectern") == EspSettings.StorageGroup.LECTERNS, "Lectern books included");
        check(EspSettings.storageGroup("minecraft:beehive") == EspSettings.StorageGroup.HIVES, "Beehives included");
        check(EspSettings.storageGroup("minecraft:bee_nest") == EspSettings.StorageGroup.HIVES, "Bee nests share their storage group");
        check(EspSettings.storageGroup("minecraft:campfire") == EspSettings.StorageGroup.CAMPFIRES, "Campfire item slots included");
        check(EspSettings.storageGroup("minecraft:soul_campfire") == EspSettings.StorageGroup.CAMPFIRES, "Soul campfire slots included");
        check(EspSettings.storageGroup("minecraft:jukebox") == EspSettings.StorageGroup.JUKEBOXES, "Jukebox record included");
        for (String name : List.of("furnace", "blast_furnace", "smoker"))
            check(EspSettings.storageGroup("minecraft:" + name) == EspSettings.StorageGroup.FURNACES, "Furnace inventory " + name);
        for (String color : List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"))
            check(EspSettings.storageGroup("minecraft:" + color + "_shulker_box") == EspSettings.StorageGroup.SHULKERS,
                    "Dyed special storage model " + color);
        check(EspSettings.storageGroup("minecraft:shulker_box") == EspSettings.StorageGroup.SHULKERS, "Undyed shulker included");
        for (String id : List.of("mod:chest", "minecraft:fake_shulker_box", "minecraft:stone", "minecraft:crafting_table", "chest"))
            check(EspSettings.storageGroup(id) == null, "No false storage match " + id);
        check(EspSettings.storageGroup(null) == null, "Missing block ID ignored");
        check(EspSettings.color("Custom", 17, 34, 51) == 0xFF112233, "Custom RGB channels retain their independent values");
        check(EspSettings.color("Custom", -20, 999, Double.NaN) == 0xFF00FF00, "Custom RGB rejects invalid/out-of-bounds channels");
        check(EspSettings.color("Pink", 0, 0, 0) == EspSettings.palette("Pink"), "Preset colors ignore retained custom channels");
        var colors = EspSettings.colors("players", "Players", "Cyan");
        check(colors.getFirst().choices().contains("Custom"), "Palette exposes custom RGB");
        HashSet<Integer> swatches = new HashSet<>();
        for (String name : colors.getFirst().choices()) if (!name.equals("Custom")) {
            int color = EspSettings.palette(name);
            check((color >>> 24) == 255, "Every preset has a fully opaque line base " + name);
            check(swatches.add(color), "Every named swatch is visually distinct " + name);
        }
        HashSet<String> groups = new HashSet<>();
        for (EspSettings.StorageGroup group : EspSettings.StorageGroup.values()) {
            check(groups.add(group.id()), "Unique saved storage filter " + group.id());
            check(colors.getFirst().choices().contains(group.defaultColor()), "Valid default storage palette " + group.id());
            check(EspSettings.colors(group.id(), group.label(), group.defaultColor()).size() == 4,
                    "Every storage group has a palette and retained custom RGB " + group.id());
        }
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
