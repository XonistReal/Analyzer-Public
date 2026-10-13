package dev.analyzer.gameplay;

public final class HvhPolicySelfTest {
    private static int checks;
    public static void main(String[] args) {
        check(HvhPolicy.rawExplosionDamage(13, 6, 1) == 0, "Outside blast radius");
        check(HvhPolicy.rawExplosionDamage(3, 6, 0) == 1, "Vanilla retains one raw HP at zero exposure");
        check(HvhPolicy.rawExplosionDamage(0, 6, 1) == 85, "Crystal center damage");
        check(HvhPolicy.rawExplosionDamage(0, 5, 1) == 71, "Anchor center damage");
        check(HvhPolicy.rawExplosionDamage(4, 6, .5) < HvhPolicy.rawExplosionDamage(4, 6, 1), "Exposure changes damage");
        check(Double.isNaN(HvhPolicy.rawExplosionDamage(1, 6, Double.NaN)), "Unknown exposure fails closed");
        check(!HvhPolicy.damageAllowed(10, Double.NaN, 6, 8, 20, true, false), "Unknown self damage fails closed");
        check(!HvhPolicy.damageAllowed(10, 9, 6, 8, 20, true, false), "Self cap applies");
        check(!HvhPolicy.damageAllowed(10, 8, 6, 8, 9, true, false), "Anti-suicide leaves health margin");
        check(HvhPolicy.damageAllowed(1, 2, 6, 8, 20, true, true), "Face place only relaxes enemy minimum");
        check(!HvhPolicy.damageAllowed(0, 2, 6, 8, 20, true, true), "Face place still needs damage");
        check(!HvhPolicy.damageAllowed(1, 9, 6, 8, 20, true, true), "Face place retains self cap");
        check(HvhPolicy.placementBudget(8, 3, false) == 3, "Shared cap controls aggregate placement");
        check(HvhPolicy.placementBudget(8, 8, true) == 1, "Legit mode permits one placement");
        check(HvhPolicy.placementBudget(8, 0, false) == 0, "Exhausted aggregate cap");
        check(HvhPolicy.protectedHole(1, 5, true, true), "Keep nearby self hole");
        check(HvhPolicy.protectedHole(5, 1, true, true), "Keep nearby friend hole");
        check(!HvhPolicy.protectedHole(5, 5, true, true), "Distant hole available");
        System.out.println("Analyzer HVH policy checks passed: " + checks + " assertions");
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
