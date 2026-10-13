package dev.analyzer.ui;

import dev.analyzer.automation.TaskPlanner;
import dev.analyzer.core.AnalyzerCore;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.HashMap;
import java.util.Map;

/** An explicit plan review and live task console, backed by a real automation controller. */
public final class AutomationScreen extends Screen {
    public interface Controller {
        View snapshot();
        void submit(TaskPlanner.Plan plan);
        void pause();
        void resume();
        void stop();
    }
    public record StepView(String label, String status, String detail) {
        public StepView { label = Objects.requireNonNullElse(label, ""); status = Objects.requireNonNullElse(status, "Queued"); detail = Objects.requireNonNullElse(detail, ""); }
    }
    public record View(String title, String state, String detail, int currentStep, List<StepView> steps,
                       boolean running, boolean paused, boolean baritoneAvailable) {
        public View {
            title = Objects.requireNonNullElse(title, "No active task"); state = Objects.requireNonNullElse(state, "Idle");
            detail = Objects.requireNonNullElse(detail, ""); steps = steps == null ? List.of() : List.copyOf(steps);
        }
    }
    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double px, double py) { return px >= x && py >= y && px < x + w && py < y + h; }
    }
    private static final int W = 960, H = 560;
    private static final String[] PRESETS = { "Escape spawn", "Build a base", "Diamond kit", "Enchanted kit", "Expedition", "Mine diamonds" };
    private static final String[] PHRASES = { "escape spawn", "build a base", "get diamond armor", "get fully enchanted diamond armor", "escape spawn, build a base and get fully enchanted diamond armor", "mine 32 diamonds" };
    private final Screen parent;
    private final Controller controller;
    private final AnalyzerCore core;
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, double[]> diagnostics = new HashMap<>();
    private final String[] fields = new String[5];
    private TaskPlanner.Plan reviewed;
    private TaskPlanner.Direction direction = TaskPlanner.Direction.NORTH;
    private String message = "Choose a task, review its steps, then start.";
    private int focus = 0, stepScroll, prerequisiteScroll;
    private boolean selectAll, error, showReviewed, originSeeded;
    private float canvasScale = 1, canvasX, canvasY;
    private double mx, my;
    private int originX, originY, originZ;

    public AutomationScreen(Screen parent, Controller controller, AnalyzerCore core) {
        super(Text.literal("Analyzer Automation"));
        this.parent = parent; this.controller = Objects.requireNonNull(controller); this.core = core;
        fields[0] = "escape spawn"; fields[1] = "0"; fields[2] = "64"; fields[3] = "0"; fields[4] = "10000";
    }
    @Override protected void init() {
        if (client != null && client.player != null && !originSeeded) {
            originX = client.player.getBlockX(); originY = client.player.getBlockY(); originZ = client.player.getBlockZ();
            fields[1] = Integer.toString(originX); fields[2] = Integer.toString(originY); fields[3] = Integer.toString(originZ);
            originSeeded = true;
        }
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { if (client != null) client.setScreen(parent); }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        try (GuiDraw.GlassScope optical = GuiDraw.glassScope(context, theme())) {
        canvasScale = StudioAuxiliary.canvasScale(client, W, H, .84, .88);
        canvasX = (width - W * canvasScale) / 2; canvasY = (height - H * canvasScale) / 2;
        mx = (mouseX - canvasX) / canvasScale; my = (mouseY - canvasY) / canvasScale;
        hits.clear(); diagnostics.clear();
        View view = controller.snapshot();
        int accent = accent();
        StudioAuxiliary.backdrop(context, core, width, height);
        context.getMatrices().push(); context.getMatrices().translate(canvasX, canvasY, 0); context.getMatrices().scale(canvasScale, canvasScale, 1);
        try (var font = GuiDraw.sansFont()) {
        GuiDraw.shaderPanel(context, 0, 0, W, H, 24, accent, glass(), theme());
        GuiDraw.heading(context, textRenderer, "Automation", 23, 18, 1.75f, StudioAuxiliary.TEXT);
        GuiDraw.label(context, textRenderer, "Plan a task and follow its progress.", 24, 45, .95f, StudioAuxiliary.MUTED);
        statusPill(context, 675, 20, view.baritoneAvailable ? "BARITONE CONNECTED" : "BARITONE REQUIRED", view.baritoneAvailable ? accent : Palette.GOLD);
        button(context, "back", 867, 18, 74, 32, "Back", true, false, this::close);
        GuiDraw.shaderCard(context, 8, 75, 414, 439, 18, accent, glass(), theme(), 0, false);
        GuiDraw.shaderCard(context, 438, 75, 506, 439, 18, accent, glass(), theme(), 0, false);
        drawComposer(context, view, accent);
        drawPlan(context, view, accent);
        GuiDraw.label(context, textRenderer, "Esc to return  ·  Enter to review", 22, 533, .90f, StudioAuxiliary.SUBTLE);
        String status = GuiDraw.truncate(textRenderer, message, 462, .9f);
        GuiDraw.label(context, textRenderer, status, 476, 533, .9f, error ? Palette.ERROR : StudioAuxiliary.MUTED);
        }
        context.getMatrices().pop();
        }
    }

    private void drawComposer(DrawContext context, View view, int accent) {
        GuiDraw.heading(context, textRenderer, "New task", 22, 86, 1.3f, StudioAuxiliary.TEXT);
        GuiDraw.label(context, textRenderer, "Type a task or choose a preset.", 22, 111, .95f, StudioAuxiliary.MUTED);
        field(context, 0, 22, 135, 386, 47, "TASK");
        for (int i = 0; i < PRESETS.length; i++) {
            int index = i;
            button(context, "preset:" + i, 22 + i % 3 * 131, 195 + i / 3 * 35, 124, 28, PRESETS[i], !view.running && !view.paused, false, () -> {
                fields[0] = PHRASES[index]; reviewed = null; showReviewed = true; error = false; message = "Review this task to see every stage.";
            });
        }
        GuiDraw.heading(context, textRenderer, "Route", 22, 278, 1.05f, StudioAuxiliary.TEXT);
        GuiDraw.label(context, textRenderer, "Travel / shelter coordinates; current location by default.", 22, 303, .86f, StudioAuxiliary.MUTED);
        field(context, 1, 22, 324, 124, 42, "X"); field(context, 2, 153, 324, 124, 42, "Y"); field(context, 3, 284, 324, 124, 42, "Z");
        field(context, 4, 22, 378, 187, 42, "ESCAPE DISTANCE · BLOCKS");
        button(context, "direction", 220, 378, 188, 42, "Direction: " + direction.name(), !view.running && !view.paused, false, () -> {
            direction = TaskPlanner.Direction.values()[(direction.ordinal() + 1) % TaskPlanner.Direction.values().length]; reviewed = null; showReviewed = true;
        });
        String explanation = view.baritoneAvailable ? "Existing inventory counts toward goals. Vanilla enchanting needs table access, lapis, XP and suitable books for upgrades."
                : "Install Baritone for Fabric 1.21.4 in this Prism instance, then restart Minecraft. Plans can be reviewed now; execution needs Baritone.";
        wrapped(context, explanation, 22, 433, 386, .9f, view.baritoneAvailable ? StudioAuxiliary.SUBTLE : Palette.GOLD, 15, 3);
        button(context, "review", 22, 483, 386, 28, "Review plan", !view.running && !view.paused, true, this::review);
    }

    private void drawPlan(DrawContext context, View view, int accent) {
        boolean live = view.running || view.paused || (!showReviewed && !view.steps.isEmpty());
        String title = live ? view.title : reviewed == null ? "Your plan appears here" : reviewed.title();
        GuiDraw.heading(context, textRenderer, GuiDraw.truncate(textRenderer, title, 346, 1.25f), 449, 85, 1.25f, StudioAuxiliary.TEXT);
        statusPill(context, 807, 84, live ? view.state.toUpperCase(Locale.ROOT) : reviewed == null ? "DRAFT" : "READY TO START", accent);
        String detail = live ? view.detail : reviewed == null ? "Examples: go to 1500 64 -2200, mine 32 diamonds, or escape spawn and build a base." : reviewed.description();
        wrapped(context, detail, 449, 116, 484, .92f, StudioAuxiliary.MUTED, 16, 2);
        List<StepView> steps = live ? view.steps : reviewed == null ? List.of() : reviewed.steps().stream().map(step -> new StepView(step.label(), "Queued", "")).toList();
        int visible = 6, maxScroll = Math.max(0, steps.size() - visible);
        stepScroll = Math.clamp(stepScroll, 0, maxScroll);
        clip(context, 447, 158, 489, 238);
        if (steps.isEmpty()) {
            GuiDraw.shaderCard(context, 466, 195, 449, 154, 17, accent, false, theme(), 0, false);
            GuiDraw.heading(context, textRenderer, "Review a task", 488, 230, 1.35f, StudioAuxiliary.TEXT);
            wrapped(context, "Its stages and requirements appear here before you start.", 488, 263, 400, 1.0f, StudioAuxiliary.MUTED, 18, 2);
        }
        for (int row = 0; row < visible && row + stepScroll < steps.size(); row++) {
            int index = row + stepScroll, y = 159 + row * 39;
            StepView step = steps.get(index);
            boolean active = live && index == view.currentStep;
            boolean done = step.status.toLowerCase(Locale.ROOT).contains("done") || step.status.toLowerCase(Locale.ROOT).contains("complete");
            int color = done ? accent : active ? Palette.GOLD : StudioAuxiliary.SUBTLE;
            GuiDraw.shaderCard(context, 449, y, 483, 35, 9, accent, glass(), theme(), 0, active);
            StudioAuxiliary.surface(context, 459, y + 8, 20, 20, 10, Palette.alpha(color, 35), Palette.alpha(color, 25), 0, 0, 0, 0);
            GuiDraw.label(context, textRenderer, Integer.toString(index + 1), 465, y + 13, .84f, color);
            GuiDraw.label(context, textRenderer, GuiDraw.truncate(textRenderer, step.label, 348, .94f), 489, y + 6, .94f, StudioAuxiliary.TEXT);
            String stageDetail = step.detail.isEmpty() ? step.status : step.detail;
            GuiDraw.label(context, textRenderer, GuiDraw.truncate(textRenderer, stageDetail, 402, .81f), 489, y + 21, .81f, color);
        }
        context.disableScissor();
        if (maxScroll > 0) {
            button(context, "steps-up", 893, 398, 19, 20, "↑", stepScroll > 0, false, () -> stepScroll--);
            button(context, "steps-down", 915, 398, 19, 20, "↓", stepScroll < maxScroll, false, () -> stepScroll++);
            GuiDraw.label(context, textRenderer, "Scroll to inspect all " + steps.size() + " stages", 451, 404, .84f, StudioAuxiliary.SUBTLE);
        }
        List<String> prerequisites = reviewed == null || live ? List.of("Pause and Stop cancel active pathing and release task-owned inputs.",
                "Enchanting verifies real item data. Missing XP, lapis or upgrade books are reported as unmet requirements.") : reviewed.prerequisites();
        prerequisiteScroll = Math.clamp(prerequisiteScroll, 0, Math.max(0, prerequisites.size() - 1));
        GuiDraw.label(context, textRenderer, "REQUIREMENTS  " + (prerequisiteScroll + 1) + "/" + prerequisites.size(), 451, 426, .82f, Palette.GOLD);
        wrapped(context, prerequisites.get(prerequisiteScroll), 451, 444, 450, .88f, StudioAuxiliary.MUTED, 14, 2);
        button(context, "requirements-next", 914, 438, 19, 27, "›", prerequisites.size() > 1, false, () -> prerequisiteScroll = (prerequisiteScroll + 1) % prerequisites.size());
        if (view.running || view.paused) {
            button(context, "pause", 449, 483, 230, 28, view.paused && !view.baritoneAvailable ? "Resume needs Baritone" : view.paused ? "Resume task" : "Pause task", !view.paused || view.baritoneAvailable, true, () -> {
                if (view.paused) { controller.resume(); if (client != null) client.setScreen(null); }
                else controller.pause();
            });
            button(context, "stop", 694, 483, 238, 28, "Stop task", true, false, () -> { controller.stop(); message = "Task stopped. Review a plan before starting again."; });
        } else {
            button(context, "start", 449, 483, 483, 28, "Start reviewed plan", reviewed != null && view.baritoneAvailable, true, () -> {
                try { controller.submit(reviewed); showReviewed = false; error = false; message = "Task started."; if (client != null) client.setScreen(null); }
                catch (RuntimeException e) { error = true; message = Objects.requireNonNullElse(e.getMessage(), "Could not start this task."); }
            });
        }
    }

    private void review() {
        View view = controller.snapshot();
        if (view.running || view.paused) { error = true; message = "Stop the current task before reviewing a replacement."; return; }
        try {
            if (client != null && client.player != null) {
                originX = client.player.getBlockX(); originY = client.player.getBlockY(); originZ = client.player.getBlockZ();
            }
            reviewed = TaskPlanner.plan(fields[0], new TaskPlanner.Options(originX, originY, originZ,
                    Integer.parseInt(fields[1].trim()), Integer.parseInt(fields[2].trim()), Integer.parseInt(fields[3].trim()),
                    Integer.parseInt(fields[4].trim()), direction));
            showReviewed = true; error = false; message = "Reviewed " + reviewed.steps().size() + " stages. Starting is a separate action."; stepScroll = 0; prerequisiteScroll = 0;
        } catch (RuntimeException e) { reviewed = null; error = true; message = e instanceof NumberFormatException ? "Coordinates and distance must be whole numbers." : Objects.requireNonNullElse(e.getMessage(), "This task is not supported."); }
    }
    private void field(DrawContext context, int index, int x, int y, int w, int h, String label) {
        StudioAuxiliary.field(context, core, x, y, w, h, focus == index);
        GuiDraw.label(context, textRenderer, label, x + 9, y + 6, .8f, StudioAuxiliary.SUBTLE);
        String displayed = fields[index];
        while (!displayed.isEmpty() && GuiDraw.width(textRenderer, displayed, .98f) > w - 24) displayed = displayed.substring(displayed.offsetByCodePoints(0, 1));
        if (selectAll && focus == index) context.fill(x + 8, y + 19, x + w - 8, y + h - 6, Palette.alpha(accent(), 38));
        GuiDraw.label(context, textRenderer, displayed, x + 9, y + 22, .98f, StudioAuxiliary.TEXT);
        if (focus == index && System.currentTimeMillis() / 500 % 2 == 0) {
            int caret = x + 10 + (int) GuiDraw.width(textRenderer, displayed, .98f); context.fill(caret, y + 21, caret + 1, y + h - 7, accent());
        }
        hits.add(new Hit(x, y, w, h, () -> { focus = index; selectAll = false; }));
        diagnostic("field:" + index, x, y, w, h);
    }
    private void button(DrawContext context, String id, int x, int y, int w, int h, String label, boolean enabled, boolean primary, Runnable action) {
        boolean hover = enabled && mx >= x && my >= y && mx < x + w && my < y + h;
        StudioAuxiliary.button(context, core, textRenderer, x, y, w, h, label, enabled, primary, hover);
        if (enabled) hits.add(new Hit(x, y, w, h, action));
        diagnostic(id, x, y, w, h);
    }
    private void statusPill(DrawContext context, int x, int y, String label, int color) {
        StudioAuxiliary.pill(context, x, y, label, color, textRenderer);
    }
    private void wrapped(DrawContext context, String text, int x, int y, int w, float scale, int color, int pitch, int maxLines) {
        List<String> lines = GuiDraw.wrap(textRenderer, text, w, scale);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            String line = i == maxLines - 1 && lines.size() > maxLines ? GuiDraw.truncate(textRenderer, lines.get(i) + " …", w, scale) : lines.get(i);
            GuiDraw.label(context, textRenderer, line, x, y + i * pitch, scale, color);
        }
    }
    private void clip(DrawContext context, int x, int y, int w, int h) {
        // DrawContext applies the current canvas matrix to its scissor rectangle.
        context.enableScissor(x, y, x + w, y + h);
    }
    private int accent() {
        return StudioAuxiliary.accent(core);
    }
    private boolean glass() { return StudioAuxiliary.glass(core); }
    private String theme() { return StudioAuxiliary.theme(core); }
    private void diagnostic(String id, int x, int y, int w, int h) { diagnostics.put(id, new double[]{canvasX + (x + w / 2.0) * canvasScale, canvasY + (y + h / 2.0) * canvasScale}); }
    public double[] controlPoint(String id) { return diagnostics.get(id); }
    public TaskPlanner.Plan reviewedPlan() { return reviewed; }
    public boolean isReviewError() { return error; }
    public String errorText() { return error ? message : ""; }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        double px = (x - canvasX) / canvasScale, py = (y - canvasY) / canvasScale;
        for (Hit hit : List.copyOf(hits)) if (hit.contains(px, py)) { hit.action.run(); return true; }
        focus = -1; selectAll = false; return true;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        double px = (x - canvasX) / canvasScale, py = (y - canvasY) / canvasScale;
        if (px > 447 && py >= 158 && py <= 416) { stepScroll += vertical < 0 ? 1 : -1; return true; }
        if (px > 447 && py >= 426 && py < 474) { prerequisiteScroll += vertical < 0 ? 1 : -1; return true; }
        return false;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 || hasShiftDown();
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || hasControlDown();
        if (key == GLFW.GLFW_KEY_TAB) { focus = focus < 0 ? shift ? 4 : 0 : Math.floorMod(focus + (shift ? -1 : 1), 5); selectAll = true; return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { review(); return true; }
        if (focus < 0) return super.keyPressed(key, scan, modifiers);
        if (control && key == GLFW.GLFW_KEY_A) { selectAll = true; return true; }
        if (control && key == GLFW.GLFW_KEY_C) { if (client != null) client.keyboard.setClipboard(fields[focus]); return true; }
        if (control && key == GLFW.GLFW_KEY_V) { if (client != null) append(client.keyboard.getClipboard().replace('\n', ' ').replace('\r', ' ')); return true; }
        if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
            if (selectAll) fields[focus] = "";
            else if (!fields[focus].isEmpty()) fields[focus] = fields[focus].substring(0, fields[focus].offsetByCodePoints(fields[focus].length(), -1));
            selectAll = false; reviewed = null; showReviewed = true; return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean charTyped(char character, int modifiers) {
        if (focus < 0 || Character.isISOControl(character)) return false;
        append(String.valueOf(character)); return true;
    }
    private void append(String text) {
        if (focus < 0) return;
        String base = selectAll ? "" : fields[focus], next = base + text;
        int limit = focus == 0 ? 512 : 12;
        if (next.length() <= limit) { fields[focus] = next; reviewed = null; showReviewed = true; }
        selectAll = false;
    }
}
