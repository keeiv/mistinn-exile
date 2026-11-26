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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * 晶蜥: the small one of the lattice, a low lizard of vein stone and crystal that keeps to the
 * ground of a Terra highland in threes. Left alone it settles flat on the stone and lies still,
 * looking like an outcrop, until a hunter comes within a few strides; then it springs, and its
 * pack with it. Its crystal teeth grind at armour: every bite wears what the bitten one has on.
 * <p>
 * Lifeless, as the drakes are: poison and withering do nothing to it, nor does a fall; shot and
 * stab glance off it, heavy blows crack it, and it dies in a burst of crystal.
 */
public class CrystalLizard extends Monster {
	private static final EntityDataAccessor<Boolean> DATA_STILL = SynchedEntityData.defineId(CrystalLizard.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_BITE = SynchedEntityData.defineId(CrystalLizard.class, EntityDataSerializers.INT);

	/** How many keep together. */
	public static final int PACK = 3;
	/** How near a hunter comes to a still lizard before it springs. */
	public static final double WAKE = 7.0;
	/** How much more every piece of armour wears from one bite. */
	public static final int WEAR = 4;
	/** How long it lies still, at the least and the most, and how long before it settles again. */
	public static final int STILL_MIN = 300;
	public static final int STILL_MAX = 700;
	private static final int BITE_SHOWN = 8;

	private int still;
	private int restless = 100;
	private float stillO;
	private float stillAmount;
	private float biteO;
	private float bite;

	public CrystalLizard(final EntityType<? extends CrystalLizard> type, final Level level) {
		super(type, level);
		this.xpReward = 5;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 16.0)
			.add(Attributes.ARMOR, 5.0)
			.add(Attributes.ATTACK_DAMAGE, 3.0)
			.add(Attributes.MOVEMENT_SPEED, 0.32)
			.add(Attributes.FOLLOW_RANGE, 20.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STILL, false);
		builder.define(DATA_BITE, 0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new LieStillGoal());
		this.goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.32F));
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.3, true));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(CrystalLizard.class));
		// lying still it notices only who comes close; up and about, whoever it sees
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
			(target, level) -> !this.isStill() || target.distanceTo(this) < WAKE));
	}

	// ── Lying still ───────────────────────────────────────────────

	public boolean isStill() {
		return this.entityData.get(DATA_STILL);
	}

	/** Settles flat and still on the ground, for a while. */
	public void lieStill(final int ticks) {
		this.still = ticks;
		this.entityData.set(DATA_STILL, ticks > 0);
		if (ticks > 0) {
			this.getNavigation().stop();
		}
	}

	/** Left alone, now and then it settles flat on the stone and lies there like an outcrop. */
	private final class LieStillGoal extends Goal {
		LieStillGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			CrystalLizard self = CrystalLizard.this;
			if (self.getTarget() != null) {
				return false;
			}
			// already lying still (told to, or so it was when the world was saved), or ready to settle
			return self.still > 0 || self.onGround() && !self.isInWater() && self.restless <= 0 && self.random.nextInt(40) == 0;
		}

		@Override
		public boolean canContinueToUse() {
			return CrystalLizard.this.still > 0 && CrystalLizard.this.getTarget() == null;
		}

		@Override
		public void start() {
			if (CrystalLizard.this.still <= 0) {
				CrystalLizard.this.lieStill(STILL_MIN + CrystalLizard.this.random.nextInt(STILL_MAX - STILL_MIN));
			}
		}

		@Override
		public void tick() {
			CrystalLizard.this.getNavigation().stop();
		}

		@Override
		public void stop() {
			CrystalLizard self = CrystalLizard.this;
			self.restless = 200 + self.random.nextInt(200);
			LivingEntity target = self.getTarget();
			if (target != null && self.isStill() && self.level() instanceof ServerLevel level) {
				// woken by whoever came near: up at them, and the pack with it
				self.spring(level, target);
			} else {
				self.lieStill(0);
			}
		}
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		if (this.still > 0) {
			this.still--;
		}
		if (this.restless > 0 && !this.isStill()) {
			this.restless--;
		}
		LivingEntity target = this.getTarget();
		if (this.isStill() && target != null) {
			this.spring(level, target);
		}
	}

	/** Up off the stone at whoever came near, and the rest of the pack with it. */
	private void spring(final ServerLevel level, final LivingEntity target) {
		this.lieStill(0);
		this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.32, 0.0));
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.0F, 1.6F);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, MistinnBlocks.VEIN_STONE.defaultBlockState()), this.getX(), this.getY() + 0.2, this.getZ(),
			12, 0.3, 0.1, 0.3, 0.1);
		for (CrystalLizard other : level.getEntitiesOfClass(CrystalLizard.class, this.getBoundingBox().inflate(10.0), l -> l != this && l.getTarget() == null)) {
			other.setTarget(target);
		}
	}

	// ── Biting ────────────────────────────────────────────────────

	@Override
	public boolean doHurtTarget(final ServerLevel level, final Entity target) {
		boolean bitten = super.doHurtTarget(level, target);
		if (bitten) {
			this.entityData.set(DATA_BITE, BITE_SHOWN);
			if (target instanceof LivingEntity living) {
				grind(living);
			}
		}
		return bitten;
	}

	/** Its crystal teeth grind at whatever armour the bitten one has on. */
	public static void grind(final LivingEntity bitten) {
		for (EquipmentSlot slot : EquipmentSlot.VALUES) {
			if (!slot.isArmor()) {
				continue;
			}
			ItemStack piece = bitten.getItemBySlot(slot);
			if (!piece.isEmpty() && piece.isDamageableItem()) {
				piece.hurtAndBreak(WEAR, bitten, slot);
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
		this.stillO = this.stillAmount;
		this.biteO = this.bite;
		this.stillAmount = Mth.approach(this.stillAmount, this.isStill() ? 1.0F : 0.0F, 0.1F);
		int biting = this.entityData.get(DATA_BITE);
		this.bite = biting > 0 ? Math.min(1.0F, this.bite + 0.4F) : Math.max(0.0F, this.bite - 0.2F);
		if (!this.level().isClientSide() && biting > 0) {
			this.entityData.set(DATA_BITE, biting - 1);
		}
	}

	public float still(final float partial) {
		return Mth.lerp(partial, this.stillO, this.stillAmount);
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
		return super.hurtServer(level, source, amount * CrystalDrake.facets(source));
	}

	@Override
	public void die(final DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, MistinnBlocks.LATTICE_CRYSTAL.defaultBlockState()),
				this.getX(), this.getY() + 0.3, this.getZ(), 30, 0.4, 0.2, 0.4, 0.15);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.0F, 1.2F);
		}
	}

	// ── Spawning ──────────────────────────────────────────────────

	/** Where they may come of themselves: on the ground, day or night (they have no blood to fear the sun), and not where the stone is already crowded. */
	public static boolean checkSpawnRules(final EntityType<CrystalLizard> type, final ServerLevelAccessor level, final EntitySpawnReason reason, final BlockPos pos,
		final RandomSource random) {
		if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
			return true;
		}
		BlockState under = level.getBlockState(pos.below());
		return under.isSolid() && level.getEntitiesOfClass(CrystalLizard.class, new AABB(pos).inflate(40.0)).size() < PACK * 3 && random.nextInt(2) == 0;
	}

	// ── The voice ─────────────────────────────────────────────────

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return this.isStill() ? null : SoundEvents.AMETHYST_BLOCK_CHIME;
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return SoundEvents.AMETHYST_CLUSTER_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.AMETHYST_CLUSTER_BREAK;
	}

	@Override
	protected void playStepSound(final BlockPos pos, final BlockState state) {
		this.playSound(SoundEvents.AMETHYST_CLUSTER_STEP, 0.3F, 1.4F);
	}

	@Override
	public float getVoicePitch() {
		return 1.3F + this.random.nextFloat() * 0.3F;
	}

	// ── Saving ────────────────────────────────────────────────────

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("still", this.still);
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.lieStill(input.getIntOr("still", 0));
	}
}
