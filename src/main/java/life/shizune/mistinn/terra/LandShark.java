package life.shizune.mistinn.terra;

import java.util.EnumSet;
import life.shizune.mistinn.registry.MistinnBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 陸鯊: a shark that swims through the turf and soil of Terra's valleys as a shark swims through water; only its fin of
 * grown crystal shows, cutting a furrow. It leaves people alone. Struck, it hunts whoever struck it (and the others near
 * it with it): it closes under the ground and bursts up out of it in a leap, jaws first, and dives back in. Under the
 * ground it is hard to hurt: strike it as it leaps. Now and then, left alone, it leaps for nothing at all.
 */
public class LandShark extends PathfinderMob {
	private static final EntityDataAccessor<Integer> DATA_LEAP = SynchedEntityData.defineId(LandShark.class, EntityDataSerializers.INT);
	/** How long a leap shows it out of the ground, how long between two at its quarry, and from how far it leaps. */
	public static final int LEAP = 18;
	public static final int LEAP_EVERY = 60;
	public static final double LEAP_FROM = 6.0;
	/** What a blow comes to on it under the ground. */
	public static final float UNDER = 0.4F;
	/** How long it hunts after it was last struck. */
	public static final int TEMPER = 300;

	private int leapWait;
	private boolean bit;
	private float outO;
	private float out;

