package dev.analyzer.ui;

import dev.analyzer.AnalyzerClient;
import dev.analyzer.effects.UiShader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerWarningScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.screen.world.WorldListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.LayoutWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.TranslatableTextContent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Restyles native pages without copying their world, server, or options implementations. */
public final class HomePages {
    private static final String[] NAVIGATION = {"singleplayer", "multiplayer", "realms", "analyzer", "options", "quit"};
    private static final List<String> OPTIONS_ORDER = List.of("options.video", "options.sounds", "options.controls",
            "options.skinCustomisation", "options.chat", "options.language", "options.resourcepack",
            "options.accessibility", "options.telemetry", "options.credits_and_attribution");

    private HomePages() { }

    public static Screen create(HomeScreen home, String section) {
        return switch (section.toLowerCase(Locale.ROOT)) {
            case "singleplayer", "worlds" -> shortPane(home) ? new SelectWorldScreen(home) : new Worlds(home);
            case "multiplayer", "servers" -> MinecraftClient.getInstance().options.skipMultiplayerWarning
                    ? serverPage(home) : new MultiplayerWarning(home);
            case "options" -> shortPane(home) ? new OptionsScreen(home, MinecraftClient.getInstance().options) : new Options(home);
            default -> throw new IllegalArgumentException("No native home page for " + section);
        };
    }

    private static boolean shortPane(HomeScreen home) { return home.pageBounds()[3] < 145; }
    private static Screen serverPage(HomeScreen home) { return shortPane(home) ? new MultiplayerScreen(home) : new Servers(home); }

