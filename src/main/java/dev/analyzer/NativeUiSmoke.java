package dev.analyzer;

import net.minecraft.client.MinecraftClient;

/** Compatibility run target for the current shader-menu acceptance fixture. */
final class NativeUiSmoke {
    private NativeUiSmoke() { }
    static void tick(MinecraftClient client, int tick) { ShaderStudioSmoke.tick(client, tick); }
}
