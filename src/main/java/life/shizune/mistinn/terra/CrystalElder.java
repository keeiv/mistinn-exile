package life.shizune.mistinn.terra;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 晶冠古龍: the ancient drake grown at the heart of a Terra highland, two and a half times the
 * size of the others, crowned in pale gold crystal. It flies and stoops as they do, wider and
 * higher; besides, it calls the lattice up out of the ground under its quarry (five rings of light,
 * and a moment later the crystal erupts), and once, badly hurt, it calls two drakes to it.
 * <p>
 * Below half its strength it comes down into its nest and fights on foot a while, then takes the
 * air again, and comes down again, by turns: on the ground it sweeps all round it with its tail,
 * and charges in a straight line at a quarry farther off; a charge that ends against stone (a
 * spire of the crown, the dais, a cliff) leaves it stunned and open a moment. Brought down, it
 * leaves crystal cores enough to wake a hub, and the crown grows another in a few days.
 */
public class CrystalElder extends CrystalDrake {
	/** How often it calls the lattice up, how long the rings burn first, and how hard and wide the eruption. */
	public static final int ERUPT_EVERY = 160;
	public static final int ERUPT_WARNING = 30;
	public static final float ERUPT_DAMAGE = 8.0F;
	public static final double ERUPT_REACH = 1.8;
	private static final DustParticleOptions RING = new DustParticleOptions(0x8FF5FF, 1.4F);
	private static final DustParticleOptions COIL = new DustParticleOptions(0xFFE7A0, 1.8F);

	/** Below this share of its strength it fights in its nest by turns. */
	public static final float LAND_AT = 0.5F;
	/** How long it stays down each time, how long it keeps the air between, and how soon after coming back to the world it lands. */
	public static final int GROUND_TIME = 400;
	public static final int SKY_TIME = 300;
	public static final int RELANDS = 150;
	/** Coming down, it first flies in this high over its nest, above the spires of the crown. */
	public static final double OVER_SPIRES = 28.0;
	/** Coming down: how hard it shakes the ground and how far. */
	public static final float LANDING_DAMAGE = 6.0F;
	public static final double LANDING_REACH = 6.0;
	/** The sweep: how long it coils, how long the turn, how far the tail reaches and how hard it strikes. */
	public static final int SWEEP_WINDUP = 16;
	public static final int SWEEP_TIME = 8;
	public static final double SWEEP_REACH = 6.5;
	public static final float SWEEP_DAMAGE = 12.0F;
	/** The charge: how long it lowers its head, how long it runs at the most, how fast, how hard, and how near or far a quarry it charges. */
	public static final int CHARGE_WINDUP = 20;
	public static final int CHARGE_TIME = 26;
	public static final double CHARGE_SPEED = 0.95;
	public static final float CHARGE_DAMAGE = 16.0F;
	public static final double CHARGE_NEAR = 8.0;
	public static final double CHARGE_FAR = 24.0;
	/** Stunned against stone: how long, and how much harder it is hurt meanwhile. */
	public static final int STUN = 80;
	public static final float STAGGER = 1.5F;
	/** Between moves on the ground. */
	public static final int MOVE_GAP = 30;

	/** Whether it keeps the air or its nest. */
	public enum Stance {
		SKY, LANDING, GROUND
	}

	/** What it is doing on the ground, shown to the clients so each move is seen coming. */
	public enum Move {
		NONE, SWEEP_WINDUP, SWEEP, CHARGE_WINDUP, CHARGE, STUNNED, RECOVER
	}

	private static final EntityDataAccessor<Integer> DATA_MOVE = SynchedEntityData.defineId(CrystalElder.class, EntityDataSerializers.INT);

	private final ServerBossEvent bar = new ServerBossEvent(java.util.UUID.randomUUID(), this.getDisplayName(), BossEvent.BossBarColor.BLUE,
		BossEvent.BossBarOverlay.NOTCHED_10);
	private int erupt = ERUPT_EVERY / 2;
	private int erupting;
	private final List<Vec3> rings = new ArrayList<>();
	private boolean called;
	private Stance stance = Stance.SKY;
	private int stanceTimer = 40;
	private int moveTimer;
	private int moveGap = 20;
	private boolean descending;
	private Vec3 rush = Vec3.ZERO;
	private final Set<UUID> struck = new HashSet<>();
	private Move shownMove = Move.NONE;
	private int shownAge;

