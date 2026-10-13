package dev.analyzer;

import com.google.gson.GsonBuilder;
import dev.analyzer.automation.AutomationEngine;
import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.core.Category;
import dev.analyzer.core.CosmeticModules;
import dev.analyzer.core.GameplayModules;
import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.core.Setting;
import dev.analyzer.core.UtilityModules;
import dev.analyzer.effects.GpuEffects;
import dev.analyzer.effects.UiShader;
import dev.analyzer.gameplay.GameplayCombatSmoke;
import dev.analyzer.gameplay.GameplayEngine;
import dev.analyzer.gameplay.UtilityEngine;
import dev.analyzer.render.AnalyzerRenderer;
import dev.analyzer.render.CosmeticRenderer;
import dev.analyzer.render.VisualEffects;
import dev.analyzer.ui.StudioScreen;
import dev.analyzer.ui.HudWidgets;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Opt-in, bounded catalog sweep. Settings acceptance is distinct from verified game actions. */
final class AllModulesSmoke {
    private static final Set<String> GAMEPLAY = ids(GameplayModules.definitions());
    private static final Set<String> UTILITY = ids(UtilityModules.definitions());
    private static final Set<String> COSMETICS = ids(CosmeticModules.definitions());
    private static final Set<String> THIRD_PERSON = Set.of("cape", "pets", "emotes", "walk-style", "spinbot", "player-aura", "contrails");
    private static final List<Map<String, Object>> modules = new ArrayList<>();
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static int stage, entered, tick, moduleIndex, variant, assertions, settingsWrites, rejectedWrites, configRoundtrips, profileRoundtrips, screenshots;
    private static long actionsBefore, operationsBefore, cosmeticBefore, uiBefore, menuActions;
    private static int eventsBefore;
    private static double originalStep;
    private static Perspective originalPerspective;
    private static StudioScreen menu;
    private static Map<String, Object> currentVariant;
    private static List<HudWidgets.WidgetBounds> lastHud = List.of();
    private static long started;
    private static boolean complete;

    private AllModulesSmoke() {}
    private static Set<String> ids(List<ModuleDefinition> definitions) { return definitions.stream().map(ModuleDefinition::id).collect(Collectors.toSet()); }

    static void tick(MinecraftClient mc, int t) {
        try { tickOwned(mc, t); }
        catch (RuntimeException failure) {
            if (originalPerspective != null) mc.options.setPerspective(originalPerspective);
            throw failure;
        }
    }

