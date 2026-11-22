package life.shizune.mistinn.shore;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.enemy.StaggerService;
import life.shizune.mistinn.enemy.WindupService;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 燈奴: once a lamplighter of the lighthouses, now the mist's thin servant, a little soul lantern in
 * its hand. It closes on a hunter; near enough, and seeing them, it stops and its lantern flares
 * bright (the warning), and a breath later it rushes them. After the rush it is spent for a moment,
 * its lantern guttering back to blue. A dodge beats the rush; so does a full heavy blow during the
 * flare, which breaks it off.
 */
public class LampThrall extends Zombie {
	/** How long the lantern flares before the rush, how long the rush lasts, and how long it is spent after. */
	public static final int FLARE_TICKS = 18;
	public static final int RUSH_TICKS = 10;
	public static final int SPENT_TICKS = 30;
	/** Ticks between one rush and the next flare. */
	public static final int COOLDOWN = 80;
	/** How near (and not nearer) a hunter must be for it to flare. */
	public static final double FLARE_FROM = 9.0;
	public static final double FLARE_TO = 3.5;
	/** The rush's speed, blocks a tick: enough to carry it to where the hunter stood, within these bounds. */
	private static final double SLOWEST = 0.45;
	private static final double FASTEST = 1.05;

	public static final byte PROWL = 0;
	public static final byte FLARE = 1;
	public static final byte RUSHING = 2;
	public static final byte SPENT = 3;

	private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(LampThrall.class, EntityDataSerializers.BYTE);

	private int stateTicks;
	private int cooldown;
	private Vec3 rush = Vec3.ZERO;
	private boolean struck;