	public LandShark(final EntityType<? extends LandShark> type, final Level level) {
		super(type, level);
		this.xpReward = 5;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 24.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.ATTACK_DAMAGE, 6.0)
			.add(Attributes.FOLLOW_RANGE, 24.0)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_LEAP, 0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new HuntGoal());
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(LandShark.class));
	}

	/** Whether it is out of the ground, leaping, now. */
	public boolean leaping() {
		return this.entityData.get(DATA_LEAP) > 0;
	}

	/** 0 under the ground (only the fin out), 1 all out of it. */
	public float out(final float partial) {
		return Mth.lerp(partial, this.outO, this.out);
	}

	/** It bursts out of the ground toward a place, jaws first. */
	public void leap(final Vec3 toward) {
		Vec3 flat = toward.subtract(this.position()).multiply(1.0, 0.0, 1.0);
		Vec3 go = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 0.0) : flat.normalize().scale(Math.min(1.0, 0.25 + flat.length() * 0.12));
		this.setDeltaMovement(go.x, 0.62, go.z);
		this.setYRot((float) (Mth.atan2(go.z, go.x) * Mth.RAD_TO_DEG) - 90.0F);
		this.yBodyRot = this.getYRot();
		this.entityData.set(DATA_LEAP, LEAP);
		this.bit = false;
		if (this.level() instanceof ServerLevel level) {
			BlockState ground = level.getBlockState(this.blockPosition().below());
			if (!ground.isAir()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), this.getX(), this.getY() + 0.2, this.getZ(), 30, 0.6, 0.2, 0.6, 0.2);
			}
			level.playSound(null, this, SoundEvents.DOLPHIN_JUMP, SoundSource.NEUTRAL, 1.0F, 0.6F);
			level.playSound(null, this, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.NEUTRAL, 1.2F, 0.7F);
		}
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
		// under the ground only its fin can be reached
		boolean under = !this.leaping() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
		boolean hurt = super.hurtServer(level, source, under ? amount * UNDER : amount);
		// struck, it hunts its striker at once, and the others near it with it
		if (hurt && source.getEntity() instanceof LivingEntity striker && striker != this
			&& !(striker instanceof Player player && (player.isCreative() || player.isSpectator()))) {
			this.setTarget(striker);
			for (LandShark near : level.getEntitiesOfClass(LandShark.class, this.getBoundingBox().inflate(16.0), s -> s != this && s.getTarget() == null)) {
				near.setTarget(striker);
			}
		}
		return hurt;
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		int leap = this.entityData.get(DATA_LEAP);
		if (leap > 0) {
			this.entityData.set(DATA_LEAP, leap - 1);
			LivingEntity quarry = this.getTarget();
			if (!this.bit && quarry != null && this.getBoundingBox().inflate(0.4).intersects(quarry.getBoundingBox())) {
				this.bit = true;
				this.doHurtTarget(level, quarry);
			}
			if (leap < LEAP - 4 && this.onGround()) {
				this.entityData.set(DATA_LEAP, 0);
				BlockState ground = level.getBlockState(this.blockPosition().below());
				if (!ground.isAir()) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), this.getX(), this.getY() + 0.2, this.getZ(), 20, 0.5, 0.1, 0.5, 0.15);
				}
			}
		}
		if (this.leapWait > 0) {
			this.leapWait--;
		}
		if (this.getTarget() != null && this.tickCount - this.getLastHurtByMobTimestamp() > TEMPER) {
			this.setTarget(null);
		}
		// now and then, alone, it leaps for nothing at all
		if (this.getTarget() == null && !this.leaping() && this.onGround() && this.random.nextInt(900) == 0) {
			this.leap(this.position().add(this.getLookAngle().scale(4.0)));
		}
	}

	@Override
	public void tick() {
		super.tick();
		this.outO = this.out;
		this.out = Mth.approach(this.out, this.leaping() ? 1.0F : 0.0F, this.leaping() ? 0.35F : 0.12F);
		if (this.level().isClientSide() && !this.leaping() && this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-3) {
			// the furrow it cuts
			BlockState ground = this.level().getBlockState(this.blockPosition().below());
			if (!ground.isAir()) {
				this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), this.getX() + (this.random.nextFloat() - 0.5) * 0.6,
					this.getY() + 0.1, this.getZ() + (this.random.nextFloat() - 0.5) * 0.6, 0.0, 0.05, 0.0);
			}
		}
	}

	/** Its hunt: under the ground to its quarry, then out at it in a leap. */
	private final class HuntGoal extends Goal {
		HuntGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			LivingEntity quarry = LandShark.this.getTarget();
			return quarry != null && quarry.isAlive() && !(quarry instanceof Player player && (player.isCreative() || player.isSpectator()));
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LandShark self = LandShark.this;
			LivingEntity quarry = self.getTarget();
			if (quarry == null || self.leaping()) {
				return;
			}
			double far = self.distanceTo(quarry);
			if (far < LEAP_FROM && self.leapWait <= 0 && self.onGround() && self.hasLineOfSight(quarry)) {
				self.getNavigation().stop();
				self.leap(quarry.position());
				self.leapWait = LEAP_EVERY;
				return;
			}
			if (self.tickCount % 5 == 0 || self.getNavigation().isDone()) {
				self.getNavigation().moveTo(quarry, 1.35);
			}
		}
	}

	/** Where one may come of itself: in loose ground, turf or soil. */
	public static boolean checkSpawnRules(final EntityType<LandShark> type, final ServerLevelAccessor level, final EntitySpawnReason reason,
		final BlockPos pos, final RandomSource random) {
		BlockState below = level.getBlockState(pos.below());
		return below.is(MistinnBlocks.JADE_TURF) || below.is(MistinnBlocks.VEIN_SOIL) || below.is(BlockTags.DIRT);
	}

	@Override
	public boolean causeFallDamage(final double distance, final float multiplier, final DamageSource source) {
		// it dives back into the ground it leapt from
		return false;
	}

	// ── The voice ─────────────────────────────────────────────────

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return SoundEvents.DOLPHIN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.DOLPHIN_DEATH;
	}

	@Override
	protected void playStepSound(final BlockPos pos, final BlockState state) {
		this.playSound(SoundEvents.ROOTED_DIRT_STEP, 0.2F, 0.8F);
	}

	@Override
	public float getVoicePitch() {
		return 0.6F + this.random.nextFloat() * 0.1F;
	}
}
