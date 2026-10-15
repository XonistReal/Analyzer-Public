package dev.analyzer.automation;

/** Deterministic camera traces: braking, arrival, target changes and the shared tick budget. */
public final class MotionPolicySelfTest {
    private static int assertions;
    public static void main(String[] args) {
        check(MotionPolicy.angle(359) == -1 && MotionPolicy.angle(-359) == 1, "short-path yaw wrap");
        var limits = new MotionPolicy.Limits(150, 600);
        var start = MotionPolicy.Rotation.at(0, 0);
        var first = MotionPolicy.next(start, 90, 35, limits, .05);
        check(close(first.yaw(), .75) && close(first.pitch(), .75), "first tick integrates acceleration from rest");
        check(close(first.yawVelocity(), 30) && close(first.pitchVelocity(), 30), "first tick respects acceleration");
        check(first.equals(MotionPolicy.next(start, 90, 35, limits, .05)), "a preview is pure and repeatable");
        check(!MotionPolicy.aligned(first, 90, 35, 3), "actions wait until aim reaches tolerance");

        for (var profile : new MotionPolicy.Limits[] {limits, new MotionPolicy.Limits(30, 60),
                new MotionPolicy.Limits(720, 3600), new MotionPolicy.Limits(720, 60)}) {
            for (double dt : new double[] {.02, .05, .1}) {
                for (double target : new double[] {.000001, .01, .3, 1.5, 12, 90, 179.8, -179.8})
                    stableTrace(MotionPolicy.Rotation.at(0, 0), target, Math.copySign(Math.min(89.8, Math.abs(target) * .6), target), profile, dt);
                stableTrace(MotionPolicy.Rotation.at(179, -89), -179, 89.9, profile, dt);
                stableTrace(MotionPolicy.Rotation.at(-179, 89), 179, -89.9, profile, dt);
            }
        }
        brakingTrace(limits);
        reversalTrace(limits);
        nearTargetRetargetTrace(limits);
        onTargetMomentumTrace(limits);
        disturbedTargetTrace(limits);
        framePreviewTrace(limits);

        var unsafe = MotionPolicy.next(new MotionPolicy.Rotation(Double.NaN, Double.NaN, Double.NaN, Double.NaN),
                Double.NaN, 200, new MotionPolicy.Limits(Double.NaN, Double.POSITIVE_INFINITY), Double.NaN);
        check(Double.isFinite(unsafe.yaw()) && Double.isFinite(unsafe.pitch()) && unsafe.pitch() <= 90,
                "invalid snapshots and settings use finite bounded defaults");
        check(new MotionPolicy.Limits(0, -1).speed() == 30 && new MotionPolicy.Limits(0, -1).acceleration() == 60,
                "settings clamp to supported limits");
        System.out.println("Automation motion policy passed " + assertions + " assertions");
    }

    private static void stableTrace(MotionPolicy.Rotation rotation, double yaw, double pitch, MotionPolicy.Limits limits, double dt) {
        double destination = rotation.yaw() + MotionPolicy.angle(yaw - rotation.yaw());
        double yawDirection = Math.signum(destination - rotation.yaw()), pitchDirection = Math.signum(pitch - rotation.pitch());
        int settled = 0;
        for (int tick = 0; tick < 1600; tick++) {
            var next = MotionPolicy.next(rotation, yaw, pitch, limits, dt);
            bounds(rotation, next, limits, dt);
            check((next.yaw() - rotation.yaw()) * yawDirection >= -1e-8, "held yaw target never hunts left and right");
            check((next.pitch() - rotation.pitch()) * pitchDirection >= -1e-8, "held pitch target never hunts up and down");
            check((destination - next.yaw()) * yawDirection >= -1e-8, "held yaw target does not overshoot");
            check((pitch - next.pitch()) * pitchDirection >= -1e-8, "held pitch target does not overshoot");
            rotation = next;
            if (close(rotation.yaw(), destination) && close(rotation.pitch(), pitch) && stopped(rotation)) {
                if (++settled == 8) return;
            } else settled = 0;
        }
        throw new AssertionError("Held target failed to settle: " + rotation + " -> " + yaw + ", " + pitch + " at " + limits);
    }

