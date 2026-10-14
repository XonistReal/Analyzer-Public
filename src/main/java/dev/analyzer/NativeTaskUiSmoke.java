package dev.analyzer;

import dev.analyzer.automation.AutomationEngine;
import dev.analyzer.automation.AutomationInputDiagnostics;
import dev.analyzer.automation.TaskPlanner;
import dev.analyzer.ui.AutomationScreen;
import dev.analyzer.ui.SchematicBrowserScreen;
import dev.analyzer.effects.UiShader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Actual native screen clicks, local file selection and Survival input handoffs in the isolated dev save. */
final class NativeTaskUiSmoke {
    private enum Stage { SETUP, INPUT_MODE, OPEN_BROWSER, ROOT_LIST, FILE_LIST, SELECTED, REVIEW,
        TASK_MODE, TASK_TEXT, TASK_REVIEW, START, START_HELD, START_RELEASED, MANUAL_INPUT,
        PAUSED, RESUME_SCREEN, RESUME_HELD, RESUME_RELEASED, COMPLETE, STOP_SCREEN,
        STOP_REVIEW, STOP_START, STOP_HELD, STOP_PENDING_SCREEN, STOPPED, FINISHED }
    private static final AutomationEngine ENGINE = AutomationEngine.INSTANCE;
    private static final Vec3d DESTINATION = new Vec3d(54.5, 161, 65.5);
    private static Stage stage = Stage.SETUP;
    private static int tick, entered, assertions, captures;
    private static AutomationScreen console;
    private static SchematicBrowserScreen browser;
    private static Path fixture;
    private static Vec3d heldPosition, pausedPosition, movingPosition;

    private NativeTaskUiSmoke() { }
    static boolean done() { return stage == Stage.FINISHED; }

