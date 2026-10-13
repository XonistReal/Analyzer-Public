package dev.analyzer.automation;

import java.util.concurrent.atomic.AtomicReference;

/** Checks rollback and external edits without needing Minecraft or Baritone on the classpath. */
public final class ScopedSettingLeaseSelfTest {
    private static int assertions;
    public static void main(String[] args) {
        var radius = new AtomicReference<>(7);
        var avoidance = new AtomicReference<>(false);
        var lease = new ScopedSettingLease();
        lease.bind(radius::get, radius::set, value -> Math.max(10, value));
        lease.bind(avoidance::get, avoidance::set, value -> true);
        lease.apply();
        check(radius.get() == 10 && avoidance.get(), "profile applies temporary values");
        check(lease.active() && lease.changedSettings() == 2, "active profile records changes");
        lease.apply();
        lease.close();
        check(radius.get() == 7 && !avoidance.get(), "duplicate apply retains the original snapshot");
        check(!lease.active() && lease.changedSettings() == 0, "closed profile releases its snapshot");
        lease.close();
        check(radius.get() == 7, "restoration is idempotent");

        lease.apply();
        radius.set(14);
        lease.close();
        check(radius.get() == 14 && !avoidance.get(), "an external edit survives while untouched values restore");
        lease.apply();
        check(radius.get() == 14, "a stricter preference is retained by the transform");
        lease.close();
        check(radius.get() == 14, "each new process snapshots the current preference");

        var first = new AtomicReference<>(1);
        var second = new AtomicReference<>(2);
        var failing = new ScopedSettingLease();
        failing.bind(first::get, first::set, value -> 3);
        failing.bind(second::get, value -> { if (value == 4) throw new IllegalStateException("fixture"); second.set(value); }, value -> 4);
        boolean thrown = false;
        try { failing.apply(); } catch (IllegalStateException expected) { thrown = true; }
        check(thrown && !failing.active(), "a failed apply releases the profile");
        check(first.get() == 1 && second.get() == 2, "a partial apply rolls back earlier writes");
        System.out.println("Scoped settings lease passed " + assertions + " assertions");
    }
    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
}
