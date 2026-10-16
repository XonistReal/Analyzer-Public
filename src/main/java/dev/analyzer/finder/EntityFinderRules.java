package dev.analyzer.finder;

/** Dependency-free predicates. All facts must come from actual client entity state. */
public final class EntityFinderRules {
    private EntityFinderRules() { }

    /** Each independent cursor makes progress while all entity surveys share a finite tick cap. */
    public static int scanAllowance(int activeModules, int requestedBudget) {
        if (activeModules <= 0 || requestedBudget <= 0) return 0;
        return Math.min(requestedBudget, 16_384 / activeModules);
    }
    public enum Group { PLAYER, HOSTILE, PASSIVE, OTHER }
    public enum Kind { ITEM_FRAME, GLOW_ITEM_FRAME, ARMOR_STAND, DROWNED, LLAMA, TRADER_LLAMA,
        PILLAGER, VILLAGER, ZOMBIE_VILLAGER, WANDERING_TRADER, OTHER }
    public record Facts(Kind kind, Group group, double distanceSquared, boolean removed, boolean alive,
                        boolean spectator, boolean self, boolean firstPerson, boolean invisible,
                        boolean heldTrident, boolean emptyFrame, boolean baby) { }
    public record Options(boolean enabled, double range, boolean players, boolean hostiles, boolean passives,
                          boolean invisibles, boolean self, boolean itemFrames, boolean glowFrames,
                          boolean armorStands, boolean filledFramesOnly, boolean traderLlamas,
                          boolean zombieVillagers, boolean adultsOnly) { }

    public static boolean accepts(String module, Facts entity, Options options) {
        if (entity == null || options == null || !options.enabled || entity.kind == null || entity.group == null
                || !Double.isFinite(options.range) || options.range < 0
                || !Double.isFinite(entity.distanceSquared) || entity.distanceSquared < 0
                || entity.distanceSquared > options.range * options.range || entity.removed || !entity.alive
                || entity.spectator || entity.self && (entity.firstPerson || !options.self)) return false;
        return switch (module == null ? "" : module) {
            case "collectible-esp" -> switch (entity.kind) {
                case ITEM_FRAME -> options.itemFrames && (!options.filledFramesOnly || !entity.emptyFrame);
                case GLOW_ITEM_FRAME -> options.glowFrames && (!options.filledFramesOnly || !entity.emptyFrame);
                case ARMOR_STAND -> options.armorStands;
                default -> false;
            };
            case "drowned-trident-esp" -> entity.kind == Kind.DROWNED && entity.heldTrident;
            case "invisible-esp" -> entity.invisible && selected(entity.group, options);
            case "llama-esp" -> entity.kind == Kind.LLAMA || entity.kind == Kind.TRADER_LLAMA && options.traderLlamas;
            case "pillager-esp" -> entity.kind == Kind.PILLAGER;
            case "skeleton-esp" -> selected(entity.group, options) && (!entity.invisible || options.invisibles);
            case "villager-esp" -> (entity.kind == Kind.VILLAGER || entity.kind == Kind.ZOMBIE_VILLAGER && options.zombieVillagers)
                    && (!options.adultsOnly || !entity.baby);
            case "wandering-trader-esp" -> entity.kind == Kind.WANDERING_TRADER;
            default -> false;
        };
    }

    private static boolean selected(Group group, Options options) {
        return switch (group) {
            case PLAYER -> options.players;
            case HOSTILE -> options.hostiles;
            case PASSIVE -> options.passives;
            default -> false;
        };
    }
}
