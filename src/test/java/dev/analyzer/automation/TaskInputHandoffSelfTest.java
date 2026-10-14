package dev.analyzer.automation;

/** Covers long held launch clicks, menu/focus changes and explicit cancellation without a game window. */
public final class TaskInputHandoffSelfTest {
    private static int assertions;
    public static void main(String[] args) {
        var handoff = new TaskInputHandoff();
        check(handoff.poll(false, true, false) == TaskInputHandoff.Result.INACTIVE, "idle controls never start a task");
        handoff.request();
        check(handoff.poll(true, true, true) == TaskInputHandoff.Result.MENU_OPEN, "Start click cannot act through its menu");
        for (int tick = 0; tick < 100; tick++)
            check(handoff.poll(false, true, true) == TaskInputHandoff.Result.CONTROLS_HELD && handoff.pending(), "held launch control never expires into movement");
        check(handoff.poll(false, false, false) == TaskInputHandoff.Result.UNFOCUSED, "releasing while away does not start a background task");
        check(handoff.poll(false, true, false) == TaskInputHandoff.Result.START && !handoff.pending(), "neutral focused gameplay starts the authorized task once");
        check(handoff.poll(false, true, false) == TaskInputHandoff.Result.INACTIVE, "completion of handoff cannot issue a duplicate start");
        check(handoff.poll(false, true, true) == TaskInputHandoff.Result.INACTIVE, "later manual input does not rearm startup grace");
        handoff.request();
        check(handoff.poll(false, true, true) == TaskInputHandoff.Result.CONTROLS_HELD, "explicit Resume also waits for its held click");
        handoff.cancel();
        check(handoff.poll(false, true, false) == TaskInputHandoff.Result.INACTIVE && !handoff.pending(), "Stop while waiting cannot start later on release");
        handoff.request();
        check(handoff.poll(true, true, false) == TaskInputHandoff.Result.MENU_OPEN, "opening a menu keeps the reviewed request inert");
        check(handoff.poll(false, true, true) == TaskInputHandoff.Result.CONTROLS_HELD, "movement held across menu closure must also be released");
        check(handoff.poll(false, true, false) == TaskInputHandoff.Result.START, "a later explicit request is independent of the cancelled request");
        System.out.println("Automation input handoff passed " + assertions + " assertions");
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
