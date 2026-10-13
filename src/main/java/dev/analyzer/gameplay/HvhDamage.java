package dev.analyzer.gameplay;

import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.explosion.ExplosionImpl;

/** Vanilla loaded-world estimate. Unknown data fails closed; self ignores protective enchantments/effects. */
final class HvhDamage {
    private HvhDamage() { }
    static double estimate(World world, LivingEntity entity, Vec3d origin, float power, BlockPos removedAnchor, boolean self) {
        try {
            double distance = entity.getPos().distanceTo(origin);
            if (distance > power * 2) return 0;
            if (!loaded(world, entity.getBoundingBox(), origin)) return Double.NaN;
            double exposure = removedAnchor == null ? ExplosionImpl.calculateReceivedDamage(origin, entity)
                    : anchorExposure(world, entity, origin, removedAnchor);
            double raw = HvhPolicy.rawExplosionDamage(distance, power, exposure);
            if (!Double.isFinite(raw)) return Double.NaN;
            raw = switch (world.getDifficulty()) {
                case PEACEFUL -> entity instanceof net.minecraft.entity.player.PlayerEntity ? 0 : raw;
                case EASY -> entity instanceof net.minecraft.entity.player.PlayerEntity ? Math.min(raw / 2 + 1, raw) : raw;
                case HARD -> entity instanceof net.minecraft.entity.player.PlayerEntity ? raw * 1.5 : raw;
                default -> raw;
            };
            var source = world.getDamageSources().explosion(null, null);
            float damage = DamageUtil.getDamageLeft(entity, (float) raw, source, entity.getArmor(),
                    (float) entity.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS));
            if (self) return damage; // upper bound: never assumes resistance or protection will save us.
            int protection = 0;
            for (var armor : entity.getArmorItems()) {
                var enchantments = EnchantmentHelper.getEnchantments(armor);
                for (var enchantment : enchantments.getEnchantments()) {
                    var key = enchantment.getKey();
                    if (key.isEmpty() || !key.get().getValue().getNamespace().equals("minecraft")) return Double.NaN;
                    int level = enchantments.getLevel(enchantment);
                    if (enchantment.matchesKey(Enchantments.PROTECTION)) protection += level;
                    if (enchantment.matchesKey(Enchantments.BLAST_PROTECTION)) protection += level * 2;
                }
            }
            var resistance = entity.getStatusEffect(StatusEffects.RESISTANCE);
            if (resistance != null) damage *= Math.max(0, 1 - .2f * (resistance.getAmplifier() + 1));
            return DamageUtil.getInflictedDamage(damage, protection);
        } catch (RuntimeException invalidData) { return Double.NaN; }
    }
    private static boolean loaded(World world, net.minecraft.util.math.Box box, Vec3d end) {
        int x1 = (int) Math.floor(Math.min(box.minX, end.x)) >> 4;
        int x2 = (int) Math.floor(Math.max(box.maxX, end.x)) >> 4;
        int z1 = (int) Math.floor(Math.min(box.minZ, end.z)) >> 4;
        int z2 = (int) Math.floor(Math.max(box.maxZ, end.z)) >> 4;
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++)
            if (world.getChunkManager().getChunk(x, z, net.minecraft.world.chunk.ChunkStatus.FULL, false) == null) return false;
        return true;
    }
    /** Same density as vanilla exposure, excluding only the anchor that disappears at detonation. */
    private static double anchorExposure(World world, LivingEntity entity, Vec3d origin, BlockPos anchor) {
        var box = entity.getBoundingBox();
        double sx = 1 / (box.getLengthX() * 2 + 1), sy = 1 / (box.getLengthY() * 2 + 1), sz = 1 / (box.getLengthZ() * 2 + 1);
        double ox = (1 - Math.floor(1 / sx) * sx) / 2, oz = (1 - Math.floor(1 / sz) * sz) / 2;
        int visible = 0, total = 0;
        if (sx <= 0 || sy <= 0 || sz <= 0) return Double.NaN;
        for (double x = 0; x <= 1; x += sx) for (double y = 0; y <= 1; y += sy) for (double z = 0; z <= 1; z += sz) {
            if (++total > 512) return Double.NaN;
            Vec3d point = new Vec3d(box.minX + box.getLengthX() * x + ox, box.minY + box.getLengthY() * y, box.minZ + box.getLengthZ() * z + oz);
            var hit = world.raycast(new RaycastContext(point, origin, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity));
            if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(anchor)) visible++;
        }
        return total == 0 ? Double.NaN : visible / (double) total;
    }
}
