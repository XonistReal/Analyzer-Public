package dev.analyzer;

import dev.analyzer.automation.AutomationEngine;
import dev.analyzer.core.Category;
import dev.analyzer.render.XrayPolicy;
import dev.analyzer.render.XrayRenderer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Developer-only actual textured terrain, opacity and chunk/resource restoration acceptance. */
final class NativeXraySmoke {
    private enum Stage { SETUP, NORMAL, FADE, OUTLINES, ORES, FILTER, UTILITIES_OFF, RESTORE, RELOAD, VERIFY_RELOAD, FINISHED }
    private static final List<Block> ORES = List.of(Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE, Blocks.GOLD_ORE, Blocks.IRON_ORE, Blocks.COPPER_ORE, Blocks.LAPIS_ORE, Blocks.REDSTONE_ORE,
            Blocks.COAL_ORE, Blocks.NETHER_QUARTZ_ORE, Blocks.ANCIENT_DEBRIS, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.DEEPSLATE_LAPIS_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.NETHER_GOLD_ORE);
    private static final BlockPos CAMERA = new BlockPos(330, 170, 327);
    private static final List<Block> UTILITIES = List.of(Blocks.CRAFTING_TABLE, Blocks.ENCHANTING_TABLE, Blocks.ANVIL, Blocks.CHEST, Blocks.ENDER_CHEST, Blocks.BARREL, Blocks.RED_SHULKER_BOX, Blocks.HOPPER, Blocks.FURNACE, Blocks.BREWING_STAND, Blocks.JUKEBOX, Blocks.BEACON);
    private static Stage stage = Stage.SETUP;
    private static int entered, currentTick, checks, captures;
    private static boolean initialized;
    private static volatile boolean ready;
    private static volatile String failure = "";
    private static CompletableFuture<Void> reload;
    private static long reloadBefore;
    private NativeXraySmoke() { }
    static boolean done() {return stage == Stage.FINISHED;}
    static int assertions() {return checks;}
    static int captures() {return captures;}
    static void tick(MinecraftClient mc, int tick) {
        if (done()) return;
        if (!FabricLoader.getInstance().isDevelopmentEnvironment() || !Boolean.getBoolean("analyzer.smoke") || !Boolean.getBoolean("analyzer.nativeAutomationSmoke")) throw new IllegalStateException("X-ray fixture is development-only");
        if (mc.player == null || mc.world == null || mc.getServer() == null) return;
        currentTick = tick;
        if (!initialized) {initialized = true; entered = tick; setup(mc);}
        if (!failure.isEmpty()) throw new IllegalStateException("X-ray server fixture: " + failure);
        if (age() > 600) throw new IllegalStateException("X-ray acceptance timed out at " + stage + ": " + XrayRenderer.diagnostics());
        switch (stage) {
            case SETUP -> {
                if (!ready || age() < 30 || mc.player.squaredDistanceTo(Vec3d.ofBottomCenter(CAMERA)) > .1 || !mc.world.isChunkLoaded(new BlockPos(330, 172, 335))) return;
                require(!AnalyzerClient.CORE.isEnabled("xray") && !XrayRenderer.active(), "X-ray is opt-in and ordinary terrain is initially enabled");
                require(mc.world.getBlockState(new BlockPos(330, 171, 332)).isOf(Blocks.STONE), "Actual opaque stone conceals the ore sample wall");
                for (int i = 0; i < ORES.size(); i++) require(mc.world.getBlockState(ore(i)).isOf(ORES.get(i)), "The loaded world contains the real ore block state " + i);
                enter(Stage.NORMAL);
            }
            case NORMAL -> {
                if (age() < 40) return;
                capture(mc, "normal-before"); style("Fade terrain"); enter(Stage.FADE);
            }
            case FADE -> {
                var proof = XrayRenderer.diagnostics();
                if (age() < 70 || proof.oreVertices() == 0 || proof.terrainVertices() == 0 || proof.glowBlocks() == 0) return;
                require(proof.enabled() && proof.style().equals("FADE") && proof.terrainAlpha() == 38, "Fade uses actual terrain model vertex alpha 38/255, approximately fifteen percent");
                require(proof.oreModels() > 0 && proof.oreVertices() > 0 && proof.badOreAlpha() == 0, "Actual selected ore block models retain their textured fully opaque vertices");
                require(proof.terrainModels() > 0 && proof.terrainVertices() > 0 && proof.badTerrainAlpha() == 0, "Ordinary textured terrain emits faded native vertices rather than opaque squares");
                require(proof.glowBlocks() >= ORES.size() && proof.wireBlocks() == 0, "Fade adds a subtle bounded ore fringe without terrain wireframes");
                capture(mc, "fade-terrain"); style("Terrain outlines"); enter(Stage.OUTLINES);
            }
            case OUTLINES -> {
                var proof = XrayRenderer.diagnostics();
                if (age() < 70 || proof.oreVertices() == 0 || proof.hiddenModels() == 0 || proof.wireBlocks() == 0) return;
                require(proof.style().equals("OUTLINES") && proof.oreVertices() > 0 && proof.badOreAlpha() == 0, "Terrain outlines retains actual opaque ore textures");
                require(proof.hiddenModels() > 0 && proof.wireBlocks() > 0 && proof.glowBlocks() == 0, "Ordinary filled terrain is replaced with small native line outlines");
                capture(mc, "terrain-outlines"); style("Ores only"); enter(Stage.ORES);
            }
            case ORES -> {
                var proof = XrayRenderer.diagnostics();
                if (age() < 65 || proof.oreVertices() == 0 || proof.hiddenModels() == 0 || proof.blockEntities() == 0 || proof.entities() == 0) return;
                require(proof.style().equals("ORES") && proof.oreVertices() > 0 && proof.badOreAlpha() == 0, "Ore-only style emits the real ore model geometry and original texture UVs");
                require(proof.hiddenModels() > 0 && proof.wireBlocks() == 0 && proof.glowBlocks() == 0, "Ore-only style has neither ordinary terrain fill nor terrain outlines");
                for (var block : UTILITIES) require(XrayRenderer.selected(block.getDefaultState()) && !XrayRenderer.hidden(block.getDefaultState()), "Requested storage/workstation remains visible with its real model: " + block);
                require(proof.blockEntities() > 0 && proof.entities() > 0, "Special container models reach vanilla block-entity rendering and real entities receive through-terrain highlights");
                capture(mc, "ores-only"); reloadBefore = proof.reloads(); AnalyzerClient.CORE.setValue("xray", "diamond", false); enter(Stage.FILTER);
            }
            case FILTER -> {
                if (age() < 50 || XrayRenderer.diagnostics().reloads() <= reloadBefore) return;
                require(!XrayRenderer.selected(Blocks.DIAMOND_ORE.getDefaultState()) && !XrayRenderer.selected(Blocks.DEEPSLATE_DIAMOND_ORE.getDefaultState()) && XrayRenderer.selected(Blocks.GOLD_ORE.getDefaultState()), "A live ore toggle removes both diamond variants and preserves other ore groups");
                require(mc.world.getBlockState(ore(0)).isOf(Blocks.DIAMOND_ORE), "X-ray settings never change the actual world block state");
                AnalyzerClient.CORE.setValue("xray", "diamond", true);
                for (String group : new String[]{"workstations", "storage", "entities"}) AnalyzerClient.CORE.setValue("xray", group, false);
                reloadBefore = XrayRenderer.diagnostics().reloads(); XrayRenderer.resetDiagnostics(); enter(Stage.UTILITIES_OFF);
            }
            case UTILITIES_OFF -> {
                if (age() < 60 || XrayRenderer.diagnostics().reloads() <= reloadBefore) return;
                for (var block : UTILITIES) require(!XrayRenderer.selected(block.getDefaultState()), "Live utility filters actually remove the selected group: " + block);
                require(XrayRenderer.diagnostics().blockEntities() == 0 && XrayRenderer.diagnostics().entities() == 0, "Utility block-entity rendering and entity highlights respond to their live toggles");
                for (String group : new String[]{"workstations", "storage", "entities"}) AnalyzerClient.CORE.setValue("xray", group, true);
                reloadBefore = XrayRenderer.diagnostics().reloads(); AnalyzerClient.CORE.setEnabled("xray", false); enter(Stage.RESTORE);
            }
            case RESTORE -> {
                if (age() < 65 || XrayRenderer.diagnostics().reloads() <= reloadBefore) return;
                require(!XrayRenderer.active() && !XrayRenderer.hidden(Blocks.STONE.getDefaultState()) && !XrayRenderer.fadeTerrain(Blocks.STONE.getDefaultState()), "Disabling restores the original stone block rendering path");
                require(XrayRenderer.diagnostics().wireBlocks() == 0 && XrayRenderer.diagnostics().glowBlocks() == 0, "Disabling clears every auxiliary line and glow surface");
                capture(mc, "normal-restored"); style("Fade terrain"); enter(Stage.RELOAD);
            }
            case RELOAD -> {
                if (age() < 50 || XrayRenderer.diagnostics().oreVertices() == 0) return;
                reloadBefore = XrayRenderer.diagnostics().reloads(); reload = mc.reloadResources(); enter(Stage.VERIFY_RELOAD);
            }
            case VERIFY_RELOAD -> {
                if (reload == null || !reload.isDone() || mc.getOverlay() != null || age() < 80 || XrayRenderer.diagnostics().reloads() <= reloadBefore) return;
                reload.join(); require(XrayRenderer.active() && XrayRenderer.diagnostics().oreVertices() > 0, "Actual resource reload rebuilds X-ray terrain models using the refreshed native texture atlas");
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "Native terrain and detail passes restore a clean GL state after reload");
                capture(mc, "resource-reloaded"); AnalyzerClient.CORE.setEnabled("xray", false); stage = Stage.FINISHED;
                AnalyzerClient.LOGGER.info("ANALYZER_NATIVE_XRAY_COMPLETE: {} assertions, {} captures; real textured ore models, terrain alpha38/255, native thin terrain edges, ore-only world pass, live ore filter and disable/resource restoration", checks, captures);
            }
            case FINISHED -> { }
        }
    }
    private static void setup(MinecraftClient mc) {
        AutomationEngine.INSTANCE.stop(); mc.setScreen(null);
        for (var module : AnalyzerClient.CORE.modules()) if (module.category() == Category.VISUAL || module.category() == Category.COSMETICS || module.category() == Category.COMBAT || module.category() == Category.HVH || module.category() == Category.MOVEMENT || module.category() == Category.PLAYER) AnalyzerClient.CORE.setEnabled(module.id(), false);
        AnalyzerClient.CORE.setEnabled("sky", false); AnalyzerClient.CORE.setEnabled("fullbright", false);
        AnalyzerClient.CORE.setValue("xray", "range", 16d); AnalyzerClient.CORE.setValue("xray", "glow", true);
        AnalyzerClient.CORE.setValue("xray", "storage", true); AnalyzerClient.CORE.setValue("xray", "workstations", true); AnalyzerClient.CORE.setValue("xray", "entities", true);
        for (String group : XrayPolicy.GROUPS) AnalyzerClient.CORE.setValue("xray", group, true);
        mc.getServer().execute(() -> {
            try {
                var world = mc.getServer().getOverworld();
                for (int x = 320; x <= 342; x += 5) for (int z = 320; z <= 344; z += 5) world.getChunk(new BlockPos(x, 170, z));
                for (BlockPos pos : BlockPos.iterate(new BlockPos(320, 169, 320), new BlockPos(342, 180, 344))) world.setBlockState(pos, Blocks.AIR.getDefaultState());
                for (BlockPos pos : BlockPos.iterate(new BlockPos(322, 169, 322), new BlockPos(338, 169, 341))) world.setBlockState(pos, Blocks.STONE.getDefaultState());
                for (BlockPos pos : BlockPos.iterate(new BlockPos(324, 170, 332), new BlockPos(336, 175, 338))) world.setBlockState(pos, Blocks.STONE.getDefaultState());
                for (int i = 0; i < ORES.size(); i++) world.setBlockState(ore(i), ORES.get(i).getDefaultState());
                for (int i = 0; i < UTILITIES.size(); i++) world.setBlockState(new BlockPos(325 + i, 173, 335), UTILITIES.get(i).getDefaultState());
                var source = mc.getServer().getCommandSource().withLevel(4).withSilent();
                mc.getServer().getCommandManager().executeWithPrefix(source, "tp AnalyzerDev 330.5 170 327.5 0 7");
                mc.getServer().getCommandManager().executeWithPrefix(source, "summon minecraft:pig 330.5 170 337.5 {NoAI:1b,PersistenceRequired:1b}");
                mc.getServer().getCommandManager().executeWithPrefix(source, "time set 6000"); ready = true;
            } catch (RuntimeException error) {failure = error.toString();}
        });
    }
    private static BlockPos ore(int index) {return new BlockPos(327 + index % 7, 170 + index / 7, 335);}
    private static void style(String value) {XrayRenderer.resetDiagnostics(); AnalyzerClient.CORE.setValue("xray", "style", value); AnalyzerClient.CORE.setEnabled("xray", true);}
    private static int age() {return currentTick - entered;}
    private static void enter(Stage value) {stage = value; entered = currentTick + 1;}
    private static void capture(MinecraftClient mc, String name) {require(GL11.glGetError() == GL11.GL_NO_ERROR, "Clean actual GL state before " + name); mc.inGameHud.getChatHud().clear(false); mc.getToastManager().clear(); ScreenshotRecorder.saveScreenshot(mc.runDirectory, "analyzer-native-xray-" + name + ".png", mc.getFramebuffer(), text -> AnalyzerClient.LOGGER.info("X-ray capture: {}", text.getString())); captures++;}
    private static void require(boolean value, String message) {if (!value) throw new IllegalStateException("Native X-ray assertion: " + message); checks++;}
}
