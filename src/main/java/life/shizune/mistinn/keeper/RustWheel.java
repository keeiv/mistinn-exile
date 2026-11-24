package life.shizune.mistinn.keeper;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.boss.BossMove;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.salt.SaltService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 鏽輪, the third Lighthouse Keeper: the great brine wheel of a dead salt works, still turning, a man chained across the
 * yoke at its back. Its front is the iron-shod rim, which turns most of a blow aside; its back is the axle and the man,
 * where blows go home. It rolls at hunters and its momentum builds with every run; a wall or a pillar stops it dead and
 * leaves it stuck, its back bare, and a full heavy blow then throws it off balance at once. A hunter who keeps to its back
 * too long gets the chain. Below half health the man tears one chain loose, rim plates fall away, its runs turn toward the
 * hunter, and it spills brine where it slams down.
 */
public class RustWheel extends Keeper {
	public static final float MAX_HEALTH = 420.0F;
	/** The phases of its fight, as its moves know them. */
	public static final String WHOLE = "whole";
	public static final String BROKEN = "broken";
	/** How far its yard reaches from where it stands, and the ring its circuit keeps to. */
	public static final int ROOM_RADIUS = 13;
	public static final double CIRCUIT_RADIUS = 9.5;
	/** What a blow on its front, the iron rim, does of its full weight: whole, and once its plates are gone. */
	public static final float FRONT_WHOLE = 0.35F;
	public static final float FRONT_BROKEN = 0.55F;
	/** What a blow on its back, the axle and the man, does. */
	public static final float BACK = 1.5F;
	/** The cosine of the half-angle of its front and of its back (60 degrees either side). */
	private static final double ARC = 0.5;
	/** The most momentum it builds. */
	public static final int MAX_MOMENTUM = 3;
	/** Ticks a hunter may stand at its back before the chain comes. */
	public static final int LASH_AFTER = 16;
	/** Ticks the chain rests after a lash: the wheel has its turn to come round before it can lash again. */
	public static final int LASH_REST = 60;
	/** How fast it turns when it is not rolling, degrees a tick: a heavy wheel comes round slowly, and that is the way to its back. */
	public static final float TURN = 2.5F;
	/** How long a pool of brine lies, and how far it spreads. */
	public static final int BRINE_TICKS = 300;
	public static final double BRINE_RADIUS = 2.2;

	private static final EntityDataAccessor<Byte> DATA_MOMENTUM = SynchedEntityData.defineId(RustWheel.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_BROKEN = SynchedEntityData.defineId(RustWheel.class, EntityDataSerializers.BOOLEAN);

	private boolean broken;
	/** Ticks a hunter has kept to its back. */
	private int behindTicks;
	/** Ticks until the chain may come again. */
	private int lashRest;
	/** Whether the last blow taken came from behind: a full heavy blow from there counts twice toward its balance. */
	private boolean lastFromBehind;
	private final List<Brine> brine = new ArrayList<>();
	/** On the client: how far the wheel has turned, and how far it had the tick before, for the renderer. */
	private float turned;
	private float turnedBefore;

	/** A pool of spilled brine: where, and ticks left. */
	public record Brine(Vec3 at, int ticks) {
	}

	public RustWheel(final EntityType<? extends RustWheel> type, final Level level) {
		super(type, level, WheelMoves.SET, BossEvent.BossBarColor.RED);
		this.xpReward = 160;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, MAX_HEALTH)
			.add(Attributes.ARMOR, 12.0)
			.add(Attributes.ATTACK_DAMAGE, 8.0)
			.add(Attributes.MOVEMENT_SPEED, 0.24)
			.add(Attributes.FOLLOW_RANGE, 40.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MOMENTUM, (byte) 0);
		builder.define(DATA_BROKEN, false);
	}

	@Override
	protected String key() {
		return "rust_wheel";
	}

	@Override
	protected int roomRadius() {
		return ROOM_RADIUS;
	}

	@Override
	protected String phase() {
		return this.broken ? BROKEN : WHOLE;
	}

	public boolean broken() {
		return this.level().isClientSide() ? this.entityData.get(DATA_BROKEN) : this.broken;
	}

	public int momentum() {
		return this.entityData.get(DATA_MOMENTUM);
	}

	/** A run finished without hitting anything: a layer more momentum. */
	public void gainMomentum() {
		this.setMomentum(Math.min(MAX_MOMENTUM, this.momentum() + 1));
	}

