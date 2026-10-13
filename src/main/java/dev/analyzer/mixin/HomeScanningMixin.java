package dev.analyzer.mixin;

import dev.analyzer.ui.GuiDraw;
import dev.analyzer.ui.HomePage;
import dev.analyzer.ui.HomePages;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.LoadingDisplay;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla centers its LAN scan status on the full screen; an owned Home list has its own center. */
@Mixin(MultiplayerServerListWidget.ScanningEntry.class)
public abstract class HomeScanningMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void analyzer$panelScan(DrawContext draw, int index, int y, int x, int entryWidth, int entryHeight,
                                   int mouseX, int mouseY, boolean hovered, float delta, CallbackInfo callback) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof HomePage page)
                || !(page.controls().get("server-list") instanceof MultiplayerServerListWidget list)
                || !HomePages.ownsList(list)) return;
        float unit = client.getWindow().getScaledWidth() / 1000f;
        float center = (list.getX() + list.getWidth() / 2f) / unit;
        float top = (y + entryHeight / 2f) / unit - 13;
        String status = ((MultiplayerServerListWidget.ScanningEntry) (Object) this).getNarration().getString();
        String progress = LoadingDisplay.get(Util.getMeasuringTimeMs());
        try (GuiDraw.FontScope font = GuiDraw.sansFont()) {
            draw.getMatrices().push();draw.getMatrices().scale(unit, unit, 1);
            status = GuiDraw.truncate(client.textRenderer, status, list.getWidth() / unit - 24, 1.02f);
            GuiDraw.label(draw, client.textRenderer, status, center - GuiDraw.width(client.textRenderer, status, 1.02f) / 2, top, 1.02f, 0xFFF8F7FA);
            GuiDraw.label(draw, client.textRenderer, progress, center - GuiDraw.width(client.textRenderer, progress, .92f) / 2, top + 17, .92f, 0xFFD0CAD2);
            draw.getMatrices().pop();
        }
        callback.cancel();
    }
}
