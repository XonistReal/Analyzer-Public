package dev.analyzer.ui;

import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.effects.HomeEffects;
import dev.analyzer.effects.UiShader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.option.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screen.option.LanguageOptionsScreen;
import net.minecraft.client.gui.screen.CreditsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.realms.gui.screen.RealmsMainScreen;
import net.minecraft.text.Text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/** Native title navigation over Minecraft's panorama or original Orbit GLSL. */
public final class HomeScreen extends Screen {
    private static final String[] SECTIONS = {"singleplayer", "multiplayer", "realms", "analyzer", "options", "quit"};
    private static final String[] LABELS = {"Singleplayer", "Multiplayer", "Realms", "Modules", "Options", "Quit"};
    private static final String[] IDS = {"home-worlds", "home-servers", "home-realms", "home-analyzer", "home-options", "home-quit"};
    private static final int WHITE = 0xFFF8F7FA, MUTED = 0xFFD0CAD2;
    private final AnalyzerCore core;
    private final long opened = System.nanoTime();
    private final Map<String, ButtonWidget> controls = new LinkedHashMap<>();
    private String error = "";
    private float panoramaYaw = 35;

    public HomeScreen(AnalyzerCore core) {super(Text.literal("Home"));this.core = core;}
    public AnalyzerCore core() {return core;}
    public String backgroundMode() {return core.getChoice("menu", "home-background");}
    public float panoramaYaw() {return panoramaYaw;}
    @Override public boolean shouldPause() {return false;}
    @Override public boolean shouldCloseOnEsc() {return false;}
    private float unit() {return (client == null ? width : client.getWindow().getScaledWidth()) / 1000f;}
    private float canvasHeight() {return (client == null ? height : client.getWindow().getScaledHeight()) / unit();}