	/** Its momentum, shown on its health bar a mark a layer, so a hunter can read how hard the next run will come. */
	private void setMomentum(final int momentum) {
		this.entityData.set(DATA_MOMENTUM, (byte) momentum);
		this.showOnBar(momentum <= 0 ? null : "entity.mistinn.rust_wheel.momentum", momentum, MAX_MOMENTUM);
	}

	/** The middle of its yard. */
	public Vec3 room() {
		return this.home == null ? this.position() : this.home;
	}

	/** Whether a point stands inside its yard. */
	public boolean insideRoom(final Vec3 at) {
		Vec3 centre = this.room();
		double dx = at.x - centre.x;
		double dz = at.z - centre.z;
		return dx * dx + dz * dz <= ROOM_RADIUS * ROOM_RADIUS;
	}

	/** Whether {@code at} lies at the back of {@code wheel}: within sixty degrees of straight behind it. */
	public static boolean behind(final Entity wheel, final Vec3 at) {
		return side(wheel, at) < -ARC;
	}

	/** Whether {@code at} lies in front of {@code wheel}. */
	public static boolean front(final Entity wheel, final Vec3 at) {
		return side(wheel, at) > ARC;
	}

	/** The cosine of the angle between where it faces and where {@code at} lies. */
	private static double side(final Entity wheel, final Vec3 at) {
		Vec3 to = at.subtract(wheel.position()).multiply(1.0, 0.0, 1.0);
		if (to.lengthSqr() < 1.0E-4) {
			return 0.0;
		}
		float yaw = wheel.getYRot() * Mth.DEG_TO_RAD;
		Vec3 facing = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		return facing.dot(to.normalize());
	}

	/** What a blow from {@code from} does of its full weight: little on the rim, more on the axle. */
	public float struckFrom(final Vec3 from) {
		if (front(this, from)) {
			return this.broken ? FRONT_BROKEN : FRONT_WHOLE;
		}
		return behind(this, from) ? BACK : 1.0F;
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float damage) {
		Vec3 from = source.getSourcePosition();
		if (from == null && source.getEntity() != null) {
			from = source.getEntity().position();
		}
		float scale = from == null ? 1.0F : this.struckFrom(from);
		this.lastFromBehind = from != null && behind(this, from);
		if (from != null && front(this, from)) {
			// iron on iron
			level.playSound(null, this.blockPosition(), MistinnSounds.RUST_WHEEL_CLANG, SoundSource.HOSTILE, 0.8F, 1.0F);
			level.sendParticles(ParticleTypes.CRIT, from.x * 0.3 + this.getX() * 0.7, this.getY() + 1.2, from.z * 0.3 + this.getZ() * 0.7, 10, 0.3, 0.3, 0.3, 0.3);
		}
		return super.hurtServer(level, source, damage * scale);
	}

	/**
	 * A full heavy blow: stuck, it is thrown off balance at once; from behind, it counts twice; from anywhere else as on
	 * any keeper.
	 */
	@Override
	public void takeHeavyBlow(final ServerLevel level) {
		if (this.staggered() || this.isDeadOrDying()) {
			return;
		}
		if (this.stuck()) {
			this.stagger(level, "message.mistinn.rust_wheel.stagger_stuck");
			return;
		}
		super.takeHeavyBlow(level);
		if (this.lastFromBehind && !this.staggered()) {
			super.takeHeavyBlow(level);
		}
	}

	/** Whether it is stuck where it ran into stone. */
	public boolean stuck() {
		BossMove current = this.currentMove();
		return current != null && "stuck".equals(current.id());
	}

