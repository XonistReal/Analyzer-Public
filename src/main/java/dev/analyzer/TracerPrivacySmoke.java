package dev.analyzer;

import dev.analyzer.finder.FinderEngine;
import dev.analyzer.privacy.NameChanger;
import dev.analyzer.render.AnalyzerRenderer;
import dev.analyzer.render.EntityFinderRenderer;
import dev.analyzer.render.TracerOrigin;
import dev.analyzer.ui.GuiDraw;
import dev.analyzer.ui.TextSettingScreen;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.EditBox;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ChatMessages;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.SelectionManager;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Isolated development acceptance of real native tracer vertices and local name presentation. */
public final class TracerPrivacySmoke {
    private static final String WORLD = "Analyzer-Tracer-Privacy-Smoke";
    private static final String ALIAS = "PrivatePilot";
    private static final List<String> PRODUCERS = List.of("tracers", "storage-esp", "amethyst-finder", "villager-esp", "skeleton-esp");
    private static final List<String> ORIGINS = List.of("Crosshair", "Bottom", "Top");
    private static final List<String> CAPTURES = new ArrayList<>();
    private static final Set<String> OBSERVED = new LinkedHashSet<>();
    private static final Set<Integer> WALKING_CASES = new LinkedHashSet<>();
    private static boolean requested, setupRequested, ready, finished, disconnected, renderedTextChecked;
    private static volatile boolean serverPrepared;
    private static volatile int written;
    private static int ticks, phaseTicks, phase, assertions, disconnectTicks, bobSamples;
    private static String realName;
    private static UUID sessionUuid, playerUuid;
    private static Text rawText;
    private static OrderedText rawOrdered;
    private static float previousRelativeX, previousRelativeY, previousRelativeZ;
    private static double caseStartX;

    private TracerPrivacySmoke() { }