    private static void brakingTrace(MotionPolicy.Limits limits) {
        var rotation = MotionPolicy.Rotation.at(0, 0);
        boolean brakingBeforeArrival = false;
        for (int tick = 0; tick < 100; tick++) {
            var next = MotionPolicy.next(rotation, 90, -35, limits, .05);
            bounds(rotation, next, limits, .05);
            if (next.yawVelocity() < rotation.yawVelocity() - 1e-8 && Math.abs(90 - next.yaw()) > .001)
                brakingBeforeArrival = true;
            rotation = next;
        }
        check(brakingBeforeArrival, "braking starts while a visible angle remains before the target");
        check(close(rotation.yaw(), 90) && close(rotation.pitch(), -35) && stopped(rotation), "braking arrives at rest");
    }

    private static void reversalTrace(MotionPolicy.Limits limits) {
        var rotation = new MotionPolicy.Rotation(30, 10, 150, 80);
        var first = MotionPolicy.next(rotation, -90, -35, limits, .05);
        bounds(rotation, first, limits, .05);
        check(close(first.yawVelocity(), 120) && close(first.yaw(), 36.75), "reversal brakes existing yaw momentum before turning back");
        check(close(first.pitchVelocity(), 50), "reversal brakes existing pitch momentum before turning back");
        int yawReversals = 0, pitchReversals = 0, lastYawSign = 1, lastPitchSign = 1;
        for (int tick = 0; tick < 160; tick++) {
            var next = MotionPolicy.next(rotation, -90, -35, limits, .05);
            bounds(rotation, next, limits, .05);
            int yawSign = sign(next.yawVelocity()), pitchSign = sign(next.pitchVelocity());
            if (yawSign != 0 && yawSign != lastYawSign) { yawReversals++; lastYawSign = yawSign; }
            if (pitchSign != 0 && pitchSign != lastPitchSign) { pitchReversals++; lastPitchSign = pitchSign; }
            rotation = next;
        }
        check(yawReversals == 1 && pitchReversals == 1, "one target reversal causes one direction change per axis");
        check(close(rotation.yaw(), -90) && close(rotation.pitch(), -35) && stopped(rotation), "reversed target settles without hunting");
    }

    private static void nearTargetRetargetTrace(MotionPolicy.Limits limits) {
        // This target is inside the current stopping distance. Crossing it once is unavoidable
        // under the configured acceleration limit; repeatedly crossing it is not.
        var rotation = new MotionPolicy.Rotation(0, 0, 120, -80);
        int yawCrossings = 0, pitchCrossings = 0;
        int yawSide = sign(rotation.yaw() - 1), pitchSide = sign(rotation.pitch() + .5);
        for (int tick = 0; tick < 120; tick++) {
            var next = MotionPolicy.next(rotation, 1, -.5, limits, .05);
            bounds(rotation, next, limits, .05);
            int nextYawSide = sign(next.yaw() - 1), nextPitchSide = sign(next.pitch() + .5);
            if (nextYawSide != 0 && nextYawSide != yawSide) { yawCrossings++; yawSide = nextYawSide; }
            if (nextPitchSide != 0 && nextPitchSide != pitchSide) { pitchCrossings++; pitchSide = nextPitchSide; }
            rotation = next;
        }
        check(yawCrossings == 1 && pitchCrossings == 1, "a close retarget does not cause repeated left-right hunting");
        check(close(rotation.yaw(), 1) && close(rotation.pitch(), -.5) && stopped(rotation), "close retarget converges at rest");
    }

