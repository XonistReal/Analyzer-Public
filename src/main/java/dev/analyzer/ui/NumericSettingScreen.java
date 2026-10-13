package dev.analyzer.ui;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.io.IOException;

/** Precise keyboard entry for any numeric slider, with core normalization and explicit save. */
final class NumericSettingScreen extends Screen {
    private final AnalyzerCore core;
    private final String module;
    private final Setting<?> setting;
    private final Screen parent;
    private TextFieldWidget value;
    private String error = "";
    NumericSettingScreen(AnalyzerCore core, String module, Setting<?> setting, Screen parent) {
        super(Text.literal(setting.label())); this.core = core; this.module = module; this.setting = setting; this.parent = parent;
    }
    @Override public boolean shouldPause() { return false; }
    @Override protected void init() {
        int left = width / 2 - 130, top = height / 2 - 65;
        value = addDrawableChild(new TextFieldWidget(textRenderer, left + 10, top + 48, 240, 22, Text.literal(setting.label())));
        value.setMaxLength(32); value.setText(Double.toString(core.getNumber(module, setting.id())));
        value.setSelectionStart(0); value.setSelectionEnd(value.getText().length()); setInitialFocus(value);
        addDrawableChild(ButtonWidget.builder(Text.literal("Apply"), button -> apply()).dimensions(left + 10, top + 82, 115, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close()).dimensions(left + 135, top + 82, 115, 20).build());
    }
    private void apply() {
        try {
            double parsed = Double.parseDouble(value.getText().strip());
            if (!Double.isFinite(parsed)) throw new NumberFormatException();
            core.setValue(module, setting.id(), parsed); core.save(); close();
        } catch (NumberFormatException ex) { error = "Enter a finite number."; }
        catch (IOException ex) { error = "Could not save: " + ex.getMessage(); }
    }
    @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        draw.fill(0, 0, width, height, 0xE6081018);
        int left = width / 2 - 130, top = height / 2 - 65;
        GuiDraw.outlined(draw, left, top, 260, 132, 8, Palette.BORDER, Palette.SURFACE);
        draw.drawText(textRenderer, Text.literal(GuiDraw.truncate(textRenderer, title.getString(), 240, 1)), left + 10, top + 12, Palette.TEXT, false);
        draw.drawText(textRenderer, Text.literal("Range " + setting.min() + "–" + setting.max() + " / step " + setting.step()), left + 10, top + 30, Palette.MUTED, false);
        if (!error.isEmpty()) draw.drawText(textRenderer, Text.literal(GuiDraw.truncate(textRenderer, error, 240, 1)), left + 10, top + 113, Palette.ERROR, false);
        super.render(draw, mouseX, mouseY, delta);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { apply(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void close() { if (client != null) client.setScreen(parent); }
}
