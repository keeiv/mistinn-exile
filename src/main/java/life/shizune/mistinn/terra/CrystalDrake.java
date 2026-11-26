package life.shizune.mistinn.terra;

import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.weapon.WeaponStyle;
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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 晶龍: a wyvern of living crystal, with no flesh and no blood, that keeps the skies of a Terra
 * highland. It circles high, and when it has a quarry it wheels over it and stoops: down in a
 * swept-wing dive, a bite in passing, and up again to circle for the next. Left alone a while it
 * comes down to rest near where it first took the air, and rises again when someone comes.
 * <p>
 * Lifeless: poison and withering do nothing to it, nor does a fall. Shot and stab glance off its
 * facets; a greatsword or a hammer cracks them. It dies in a burst of crystal.
 */
public class CrystalDrake extends Monster {
	private static final EntityDataAccessor<Boolean> DATA_ALOFT = SynchedEntityData.defineId(CrystalDrake.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_SWOOP = SynchedEntityData.defineId(CrystalDrake.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_BITE = SynchedEntityData.defineId(CrystalDrake.class, EntityDataSerializers.INT);

	/** How it moves at a circle and in a dive, in blocks a tick. */
	public static final double CIRCLE_SPEED = 0.55;
	public static final double SWOOP_SPEED = 0.95;
	/** How long between dives, at the least and the most. */
	public static final int DIVE_MIN = 60;
	public static final int DIVE_MAX = 110;
	/** How long it circles alone before coming down to rest, and how long it rests. */
	public static final int RESTLESS = 400;
	/** For tests: how long it circles alone before coming down. */
	public static int restless = RESTLESS;
	public static final int REST = 400;
	/** Damage it takes from shots and stabs, and from heavy blows. */
	public static final float GLANCE = 0.7F;
	public static final float CRACK = 1.3F;
	private static final int BITE_SHOWN = 10;

	/** What it is doing. */
	public enum Mode {
		CIRCLE, SWOOP, LAND, REST
	}

	private Mode mode = Mode.CIRCLE;
	private @Nullable BlockPos home;
	private double angle;
	private double radius = 14.0;
	private boolean clockwise;
	private int timer = 40;
	private int alone;
	private Vec3 aim = Vec3.ZERO;

	private float aloftO;
	private float aloft;
	private float swoopO;
	private float swoop;
	private float biteO;
	private float bite;

	public CrystalDrake(final EntityType<? extends CrystalDrake> type, final Level level) {
		super(type, level);
		this.xpReward = 14;
		this.setNoGravity(true);
		this.clockwise = this.random.nextBoolean();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 40.0)
			.add(Attributes.ARMOR, 6.0)
			.add(Attributes.ATTACK_DAMAGE, 7.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FLYING_SPEED, 0.6)
			.add(Attributes.FOLLOW_RANGE, 48.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ALOFT, true);
		builder.define(DATA_SWOOP, false);
		builder.define(DATA_BITE, 0);
	}

	@Override
	protected void registerGoals() {
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	// ── Flight ────────────────────────────────────────────────────

	public boolean isAloft() {
		return this.entityData.get(DATA_ALOFT);
	}

	public Mode mode() {
		return this.mode;
	}

	private void setMode(final Mode next) {
		this.mode = next;
		this.entityData.set(DATA_SWOOP, next == Mode.SWOOP);
		boolean up = next != Mode.REST;
		this.entityData.set(DATA_ALOFT, up);
		this.setNoGravity(up);
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		if (this.home == null) {
			this.home = this.blockPosition();
		}
		LivingEntity target = this.getTarget();
		if (target != null && (!target.isAlive() || target.isSpectator() || target instanceof Player player && player.isCreative())) {
			this.setTarget(null);
			target = null;
		}
		this.alone = target == null ? this.alone + 1 : 0;
		this.timer--;
		switch (this.mode) {
			case CIRCLE -> {
				Vec3 anchor = target != null ? target.position().add(0.0, 9.0, 0.0) : Vec3.atCenterOf(this.home).add(0.0, 14.0, 0.0);
				this.angle += (this.clockwise ? 1.0 : -1.0) * CIRCLE_SPEED / this.radius;
				this.aim = anchor.add(Math.cos(this.angle) * this.radius, Math.sin(this.tickCount * 0.05) * 2.0, Math.sin(this.angle) * this.radius);
				if (target != null && this.timer <= 0 && this.hasLineOfSight(target)) {
					this.setMode(Mode.SWOOP);
					this.timer = 100;
					this.playSound(SoundEvents.PHANTOM_SWOOP, 2.0F, 1.4F);
				} else if (target == null && this.alone > restless) {
					this.setMode(Mode.LAND);
					BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.home);
					this.aim = Vec3.atBottomCenterOf(ground);
				}
			}
			case SWOOP -> {
				if (target == null || this.timer <= 0) {
					this.climb();
				} else {
					this.aim = target.getEyePosition();
					if (this.getBoundingBox().inflate(0.8).intersects(target.getBoundingBox())) {
						this.doHurtTarget(level, target);
						this.entityData.set(DATA_BITE, BITE_SHOWN);
						this.climb();
					}
				}
			}
			case LAND -> {
				if (target != null) {
					this.setMode(Mode.CIRCLE);
				} else if (this.onGround() || this.position().distanceTo(this.aim) < 1.5) {
					this.setMode(Mode.REST);
					this.timer = REST + this.random.nextInt(REST);
				}
			}
			case REST -> {
				if (target != null || this.timer <= 0 || this.getLastHurtByMob() != null && this.tickCount - this.getLastHurtByMobTimestamp() < 20) {
					this.alone = 0;
					this.setMode(Mode.CIRCLE);
					this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.6, 0.0));
					this.playSound(SoundEvents.ENDER_DRAGON_FLAP, 1.2F, 1.6F);
				}
			}
		}
		if (this.mode != Mode.REST) {
			this.steer(this.mode == Mode.SWOOP ? SWOOP_SPEED : this.mode == Mode.LAND ? 0.3 : CIRCLE_SPEED);
		}
	}

	/** Pulls up out of a dive and goes back to circling, a while before the next. */
	private void climb() {
		this.setMode(Mode.CIRCLE);
		this.timer = DIVE_MIN + this.random.nextInt(DIVE_MAX - DIVE_MIN);
		this.radius = 10.0 + this.random.nextDouble() * 8.0;
		this.angle = Math.atan2(this.getZ() - this.aim.z, this.getX() - this.aim.x);
		this.setDeltaMovement(this.getDeltaMovement().multiply(0.6, 0.0, 0.6).add(0.0, 0.5, 0.0));
	}

	/** Turns its course towards where it is going, as a flyer does, not all at once. */
	private void steer(final double speed) {
		Vec3 to = this.aim.subtract(this.position());
		if (to.lengthSqr() < 0.01) {
			return;
		}
		Vec3 want = to.normalize().scale(speed);
		Vec3 now = this.getDeltaMovement().scale(0.86).add(want.scale(0.14));
		this.setDeltaMovement(now);
		if (now.horizontalDistanceSqr() > 1.0E-4) {
			float yaw = (float) (Mth.atan2(now.z, now.x) * Mth.RAD_TO_DEG) - 90.0F;
			this.setYRot(Mth.approachDegrees(this.getYRot(), yaw, 12.0F));
			this.yBodyRot = this.getYRot();
			this.yHeadRot = this.getYRot();
		}
	}

	@Override
	public void tick() {
		super.tick();
		this.aloftO = this.aloft;
		this.swoopO = this.swoop;
		this.biteO = this.bite;
		this.aloft = Mth.approach(this.aloft, this.isAloft() ? 1.0F : 0.0F, 0.08F);
		this.swoop = Mth.approach(this.swoop, this.entityData.get(DATA_SWOOP) ? 1.0F : 0.0F, 0.12F);
		int biting = this.entityData.get(DATA_BITE);
		this.bite = biting > 0 ? Math.min(1.0F, this.bite + 0.35F) : Math.max(0.0F, this.bite - 0.15F);
		if (!this.level().isClientSide() && biting > 0) {
			this.entityData.set(DATA_BITE, biting - 1);
		}
		if (this.level().isClientSide() && this.isAloft() && this.tickCount % 19 == 0) {
			// a wingbeat, heard
			this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 0.5F,
				1.5F + this.random.nextFloat() * 0.2F, false);
		}
	}

	public float aloft(final float partial) {
		return Mth.lerp(partial, this.aloftO, this.aloft);
	}

	public float swoop(final float partial) {
		return Mth.lerp(partial, this.swoopO, this.swoop);
	}

	public float bite(final float partial) {
		return Mth.lerp(partial, this.biteO, this.bite);
	}

	// ── Lifeless ──────────────────────────────────────────────────

	@Override
	public boolean canBeAffected(final MobEffectInstance effect) {
		return !effect.is(MobEffects.POISON) && !effect.is(MobEffects.WITHER) && super.canBeAffected(effect);
	}

	@Override
	public boolean causeFallDamage(final double distance, final float multiplier, final DamageSource source) {
		return false;
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
		return super.hurtServer(level, source, amount * facets(source));
	}

	/** How its facets take a blow: shots and spear thrusts glance off, greatswords and hammers crack them. */
	public static float facets(final DamageSource source) {
		if (source.is(DamageTypeTags.IS_PROJECTILE)) {
			return GLANCE;
		}
		ItemStack weapon = source.getWeaponItem();
		WeaponStyle style = weapon == null ? null : WeaponStyle.of(weapon);
		if (style == WeaponStyle.CLEAVE || style == WeaponStyle.SMASH) {
			return CRACK;
		}
		return style == WeaponStyle.THRUST ? GLANCE : 1.0F;
	}

	@Override
	public void die(final DamageSource source) {
		super.die(source);
		if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
			life.shizune.mistinn.achievement.AchievementService.grant(player, life.shizune.mistinn.achievement.AchievementService.DRAKE_SHATTERED);
		}
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, MistinnBlocks.LATTICE_CRYSTAL.defaultBlockState()),
				this.getX(), this.getY() + 1.0, this.getZ(), 80, 1.2, 0.8, 1.2, 0.2);
			level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.0, this.getZ(), 20, 0.8, 0.6, 0.8, 0.08);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0F, 0.6F);
		}
	}

	// ── Spawning ──────────────────────────────────────────────────

	/** Where one may come of itself: under the open sky, and not where a few already keep the air. */
	public static boolean checkSpawnRules(final EntityType<CrystalDrake> type, final ServerLevelAccessor level, final EntitySpawnReason reason, final BlockPos pos,
		final RandomSource random) {
		if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
			return true;
		}
		return level.canSeeSky(pos) && level.getEntitiesOfClass(CrystalDrake.class, new AABB(pos).inflate(64.0)).size() < 3 && random.nextInt(4) == 0;
	}

	// ── The voice ─────────────────────────────────────────────────

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return SoundEvents.AMETHYST_BLOCK_CHIME;
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return SoundEvents.AMETHYST_BLOCK_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.AMETHYST_CLUSTER_BREAK;
	}

	@Override
	public float getVoicePitch() {
		return 0.7F + this.random.nextFloat() * 0.2F;
	}

	@Override
	protected float getSoundVolume() {
		return 2.0F;
	}

	// ── Saving ────────────────────────────────────────────────────

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.home != null) {
			output.putLong("home", this.home.asLong());
		}
		output.putBoolean("resting", this.mode == Mode.REST);
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		long home = input.getLongOr("home", Long.MIN_VALUE);
		this.home = home == Long.MIN_VALUE ? null : BlockPos.of(home);
		this.setMode(input.getBooleanOr("resting", false) ? Mode.REST : Mode.CIRCLE);
	}
}