    @SuppressWarnings("deprecation")
    public static void tick(MinecraftClient mc) {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment() || !Boolean.getBoolean("analyzer.smoke")
                || !Boolean.getBoolean("analyzer.tracerPrivacySmoke")
                || !mc.runDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().equals("run-tracer-privacy-smoke"))
            throw new IllegalStateException("Tracer/privacy native acceptance requires its isolated development launch");
        if (finished) return;
        if (++ticks > 3600) throw new IllegalStateException("Tracer/privacy native fixture timed out; phase=" + phase + ", samples=" + TracerOrigin.samples());
        if (disconnected) {
            if (++disconnectTicks < 12) return;
            require(written == 3 && allFilesExist(mc), "Three unedited framebuffer callbacks and files completed");
            require(OBSERVED.size() == 30, "Five actual tracer producers submitted all three origins in both native visibility passes");
            require(bobSamples > 0, "Actual walking with view bob supplied nonzero projection offsets");
            require(WALKING_CASES.size() == 2, "All five submitted tracer origins remain screen locked during actual walking bob in both visibility passes");
            TracerOrigin.captureForChecks(false);
            finished = true;
            AnalyzerClient.LOGGER.info("ANALYZER_TRACER_PRIVACY_COMPLETE: {} checks; {} producer/origin/pass combinations {}; {} walking-bob matrix samples; three native framebuffer photos; real String/Text/OrderedText draws and width/wrap/trim; live aliases and original chat/session/profile retained",
                    assertions, OBSERVED.size(), OBSERVED, bobSamples);
            mc.scheduleStop();
            return;
        }
        if (!requested && mc.getOverlay() == null && mc.currentScreen != null
                && mc.currentScreen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")) mc.setScreen(new TitleScreen());
        if (!requested && mc.getOverlay() == null && mc.currentScreen instanceof TitleScreen) {
            requested = true;
            AnalyzerClient.CORE.resetAll();
            for (var module : AnalyzerClient.CORE.modules()) AnalyzerClient.CORE.setEnabled(module.id(), false);
            mc.options.pauseOnLostFocus = false;
            mc.options.getViewDistance().setValue(4);
            mc.options.getSimulationDistance().setValue(5);
            mc.options.getGuiScale().setValue(2);
            mc.options.getFov().setValue(70);
            mc.options.setPerspective(Perspective.FIRST_PERSON);
            mc.options.hudHidden = false;
            mc.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
            mc.getToastManager().clear();
            GLFW.glfwHideWindow(mc.getWindow().getHandle());
            HudRenderCallback.EVENT.register((draw, counter) -> renderEvidence(mc, draw));
            if (Files.exists(mc.runDirectory.toPath().resolve("saves/" + WORLD + "/level.dat")))
                mc.createIntegratedServerLoader().start(WORLD, () -> mc.scheduleStop());
            else mc.createIntegratedServerLoader().createAndStart(WORLD,
                    new LevelInfo(WORLD, GameMode.CREATIVE, false, Difficulty.NORMAL, true,
                            new GameRules(FeatureFlags.VANILLA_FEATURES), DataConfiguration.SAFE_MODE),
                    new GeneratorOptions(8261301L, false, false), WorldPresets::createDemoOptions, new TitleScreen());
        }
        if (!requested || mc.world == null || mc.player == null || mc.getServer() == null || mc.getOverlay() != null) return;
        if (!setupRequested) { setupRequested = true; setup(mc); return; }
        if (!ready) {
            if (!serverPrepared || mc.player.getBlockY() != 161
                    || !mc.world.getBlockState(new BlockPos(10, 161, 7)).isOf(Blocks.BUDDING_AMETHYST)
                    || !mc.world.getBlockState(new BlockPos(6, 161, 7)).isOf(Blocks.CHEST)) return;
            ready = true;
            mc.setScreen(null);
            mc.getToastManager().clear();
            configure(mc);
            checkNames(mc);
            beginCase(mc);
        }
        phaseTicks++;
        if (phase < 6) {
            boolean moving = phase % 3 == 1;
            mc.options.forwardKey.setPressed(moving && phaseTicks < 16);
            if (moving && phaseTicks < 16) {
                mc.player.setSprinting(true);
                mc.player.setYaw(mc.player.getYaw() + .15f);
            }
            List<TracerOrigin.Sample> samples = TracerOrigin.samples();
            boolean allSamples = PRODUCERS.stream().allMatch(id -> samples.stream().anyMatch(sample -> sample.module().equals(id)));
            boolean populated = AnalyzerRenderer.counts().tracers() >= 2 && AnalyzerRenderer.counts().containers() >= 1
                    && FinderEngine.count("amethyst-finder") >= 1 && EntityFinderRenderer.count("villager-esp") >= 1
                    && EntityFinderRenderer.boneCount() >= 8;
            double movedSquared = (mc.player.getX() - caseStartX) * (mc.player.getX() - caseStartX)
                    + (mc.player.getZ() - 19.5) * (mc.player.getZ() - 19.5);
            if (moving && phaseTicks >= 5 && phaseTicks < 16 && !WALKING_CASES.contains(phase)
                    && allSamples && populated && movedSquared > .04
                    && samples.stream().allMatch(sample -> Math.abs(sample.projection20()) + Math.abs(sample.projection21())
                    + Math.abs(sample.projection30()) + Math.abs(sample.projection31()) > 1e-6f)) {
                validateSamples(samples, "during actual walking bob");
                bobSamples += PRODUCERS.size(); WALKING_CASES.add(phase);
                require(true, "All five actual emitted origins stay fixed while walking bob offsets are nonzero; pass=" + (phase < 3));
            }
            if (phaseTicks < 65 || !renderedTextChecked || !allSamples) return;
            String population = "tracers=" + AnalyzerRenderer.counts().tracers() + ", storage=" + AnalyzerRenderer.counts().containers()
                    + ", amethyst=" + FinderEngine.count("amethyst-finder") + ", villagers=" + EntityFinderRenderer.count("villager-esp")
                    + ", skeletons=" + EntityFinderRenderer.count("skeleton-esp") + ", bones=" + EntityFinderRenderer.boneCount();
            if (!populated) {
                if (phaseTicks % 100 == 0) AnalyzerClient.LOGGER.info("Native fixture waits for complete geometry: {}", population);
                if (phaseTicks > 400) throw new IllegalStateException("Tracer/privacy native geometry did not populate: " + population);
                return;
            }
            require(true, "Real received entity, storage, block-finder, entity-finder and posed-skeleton geometry is populated: " + population);
            if (moving) require(WALKING_CASES.contains(phase), "All five projected origins were checked during actual walking bob before stopping");
            validateSamples(samples, "settled camera");
            String origin = ORIGINS.get(phase % 3);
            var own = samples.stream().filter(value -> value.module().equals("tracers")).findFirst().orElseThrow();
            if (phase > 0) require(Math.abs(own.relativeX() - previousRelativeX) + Math.abs(own.relativeY() - previousRelativeY)
                            + Math.abs(own.relativeZ() - previousRelativeZ) > .00001f,
                    "Screen lock recomputes a changed native point after origin, camera, FOV or perspective changes");
            previousRelativeX = own.relativeX(); previousRelativeY = own.relativeY(); previousRelativeZ = own.relativeZ();
            if (phase < 3) capture(mc, "analyzer-native-tracer-privacy-" + origin.toLowerCase(java.util.Locale.ROOT) + ".png");
            mc.options.forwardKey.setPressed(false);
            mc.player.setSprinting(false);
            phase++; phaseTicks = 0;
            if (phase < 6) beginCase(mc);
            else {
                checkChatRefresh(mc);
                require(realName.equals(mc.getSession().getUsername()) && sessionUuid.equals(mc.getSession().getUuidOrNull())
                                && playerUuid.equals(mc.player.getUuid()) && realName.equals(mc.player.getGameProfile().getName()),
                        "Alias leaves the actual session, entity UUID and GameProfile unchanged");
                require(rawText.getString().equals("Welcome, " + realName) && ordered(rawOrdered).equals("Welcome, " + realName),
                        "Native rendering and live alias refresh never mutate source Text or original OrderedText");
                TracerOrigin.captureForChecks(false);
            }
        } else if (phase == 6 && phaseTicks >= 15) {
            checkEditorGeometry(mc);
            openAliasScreen(mc);
            phase = 7; phaseTicks = 0;
        } else if (phase == 7 && phaseTicks >= 20) {
            require(mc.currentScreen instanceof TextSettingScreen, "Actual shader-styled alias editor opens and renders");
            var screen = (TextSettingScreen) mc.currentScreen;
            var field = screen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow();
            field.setText("bad alias");
            screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            require(mc.currentScreen == screen && !screen.errorText().isEmpty() && AnalyzerClient.CORE.getText("name-changer", "alias").equals(ALIAS),
                    "Invalid alias stays in the actual entry screen with a visible validation message and unchanged setting");
            field.setText(""); field.setFocused(true); screen.setFocused(field);
            for (char letter : "StreamPilot".toCharArray()) require(screen.charTyped(letter, 0), "Actual alias entry accepts typed character: " + letter);
            require(screen.inputValue().equals("StreamPilot"), "Native focused text input retains exactly the typed alias");
            double[] apply = screen.controlPoint("apply");
            screen.mouseClicked(apply[0], apply[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(mc.currentScreen == null && AnalyzerClient.CORE.getText("name-changer", "alias").equals("StreamPilot")
                            && NameChanger.render(realName).equals("StreamPilot"),
                    "Actual Apply button saves the alias, closes the editor and updates local presentation");
            openAliasScreen(mc);
            phase = 8; phaseTicks = 0;
        } else if (phase == 8 && phaseTicks >= 20) {
            var screen = (TextSettingScreen) mc.currentScreen;
            var field = screen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow();
            field.setText("DiscardThis");
            double[] cancel = screen.controlPoint("cancel");
            screen.mouseClicked(cancel[0], cancel[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(mc.currentScreen == null && AnalyzerClient.CORE.getText("name-changer", "alias").equals("StreamPilot"),
                    "Actual Cancel button discards unsaved text and preserves the applied alias");
            AnalyzerClient.CORE.setValue("name-changer", "alias", ALIAS);
            NameChanger.refreshChat(mc);
            phase = 9; phaseTicks = 0;
        } else if (phase == 9 && phaseTicks >= 15 && written == 3 && allFilesExist(mc)) {
            mc.options.forwardKey.setPressed(false);
            mc.player.setSprinting(false);
            disconnected = true;
            mc.world.disconnect();
            mc.disconnect(new MessageScreen(Text.translatable("menu.savingLevel")));
            mc.setScreen(new TitleScreen());
        }
    }

    private static void configure(MinecraftClient mc) {
        var core = AnalyzerClient.CORE;
        for (String id : PRODUCERS) {
            core.setEnabled(id, true);
            core.setValue(id, "range", 48);
            core.setValue(id, "through-walls", true);
            if (!id.equals("tracers")) core.setValue(id, "tracers", true);
        }
        core.setValue("tracers", "hostiles", true);
        core.setValue("tracers", "passives", true);
        core.setValue("tracers", "line-width", 1.5);
        core.setValue("storage-esp", "tracer-width", 1.5);
        for (String id : List.of("amethyst-finder", "villager-esp", "skeleton-esp")) {
            core.setValue(id, "scan-budget", 16384);
            core.setValue(id, "line-width", 1.5);
        }
        core.setValue("amethyst-finder", "min-y", 160);
        core.setValue("amethyst-finder", "max-y", 170);
        core.setValue("skeleton-esp", "hostiles", true);
        realName = mc.getSession().getUsername(); sessionUuid = mc.getSession().getUuidOrNull(); playerUuid = mc.player.getUuid();
        rawText = Text.literal("Welcome, " + realName);
        rawOrdered = rawText.asOrderedText();
        core.setValue("name-changer", "alias", ALIAS);
        core.setEnabled("name-changer", true);
        mc.inGameHud.getChatHud().clear(false);
        mc.inGameHud.getChatHud().addMessage(Text.literal("<" + realName + "> Original chat stays stored locally."));
        mc.options.sendClientSettings();
    }

    private static void beginCase(MinecraftClient mc) {
        String origin = ORIGINS.get(phase % 3);
        for (String producer : PRODUCERS) {
            AnalyzerClient.CORE.setValue(producer, "tracer-origin", origin);
            AnalyzerClient.CORE.setValue(producer, "through-walls", phase < 3);
        }
        mc.options.getBobView().setValue(phase % 3 != 0);
        mc.options.getFov().setValue(phase % 3 == 0 ? 70 : phase % 3 == 1 ? 90 : 100);
        mc.options.setPerspective(phase % 3 == 2 ? Perspective.THIRD_PERSON_BACK : Perspective.FIRST_PERSON);
        mc.player.setVelocity(Vec3d.ZERO);
        double x = phase % 3 == 0 ? 10.5 : phase % 3 == 1 ? 12.5 : 8.5;
        caseStartX = x;
        float yaw = phase % 3 == 0 ? 180 : phase % 3 == 1 ? 170 : 190;
        command(mc, "tp AnalyzerDev " + x + " 161 19.5 " + yaw + " 11");
        TracerOrigin.captureForChecks(true);
        AnalyzerClient.LOGGER.info("Tracer/privacy native case {}: origin={}, depth={}, FOV={}, perspective={}, viewBob={}",
                phase, origin, phase < 3 ? "through walls" : "depth tested", mc.options.getFov().getValue(),
                mc.options.getPerspective(), mc.options.getBobView().getValue());
    }

    private static void checkNames(MinecraftClient mc) {
        var core = AnalyzerClient.CORE;
        var renderer = mc.textRenderer;
        String sentence = "Welcome, " + realName;
        String expected = "Welcome, " + ALIAS;
        Text source = Text.literal("Welcome, ").append(Text.literal(realName.substring(0, 4)).formatted(Formatting.AQUA))
                .append(Text.literal(realName.substring(4)).formatted(Formatting.GOLD));
        List<Style> before = new ArrayList<>();
        source.visit((style, content) -> { before.add(style); return java.util.Optional.empty(); }, Style.EMPTY);
        require(NameChanger.render(sentence).equals(expected), "Own username is locally replaced inside a normal sentence");
        var cachedOrdered = NameChanger.render(rawOrdered);
        var cachedVisitable = NameChanger.render(rawText);
        require(NameChanger.render(realName + "Suffix").equals(realName + "Suffix"), "Longer usernames sharing the real-name prefix remain unchanged");
        require(NameChanger.render(source).getString().equals(expected), "A username split across styled Text siblings is masked");
        require(ordered(NameChanger.render(rawOrdered)).equals(expected), "Existing OrderedText created before masking receives the selected alias");
        require(renderer.getWidth(sentence) == renderer.getWidth(expected), "Actual String measurement agrees with alias glyphs");
        require(renderer.getWidth(rawText) == renderer.getWidth(Text.literal(expected)), "Actual Text measurement agrees with alias glyphs");
        require(renderer.getWidth(rawOrdered) == renderer.getWidth(Text.literal(expected).asOrderedText()), "Actual OrderedText measurement agrees with alias glyphs");
        require(renderer.trimToWidth(sentence, 10000).equals(expected), "Native String trimming presents the alias");
        require(renderer.trimToWidth(rawText, 10000).getString().equals(expected), "Native styled trimming presents the alias");
        require(join(renderer.wrapLines(rawText, 10000)).equals(expected), "Native wrapped Text presents the alias");
        require(join(ChatMessages.breakRenderedChatMessageLines(rawText, 10000, renderer)).strip().equals(expected), "Native chat line wrapping masks before width layout");
        require(source.getString().equals(sentence), "Styling-aware replacement retains the raw source Text");
        List<Style> after = new ArrayList<>();
        source.visit((style, content) -> { after.add(style); return java.util.Optional.empty(); }, Style.EMPTY);
        require(before.equals(after), "Presentation leaves source sibling styles intact");
        core.setValue("name-changer", "alias", realName + "_Alt");
        require(ordered(NameChanger.render(cachedOrdered)).equals("Welcome, " + realName + "_Alt")
                        && NameChanger.render(cachedVisitable).getString().equals("Welcome, " + realName + "_Alt"),
                "Previously returned presentation wrappers refresh from their raw originals after an alias edit");
        require(NameChanger.render(realName).equals(realName + "_Alt") && NameChanger.render(realName + "_Alt").equals(realName + "_Alt"),
                "An alias containing the real-name prefix remains stable under repeated presentation");
        core.setValue("name-changer", "alias", ALIAS);
        core.setEnabled("name-changer", false);
        require(NameChanger.render(sentence).equals(sentence) && ordered(NameChanger.render(rawOrdered)).equals(sentence),
                "Disabling restores the real name on existing String and OrderedText inputs");
        require(ordered(NameChanger.render(cachedOrdered)).equals(sentence) && NameChanger.render(cachedVisitable).getString().equals(sentence),
                "Disabling unwraps previously returned presentation wrappers back to their real source");
        core.setEnabled("name-changer", true);
        require(NameChanger.render(sentence).equals(expected), "Re-enabling restores the saved alias immediately");
        NameChanger.refreshChat(mc);
    }

    private static void openAliasScreen(MinecraftClient mc) {
        var core = AnalyzerClient.CORE;
        mc.setScreen(new TextSettingScreen(core, "name-changer", core.definition("name-changer").setting("alias"), null));
    }

    private static void checkEditorGeometry(MinecraftClient mc) {
        for (String type : List.of("net.minecraft.client.gui.screen.ingame.BookEditScreen",
                "net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen", "net.minecraft.client.gui.widget.EditBoxWidget",
                "net.minecraft.client.gui.widget.TextFieldWidget", "net.minecraft.client.gui.EditBox",
                "net.minecraft.client.util.SelectionManager", "net.minecraft.client.gui.hud.ChatHud",
                "net.minecraft.client.font.TextRenderer", "net.minecraft.client.font.TextHandler")) {
            try { Class.forName(type); }
            catch (ClassNotFoundException failure) { throw new IllegalStateException("Native editor fixture class missing: " + type, failure); }
            require(true, "Native editor class loads with its required privacy mixins: " + type);
        }
        var core = AnalyzerClient.CORE;
        core.setEnabled("name-changer", false);
        int actualWidth = mc.textRenderer.getWidth(realName);
        TextFieldWidget baseline = new TextFieldWidget(mc.textRenderer, 10, 10, 200, 20, Text.literal("Native edit baseline"));
        baseline.setText(realName); baseline.setCursorToEnd(false);
        int rawX = baseline.getCharacterX(realName.length());
        EditBox original = new EditBox(mc.textRenderer, Math.max(1, actualWidth - 1));
        String content = realName + "\n" + realName;
        original.setText(content);
        List<Object> rawLines = new ArrayList<>(); ((Iterable<?>) original.getLines()).forEach(rawLines::add);
        original.moveCursor(actualWidth - 3, 0);
        int rawCursor = original.getCursor();
        original.moveCursorLine(1); int rawNextCursor = original.getCursor();
        core.setValue("name-changer", "alias", "I");
        core.setEnabled("name-changer", true);
        require(mc.textRenderer.getWidth(realName) != actualWidth, "Short active alias creates a meaningful raw editor geometry contrast");
        TextFieldWidget field = new TextFieldWidget(mc.textRenderer, 10, 10, 200, 20, Text.literal("Native editing fixture"));
        field.setText(realName); field.setCursorToEnd(false);
        require(field.getText().equals(realName) && field.getCursor() == realName.length() && field.getCharacterX(realName.length()) == rawX,
                "Actual text-field content, UTF-16 cursor and pixel geometry retain the real editable value");
        field.moveCursor(-2, false); field.write("Z");
        require(field.getText().equals(realName.substring(0, realName.length() - 2) + "Z" + realName.substring(realName.length() - 2)),
                "Native text-field insertion uses original indices while display masking is active");
        EditBox edited = new EditBox(mc.textRenderer, Math.max(1, actualWidth - 1));
        edited.setText(content);
        List<Object> activeLines = new ArrayList<>(); ((Iterable<?>) edited.getLines()).forEach(activeLines::add);
        require(edited.getText().equals(content) && rawLines.equals(activeLines), "Actual multiline editor wraps original character ranges rather than alias ranges");
        edited.moveCursor(actualWidth - 3, 0);
        require(edited.getCursor() == rawCursor, "Actual multiline pointer placement matches the unmasked raw-index baseline");
        edited.moveCursorLine(1);
        require(edited.getCursor() == rawNextCursor, "Actual multiline vertical movement matches the unmasked raw-index baseline");
        StringBuilder selection = new StringBuilder();
        SelectionManager manager = new SelectionManager(selection::toString, value -> { selection.setLength(0); selection.append(value); },
                () -> "", value -> { }, value -> mc.textRenderer.getWidth(value) < actualWidth);
        manager.insert(realName);
        require(selection.isEmpty(), "Native selection-manager width validation rejects the same raw input despite a much shorter alias");
        try (var ignored = NameChanger.editable()) {
            require(mc.textRenderer.getWidth(realName) == actualWidth && NameChanger.render(realName).equals(realName),
                    "Explicit editing scope retains original text and native width for validators");
        }
        require(NameChanger.render(realName).equals("I"), "Leaving editing scope restores ordinary local masking");
        core.setValue("name-changer", "alias", ALIAS);
        NameChanger.refreshChat(mc);
    }

    private static void validateSamples(List<TracerOrigin.Sample> samples, String moment) {
        String origin = ORIGINS.get(phase % 3);
        boolean throughWalls = phase < 3;
        for (String producer : PRODUCERS) {
            var sample = samples.stream().filter(value -> value.module().equals(producer)).findFirst().orElseThrow();
            require(sample.choice().equals(origin), "Submitted origin matches selected setting: " + producer + " / " + origin + " / " + moment);
            require(Float.isFinite(sample.relativeX() + sample.relativeY() + sample.relativeZ())
                            && Math.abs(sample.screenX()) < .0005f && Math.abs(sample.screenY() - sample.expectedY()) < .0005f
                            && sample.clipZ() > -1 && sample.clipZ() < 1,
                    "Actual native line shader vertex projects to fixed screen anchor: " + producer + " / " + origin
                            + " / " + (throughWalls ? "through walls" : "depth tested") + " / " + moment
                            + " / (" + sample.screenX() + ", " + sample.screenY() + ")");
            OBSERVED.add(producer + ":" + origin + ":" + throughWalls);
        }
    }

    private static void checkChatRefresh(MinecraftClient mc) {
        var core = AnalyzerClient.CORE;
        String message = "<" + realName + "> Original chat stays stored locally.";
        require(storedChat(mc).stream().anyMatch(line -> line.content().getString().equals(message)), "Chat history retains its genuine original message component");
        core.setValue("name-changer", "alias", "SecondPilot");
        NameChanger.refreshChat(mc);
        require(visibleChat(mc).stream().map(line -> ordered(line.content())).anyMatch(value -> value.contains("SecondPilot")),
                "Actual pre-existing visible chat lines rebuild when the alias changes");
        core.setEnabled("name-changer", false);
        NameChanger.refreshChat(mc);
        require(visibleChat(mc).stream().map(line -> ordered(line.content())).anyMatch(value -> value.contains(realName)),
                "Actual pre-existing visible chat lines restore genuine content when disabled");
        require(storedChat(mc).stream().anyMatch(line -> line.content().getString().equals(message)), "Refreshing display caches never rewrites stored chat");
        core.setValue("name-changer", "alias", ALIAS);
        core.setEnabled("name-changer", true);
        NameChanger.refreshChat(mc);
    }

    private static void renderEvidence(MinecraftClient mc, DrawContext draw) {
        if (!ready || finished || disconnected || mc.world == null || mc.currentScreen != null) return;
        String original = "Welcome, " + realName;
        String expected = "Welcome, " + ALIAS;
        int width = mc.textRenderer.getWidth(expected);
        draw.fill(10, 10, 352, 105, 0xB5181820);
        draw.drawText(mc.textRenderer, "Native tracer / local name presentation", 18, 18, 0xFFF3D5EC, false);
        draw.drawText(mc.textRenderer, "Origin: " + ORIGINS.get(Math.min(phase, 5) % 3)
                + " / " + (phase < 3 ? "through walls" : "depth tested"), 18, 34, 0xFFFFFFFF, false);
        int stringEnd = draw.drawText(mc.textRenderer, original, 18, 50, 0xFFB3F5FF, false);
        int textEnd = draw.drawText(mc.textRenderer, rawText, 18, 64, 0xFFFFD5A3, false);
        int orderedEnd = draw.drawText(mc.textRenderer, rawOrdered, 18, 78, 0xFFC8FFC3, false);
        try (var ignored = GuiDraw.sansFont()) { GuiDraw.label(draw, mc.textRenderer, original, 180, 70, .9f, 0xFFF0C2EA); }
        // Use the genuine current player-list data, even though singleplayer normally hides a one-entry list.
        mc.inGameHud.getPlayerListHud().render(draw, mc.getWindow().getScaledWidth(), mc.world.getScoreboard(), null);
        if (!renderedTextChecked) {
            require(stringEnd == 18 + width && textEnd == 18 + width && orderedEnd == 18 + width,
                    "Actual native String, Text and OrderedText drawing returns the alias glyph endpoint");
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "Native alias text and actual player-list rendering retain clean GL state");
            renderedTextChecked = true;
        }
    }

    private static void setup(MinecraftClient mc) {
        mc.getServer().execute(() -> {
            var server = mc.getServer();
            for (int x = -1; x <= 2; x++) for (int z = -1; z <= 2; z++) server.getOverworld().getChunk(x, z);
            List<String> commands = List.of("gamemode creative AnalyzerDev", "clear AnalyzerDev", "effect clear AnalyzerDev",
                    "gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule randomTickSpeed 0", "time set 1000", "weather clear", "kill @e[type=!minecraft:player]",
                    "fill -2 160 -2 24 160 26 minecraft:polished_deepslate", "fill -2 161 -2 24 173 26 minecraft:air",
                    "setblock 6 161 7 minecraft:chest[facing=south]", "setblock 10 161 7 minecraft:budding_amethyst",
                    "summon minecraft:villager 13.5 161 7.5 {NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,Invulnerable:1b}",
                    "summon minecraft:husk 10.5 161 3.5 {NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,Invulnerable:1b}",
                    "scoreboard objectives remove stream", "scoreboard objectives add stream dummy \"Native alias fixture\"",
                    "scoreboard objectives setdisplay sidebar stream", "scoreboard players set AnalyzerDev stream 7",
                    "tp AnalyzerDev 10.5 161 19.5 180 11");
            var source = server.getCommandSource().withSilent();
            for (String command : commands) server.getCommandManager().executeWithPrefix(source, command);
            serverPrepared = true;
            AnalyzerClient.LOGGER.info("ANALYZER_TRACER_PRIVACY_ARENA_READY: real received chest/amethyst/villager/husk and own scoreboard entry");
        });
    }

    @SuppressWarnings("unchecked")
    private static List<ChatHudLine> storedChat(MinecraftClient mc) { return (List<ChatHudLine>) chatField(mc, "messages"); }
    @SuppressWarnings("unchecked")
    private static List<ChatHudLine.Visible> visibleChat(MinecraftClient mc) { return (List<ChatHudLine.Visible>) chatField(mc, "visibleMessages"); }
    private static Object chatField(MinecraftClient mc, String name) {
        try {
            var field = mc.inGameHud.getChatHud().getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(mc.inGameHud.getChatHud());
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Development-only chat evidence field unavailable: " + name, failure); }
    }
    private static String ordered(OrderedText value) {
        StringBuilder result = new StringBuilder();
        value.accept((index, style, codePoint) -> { result.appendCodePoint(codePoint); return true; });
        return result.toString();
    }
    private static String join(List<OrderedText> lines) { return String.join("", lines.stream().map(TracerPrivacySmoke::ordered).toList()); }
    private static void command(MinecraftClient mc, String command) {
        mc.getServer().execute(() -> mc.getServer().getCommandManager().executeWithPrefix(mc.getServer().getCommandSource().withSilent(), command));
    }
    private static void capture(MinecraftClient mc, String name) {
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Clean GL state before native capture: " + name);
        CAPTURES.add(name);
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, name, mc.getFramebuffer(), message -> {
            written++;
            AnalyzerClient.LOGGER.info("Tracer/privacy native written: {} / {}", name, message.getString());
        });
    }
    private static boolean allFilesExist(MinecraftClient mc) {
        try {
            for (String name : CAPTURES) {
                var path = mc.runDirectory.toPath().resolve("screenshots").resolve(name);
                if (!Files.isRegularFile(path) || Files.size(path) < 1024) return false;
            }
            return CAPTURES.size() == 3;
        } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot verify native tracer/name photographs", failure); }
    }
    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new IllegalStateException("Tracer/privacy native acceptance: " + message);
        AnalyzerClient.LOGGER.info("Tracer/privacy native check: {}", message);
    }
}
