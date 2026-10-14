package dev.analyzer.automation;

/** Checks physical bounds, short-path wrapping, braking, repeatable previews and target changes. */
public final class MotionPolicySelfTest {
    private static int assertions;
    public static void main(String[] args) {
        check(MotionPolicy.angle(359) == -1 && MotionPolicy.angle(-359) == 1, "short-path yaw wrap");
        var limits = new MotionPolicy.Limits(150, 600);
        var start = MotionPolicy.Rotation.at(0, 0);
        var first = MotionPolicy.next(start, 90, 35, limits, .05);
        check(close(first.yaw(), 1.5) && close(first.pitch(), 1.5), "first tick accelerates from rest");
        check(first.equals(MotionPolicy.next(start, 90, 35, limits, .05)), "a preview is pure and repeatable");
        var rotation = start;
        for (int i = 0; i < 160; i++) {
            var next = MotionPolicy.next(rotation, 90, 35, limits, .05);
            check(Math.abs(next.yaw() - rotation.yaw()) <= 7.500001, "yaw speed bounded");
            check(Math.abs(next.pitch() - rotation.pitch()) <= 7.500001, "pitch speed bounded");
            check(next.yaw() <= 90.000001 && next.pitch() <= 35.000001, "stable target cannot overshoot");
            check(next.pitch() >= -90 && next.pitch() <= 90, "pitch is legal");
            if (next.yawVelocity() != 0 && rotation.yawVelocity() != 0)
                check(Math.abs(next.yawVelocity() - rotation.yawVelocity()) <= 30.000001, "angular acceleration bounded while moving");
            rotation = next;
        }
        check(MotionPolicy.aligned(rotation, 90, 35, .001), "turn converges and stops");
        check(rotation.yawVelocity() == 0 && rotation.pitchVelocity() == 0, "arrival releases angular momentum");
        var wrap = MotionPolicy.Rotation.at(179, 0);
        for (int i = 0; i < 30; i++) {
            var next = MotionPolicy.next(wrap, -179, 0, limits, .05);
            check(Math.abs(next.yaw() - wrap.yaw()) < 3, "crossing 180 does not spin the camera");
            wrap = next;
        }
        check(close(wrap.yaw(), 181), "camera yaw stays continuous across wrap");
        var reverse = new MotionPolicy.Rotation(30, 0, 150, 0);
        var reversed = MotionPolicy.next(reverse, -90, 0, limits, .05);
        check(close(reversed.yawVelocity(), 120), "target reversal brakes instead of snapping velocity");
        var unsafe = MotionPolicy.next(MotionPolicy.Rotation.at(0, 89), 1000, 200, new MotionPolicy.Limits(Double.NaN, Double.POSITIVE_INFINITY), Double.NaN);
        check(Double.isFinite(unsafe.yaw()) && unsafe.pitch() <= 90, "invalid settings fail to finite bounded defaults");
        check(new MotionPolicy.Limits(0, -1).speed() == 30 && new MotionPolicy.Limits(0, -1).acceleration() == 60, "settings clamp to supported limits");
        check(!MotionPolicy.aligned(first, 90, 35, 3), "actions wait until aim reaches tolerance");
        System.out.println("Automation motion policy passed " + assertions + " assertions");
    }
    private static boolean close(double left, double right) { return Math.abs(left - right) < .00001; }
    private static void check(boolean condition, String label) { assertions++; if (!condition) throw new AssertionError(label); }
}