    /** Resize an already-open page through the same safe native fallback, outside render/init. */
    private static boolean fallbackAfterResize(HomePage page, Screen owner) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!shortPane(page.home()) || client.currentScreen != owner) return false;
        client.setScreen(create(page.home(), page.section()));
        return true;
    }

    /** Scoped by both screen type and widget identity, leaving all other Minecraft lists alone. */
    public static boolean ownsList(Object list) {
        Screen screen = MinecraftClient.getInstance().currentScreen;
        return screen instanceof HomePage && screen.children().contains(list);
    }

    /** Match actual native children rather than a class name or global appearance preference. */
    public static boolean ownsWidget(Object widget) { return ownsList(widget); }

    public static int rowWidth(Object list, int nativeWidth) {
        return ownsList(list) && list instanceof ClickableWidget widget
                ? Math.min(nativeWidth, Math.max(1, widget.getWidth() - 20)) : nativeWidth;
    }

    /** Actual GUI coordinates used by the isolated native smoke fixture. */
    public static double[] controlPoint(Screen screen, String id) {
        ClickableWidget widget = screen instanceof HomePage page ? page.controls().get(id) : null;
        if (widget == null && (screen instanceof SelectWorldScreen || screen instanceof MultiplayerScreen || screen instanceof OptionsScreen))
            widget = nativeControls(screen).get(id);
        if (widget == null && screen instanceof MultiplayerWarning) {
            String key = switch (id) { case "warning-proceed" -> "gui.proceed"; case "back" -> "gui.back"; default -> id; };
            for (Element child : screen.children()) {
                if (child instanceof ClickableWidget candidate && key.equals(key(candidate))) { widget = candidate; break; }
            }
        }
        return widget == null || !widget.visible ? null
                : new double[]{widget.getX() + widget.getWidth() * .5, widget.getY() + widget.getHeight() * .5};
    }

    private static String key(ClickableWidget widget) {
        return widget.getMessage().getContent() instanceof TranslatableTextContent translated
                ? translated.getKey() : widget.getMessage().getString();
    }

    private static Map<String, ClickableWidget> nativeControls(Screen screen) {
        Map<String, ClickableWidget> controls = new LinkedHashMap<>();
        for (Element child : screen.children()) if (child instanceof ClickableWidget widget) {
            String key = key(widget);
            controls.put(key, widget);
            String alias = switch (key) {
                case "selectWorld.select" -> "world-play";
                case "selectWorld.create" -> "world-create";
                case "selectWorld.edit" -> "world-edit";
                case "selectWorld.delete" -> "world-delete";
                case "selectWorld.recreate" -> "world-recreate";
                case "selectServer.select" -> "server-join";
                case "selectServer.direct" -> "server-direct";
                case "selectServer.add" -> "server-add";
                case "selectServer.edit" -> "server-edit";
                case "selectServer.delete" -> "server-delete";
                case "selectServer.refresh" -> "server-refresh";
                case "gui.back", "gui.done" -> "back";
                case "options.skinCustomisation" -> "options-skin";
                case "options.resourcepack" -> "options-resource-packs";
                case "options.credits_and_attribution" -> "options-credits";
                default -> key.startsWith("options.") ? "options-" + key.substring("options.".length()) : null;
            };
            if (alias != null) controls.put(alias, widget);
        }
        return controls;
    }

    private static void navigationControls(Map<String, ClickableWidget> controls, List<ButtonWidget> navigation) {
        for (int index = 0; index < navigation.size() && index < NAVIGATION.length; index++)
            controls.put("nav:" + NAVIGATION[index], navigation.get(index));
    }

    private static void place(Map<String, ClickableWidget> controls, String id, int x, int y, int width, int height) {
        ClickableWidget widget = controls.get(id);
        if (widget != null) widget.setDimensionsAndPosition(Math.max(1, width), Math.max(1, height), x, y);
    }

    private static void render(HomePage page, Screen screen, DrawContext draw, int mouseX, int mouseY, float delta) {
        page.home().drawBackdrop(draw, true);
        page.home().drawChrome(draw, mouseX, mouseY, page.section(), true);
        for (Element child : screen.children()) {
            if (child instanceof ClickableWidget widget && !widget.visible) continue;
            if (child instanceof Drawable drawable) drawable.render(draw, mouseX, mouseY, delta);
        }
    }

    /** A native option slider keeps its actual value and input lifecycle; this changes its pixels only. */
    public static void drawNativeSlider(DrawContext draw, SliderWidget slider, double value, int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        float unit = client.getWindow().getScaledWidth() / 1000f;
        int x = Math.round(slider.getX() / unit), y = Math.round(slider.getY() / unit);
        int width = Math.round(slider.getWidth() / unit), height = Math.round(slider.getHeight() / unit);
        int accent = HudWidgets.accent(AnalyzerClient.CORE);
        boolean selected = slider.isHovered() || slider.isFocused();
        int surface = slider.active ? selected ? 0x8A28262E : 0x681E1D23 : 0x351E1D23;
        try (GuiDraw.FontScope font = GuiDraw.sansFont()) {
            draw.getMatrices().push();
            draw.getMatrices().scale(unit, unit, 1);
            if (!UiShader.surface(draw, x, y, width, height, 8, surface, surface, 0, 0, 0, 0))
                GuiDraw.rounded(draw, x, y, width, height, 8, surface);
            int trackWidth = Math.max(1, width - 16), trackY = y + height - 7;
            GuiDraw.rounded(draw, x + 8, trackY, trackWidth, 3, 1, 0xFF79727F);
            int progress = (int) Math.round(Math.clamp(value, 0, 1) * trackWidth);
            if (progress > 0) GuiDraw.rounded(draw, x + 8, trackY, progress, 3, 1, slider.active ? accent : 0xFF9B97A0);
            float thumbX = x + 8 + (float) Math.clamp(value, 0, 1) * Math.max(0, trackWidth - 6);
            if (!UiShader.surface(draw, thumbX, trackY - 2, 6, 7, 3, 0xFFF8F7FA, 0xFFF8F7FA, 0, 0, 0, 0))
                GuiDraw.rounded(draw, Math.round(thumbX), trackY - 2, 6, 7, 3, 0xFFF8F7FA);
            String text = GuiDraw.truncate(client.textRenderer, slider.getMessage().getString(), width - 14, .91f);
            GuiDraw.label(draw, client.textRenderer, text, x + (width - GuiDraw.width(client.textRenderer, text, .91f)) / 2,
                    y + (height - 12) / 2f - 2, .91f, slider.active ? Palette.TEXT : 0xFF9B97A0);
            if (slider.isFocused()) GuiDraw.roundedBorder(draw, x, y, width, height, 8, Palette.alpha(accent, 170));
            draw.getMatrices().pop();
        }
    }

    private static final class Worlds extends SelectWorldScreen implements HomePage {
        private final HomeScreen home;
        private final List<ButtonWidget> navigation = new ArrayList<>();
        private Map<String, ClickableWidget> controls = Map.of();

        private Worlds(HomeScreen home) { super(home); this.home = home; }
        public HomeScreen home() { return home; }
        public String section() { return "Singleplayer"; }
        public List<ButtonWidget> navigationButtons() { return List.copyOf(navigation); }
        public Map<String, ClickableWidget> controls() { return Map.copyOf(controls); }

        @Override protected void init() {
            super.init();
            controls = nativeControls(this);
            int[] pane = home.pageBounds();
            int x = pane[0] + 12, right = pane[0] + pane[2] - 12;
            int available = Math.max(40, right - x);
            searchBox.setDimensionsAndPosition(available, 20, x, pane[1] + 36);
            controls.put("world-search", searchBox);
            place(controls, "world-create", right - 88, pane[1] + 10, 88, 20);
            for (Element child : children()) if (child instanceof WorldListWidget list) {
                list.setDimensionsAndPosition(available, Math.max(36, pane[3] - 126), x, pane[1] + 62);
                list.refreshScroll();
                controls.put("world-list", list);
            }
            int small = Math.max(1, (available - 12) / 3), firstRow = pane[1] + pane[3] - 52;
            place(controls, "world-edit", x, firstRow, small, 20);
            place(controls, "world-delete", x + small + 6, firstRow, small, 20);
            place(controls, "world-recreate", x + (small + 6) * 2, firstRow, available - (small + 6) * 2, 20);
            int backWidth = Math.min(74, Math.max(40, available / 3));
            place(controls, "back", x, firstRow + 24, backWidth, 20);
            place(controls, "world-play", x + backWidth + 6, firstRow + 24, available - backWidth - 6, 20);
            navigation.clear();
            for (ButtonWidget button : home.createNavigation(this, section())) navigation.add(addDrawableChild(button));
            navigationControls(controls, navigation);
        }

        @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
            HomePages.render(this, this, draw, mouseX, mouseY, delta);
        }

        @Override public void tick() { if (!fallbackAfterResize(this, this)) super.tick(); }
    }

    private static final class Servers extends MultiplayerScreen implements HomePage {
        private final HomeScreen home;
        private final List<ButtonWidget> navigation = new ArrayList<>();
        private Map<String, ClickableWidget> controls = Map.of();

        private Servers(HomeScreen home) { super(home); this.home = home; }
        public HomeScreen home() { return home; }
        public String section() { return "Multiplayer"; }
        public List<ButtonWidget> navigationButtons() { return List.copyOf(navigation); }
        public Map<String, ClickableWidget> controls() { return Map.copyOf(controls); }

        @Override protected void init() {
            super.init();
            controls = nativeControls(this);
            // Vanilla's refresh callback creates a plain MultiplayerScreen. The equivalent
            // constructor here keeps this home page while retaining native loading and cleanup.
            ClickableWidget oldRefresh = controls.get("server-refresh");
            if (oldRefresh instanceof ButtonWidget oldButton) {
                remove(oldButton);
                ButtonWidget refresh = addDrawableChild(ButtonWidget.builder(oldButton.getMessage(),
                        button -> client.setScreen(serverPage(home))).build());
                refresh.setTooltip(oldButton.getTooltip());
                controls.put("server-refresh", refresh);
                controls.put("selectServer.refresh", refresh);
            }
            int[] pane = home.pageBounds();
            int x = pane[0] + 12, available = Math.max(40, pane[2] - 24);
            int small = Math.max(1, (available - 12) / 3), top = pane[1] + 36;
            place(controls, "server-add", x, top, small, 20);
            place(controls, "server-direct", x + small + 6, top, small, 20);
            place(controls, "server-refresh", x + (small + 6) * 2, top, available - (small + 6) * 2, 20);
            serverListWidget.setDimensionsAndPosition(available, Math.max(36, pane[3] - 100), x, pane[1] + 62);
            serverListWidget.refreshScroll();
            controls.put("server-list", serverListWidget);
            int footer = pane[1] + pane[3] - 28, quarter = Math.max(1, (available - 18) / 4);
            place(controls, "back", x, footer, quarter, 20);
            place(controls, "server-edit", x + quarter + 6, footer, quarter, 20);
            place(controls, "server-delete", x + (quarter + 6) * 2, footer, quarter, 20);
            place(controls, "server-join", x + (quarter + 6) * 3, footer, available - (quarter + 6) * 3, 20);
            navigation.clear();
            for (ButtonWidget button : home.createNavigation(this, section())) navigation.add(addDrawableChild(button));
            navigationControls(controls, navigation);
        }

        @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (key == GLFW.GLFW_KEY_F5) { client.setScreen(serverPage(home)); return true; }
            return super.keyPressed(key, scanCode, modifiers);
        }

        @Override public void tick() { if (!fallbackAfterResize(this, this)) super.tick(); }

        @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
            HomePages.render(this, this, draw, mouseX, mouseY, delta);
        }
    }

    private static final class Options extends OptionsScreen implements HomePage {
        private final HomeScreen home;
        private final List<ButtonWidget> navigation = new ArrayList<>();
        private Map<String, ClickableWidget> controls = Map.of();

        private Options(HomeScreen home) { super(home, MinecraftClient.getInstance().options); this.home = home; }
        public HomeScreen home() { return home; }
        public String section() { return "Options"; }
        public List<ButtonWidget> navigationButtons() { return List.copyOf(navigation); }
        public Map<String, ClickableWidget> controls() { return Map.copyOf(controls); }

        @Override protected void init() {
            navigation.clear();
            super.init();
            controls = nativeControls(this);
            for (Element child : children()) if (child instanceof SliderWidget slider) {
                controls.put("options-fov", slider);
                break;
            }
            positionNativeWidgets();
            for (ButtonWidget button : home.createNavigation(this, section())) navigation.add(addDrawableChild(button));
            navigationControls(controls, navigation);
        }

        @Override protected void refreshWidgetPositions() {
            super.refreshWidgetPositions();
            positionNativeWidgets();
        }

        private void positionNativeWidgets() {
            int[] pane = home.pageBounds();
            int x = pane[0] + 12, available = Math.max(40, pane[2] - 24), column = Math.max(1, (available - 6) / 2);
            List<ClickableWidget> top = new ArrayList<>();
            List<ButtonWidget> body = new ArrayList<>();
            ButtonWidget done = null;
            for (Element child : children()) if (child instanceof ClickableWidget widget && !navigation.contains(widget)) {
                if (widget instanceof TextWidget) { widget.visible = false; continue; }
                if (widget instanceof ButtonWidget button) {
                    String key = key(button);
                    if (key.equals("gui.done")) done = button;
                    else if (OPTIONS_ORDER.contains(key)) body.add(button);
                    else if (key.equals("options.online")) top.add(button);
                    else body.add(button);
                } else top.add(widget);
            }
            for (int index = 0; index < top.size(); index++) {
                ClickableWidget widget = top.get(index);
                widget.setDimensionsAndPosition(column, 20, x + (column + 6) * (index % 2), pane[1] + 35 + 24 * (index / 2));
            }
            body.sort((a, b) -> Integer.compare(optionOrder(key(a)), optionOrder(key(b))));
            int first = pane[1] + 62, footer = pane[1] + pane[3] - 28;
            int rows = Math.max(1, (body.size() + 1) / 2), step = Math.max(10, Math.min(28, (footer - 7 - first) / rows));
            int rowHeight = step >= 16 ? Math.min(22, step - 3) : Math.max(8, step - 2);
            for (int index = 0; index < body.size(); index++) body.get(index).setDimensionsAndPosition(column,
                    rowHeight, x + (column + 6) * (index % 2), first + step * (index / 2));
            if (done != null) done.setDimensionsAndPosition(Math.min(100, available), 20, x + available - Math.min(100, available), footer);
        }

        private static int optionOrder(String key) {
            int index = OPTIONS_ORDER.indexOf(key);
            return index < 0 ? OPTIONS_ORDER.size() : index;
        }

        @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
            HomePages.render(this, this, draw, mouseX, mouseY, delta);
        }

        @Override public void tick() { if (!fallbackAfterResize(this, this)) super.tick(); }
    }

    /** The original warning and checkbox, with its accepted transition returning to the styled native list. */
    private static final class MultiplayerWarning extends MultiplayerWarningScreen {
        private final HomeScreen home;
        private MultiplayerWarning(HomeScreen home) { super(home); this.home = home; }
        @Override protected LayoutWidget getLayout() {
            DirectionalLayoutWidget buttons = DirectionalLayoutWidget.horizontal().spacing(8);
            buttons.add(ButtonWidget.builder(ScreenTexts.PROCEED, button -> {
                if (checkbox.isChecked()) { client.options.skipMultiplayerWarning = true; client.options.write(); }
                client.setScreen(serverPage(home));
            }).build());
            buttons.add(ButtonWidget.builder(ScreenTexts.BACK, button -> close()).build());
            return buttons;
        }
    }
}
