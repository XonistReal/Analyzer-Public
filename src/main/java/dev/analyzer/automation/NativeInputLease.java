package dev.analyzer.automation;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;

/** Reversible ordinary Minecraft key presses. Release restores actual hardware state, rather than a stale held key. */
final class NativeInputLease {
    private static NativeInputLease controller;
    private static Object reservedGroup;
    private static int reservations;
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Object group;
    private final Map<KeyBinding, Boolean> writes = new LinkedHashMap<>();
    NativeInputLease(Object group) { this.group = group; }
    boolean apply(boolean forward, boolean sprint, boolean jump) {
        return direction(forward, false, sprint, jump, false);
    }
    boolean backward(boolean jump) { return direction(false, true, false, jump, false); }
    boolean applySneaking(boolean forward, boolean jump) { return direction(forward, false, false, jump, true); }
    private boolean direction(boolean forward, boolean backward, boolean sprint, boolean jump, boolean sneak) {
        if (reservedGroup != null && reservedGroup != group) return false;
        if (controller != null && controller != this) {
            if (controller.group != group) return false;
            controller.close();
        }
        controller = this;
        write(mc.options.forwardKey, forward); write(mc.options.backKey, backward);
        write(mc.options.leftKey, false); write(mc.options.rightKey, false);
        write(mc.options.sprintKey, sprint && forward); write(mc.options.jumpKey, jump);
        write(mc.options.sneakKey, sneak);
        return true;
    }
    private void write(KeyBinding key, boolean state) { writes.put(key, state); key.setPressed(state); }
    void close() {
        for (var entry : writes.entrySet()) {
            // An unrelated live write wins. Otherwise resample the physical key instead of restoring a stale snapshot.
            if (entry.getKey().isPressed() != entry.getValue()) continue;
            entry.getKey().setPressed(physical(entry.getKey(), mc));
        }
        writes.clear(); if (controller == this) controller = null;
    }
    boolean active() { return controller == this; }
    boolean reserve() {
        if (reservedGroup != null && reservedGroup != group) return false;
        reservedGroup = group; reservations++; return true;
    }
    void releaseReservation() {
        if (reservedGroup == group && --reservations <= 0) { reservedGroup = null; reservations = 0; }
    }
    static boolean anotherController(NativeInputLease lease) {
        return reservedGroup != null && reservedGroup != lease.group || controller != null && controller.group != lease.group;
    }
    static boolean hasPhysicalInput(MinecraftClient mc) {
        for (KeyBinding key : new KeyBinding[]{mc.options.forwardKey, mc.options.backKey, mc.options.leftKey, mc.options.rightKey,
                mc.options.jumpKey, mc.options.sneakKey, mc.options.sprintKey, mc.options.attackKey, mc.options.useKey})
            if (physical(key, mc)) return true;
        return false;
    }
    private static boolean physical(KeyBinding binding, MinecraftClient mc) {
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(binding); long handle = mc.getWindow().getHandle();
        if (key.getCategory() == InputUtil.Type.MOUSE) return key.getCode() >= 0 && GLFW.glfwGetMouseButton(handle, key.getCode()) == GLFW.GLFW_PRESS;
        if (key.getCategory() == InputUtil.Type.KEYSYM) return key.getCode() >= 0 && InputUtil.isKeyPressed(handle, key.getCode());
        // Scancode bindings need the current platform's GLFW mapping, not the integer treated as a keycode.
        if (key.getCategory() == InputUtil.Type.SCANCODE) for (int code = GLFW.GLFW_KEY_SPACE; code <= GLFW.GLFW_KEY_LAST; code++)
            if (validKeycode(code) && GLFW.glfwGetKeyScancode(code) == key.getCode()) return GLFW.glfwGetKey(handle, code) == GLFW.GLFW_PRESS;
        return false;
    }
    private static boolean validKeycode(int code) {
        return code == 32 || code == 39 || code >= 44 && code <= 57 || code == 59 || code == 61 || code >= 65 && code <= 93
                || code == 96 || code == 161 || code == 162 || code >= 256 && code <= 269 || code >= 280 && code <= 284
                || code >= 290 && code <= 314 || code >= 320 && code <= 336 || code >= 340 && code <= 348;
    }
}
