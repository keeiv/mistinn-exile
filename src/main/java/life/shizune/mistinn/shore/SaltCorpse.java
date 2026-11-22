package life.shizune.mistinn.shore;

import net.minecraft.sounds.SoundEvent;
import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * 鹽屍: a hunter who walked into the mist and lay in the brine until the salt crusted over them.
 * Slow and hard to hurt, and it gets back up once: the first blow that should kill it drops it
 * like a corpse, and after a few breaths it climbs to its feet again with half its strength. A
 * blow on the body while it lies finishes it. Some lie about the lighthouses from the start,
 * dormant, and rise when a hunter comes close.
 */
public class SaltCorpse extends Husk {
	/** How long it lies after the blow that should have killed it, and how long it takes to get up. */
	public static final int DOWN_TICKS = 70;
	public static final int RISE_TICKS = 16;
	/** How long the fall to the ground takes on screen. */
	public static final int FALL_TICKS = 10;
	/** How close a hunter comes before a dormant corpse rises. */
	public static final double WAKE = 6.0;

	public static final byte STANDING = 0;
	public static final byte FALLEN = 1;
	public static final byte DORMANT = 2;
	public static final byte RISING = 3;

	private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(SaltCorpse.class, EntityDataSerializers.BYTE);

	/** Whether it has already got up once after a killing blow. */
	private boolean risen;
	/** Laid by a lighthouse when it was built: it stays, where those the mist brings up may go. */
	private boolean laid;
	/** Ticks in its current state, on the server. */
	private int stateTicks;
	/** When its state last changed, on the client, for the fall and the rise on screen. */
	private int stateChangedAt;

