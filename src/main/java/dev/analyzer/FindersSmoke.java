package dev.analyzer;

import com.mojang.authlib.GameProfile;
import dev.analyzer.core.BlockFinderModules;
import dev.analyzer.core.EntityFinderModules;
import dev.analyzer.core.ModuleDefinition;
import dev.analyzer.core.SurveyFinderModules;
import dev.analyzer.finder.FinderEngine;
import dev.analyzer.render.EntityFinderRenderer;
import dev.analyzer.ui.FinderHistoryScreen;
import dev.analyzer.ui.MenuScreens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
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

/** Read-only finder acceptance in an isolated, command-built development world. */
public final class FindersSmoke {
    private static final String WORLD = "Analyzer-Finders-Smoke";
    private static final int SCOUT_ID = 991383;
    private static final List<String> BLOCK_CHECKS = List.of("amethyst-finder", "beehive-esp", "block-notifier",
            "covered-hole-esp", "deepslate-esp", "dripstone-esp", "hole-tunnel-stairs-esp", "light-esp",
            "one-by-one-holes", "piston-esp", "rotated-deepslate-esp", "spawner-notifier", "sweet-berry-esp",
            "vine-esp", "advanced-stash-finder", "chunk-finder", "kelp-esp");
    private static final List<String> ENTITY_CHECKS = EntityFinderModules.definitions().stream().map(ModuleDefinition::id).toList();
    private static final List<String> BLOCK_PHOTO = List.of("amethyst-finder", "beehive-esp", "piston-esp",
            "rotated-deepslate-esp", "dripstone-esp", "spawner-notifier", "sweet-berry-esp", "vine-esp");
    private static final List<String> CAPTURES = new ArrayList<>();
    private static final Set<String> POSITIVES = new LinkedHashSet<>();
    private static boolean requested, setupRequested, ready, finished, disconnected;
    private static volatile boolean serverPrepared;
    private static volatile int written;
    private static int ticks, sceneTicks, phase, phaseTicks, assertions, disconnectTicks;
    private static int observerRequests, receivedObserverChanges;
    private static Boolean lastObserverPower;
    private static String lastScreen = "";

    private FindersSmoke() { }

