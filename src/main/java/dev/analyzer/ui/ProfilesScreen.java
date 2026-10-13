package dev.analyzer.ui;

import dev.analyzer.core.AnalyzerCore;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** Saved configurations use the same shader/Lato shell as Studio, without opening another layout. */
public final class ProfilesScreen extends CatalogScreen {
    private static final Area LIST = new Area(38, 153, 586, 397);
    private final Screen parent;
    private List<String> profiles = List.of();
    private String profileName = "", selectedProfile = "";
    private boolean nameFocused, nameSelectAll;
    private double scroll;

    public ProfilesScreen(AnalyzerCore core, Screen parent) {
        super(core, "Profiles");this.parent = parent;withParent(parent);refresh();
    }
    @Override protected String layout() { return "Studio"; }
    @Override protected boolean shaderStyle() { return true; }
    @Override protected float canvasScale() { return StudioAuxiliary.canvasScale(client, W, H, .72f, .84f); }
    @Override protected void backdrop(DrawContext draw) { StudioAuxiliary.backdrop(draw, core, width, height); }
    @Override protected void header(DrawContext draw) {
        GuiDraw.heading(draw, textRenderer, "Profiles", 43, 31, 2, StudioAuxiliary.TEXT);
        label(draw, "Save your current settings or restore a saved setup", 44, 66, .96f, StudioAuxiliary.MUTED);
        button(draw, "profiles-back", "Back to modules", 787, 31, 169, 31, false, this::close);
    }
    @Override protected void button(DrawContext draw, String id, String value, int x, int y, int w, int h, boolean selected, Runnable action) {
        Area area = new Area(x, y, w, h);
        StudioAuxiliary.button(draw, core, textRenderer, x, y, w, h, value, true, selected, area.contains(mx, my));
        control(id, area, 0, action);
    }
    @Override protected void body(DrawContext draw) {
        StudioAuxiliary.field(draw, core, 43, 104, 380, 33, nameFocused);
        label(draw, GuiDraw.truncate(textRenderer, profileName.isEmpty() ? "Profile name" : profileName, 349, 1), 57, 115, 1,
                profileName.isEmpty() ? StudioAuxiliary.MUTED : StudioAuxiliary.TEXT);
        control("profile-name", new Area(43, 104, 380, 33), 0, () -> {nameFocused = true;nameSelectAll = false;});
        button(draw, "save-profile", "Save current setup", 435, 104, 183, 33, true, this::saveProfile);
        diagnostics.put("profile-save", diagnostics.get("save-profile"));
        double max = Math.max(0, profiles.size() * 70 - LIST.h());scroll = Math.clamp(scroll, 0, max);
        draw.enableScissor(LIST.x(), LIST.y(), LIST.right(), LIST.bottom());
        if (profiles.isEmpty()) label(draw, "No saved profiles", 60, 185, 1.2f, StudioAuxiliary.MUTED);
        for (int i = 0; i < profiles.size(); i++) {
            String name = profiles.get(i);int y = LIST.y() + 3 + i * 70 - (int) scroll;
            if (y + 59 < LIST.y() || y >= LIST.bottom()) continue;
            Area card = new Area(43, y, 575, 59);boolean selected = name.equals(selectedProfile);
            GuiDraw.shaderCard(draw, card.x(), card.y(), card.w(), card.h(), 15, accent, glass(), theme(), card.contains(mx, my) ? 1 : 0, selected);
            GuiDraw.heading(draw, textRenderer, GuiDraw.truncate(textRenderer, name, 436, 1.22f), 59, y + 12, 1.22f, selected ? accent : StudioAuxiliary.TEXT);
            label(draw, "Modules, settings and keybinds", 59, y + 36, .86f, StudioAuxiliary.MUTED);
            clippedControl("profile:" + name, card, LIST, 0, () -> {selectedProfile = name;profileName = name;nameFocused = false;});
            Area load = new Area(522, y + 15, 80, 28);
            StudioAuxiliary.button(draw, core, textRenderer, load.x(), load.y(), load.w(), load.h(), "Load", true, false, load.contains(mx, my));
            clippedControl("load-profile:" + name, load, LIST, 0, () -> loadProfile(name));
        }
        draw.disableScissor();
        if (max > 0) {
            int h = Math.max(25, (int) (LIST.h() * LIST.h() / (LIST.h() + max)));
            StudioAuxiliary.surface(draw, LIST.right() - 3, LIST.y() + (int) ((LIST.h() - h) * scroll / max), 3, h, 1.5f, accent, accent, 0, 0, 0, 0);
        }
        GuiDraw.shaderCard(draw, 643, 105, 314, 445, 18, accent, glass(), theme(), 0, false);
        GuiDraw.heading(draw, textRenderer, "Saved setup", 664, 125, 1.5f, StudioAuxiliary.TEXT);
        label(draw, core.modules().stream().filter(m -> core.isEnabled(m.id())).count() + " modules enabled", 664, 157, .98f, StudioAuxiliary.MUTED);
        GuiDraw.heading(draw, textRenderer, GuiDraw.truncate(textRenderer, selectedProfile.isBlank() ? "Choose a profile" : selectedProfile, 270, 1.18f), 664, 211, 1.18f, StudioAuxiliary.TEXT);
        List<String> description = GuiDraw.wrap(textRenderer, "Loading replaces active settings and keybinds. Worlds and your friend list stay intact.", 270, .97f);
        for (int i = 0; i < description.size(); i++) label(draw, description.get(i), 664, 246 + i * 17, .97f, StudioAuxiliary.MUTED);
        if (!selectedProfile.isBlank()) {
            button(draw, "load-selected", "Load selected profile", 664, 339, 270, 35, true, () -> loadProfile(selectedProfile));
            button(draw, "delete-selected", "Delete selected profile", 664, 384, 270, 32, false, this::deleteProfile);
        }
        label(draw, "Profiles are stored on this computer", 664, 504, .87f, StudioAuxiliary.MUTED);
    }
    @Override protected void footer(DrawContext draw) {
        label(draw, GuiDraw.truncate(textRenderer, status.isBlank() ? "Settings save automatically; profiles are saved when you choose Save" : status, 850, .87f),
                43, 570, .87f, failed ? Palette.ERROR : StudioAuxiliary.MUTED);
    }
    private void refresh() {try {profiles = core.profiles();} catch (IOException failure) {report("Could not read profiles: " + failure.getMessage(), true);}}
    private void report(String text, boolean error) {status = text;failed = error;}
    private void saveProfile() {
        if (profileName.isBlank()) {report("Enter a profile name first", true);nameFocused = true;return;}
        try {
            core.saveProfile(profileName.strip());selectedProfile = profileName.strip().toLowerCase(Locale.ROOT).replace(' ', '-');refresh();
            report("Saved " + selectedProfile, false);
        } catch (IOException | IllegalArgumentException failure) {report("Could not save profile: " + failure.getMessage(), true);}
    }
    private void loadProfile(String name) {
        try {core.loadProfile(name);core.save();selectedProfile = name;profileName = name;report("Loaded " + name + "; settings and keybinds restored", false);}
        catch (IOException | IllegalArgumentException failure) {report("Could not load profile: " + failure.getMessage(), true);}
    }
    private void deleteProfile() {
        try {core.deleteProfile(selectedProfile);selectedProfile = "";refresh();report("Profile deleted", false);}
        catch (IOException | IllegalArgumentException failure) {report("Could not delete profile: " + failure.getMessage(), true);}
    }
    @Override public void close() {
        if (parent == null || MenuScreens.matchesLayout(core, parent)) {super.close();return;}
        Screen next = MenuScreens.create(core, parent instanceof CatalogScreen catalog ? catalog.selectedModuleId() : null);
        if (next instanceof CatalogScreen catalog && parent instanceof CatalogScreen previous) {
            catalog.restoreQueryText(previous.queryText());catalog.withParent(previous.parentScreen());
        }
        client.setScreen(next);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {nameFocused = false;return super.mouseClicked(x, y, button);}
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (LIST.contains((x - ox) / scale, (y - oy) / scale)) {scroll = Math.max(0, scroll - vertical * 35);return true;}
        return false;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (nameFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {nameFocused = false;return true;}
            if (key == GLFW.GLFW_KEY_TAB) {nameFocused = false;return super.keyPressed(key, scan, modifiers);}
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_A) {nameSelectAll = true;return true;}
            if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                profileName = nameSelectAll || profileName.isEmpty() ? "" : profileName.substring(0, profileName.offsetByCodePoints(profileName.length(), -1));nameSelectAll = false;return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {saveProfile();nameFocused = false;return true;}
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_V) {append(client.keyboard.getClipboard().replaceAll("[\\p{Cntrl}]", ""));return true;}
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean charTyped(char character, int modifiers) {if (nameFocused && !Character.isISOControl(character)) {append(String.valueOf(character));return true;}return false;}
    private void append(String text) {profileName = (nameSelectAll ? "" : profileName) + text;if (profileName.length() > 48) profileName = profileName.substring(0, 48);nameSelectAll = false;}
}