	public CrystalElder(final EntityType<? extends CrystalElder> type, final Level level) {
		super(type, level);
		this.xpReward = 120;
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 320.0)
			.add(Attributes.ARMOR, 10.0)
			.add(Attributes.ATTACK_DAMAGE, 14.0)
			.add(Attributes.ATTACK_KNOCKBACK, 1.5)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FLYING_SPEED, 0.6)
			.add(Attributes.FOLLOW_RANGE, 64.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
			.add(Attributes.STEP_HEIGHT, 1.1);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MOVE, Move.NONE.ordinal());
	}

	@Override
	protected double over() {
		return 15.0;
	}

	@Override
	protected double wheel() {
		return 18.0;
	}

	// ── The nest ──────────────────────────────────────────────────

	/** Whether it is below half its strength, and so fights in its nest by turns. */
	public boolean landed() {
		return this.getHealth() < this.getMaxHealth() * LAND_AT;
	}

	public Stance stance() {
		return this.stance;
	}

	public Move move() {
		return Move.values()[this.entityData.get(DATA_MOVE)];
	}

	/** How long its present move has gone on, as its client has seen it. */
	public float moveAge(final float partial) {
		return this.shownAge + partial;
	}

	private void begin(final Move next, final int ticks) {
		this.entityData.set(DATA_MOVE, next.ordinal());
		this.moveTimer = ticks;
		this.struck.clear();
	}

	@Override
	protected boolean grounded(final ServerLevel level, final @Nullable LivingEntity target) {
		if (!this.landed()) {
			return false;
		}
		this.stanceTimer--;
		switch (this.stance) {
			case SKY -> {
				if (this.stanceTimer <= 0) {
					this.stance = Stance.LANDING;
					this.stanceTimer = 400;
					this.descending = false;
					this.setMode(Mode.CIRCLE);
					this.playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.9F);
				}
				return false;
			}
			case LANDING -> {
				BlockPos nest = this.home() != null ? this.home() : this.blockPosition();
				Vec3 down = Vec3.atBottomCenterOf(nest);
				double across = Math.hypot(this.getX() - down.x, this.getZ() - down.z);
				// in over the spires of the crown, then straight down into the ring
				this.descending = across < (this.descending ? 7.0 : 3.0);
				this.aim(this.descending ? down : down.add(0.0, OVER_SPIRES, 0.0));
				this.steer(0.7);
				if (this.descending && (this.onGround() || this.getY() - down.y < 1.0) || this.stanceTimer <= 0) {
					this.touchDown(level);
				}
				return true;
			}
			case GROUND -> {
				this.onFoot(level, target);
				return true;
			}
		}
		return false;
	}

	/** It comes down on the dais, and the ground shakes. */
	private void touchDown(final ServerLevel level) {
		this.stance = Stance.GROUND;
		this.stanceTimer = GROUND_TIME;
		this.setMode(Mode.REST);
		this.setDeltaMovement(Vec3.ZERO);
		this.begin(Move.RECOVER, 20);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0F, 0.5F);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, MistinnBlocks.VEIN_STONE.defaultBlockState()),
			this.getX(), this.getY() + 0.2, this.getZ(), 120, 3.5, 0.2, 3.5, 0.25);
		for (LivingEntity hit : this.quarry(level, LANDING_REACH, 2.5)) {
			this.strike(level, hit, LANDING_DAMAGE, flat(hit.position().subtract(this.position())).add(0.0, 0.5, 0.0));
		}
	}

	/** It takes the air again from its nest. */
	private void takeOff() {
		this.stance = Stance.SKY;
		this.stanceTimer = SKY_TIME;
		this.begin(Move.NONE, 0);
		this.setMode(Mode.CIRCLE);
		this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.9, 0.0));
		this.playSound(SoundEvents.ENDER_DRAGON_FLAP, 3.0F, 0.8F);
	}

	/** A moment on the ground: walking at its quarry, coiling, sweeping, charging, or stunned. */
	private void onFoot(final ServerLevel level, final @Nullable LivingEntity target) {
		Move move = this.move();
		this.moveTimer--;
		switch (move) {
			case NONE -> {
				if (this.stanceTimer <= 0) {
					this.takeOff();
					return;
				}
				if (target == null) {
					this.hold();
					return;
				}
				this.face(target.position(), 20.0F);
				double gap = Math.hypot(target.getX() - this.getX(), target.getZ() - this.getZ()) - this.getBbWidth() / 2.0;
				if (--this.moveGap <= 0) {
					if (gap < SWEEP_REACH - 1.5) {
						this.hold();
						this.begin(Move.SWEEP_WINDUP, SWEEP_WINDUP);
						this.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 3.0F, 0.6F);
						return;
					}
					if (gap >= CHARGE_NEAR && gap <= CHARGE_FAR && this.hasLineOfSight(target)) {
						this.hold();
						this.begin(Move.CHARGE_WINDUP, CHARGE_WINDUP);
						this.playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.6F);
						return;
					}
				}
				this.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.3);
			}
			case SWEEP_WINDUP -> {
				this.hold();
				if (target != null) {
					this.face(target.position(), 8.0F);
				}
				if (this.tickCount % 2 == 0) {
					for (int i = 0; i < 16; i++) {
						double angle = i * Math.PI / 8.0;
						level.sendParticles(COIL, this.getX() + Math.cos(angle) * SWEEP_REACH, this.getY() + 0.2, this.getZ() + Math.sin(angle) * SWEEP_REACH,
							1, 0.0, 0.0, 0.0, 0.0);
					}
				}
				if (this.moveTimer <= 0) {
					this.begin(Move.SWEEP, SWEEP_TIME);
					this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 3.0F, 0.5F);
				}
			}
			case SWEEP -> {
				this.hold();
				if (this.moveTimer == SWEEP_TIME / 2) {
					for (LivingEntity hit : this.quarry(level, SWEEP_REACH, 3.0)) {
						this.strike(level, hit, SWEEP_DAMAGE, flat(hit.position().subtract(this.position())).scale(1.3).add(0.0, 0.45, 0.0));
					}
					level.sendParticles(ParticleTypes.SWEEP_ATTACK, this.getX(), this.getY() + 1.0, this.getZ(), 10, SWEEP_REACH * 0.6, 0.3, SWEEP_REACH * 0.6, 0.0);
				}
				if (this.moveTimer <= 0) {
					this.begin(Move.RECOVER, 14);
				}
			}
			case CHARGE_WINDUP -> {
				this.hold();
				if (target != null && this.moveTimer > 4) {
					this.face(target.position(), 15.0F);
					this.rush = flat(target.position().subtract(this.position()));
				}
				if (this.tickCount % 3 == 0) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, MistinnBlocks.VEIN_STONE.defaultBlockState()),
						this.getX(), this.getY() + 0.1, this.getZ(), 10, 1.5, 0.1, 1.5, 0.1);
				}
				if (this.moveTimer <= 0) {
					if (this.rush.lengthSqr() < 1.0E-4) {
						this.rush = flat(this.getLookAngle());
					}
					this.begin(Move.CHARGE, CHARGE_TIME);
					this.playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 0.7F);
				}
			}
			case CHARGE -> {
				this.face(this.position().add(this.rush), 30.0F);
				if (this.horizontalCollision && this.moveTimer < CHARGE_TIME - 1) {
					this.slam(level);
					return;
				}
				this.setDeltaMovement(this.rush.x * CHARGE_SPEED, this.getDeltaMovement().y, this.rush.z * CHARGE_SPEED);
				for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.6), this::fair)) {
					if (this.struck.add(hit.getUUID())) {
						Vec3 side = flat(hit.position().subtract(this.position()));
						this.strike(level, hit, CHARGE_DAMAGE, this.rush.scale(1.6).add(side.scale(0.6)).add(0.0, 0.5, 0.0));
					}
				}
				if (this.moveTimer <= 0) {
					this.setDeltaMovement(Vec3.ZERO);
					this.begin(Move.RECOVER, 20);
				}
			}
			case STUNNED -> {
				this.hold();
				this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
				if (this.tickCount % 4 == 0) {
					level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 3.2, this.getZ(), 3, 1.0, 0.3, 1.0, 0.02);
				}
				if (this.moveTimer <= 0) {
					this.begin(Move.RECOVER, 10);
				}
			}
			case RECOVER -> {
				this.hold();
				if (this.moveTimer <= 0) {
					this.begin(Move.NONE, 0);
					this.moveGap = MOVE_GAP;
				}
			}
		}
	}

	/** A charge ends against stone: it is stunned a while, and hurt harder meanwhile. */
	private void slam(final ServerLevel level) {
		this.setDeltaMovement(this.rush.scale(-0.3));
		this.begin(Move.STUNNED, STUN);
		// whoever drew it into the stone, and all near enough to see it
		for (ServerPlayer near : level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(24.0), p -> p.isAlive() && !p.isSpectator())) {
			AchievementService.grant(near, AchievementService.ELDER_STUNNED);
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 3.0F, 0.4F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5F, 0.5F);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, MistinnBlocks.LATTICE_CRYSTAL.defaultBlockState()),
			this.getX() + this.rush.x * 2.6, this.getY() + 1.5, this.getZ() + this.rush.z * 2.6, 80, 0.8, 1.0, 0.8, 0.3);
	}

	public boolean stunned() {
		return this.move() == Move.STUNNED;
	}

	/** Stands where it is. */
	private void hold() {
		this.getNavigation().stop();
		this.getMoveControl().setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0);
	}

	/** Turns to face a place, a little at a time. */
	private void face(final Vec3 at, final float most) {
		float yaw = (float) (Mth.atan2(at.z - this.getZ(), at.x - this.getX()) * Mth.RAD_TO_DEG) - 90.0F;
		this.setYRot(Mth.approachDegrees(this.getYRot(), yaw, most));
		this.yBodyRot = this.getYRot();
		this.yHeadRot = this.getYRot();
	}

	private static Vec3 flat(final Vec3 v) {
		Vec3 across = new Vec3(v.x, 0.0, v.z);
		return across.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : across.normalize();
	}

	/** Whether one may be struck by it: alive, not one of its own, not a watcher or a builder. */
	private boolean fair(final LivingEntity entity) {
		return entity.isAlive() && !(entity instanceof CrystalDrake) && !entity.isSpectator() && !(entity instanceof Player player && player.isCreative());
	}

	/** All it may strike this near it, across the ground. */
	private List<LivingEntity> quarry(final ServerLevel level, final double reach, final double height) {
		return level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(reach, height, reach),
			e -> this.fair(e) && Math.hypot(e.getX() - this.getX(), e.getZ() - this.getZ()) <= reach + this.getBbWidth() / 2.0);
	}

	/** Strikes one and flings it. */
	private void strike(final ServerLevel level, final LivingEntity hit, final float damage, final Vec3 fling) {
		hit.hurtServer(level, level.damageSources().mobAttack(this), damage);
		hit.setDeltaMovement(fling);
		hit.syncVelocity = true;
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
		return super.hurtServer(level, source, this.stunned() ? amount * STAGGER : amount);
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
	}

	// ── In the air ────────────────────────────────────────────────

	@Override
	protected void abilities(final ServerLevel level, final @Nullable LivingEntity target) {
		this.bar.setProgress(this.getHealth() / this.getMaxHealth());
		if (!this.called && this.getHealth() < this.getMaxHealth() * 0.5F) {
			this.called = true;
			this.call(level, target);
		}
		if (!this.rings.isEmpty()) {
			if (--this.erupting <= 0) {
				this.eruptAll(level);
			} else if (this.tickCount % 3 == 0) {
				for (Vec3 at : this.rings) {
					for (int i = 0; i < 12; i++) {
						double angle = i * Math.PI / 6.0;
						level.sendParticles(RING, at.x + Math.cos(angle) * ERUPT_REACH, at.y + 0.15, at.z + Math.sin(angle) * ERUPT_REACH, 1, 0.0, 0.0, 0.0, 0.0);
					}
				}
			}
			return;
		}
		if (--this.erupt <= 0 && target != null && this.stance != Stance.LANDING && this.distanceTo(target) < 48.0F && this.hasLineOfSight(target)) {
			this.erupt = this.landed() ? ERUPT_EVERY * 3 / 4 : ERUPT_EVERY;
			this.rings.add(ground(level, target.position()));
			for (int i = 0; i < 4; i++) {
				double angle = i * Math.PI / 2.0 + this.random.nextDouble() * 0.8;
				double reach = 3.0 + this.random.nextDouble() * 3.0;
				this.rings.add(ground(level, target.position().add(Math.cos(angle) * reach, 0.0, Math.sin(angle) * reach)));
			}
			this.erupting = ERUPT_WARNING;
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2.0F, 0.5F);
		}
	}

	private static Vec3 ground(final ServerLevel level, final Vec3 at) {
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
		return new Vec3(at.x, Math.abs(top.getY() - at.y) < 6.0 ? top.getY() : at.y, at.z);
	}

	/** The lattice erupts under every ring at once. */
	private void eruptAll(final ServerLevel level) {
		BlockParticleOption shards = new BlockParticleOption(ParticleTypes.BLOCK, MistinnBlocks.LATTICE_CRYSTAL.defaultBlockState());
		for (Vec3 at : this.rings) {
			level.sendParticles(shards, at.x, at.y + 0.8, at.z, 40, 0.5, 0.9, 0.5, 0.25);
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 12, 0.3, 1.2, 0.3, 0.06);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.6F, 0.6F);
			for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(ERUPT_REACH, 3.0, ERUPT_REACH),
				e -> e.isAlive() && !(e instanceof CrystalDrake) && e.position().distanceTo(at) <= ERUPT_REACH + 0.5)) {
				hit.hurtServer(level, level.damageSources().mobAttack(this), ERUPT_DAMAGE);
				hit.setDeltaMovement(hit.getDeltaMovement().add(0.0, 0.7, 0.0));
				hit.syncVelocity = true;
			}
		}
		this.rings.clear();
	}

	/** Badly hurt, it calls two drakes out of the crown to it. */
	private void call(final ServerLevel level, final @Nullable LivingEntity target) {
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0F, 1.4F);
		for (int i = 0; i < 2; i++) {
			CrystalDrake drake = MistinnEntities.CRYSTAL_DRAKE.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (drake != null) {
				drake.snapTo(this.getX() + (i == 0 ? 6.0 : -6.0), this.getY() + 2.0, this.getZ(), this.getYRot(), 0.0F);
				drake.setTarget(target);
				level.addFreshEntity(drake);
				level.sendParticles(ParticleTypes.END_ROD, drake.getX(), drake.getY() + 1.0, drake.getZ(), 30, 1.0, 0.6, 1.0, 0.08);
			}
		}
	}

	/** Where the rift will erupt next, for tests. */
	public List<Vec3> rings() {
		return List.copyOf(this.rings);
	}

	public boolean called() {
		return this.called;
	}

	@Override
	public void die(final DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			Elders.died(level, this);
			ServerPlayer killer = source.getEntity() instanceof ServerPlayer player ? player
				: this.getLastHurtByPlayer() instanceof ServerPlayer striker ? striker : null;
			if (killer != null) {
				AchievementService.grant(killer, AchievementService.ELDER_SLAIN);
				List<net.minecraft.world.item.Item> pieces = MistinnItems.gearOfTier(5);
				this.spawnAtLocation(level, new ItemStack(pieces.get(level.getRandom().nextInt(pieces.size()))));
			}
		}
	}

	@Override
	public void startSeenByPlayer(final ServerPlayer player) {
		super.startSeenByPlayer(player);
		this.bar.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(final ServerPlayer player) {
		super.stopSeenByPlayer(player);
		this.bar.removePlayer(player);
	}

	@Override
	public void remove(final RemovalReason reason) {
		this.bar.removeAllPlayers();
		super.remove(reason);
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("called", this.called);
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.called = input.getBooleanOr("called", false);
		// back in the world, it takes the air first and comes down to its nest a little later
		this.stance = Stance.SKY;
		this.stanceTimer = RELANDS;
		this.setMode(Mode.CIRCLE);
	}

	@Override
	public boolean removeWhenFarAway(final double distance) {
		return false;
	}

	@Override
	protected float getSoundVolume() {
		return 4.0F;
	}

	@Override
	public float getVoicePitch() {
		return 0.45F + this.random.nextFloat() * 0.1F;
	}
}
