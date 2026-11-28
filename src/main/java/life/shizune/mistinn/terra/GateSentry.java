package life.shizune.mistinn.terra;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import life.shizune.mistinn.enemy.NoiseService;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 閘衛機兵: one of the machines the forebears left to keep the dam, squat and heavy, oxidized copper over iron, driven by
 * a block of lattice crystal caged on its back. Left alone it kneels at its post, powered down; a noise, a hunter too near
 * (one creeping past may come closer) or a blow wakes it, and it takes a while to rise.
 * <p>
 * Awake it is slow, and slow to turn. Close in and in front of it, it raises both fists and brings them down on the ground
 * before it; the fists stick fast a moment after, and then its guard is down. Kept off at a distance, it hisses steam,
 * crouches and charges straight at where its quarry stood; a charge that meets a wall leaves it stuck longer. Its copper
 * front turns most of a blow; the crystal on its back cracks under one. With nothing to chase a while, it walks back to
 * its post and kneels again.
 */
public class GateSentry extends Monster {
	/** What it is doing, shown to the clients so each move is seen coming. */
	public enum Move {
		NONE, WAKING, SLAM_WINDUP, SLAM, RECOVER, RUSH_WINDUP, RUSH, STUCK
	}

	private static final EntityDataAccessor<Integer> DATA_MOVE = SynchedEntityData.defineId(GateSentry.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_DORMANT = SynchedEntityData.defineId(GateSentry.class, EntityDataSerializers.BOOLEAN);

	/** How long it takes to rise, once woken. */
	public static final int WAKE = 40;
	/** The slam: how long the fists are raised, how long they come down, how long they stick fast after. */
	public static final int SLAM_WINDUP = 22;
	public static final int SLAM_DOWN = 4;
	public static final int RECOVER = 30;
	/** How near (across the ground, past its own breadth) its quarry must be for it to slam, and how far ahead the fists land and what they reach. */
	public static final double SLAM_REACH = 3.0;
	public static final double SLAM_AHEAD = 1.8;
	public static final double SLAM_RADIUS = 2.3;
	/** The charge: how long it crouches, at most how long it runs and how fast, and how long it is stuck after a wall. */
	public static final int RUSH_WINDUP = 18;
	public static final int RUSH = 24;
	public static final double RUSH_SPEED = 0.55;
	public static final int RUSH_RECOVER = 16;
	public static final int STUCK = 50;
	/** How far off its quarry must be for it to charge, and at least how long between charges. */
	public static final double RUSH_MIN = 5.0;
	public static final double RUSH_MAX = 13.0;
	public static final int RUSH_EVERY = 140;
	/** What a charge deals, as a share of its blow. */
	public static final float RUSH_SHARE = 0.7F;
	/** How far it turns in a tick, walking and with its fists raised. */
	public static final float TURN = 5.0F;
	public static final float TURN_WINDUP = 1.5F;
	/** What a blow comes to on its back (the crystal) and on its front (the copper, unless its guard is down). */
	public static final float BACK = 2.5F;
	public static final float FRONT = 0.35F;
	/** The cosine of how far round from its front a blow must come from to reach its back, and to meet its copper. */
	private static final double BEHIND = -0.42;
	private static final double AHEAD = 0.3;
	/** How near a hunter wakes it, walking and creeping. */
	public static final double WAKE_NEAR = 4.0;
	public static final double WAKE_CREEP = 2.0;
	/** With nothing to chase this long, it goes back to its post. */
	public static final int GIVE_UP = 160;

	private int moveTimer;
	private int rushWait = RUSH_EVERY / 2;
	private int slamWait;
	private int idle;
	private Vec3 rush = Vec3.ZERO;
	private final Set<UUID> struck = new HashSet<>();
	private @Nullable BlockPos post;
	private float postYaw;
	private Move shownMove = Move.NONE;
	private int shownAge;
	private float dormantO;
	private float dormant;

	public GateSentry(final EntityType<? extends GateSentry> type, final Level level) {
		super(type, level);
		this.xpReward = 30;
		this.moveControl = new Heavy(this);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 90.0)
			.add(Attributes.ARMOR, 8.0)
			.add(Attributes.ARMOR_TOUGHNESS, 4.0)
			.add(Attributes.ATTACK_DAMAGE, 10.0)
			.add(Attributes.MOVEMENT_SPEED, 0.2)
			.add(Attributes.FOLLOW_RANGE, 24.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MOVE, Move.NONE.ordinal());
		builder.define(DATA_DORMANT, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new KneelGoal());
		this.goalSelector.addGoal(2, new FightGoal());
		this.goalSelector.addGoal(6, new PostGoal());
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected BodyRotationControl createBodyControl() {
		// its body is where it faces: the copper front and the crystal behind go round with it, no slower or faster
		return new BodyRotationControl(this) {
			@Override
			public void clientTick() {
				GateSentry.this.yBodyRot = GateSentry.this.getYRot();
				GateSentry.this.yHeadRot = Mth.approachDegrees(GateSentry.this.getYRot(), GateSentry.this.yHeadRot, GateSentry.this.getMaxHeadYRot());
			}
		};
	}

	@Override
	public int getMaxHeadYRot() {
		return 25;
	}

	// ── Its state ─────────────────────────────────────────────────

	public Move move() {
		return Move.values()[this.entityData.get(DATA_MOVE)];
	}

	public boolean dormant() {
		return this.entityData.get(DATA_DORMANT);
	}

	/** How long its present move has gone on, as its client has seen it. */
	public float moveAge(final float partial) {
		return this.shownAge + partial;
	}

	/** 0 standing, 1 knelt and powered down. */
	public float kneel(final float partial) {
		return Mth.lerp(partial, this.dormantO, this.dormant);
	}

	/** Whether its guard is down: the fists stuck fast after a slam, or stuck after a charge into a wall. */
	public boolean open() {
		Move move = this.move();
		return move == Move.RECOVER || move == Move.STUCK;
	}

	private void begin(final Move next, final int ticks) {
		this.entityData.set(DATA_MOVE, next.ordinal());
		this.moveTimer = ticks;
		this.struck.clear();
	}

	/** Its post: where it kneels, facing which way, and goes back to when it has nothing to chase. It kneels there now. */
	public void post(final BlockPos at, final float yaw) {
		this.post = at.immutable();
		this.postYaw = yaw;
		this.setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
		this.entityData.set(DATA_DORMANT, true);
		this.begin(Move.NONE, 0);
	}

	@Override
	public void setTarget(final @Nullable LivingEntity target) {
		// knelt and powered down, it sets on no one (and so does not cry out and wake itself)
		if (target != null && this.dormant()) {
			return;
		}
		super.setTarget(target);
	}

	/** It is woken: it rises, slowly. */
	public void wake(final ServerLevel level) {
		if (!this.dormant()) {
			return;
		}
		this.entityData.set(DATA_DORMANT, false);
		this.begin(Move.WAKING, WAKE);
		this.idle = 0;
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.6F, 0.6F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.HOSTILE, 1.2F, 0.5F);
		this.steam(level, 12);
	}

