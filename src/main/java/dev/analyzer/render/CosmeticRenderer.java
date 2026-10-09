package dev.analyzer.render;

import dev.analyzer.AnalyzerClient;
import dev.analyzer.core.AnalyzerCore;
import dev.analyzer.ui.Palette;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EndCrystalEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/** Pure render-state/model transforms and bounded original meshes. No game entity is changed. */
public final class CosmeticRenderer {
    private static AnalyzerCore core;
    private static boolean registered, emoteWas;
    private static long emoteStart;
    private static Object world;
    private static final Map<String, Long> FRAMES = new HashMap<>();
    private static final Identifier CRYSTAL_TEXTURE = Identifier.ofVanilla("textures/entity/end_crystal/end_crystal.png");
    private CosmeticRenderer() { }
    public static void register(AnalyzerCore analyzer) {
        core = analyzer;
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (world != client.world) { world = client.world; emoteWas = false; emoteStart = 0; }
            boolean enabled = on("emotes") && client.player != null;
            if (enabled && !emoteWas) emoteStart = client.player.age;
            if (enabled && ((!flag("emotes", "loop") && client.player.age - emoteStart >= number("emotes", "length") * 20)
                    || flag("emotes", "stop-moving") && (client.player.getVelocity().horizontalLengthSquared() > 0.0025 || !client.player.isOnGround() || client.player.isSneaking()))) {
                core.setEnabled("emotes", false); AnalyzerClient.saveConfig(); enabled = false;
            }
            emoteWas = enabled;
        });
    }
    public static boolean on(String id) { return core != null && core.isEnabled(id); }
    public static long frames(String id) { return FRAMES.getOrDefault(id, 0L); }
    private static void frame(String id) { FRAMES.merge(id, 1L, Long::sum); }
    public static String status(String id) {
        if (!on(id)) return "Disabled";
        if (MinecraftClient.getInstance().world == null) return "Join a world to see this cosmetic";
        return switch (id) {
            case "cape", "pets", "emotes", "walk-style", "spinbot" -> "Render-only / visible on third-person player models";
            case "hand-tweaks" -> "First-person transform / attacks unchanged";
            case "crystal-chams" -> "Actual crystal mesh / normal world depth";
            default -> "Render-only / game physics unchanged";
        };
    }
    private static double number(String id, String setting) { return core.getNumber(id, setting); }
    private static boolean flag(String id, String setting) { return core.getBoolean(id, setting); }
    private static String choice(String id, String setting) { return core.getChoice(id, setting); }
    public static boolean self(PlayerEntityRenderState state) { var p = MinecraftClient.getInstance().player; return p != null && state.id == p.getId(); }
    private static boolean wearer(PlayerEntityRenderState state) {
        String who = choice("cape", "who"); return who.equals("Both") || who.equals("Self") == self(state);
    }
    private static int color(String id, float age) {
        return switch (choice(id, "color")) {
            case "Mint" -> Palette.MINT;
            case "Violet" -> Palette.VIOLET;
            case "Gold" -> Palette.GOLD;
            case "Rainbow" -> 0xFF000000 | Color.HSBtoRGB((age / 180f) % 1, 0.58f, 1);
            case "Custom" -> 0xFF000000 | ((int) number(id, "red") << 16) | ((int) number(id, "green") << 8) | (int) number(id, "blue");
            case "Original" -> 0xFFFFFFFF;
            default -> switch (core.getChoice("menu", "accent")) { case "Violet" -> Palette.VIOLET; case "Gold" -> Palette.GOLD; default -> Palette.MINT; };
        };
    }

    public static void dropped(ItemEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertices, int light, Random random) {
        ItemCosmeticState physical = (ItemCosmeticState) state;
        matrices.push();
        try {
            float angle = physical.analyzer$angle(), phase = state.age * 0.13f * (float) number("item-physics", "tumble");
            boolean depth = state.itemRenderState.hasDepth();
            float ground = (depth ? 0.25f * state.itemRenderState.getTransformation().scale.y() : 0) + (float) number("item-physics", "ground-lift");
            matrices.translate(0, physical.analyzer$grounded() ? ground : 0.12f, 0);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(angle + (physical.analyzer$grounded() ? 0 : phase * 0.67f)));
            if (physical.analyzer$grounded()) { if (!depth) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90)); }
            else { matrices.multiply(RotationAxis.POSITIVE_X.rotation(phase + angle)); matrices.multiply(RotationAxis.POSITIVE_Z.rotation(phase * 0.53f)); }
            if (physical.analyzer$water() && flag("item-physics", "water-rock")) matrices.multiply(RotationAxis.POSITIVE_Z.rotation((float) Math.sin(state.age * 0.15f + angle) * (float) number("item-physics", "water-strength")));
            ItemEntityRenderer.renderStack(matrices, vertices, light, state, random); frame("item-physics");
        } finally { matrices.pop(); }
    }

    public static boolean hideHand(Hand hand) { return on("hand-tweaks") && (flag("hand-tweaks", "hide-hands") || hand == Hand.OFF_HAND && flag("hand-tweaks", "hide-offhand")); }
    public static float swing(float vanilla) {
        if (!on("hand-tweaks")) return vanilla;
        if (flag("hand-tweaks", "cancel-swing")) return 0;
        return flag("hand-tweaks", "hold-pose") ? (float) number("hand-tweaks", "swing-pose") : vanilla;
    }
    public static float equip(float vanilla) { return on("hand-tweaks") ? vanilla * (float) number("hand-tweaks", "equip-dip") : vanilla; }
    public static boolean cancelEat() { return on("hand-tweaks") && flag("hand-tweaks", "cancel-eat"); }
    public static void hand(MatrixStack matrices, Hand hand) {
        if (!on("hand-tweaks")) return;
        String side = hand == Hand.MAIN_HAND ? "main" : "off";
        matrices.translate(number("hand-tweaks", side + "-x"), number("hand-tweaks", side + "-y"), number("hand-tweaks", side + "-z"));
        float scale = (float) number("hand-tweaks", "hand-scale"); matrices.scale(scale, scale, scale); frame("hand-tweaks");
    }
    public static void heldItem(MatrixStack matrices, boolean main) {
        String side = main ? "main" : "off";
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) number("hand-tweaks", side + "-tilt")));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) number("hand-tweaks", side + "-turn")));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) number("hand-tweaks", side + "-roll")));
        float size = (float) number("hand-tweaks", side + "-size");
        matrices.scale(size * (float) number("hand-tweaks", "stretch-x"), size * (float) number("hand-tweaks", "stretch-y"), size * (float) number("hand-tweaks", "stretch-z"));
    }
    public static boolean customSwing(MatrixStack matrices, Arm arm, float progress) {
        if (!on("hand-tweaks") || choice("hand-tweaks", "swing").equals("Vanilla")) return false;
        float side = arm == Arm.RIGHT ? 1 : -1;
        float wave = MathHelper.sin(progress * (float) Math.PI), strength = (float) number("hand-tweaks", "swing-strength");
        switch (choice("hand-tweaks", "swing")) {
            case "Low" -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-18 * wave * strength));
            case "Smooth" -> { matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-side * 25 * wave * strength)); matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-35 * wave * strength)); }
            case "Stab" -> matrices.translate(0, 0, -0.65 * wave * strength);
            case "Slide" -> matrices.translate(side * -0.5 * wave * strength, 0, 0);
            case "Spin" -> matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * 360 * progress * strength));
            case "Tap" -> { matrices.translate(0, -0.2 * wave * strength, 0); matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(40 * wave * strength)); }
            default -> { return false; }
        }
        return true;
    }

    public static void updatePlayer(PlayerEntityRenderState state) {
        if (on("cape") && wearer(state) && flag("cape", "hide-vanilla") && (!flag("cape", "hide-elytra") || !state.equippedChestStack.isOf(Items.ELYTRA))) state.capeVisible = false;
        if (self(state) && on("spinbot")) { state.bodyYaw = state.age * (float) number("spinbot", "speed"); state.yawDegrees = 0; state.pitch = (float) number("spinbot", "pitch"); frame("spinbot"); }
    }
    public static void pose(PlayerEntityModel model, PlayerEntityRenderState state) {
        if (!self(state) || state.spectator || state.isGliding || state.isSwimming || state.hasVehicle || state.pose == EntityPose.SLEEPING) return;
        boolean altered = false;
        if (on("walk-style") && (!flag("walk-style", "only-moving") || state.limbAmplitudeMultiplier > 0.03f)) {
            float strength = (float) number("walk-style", "strength"), walk = MathHelper.sin(state.limbFrequency * 0.6662f) * strength;
            switch (choice("walk-style", "style")) {
                case "Swagger" -> { model.rightArm.pitch *= 1 + strength; model.leftArm.pitch *= 1 + strength; model.rightArm.roll = 0.18f * strength; model.leftArm.roll = -0.18f * strength; }
                case "Robot" -> { model.rightArm.pitch = model.leftArm.pitch = -1.1f * strength; model.rightLeg.pitch = walk * 0.55f; model.leftLeg.pitch = -walk * 0.55f; }
                case "Ninja" -> { model.body.pitch = 0.32f * strength; model.rightArm.pitch = model.leftArm.pitch = 1.25f * strength; model.rightArm.roll = 0.1f; model.leftArm.roll = -0.1f; }
                case "Zombie" -> { model.rightArm.pitch = model.leftArm.pitch = -1.4f * strength; model.rightArm.yaw = -0.15f; model.leftArm.yaw = 0.15f; }
                case "Bouncy" -> { model.rightLeg.pitch = walk * 1.5f; model.leftLeg.pitch = -walk * 1.5f; model.body.pivotY += Math.abs(walk) * 1.2f; }
                default -> { }
            }
            altered = true; frame("walk-style");
        }
        if (on("emotes") && emoteWas) {
            float phase = (state.age - emoteStart) * 0.23f, strength = (float) number("emotes", "strength");
            switch (choice("emotes", "emote")) {
                case "Wave" -> { model.rightArm.pitch = -2.5f * strength; model.rightArm.roll = (0.35f + MathHelper.sin(phase) * 0.35f) * strength; }
                case "Dance" -> { model.rightArm.roll = (1.1f + MathHelper.sin(phase) * 0.3f) * strength; model.leftArm.roll = (-1.1f + MathHelper.sin(phase) * 0.3f) * strength; model.body.roll = MathHelper.sin(phase) * 0.13f * strength; model.rightLeg.pitch = MathHelper.sin(phase) * 0.5f; model.leftLeg.pitch = -model.rightLeg.pitch; }
                case "Bow" -> { model.body.pitch = 0.8f * strength; model.head.pitch = 0.4f * strength; model.rightArm.pitch = model.leftArm.pitch = 0.35f; }
                case "Sit" -> { model.rightLeg.pitch = model.leftLeg.pitch = -1.5f * strength; model.rightLeg.yaw = 0.18f; model.leftLeg.yaw = -0.18f; model.rightArm.pitch = model.leftArm.pitch = -0.25f; }
                case "Cheer" -> { model.rightArm.pitch = model.leftArm.pitch = -2.8f * strength; model.rightArm.roll = 0.3f; model.leftArm.roll = -0.3f; model.head.pitch = -0.2f; }
                default -> { }
            }
            altered = true; frame("emotes");
        }
        if (altered) { model.rightSleeve.copyTransform(model.rightArm); model.leftSleeve.copyTransform(model.leftArm); model.rightPants.copyTransform(model.rightLeg); model.leftPants.copyTransform(model.leftLeg); model.jacket.copyTransform(model.body); model.hat.copyTransform(model.head); }
    }

    public static void crystalPose(EndCrystalEntityModel model, EndCrystalEntityRenderState state) {
        if (!on("crystal-chams")) return;
        model.base.visible = switch (choice("crystal-chams", "base")) { case "Always" -> true; case "Never" -> false; default -> state.baseVisible; };
        float spin = state.age * 3 * ((float) number("crystal-chams", "spin") - 1);
        model.outerGlass.rotate(RotationAxis.POSITIVE_Y.rotationDegrees(spin)); model.innerGlass.rotate(RotationAxis.POSITIVE_Y.rotationDegrees(spin)); model.cube.rotate(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
        model.outerGlass.pivotY += EndCrystalEntityRenderer.getYOffset(state.age) * 8 * ((float) number("crystal-chams", "bounce") - 1);
    }
    public static void crystal(EndCrystalEntityModel model, EndCrystalEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertices, VertexConsumer vanilla, int light, int overlay) {
        if (!on("crystal-chams")) { model.render(matrices, vanilla, light, overlay); return; }
        matrices.push();
        try {
            float size = (float) number("crystal-chams", "size"); matrices.scale(size, size, size);
            int tint = Palette.alpha(color("crystal-chams", state.age), (int) (number("crystal-chams", "opacity") * 255));
            String mode = choice("crystal-chams", "model");
            if (mode.equals("Vanilla")) model.render(matrices, vertices.getBuffer(RenderLayer.getEntityTranslucent(CRYSTAL_TEXTURE)), flag("crystal-chams", "glow") ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light, overlay, tint);
            else {
                VertexConsumer out = vertices.getBuffer(RenderLayer.getDebugQuads());
                if (model.base.visible) cube(out, matrices, -0.37f, 0, -0.37f, 0.37f, 0.25f, 0.37f, Palette.alpha(0xFF273747, tint >>> 24));
                matrices.push();
                try { matrices.translate(0, model.outerGlass.pivotY / 16f, 0); matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(state.age * 3 * (float) number("crystal-chams", "spin")));
                    if (mode.equals("Gem")) gem(out, matrices, 0.33f, 0.46f, tint);
                    else { cube(out, matrices, -0.27f, -0.4f, -0.27f, 0.27f, 0.4f, 0.27f, tint); ring(out, matrices, 0.39f, 0, 0.025f, tint); }
                } finally { matrices.pop(); }
            }
            if (flag("crystal-chams", "rings")) { matrices.push(); try { matrices.translate(0, model.outerGlass.pivotY / 16f, 0); matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(20)); ring(vertices.getBuffer(RenderLayer.getDebugQuads()), matrices, 0.48f, 0, 0.015f, tint); } finally { matrices.pop(); } }
            frame("crystal-chams");
        } finally { matrices.pop(); }
    }

    public static final class Accessories extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
        public Accessories(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) { super(context); }
        @Override public void render(MatrixStack matrices, VertexConsumerProvider vertices, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
            if (state.invisible || state.spectator) return;
            boolean cape = on("cape") && wearer(state) && (!flag("cape", "hide-elytra") || !state.equippedChestStack.isOf(Items.ELYTRA));
            boolean pet = on("pets") && self(state);
            if (!cape && !pet) return;
            matrices.push();
            try { getContextModel().body.rotate(matrices); VertexConsumer out = vertices.getBuffer(RenderLayer.getDebugQuads()); if (cape) cape(out, matrices, state); if (pet) pet(out, matrices, state); }
            finally { matrices.pop(); }
        }
    }
    private static void cape(VertexConsumer out, MatrixStack matrices, PlayerEntityRenderState state) {
        float width = 0.54f * (float) number("cape", "width"), length = 0.95f * (float) number("cape", "length");
        int tint = Palette.alpha(color("cape", state.age), (int) (number("cape", "opacity") * 255));
        String style = choice("cape", "style"), hem = choice("cape", "hem");
        for (int row = 0; row < 12; row++) for (int col = 0; col < 6; col++) {
            float x1 = (col / 6f - 0.5f) * width, x2 = ((col + 1) / 6f - 0.5f) * width;
            float y1 = row / 12f * length + 0.04f, y2 = (row + 1) / 12f * length + 0.04f;
            if (row == 11 && !hem.equals("Straight")) { float normalized = Math.abs((col + 0.5f) / 3f - 1); float inset = hem.equals("Pointed") ? normalized * 0.14f : (1 - normalized) * 0.14f; y2 -= inset * length; }
            int face = tint;
            if (style.equals("Gradient")) face = Palette.mix(tint, Palette.alpha(0xFF142337, tint >>> 24), row / 16.0);
            if (style.equals("Stripes") && col % 2 == 0 || style.equals("Trimmed") && (col == 0 || col == 5 || row == 11)) face = Palette.mix(tint, Palette.alpha(0xFFFFFFFF, tint >>> 24), 0.5);
            quad(out, matrices, x1, y1, clothZ(state, x1, y1, length), x2, y1, clothZ(state, x2, y1, length), x2, y2, clothZ(state, x2, y2, length), x1, y2, clothZ(state, x1, y2, length), face);
        }
        frame("cape");
    }
    private static float clothZ(PlayerEntityRenderState state, float x, float y, float length) {
        float t = y / length;
        return 0.17f + t * (0.04f + state.limbAmplitudeMultiplier * 0.16f * (float) number("cape", "flow"))
                + t * t * ((float) Math.sin(state.age * 0.12 + y * 5 + x * 2) * 0.065f * (float) number("cape", "ripple")
                + (float) Math.sin(state.age * 0.035 + x * 3) * 0.06f * (float) number("cape", "wind"));
    }
    private static void pet(VertexConsumer out, MatrixStack matrices, PlayerEntityRenderState state) {
        float phase = state.age * 0.06f * (float) number("pets", "speed");
        matrices.push();
        try {
            switch (choice("pets", "position")) {
                case "Orbit" -> matrices.translate(Math.sin(phase) * 0.75, -0.15 + Math.sin(phase * 1.7) * 0.07, Math.cos(phase) * 0.75);
                case "Behind" -> matrices.translate(0, 0.12 + Math.sin(phase) * 0.04, 0.65);
                default -> matrices.translate(-0.52, -0.15 + Math.sin(phase * 1.5) * 0.045, 0);
            }
            float size = (float) number("pets", "size"); matrices.scale(size, size, size);
            int tint = Palette.alpha(color("pets", state.age), (int) (number("pets", "opacity") * 255));
            switch (choice("pets", "pet")) {
                case "Rune Orb" -> { matrices.multiply(RotationAxis.POSITIVE_Y.rotation(phase)); gem(out, matrices, 0.13f, 0.16f, tint); ring(out, matrices, 0.19f, 0, 0.013f, tint); }
                case "Ghost" -> { cube(out, matrices, -0.12f, -0.13f, -0.08f, 0.12f, 0.08f, 0.08f, tint); for (int i = 0; i < 4; i++) cube(out, matrices, -0.12f + i * 0.06f, 0.08f, -0.08f, -0.066f + i * 0.06f, 0.14f + MathHelper.sin(phase * 2 + i) * 0.03f, 0.08f, tint); cube(out, matrices, -0.072f, -0.055f, -0.092f, -0.04f, -0.018f, -0.078f, 0xFF152232); cube(out, matrices, 0.04f, -0.055f, -0.092f, 0.072f, -0.018f, -0.078f, 0xFF152232); }
                case "Dragon" -> { cube(out, matrices, -0.09f, -0.08f, -0.1f, 0.09f, 0.08f, 0.16f, tint); cube(out, matrices, -0.09f, -0.13f, -0.23f, 0.09f, 0.01f, -0.1f, tint); cube(out, matrices, -0.035f, -0.03f, 0.16f, 0.035f, 0.03f, 0.33f, tint); for (int side : new int[]{-1, 1}) { float flap = MathHelper.sin(phase * 3) * 0.12f; quad(out, matrices, side * 0.08f, 0, -0.03f, side * 0.34f, flap, -0.1f, side * 0.28f, flap + 0.03f, 0.18f, side * 0.08f, 0.03f, 0.12f, tint); } }
                default -> { }
            }
            frame("pets");
        } finally { matrices.pop(); }
    }

    private static void cube(VertexConsumer out, MatrixStack matrices, float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        quad(out, matrices, x1,y1,z1, x2,y1,z1, x2,y2,z1, x1,y2,z1, color); quad(out, matrices, x2,y1,z2, x1,y1,z2, x1,y2,z2, x2,y2,z2, color);
        quad(out, matrices, x1,y1,z2, x1,y1,z1, x1,y2,z1, x1,y2,z2, color); quad(out, matrices, x2,y1,z1, x2,y1,z2, x2,y2,z2, x2,y2,z1, color);
        quad(out, matrices, x1,y1,z2, x2,y1,z2, x2,y1,z1, x1,y1,z1, color); quad(out, matrices, x1,y2,z1, x2,y2,z1, x2,y2,z2, x1,y2,z2, color);
    }
    private static void gem(VertexConsumer out, MatrixStack matrices, float radius, float height, int color) {
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4, b = (i + 1) * Math.PI / 4;
            float x1 = (float) Math.cos(a) * radius, z1 = (float) Math.sin(a) * radius, x2 = (float) Math.cos(b) * radius, z2 = (float) Math.sin(b) * radius;
            int face = Palette.mix(color, Palette.alpha(0xFF182A3A, color >>> 24), i % 2 == 0 ? 0 : 0.22);
            quad(out, matrices, 0,-height,0, x1,0,z1, x2,0,z2, x2,0,z2, face);
            quad(out, matrices, 0,height,0, x2,0,z2, x1,0,z1, x1,0,z1, face);
        }
    }
    private static void ring(VertexConsumer out, MatrixStack matrices, float radius, float y, float thickness, int color) {
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI / 24, b = (i + 1) * Math.PI / 24;
            float outer = radius + thickness, inner = radius - thickness;
            quad(out, matrices, (float)Math.cos(a)*outer,y,(float)Math.sin(a)*outer, (float)Math.cos(b)*outer,y,(float)Math.sin(b)*outer,
                    (float)Math.cos(b)*inner,y,(float)Math.sin(b)*inner, (float)Math.cos(a)*inner,y,(float)Math.sin(a)*inner, color);
        }
    }
    private static void quad(VertexConsumer out, MatrixStack matrices, float x1,float y1,float z1,float x2,float y2,float z2,float x3,float y3,float z3,float x4,float y4,float z4,int color) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        out.vertex(matrix,x1,y1,z1).color(color); out.vertex(matrix,x2,y2,z2).color(color); out.vertex(matrix,x3,y3,z3).color(color); out.vertex(matrix,x4,y4,z4).color(color);
    }
}
