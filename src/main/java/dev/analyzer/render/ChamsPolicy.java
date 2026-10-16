package dev.analyzer.render;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.EspSettings;

import java.util.List;

/** Frame-immutable selection and material policy. No game objects or OpenGL calls enter these rules. */
public final class ChamsPolicy {
    private ChamsPolicy() { }

    public enum Style {
        FLAT("Flat", 0), METALLIC("Metallic", 1), GLASS("Glass", 2), GLOW("Glow", 3), WIREFRAME("Wireframe", 4);

        private final String label;
        private final int shaderCode;
        Style(String label, int shaderCode) { this.label = label; this.shaderCode = shaderCode; }
        public String label() { return label; }
        public int shaderCode() { return shaderCode; }

        /** Unknown historical or damaged input uses a plain material; live choices stay strictly validated. */
        public static Style from(String value) {
            for (Style style : values()) if (style.label.equals(value)) return style;
            return FLAT;
        }
    }

    public enum Group {
        PLAYER("players"), HOSTILE("hostiles"), PASSIVE("passives");
        private final String settingId;
        Group(String settingId) { this.settingId = settingId; }
        public String settingId() { return settingId; }
    }

    /** The hidden pass uses strict greater-than depth; visible geometry uses less-than-or-equal depth. */
    public enum Depth {
        HIDDEN_GREATER, VISIBLE_LEQUAL;
        public boolean accepts(double fragmentDepth, double worldDepth) {
            if (!Double.isFinite(fragmentDepth) || !Double.isFinite(worldDepth)
                    || fragmentDepth < 0 || fragmentDepth > 1 || worldDepth < 0 || worldDepth > 1) return false;
            return this == HIDDEN_GREATER ? fragmentDepth > worldDepth : fragmentDepth <= worldDepth;
        }
    }

    public record Colors(int players, int hostiles, int passives) {
        public int color(Group group) {
            return switch (group) {
                case PLAYER -> players;
                case HOSTILE -> hostiles;
                case PASSIVE -> passives;
            };
        }
    }

    public record Snapshot(boolean enabled, Style style,
                           boolean players, boolean hostiles, boolean passives, boolean self, boolean invisibles,
                           double range, boolean throughWalls, double visibleOpacity, double hiddenOpacity,
                           float effectStrength, float animationSpeed, float wireWidth,
                           Colors visibleColors, Colors hiddenColors) {
        public Snapshot {
            style = style == null ? Style.FLAT : style;
            // Invalid distances never turn into an unlimited selection. Percentages are normalized once per frame.
            range = bounded(range, 0, 256, 0);
            visibleOpacity = bounded(visibleOpacity, 0, 100, 0);
            hiddenOpacity = bounded(hiddenOpacity, 0, 100, 0);
            effectStrength = (float) bounded(effectStrength, 0, 2, 1);
            animationSpeed = (float) bounded(animationSpeed, 0, 3, 1);
            wireWidth = (float) bounded(wireWidth, .5, 3, 1);
            if (visibleColors == null || hiddenColors == null) throw new IllegalArgumentException("Chams palettes are required");
        }

        public boolean accepts(Group group, double distanceSquared, boolean removed, boolean alive,
                               boolean selfEntity, boolean firstPerson, boolean invisible, boolean spectator) {
            if (!enabled || group == null || removed || !alive || spectator || range <= 0
                    || !Double.isFinite(distanceSquared) || distanceSquared < 0 || distanceSquared > range * range
                    || selfEntity && (!self || firstPerson) || invisible && !invisibles
                    || !hasVisiblePass() && !hasHiddenPass()) return false;
            return switch (group) {
                case PLAYER -> players;
                case HOSTILE -> hostiles;
                case PASSIVE -> passives;
            };
        }

        /** Alpha carries the independent pass opacity to the shader; the retained RGB is never modified. */
        public int color(Group group, boolean hidden) {
            int rgb = (hidden ? hiddenColors : visibleColors).color(group);
            int alpha = (int) Math.round((hidden ? hiddenOpacity : visibleOpacity) * 255 / 100);
            return alpha << 24 | rgb & 0xFFFFFF;
        }

        public boolean hasVisiblePass() { return enabled && visibleOpacity > 0; }
        public boolean hasHiddenPass() { return enabled && throughWalls && hiddenOpacity > 0; }

        public List<Depth> passes() {
            if (hasHiddenPass() && hasVisiblePass()) return List.of(Depth.HIDDEN_GREATER, Depth.VISIBLE_LEQUAL);
            if (hasHiddenPass()) return List.of(Depth.HIDDEN_GREATER);
            if (hasVisiblePass()) return List.of(Depth.VISIBLE_LEQUAL);
            return List.of();
        }
    }

    /** Capture all settings under the core's lock so one frame cannot mix values from two profile loads. */
    public static Snapshot read(AnalyzerCore core) {
        synchronized (core) {
            String visibleModule = core.getBoolean("chams", "match-esp-colors") ? "entity-esp" : "chams";
            return new Snapshot(core.isEnabled("chams"), Style.from(core.getChoice("chams", "style")),
                    core.getBoolean("chams", "players"), core.getBoolean("chams", "hostiles"),
                    core.getBoolean("chams", "passives"), core.getBoolean("chams", "self"),
                    core.getBoolean("chams", "invisibles"), core.getNumber("chams", "range"),
                    core.getBoolean("chams", "through-walls"), core.getNumber("chams", "visible-opacity"),
                    core.getNumber("chams", "hidden-opacity"), (float) core.getNumber("chams", "effect-strength"),
                    (float) core.getNumber("chams", "animation-speed"), (float) core.getNumber("chams", "wire-width"),
                    colors(core, visibleModule, ""), colors(core, "chams", "-hidden"));
        }
    }

    private static Colors colors(AnalyzerCore core, String module, String suffix) {
        return new Colors(color(core, module, "players" + suffix), color(core, module, "hostiles" + suffix),
                color(core, module, "passives" + suffix));
    }

    private static int color(AnalyzerCore core, String module, String group) {
        return EspSettings.color(core.getChoice(module, group + "-color"), core.getNumber(module, group + "-red"),
                core.getNumber(module, group + "-green"), core.getNumber(module, group + "-blue"));
    }

    private static double bounded(double value, double minimum, double maximum, double fallback) {
        return Math.clamp(Double.isFinite(value) ? value : fallback, minimum, maximum);
    }
}
