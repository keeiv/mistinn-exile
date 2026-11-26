package life.shizune.mistinn.rift;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 裂隙獵犬: a gaunt hound of scorched hide, lit from inside by the rift's light through every
 * crack, with shards of rift crystal along its spine. They hunt in packs: they ring their quarry,
 * wheeling round it, and the one that has come round to its side or its back crouches and lunges.
 * The one you face does not lunge; the one you do not see does. Stand close to one and it snaps.
 */
public class RiftHound extends Monster implements RiftTouched {
	private static final EntityDataAccessor<Boolean> DATA_CROUCH = SynchedEntityData.defineId(RiftHound.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_LUNGE = SynchedEntityData.defineId(RiftHound.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_BITE = SynchedEntityData.defineId(RiftHound.class, EntityDataSerializers.INT);

	/** How far off they ring their quarry, and from how far they come straight in. */
	public static final double RING = 5.0;
	public static final double NEAR = 14.0;
	/** How long between one hound's lunges, at the least and the most, and how long it crouches first. */
	public static final int LUNGE_MIN = 40;
	public static final int LUNGE_MAX = 90;
	public static final int CROUCH = 10;
	/** How far round from where its quarry looks a hound must be to lunge, in degrees. */
	public static final double FLANK = 55.0;
	/** How long a hound waits past its time, unseen or not, before it lunges anyway. */
	public static final int IMPATIENT = 80;
	private static final double LUNGE_SPEED = 1.0;
	private static final double LUNGE_UP = 0.36;
	private static final int BITE_SHOWN = 8;

	private float crouchO;
	private float crouch;
	private float lungeO;
	private float lunge;
	private float biteO;
	private float bite;

	public RiftHound(final EntityType<? extends RiftHound> type, final Level level) {
		super(type, level);
		this.xpReward = 7;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 20.0)
			.add(Attributes.ATTACK_DAMAGE, 4.0)
			.add(Attributes.MOVEMENT_SPEED, 0.34)
			.add(Attributes.FOLLOW_RANGE, 40.0)
			.add(Attributes.ARMOR, 2.0)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_CROUCH, false);
		builder.define(DATA_LUNGE, false);
		builder.define(DATA_BITE, 0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new PackHuntGoal());
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.9));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(RiftHound.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	public boolean isCrouching() {
		return this.entityData.get(DATA_CROUCH);
	}

	public boolean isLunging() {
		return this.entityData.get(DATA_LUNGE);
	}

	/** Whether {@code from} is round at the side or the back of {@code quarry}, out of where it looks. */
	public static boolean flanking(final LivingEntity quarry, final Vec3 from) {
		Vec3 look = quarry.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
		Vec3 toward = from.subtract(quarry.position()).multiply(1.0, 0.0, 1.0);
		if (look.lengthSqr() < 1.0E-6 || toward.lengthSqr() < 1.0E-6) {
			return true;
		}
		return look.normalize().dot(toward.normalize()) < Math.cos(Math.toRadians(FLANK));
	}

	/** The pack hunting the same quarry near this one, in a steady order so each keeps its own place on the ring. */
	private List<RiftHound> pack(final LivingEntity quarry) {
		return this.level().getEntitiesOfClass(RiftHound.class, this.getBoundingBox().inflate(16.0), h -> h.isAlive() && h.getTarget() == quarry).stream()
			.sorted(Comparator.comparingInt(RiftHound::getId)).toList();
	}

	/**
	 * The pack's hunt: come in from afar, then ring the quarry, each hound in its own place, the ring
	 * turning; a hound round at the quarry's side or back crouches and lunges, one at a time.
	 */
	private final class PackHuntGoal extends Goal {
		private int wait = LUNGE_MIN;
		private int crouching;
		private int flying;
		private boolean bit;
		private int snap;

		PackHuntGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			LivingEntity quarry = RiftHound.this.getTarget();
			return quarry != null && quarry.isAlive() && !(quarry instanceof Player player && (player.isCreative() || player.isSpectator()));
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void start() {
			this.wait = LUNGE_MIN / 2 + RiftHound.this.random.nextInt(LUNGE_MAX - LUNGE_MIN);
		}

		@Override
		public void stop() {
			this.crouching = 0;
			this.flying = 0;
			RiftHound.this.entityData.set(DATA_CROUCH, false);
			RiftHound.this.entityData.set(DATA_LUNGE, false);
		}

		@Override
		public void tick() {
			RiftHound self = RiftHound.this;
			LivingEntity quarry = self.getTarget();
			if (quarry == null || !(self.level() instanceof ServerLevel level)) {
				return;
			}
			self.getLookControl().setLookAt(quarry, 30.0F, 30.0F);
			double reach = self.distanceTo(quarry);
			if (this.snap > 0) {
				this.snap--;
			}

			if (this.flying > 0) {
				// in the air: a bite in passing, and down again
				this.flying--;
				if (!this.bit && self.getBoundingBox().inflate(0.5).intersects(quarry.getBoundingBox())) {
					this.bit = true;
					self.doHurtTarget(level, quarry);
				}
				if (this.flying <= 0 || this.flying < 12 && self.onGround()) {
					this.flying = 0;
					self.entityData.set(DATA_LUNGE, false);
				}
				return;
			}
			if (this.crouching > 0) {
				self.getNavigation().stop();
				if (--this.crouching <= 0) {
					self.entityData.set(DATA_CROUCH, false);
					this.leap(quarry);
				}
				return;
			}
			if (reach < 1.8 && this.snap <= 0) {
				// stood next to, it snaps
				this.snap = 20;
				self.doHurtTarget(level, quarry);
			}
			if (reach > NEAR) {
				self.getNavigation().moveTo(quarry, 1.4);
				return;
			}
			List<RiftHound> pack = self.pack(quarry);
			int place = Math.max(0, pack.indexOf(self));
			double turn = level.getGameTime() * 0.025 + (quarry.getId() % 7) + place * Math.PI * 2.0 / Math.max(1, pack.size());
			Vec3 spot = quarry.position().add(Math.cos(turn) * RING, 0.0, Math.sin(turn) * RING);
			self.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.3);

			this.wait--;
			boolean busy = pack.stream().anyMatch(h -> h != self && (h.isCrouching() || h.isLunging()));
			if (this.wait <= 0 && !busy && reach > 2.0 && reach < 8.0 && self.hasLineOfSight(quarry)
				&& (flanking(quarry, self.position()) || this.wait < -IMPATIENT)) {
				this.crouching = CROUCH;
				self.entityData.set(DATA_CROUCH, true);
				self.playSound(SoundEvents.ZOGLIN_ANGRY, 1.0F, 1.5F + self.random.nextFloat() * 0.2F);
				this.wait = LUNGE_MIN + self.random.nextInt(LUNGE_MAX - LUNGE_MIN);
			}
		}

		private void leap(final LivingEntity quarry) {
			RiftHound self = RiftHound.this;
			Vec3 toward = quarry.position().subtract(self.position()).multiply(1.0, 0.0, 1.0);
			if (toward.lengthSqr() < 1.0E-4) {
				return;
			}
			Vec3 go = toward.normalize().scale(LUNGE_SPEED);
			self.setDeltaMovement(go.x, LUNGE_UP, go.z);
			self.setYRot((float) (Mth.atan2(go.z, go.x) * Mth.RAD_TO_DEG) - 90.0F);
			self.yBodyRot = self.getYRot();
			this.flying = 20;
			this.bit = false;
			self.entityData.set(DATA_LUNGE, true);
			self.playSound(SoundEvents.ZOGLIN_ATTACK, 1.0F, 1.4F);
		}
	}

	@Override
	public boolean doHurtTarget(final ServerLevel level, final net.minecraft.world.entity.Entity target) {
		boolean bitten = super.doHurtTarget(level, target);
		if (bitten) {
			this.entityData.set(DATA_BITE, BITE_SHOWN);
		}
		return bitten;
	}

	@Override
	public void tick() {
		super.tick();
		this.crouchO = this.crouch;
		this.lungeO = this.lunge;
		this.biteO = this.bite;
		this.crouch = Mth.approach(this.crouch, this.isCrouching() ? 1.0F : 0.0F, 0.2F);
		this.lunge = Mth.approach(this.lunge, this.isLunging() ? 1.0F : 0.0F, 0.25F);
		int biting = this.entityData.get(DATA_BITE);
		this.bite = biting > 0 ? Math.min(1.0F, this.bite + 0.4F) : Math.max(0.0F, this.bite - 0.2F);
		if (!this.level().isClientSide() && biting > 0) {
			this.entityData.set(DATA_BITE, biting - 1);
		}
	}

	public float crouch(final float partial) {
		return Mth.lerp(partial, this.crouchO, this.crouch);
	}

	public float lunge(final float partial) {
		return Mth.lerp(partial, this.lungeO, this.lunge);
	}

	public float bite(final float partial) {
		return Mth.lerp(partial, this.biteO, this.bite);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level().isClientSide() && this.random.nextInt(5) == 0) {
			// the rift's light, leaking out through its cracks
			this.level().addParticle(ParticleTypes.CRIMSON_SPORE, this.getRandomX(0.5), this.getRandomY(), this.getRandomZ(0.5), 0.0, 0.0, 0.0);
		}
	}

	@Override
	public boolean causeFallDamage(final double distance, final float multiplier, final DamageSource source) {
		// it lands its lunges
		return distance > 6.0 && super.causeFallDamage(distance - 3.0, multiplier, source);
	}

	// ── The voice ─────────────────────────────────────────────────

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return SoundEvents.ZOGLIN_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return SoundEvents.ZOGLIN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ZOGLIN_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 1.35F + this.random.nextFloat() * 0.15F;
	}

	@Override
	protected float getSoundVolume() {
		return 0.8F;
	}
}