    @Override protected void init() {
        controls.clear();
        List<ButtonWidget> navigation = createNavigation(this, "");
        for (int i = 0; i < navigation.size(); i++) {addDrawableChild(navigation.get(i));controls.put(IDS[i], navigation.get(i));}
        footer("home-language", "Language", 26, canvasHeight() - 37, 77,
                () -> client.setScreen(new LanguageOptionsScreen(this, client.options, client.getLanguageManager())));
        footer("home-accessibility", "Accessibility", 112, canvasHeight() - 37, 99,
                () -> client.setScreen(new AccessibilityOptionsScreen(this, client.options)));
        footer("home-credits", "Credits", 220, canvasHeight() - 37, 71,
                () -> client.setScreen(new CreditsScreen(false, () -> client.setScreen(this))));
        footer("home-background", backgroundMode(), 841, 24, 129, this::switchBackground);
        footer("home-vanilla", "Vanilla menu", 869, canvasHeight() - 37, 102, this::useVanilla);
    }
    private void footer(String id, String title, float x, float y, int w, Runnable action) {
        ButtonWidget button = new NavButton(id, (int) (x * unit()), (int) (y * unit()), Math.round(w * unit()), Math.round(24 * unit()), Text.literal(title), b -> action.run(), false);
        addDrawableChild(button);controls.put(id, button);
    }
    public List<ButtonWidget> createNavigation(Screen owner, String active) {
        List<ButtonWidget> buttons = new ArrayList<>();
        float u = owner.width / 1000f, logicalHeight = owner.height / u;
        float top = Math.min(Math.max(122, Math.min(202, logicalHeight * .34f)), Math.max(90, logicalHeight - 305));
        float pitch = Math.min(42, Math.max(24, (logicalHeight - 58 - top) / SECTIONS.length));
        float buttonHeight = Math.min(34, pitch - 5);
        for (int i = 0; i < SECTIONS.length; i++) {
            String section = SECTIONS[i];
            ButtonWidget button = new NavButton("nav:" + section, Math.round(26 * u), Math.round((top + i * pitch) * u),
                    Math.round(222 * u), Math.round(buttonHeight * u), Text.literal(LABELS[i]), b -> {if (!section.equalsIgnoreCase(active)) openSection(section);}, section.equalsIgnoreCase(active));
            if (section.equals("multiplayer") && !MinecraftClient.getInstance().isMultiplayerEnabled()) {
                button.active = false;button.setTooltip(Tooltip.of(Text.literal("Multiplayer is unavailable for this account.")));
            }
            if (section.equals("realms") && !MinecraftClient.getInstance().isRealmsEnabled()) {
                button.active = false;button.setTooltip(Tooltip.of(Text.literal("Realms is unavailable for this account.")));
            }
            buttons.add(button);
        }
        return buttons;
    }
    public int[] pageBounds() {
        float u = unit();
        return new int[]{Math.round(284 * u), Math.round(68 * u), Math.round(675 * u), Math.round((canvasHeight() - 116) * u)};
    }
    public void openSection(String section) {
        switch (section.toLowerCase(Locale.ROOT)) {
            case "singleplayer" -> client.setScreen(HomePages.create(this, "singleplayer"));
            case "multiplayer" -> {if (client.isMultiplayerEnabled()) client.setScreen(HomePages.create(this, "multiplayer"));}
            case "realms" -> {if (client.isRealmsEnabled()) client.setScreen(new RealmsMainScreen(this));}
            case "options" -> client.setScreen(HomePages.create(this, "options"));
            case "analyzer" -> {
                Screen menu = MenuScreens.create(core);
                if (menu instanceof CatalogScreen catalog) catalog.withParent(this);
                client.setScreen(menu);
            }
            case "quit" -> client.scheduleStop();
        }
    }
    private void save() {try {core.save();error = "";} catch (IOException failure) {error = "Could not save home preference: " + failure.getMessage();}}
    private void switchBackground() {
        core.setValue("menu", "home-background", backgroundMode().equals("Panorama") ? "Orbit" : "Panorama");
        save();controls.get("home-background").setMessage(Text.literal(backgroundMode()));
    }
    private void useVanilla() {
        core.setValue("menu", "home-enabled", false);save();
        if (error.isEmpty()) client.setScreen(new TitleScreen());
        else core.setValue("menu", "home-enabled", true);
    }
    @Override public void tick() {if (!core.getBoolean("menu", "home-enabled")) client.setScreen(new TitleScreen());}
    public void drawBackdrop(DrawContext draw, boolean blurred) {
        float motion = (float) core.getNumber("menu", "home-motion");
        if (backgroundMode().equals("Panorama")) {
            panoramaYaw = 35 + (float) ((System.nanoTime() - opened) / 1e9) * .7f * motion * client.options.getPanoramaSpeed().getValue().floatValue();
            draw.draw();PANORAMA_RENDERER.draw(client, 10f, panoramaYaw, 1f);draw.draw();
        }
        HomeEffects.draw(draw, backgroundMode(), blurred && core.getBoolean("menu", "home-blur"), motion);
    }
    public void drawChrome(DrawContext draw, int mouseX, int mouseY, String section, boolean withPanel) {
        float u = unit();int accent = HudWidgets.accent(core);
        try (GuiDraw.FontScope font = GuiDraw.sansFont()) {
            draw.getMatrices().push();draw.getMatrices().scale(u, u, 1);
            BrandAssets.monogram(draw, 26, 13, 44);
            GuiDraw.label(draw, textRenderer, "Welcome, " + client.getSession().getUsername(), 29, 64, .84f, MUTED);
            if (withPanel) {
                int panelHeight = Math.round(canvasHeight() - 116);
                if (!UiShader.surface(draw, 284, 68, 675, panelHeight, 17, 0xA21C1B20, 0xB0151419, 0x65000000, 19, 0, 0))
                    GuiDraw.rounded(draw, 284, 68, 675, panelHeight, 17, 0xA21C1B20);
                GuiDraw.roundedBorder(draw, 284, 68, 675, panelHeight, 17, 0x22FFFFFF);
                String title = section.isBlank() ? "Home" : section.substring(0, 1).toUpperCase(Locale.ROOT) + section.substring(1);
                GuiDraw.heading(draw, textRenderer, title, 307, 88, 1.35f, WHITE);
            } else if (backgroundMode().equals("Panorama")) {
                BrandAssets.wordmark(draw, 477, canvasHeight() * .42f - 28, 350);
            }
            GuiDraw.label(draw, textRenderer, "Minecraft 1.21.4", 28, canvasHeight() - 12, .8f, MUTED);
            if (!error.isEmpty()) GuiDraw.label(draw, textRenderer, GuiDraw.truncate(textRenderer, error, 650, .9f), 307, canvasHeight() - 31, .9f, Palette.ERROR);
            draw.getMatrices().pop();
        }
    }
    @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        drawBackdrop(draw, false);drawChrome(draw, mouseX, mouseY, "", false);
        for (var child : children()) if (child instanceof Drawable drawable) drawable.render(draw, mouseX, mouseY, delta);
    }
    public double[] controlPoint(String id) {
        ButtonWidget button = controls.get(id);
        return button == null ? null : new double[]{button.getX() + button.getWidth() / 2D, button.getY() + button.getHeight() / 2D};
    }
    /** Shader visuals retain the real button's bounds, active state, focus and action. */
    public static void drawNativeButton(DrawContext draw, ButtonWidget button, int mouseX, int mouseY) {
        if (!button.visible) return;
        var mc = MinecraftClient.getInstance();float u = mc.getWindow().getScaledWidth() / 1000f;
        int x = Math.round(button.getX() / u), y = Math.round(button.getY() / u), w = Math.round(button.getWidth() / u), h = Math.round(button.getHeight() / u);
        boolean hover = button.isMouseOver(mouseX, mouseY), focused = button.isFocused();
        int accent = HudWidgets.accent(dev.analyzer.AnalyzerClient.CORE);
        boolean nav = button instanceof NavButton, selected = nav && ((NavButton) button).selected;
        try (GuiDraw.FontScope font = GuiDraw.sansFont()) {
            draw.getMatrices().push();draw.getMatrices().scale(u, u, 1);
            if (!nav || hover || focused || selected) {
                int top = selected ? Palette.alpha(accent, 45) : button.active ? 0x681E1D23 : 0x351E1D23;
                if (!UiShader.surface(draw, x, y, w, h, 8, top, top, 0, 0, 0, 0)) GuiDraw.rounded(draw, x, y, w, h, 8, top);
            }
            int color = !button.active ? 0xFF9B97A0 : selected ? accent : WHITE;
            boolean mainNav = nav && ((NavButton) button).id.startsWith("nav:");
            if (mainNav) drawIcon(draw, ((NavButton) button).id, x + 12, y + h / 2 - 6, color);
            float fontSize = mainNav ? 1.05f : .91f;
            String text = GuiDraw.truncate(mc.textRenderer, button.getMessage().getString(), w - (mainNav ? 40 : 14), fontSize);
            float tx = mainNav ? x + 35 : x + (w - GuiDraw.width(mc.textRenderer, text, fontSize)) / 2;
            GuiDraw.label(draw, mc.textRenderer, text, tx, y + (h - 12) / 2f, fontSize, color);
            if (focused) GuiDraw.roundedBorder(draw, x, y, w, h, 8, Palette.alpha(accent, 170));
            draw.getMatrices().pop();
        }
    }
    private static void drawIcon(DrawContext draw, String id, int x, int y, int color) {
        if (id.endsWith("analyzer")) {
            for (int row = 0; row < 2; row++) for (int column = 0; column < 2; column++)
                if (!UiShader.surface(draw, x + column * 7, y + row * 7, 5, 5, 1.5f, color, color, 0, 0, 0, 0))
                    GuiDraw.rounded(draw, x + column * 7, y + row * 7, 5, 5, 2, color);
            return;
        }
        if (id.endsWith("singleplayer")) {GuiDraw.cube(draw, x, y, 12, color);return;}
        if (id.endsWith("multiplayer") || id.endsWith("realms")) {
            if (!UiShader.surface(draw, x, y, 12, 12, 6, Palette.alpha(color, 40), Palette.alpha(color, 40), 0, 0, 0, 0)) GuiDraw.rounded(draw, x, y, 12, 12, 6, color);
            GuiDraw.stroke(draw, x + 1, y + 6, x + 11, y + 6, 1, color);GuiDraw.stroke(draw, x + 6, y + 1, x + 6, y + 11, 1, color);
        } else if (id.endsWith("quit")) {
            GuiDraw.stroke(draw, x, y, x, y + 12, 1, color);GuiDraw.stroke(draw, x, y + 6, x + 11, y + 6, 1, color);
            GuiDraw.stroke(draw, x + 7, y + 2, x + 11, y + 6, 1, color);GuiDraw.stroke(draw, x + 7, y + 10, x + 11, y + 6, 1, color);
        } else {
            GuiDraw.roundedBorder(draw, x, y, 12, 12, 4, color);GuiDraw.rounded(draw, x + 4, y + 4, 4, 4, 2, color);
        }
    }
    private static final class NavButton extends ButtonWidget {
        private final String id;
        private final boolean selected;
        NavButton(String id, int x, int y, int w, int h, Text text, PressAction action, boolean selected) {
            super(x, y, w, h, text, action, DEFAULT_NARRATION_SUPPLIER);this.id = id;this.selected = selected;
        }
        @Override protected void renderWidget(DrawContext draw, int mx, int my, float delta) {drawNativeButton(draw, this, mx, my);}
    }
}
