package life.shizune.mistinn.shore;

import life.shizune.mistinn.registry.MistinnEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 霧影: the shape a lost hunter leaves in the fog, half there. It is only ever met inside a
 * lighthouse's mist; out of it, it thins away to nothing. It comes up out of the grey without a
 * footfall, quick and light, and one good blow undoes it; but its touch takes the hunter's sight
 * for a moment. In the lamp room's dark the Snuffer calls two up beside it, frail as breath.
 */
public class MistShade extends Zombie {
	/** How long it takes to come out of the mist, or to thin back into it. */
	public static final int FADE_TICKS = 20;
	/** How long its touch blinds. */
	public static final int BLIND_TICKS = 50;
	/** The Snuffer's shades last this long before they thin away. */
	private static final int CLONE_TICKS = 600;
	public static final String CLONE_TAG = "mistinn.snuffer_shade";

	private static final EntityDataAccessor<Integer> DATA_FADING = SynchedEntityData.defineId(MistShade.class, EntityDataSerializers.INT);

	public MistShade(final EntityType<? extends MistShade> type, final Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Zombie.createAttributes()
			.add(Attributes.MAX_HEALTH, 12.0)
			.add(Attributes.ARMOR, 0.0)
			.add(Attributes.ATTACK_DAMAGE, 2.0)
			.add(Attributes.MOVEMENT_SPEED, 0.29)
			.add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_FADING, -1);
	}

	/** Whether it is thinning away. */
	public boolean fading() {
		return this.entityData.get(DATA_FADING) >= 0;
	}

	/** How much of it is there to see, 0 to 1: it comes out of the mist, wavers, and thins back into it. */
	public float presence(final float partialTicks) {
		float age = this.tickCount + partialTicks;
		float in = Mth.clamp(age / FADE_TICKS, 0.0F, 1.0F);
		int since = this.entityData.get(DATA_FADING);
		float out = since < 0 ? 1.0F : 1.0F - Mth.clamp((age - since) / FADE_TICKS, 0.0F, 1.0F);
		float waver = 0.82F + 0.18F * Mth.sin(age * 0.23F + this.getId());
		return in * out * waver;
	}

	/** Its touch takes the sight for a moment. */
	@Override
	public boolean doHurtTarget(final ServerLevel level, final Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof LivingEntity living) {
			living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLIND_TICKS, 0, false, true), this);
		}
		return hit;
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		if (!this.fading()) {
			boolean clone = this.entityTags().contains(CLONE_TAG);
			// out of the mist there is nothing to hold it together; the keeper's own last only a while
			if (clone ? this.tickCount > CLONE_TICKS : this.tickCount % 20 == 0 && !ShoreSpawner.inMist(level, this.getX(), this.getZ())) {
				this.entityData.set(DATA_FADING, this.tickCount);
			}
		} else if (this.tickCount - this.entityData.get(DATA_FADING) >= FADE_TICKS) {
			level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 1.0, this.getZ(), 12, 0.3, 0.6, 0.3, 0.01);
			this.discard();
		}
	}

	/**
	 * 霧中分身: as its room goes dark, the keeper calls two shades out of the mist beside it, frail
	 * as breath (one blow each), each turned on the nearest hunter.
	 */
	public static void summonClones(final ServerLevel level, final Mob keeper) {
		for (int side = -1; side <= 1; side += 2) {
			MistShade shade = MistinnEntities.MIST_SHADE.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (shade == null) {
				return;
			}
			double angle = Math.toRadians(keeper.getYRot() + 90.0 * side);
			BlockPos at = BlockPos.containing(keeper.getX() - Math.sin(angle) * 2.5, keeper.getY(), keeper.getZ() + Math.cos(angle) * 2.5);
			shade.snapTo(at.getX() + 0.5, keeper.getY(), at.getZ() + 0.5, keeper.getYRot(), 0.0F);
			shade.addTag(CLONE_TAG);
			shade.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1.0);
			shade.setHealth(1.0F);
			Player near = level.getNearestPlayer(shade, 24.0);
			if (near != null && !near.isCreative() && !near.isSpectator()) {
				shade.setTarget(near);
			}
			level.addFreshEntity(shade);
			level.sendParticles(ParticleTypes.CLOUD, shade.getX(), shade.getY() + 1.0, shade.getZ(), 16, 0.3, 0.8, 0.3, 0.01);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SOUL_ESCAPE.value();
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return SoundEvents.SOUL_SAND_BREAK;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SOUL_ESCAPE.value();
	}

	/** It makes no sound walking. */
	@Override
	protected void playStepSound(final BlockPos pos, final BlockState state) {
	}

	@Override
	protected boolean isSunSensitive() {
		return false;
	}

	@Override
	protected boolean convertsInWater() {
		return false;
	}

	@Override
	public void setBaby(final boolean baby) {
		super.setBaby(false);
	}
}
