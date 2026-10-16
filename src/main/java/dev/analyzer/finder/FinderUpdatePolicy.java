package dev.analyzer.finder;

/** Which immutable aggregate observations a received block-state change can actually alter. */
public final class FinderUpdatePolicy {
    private FinderUpdatePolicy() { }

    public record Change(boolean chunkEvidence, boolean kelpColumns, boolean fastSurvey) { }

    public static Change classify(String beforeId, String afterId, String beforeAxis, String afterAxis) {
        String before = beforeId == null ? "" : beforeId, after = afterId == null ? "" : afterId;
        boolean typeChanged = !before.equals(after);
        boolean axisEvidence = before.equals("minecraft:deepslate") && after.equals(before)
                && horizontal(beforeAxis) != horizontal(afterAxis);
        return new Change(typeChanged && (ChunkFinderRules.relevantEvidence(before) || ChunkFinderRules.relevantEvidence(after)) || axisEvidence,
                typeChanged && (kelp(before) || kelp(after)),
                typeChanged && (fast(before) || fast(after)));
    }

    /** Ignore animation properties that none of the loaded-block predicates inspect. */
    public static boolean blockMarkersChanged(FinderData.Cell before, FinderData.Cell after) {
        if (before == null || after == null || before.loaded() != after.loaded() || !before.id().equals(after.id())
                || before.air() != after.air() || before.solid() != after.solid() || before.fluid() != after.fluid()
                || before.luminance() != after.luminance()) return true;
        return switch (before.id()) {
            case "minecraft:deepslate" -> changed(before, after, "axis");
            case "minecraft:beehive", "minecraft:bee_nest" -> changed(before, after, "honey_level");
            case "minecraft:sweet_berry_bush" -> changed(before, after, "age");
            case "minecraft:pointed_dripstone" -> changed(before, after, "vertical_direction");
            case "minecraft:piston", "minecraft:sticky_piston", "minecraft:piston_head" -> changed(before, after, "extended") || changed(before, after, "facing");
            default -> false;
        };
    }

    private static boolean changed(FinderData.Cell before, FinderData.Cell after, String key) {
        return !before.property(key).equals(after.property(key));
    }

    private static boolean horizontal(String axis) { return "x".equals(axis) || "z".equals(axis); }
    private static boolean kelp(String id) { return id.equals("minecraft:kelp") || id.equals("minecraft:kelp_plant"); }
    private static boolean fast(String id) {
        return ChunkFinderRules.storageGroup(id) != null || id.equals("minecraft:spawner") || id.equals("minecraft:trial_spawner")
                || id.startsWith("minecraft:") && (id.endsWith("_banner") || id.endsWith("_wall_banner"));
    }
}
