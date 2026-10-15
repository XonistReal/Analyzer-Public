package dev.analyzer.automation;

/** Independent safety boundaries: missing vine segments, unknown landings, protected structures and long falls. */
public final class GroundRecoveryPolicySelfTest {
    private static int assertions;
    public static void main(String[] args) {
        for (int depth = 2; depth <= 24; depth++) check(GroundRecoveryPolicy.climbColumn(depth, true, true), "continuous climbable columns within the observation bound are usable");
        check(!GroundRecoveryPolicy.climbColumn(25, true, true), "a landing beyond the survey is not assumed safe");
        check(!GroundRecoveryPolicy.climbColumn(12, false, true), "one missing vine segment rejects a long descent");
        check(!GroundRecoveryPolicy.climbColumn(12, true, false), "a continuous vine over fluid or a void is rejected");
        check(GroundRecoveryPolicy.treeColumn(10, 9, 8, true, true), "a verified leafy rooted trunk can be dismantled in controlled steps");
        check(!GroundRecoveryPolicy.treeColumn(10, 9, 0, true, true), "a bare log structure is not evidence of a natural tree");
        check(!GroundRecoveryPolicy.treeColumn(10, 9, 8, false, true), "floating timber without a soil root is preserved");
        check(!GroundRecoveryPolicy.treeColumn(10, 9, 8, true, false), "a protected or unreadable column cannot authorize a cut");
        var wood = new GroundRecoveryPolicy.Support(true, true, false, false, false, true, true);
        var stone = new GroundRecoveryPolicy.Support(true, true, false, false, false, false, true);
        check(GroundRecoveryPolicy.underfootCut(1, true, wood, stone), "a centered one-block cut requires an actual solid landing");
        for (double drop : new double[]{0, -1, 1.02, 3, 10, Double.NaN, Double.POSITIVE_INFINITY}) check(!GroundRecoveryPolicy.underfootCut(drop, true, wood, stone), "invalid and damaging drops never become a recovery action");
        check(!GroundRecoveryPolicy.underfootCut(1, false, wood, stone), "the player must be centered over the recorded trunk");
        check(!GroundRecoveryPolicy.underfootCut(1, true, stone, stone), "ordinary player structures cannot be removed as a tree fallback");
        for (int risk = 0; risk < 6; risk++) {
            var unsafe = new GroundRecoveryPolicy.Support(risk != 0, risk != 1, risk == 2, risk == 3, risk == 4, true, risk != 5);
            check(!GroundRecoveryPolicy.underfootCut(1, true, unsafe, stone), "a hazardous floor cannot authorize recovery");
            check(!GroundRecoveryPolicy.underfootCut(1, true, wood, unsafe), "an unsafe landing cannot authorize recovery");
        }
        System.out.println("Ground recovery policy passed " + assertions + " assertions");
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