    public static void tick(MinecraftClient mc) {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment() || !Boolean.getBoolean("analyzer.smoke")
                || !Boolean.getBoolean("analyzer.findersSmoke")
                || !mc.runDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().equals("run-finders-smoke"))
            throw new IllegalStateException("Finder native fixture requires the isolated run-finders-smoke development launch");
        if (finished) return;
        if (++ticks > 6000) throw new IllegalStateException("Finder native fixture timed out; phase=" + phase + ", missing=" + missingBlocks());
        if (disconnected) {
            if (++disconnectTicks < 12) return;
            for (String id : BLOCK_CHECKS) require(FinderEngine.count(id) == 0, "World reset clears block observations: " + id);
            for (String id : ENTITY_CHECKS) require(EntityFinderRenderer.count(id) == 0, "World reset clears entity observations: " + id);
            require(EntityFinderRenderer.boneCount() == 0, "World reset clears captured pose segments");
            require(written == 5 && allFilesExist(mc), "All five native PNG callbacks and files completed");
            finished = true;
            AnalyzerClient.LOGGER.info("ANALYZER_FINDERS_COMPLETE: {} checks; {} native-positive module IDs {}; 5 unedited framebuffer photos; {} observer property updates sent, {} actual client changes received during full surveys; held-trident negative; family toggles; actual biped poses; local stash history and native Findings screen; received-block removal; disable and world reset; client-local invisible Scout fixture",
                    assertions, POSITIVES.size(), POSITIVES, observerRequests, receivedObserverChanges);
            mc.scheduleStop();
            return;
        }
        if (!requested && mc.world == null) {
            String screen = mc.currentScreen == null ? "null" : mc.currentScreen.getClass().getSimpleName();
            if (!screen.equals(lastScreen) || ticks % 200 == 0) {
                lastScreen = screen;
                AnalyzerClient.LOGGER.info("Finder native startup: screen={}, overlay={}", screen,
                        mc.getOverlay() == null ? "null" : mc.getOverlay().getClass().getSimpleName());
            }
            if (mc.getOverlay() == null && screen.equals("AccessibilityOnboardingScreen")) {
                mc.setScreen(new TitleScreen());
                return;
            }
        }
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
            if (Files.exists(mc.runDirectory.toPath().resolve("saves/" + WORLD + "/level.dat")))
                mc.createIntegratedServerLoader().start(WORLD, () -> mc.scheduleStop());
            else mc.createIntegratedServerLoader().createAndStart(WORLD,
                    new LevelInfo(WORLD, GameMode.CREATIVE, false, Difficulty.NORMAL, true,
                            new GameRules(FeatureFlags.VANILLA_FEATURES), DataConfiguration.SAFE_MODE),
                    new GeneratorOptions(762831L, false, false), WorldPresets::createDemoOptions, new TitleScreen());
        }
        if (!requested || mc.world == null || mc.player == null || mc.getServer() == null || mc.getOverlay() != null) return;
        if (!setupRequested) { setupRequested = true; setup(mc); return; }
        if (!ready) {
            if (!serverPrepared || mc.player.getBlockY() != 161
                    || !mc.world.getBlockState(new BlockPos(23, 160, 41)).isOf(Blocks.POLISHED_DEEPSLATE)
                    || !mc.world.getBlockState(new BlockPos(2, 161, 4)).isOf(Blocks.BUDDING_AMETHYST)) return;
            ready = true;
            mc.setScreen(null);
            mc.inGameHud.getChatHud().clear(false);
            mc.getToastManager().clear();
            var scout = new OtherClientPlayerEntity(mc.world, new GameProfile(
                    UUID.fromString("14a0b216-2f91-4ca1-9fd3-4354eb2857bc"), "Scout"));
            scout.setId(SCOUT_ID);
            scout.refreshPositionAndAngles(23.5, 161, 23.5, 150, -12);
            scout.setInvisible(true);
            scout.setHealth(20);
            mc.world.addEntity(scout);
            configure(mc);
        }
        sceneTicks++;
        phaseTicks++;
        switch (phase) {
            case 0 -> {
                if (sceneTicks < 100 || !entitiesReady()) return;
                for (String id : ENTITY_CHECKS) positive(id, EntityFinderRenderer.count(id));
                require(EntityFinderRenderer.count("drowned-trident-esp") == 1,
                        "Only the actual held-trident drowned matches; the empty-handed drowned is excluded");
                require(EntityFinderRenderer.count("villager-esp") == 1, "Zombie villagers are excluded by default");
                require(EntityFinderRenderer.count("llama-esp") == 2, "Ordinary and trader llamas are independently received");
                require(EntityFinderRenderer.boneCount() >= 16, "At least two actual posed bipeds produce real limb/head segments");
                capture(mc, "analyzer-native-finders-entities.png");
                AnalyzerClient.CORE.setValue("villager-esp", "zombie-villagers", true);
                AnalyzerClient.CORE.setValue("llama-esp", "trader-llamas", false);
                next();
            }
            case 1 -> {
                if (phaseTicks < 20 || EntityFinderRenderer.count("villager-esp") != 2 || EntityFinderRenderer.count("llama-esp") != 1) return;
                require(EntityFinderRenderer.count("villager-esp") == 2, "Live zombie-villager opt-in adds the received zombie villager");
                require(EntityFinderRenderer.count("llama-esp") == 1, "Live trader-llama opt-out removes only the trader llama");
                for (String id : ENTITY_CHECKS) AnalyzerClient.CORE.setEnabled(id, id.equals("skeleton-esp"));
                for (String id : BLOCK_CHECKS) AnalyzerClient.CORE.setEnabled(id, false);
                next();
            }
            case 2 -> {
                if (phaseTicks < 25) return;
                require(EntityFinderRenderer.count("skeleton-esp") >= 2 && EntityFinderRenderer.boneCount() >= 16,
                        "Skeleton remains independently usable after other entity finders are disabled");
                capture(mc, "analyzer-native-finders-skeleton.png");
                AnalyzerClient.CORE.setEnabled("skeleton-esp", false);
                for (String id : BLOCK_CHECKS) AnalyzerClient.CORE.setEnabled(id, true);
                command(mc, "tp AnalyzerDev 23.5 164 30.5 180 27");
                next();
            }
            case 3 -> {
                updateObserverFixture(mc);
                List<String> missing = missingBlocks();
                if (phaseTicks < 50 || !missing.isEmpty() || observerRequests < 10 || receivedObserverChanges < 10) {
                    if (phaseTicks % 200 == 0) AnalyzerClient.LOGGER.info("Finder native scan: missing={} status={} kelp={} observerRequests={} receivedChanges={}",
                            missing, FinderEngine.status("amethyst-finder"), FinderEngine.status("kelp-esp"), observerRequests, receivedObserverChanges);
                    return;
                }
                for (String id : BLOCK_CHECKS) positive(id, FinderEngine.count(id));
                require(observerRequests >= 10 && receivedObserverChanges >= 10,
                        "Full chunk and kelp surveys complete during actual repeated observer property changes: "
                                + observerRequests + " requests, " + receivedObserverChanges + " received transitions");
                require(!FinderEngine.history().isEmpty(), "Received storage cluster creates actual local stash history");
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "No native overlay GL errors after simultaneous finder passes");
                for (String id : BLOCK_CHECKS) AnalyzerClient.CORE.setEnabled(id, BLOCK_PHOTO.contains(id));
                next();
            }
            case 4 -> {
                if (phaseTicks < 25 || BLOCK_PHOTO.stream().anyMatch(id -> FinderEngine.count(id) < 1)) return;
                capture(mc, "analyzer-native-finders-blocks.png");
                for (String id : BLOCK_CHECKS) AnalyzerClient.CORE.setEnabled(id, id.equals("advanced-stash-finder"));
                AnalyzerClient.CORE.setEnabled("region-map", true);
                command(mc, "tp AnalyzerDev 9.5 163 20.5 180 22");
                next();
            }
            case 5 -> {
                if (phaseTicks < 40 || FinderEngine.count("advanced-stash-finder") == 0) return;
                require(!FinderEngine.history().isEmpty(), "Stash history survives independent overlay selection");
                capture(mc, "analyzer-native-finders-stash-map.png");
                mc.setScreen(new FinderHistoryScreen(AnalyzerClient.CORE, MenuScreens.create(AnalyzerClient.CORE)));
                next();
            }
            case 6 -> {
                if (phaseTicks < 25) return;
                require(mc.currentScreen instanceof FinderHistoryScreen && !FinderEngine.history().isEmpty(),
                        "Actual native Findings screen displays the current world's recorded storage observations");
                capture(mc, "analyzer-native-finders-history.png");
                mc.setScreen(null);
                AnalyzerClient.CORE.setEnabled("amethyst-finder", true);
                next();
            }
            case 7 -> {
                if (phaseTicks < 20 || FinderEngine.count("amethyst-finder") < 1) return;
                require(FinderEngine.count("amethyst-finder") >= 1, "Real budding-amethyst observation exists immediately before removal");
                command(mc, "setblock 2 161 4 minecraft:air");
                next();
            }
            case 8 -> {
                if (phaseTicks < 40 || mc.world.getBlockState(new BlockPos(2, 161, 4)).isOf(Blocks.BUDDING_AMETHYST)
                        || FinderEngine.count("amethyst-finder") != 0) return;
                require(FinderEngine.count("amethyst-finder") == 0, "Removed received budding amethyst no longer leaves a stale marker");
                for (var module : AnalyzerClient.CORE.modules()) AnalyzerClient.CORE.setEnabled(module.id(), false);
                next();
            }
            case 9 -> {
                if (phaseTicks < 12) return;
                for (String id : BLOCK_CHECKS) require(FinderEngine.count(id) == 0, "Disabling clears block markers: " + id);
                for (String id : ENTITY_CHECKS) require(EntityFinderRenderer.count(id) == 0, "Disabling clears entity targets: " + id);
                require(EntityFinderRenderer.boneCount() == 0, "Disabling skeleton clears all pose segments");
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "GL state remains clean after disabling all overlays");
                AnalyzerClient.CORE.setEnabled("piston-esp", true);
                AnalyzerClient.CORE.setEnabled("villager-esp", true);
                next();
            }
            case 10 -> {
                if (phaseTicks < 20 || FinderEngine.count("piston-esp") < 1 || EntityFinderRenderer.count("villager-esp") < 1
                        || written != 5 || !allFilesExist(mc)) return;
                require(FinderEngine.count("piston-esp") >= 1 && EntityFinderRenderer.count("villager-esp") >= 1,
                        "Real block and entity observations repopulate before the world-reset check");
                disconnected = true;
                // Match GameMenuScreen's normal singleplayer quit sequence: close the actual
                // play connection before MinecraftClient waits for its integrated server to stop.
                AnalyzerClient.LOGGER.info("Finder native fixture closes its world connection before native server shutdown");
                mc.world.disconnect();
                mc.disconnect(new MessageScreen(Text.translatable("menu.savingLevel")));
                mc.setScreen(new TitleScreen());
            }
            default -> throw new IllegalStateException("Unknown finder native phase " + phase);
        }
    }

    private static void configure(MinecraftClient mc) {
        var core = AnalyzerClient.CORE;
        List<ModuleDefinition> definitions = new ArrayList<>(BlockFinderModules.definitions());
        definitions.addAll(SurveyFinderModules.definitions());
        definitions.addAll(EntityFinderModules.definitions());
        for (var module : definitions) {
            core.setValue(module.id(), "range", 48);
            core.setValue(module.id(), "scan-budget", 16384);
            core.setValue(module.id(), "max-markers", 64);
            core.setValue(module.id(), "line-width", 1.5);
            core.setValue(module.id(), "through-walls", true);
            core.setValue(module.id(), "notify", false);
            // Keep the first two photos focused on entities; phase 2 starts the complete block survey.
            core.setEnabled(module.id(), ENTITY_CHECKS.contains(module.id()));
        }
        for (String id : List.of("amethyst-finder", "deepslate-esp")) {
            core.setValue(id, "min-y", 157); core.setValue(id, "max-y", 180);
        }
        core.setValue("block-notifier", "target", "Spawners");
        core.setValue("kelp-esp", "min-columns", 2);
        core.setValue("kelp-esp", "min-tall-columns", 2);
        core.setValue("kelp-esp", "min-length", 4);
        core.setValue("kelp-esp", "top-y", 164);
        core.setValue("kelp-esp", "sea-level-ratio", 100);
        core.setValue("skeleton-esp", "invisibles", true);
        core.setValue("skeleton-esp", "players", true);
        core.setValue("skeleton-esp", "hostiles", true);
        mc.options.sendClientSettings();
    }

    private static void setup(MinecraftClient mc) {
        mc.getServer().execute(() -> {
            var server = mc.getServer();
            for (int x = -1; x <= 3; x++) for (int z = -1; z <= 3; z++) server.getOverworld().getChunk(x, z);
            List<String> commands = new ArrayList<>(List.of(
                    "difficulty normal", "gamemode creative AnalyzerDev", "clear AnalyzerDev", "effect clear AnalyzerDev",
                    "gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobLoot false", "gamerule randomTickSpeed 0", "time set 1000", "weather clear",
                    "kill @e[type=!minecraft:player]",
                    // The floating arena sits above generated ocean terrain. Isolate this chunk's
                    // two authored kelp columns without removing its other blocks or weakening ratios.
                    // Each command examines no more than vanilla's 32,768-block fill limit.
                    "fill 32 -64 0 47 63 15 minecraft:air replace minecraft:kelp",
                    "fill 32 -64 0 47 63 15 minecraft:air replace minecraft:kelp_plant",
                    "fill 32 64 0 47 160 15 minecraft:air replace minecraft:kelp",
                    "fill 32 64 0 47 160 15 minecraft:air replace minecraft:kelp_plant",
                    "fill 0 160 0 47 160 47 minecraft:polished_deepslate",
                    "fill 0 161 0 47 173 47 minecraft:air",
                    "setblock 2 161 4 minecraft:budding_amethyst", "setblock 6 161 4 minecraft:beehive[honey_level=5,facing=south]",
                    "setblock 10 161 4 minecraft:deepslate[axis=x]", "setblock 14 161 4 minecraft:amethyst_block",
                    "setblock 18 161 4 minecraft:sticky_piston[facing=south]", "setblock 22 161 4 minecraft:spawner",
                    "setblock 21 161 4 minecraft:observer[facing=north,powered=false]",
                    "setblock 26 160 4 minecraft:dirt", "setblock 26 161 4 minecraft:sweet_berry_bush[age=3]",
                    "setblock 30 161 4 minecraft:torch", "setblock 42 161 4 minecraft:crafting_table",
                    "setblock 34 161 4 minecraft:pointed_dripstone[vertical_direction=up,thickness=base]",
                    "setblock 34 162 4 minecraft:pointed_dripstone[vertical_direction=up,thickness=middle]",
                    "setblock 34 163 4 minecraft:pointed_dripstone[vertical_direction=up,thickness=frustum]",
                    "setblock 34 164 4 minecraft:pointed_dripstone[vertical_direction=up,thickness=tip]",
                    "fill 40 161 4 40 164 4 minecraft:stone_bricks", "fill 39 161 4 39 163 4 minecraft:vine[east=true]",
                    "setblock 2 161 8 minecraft:chest[facing=south]", "setblock 4 161 8 minecraft:chest[facing=south]",
                    "setblock 6 161 8 minecraft:barrel[facing=south]", "setblock 8 161 8 minecraft:purple_shulker_box",
                    "setblock 10 161 8 minecraft:furnace[facing=south]", "setblock 12 161 8 minecraft:hopper",
                    "fill 35 160 7 39 165 11 minecraft:glass hollow", "fill 36 161 8 38 164 10 minecraft:water",
                    "fill 36 161 9 36 163 9 minecraft:kelp_plant", "setblock 36 164 9 minecraft:kelp[age=25]",
                    "fill 38 161 9 38 163 9 minecraft:kelp_plant", "setblock 38 164 9 minecraft:kelp[age=25]",
                    "fill 2 161 29 4 162 31 minecraft:stone_bricks", "setblock 3 161 30 minecraft:air",
                    "fill 7 161 29 9 164 31 minecraft:stone_bricks", "fill 8 161 30 8 163 30 minecraft:air",
                    "fill 12 161 29 19 163 31 minecraft:stone_bricks", "fill 13 161 30 18 162 30 minecraft:air",
                    "fill 3 161 13 7 163 13 minecraft:stone_bricks",
                    "summon minecraft:item_frame 4.5 162.5 14.5 {Facing:3b,Fixed:1b,Invulnerable:1b,Item:{id:\"minecraft:diamond\",count:1}}",
                    "summon minecraft:glow_item_frame 6.5 162.5 14.5 {Facing:3b,Fixed:1b,Invulnerable:1b,Item:{id:\"minecraft:amethyst_shard\",count:1}}",
                    "summon minecraft:armor_stand 9.5 161 14.5 {NoGravity:1b,Invulnerable:1b,ShowArms:1b}",
                    "summon minecraft:drowned 8.5 161 20.5 {NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,Invulnerable:1b,HandItems:[{id:\"minecraft:trident\",count:1},{}]}",
                    "summon minecraft:drowned 11.5 161 20.5 {NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,Invulnerable:1b,HandItems:[{},{}]}",
                    "summon minecraft:villager 14.5 161 20.5 {NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,Invulnerable:1b}",
                    "summon minecraft:zombie_villager 17.5 161 20.5 {NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,Invulnerable:1b}",
                    "summon minecraft:husk 20.5 161 23.5 {NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,Invulnerable:1b}",
                    "summon minecraft:llama 26.5 161 20.5 {NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,Invulnerable:1b}",
                    "summon minecraft:trader_llama 30.5 161 20.5 {NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,Invulnerable:1b}",
                    "summon minecraft:pillager 34.5 161 20.5 {NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,Invulnerable:1b}",
                    "summon minecraft:wandering_trader 38.5 161 20.5 {NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b,Invulnerable:1b,DespawnDelay:48000}",
                    "tp AnalyzerDev 23.5 161 41.5 180 10"));
            var source = server.getCommandSource().withSilent();
            for (String text : commands) server.getCommandManager().executeWithPrefix(source, text);
            serverPrepared = true;
            AnalyzerClient.LOGGER.info("ANALYZER_FINDERS_ARENA_READY: real block/entity fixtures built in isolated development world");
        });
    }

    private static void next() { phase++; phaseTicks = 0; }
    private static void updateObserverFixture(MinecraftClient mc) {
        var observed = mc.world.getBlockState(new BlockPos(21, 161, 4));
        if (observed.isOf(Blocks.OBSERVER)) {
            boolean powered = observed.get(Properties.POWERED);
            if (lastObserverPower != null && powered != lastObserverPower) receivedObserverChanges++;
            lastObserverPower = powered;
        }
        if (phaseTicks % 4 == 0) {
            boolean powered = (++observerRequests & 1) == 1;
            command(mc, "setblock 21 161 4 minecraft:observer[facing=north,powered=" + powered + "]");
        }
    }
    private static boolean entitiesReady() {
        for (String id : ENTITY_CHECKS) if (EntityFinderRenderer.count(id) < (id.equals("collectible-esp") ? 3 : id.equals("llama-esp") ? 2 : 1)) return false;
        return EntityFinderRenderer.boneCount() >= 16;
    }
    private static List<String> missingBlocks() {
        List<String> missing = new ArrayList<>();
        for (String id : BLOCK_CHECKS) if (FinderEngine.count(id) < 1) missing.add(id);
        return missing;
    }
    private static void positive(String id, int count) {
        require(count > 0, "Native received-state positive " + id + ": " + count);
        POSITIVES.add(id);
    }
    private static void command(MinecraftClient mc, String text) {
        mc.getServer().execute(() -> mc.getServer().getCommandManager().executeWithPrefix(mc.getServer().getCommandSource().withSilent(), text));
    }
    private static void capture(MinecraftClient mc, String name) {
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Clean GL state before native framebuffer " + name);
        CAPTURES.add(name);
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, name, mc.getFramebuffer(), result -> {
            written++;
            AnalyzerClient.LOGGER.info("Finder native written: {} / {}", name, result.getString());
        });
    }
    private static boolean allFilesExist(MinecraftClient mc) {
        try {
            for (String name : CAPTURES) {
                var path = mc.runDirectory.toPath().resolve("screenshots").resolve(name);
                if (!Files.isRegularFile(path) || Files.size(path) < 1024) return false;
            }
            return CAPTURES.size() == 5;
        } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot verify finder native photo files", failure); }
    }
    private static void require(boolean condition, String text) {
        assertions++;
        if (!condition) throw new IllegalStateException("Finder native fixture: " + text);
        AnalyzerClient.LOGGER.info("Finder native check: {}", text);
    }
}
