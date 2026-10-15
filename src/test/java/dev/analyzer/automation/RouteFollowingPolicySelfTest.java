package dev.analyzer.automation;

/** Overshoot/landing regressions independent of camera rendering or synthetic game inputs. */
public final class RouteFollowingPolicySelfTest {
    private static int assertions;
    public static void main(String[] args) {
        check(!RouteFollowingPolicy.reached(.1, .8, .15, true, false), "horizontal arrival is not descent completion");
        check(!RouteFollowingPolicy.reached(.1, .2, .15, true, false), "a descent waits for actual ground even near its final Y");
        check(RouteFollowingPolicy.reached(.1, 0, .15, true, true), "a grounded checked landing can advance the route");
        check(!RouteFollowingPolicy.reached(.16, 0, .15, false, true), "interaction centering keeps its actual precision requirement");
        check(RouteFollowingPolicy.reached(.27, 0, .28, false, true), "ordinary intermediate waypoints keep the walking tolerance");
        check(RouteFollowingPolicy.localCorrection(false, true, false, .7), "a nearby final approach uses local correction");
        check(RouteFollowingPolicy.localCorrection(false, false, true, 1.1), "descent braking begins before passing the landing center");
        check(!RouteFollowingPolicy.localCorrection(false, false, true, 1.3), "far route travel still uses its checked heading");
        check(RouteFollowingPolicy.localCorrection(true, false, false, .6), "cell recentering does not require a reverse camera turn");
        var overshot = RouteFollowingPolicy.correct(-.1, 0, .2, 0, -90);
        check(overshot.backward() && !overshot.forward(), "overshoot is braked with a back key while keeping the view direction");
        check(RouteFollowingPolicy.correct(1, 0, 0, 0, -90).forward(), "east-facing correction walks forward toward an east target");
        check(RouteFollowingPolicy.correct(1, 0, 0, 0, 90).backward(), "a close east target can be reached without spinning a west-facing camera");
        check(RouteFollowingPolicy.correct(0, 1, 0, 0, -90).right(), "local sideways correction uses the actual view-relative right key");
        check(!RouteFollowingPolicy.safeCorrection(overshot, .2, 0, -90, (dx, dz) -> dx >= -.01),
                "braking cannot press back into an unsupported cell even when momentum points toward safe ground");
        check(!RouteFollowingPolicy.safeCorrection(overshot, .2, 0, -90, (dx, dz) -> dx <= .4),
                "a safe back key does not authorize unchecked forward drift");
        check(RouteFollowingPolicy.safeCorrection(overshot, .2, 0, -90, (dx, dz) -> Math.abs(dx) <= .7 && Math.abs(dz) < .001),
                "a checked straight braking corridor permits ordinary opposite-key correction");
        check(!RouteFollowingPolicy.safeCorrection(new RouteFollowingPolicy.Steering(true, false, true, false), 0, 0, 0,
                (dx, dz) -> !(dx > .1 && dz > .1)), "combined diagonal input must have a checked diagonal corridor");
        check(!RouteFollowingPolicy.safeCorrection(overshot, Double.NaN, 0, -90, (dx, dz) -> true),
                "invalid observed momentum cannot authorize movement");
        for (int yaw = -360; yaw <= 360; yaw += 15) {
            var input = RouteFollowingPolicy.correct(.14, -.2, -.03, .02, yaw);
            check(!(input.forward() && input.backward()) && !(input.left() && input.right()), "a correction never holds opposing directions");
        }
        var invalid = RouteFollowingPolicy.correct(Double.NaN, 0, 0, 0, 0);
        check(!invalid.forward() && !invalid.backward() && !invalid.left() && !invalid.right(), "invalid observations release correction directions");
        // A descending player can pass the target while falling. Normal key acceleration/friction,
        // with a fixed camera heading, must brake and recenter after the actual landing.
        double x = .1, velocity = .24, greatest = x;
        for (int tick = 0; tick < 100; tick++) {
            var input = RouteFollowingPolicy.correct(-x, 0, velocity, 0, -90);
            double acceleration = tick < 5 ? .02 : .08, friction = tick < 5 ? .91 : .6;
            velocity = (velocity + ((input.forward() ? 1 : 0) - (input.backward() ? 1 : 0)) * acceleration) * friction;
            x += velocity; greatest = Math.max(greatest, x);
        }
        check(greatest < 1, "landing correction bounds drift while ordinary fall momentum settles");
        check(Math.abs(x) < .15 && Math.abs(velocity) < .035, "normal movement converges to the interaction tolerance without changing yaw");
        System.out.println("Route following policy passed " + assertions + " assertions; landing drift=" + greatest + ", final offset=" + x);
    }
    private static void check(boolean condition, String detail) { assertions++; if (!condition) throw new AssertionError(detail); }
}