    static void tick(MinecraftClient mc, int t) {
        if (done()) return;
        if (!FabricLoader.getInstance().isDevelopmentEnvironment() || !Boolean.getBoolean("analyzer.smoke")
                || !Boolean.getBoolean("analyzer.nativeAutomationSmoke")) throw new IllegalStateException("Task UI fixture is development-only");
        tick = t;
        if (t > 1500 || age() > 600) throw new IllegalStateException("Task UI fixture timed out at " + stage + ": " + ENGINE.state() + " / " + ENGINE.detail());
        switch (stage) {
            case SETUP -> {
                ENGINE.stop(); mc.setScreen(null); AutomationInputDiagnostics.developmentOverride(Set.of());
                command(mc, "kill @e[type=!minecraft:player]"); command(mc, "difficulty peaceful");
                command(mc, "fill 40 160 40 100 160 90 minecraft:bedrock"); command(mc, "fill 40 161 40 100 168 90 minecraft:air");
                command(mc, "tp AnalyzerDev 50.5 161 50.5 0 0"); command(mc, "effect give AnalyzerDev minecraft:instant_health 1 4 true");
                command(mc, "effect give AnalyzerDev minecraft:saturation 120 4 true");
                AnalyzerClient.CORE.setValue("expedition", "pause-on-input", true);
                AnalyzerClient.CORE.setValue("expedition", "pause-on-menu", true);
                AnalyzerClient.CORE.setValue("expedition", "auto-site", false);
                try { fixture = writeFixture(mc); }
                catch (Exception error) { throw new IllegalStateException("Cannot create isolated schematic browser fixture", error); }
                console = new AutomationScreen(null, ENGINE, AnalyzerClient.CORE); mc.setScreen(console); enter(Stage.INPUT_MODE);
            }
            case INPUT_MODE -> { if (age() >= 20) { clickConsole("input-mode"); enter(Stage.OPEN_BROWSER); } }
            case OPEN_BROWSER -> {
                if (age() < 6) return;
                clickConsole("browse-schematics");
                require(mc.currentScreen instanceof SchematicBrowserScreen, "Browse opens the shader schematic list instead of requiring a typed path");
                browser = (SchematicBrowserScreen) mc.currentScreen; enter(Stage.ROOT_LIST);
            }
            case ROOT_LIST -> {
                if (age() < 8) return;
                require(browser.currentFolder().equals(mc.runDirectory.toPath().resolve("schematics").toAbsolutePath().normalize()), "The initial list uses the actual game schematics folder");
                require(browser.entryNames().contains("regression"), "The browser displays a real subfolder");
                clickBrowser("entry:regression"); enter(Stage.FILE_LIST);
            }
            case FILE_LIST -> {
                if (age() < 8) return;
                require(browser.currentFolder().equals(fixture.getParent()), "A folder row navigates into the selected schematic folder");
                require(browser.entryNames().contains("fixture.litematic"), "The browser discovers an actual supported schematic file");
                require(!browser.entryNames().contains("ignored.txt"), "Unrelated text files are excluded from the schematic list");
                clickBrowser("entry:fixture.litematic"); enter(Stage.SELECTED);
            }
            case SELECTED -> {
                if (age() < 8) return;
                require(fixture.equals(browser.selectedFile()), "Selecting a file row records its full actual local path");
                require(!ENGINE.isRunning(), "Selecting a schematic never starts a task");
                capture(mc, "schematic-browser"); clickBrowser("use-selected"); enter(Stage.REVIEW);
            }
            case REVIEW -> {
                if (age() < 8) return;
                require(mc.currentScreen == console && console.reviewedPlan() != null && !console.isReviewError(), "Use selected returns to the composer and reviews the real file: " + console.errorText());
                TaskPlanner.Plan plan = console.reviewedPlan();
                require(plan.steps().size() == 3 && plan.steps().getLast().action() == TaskPlanner.Action.BUILD_SCHEMATIC,
                        "The selected file produces both material stages and its actual schematic construction stage");
                require(plan.steps().getFirst().action() == TaskPlanner.Action.SUPPLY_MATERIAL && !ENGINE.isRunning(), "File selection preserves material-first planning and explicit Start");
                capture(mc, "schematic-selected-review"); clickConsole("input-mode"); enter(Stage.TASK_MODE);
            }
            case TASK_MODE -> { if (age() >= 6) { replaceTask("go to 54 161 65"); enter(Stage.TASK_TEXT); } }
            case TASK_TEXT -> { if (age() >= 6) { clickConsole("review"); enter(Stage.TASK_REVIEW); } }
            case TASK_REVIEW -> {
                if (age() < 6) return;
                require(console.reviewedPlan() != null && console.reviewedPlan().steps().size() == 1 && !console.isReviewError(), "Travel is reviewed through ordinary text input and the rendered Review control");
                require(!ENGINE.isRunning(), "Review does not arm movement"); enter(Stage.START);
            }
            case START -> {
                AutomationInputDiagnostics.developmentOverride(Set.of("attack")); heldPosition = mc.player.getPos();
                clickConsole("start"); require(mc.currentScreen == null, "Start closes the composer through its normal callback"); enter(Stage.START_HELD);
            }
            case START_HELD -> {
                require(ENGINE.state().equals("WAITING_INPUT"), "The held Start click remains a visible input handoff, not a safety failure: " + ENGINE.state() + " / " + ENGINE.detail());
                require(!ENGINE.pathStatus().owned() && !movementPressed(mc) && heldPosition.distanceTo(mc.player.getPos()) < .05, "A held Start click cannot own movement or move the player");
                if (age() < 28) return;
                capture(mc, "start-waits-for-release"); AutomationInputDiagnostics.developmentOverride(Set.of()); movingPosition = mc.player.getPos(); enter(Stage.START_RELEASED);
            }
            case START_RELEASED -> {
                require(!ENGINE.state().equals("BLOCKED") && !ENGINE.state().equals("PAUSED"), "Releasing Start permits automatic handoff without a second click: " + ENGINE.detail());
                if (!ENGINE.state().equals("RUNNING") || movingPosition.distanceTo(mc.player.getPos()) < .8) return;
                require(ENGINE.pathStatus().owned() && serverPosition(mc).distanceTo(movingPosition) > .5, "Released Start produces real server-backed native movement");
                AutomationInputDiagnostics.developmentOverride(Set.of("forward")); enter(Stage.MANUAL_INPUT);
            }
            case MANUAL_INPUT -> {
                if (age() < 3) return;
                require(ENGINE.state().equals("PAUSED"), "Fresh manual movement still pauses a running task immediately: " + ENGINE.state() + " / " + ENGINE.detail());
                AutomationInputDiagnostics.developmentOverride(Set.of()); enter(Stage.PAUSED);
            }
            case PAUSED -> {
                require(ENGINE.state().equals("PAUSED"), "Releasing manual movement does not resume an explicitly paused task");
                if (age() == 8) pausedPosition = mc.player.getPos();
                if (age() < 25) return;
                require(pausedPosition.distanceTo(mc.player.getPos()) < .05 && !movementPressed(mc), "Manual pause releases native movement after ordinary momentum settles");
                console = new AutomationScreen(null, ENGINE, AnalyzerClient.CORE); mc.setScreen(console); enter(Stage.RESUME_SCREEN);
            }
            case RESUME_SCREEN -> {
                if (age() < 6) return;
                AutomationInputDiagnostics.developmentOverride(Set.of("attack")); heldPosition = mc.player.getPos();
                clickConsole("pause"); require(mc.currentScreen == null, "Resume uses the actual live-task button and closes the console"); enter(Stage.RESUME_HELD);
            }
            case RESUME_HELD -> {
                require(ENGINE.state().equals("WAITING_INPUT"), "The held Resume click waits instead of blocking or acting");
                require(!movementPressed(mc) && heldPosition.distanceTo(mc.player.getPos()) < .05, "No task movement is pressed during the Resume handoff");
                if (age() < 28) return;
                AutomationInputDiagnostics.developmentOverride(Set.of()); enter(Stage.RESUME_RELEASED);
            }
            case RESUME_RELEASED -> {
                require(!ENGINE.state().equals("BLOCKED") && !ENGINE.state().equals("PAUSED"), "Released Resume continues the existing route: " + ENGINE.detail());
                if (!ENGINE.state().equals("COMPLETED")) return;
                require(mc.player.getPos().distanceTo(DESTINATION) < 1.3 && serverPosition(mc).distanceTo(DESTINATION) < 1.5, "Resumed task completes only at the actual client and server destination");
                require(!movementPressed(mc), "Completed route releases its native movement controls"); capture(mc, "task-start-resume-complete"); enter(Stage.COMPLETE);
            }
            case COMPLETE -> {
                ENGINE.stop(); console = new AutomationScreen(null, ENGINE, AnalyzerClient.CORE); mc.setScreen(console); enter(Stage.STOP_SCREEN);
            }
            case STOP_SCREEN -> { if (age() >= 6) { replaceTask("go to 54 161 80"); enter(Stage.STOP_REVIEW); } }
            case STOP_REVIEW -> { if (age() >= 6) { clickConsole("review"); enter(Stage.STOP_START); } }
            case STOP_START -> {
                if (age() < 6) return;
                require(console.reviewedPlan() != null, "Cancellation fixture has a reviewed route");
                AutomationInputDiagnostics.developmentOverride(Set.of("attack")); heldPosition = mc.player.getPos();
                clickConsole("start"); enter(Stage.STOP_HELD);
            }
            case STOP_HELD -> {
                if (age() < 14) return;
                require(ENGINE.state().equals("WAITING_INPUT") && !movementPressed(mc), "A second held Start is cancellable before ownership begins");
                console = new AutomationScreen(null, ENGINE, AnalyzerClient.CORE); mc.setScreen(console); enter(Stage.STOP_PENDING_SCREEN);
            }
            case STOP_PENDING_SCREEN -> {
                if (age() < 6) return;
                clickConsole("stop"); require(ENGINE.state().equals("STOPPED"), "The rendered Stop button cancels a pending input handoff");
                AutomationInputDiagnostics.developmentOverride(Set.of()); mc.setScreen(null); enter(Stage.STOPPED);
            }
            case STOPPED -> {
                require(ENGINE.state().equals("STOPPED") && !ENGINE.isRunning(), "Releasing a cancelled Start never starts it later");
                require(heldPosition.distanceTo(mc.player.getPos()) < .05 && !movementPressed(mc), "Cancelled handoff leaves position and task controls unchanged");
                if (age() < 25) return;
                ENGINE.stop(); require(!mc.options.attackKey.isPressed() && !mc.options.useKey.isPressed(), "Task UI completion releases attack and use as well as movement");
                require(UiShader.isHealthy() && GL11.glGetError() == GL11.GL_NO_ERROR, "Browser, review and handoff HUD leave healthy native shaders and clean GL state");
                AnalyzerClient.LOGGER.info("ANALYZER_NATIVE_TASK_UI_COMPLETE: {} assertions, {} captures, {} elapsed ticks; selected local schematic review and held-click start/resume/manual pause/cancel passed", assertions, captures, tick);
                enter(Stage.FINISHED);
            }
            case FINISHED -> { }
        }
    }

