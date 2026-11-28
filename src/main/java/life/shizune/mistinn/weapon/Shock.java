package life.shizune.mistinn.weapon;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 電擊: a jolt through the body. The hand clenches and opens: whatever it held flies out of it and lands a little way
 * off (a great foe's grip holds). The jolted one is slowed a moment.
 */
public final class Shock {
	/** How long the jolted one is slowed, and how long a weapon thrown from a hunter's hand lies before it can be caught up again. */
	public static final int SLOWED = 30;
	public static final int PICKUP_DELAY = 30;

	private Shock() {
	}

	/** A jolt through one: sparks, a crack, the weapon out of the hand, a moment's stiffness. */
	public static void jolt(final ServerLevel level, final LivingEntity living) {
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, living.getX(), living.getY(0.6), living.getZ(), 30, 0.35, 0.6, 0.35, 0.15);
		level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.NEUTRAL, 0.5F, 1.8F);
		living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOWED, 2));
		disarm(level, living);
	}

	/** The jolted hand lets go: the main hand's weapon flies out and lands a little way off. False when nothing was let go. */
	public static boolean disarm(final ServerLevel level, final LivingEntity living) {
		ItemStack held = living.getMainHandItem();
		if (held.isEmpty() || ParryService.great(living)) {
			return false;
		}
		ItemStack thrown = held.copy();
		living.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		Vec3 look = living.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
		Vec3 fling = (look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize()).scale(0.25).add(0.0, 0.3, 0.0);
		ItemEntity item = new ItemEntity(level, living.getX(), living.getEyeY() - 0.3, living.getZ(), thrown, fling.x, fling.y, fling.z);
		item.setPickUpDelay(PICKUP_DELAY);
		level.addFreshEntity(item);
		if (living instanceof Mob mob) {
			// a creature does not pick its weapon up again of itself
			mob.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		}
		if (living instanceof ServerPlayer player) {
			player.getInventory().setChanged();
		}
		return true;
	}
}
