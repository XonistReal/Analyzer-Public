package dev.analyzer.automation;

import net.fabricmc.loader.api.FabricLoader;

import java.util.Set;

/** Development-only physical-input fixture. Production always samples actual hardware. */
public final class AutomationInputDiagnostics {
    private static final Set<String> CONTROLS = Set.of("forward", "back", "left", "right", "jump", "sneak", "sprint", "attack", "use");
    private static Set<String> held;
    private AutomationInputDiagnostics() { }

    public static void developmentOverride(Set<String> heldControls) {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment() || !Boolean.getBoolean("analyzer.smoke")
                || !Boolean.getBoolean("analyzer.nativeAutomationSmoke"))
            throw new IllegalStateException("Automation input fixtures require the native development smoke run");
        if (heldControls != null && !CONTROLS.containsAll(heldControls))
            throw new IllegalArgumentException("Unknown automation input fixture control");
        held = heldControls == null ? null : Set.copyOf(heldControls);
    }

    static Set<String> heldOverride() { return held; }
}
