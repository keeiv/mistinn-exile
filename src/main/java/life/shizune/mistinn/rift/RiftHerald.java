package life.shizune.mistinn.rift;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 裂隙使者: what comes down last out of a sky rift: tall, robed in tatters, crowned with a ring of
 * the rift's crystal. Besides its blows it has two works: it drags a hunter who keeps their
 * distance in towards it, and it marks the ground round its quarry with three rings of red light
 * that the rift strikes a moment later.
 * <p>
 * Below half its strength its wrath comes on it: it roars and throws back all near it, the rift
 * then strikes in a line running out from its feet to its quarry, ring after ring, and now and
 * then it tears a rift, steps through, and comes out behind its quarry.
 */
public class RiftHerald extends Monster implements RiftTouched {
	private static final EntityDataAccessor<Byte> DATA_CAST = SynchedEntityData.defineId(RiftHerald.class, EntityDataSerializers.BYTE);

	/** What it is doing with its hands, for the eye: nothing, dragging a hunter in, or calling the rift down. */
	public enum Cast {
		NONE, DRAG, MARK
	}

	/** How long a cast shows. */
	public static final int CAST_SHOWN = 24;
	/** How long between drags and between strikes, and how long a mark burns before the strike. */
	public static final int DRAG_EVERY = 160;
	public static final int STRIKE_EVERY = 220;
	public static final int MARKED = 30;
	/** How far a strike reaches, and how hard it hits. */
	public static final double STRIKE_REACH = 2.5;
	public static final float STRIKE_DAMAGE = 7.0F;
	private static final DustParticleOptions MARK = new DustParticleOptions(0xE03A1E, 1.4F);
	/** Below this share of its strength, its wrath. */
	public static final float WRATH = 0.5F;
	/** The roar as its wrath comes on: how far it throws back. */
	public static final double ROAR_REACH = 6.0;
	/** The line of strikes: how many rings, how far apart, and how long after each the next. */
	public static final int LINE = 7;
	public static final double LINE_STEP = 2.4;
	public static final int LINE_GAP = 4;
	/** Stepping through the rift: how often, how long the way out shows first, and how far behind its quarry it comes out. */
	public static final int BLINK_EVERY = 200;
	public static final int BLINK_SHOWN = 14;
	public static final double BEHIND = 3.5;

	private final ServerBossEvent bar = new ServerBossEvent(java.util.UUID.randomUUID(), this.getDisplayName(), BossEvent.BossBarColor.RED,
		BossEvent.BossBarOverlay.NOTCHED_10);
	private int drag = DRAG_EVERY / 2;
	private int strike = STRIKE_EVERY / 2;
	/** Where the rift will strike, and in how many ticks each. */
	private final List<Vec3> marks = new ArrayList<>();
	private final List<Integer> lands = new ArrayList<>();
	private boolean wrath;
	private int blink = BLINK_EVERY / 2;
	private int stepping;
	private @Nullable Vec3 way;
	private int casting;
	private float castO;
	private float castAmount;