	public LampThrall(final EntityType<? extends LampThrall> type, final Level level) {
		super(type, level);
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SOUL_LANTERN));
		this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Zombie.createAttributes()
			.add(Attributes.MAX_HEALTH, 24.0)
			.add(Attributes.ARMOR, 2.0)
			.add(Attributes.ATTACK_DAMAGE, 5.0)
			.add(Attributes.MOVEMENT_SPEED, 0.24)
			.add(Attributes.FOLLOW_RANGE, 40.0)
			.add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, PROWL);
	}

	/** Whether its last rush struck someone. */
	public boolean struckLastRush() {
		return this.struck;
	}

	public byte state() {
		return this.entityData.get(DATA_STATE);
	}

	private void setState(final byte state) {
		this.entityData.set(DATA_STATE, state);
		this.stateTicks = 0;
		// flaring and rushing, its own goals rest: the rush is not walked
		boolean held = state == FLARE || state == RUSHING;
		for (Goal.Flag flag : new Goal.Flag[] {Goal.Flag.MOVE, Goal.Flag.JUMP}) {
			if (held) {
				this.goalSelector.disableControlFlag(flag);
			} else {
				this.goalSelector.enableControlFlag(flag);
			}
		}
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(state == FLARE || state == RUSHING ? Items.LANTERN : Items.SOUL_LANTERN));
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		this.stateTicks++;
		if (this.cooldown > 0) {
			this.cooldown--;
		}
		LivingEntity target = this.getTarget();
		if ((this.state() == FLARE || this.state() == RUSHING) && StaggerService.staggered(this)) {
			// a full heavy blow knocks the flare or the rush clean out of it
			this.spend(level);
			return;
		}
		switch (this.state()) {
			case PROWL -> {
				if (this.cooldown <= 0 && target != null && target.isAlive() && this.hasLineOfSight(target)) {
					double distance = this.distanceTo(target);
					if (distance <= FLARE_FROM && distance >= FLARE_TO) {
						this.flare(level);
					}
				}
			}
			case FLARE -> {
				this.getNavigation().stop();
				this.setDeltaMovement(new Vec3(0.0, this.getDeltaMovement().y, 0.0));
				if (target != null) {
					this.getLookControl().setLookAt(target, 60.0F, 60.0F);
				}
				if (this.stateTicks % 3 == 0) {
					Vec3 hand = this.lantern();
					level.sendParticles(ParticleTypes.FLAME, hand.x, hand.y, hand.z, 3, 0.08, 0.08, 0.08, 0.01);
				}
				if (this.stateTicks >= FLARE_TICKS) {
					this.startRush(level, target);
				}
			}
			case RUSHING -> {
				this.getNavigation().stop();
				this.setDeltaMovement(this.rush.x, this.getDeltaMovement().y, this.rush.z);
				this.needsSync = true;
				if (!this.struck && target != null && this.getBoundingBox().inflate(0.45, 0.2, 0.45).intersects(target.getBoundingBox())) {
					this.struck = true;
					WindupService.land(level, this, target);
					// knocked back along the rush
					target.setDeltaMovement(target.getDeltaMovement().add(this.rush.x * 1.2, 0.25, this.rush.z * 1.2));
					target.syncVelocity = true;
				}
				if (this.stateTicks >= RUSH_TICKS || this.struck && this.stateTicks >= 3 || this.horizontalCollision) {
					this.spend(level);
				}
			}
			case SPENT -> {
				if (this.stateTicks >= SPENT_TICKS) {
					this.setState(PROWL);
				}
			}
			default -> {
			}
		}
	}

	private void flare(final ServerLevel level) {
		this.setState(FLARE);
		this.getNavigation().stop();
		level.playSound(null, this.getX(), this.getY(), this.getZ(), MistinnSounds.LAMP_THRALL_FLARE, SoundSource.HOSTILE, 1.1F, 1.0F);
	}

	private void startRush(final ServerLevel level, final @Nullable LivingEntity target) {
		Vec3 toward = target == null ? Vec3.directionFromRotation(0.0F, this.getYRot())
			: target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
		// fast enough to reach where the hunter stands, a little past, before the rush runs out
		double speed = net.minecraft.util.Mth.clamp((toward.length() + 0.8) / (RUSH_TICKS - 2), SLOWEST, FASTEST);
		this.rush = (toward.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, this.getYRot()) : toward.normalize()).scale(speed);
		this.struck = false;
		this.setState(RUSHING);
		this.setDeltaMovement(this.rush.x, 0.12, this.rush.z);
		this.needsSync = true;
		level.playSound(null, this.getX(), this.getY(), this.getZ(), MistinnSounds.LAMP_THRALL_RUSH, SoundSource.HOSTILE, 1.0F, 1.0F);
	}

	private void spend(final ServerLevel level) {
		this.setState(SPENT);
		this.cooldown = COOLDOWN;
		this.rush = Vec3.ZERO;
		level.playSound(null, this.getX(), this.getY(), this.getZ(), MistinnSounds.LAMP_THRALL_SPEND, SoundSource.HOSTILE, 1.0F, 1.0F);
	}

	/** Where the lantern hangs, in front of it at hand height. */
	private Vec3 lantern() {
		return this.position().add(Vec3.directionFromRotation(0.0F, this.yBodyRot).scale(0.5)).add(0.0, 1.0, 0.0);
	}

	/** Spent, it moves at half its pace. */
	@Override
	public float getSpeed() {
		return this.state() == SPENT ? super.getSpeed() * 0.5F : super.getSpeed();
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

	/** It always carries its lantern, whatever it was spawned with. */
	@Override
	public @Nullable SpawnGroupData finalizeSpawn(final ServerLevelAccessor level, final DifficultyInstance difficulty, final EntitySpawnReason reason,
		final @Nullable SpawnGroupData data) {
		SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SOUL_LANTERN));
		this.setBaby(false);
		return result;
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("ThrallCooldown", this.cooldown);
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.cooldown = input.getIntOr("ThrallCooldown", 0);
	}

	// ── Voice ────────────────────────────────────────────────────

	@Override
	protected SoundEvent getAmbientSound() {
		return MistinnSounds.LAMP_THRALL_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return MistinnSounds.LAMP_THRALL_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return MistinnSounds.LAMP_THRALL_DEATH;
	}

	@Override
	protected SoundEvent getStepSound() {
		return MistinnSounds.LAMP_THRALL_STEP;
	}
}
