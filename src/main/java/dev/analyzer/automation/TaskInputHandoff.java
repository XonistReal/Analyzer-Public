package dev.analyzer.automation;

/** A reviewed Start/Resume may wait for released controls, but never grants a period of unchecked input. */
final class TaskInputHandoff {
    enum Result { INACTIVE, MENU_OPEN, UNFOCUSED, CONTROLS_HELD, START }
    private boolean pending;

    void request() { pending = true; }
    void cancel() { pending = false; }
    boolean pending() { return pending; }

    Result poll(boolean menuOpen, boolean focused, boolean controlsHeld) {
        if (!pending) return Result.INACTIVE;
        if (menuOpen) return Result.MENU_OPEN;
        if (!focused) return Result.UNFOCUSED;
        if (controlsHeld) return Result.CONTROLS_HELD;
        pending = false;
        return Result.START;
    }
}