	public RiftHerald(final EntityType<? extends RiftHerald> type, final Level level) {
		super(type, level);
		this.xpReward = 40;
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 180.0)
			.add(Attributes.ATTACK_DAMAGE, 9.0)
			.add(Attributes.ATTACK_KNOCKBACK, 1.0)
			.add(Attributes.MOVEMENT_SPEED, 0.27)
			.add(Attributes.FOLLOW_RANGE, 48.0)
			.add(Attributes.ARMOR, 8.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_CAST, (byte) 0);
	}

	public Cast cast() {
		return Cast.values()[Math.floorMod(this.entityData.get(DATA_CAST), Cast.values().length)];
	}

	private void cast(final Cast what) {
		this.entityData.set(DATA_CAST, (byte) what.ordinal());
		this.casting = what == Cast.NONE ? 0 : CAST_SHOWN;
	}

	/** How far into a cast its pose is, 0 to 1. */
	public float casting(final float partial) {
		return Mth.lerp(partial, this.castO, this.castAmount);
	}

	@Override
	public void tick() {
		super.tick();
		this.castO = this.castAmount;
		this.castAmount = Mth.approach(this.castAmount, this.cast() != Cast.NONE ? 1.0F : 0.0F, 0.18F);
		if (!this.level().isClientSide() && this.casting > 0 && --this.casting == 0) {
			this.cast(Cast.NONE);
		}
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15, true));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 24.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		this.bar.setProgress(this.getHealth() / this.getMaxHealth());
		LivingEntity target = this.getTarget();
		if (--this.drag <= 0 && target != null) {
			double distance = this.distanceTo(target);
			if (distance >= 6.0 && distance <= 18.0 && this.hasLineOfSight(target)) {
				this.dragIn(level, target);
				this.drag = DRAG_EVERY;
			} else {
				this.drag = 20;
			}
		}
		if (!this.wrath && this.getHealth() < this.getMaxHealth() * WRATH) {
			this.roar(level);
		}
		if (--this.strike <= 0 && target != null && this.marks.isEmpty()) {
			if (this.wrath) {
				this.line(level, target);
			} else {
				this.mark(level, target);
			}
			this.strike = STRIKE_EVERY;
		}
		if (!this.marks.isEmpty()) {
			this.landMarks(level);
		}
		if (this.wrath) {
			this.stepThrough(level, target);
		}
	}

	/** Whether its wrath has come on it. */
	public boolean wrathful() {
		return this.wrath;
	}

	/** Its wrath comes on it: a roar, and everyone near thrown back. */
	private void roar(final ServerLevel level) {
		this.wrath = true;
		this.cast(Cast.MARK);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.5F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 3.0F, 0.4F);
		level.sendParticles(MARK, this.getX(), this.getY() + 1.4, this.getZ(), 80, ROAR_REACH * 0.4, 0.6, ROAR_REACH * 0.4, 0.0);
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(ROAR_REACH),
			e -> e.isAlive() && !(e instanceof RiftTouched) && e.distanceTo(this) <= ROAR_REACH)) {
			Vec3 away = near.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
			Vec3 push = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
			near.setDeltaMovement(push.x * 1.4, 0.5, push.z * 1.4);
			near.syncVelocity = true;
		}
	}

	/** In its wrath: the rift strikes in a line from its feet out to its quarry and past, ring after ring. */
	private void line(final ServerLevel level, final LivingEntity target) {
		this.cast(Cast.MARK);
		this.marks.clear();
		this.lands.clear();
		Vec3 across = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
		Vec3 step = (across.lengthSqr() < 1.0E-4 ? this.getLookAngle().multiply(1.0, 0.0, 1.0) : across).normalize().scale(LINE_STEP);
		for (int i = 1; i <= LINE; i++) {
			this.marks.add(ground(level, this.position().add(step.scale(i))));
			this.lands.add(MARKED + i * LINE_GAP);
		}
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.4F, 0.45F);
	}

	/** The marks burn, and each strikes in its turn. */
	private void landMarks(final ServerLevel level) {
		for (int i = this.marks.size() - 1; i >= 0; i--) {
			int left = this.lands.get(i) - 1;
			if (left <= 0) {
				this.strikeAt(level, this.marks.get(i));
				this.marks.remove(i);
				this.lands.remove(i);
			} else {
				this.lands.set(i, left);
				if (this.tickCount % 3 == 0) {
					ring(level, this.marks.get(i));
				}
			}
		}
	}

	/**
	 * In its wrath, now and then: it tears a rift where it will come out, behind its quarry (shown a
	 * moment first), and steps through it.
	 */
	private void stepThrough(final ServerLevel level, final @Nullable LivingEntity target) {
		if (this.way != null) {
			Vec3 out = this.way;
			if (this.tickCount % 2 == 0) {
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, out.x, out.y + 1.2, out.z, 12, 0.3, 0.8, 0.3, 0.02);
				level.sendParticles(MARK, out.x, out.y + 1.2, out.z, 6, 0.15, 0.9, 0.15, 0.0);
			}
			if (--this.stepping <= 0) {
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + 1.2, this.getZ(), 40, 0.4, 1.0, 0.4, 0.05);
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.5F, 0.5F);
				this.teleportTo(out.x, out.y, out.z);
				if (target != null) {
					this.getLookControl().setLookAt(target, 180.0F, 90.0F);
					float yaw = (float) (Mth.atan2(target.getZ() - out.z, target.getX() - out.x) * Mth.RAD_TO_DEG) - 90.0F;
					this.setYRot(yaw);
					this.yBodyRot = yaw;
					this.yHeadRot = yaw;
				}
				level.playSound(null, out.x, out.y, out.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.5F, 0.45F);
				this.way = null;
				this.blink = BLINK_EVERY;
			}
			return;
		}
		if (--this.blink <= 0 && target != null && this.distanceTo(target) > 3.0F) {
			Vec3 look = target.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
			Vec3 back = look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize().scale(-1.0);
			Vec3 out = ground(level, target.position().add(back.scale(BEHIND)));
			if (level.noCollision(this, this.getBoundingBox().move(out.subtract(this.position())))) {
				this.way = out;
				this.stepping = BLINK_SHOWN;
				this.cast(Cast.DRAG);
				level.playSound(null, out.x, out.y, out.z, SoundEvents.PORTAL_TRIGGER, SoundSource.HOSTILE, 0.6F, 1.8F);
			} else {
				this.blink = 20;
			}
		}
	}

	/** Where it will come out of the rift, while it shows: for tests. */
	public @Nullable Vec3 way() {
		return this.way;
	}

	/** Hauls a hunter who keeps their distance in towards it along a streak of light. */
	private void dragIn(final ServerLevel level, final LivingEntity target) {
		this.cast(Cast.DRAG);
		Vec3 pull = this.position().subtract(target.position()).normalize();
		target.setDeltaMovement(pull.x * 1.15, 0.38, pull.z * 1.15);
		target.syncVelocity = true;
		Vec3 from = this.getEyePosition();
		Vec3 to = target.getEyePosition();
		for (int i = 0; i <= 16; i++) {
			Vec3 p = from.lerp(to, i / 16.0);
			level.sendParticles(MARK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 1.6F, 0.5F);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.HOSTILE, 1.0F, 0.6F);
	}

	/** Three rings of red light on the ground round its quarry: under them, and on either side. */
	private void mark(final ServerLevel level, final LivingEntity target) {
		this.cast(Cast.MARK);
		this.marks.clear();
		this.marks.add(ground(level, target.position()));
		for (int i = 0; i < 2; i++) {
			double angle = this.random.nextDouble() * Math.PI * 2.0;
			double reach = 3.0 + this.random.nextDouble() * 2.5;
			this.marks.add(ground(level, target.position().add(Math.cos(angle) * reach, 0.0, Math.sin(angle) * reach)));
		}
		this.lands.clear();
		for (int i = 0; i < this.marks.size(); i++) {
			this.lands.add(MARKED);
		}
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.4F, 0.6F);
	}

	private static Vec3 ground(final ServerLevel level, final Vec3 at) {
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
		return new Vec3(at.x, Math.abs(top.getY() - at.y) < 6.0 ? top.getY() : at.y, at.z);
	}

	private static void ring(final ServerLevel level, final Vec3 at) {
		for (int i = 0; i < 12; i++) {
			double angle = i * Math.PI / 6.0;
			level.sendParticles(MARK, at.x + Math.cos(angle) * STRIKE_REACH, at.y + 0.15, at.z + Math.sin(angle) * STRIKE_REACH, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	private void strikeAt(final ServerLevel level, final Vec3 at) {
		LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(at);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(STRIKE_REACH, 3.0, STRIKE_REACH),
			e -> e.isAlive() && !(e instanceof RiftTouched) && e.position().distanceTo(at) <= STRIKE_REACH + 0.5)) {
			hit.hurtServer(level, level.damageSources().indirectMagic(this, this), STRIKE_DAMAGE);
		}
	}

	/** Where the rift will strike next, for tests. */
	public List<Vec3> marks() {
		return List.copyOf(this.marks);
	}

	// ── The bar ───────────────────────────────────────────────────

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
		output.putBoolean("wrath", this.wrath);
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.wrath = input.getBooleanOr("wrath", false);
	}

	@Override
	public boolean removeWhenFarAway(final double distance) {
		return false;
	}

	// ── The voice ─────────────────────────────────────────────────

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return SoundEvents.RAVAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return SoundEvents.AMETHYST_BLOCK_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.AMETHYST_BLOCK_BREAK;
	}

	@Override
	public float getVoicePitch() {
		return 0.45F;
	}

	@Override
	protected float getSoundVolume() {
		return 1.6F;
	}
}
