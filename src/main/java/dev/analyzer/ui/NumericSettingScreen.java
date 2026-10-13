package dev.analyzer.ui;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.io.IOException;

/** Precise keyboard entry for any numeric slider, with core normalization and explicit save. */
public final class NumericSettingScreen extends Screen {
    private static final int W = 440, H = 246;
    private final AnalyzerCore core;
    private final String module;
    private final Setting<?> setting;
    private final Screen parent;
    private TextFieldWidget value;
    private String error = "";
    private float scale = 1, originX, originY;
    public NumericSettingScreen(AnalyzerCore core, String module, Setting<?> setting, Screen parent) {
        super(Text.literal(setting.label())); this.core = core; this.module = module; this.setting = setting; this.parent = parent;
    }
    @Override public boolean shouldPause() { return false; }
    @Override protected void init() {
        String text = value == null ? Double.toString(core.getNumber(module, setting.id())) : value.getText();
        value = addDrawableChild(new TextFieldWidget(textRenderer, 32, 101, 376, 22, Text.literal(setting.label())));
        value.setDrawsBackground(false); value.setEditableColor(StudioAuxiliary.TEXT);
        value.setMaxLength(32); value.setText(text);
        value.setSelectionStart(0); value.setSelectionEnd(value.getText().length()); setInitialFocus(value);
        addDrawableChild(new StudioAuxiliary.Button(core, 20, 151, 194, 36, Text.literal("Apply"), button -> apply()));
        addDrawableChild(new StudioAuxiliary.Button(core, 226, 151, 194, 36, Text.literal("Cancel"), button -> close()));
        geometry();
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
        try (GuiDraw.GlassScope optical = StudioAuxiliary.backdropScope(draw, StudioAuxiliary.theme(core),
                () -> StudioAuxiliary.backdrop(draw, core, width, height))) {
        geometry();
        draw.getMatrices().push(); draw.getMatrices().translate(originX, originY, 0); draw.getMatrices().scale(scale, scale, 1);
        try (var font = GuiDraw.sansFont()) {
            GuiDraw.shaderPanel(draw, 0, 0, W, H, 22, StudioAuxiliary.accent(core), StudioAuxiliary.glass(core), StudioAuxiliary.theme(core));
            GuiDraw.heading(draw, textRenderer, GuiDraw.truncate(textRenderer, title.getString(), 400, 1.55f), 20, 22, 1.55f, StudioAuxiliary.TEXT);
            GuiDraw.label(draw, textRenderer, "Range " + setting.min() + "–" + setting.max() + "  /  step " + setting.step(), 20, 54, .94f, StudioAuxiliary.MUTED);
            StudioAuxiliary.field(draw, core, 20, 86, 400, 44, value.isFocused());
            super.render(draw, Math.round((mouseX - originX) / scale), Math.round((mouseY - originY) / scale), delta);
            GuiDraw.label(draw, textRenderer, GuiDraw.truncate(textRenderer, error.isEmpty() ? "Enter to apply  ·  Esc to cancel" : error, 400, .94f),
                    20, 209, .94f, error.isEmpty() ? StudioAuxiliary.SUBTLE : Palette.ERROR);
        }
        draw.getMatrices().pop();
        }
    }
    private void geometry() {
        scale = StudioAuxiliary.canvasScale(client, W, H, .82, .70);
        originX = (width - W * scale) / 2; originY = (height - H * scale) / 2;
    }
    /** The full-screen backdrop is drawn before entering the dialog's local canvas. */
    @Override public void renderBackground(DrawContext draw, int mouseX, int mouseY, float delta) { }
    @Override public boolean mouseClicked(double x, double y, int button) { return super.mouseClicked((x - originX) / scale, (y - originY) / scale, button); }
    @Override public boolean mouseReleased(double x, double y, int button) { return super.mouseReleased((x - originX) / scale, (y - originY) / scale, button); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return super.mouseDragged((x - originX) / scale, (y - originY) / scale, button, dx / scale, dy / scale); }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (getFocused() instanceof StudioAuxiliary.Button button) button.onPress(); else apply();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void close() { if (client != null) client.setScreen(parent); }
    public double[] controlPoint(String id) {
        int x, y;
        switch (id) { case "value" -> { x = 220; y = 112; } case "apply" -> { x = 117; y = 169; } case "cancel" -> { x = 323; y = 169; } default -> { return null; } }
        return new double[]{originX + x * scale, originY + y * scale};
    }
    public String inputValue() { return value == null ? "" : value.getText(); }
    public String errorText() { return error; }
}
