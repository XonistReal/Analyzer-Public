package dev.analyzer.render;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.EspSettings;
import dev.analyzer.finder.FinderEngine;
import dev.analyzer.finder.RegionMapPolicy;
import dev.analyzer.finder.StashJournal;
import dev.analyzer.ui.GuiDraw;
import dev.analyzer.ui.HudWidgets;
import dev.analyzer.ui.Palette;
import dev.analyzer.ui.StudioAuxiliary;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Original coordinate sectors. No historical server region IDs or ownership claims are inferred. */
public final class RegionMapHud {
    private static final String MODULE = "region-map";
    private static boolean registered;
    private RegionMapHud() { }

    public static void register(AnalyzerCore core) {
        if (registered) return;
        registered = true;
        HudRenderCallback.EVENT.register((draw, tickCounter) -> render(core, draw));
    }

    private static void render(AnalyzerCore core, DrawContext draw) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.currentScreen != null || client.options.hudHidden
                || client.getDebugHud().shouldShowDebugHud() || !core.isEnabled(MODULE)) return;
        int cells;
        try { cells = Integer.parseInt(core.getChoice(MODULE, "cells")); }
        catch (NumberFormatException ignored) { cells = 5; }
        cells = cells == 3 || cells == 5 || cells == 7 || cells == 9 ? cells : 5;
        int cellSize = (int) core.getNumber(MODULE, "grid-size");
        if (cellSize < 1) return;
        String theme = core.getChoice("hud", "theme");
        double opacity = Math.clamp(core.getNumber(MODULE, "opacity") / 100, 0, 1);
        int color = EspSettings.color(core.getChoice(MODULE, "marker-color"), core.getNumber(MODULE, "marker-red"),
                core.getNumber(MODULE, "marker-green"), core.getNumber(MODULE, "marker-blue"));
        float scale = HudWidgets.scale(core, draw);
        int panelWidth = 162, panelHeight = 204;
        scale = Math.max(.2f, Math.min(scale, Math.min((draw.getScaledWindowWidth() - 16f) / panelWidth,
                (draw.getScaledWindowHeight() - 16f) / panelHeight)));
        int logicalWidth = (int) (draw.getScaledWindowWidth() / scale), logicalHeight = (int) (draw.getScaledWindowHeight() / scale);
        int x = (int) Math.round(Math.max(0, logicalWidth - panelWidth) * optionalNumber(core, "position-x", 98) / 100);
        int y = (int) Math.round(Math.max(0, logicalHeight - panelHeight) * optionalNumber(core, "position-y", 70) / 100);
        try (GuiDraw.GlassScope optical = GuiDraw.glassScope(draw, theme); GuiDraw.FontScope font = GuiDraw.sansFont()) {
            draw.getMatrices().push();
            try {
                draw.getMatrices().scale(scale, scale, 1);
                draw.getMatrices().translate(x, y, 0);
                HudWidgets.panel(draw, 0, 0, panelWidth, panelHeight, theme, color, opacity * .88);
                GuiDraw.heading(draw, client.textRenderer, "Region grid", 12, 10, .96f, fade(StudioAuxiliary.TEXT, opacity));
                String cellCaption = core.getBoolean(MODULE, "show-labels")
                        ? "Cell " + coordinateCell(client.player.getX(), cellSize) + ", " + coordinateCell(client.player.getZ(), cellSize)
                        : cellSize + " blocks per cell";
                GuiDraw.label(draw, client.textRenderer, GuiDraw.truncate(client.textRenderer, cellCaption, 120, .8f),
                        12, 29, .8f, fade(StudioAuxiliary.MUTED, opacity));
                grid(core, client, draw, cells, cellSize, color, opacity);
            } finally { draw.getMatrices().pop(); }
        }
    }

    private static void grid(AnalyzerCore core, MinecraftClient client, DrawContext draw, int cells, int cellSize, int color, double opacity) {
        double playerX = client.player.getX(), playerZ = client.player.getZ();
        long centerX = coordinateCell(playerX, cellSize), centerZ = coordinateCell(playerZ, cellSize);
        long firstX = centerX - cells / 2, firstZ = centerZ - cells / 2;
        int mapX = 15, mapY = 48, size = 132;
        double pitch = size / (double) cells;
        int currentX = mapX + (int) Math.floor(cells / 2 * pitch), currentY = mapY + (int) Math.floor(cells / 2 * pitch);
        int currentRight = mapX + (int) Math.ceil((cells / 2 + 1) * pitch), currentBottom = mapY + (int) Math.ceil((cells / 2 + 1) * pitch);
        StudioAuxiliary.surface(draw, currentX, currentY, currentRight - currentX, currentBottom - currentY, 2,
                fade(Palette.alpha(color, 28), opacity), fade(Palette.alpha(color, 16), opacity), 0, 0, 0, 0);
        for (int i = 0; i <= cells; i++) {
            float offset = (float) (i * pitch);
            StudioAuxiliary.surface(draw, mapX + offset, mapY, .7f, size, .3f,
                    fade(0x667E7B84, opacity), fade(0x667E7B84, opacity), 0, 0, 0, 0);
            StudioAuxiliary.surface(draw, mapX, mapY + offset, size, .7f, .3f,
                    fade(0x667E7B84, opacity), fade(0x667E7B84, opacity), 0, 0, 0, 0);
        }
        String dimension = client.world.getRegistryKey().getValue().toString();
        if (core.getBoolean(MODULE, "show-stashes")) {
            int drawn = 0;
            for (StashJournal.Entry entry : FinderEngine.history()) {
                if (!entry.dimension().equals(dimension)) continue;
                double u = projection(entry.chunkX() * 16.0 + 8, firstX, cellSize, cells);
                double v = projection(entry.chunkZ() * 16.0 + 8, firstZ, cellSize, cells);
                if (!RegionMapPolicy.visible(u) || !RegionMapPolicy.visible(v)) continue;
                double px = mapX + u * size, pz = mapY + v * size;
                StudioAuxiliary.surface(draw, (float) px - 2, (float) pz - 2, 4, 4, 1,
                        fade(color, opacity), fade(color, opacity), 0, 0, 0, 0);
                if (++drawn >= 128) break;
            }
        }
        if (core.getBoolean(MODULE, "show-player")) {
            double px = mapX + projection(playerX, firstX, cellSize, cells) * size;
            double pz = mapY + projection(playerZ, firstZ, cellSize, cells) * size;
            double yaw = Math.toRadians(client.player.getYaw());
            GuiDraw.stroke(draw, px, pz, px - Math.sin(yaw) * 7, pz + Math.cos(yaw) * 7, 1.4f, fade(0xFFF7F5F8, opacity));
            StudioAuxiliary.surface(draw, (float) px - 2.2f, (float) pz - 2.2f, 4.4f, 4.4f, 2.2f,
                    fade(0xFFFFFFFF, opacity), fade(0xFFFFFFFF, opacity), 0, 0, fade(color, opacity * .3), 3);
        }
        String caption = core.getBoolean(MODULE, "show-coordinates") ? "X " + (long) Math.floor(playerX) + "  Z " + (long) Math.floor(playerZ)
                : core.getBoolean(MODULE, "show-labels") ? "Cell " + centerX + ", " + centerZ : "North ↑";
        GuiDraw.label(draw, client.textRenderer, GuiDraw.truncate(client.textRenderer, caption, 140, .8f), 12, 189, .8f, fade(StudioAuxiliary.TEXT, opacity));
        if (core.getBoolean(MODULE, "show-labels")) {
            String north = "N";
            GuiDraw.label(draw, client.textRenderer, north, 141, 30, .8f, fade(color, opacity));
        }
    }

    /** floor, rather than integer truncation, keeps negative block coordinates in the correct cell. */
    public static long coordinateCell(double coordinate, int cellSize) {
        return RegionMapPolicy.coordinateCell(coordinate, cellSize);
    }

    /** Normalized position in a bounded grid; callers clip outside markers rather than clamp them to an edge. */
    public static double projection(double coordinate, long firstCell, int cellSize, int cells) {
        return RegionMapPolicy.projection(coordinate, firstCell, cellSize, cells);
    }

    private static double optionalNumber(AnalyzerCore core, String setting, double fallback) {
        try { return Math.clamp(core.getNumber(MODULE, setting), 0, 100); }
        catch (IllegalArgumentException ignored) { return fallback; }
    }

    private static int fade(int color, double opacity) { return Palette.alpha(color, (int) Math.round((color >>> 24) * opacity)); }
}