    private static Path writeFixture(MinecraftClient mc) throws Exception {
        Path folder = mc.runDirectory.toPath().resolve("schematics/regression").toAbsolutePath().normalize(); Files.createDirectories(folder);
        NbtCompound root = new NbtCompound(); root.putInt("Version", 6); root.putInt("MinecraftDataVersion", 4189);
        NbtCompound region = new NbtCompound(), size = new NbtCompound(), position = new NbtCompound();
        size.putInt("x", 2); size.putInt("y", 1); size.putInt("z", 1); region.put("Size", size);
        position.putInt("x", 0); position.putInt("y", 0); position.putInt("z", 0); region.put("Position", position);
        NbtList palette = new NbtList();
        for (String name : new String[]{"minecraft:cobblestone", "minecraft:oak_planks"}) { NbtCompound state = new NbtCompound(); state.putString("Name", name); palette.add(state); }
        region.put("BlockStatePalette", palette); region.putLongArray("BlockStates", new long[]{4});
        NbtCompound regions = new NbtCompound(); regions.put("floor", region); root.put("Regions", regions);
        Path path = folder.resolve("fixture.litematic"); NbtIo.writeCompressed(root, path); Files.writeString(folder.resolve("ignored.txt"), "Not a schematic"); return path;
    }
    private static Vec3d serverPosition(MinecraftClient mc) { return mc.getServer().getPlayerManager().getPlayer(mc.player.getUuid()).getPos(); }
    private static void command(MinecraftClient mc, String command) { mc.getServer().execute(() -> mc.getServer().getCommandManager().executeWithPrefix(mc.getServer().getCommandSource().withSilent(), command)); }
    private static boolean movementPressed(MinecraftClient mc) { return mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed() || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed() || mc.options.sprintKey.isPressed() || mc.options.jumpKey.isPressed() || mc.options.sneakKey.isPressed(); }
    private static void replaceTask(String task) {
        clickConsole("field:0"); console.keyPressed(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL);
        for (char character : task.toCharArray()) console.charTyped(character, 0);
    }
    private static void clickConsole(String id) { double[] p = console.controlPoint(id); require(p != null, "Composer renders " + id); require(console.mouseClicked(p[0], p[1], GLFW.GLFW_MOUSE_BUTTON_LEFT), "Composer accepts normal mouse input for " + id); }
    private static void clickBrowser(String id) { double[] p = browser.controlPoint(id); require(p != null, "Browser renders " + id); require(browser.mouseClicked(p[0], p[1], GLFW.GLFW_MOUSE_BUTTON_LEFT), "Browser accepts normal mouse input for " + id); }
    private static void capture(MinecraftClient mc, String name) { require(UiShader.isHealthy() && GL11.glGetError() == GL11.GL_NO_ERROR, "Healthy native rendering before " + name); ScreenshotRecorder.saveScreenshot(mc.runDirectory, "analyzer-native-ui-" + name + ".png", mc.getFramebuffer(), text -> AnalyzerClient.LOGGER.info("Native task UI capture: {}", text.getString())); captures++; }
    private static int age() { return tick - entered; }
    private static void enter(Stage next) { stage = next; entered = tick; AnalyzerClient.LOGGER.info("Native task UI acceptance: {}", next); }
    private static void require(boolean condition, String detail) { if (!condition) throw new IllegalStateException("Native task UI check failed at " + stage + " / " + tick + ": " + detail); assertions++; }
}
