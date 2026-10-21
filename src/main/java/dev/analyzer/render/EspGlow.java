package dev.analyzer.render;

import dev.analyzer.AnalyzerClient;
import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.EspSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

/**
 * Entity ESP "Glow": accepted entities go through Minecraft's own entity-outline pass in their ESP
 * color. Only the client's outline decision and outline color are answered; the entity's glowing
 * flag, team and server state are never changed. Like vanilla glowing, the outline shows through walls.
 */
public final class EspGlow {
    private EspGlow() { }

    /** @return the RGB outline color for an accepted entity, or -1 to keep native behavior */
    public static int color(Entity entity) {
        AnalyzerCore core = AnalyzerClient.CORE;
        MinecraftClient client = MinecraftClient.getInstance();
        if (core == null || entity == null || client.player == null || entity == client.player
                || entity == client.getCameraEntity() || entity.isRemoved() || !entity.isAlive()
                || !core.isEnabled("entity-esp") || !core.getChoice("entity-esp", "style").equals("Glow")) return -1;
        AnalyzerRenderer.EntityKind kind = AnalyzerRenderer.kind(entity);
        if (kind == null || !core.getBoolean("entity-esp", kind.setting)) return -1;
        double range = core.getNumber("entity-esp", "range");
        if (entity.squaredDistanceTo(client.gameRenderer.getCamera().getPos()) > range * range) return -1;
        return EspSettings.color(core.getChoice("entity-esp", kind.setting + "-color"),
                core.getNumber("entity-esp", kind.setting + "-red"), core.getNumber("entity-esp", kind.setting + "-green"),
                core.getNumber("entity-esp", kind.setting + "-blue")) & 0xFFFFFF;
    }
}