	/** Test hook: it may slam and charge at once. */
	public void hasten() {
		this.slamWait = 0;
		this.rushWait = 0;
	}

	/** Test hook: it has had nothing to chase long enough to go back to its post. */
	public void bored() {
		this.idle = GIVE_UP;
	}

	/** It kneels at its post and powers down. */
	private void sleep(final ServerLevel level) {
		this.entityData.set(DATA_DORMANT, true);
		this.begin(Move.NONE, 0);
		this.setTarget(null);
		this.getNavigation().stop();
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.2F, 0.6F);
	}

	// ── Moving ────────────────────────────────────────────────────

	/** Stands where it is. */
	private void hold() {
		this.getNavigation().stop();
		this.getMoveControl().setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0);
	}

	/** Turns to face a place, a little at a time. */
	private void face(final Vec3 at, final float most) {
		float yaw = (float) (Mth.atan2(at.z - this.getZ(), at.x - this.getX()) * Mth.RAD_TO_DEG) - 90.0F;
		this.turn(yaw, most);
	}

	private void turn(final float yaw, final float most) {
		this.setYRot(Mth.approachDegrees(this.getYRot(), yaw, most));
		this.yBodyRot = this.getYRot();
		this.yHeadRot = Mth.approachDegrees(this.getYRot(), this.yHeadRot, this.getMaxHeadYRot());
	}

	/** Which way it faces, across the ground. */
	public Vec3 facing() {
		float yaw = this.getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
	}

	private static Vec3 flat(final Vec3 v) {
		Vec3 across = new Vec3(v.x, 0.0, v.z);
		return across.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : across.normalize();
	}

	/** How far across the ground to one, past its own breadth. */
	private double gap(final LivingEntity other) {
		return Math.hypot(other.getX() - this.getX(), other.getZ() - this.getZ()) - this.getBbWidth() / 2.0;
	}

	/** Whether one may be struck by it: alive, no fellow of the dam's, not a watcher or a builder. */
	private boolean fair(final LivingEntity entity) {
		return entity != this && entity.isAlive() && !entity.isSpectator() && !(entity instanceof Player player && player.isCreative())
			&& !(entity instanceof Enemy && !CrystalLizard.companion(entity));
	}

	/** Strikes one and flings it. */
	private void strike(final ServerLevel level, final LivingEntity hit, final float damage, final Vec3 fling) {
		if (!this.struck.add(hit.getUUID())) {
			return;
		}
		hit.hurtServer(level, level.damageSources().mobAttack(this), damage);
		hit.setDeltaMovement(fling);
		hit.syncVelocity = true;
	}

	private float blow() {
		return (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
	}

	/** Steam from its stacks. */
	private void steam(final ServerLevel level, final int count) {
		Vec3 back = this.facing().scale(-0.45);
		level.sendParticles(ParticleTypes.CLOUD, this.getX() + back.x, this.getY() + this.getBbHeight() * 0.95, this.getZ() + back.z,
			count, 0.25, 0.1, 0.25, 0.03);
	}

	/** The fists come down on the ground before it: all in reach there are struck and thrown back. */
	private void slam(final ServerLevel level) {
		this.begin(Move.SLAM, SLAM_DOWN);
		Vec3 ahead = this.facing();
		Vec3 at = this.position().add(ahead.scale(SLAM_AHEAD));
		BlockState ground = level.getBlockState(BlockPos.containing(at.x, this.getY() - 0.5, at.z));
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, this.getY() + 0.2, at.z, 60, 1.0, 0.1, 1.0, 0.2);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, this.getY() + 0.3, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.1F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 0.5F);
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(SLAM_RADIUS, 2.0, SLAM_RADIUS),
			e -> this.fair(e) && Math.hypot(e.getX() - at.x, e.getZ() - at.z) <= SLAM_RADIUS + e.getBbWidth() / 2.0 && e.getY() < this.getY() + 2.0);
		for (LivingEntity hit : near) {
			Vec3 away = flat(hit.position().subtract(this.position()));
			this.strike(level, hit, this.blow(), away.scale(0.9).add(0.0, 0.45, 0.0));
		}
	}

	/** The charge meets a wall: it is stuck a while. */
	private void stuck(final ServerLevel level) {
		this.setDeltaMovement(this.rush.scale(-0.2));
		this.begin(Move.STUCK, STUCK);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.4F, 0.4F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.HOSTILE, 1.0F, 0.6F);
		Vec3 front = this.position().add(this.rush.scale(1.0));
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, front.x, this.getY() + 1.4, front.z, 30, 0.5, 0.6, 0.5, 0.2);
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		if (this.dormant()) {
			this.listen(level);
			return;
		}
		if (this.slamWait > 0) {
			this.slamWait--;
		}
		if (this.rushWait > 0) {
			this.rushWait--;
		}
		LivingEntity target = this.getTarget();
		if (target == null || !target.isAlive()) {
			this.idle++;
		} else {
			this.idle = 0;
		}
	}

	/** Knelt and powered down, it still hears, and feels one come too near. */
	private void listen(final ServerLevel level) {
		if (NoiseService.heard(this) != null) {
			this.wake(level);
			return;
		}
		for (Player player : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(WAKE_NEAR + 1.0), p -> this.fair(p))) {
			if (this.gap(player) <= (player.isShiftKeyDown() ? WAKE_CREEP : WAKE_NEAR)) {
				this.wake(level);
				this.setTarget(player);
				return;
			}
		}
	}

	// ── Being struck ──────────────────────────────────────────────

	/** What a blow from this source comes to on it: more on its back, less on its copper front unless its guard is down. */
	public float share(final DamageSource source) {
		Vec3 from = source.getSourcePosition();
		if (from == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return 1.0F;
		}
		double side = this.facing().dot(flat(from.subtract(this.position())));
		if (side < BEHIND) {
			return BACK;
		}
		return side > AHEAD && !this.open() ? FRONT : 1.0F;
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
		float share = this.share(source);
		if (this.dormant() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			this.wake(level);
		}
		if (share > 1.0F) {
			// the crystal on its back
			Vec3 core = this.position().add(this.facing().scale(-0.6)).add(0.0, this.getBbHeight() * 0.7, 0.0);
			level.playSound(null, core.x, core.y, core.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 1.2F, 1.3F);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, core.x, core.y, core.z, 16, 0.2, 0.3, 0.2, 0.25);
		} else if (share < 1.0F) {
			// the copper turns it
			Vec3 plate = this.position().add(this.facing().scale(0.7)).add(0.0, this.getBbHeight() * 0.6, 0.0);
			level.playSound(null, plate.x, plate.y, plate.z, SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 0.45F, 1.7F);
			level.sendParticles(ParticleTypes.CRIT, plate.x, plate.y, plate.z, 8, 0.2, 0.2, 0.2, 0.2);
		}
		return super.hurtServer(level, source, amount * share);
	}

	@Override
	public void tick() {
		super.tick();
		Move now = this.move();
		if (now != this.shownMove) {
			this.shownMove = now;
			this.shownAge = 0;
		} else {
			this.shownAge++;
		}
		this.dormantO = this.dormant;
		// it kneels at once, but rises as slowly as it wakes
		this.dormant = Mth.approach(this.dormant, this.dormant() ? 1.0F : 0.0F, this.dormant() ? 0.1F : 1.0F / WAKE);
		if (this.level().isClientSide() && !this.dormant() && (now == Move.RECOVER || now == Move.RUSH_WINDUP || now == Move.STUCK)
			&& this.random.nextInt(2) == 0) {
			Vec3 back = this.facing().scale(-0.45);
			this.level().addParticle(ParticleTypes.CLOUD, this.getX() + back.x + (this.random.nextFloat() - 0.5) * 0.4,
				this.getY() + this.getBbHeight() * 0.95, this.getZ() + back.z, 0.0, 0.06, 0.0);
		}
	}

	// ── Its goals ─────────────────────────────────────────────────

	/** Knelt or rising, it does nothing else. */
	private final class KneelGoal extends Goal {
		KneelGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return GateSentry.this.dormant() || GateSentry.this.move() == Move.WAKING;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			GateSentry self = GateSentry.this;
			self.hold();
			if (self.move() == Move.WAKING && --self.moveTimer <= 0) {
				self.begin(Move.NONE, 0);
			}
		}
	}

	/** Its fight: walk at its quarry, slam close in, charge from afar. */
	private final class FightGoal extends Goal {
		FightGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			GateSentry self = GateSentry.this;
			if (self.dormant() || self.move() == Move.WAKING) {
				return false;
			}
			LivingEntity quarry = self.getTarget();
			return self.move() != Move.NONE || quarry != null && quarry.isAlive() && self.fair(quarry);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void stop() {
			GateSentry.this.getNavigation().stop();
		}

		@Override
		public void tick() {
			GateSentry self = GateSentry.this;
			if (!(self.level() instanceof ServerLevel level)) {
				return;
			}
			LivingEntity quarry = self.getTarget();
			switch (self.move()) {
				case NONE -> this.close(level, quarry);
				case SLAM_WINDUP -> {
					self.hold();
					if (quarry != null) {
						self.face(quarry.position(), TURN_WINDUP);
					}
					if (--self.moveTimer <= 0) {
						self.slam(level);
					}
				}
				case SLAM -> {
					self.hold();
					if (--self.moveTimer <= 0) {
						self.begin(Move.RECOVER, RECOVER);
						level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.8F, 0.6F);
					}
				}
				case RECOVER, STUCK -> {
					self.hold();
					if (--self.moveTimer <= 0) {
						self.begin(Move.NONE, 0);
						self.slamWait = 10;
					}
				}
				case RUSH_WINDUP -> {
					self.hold();
					self.turn((float) (Mth.atan2(self.rush.z, self.rush.x) * Mth.RAD_TO_DEG) - 90.0F, 30.0F);
					if (--self.moveTimer <= 0) {
						self.begin(Move.RUSH, RUSH);
						level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.HOSTILE, 1.4F, 0.4F);
					}
				}
				case RUSH -> this.run(level);
				default -> self.begin(Move.NONE, 0);
			}
		}

		/** Walking at its quarry, until it is near enough to slam or far enough to charge. */
		private void close(final ServerLevel level, final @Nullable LivingEntity quarry) {
			GateSentry self = GateSentry.this;
			if (quarry == null || !quarry.isAlive()) {
				return;
			}
			self.getLookControl().setLookAt(quarry, 25.0F, 25.0F);
			double gap = self.gap(quarry);
			Vec3 toward = flat(quarry.position().subtract(self.position()));
			double ahead = self.facing().dot(toward);
			if (gap <= SLAM_REACH && ahead > 0.5 && self.slamWait <= 0) {
				self.hold();
				self.begin(Move.SLAM_WINDUP, SLAM_WINDUP);
				level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.PISTON_CONTRACT, SoundSource.HOSTILE, 1.3F, 0.5F);
				level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.0F, 1.6F);
				return;
			}
			if (self.rushWait <= 0 && gap >= RUSH_MIN && gap <= RUSH_MAX && Math.abs(quarry.getY() - self.getY()) < 2.0 && self.hasLineOfSight(quarry)) {
				self.hold();
				self.rush = toward;
				self.begin(Move.RUSH_WINDUP, RUSH_WINDUP);
				self.rushWait = RUSH_EVERY + self.random.nextInt(RUSH_EVERY / 2);
				level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 1.2F, 0.5F);
				level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.0F, 1.2F);
				self.steam(level, 10);
				return;
			}
			if (gap > SLAM_REACH - 0.8) {
				if (self.tickCount % 8 == 0 || self.getNavigation().isDone()) {
					self.getNavigation().moveTo(quarry, 1.0);
				}
			} else {
				self.hold();
				self.face(quarry.position(), TURN);
			}
		}

		/** The charge, straight on: whatever it meets is struck and thrown; a wall stops it. */
		private void run(final ServerLevel level) {
			GateSentry self = GateSentry.this;
			self.getNavigation().stop();
			self.setDeltaMovement(self.rush.x * RUSH_SPEED, self.getDeltaMovement().y, self.rush.z * RUSH_SPEED);
			for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, self.getBoundingBox().inflate(0.4), self::fair)) {
				self.strike(level, hit, self.blow() * RUSH_SHARE, self.rush.scale(1.1).add(0.0, 0.5, 0.0));
			}
			if (self.tickCount % 3 == 0) {
				level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.IRON_GOLEM_STEP, SoundSource.HOSTILE, 1.2F, 0.5F);
			}
			if (self.horizontalCollision && self.moveTimer < RUSH - 2) {
				self.stuck(level);
				return;
			}
			if (--self.moveTimer <= 0) {
				self.begin(Move.RECOVER, RUSH_RECOVER);
			}
		}
	}

	/** With nothing to chase a while, back to its post, and it kneels there. */
	private final class PostGoal extends Goal {
		PostGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			GateSentry self = GateSentry.this;
			return !self.dormant() && self.move() == Move.NONE && self.post != null && self.idle >= GIVE_UP && self.getTarget() == null
				&& NoiseService.heard(self) == null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			GateSentry self = GateSentry.this;
			BlockPos post = self.post;
			if (post == null || !(self.level() instanceof ServerLevel level)) {
				return;
			}
			Vec3 at = Vec3.atBottomCenterOf(post);
			double away = self.position().distanceToSqr(at);
			if (away > 0.36) {
				if (away > 9.0) {
					if (self.getNavigation().isDone()) {
						self.getNavigation().moveTo(at.x, at.y, at.z, 0.9);
					}
					return;
				}
				// the last few steps (a way for one so broad ends short of them): turned to it first, then straight on
				self.getNavigation().stop();
				float yaw = (float) (Mth.atan2(at.z - self.getZ(), at.x - self.getX()) * Mth.RAD_TO_DEG) - 90.0F;
				if (Math.abs(Mth.wrapDegrees(yaw - self.getYRot())) > 30.0F) {
					self.hold();
					self.turn(yaw, TURN);
				} else {
					self.getMoveControl().setWantedPosition(at.x, at.y, at.z, 0.7);
				}
				return;
			}
			self.hold();
			self.turn(self.postYaw, TURN);
			if (Math.abs(Mth.wrapDegrees(self.getYRot() - self.postYaw)) < 1.0F) {
				self.sleep(level);
			}
		}
	}

	/** Walks as a heavy thing does: it turns no faster than {@link #TURN} a tick. */
	private static final class Heavy extends MoveControl<GateSentry> {
		Heavy(final GateSentry mob) {
			super(mob);
		}

		@Override
		public void tick() {
			float before = this.mob.getYRot();
			super.tick();
			float turned = Mth.wrapDegrees(this.mob.getYRot() - before);
			this.mob.setYRot(before + Mth.clamp(turned, -TURN, TURN));
		}
	}

	// ── Kept ──────────────────────────────────────────────────────

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("dormant", this.dormant());
		if (this.post != null) {
			output.putLong("post", this.post.asLong());
			output.putFloat("post_yaw", this.postYaw);
		}
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(DATA_DORMANT, input.getBooleanOr("dormant", false));
		this.post = input.getLong("post").map(BlockPos::of).orElse(null);
		this.postYaw = input.getFloatOr("post_yaw", 0.0F);
	}

	@Override
	public boolean removeWhenFarAway(final double distance) {
		return false;
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	// ── The voice ─────────────────────────────────────────────────

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return this.dormant() ? null : SoundEvents.BEACON_AMBIENT;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 200;
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	protected void playStepSound(final BlockPos pos, final BlockState state) {
		this.playSound(SoundEvents.IRON_GOLEM_STEP, 1.0F, 0.6F);
	}

	@Override
	public float getVoicePitch() {
		return 0.6F + this.random.nextFloat() * 0.1F;
	}
}