    private static void disturbedTargetTrace(MotionPolicy.Limits limits) {
        var rotation = MotionPolicy.Rotation.at(0, 0);
        for (int tick = 0; tick < 32; tick++) {
            double target = tick < 8 ? 80 : 80 + (tick % 2 == 0 ? .35 : -.35);
            var next = MotionPolicy.next(rotation, target, -target * .5, limits, .05);
            bounds(rotation, next, limits, .05);
            rotation = next;
        }
        int directionChanges = 0, lastSign = 0;
        for (int tick = 0; tick < 80; tick++) {
            var next = MotionPolicy.next(rotation, 80, -40, limits, .05);
            bounds(rotation, next, limits, .05);
            int nextSign = sign(next.yawVelocity());
            if (nextSign != 0) {
                if (lastSign != 0 && nextSign != lastSign) directionChanges++;
                lastSign = nextSign;
            }
            rotation = next;
        }
        check(directionChanges <= 1, "small changing aims stop hunting once the aim holds still");
        check(close(rotation.yaw(), 80) && close(rotation.pitch(), -40) && stopped(rotation), "small aim disturbances settle promptly");
    }

    private static void onTargetMomentumTrace(MotionPolicy.Limits limits) {
        var rotation = new MotionPolicy.Rotation(0, 0, 120, -80);
        var first = MotionPolicy.next(rotation, 0, 0, limits, .05);
        check(close(first.yaw(), 5.25) && close(first.yawVelocity(), 90),
                "being exactly on a changed target does not discard existing momentum");
        for (int tick = 0; tick < 120; tick++) {
            var next = MotionPolicy.next(rotation, 0, 0, limits, .05);
            bounds(rotation, next, limits, .05);
            rotation = next;
        }
        check(close(rotation.yaw(), 0) && close(rotation.pitch(), 0) && stopped(rotation),
                "an on-target retarget brakes and returns to a stable rest");
    }

    private static void framePreviewTrace(MotionPolicy.Limits limits) {
        var base = new MotionPolicy.Rotation(25, -15, 75, -45);
        var initial = MotionPolicy.next(base, 90, 35, limits, .05);
        for (double target : new double[] {170, -170, 25.1, -30, 90}) {
            var preview = MotionPolicy.next(base, target, Math.max(-89, Math.min(89, -target)), limits, .05);
            bounds(base, preview, limits, .05);
        }
        check(initial.equals(MotionPolicy.next(base, 90, 35, limits, .05)), "same-frame previews do not consume more angular time");
    }

    private static void bounds(MotionPolicy.Rotation from, MotionPolicy.Rotation to, MotionPolicy.Limits limits, double dt) {
        check(Double.isFinite(to.yaw()) && Double.isFinite(to.pitch()), "camera angles stay finite");
        check(to.pitch() >= -90 && to.pitch() <= 90, "pitch remains legal");
        check(Math.abs(to.yaw() - from.yaw()) <= limits.speed() * dt + 1e-8, "yaw displacement respects speed");
        check(Math.abs(to.pitch() - from.pitch()) <= limits.speed() * dt + 1e-8, "pitch displacement respects speed");
        check(Math.abs(to.yawVelocity()) <= limits.speed() + 1e-8 && Math.abs(to.pitchVelocity()) <= limits.speed() + 1e-8,
                "angular velocity stays within the profile");
        check(Math.abs(to.yawVelocity() - from.yawVelocity()) <= limits.acceleration() * dt + 1e-7,
                "yaw acceleration stays bounded, including the arrival tick");
        check(Math.abs(to.pitchVelocity() - from.pitchVelocity()) <= limits.acceleration() * dt + 1e-7,
                "pitch acceleration stays bounded, including the arrival tick");
    }
    private static boolean stopped(MotionPolicy.Rotation rotation) { return rotation.yawVelocity() == 0 && rotation.pitchVelocity() == 0; }
    private static int sign(double value) { return Math.abs(value) < 1e-8 ? 0 : value > 0 ? 1 : -1; }
    private static boolean close(double left, double right) { return Math.abs(left - right) < 1e-8; }
    private static void check(boolean condition, String label) { assertions++; if (!condition) throw new AssertionError(label); }
}