    private static void tickOwned(MinecraftClient mc, int t) {
        if (complete) return;
        tick = t;
        if (t > 3000) throw new IllegalStateException("All-module sweep timed out at stage " + stage + ", module " + moduleIndex + ", variant " + variant);
        var core = AnalyzerClient.CORE;
        int age = t - entered;
        switch (stage) {
            case 0 -> {
                if (mc.player.isDead()) { mc.player.requestRespawn(); mc.setScreen(null); return; }
                started = System.nanoTime(); originalPerspective = mc.options.getPerspective();
                originalStep = mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT).getBaseValue();
                AutomationEngine.INSTANCE.stop(); core.resetAll(); allOff(core); mc.setScreen(null);
                HudRenderCallback.EVENT.register((draw, counter) -> {
                    if (mc.world != null && mc.currentScreen == null) lastHud = HudWidgets.bounds(core, draw, false);
                });
                matrix(mc);
                report.put("moduleCount", core.modules().size());
                report.put("exposedSettingCount", core.modules().stream().mapToInt(m -> m.settings().size()).sum());
                report.put("scope", "Isolated integrated-server Analyzer-Smoke world. Every module gets default, low/false/first-choice and high/true/last-choice settings; two live ticks, two actual Studio menu ticks and one disable-cleanup tick per variant. Coord Snapper remains behind the manual-menu gate for all ticks to preserve the system clipboard.");
                report.put("limits", List.of("Config admission covers every choice, both booleans and numeric endpoints/midpoint; live execution covers three combinations per module, not every Cartesian combination.", "Creative mode and an empty inventory deliberately gate equipment-, container-, keypress-, death- and task-dependent actions. A nonzero counter records an actual local action; a successful enable/render test alone does not prove that module's behavior.", "Coord Snapper's open-menu pause gate is verified instead of copying. The fixture never reads or replaces the system clipboard.", "Automation's module toggle is tested without an explicit Start; it must remain idle. The separate focused combat fixture verifies real hostile filters, bounded camera rotation, normal reach/charge gates and server-confirmed melee, plus loaded-terrain HVH estimates and previews.", "No public multiplayer server, launcher world, saved user profile or user configuration is used. No disconnect/relog modules exist in this 123-module catalog."));
                command(mc, "gamemode creative AnalyzerDev"); command(mc, "clear AnalyzerDev");
                command(mc, "tp AnalyzerDev 11 161 21 180 12");
                next();
            }
            case 1 -> { if (age >= 6 && mc.player.isOnGround() && mc.player.getAbilities().creativeMode) {
                require(core.modules().size() == 123, "All 123 supported modules are present");
                next();
            }}
            case 2 -> sweep(mc, age);
            case 3 -> {
                if (age == 1) {
                    core.resetAll(); allOff(core); mc.setScreen(null); mc.options.setPerspective(Perspective.FIRST_PERSON);
                    command(mc, "gamemode creative AnalyzerDev"); command(mc, "fill 10 160 10 22 160 22 minecraft:polished_deepslate");
                    command(mc, "fill 10 161 10 22 169 22 minecraft:air"); command(mc, "tp AnalyzerDev 15.5 161 15.5 0 0");
                }
                if (age >= 6 && mc.player.isOnGround() && mc.player.getPos().squaredDistanceTo(15.5, 161, 15.5) < 1) {
                    mc.player.fallDistance = 0; mc.player.setVelocity(Vec3d.ZERO);
                    mc.getServer().execute(() -> {
                        var player = mc.getServer().getPlayerManager().getPlayer(mc.player.getUuid());
                        if (player != null) { player.fallDistance = 0; player.setVelocity(Vec3d.ZERO); }
                    });
                    command(mc, "gamemode survival AnalyzerDev"); next();
                } else if (age > 60) throw new IllegalStateException("Supported ground did not synchronize before Survival action checks");
            }
            case 4 -> {
                if (age == 3) {
                    require(mc.player.isAlive() && mc.player.isOnGround() && !mc.player.getAbilities().creativeMode, "Focused actions start alive on actual supported Survival ground");
                    core.setValue("step", "legit", false); core.setValue("step", "mode", "Instant"); core.setValue("step", "height", 1.25D); core.setEnabled("step", true);
                    core.setValue("auto-walk", "direction", "Forward"); core.setValue("auto-walk", "jump", false); core.setEnabled("auto-walk", true);
                }
                if (age >= 7) {
                    near(mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT).getBaseValue(), 1.25, "Real Step attribute is applied");
                    require(mc.options.forwardKey.isPressed() && GameplayEngine.actions("auto-walk") > 0, "Real Auto Walk input is owned while enabled");
                    core.setEnabled("step", false); core.setEnabled("auto-walk", false); next();
                }
            }
            case 5 -> { if (age >= 2) {
                near(mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT).getBaseValue(), originalStep, "Disabling Step restores the original attribute");
                require(!mc.options.forwardKey.isPressed(), "Disabling Auto Walk releases its actual input");
                next();
            }}
            case 6 -> { if (GameplayCombatSmoke.tick(mc, age)) finish(mc); }
            default -> throw new IllegalStateException("Unexpected sweep stage " + stage);
        }
    }

    private static void sweep(MinecraftClient mc, int age) {
        var core = AnalyzerClient.CORE;
        ModuleDefinition module = core.modules().get(moduleIndex);
        String id = module.id();
        if (age == 1) {
            core.resetAll(); allOff(core); mc.setScreen(null);
            if (module.category() == Category.HUD && !id.equals("hud")) core.setEnabled("hud", true);
            lastHud = List.of();
            // Teleports only the isolated fixture and keeps consecutive motion modules on loaded ground.
            command(mc, "tp AnalyzerDev 11 161 21 180 12"); mc.player.setVelocity(Vec3d.ZERO);
            mc.options.setPerspective(THIRD_PERSON.contains(id) ? Perspective.THIRD_PERSON_BACK : Perspective.FIRST_PERSON);
            for (Setting<?> setting : module.settings()) core.setValue(id, setting.id(), liveValue(setting, variant));
            if (variant == 0) {
                var row = new LinkedHashMap<String, Object>(); row.put("id", id); row.put("name", module.name()); row.put("category", module.category().label());
                row.put("settingCount", module.settings().size()); row.put("variants", new ArrayList<Map<String, Object>>()); modules.add(row);
            }
            currentVariant = new LinkedHashMap<>(); currentVariant.put("variant", List.of("default", "low-false-first", "high-true-last").get(variant));
            currentVariant.put("settings", core.values(id)); currentVariant.put("perspective", mc.options.getPerspective().name());
            actionsBefore = GameplayEngine.actions(id); operationsBefore = UtilityEngine.operations(id);
            cosmeticBefore = CosmeticRenderer.frames(id); eventsBefore = VisualEffects.emitted(id);
            core.setEnabled(id, true);
            require(core.isEnabled(id), "Enable " + id + " / " + variant);
            if (id.equals("coord-snapper")) {
                currentVariant.put("restraint", "Open-menu pause gate; system clipboard is never read or replaced");
                mc.setScreen(new StudioScreen(core).inspect(id));
            }
        }
        if (age == 3) {
            require(mc.player != null && mc.world != null && mc.getServer() != null && mc.player.isAlive(), "Live isolated world survives " + id + " / " + variant);
            String status = GAMEPLAY.contains(id) ? GameplayEngine.status(id) : UTILITY.contains(id) ? UtilityEngine.status(id)
                    : COSMETICS.contains(id) ? CosmeticRenderer.status(id) : VisualEffects.status(id);
            require(!status.startsWith("Paused:"), "No guarded gameplay failure in " + id + " / " + variant);
            require(GL11.glGetError() == GL11.GL_NO_ERROR && GpuEffects.isHealthy() && UiShader.isHealthy(), "Healthy live rendering for " + id + " / " + variant);
            currentVariant.put("liveStatus", status); currentVariant.put("enabledAfterLiveTicks", core.isEnabled(id));
            currentVariant.put("acceptedGameplayActions", GameplayEngine.actions(id) - actionsBefore);
            currentVariant.put("utilityOperations", UtilityEngine.operations(id) - operationsBefore);
            currentVariant.put("cosmeticRenderFrames", CosmeticRenderer.frames(id) - cosmeticBefore);
            currentVariant.put("visualEvents", VisualEffects.emitted(id) - eventsBefore);
            currentVariant.put("worldGeometry", VisualEffects.frameDiagnostics());
            if (HudWidgets.IDS.contains(id)) {
                var bounds = lastHud.stream().filter(widget -> widget.id().equals(id)).findFirst();
                require(bounds.isPresent(), "Actual HUD widget participates in live rendering for " + id + " / " + variant);
                currentVariant.put("hudWidgetBounds", bounds.get());
            }
            if (Set.of("entity-esp", "storage-esp", "tracers").contains(id)) {
                currentVariant.put("espTargets", AnalyzerRenderer.counts()); currentVariant.put("espScreenTargets", AnalyzerRenderer.screenCounts());
            }
            if (id.equals("expedition")) require(!AutomationEngine.INSTANCE.isRunning(), "Stored Automation enable cannot start a task / " + variant);
            if (id.equals("coord-snapper")) require(UtilityEngine.operations(id) == operationsBefore && core.isEnabled(id), "Open menu blocks coordinate copying / " + variant);
            menuActions = GameplayEngine.actions(id); uiBefore = UiShader.surfaces();
            menu = new StudioScreen(core).inspect(id); mc.setScreen(menu);
        }
        if (age == 5) {
            require(menu.controlPoint("module:" + id) != null, "Actual Studio card is rendered for " + id + " / " + variant);
            require(UiShader.surfaces() > uiBefore && UiShader.isHealthy() && GpuEffects.isHealthy() && GL11.glGetError() == GL11.GL_NO_ERROR,
                    "Actual shader menu remains healthy for " + id + " / " + variant);
            if (GAMEPLAY.contains(id)) require(GameplayEngine.actions(id) == menuActions, "Manual menu pauses action controller for " + id + " / " + variant);
            if (variant == 0 && firstInCategory(core, moduleIndex)) capture(mc, "analyzer-module-sweep-" + module.category().name().toLowerCase(java.util.Locale.ROOT) + ".png");
            core.setEnabled(id, false); mc.setScreen(null);
        }
        if (age >= 6) {
            require(!core.isEnabled(id), "Disable remains effective for " + id + " / " + variant);
            near(mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT).getBaseValue(), originalStep, "Owned Step cleanup after " + id + " / " + variant);
            require(!mc.options.forwardKey.isPressed() && !mc.options.backKey.isPressed() && !mc.options.leftKey.isPressed()
                    && !mc.options.rightKey.isPressed() && !mc.options.jumpKey.isPressed() && !mc.options.sneakKey.isPressed()
                    && !mc.options.sprintKey.isPressed(), "Owned movement inputs are released after " + id + " / " + variant);
            require(mc.player.getInventory().selectedSlot == 0, "Owned empty-inventory hotbar slot restores after " + id + " / " + variant);
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "GL state after disable " + id + " / " + variant);
            @SuppressWarnings("unchecked") var variants = (List<Map<String, Object>>) modules.getLast().get("variants"); variants.add(currentVariant);
            AnalyzerClient.LOGGER.info("ANALYZER_MODULE_SWEEP_VARIANT: {} {} actions={} utility={} cosmeticFrames={} events={} status={}", id, variant,
                    currentVariant.get("acceptedGameplayActions"), currentVariant.get("utilityOperations"), currentVariant.get("cosmeticRenderFrames"), currentVariant.get("visualEvents"), currentVariant.get("liveStatus"));
            if (++variant == 3) { variant = 0; moduleIndex++; }
            entered = tick;
            if (moduleIndex == core.modules().size()) {
                require(modules.size() == 123 && modules.stream().allMatch(row -> ((List<?>) row.get("variants")).size() == 3), "All 369 live module variants completed");
                next();
            }
        }
    }

    private static Object liveValue(Setting<?> setting, int variant) {
        if (variant == 0) return setting.defaultValue();
        return switch (setting.kind()) {
            case BOOLEAN -> variant == 2;
            case NUMBER -> setting.min() + (setting.max() - setting.min()) * (variant == 1 ? .15 : .85);
            case CHOICE -> variant == 1 ? setting.choices().getFirst() : setting.choices().getLast();
        };
    }

    private static boolean firstInCategory(AnalyzerCore core, int index) {
        Category category = core.modules().get(index).category();
        for (int i = 0; i < index; i++) if (core.modules().get(i).category() == category) return false;
        return true;
    }

    private static void matrix(MinecraftClient mc) {
        Path directory = mc.runDirectory.toPath().resolve("config/analyzer/module-sweep-matrix");
        AnalyzerCore matrix = new AnalyzerCore(directory);
        int cycles = Math.max(3, matrix.modules().stream().flatMap(module -> module.settings().stream()).mapToInt(setting -> setting.choices().size()).max().orElse(3));
        var coverage = new ArrayList<Map<String, Object>>();
        for (ModuleDefinition module : matrix.modules()) {
            var fields = new ArrayList<Map<String, Object>>();
            for (Setting<?> setting : module.settings()) {
                var field = new LinkedHashMap<String, Object>(); field.put("id", setting.id()); field.put("kind", setting.kind().name());
                List<?> inputs = switch (setting.kind()) {
                    case BOOLEAN -> List.of(false, true);
                    case NUMBER -> List.of(setting.min(), (setting.min() + setting.max()) / 2, setting.max());
                    case CHOICE -> setting.choices();
                };
                var normalized = new ArrayList<Object>();
                for (Object value : inputs) {
                    matrix.setValue(module.id(), setting.id(), value); Object actual = matrix.getValue(module.id(), setting.id()); normalized.add(actual); settingsWrites++;
                    if (setting.kind() == Setting.Kind.NUMBER) require((Double) actual >= setting.min() && (Double) actual <= setting.max(), "Bounded numeric matrix " + module.id() + "/" + setting.id());
                    else require(actual.equals(value), "Exact typed matrix " + module.id() + "/" + setting.id());
                }
                Object before = matrix.getValue(module.id(), setting.id());
                Object invalid = switch (setting.kind()) { case BOOLEAN -> "true"; case NUMBER -> Double.NaN; case CHOICE -> "__invalid_choice__"; };
                boolean rejected = false;
                try { matrix.setValue(module.id(), setting.id(), invalid); } catch (IllegalArgumentException expected) { rejected = true; rejectedWrites++; }
                require(rejected && matrix.getValue(module.id(), setting.id()).equals(before), "Invalid write leaves matrix state intact " + module.id() + "/" + setting.id());
                if (setting.kind() == Setting.Kind.NUMBER) {
                    matrix.setValue(module.id(), setting.id(), setting.min() - setting.step() * 10); near(matrix.getNumber(module.id(), setting.id()), setting.min(), "Lower numeric clamp " + module.id() + "/" + setting.id());
                    matrix.setValue(module.id(), setting.id(), setting.max() + setting.step() * 10);
                    require(matrix.getNumber(module.id(), setting.id()) <= setting.max() && matrix.getNumber(module.id(), setting.id()) >= setting.max() - setting.step() - .000001,
                            "Upper numeric clamp " + module.id() + "/" + setting.id()); settingsWrites += 2;
                }
                field.put("acceptedInputs", inputs); field.put("storedValues", normalized); field.put("invalidWriteRejected", true); fields.add(field);
            }
            var row = new LinkedHashMap<String, Object>(); row.put("id", module.id()); row.put("settings", fields); coverage.add(row);
        }
        try {
            for (int cycle = 0; cycle < cycles; cycle++) {
                for (ModuleDefinition module : matrix.modules()) {
                    matrix.setEnabled(module.id(), cycle % 2 == 0); matrix.setKeybind(module.id(), 65 + cycle % 26);
                    for (Setting<?> setting : module.settings()) {
                        Object value = switch (setting.kind()) {
                            case BOOLEAN -> cycle % 2 == 0;
                            case NUMBER -> setting.min() + (setting.max() - setting.min()) * (cycle % 3) / 2;
                            case CHOICE -> setting.choices().get(cycle % setting.choices().size());
                        };
                        matrix.setValue(module.id(), setting.id(), value); settingsWrites++;
                    }
                }
                matrix.save(); matrix.saveProfile("matrix-" + cycle);
                AnalyzerCore active = new AnalyzerCore(directory); active.load(); verifySnapshot(matrix, active, "Active config " + cycle); configRoundtrips++;
                AnalyzerCore profile = new AnalyzerCore(directory); profile.loadProfile("matrix-" + cycle); verifySnapshot(matrix, profile, "Profile " + cycle); profileRoundtrips++;
            }
        } catch (IOException failure) { throw new IllegalStateException("Isolated setting matrix persistence failed", failure); }
        report.put("settingMatrix", coverage); report.put("matrixCycles", cycles);
        AnalyzerClient.LOGGER.info("ANALYZER_MODULE_MATRIX_COMPLETE: {} accepted writes, {} rejected invalid writes, {} active-config and {} profile roundtrips", settingsWrites, rejectedWrites, configRoundtrips, profileRoundtrips);
    }

    private static void verifySnapshot(AnalyzerCore expected, AnalyzerCore actual, String label) {
        require(actual.loadWarnings().isEmpty(), label + " restores without warnings");
        for (ModuleDefinition module : expected.modules()) {
            require(expected.values(module.id()).equals(actual.values(module.id())), label + " settings for " + module.id());
            require(expected.isEnabled(module.id()) == actual.isEnabled(module.id()) && expected.keybind(module.id()) == actual.keybind(module.id()), label + " state/keybind for " + module.id());
        }
    }

    private static void finish(MinecraftClient mc) {
        var core = AnalyzerClient.CORE;
        AutomationEngine.INSTANCE.stop(); core.resetAll(); mc.setScreen(null); mc.options.setPerspective(originalPerspective);
        try { core.save(); } catch (IOException failure) { throw new IllegalStateException("Cannot restore isolated sweep config", failure); }
        require(GL11.glGetError() == GL11.GL_NO_ERROR && UiShader.isHealthy() && GpuEffects.isHealthy(), "Final shader/GL health");
        report.put("result", "PASS"); report.put("liveVariants", 369); report.put("assertions", assertions);
        report.put("settingWrites", settingsWrites); report.put("rejectedInvalidWrites", rejectedWrites);
        report.put("activeConfigRoundtrips", configRoundtrips); report.put("profileRoundtrips", profileRoundtrips);
        report.put("screenshotCount", screenshots); report.put("fixtureTicks", tick); report.put("elapsedSeconds", (System.nanoTime() - started) / 1e9);
        report.put("focusedCombatFixture", "PASS; see ANALYZER_GAMEPLAY_COMBAT_SMOKE_COMPLETE in runtime log for exact assertion count and tested actions");
        report.put("modules", modules);
        Path path = Path.of("build/reports/all-module-sweep-coverage.json").toAbsolutePath();
        // Loom launches from run/, so place the review report in the repository's build/reports.
        if (mc.runDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().equals("run")) path = mc.runDirectory.toPath().toAbsolutePath().normalize().getParent().resolve("build/reports/all-module-sweep-coverage.json");
        try { Files.createDirectories(path.getParent()); Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8); }
        catch (IOException failure) { throw new IllegalStateException("Cannot write module sweep report", failure); }
        complete = true;
        AnalyzerClient.LOGGER.info("ANALYZER_ALL_MODULES_SMOKE_COMPLETE: {} modules; 369 live variants; {} assertions; {} accepted setting writes; {} invalid writes rejected; {} config + {} profile roundtrips; {} screenshots; {} ticks; report={}",
                core.modules().size(), assertions, settingsWrites, rejectedWrites, configRoundtrips, profileRoundtrips, screenshots, tick, path);
        mc.scheduleStop();
    }

    private static void allOff(AnalyzerCore core) { for (ModuleDefinition module : core.modules()) core.setEnabled(module.id(), false); }
    private static void next() { stage++; entered = tick; }
    private static void near(double actual, double expected, String label) { require(Math.abs(actual - expected) < .000001, label + " (actual " + actual + ")"); }
    private static void require(boolean condition, String label) { if (!condition) throw new IllegalStateException("All-module sweep stage " + stage + ", module " + moduleIndex + ", variant " + variant + ": " + label); assertions++; }
    private static void command(MinecraftClient mc, String command) { mc.getServer().execute(() -> mc.getServer().getCommandManager().executeWithPrefix(mc.getServer().getCommandSource().withSilent(), command)); }
    private static void capture(MinecraftClient mc, String name) {
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "GL state before " + name);
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, name, mc.getFramebuffer(), message -> AnalyzerClient.LOGGER.info("Module sweep screenshot: {}", message.getString())); screenshots++;
    }
}