	/** It ran into stone: the run ends, its momentum with it, and it is stuck there. */
	public void crash(final ServerLevel level) {
		this.setMomentum(0);
		level.playSound(null, this.blockPosition(), MistinnSounds.RUST_WHEEL_CRASH, SoundSource.HOSTILE, 2.0F, 1.0F);
		level.playSound(null, this.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.4F, 0.7F);
		level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 1.2, this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 1.2, this.getZ(), 30, 0.8, 0.8, 0.8, 0.4);
		if (this.broken) {
			this.spill(level, this.blockPosition());
		}
		this.perform("stuck");
	}

	/** Brine spilled where it slammed or crashed: a pool that slows whoever wades in it. */
	public void spill(final ServerLevel level, final BlockPos at) {
		this.brine.add(new Brine(Vec3.atBottomCenterOf(at), BRINE_TICKS));
		level.playSound(null, at, SoundEvents.BUCKET_EMPTY, SoundSource.HOSTILE, 1.2F, 0.6F);
	}

	/** The pools of brine lying in its yard now. */
	public List<Brine> brine() {
		return List.copyOf(this.brine);
	}

	/** The yard of a salt works: where it first stood, when the yard's pillars stand round it; none elsewhere. */
	@Override
	protected MistWall.@Nullable Room sealedRoom(final ServerLevel level) {
		if (this.home == null) {
			return null;
		}
		BlockPos centre = BlockPos.containing(this.home);
		for (int[] pillar : life.shizune.mistinn.world.SaltWorks.PILLARS) {
			if (!level.getBlockState(centre.offset(pillar[0], 1, pillar[1])).isSolidRender()) {
				return null;
			}
		}
		return new MistWall.Room(centre, centre.getY() - 1, life.shizune.mistinn.world.SaltWorks.YARD + 0.5, life.shizune.mistinn.world.SaltWorks.YARD - 0.8);
	}

	@Override
	protected void roomRules(final ServerLevel level) {
		if (!this.broken && this.getHealth() <= this.getMaxHealth() / 2.0F) {
			this.breakLoose(level);
		}
		this.brineTick(level);
		// a hunter who keeps to its back too long gets the chain
		this.behindTicks = this.backHunter(level) == null ? 0 : this.behindTicks + 1;
		if (this.lashRest > 0) {
			this.lashRest--;
		}
		if (this.behindTicks >= LASH_AFTER && this.lashRest <= 0 && this.mayLash() && this.staggered <= 0 && !this.isDeadOrDying()) {
			this.behindTicks = 0;
			// the lash, then a rest: it cannot turn while it lashes, and would lash a hunter still at its back for ever
			this.lashRest = WheelMoves.CHAIN_LASH.length() + LASH_REST;
			this.perform("chain_lash");
		}
	}

	/**
	 * Whether the man may swing his chain now: when the wheel is doing nothing, or only shifting, feinting, or still
	 * winding up a blow. Not mid-run, not jammed, not reeling.
	 */
	private boolean mayLash() {
		BossMove move = this.currentMove();
		if (move == null) {
			return true;
		}
		return switch (move.kind()) {
			case MOVEMENT, FEINT, SIGNAL, IDLE -> true;
			case ATTACK -> this.moveTicks() < move.windup() && !"circuit".equals(move.id());
			default -> false;
		};
	}

	/** A hunter standing at its back, near enough for the chain, if there is one. */
	private @Nullable Player backHunter(final ServerLevel level) {
		for (Player player : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(WheelMoves.LASH_REACH, 2.0, WheelMoves.LASH_REACH))) {
			if (player.isAlive() && !player.isSpectator() && !player.isCreative() && behind(this, player.position())) {
				return player;
			}
		}
		return null;
	}

	private void brineTick(final ServerLevel level) {
		for (int i = this.brine.size() - 1; i >= 0; i--) {
			Brine pool = this.brine.get(i);
			if (pool.ticks() <= 0) {
				this.brine.remove(i);
				continue;
			}
			this.brine.set(i, new Brine(pool.at(), pool.ticks() - 1));
			if (pool.ticks() % 5 == 0) {
				level.sendParticles(ParticleTypes.FALLING_WATER, pool.at().x, pool.at().y + 0.1, pool.at().z, 4, BRINE_RADIUS / 2.0, 0.0, BRINE_RADIUS / 2.0, 0.0);
				level.sendParticles(ParticleTypes.WHITE_ASH, pool.at().x, pool.at().y + 0.1, pool.at().z, 3, BRINE_RADIUS / 2.0, 0.0, BRINE_RADIUS / 2.0, 0.0);
			}
			for (Player player : level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(pool.at(), pool.at()).inflate(BRINE_RADIUS, 1.0, BRINE_RADIUS))) {
				if (player.onGround() && Math.hypot(player.getX() - pool.at().x, player.getZ() - pool.at().z) <= BRINE_RADIUS) {
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 1, false, false));
				}
			}
		}
	}

	/** Below half health the man tears a chain loose and plates fall from the rim. */
	private void breakLoose(final ServerLevel level) {
		this.broken = true;
		this.entityData.set(DATA_BROKEN, true);
		level.playSound(null, this.blockPosition(), MistinnSounds.RUST_WHEEL_BREAK, SoundSource.HOSTILE, 2.0F, 1.0F);
		level.playSound(null, this.blockPosition(), SoundEvents.ANVIL_DESTROY, SoundSource.HOSTILE, 1.2F, 0.6F);
		level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 1.4, this.getZ(), 40, 1.0, 1.0, 1.0, 0.3);
		for (ServerPlayer player : this.watchers()) {
			player.sendSystemMessage(Component.translatable("message.mistinn.rust_wheel.broken"), true);
		}
	}

	/** A broken wheel stays broken through a save. */
	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("WheelBroken", this.broken);
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.broken = input.getBooleanOr("WheelBroken", false);
		this.entityData.set(DATA_BROKEN, this.broken);
	}

	/** It does not wheel round on the spot like a beast: outside its runs it turns at {@link #TURN} a tick at most. */
	@Override
	protected void customServerAiStep(final ServerLevel level) {
		float before = this.getYRot();
		super.customServerAiStep(level);
		BossMove move = this.currentMove();
		boolean rolling = move != null && (WheelMoves.spinsUp(move.id()) && this.moveTicks() >= move.windup() || "back_roll".equals(move.id()));
		if (!rolling) {
			float delta = Mth.wrapDegrees(this.getYRot() - before);
			if (Math.abs(delta) > TURN) {
				float yaw = before + Math.copySign(TURN, delta);
				this.setYRot(yaw);
				this.setYBodyRot(yaw);
				this.setYHeadRot(yaw);
			}
		}
	}

	// ── The turning wheel, on the client ──────────────────────────

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			this.turnedBefore = this.turned;
			float yaw = this.getYRot() * Mth.DEG_TO_RAD;
			double forward = (this.getX() - this.xo) * -Mth.sin(yaw) + (this.getZ() - this.zo) * Mth.cos(yaw);
			// one turn for every circumference rolled; spun in place as it winds up for a run
			this.turned += (float) (forward / WHEEL_RADIUS);
			BossMove move = this.currentMove();
			if (move != null && WheelMoves.spinsUp(move.id()) && this.moveTicks() < move.windup()) {
				this.turned += 0.15F + 0.5F * this.moveTicks() / Math.max(1, move.windup());
			} else if (move != null && "stuck".equals(move.id())) {
				// jammed against the stone, still spinning
				this.turned += 0.45F;
			}
		}
	}

	/** The wheel's radius, in blocks. */
	public static final double WHEEL_RADIUS = 1.5;

	/** How far the wheel has turned, in radians, between the last two ticks. */
	public float turned(final float partialTicks) {
		return Mth.lerp(partialTicks, this.turnedBefore, this.turned);
	}

	// ── Voice ────────────────────────────────────────────────────

	@Override
	protected SoundEvent getAmbientSound() {
		return MistinnSounds.RUST_WHEEL_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return MistinnSounds.RUST_WHEEL_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return MistinnSounds.RUST_WHEEL_DEATH;
	}

	/** It rolls: no footfall, the gears say where it is. */
	@Override
	protected void playStepSound(final BlockPos pos, final BlockState state) {
		if (this.random.nextInt(3) == 0) {
			this.playSound(MistinnSounds.RUST_WHEEL_GRIND, 0.5F, 1.0F);
		}
	}

	// ── Spoils ────────────────────────────────────────────────────

	@Override
	protected void dropCustomDeathLoot(final ServerLevel level, final DamageSource source, final boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		this.spawnAtLocation(level, new ItemStack(MistinnItems.RUST_GOD_SALT));
		this.spawnAtLocation(level, new ItemStack(MistinnItems.RUST_RIM));
		for (ServerPlayer near : level.getPlayers(p -> p.isAlive() && p.distanceTo(this) <= BAR_RANGE)) {
			life.shizune.mistinn.achievement.AchievementService.grant(near, life.shizune.mistinn.achievement.AchievementService.RUST_WHEEL);
		}
		if (this.random.nextFloat() < 0.3F) {
			int tier = 4 + this.random.nextInt(2);
			List<net.minecraft.world.item.Item> pool = List.of(
				MistinnItems.HELMETS.get(tier), MistinnItems.ARMORS.get(tier), MistinnItems.MASKS.get(tier),
				MistinnItems.RIGS.get(tier), MistinnItems.LEGGINGS.get(tier), MistinnItems.BOOTS.get(tier));
			this.spawnAtLocation(level, new ItemStack(pool.get(this.random.nextInt(pool.size()))));
		}
		if (source.getEntity() instanceof ServerPlayer player) {
			SaltService.add(player, 4000);
		}
		this.bar.removeAllPlayers();
	}
}