	public SaltCorpse(final EntityType<? extends SaltCorpse> type, final Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Zombie.createAttributes()
			.add(Attributes.MAX_HEALTH, 40.0)
			.add(Attributes.ARMOR, 6.0)
			.add(Attributes.ATTACK_DAMAGE, 5.0)
			.add(Attributes.MOVEMENT_SPEED, 0.19)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
			.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, STANDING);
	}

	@Override
	public void onSyncedDataUpdated(final EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_STATE.equals(accessor)) {
			this.stateChangedAt = this.tickCount;
		}
	}

	public byte state() {
		return this.entityData.get(DATA_STATE);
	}

	/** Whether it is on the ground: fallen, dormant, or still getting up. */
	public boolean down() {
		return this.state() != STANDING;
	}

	/** Whether it has already risen once after a killing blow, so the next one is the last. */
	public boolean risen() {
		return this.risen;
	}

	/** Lays it down to wait for a hunter; {@code laid} when a lighthouse is built with it, so it stays. */
	public void lieDormant(final boolean laid) {
		this.setState(DORMANT);
		this.laid = laid;
		this.setYBodyRot(this.getYRot());
	}

	/** How far over it lies on screen: 0 on its feet, 1 flat on the ground. */
	public float lying(final float partialTicks) {
		float since = this.tickCount - this.stateChangedAt + partialTicks;
		return switch (this.state()) {
			case FALLEN -> Mth.sqrt(Mth.clamp(since / FALL_TICKS, 0.0F, 1.0F));
			case DORMANT -> 1.0F;
			case RISING -> 1.0F - Mth.smoothstep(Mth.clamp(since / RISE_TICKS, 0.0F, 1.0F));
			default -> 0.0F;
		};
	}

	private void setState(final byte state) {
		this.entityData.set(DATA_STATE, state);
		this.stateTicks = 0;
		this.hold(state != STANDING);
	}

	/** On the ground its goals rest: it neither walks, turns, jumps nor picks a target. */
	private void hold(final boolean down) {
		for (net.minecraft.world.entity.ai.goal.Goal.Flag flag : new net.minecraft.world.entity.ai.goal.Goal.Flag[] {
			net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE, net.minecraft.world.entity.ai.goal.Goal.Flag.LOOK,
			net.minecraft.world.entity.ai.goal.Goal.Flag.JUMP, net.minecraft.world.entity.ai.goal.Goal.Flag.TARGET}) {
			if (down) {
				this.goalSelector.disableControlFlag(flag);
				this.targetSelector.disableControlFlag(flag);
			} else {
				this.goalSelector.enableControlFlag(flag);
				this.targetSelector.enableControlFlag(flag);
			}
		}
	}

	/** The first killing blow drops it like a corpse instead; a kill that ignores everything kills it outright. */
	@Override
	public void die(final DamageSource source) {
		if (!this.risen && !this.down() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && this.level() instanceof ServerLevel level) {
			this.setHealth(1.0F);
			this.fall(level);
			return;
		}
		super.die(source);
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
		if (this.state() == DORMANT) {
			// struck where it lay: it comes up at once, and the blow lands
			this.setState(RISING);
		} else if (this.state() == FALLEN && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && source.getEntity() != null) {
			// a blow on the body while it lies: this time it stays down
			this.risen = true;
			return super.hurtServer(level, source, Math.max(amount, this.getHealth() + 1.0F));
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public boolean doHurtTarget(final ServerLevel level, final Entity target) {
		return !this.down() && super.doHurtTarget(level, target);
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		if (!this.down()) {
			return;
		}
		// on the ground, it goes nowhere and does nothing
		this.getNavigation().stop();
		this.setXxa(0.0F);
		this.setZza(0.0F);
		this.setJumping(false);
		this.setDeltaMovement(new Vec3(0.0, this.getDeltaMovement().y, 0.0));
		this.stateTicks++;
		switch (this.state()) {
			case DORMANT -> {
				ServerPlayer near = level.getNearestPlayer(this, WAKE) instanceof ServerPlayer player
					&& !player.isSpectator() ? player : null;
				if (near != null) {
					this.setState(RISING);
					level.playSound(null, this.getX(), this.getY(), this.getZ(), MistinnSounds.SALT_CORPSE_RISE, SoundSource.HOSTILE, 1.2F, 0.9F);
					this.salt(level, 14);
				}
			}
			case FALLEN -> {
				if (this.stateTicks >= DOWN_TICKS) {
					this.setState(RISING);
					// it climbs back up with half of what it had, and it will not do so again
					this.risen = true;
					this.setHealth(this.getMaxHealth() * 0.5F);
					level.playSound(null, this.getX(), this.getY(), this.getZ(), MistinnSounds.SALT_CORPSE_RISE, SoundSource.HOSTILE, 1.4F, 0.8F);
					this.salt(level, 18);
				}
			}
			case RISING -> {
				if (this.stateTicks >= RISE_TICKS) {
					this.setState(STANDING);
				}
			}
			default -> {
			}
		}
	}

	private void fall(final ServerLevel level) {
		this.setState(FALLEN);
		this.getNavigation().stop();
		this.setTarget(null);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), MistinnSounds.SALT_CORPSE_FALL, SoundSource.HOSTILE, 1.0F, 1.0F);
		this.salt(level, 24);
	}

	/** Salt shaken off the body. */
	private void salt(final ServerLevel level, final int count) {
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CALCITE.defaultBlockState()),
			this.getX(), this.getY() + 0.4, this.getZ(), count, 0.5, 0.2, 0.5, 0.05);
	}

	/** Salt-dried: water does not turn it, as it would a husk. */
	@Override
	protected boolean convertsInWater() {
		return false;
	}

	/** There are no young ones among the drowned hunters. */
	@Override
	public void setBaby(final boolean baby) {
		super.setBaby(false);
	}

	/** A dormant corpse laid by a lighthouse stays where it was laid. */
	@Override
	public boolean removeWhenFarAway(final double distance) {
		return !(this.laid && this.state() == DORMANT) && super.removeWhenFarAway(distance);
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("Risen", this.risen);
		output.putBoolean("Laid", this.laid);
		output.putInt("SaltState", this.state());
		output.putInt("SaltStateTicks", this.stateTicks);
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.risen = input.getBooleanOr("Risen", false);
		this.laid = input.getBooleanOr("Laid", false);
		this.entityData.set(DATA_STATE, (byte) input.getIntOr("SaltState", STANDING));
		this.stateTicks = input.getIntOr("SaltStateTicks", 0);
		this.hold(this.down());
	}

	// ── Voice ────────────────────────────────────────────────────

	@Override
	protected SoundEvent getAmbientSound() {
		return MistinnSounds.SALT_CORPSE_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return MistinnSounds.SALT_CORPSE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return MistinnSounds.SALT_CORPSE_DEATH;
	}

	@Override
	protected SoundEvent getStepSound() {
		return MistinnSounds.SALT_CORPSE_STEP;
	}
}
