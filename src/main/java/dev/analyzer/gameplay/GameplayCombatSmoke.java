package dev.analyzer.gameplay;

import com.mojang.authlib.GameProfile;
import dev.analyzer.AnalyzerClient;
import dev.analyzer.render.HvhPreview;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;
import java.util.UUID;

/** Actual-world checks called only by the opt-in development settings fixture. No explosions are triggered. */
public final class GameplayCombatSmoke {
    private static int stage, entered, tick, assertions;
    private static long before, previewBefore;
    private static float yawBefore, pitchBefore, healthBefore;
    private static double exposed;
    private static LivingEntity victim;
    private static final Vec3d ORIGIN = new Vec3d(17.5, 162.5, 13.5);
    private GameplayCombatSmoke() { }

    public static boolean tick(MinecraftClient mc, int t) {
        tick = t;
        if (t > 220) throw new IllegalStateException("Combat settings fixture timed out at stage " + stage);
        int age = t - entered;
        var core = AnalyzerClient.CORE;
        switch (stage) {
            case 0 -> {
                core.resetAll(); mc.setScreen(null); mc.player.getInventory().selectedSlot = 0;
                command(mc, "kill @e[type=!minecraft:player]");
                command(mc, "fill 10 161 10 22 169 22 minecraft:air");
                command(mc, "gamemode survival AnalyzerDev");
                command(mc, "tp AnalyzerDev 15.5 161 15.5 90 0");
                command(mc, "item replace entity AnalyzerDev hotbar.0 with minecraft:diamond_sword");
                command(mc, "summon minecraft:zombie 15.5 161 13.1 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"analyzer_gameplay_target\"]}");
                core.setValue("aim-assist", "players", false); core.setValue("aim-assist", "hostiles", false);
                core.setValue("aim-assist", "hold-attack", false); core.setValue("aim-assist", "fov", 360D);
                core.setValue("aim-assist", "turn-speed", 5D); core.setEnabled("aim-assist", true);
                before = GameplayEngine.actions("aim-assist"); next();
            }
            case 1 -> { if (age >= 8) {
                victim = zombie(mc); require(victim != null, "Server-spawned stationary combat fixture reaches the actual client");
                require(GameplayEngine.actions("aim-assist") == before && GameplayEngine.target() == null, "Disabled target categories reject actual hostile acquisition");
                yawBefore = mc.player.getYaw(); core.setValue("aim-assist", "hostiles", true); next();
            }}
            case 2 -> { if (age >= 5) {
                double turn = Math.abs(MathHelper.wrapDegrees(mc.player.getYaw() - yawBefore));
                require(GameplayEngine.target() == victim && GameplayEngine.actions("aim-assist") > before, "Enabled hostile filter acquires the loaded fixture");
                require(turn > 5 && turn <= age * 5 + .01, "Actual camera rotation obeys configured per-tick turn limit");
                core.setEnabled("aim-assist", false);
                core.setValue("kill-aura", "players", false); core.setValue("kill-aura", "hostiles", true);
                core.setValue("kill-aura", "range", 8D); core.setValue("kill-aura", "fov", 360D);
                core.setValue("kill-aura", "rotation", "None"); core.setValue("kill-aura", "hit-angle", 180D);
                core.setValue("kill-aura", "weapon-only", true);
                command(mc, "tp @e[tag=analyzer_gameplay_target,limit=1] 15.5 161 10.5");
                before = GameplayEngine.actions("kill-aura"); next();
            }}
            case 3 -> {
                if (age == 4) { require(mc.player.distanceTo(victim) > 4, "Distant fixture synchronizes before enabling attacks"); core.setEnabled("kill-aura", true); }
                if (age >= 8) {
                require(GameplayEngine.target() == victim && mc.player.distanceTo(victim) > 4, "Extra configured range acquires a real target beyond melee reach");
                require(GameplayEngine.actions("kill-aura") == before, "Acquisition range never extends ordinary attack reach");
                mc.player.resetLastAttackedTicks();
                command(mc, "tp @e[tag=analyzer_gameplay_target,limit=1] 15.5 161 13.1");
                healthBefore = victim.getHealth(); next();
            }}
            case 4 -> { if (age >= 2) {
                require(GameplayEngine.actions("kill-aura") == before, "Actual uncharged weapon blocks an otherwise reachable attack"); next();
            }}
            case 5 -> {
                if (GameplayEngine.actions("kill-aura") > before) {
                    require(GameplayEngine.actions("kill-aura") == before + 1, "Charged reachable target receives one ordinary attack");
                    core.setEnabled("kill-aura", false); next();
                } else if (age > 35) throw new IllegalStateException("Charged attack unavailable: " + GameplayEngine.status("kill-aura"));
            }
            case 6 -> { if (age >= 6) {
                require(victim.isAlive() && victim.getHealth() < healthBefore, "Integrated server confirms damage from the ordinary attack");
                exposed = HvhDamage.estimate(mc.world, victim, ORIGIN, 6, null, false);
                require(Double.isFinite(exposed) && exposed > 1, "Loaded-world explosion estimate includes actual exposure and armor data");
                var armor = victim.getAttributeInstance(EntityAttributes.ARMOR); double original = armor.getBaseValue();
                try {
                    armor.setBaseValue(original + 18);
                    double armored = HvhDamage.estimate(mc.world, victim, ORIGIN, 6, null, false);
                    require(Double.isFinite(armored) && armored < exposed, "Actual armor attribute lowers the computed damage");
                } finally { armor.setBaseValue(original); }
                victim.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 0));
                double enemy = HvhDamage.estimate(mc.world, victim, ORIGIN, 6, null, false);
                double conservative = HvhDamage.estimate(mc.world, victim, ORIGIN, 6, null, true);
                require(Double.isFinite(enemy) && conservative > enemy, "Conservative self policy omits real protective resistance");
                victim.removeStatusEffect(StatusEffects.RESISTANCE);
                var unloaded = new OtherClientPlayerEntity(mc.world, new GameProfile(UUID.fromString("f468707b-6de2-4eea-82e1-0b1c46c56315"), "UnloadedFixture"));
                unloaded.refreshPositionAndAngles(1_000_000, 161, 1_000_000, 0, 0);
                require(Double.isNaN(HvhDamage.estimate(mc.world, unloaded, unloaded.getPos().add(1, 1, 0), 6, null, true)), "Missing chunks fail closed before exposure raycasts");
                command(mc, "fill 16 161 10 16 165 17 minecraft:obsidian"); next();
            }}
            case 7 -> { if (age >= 8) {
                double shielded = HvhDamage.estimate(mc.world, victim, ORIGIN, 6, null, false);
                require(Double.isFinite(shielded) && shielded < exposed, "Actual loaded obstruction reduces explosion exposure");
                command(mc, "fill 16 161 10 16 165 17 minecraft:air");
                command(mc, "setblock 17 162 13 minecraft:respawn_anchor[charges=1]"); next();
            }}
            case 8 -> { if (age >= 8) {
                double blocked = HvhDamage.estimate(mc.world, victim, ORIGIN, 5, null, false);
                double removed = HvhDamage.estimate(mc.world, victim, ORIGIN, 5, new BlockPos(17, 162, 13), false);
                require(Double.isFinite(removed) && removed > blocked, "Anchor estimate excludes the exact block removed during detonation");
                command(mc, "setblock 17 162 13 minecraft:air");
                core.setValue("surround", "center", false); core.setValue("surround", "off-jump", false);
                core.setValue("surround", "off-moved", false); core.setValue("surround", "red", 180D);
                previewBefore = HvhPreview.frames(); before = GameplayEngine.actions("surround");
                core.setEnabled("surround", true); next();
            }}
            case 9 -> { if (age >= 8) {
                require(HvhPreview.frames() > previewBefore, "Pending placement preview renders through its registered world hook");
                require(GameplayEngine.actions("surround") == before, "Preview without building material does not place a block");
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "Preview rendering restores clean GL state");
                core.resetAll();
                core.setValue("kill-aura", "players", false); core.setValue("kill-aura", "hostiles", true);
                core.setValue("kill-aura", "range", 8D); core.setValue("kill-aura", "fov", 360D);
                core.setValue("kill-aura", "rotation", "None"); core.setValue("kill-aura", "hit-angle", 180D);
                core.setValue("criticals", "mode", "Manual fall"); core.setValue("criticals", "hold-time", 4D);
                core.setValue("target-strafe", "players", false); core.setValue("target-strafe", "hostiles", true);
                core.setValue("target-strafe", "fov", 360D); core.setValue("target-strafe", "sprint", true);
                core.setEnabled("kill-aura", true); core.setEnabled("criticals", true); core.setEnabled("target-strafe", true);
                mc.options.sprintKey.setPressed(false); mc.player.setSprinting(true); next();
            }}
            case 10 -> { if (age >= 2) {
                require(CombatController.suppressSprint(), "Real critical wait owns sprint release while awaiting manual falling motion");
                require(!mc.options.sprintKey.isPressed(), "Target Strafe cannot reassert sprint during the critical wait");
                command(mc, "tp @e[tag=analyzer_gameplay_target,limit=1] 15.5 161 10.5"); next();
            }}
            case 11 -> { if (age >= 8) {
                require(!CombatController.suppressSprint(), "Leaving melee reach clears critical ownership despite aura acquisition remaining active");
                core.resetAll(); command(mc, "kill @e[tag=analyzer_gameplay_target]");
                command(mc, "tp AnalyzerDev 15.5 161 15.5 0 0");
                command(mc, "item replace entity AnalyzerDev hotbar.0 with minecraft:obsidian 64"); next();
            }}
            case 12 -> { if (age >= 8) {
                require(mc.player.getMainHandStack().isOf(Items.OBSIDIAN) && mc.player.isOnGround(), "Placement turn fixture has actual building material on supported ground");
                core.setValue("surround", "rotate", true); core.setValue("surround", "aim-speed", 5D);
                core.setValue("surround", "aim-smoothing", 0D); core.setValue("surround", "center", false);
                core.setValue("surround", "off-jump", false); core.setValue("surround", "off-moved", false);
                core.setValue("surround", "delay", 1D); core.setValue("surround", "blocks-per-tick", 8D);
                yawBefore = mc.player.getYaw(); pitchBefore = mc.player.getPitch(); before = GameplayEngine.actions("surround");
                core.setEnabled("surround", true); next();
            }}
            case 13 -> { if (age >= 1) {
                double yaw = Math.abs(MathHelper.wrapDegrees(mc.player.getYaw() - yawBefore)), pitch = Math.abs(mc.player.getPitch() - pitchBefore);
                require(yaw + pitch > 1 && yaw <= 5.01 && pitch <= 5.01, "Multiple placement candidates share one configured camera turn per tick");
                require(GameplayEngine.actions("surround") == before, "Placement waits for the camera to converge before interacting");
                core.resetAll();
                AnalyzerClient.LOGGER.info("ANALYZER_GAMEPLAY_COMBAT_SMOKE_COMPLETE: {} assertions, {} ticks; real filters/rotation, reach and charge gates, server-confirmed melee, terrain/armor/resistance/anchor estimates, and bounded preview rendering", assertions, t);
                stage++; return true;
            }}
            default -> { return true; }
        }
        return false;
    }
    private static LivingEntity zombie(MinecraftClient mc) {
        for (var entity : mc.world.getEntities()) if (entity instanceof ZombieEntity living && living.isAlive()) return living;
        return null;
    }
    private static void command(MinecraftClient mc, String text) {
        mc.getServer().execute(() -> mc.getServer().getCommandManager().executeWithPrefix(mc.getServer().getCommandSource().withSilent(), text));
    }
    private static void next() { stage++; entered = tick; }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Gameplay combat smoke: " + message);
        assertions++; AnalyzerClient.LOGGER.info("ANALYZER_GAMEPLAY_COMBAT_ASSERT: {}", message);
    }
}
