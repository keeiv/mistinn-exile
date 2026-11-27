package life.shizune.mistinn.path;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.story.StoryService;
import life.shizune.mistinn.weapon.WeaponMaterial;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 3412．鏽錨使徒: hunter No. 3412 as they were when no one moved them: fully trained, with no strange thoughts and no
 * clumsy hands. They have walked the whole story to the Mist Inn and come up the highland on the tribe's word to put
 * down a stitched thing that walks; to them it is a day's work. They fight the way they were taught: in a stance,
 * never still, circling at the edge of reach; in when there is an opening, with a string of blows done with whichever
 * branch of the station's arms fits the distance (changed in the hand without a pause), and out again; a swing at
 * them is rolled from, stepped past, or turned aside and answered. Every move is a piece of choreography timed to the
 * tick ({@link ApostleMove}). They talk to themself the whole time about dinner and the girl they mean to speak to.
 * The thirteenth cannot win: what reaches them is a tenth of a blow, they never fall below a floor of their strength,
 * and when enough is enough they finish it with a draw-cut it does not see.
 */
public class RustApostle extends PathfinderMob {
	/** What a blow takes from them, of what it would take; the least of their health a blow leaves; health back each second. */
	public static final float TAKEN = 0.1F;
	public static final float FLOOR = 0.15F;
	public static final float MENDS = 1.0F;
	/** Ticks of the fight before they finish it, and the share of the thirteenth's strength left at which they do. */
	public static final int FIGHT = 3000;
	public static final float SPENT = 0.3F;
	/** Ticks between their mutterings. */
	public static final int MUTTER = 240;
	/** How many things they say to themself, round and round. */
	public static final int MUTTERINGS = 12;
	/** How near they come before they draw. */
	public static final double DRAWS_AT = 13.0;

	/** The move being run, as its index plus one (0: none), and the game time it began, for the client. */
	private static final EntityDataAccessor<Byte> DATA_MOVE = SynchedEntityData.defineId(RustApostle.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Integer> DATA_MOVE_START = SynchedEntityData.defineId(RustApostle.class, EntityDataSerializers.INT);
	/** Whether the blade is out, for the client: before it they carry it low. */
	private static final EntityDataAccessor<Boolean> DATA_DRAWN = SynchedEntityData.defineId(RustApostle.class, EntityDataSerializers.BOOLEAN);

	private @Nullable ApostleMove move;
	private int moveTicks;
	/** The blow waiting on a change of arms. */
	private @Nullable ApostleMove queued;
	private @Nullable ApostleMove lastBlow;
	private float lockedYaw;
	private int pause = 20;
	private int reactCooldown;
	private int strafeSide = 1;
	private int strafeTicks;
	private int fightTicks;
	private boolean drawn;
	private boolean serious;
	private boolean finishing;
	private int nextMutter = MUTTER;
	private final List<Integer> mutterOrder = new ArrayList<>();
	private @Nullable UUID quarry;
	/** Ticks left before they are gone, once the work is done; -1 while it is not. */
	private int leaving = -1;
	private final ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("entity.mistinn.rust_apostle"),
		BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);

	/** For tests: the branches they have struck with, the moves they have made, how many things they have said, their rolls and guards. */
	public final Set<WeaponStyle> struckWith = EnumSet.noneOf(WeaponStyle.class);
	public final Set<ApostleMove> done = EnumSet.noneOf(ApostleMove.class);
	public int muttered;
	public int rolled;
	public int guarded;

	public RustApostle(final EntityType<? extends RustApostle> type, final Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.xpReward = 0;
		this.arm(WeaponStyle.SLASH);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 400.0)
			.add(Attributes.MOVEMENT_SPEED, 0.33)
			.add(Attributes.FOLLOW_RANGE, 64.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MOVE, (byte) 0);
		builder.define(DATA_MOVE_START, 0);
		builder.define(DATA_DRAWN, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
	}

	/** The move they are in (on either side), or null between moves. */
	public @Nullable ApostleMove move() {
		if (!this.level().isClientSide()) {
			return this.move;
		}
		int index = this.entityData.get(DATA_MOVE) - 1;
		return index >= 0 && index < ApostleMove.values().length ? ApostleMove.values()[index] : null;
	}

	private void draw() {
		this.drawn = true;
		this.entityData.set(DATA_DRAWN, true);
	}

	/** Whether the blade is out (on either side). */
	public boolean drawn() {
		return this.level().isClientSide() ? this.entityData.get(DATA_DRAWN) : this.drawn;
	}

	/** The game time the move began, as the client was told. */
	public int moveStart() {
		return this.entityData.get(DATA_MOVE_START);
	}

	/** The branch in hand. */
	public @Nullable WeaponStyle held() {
		return WeaponStyle.of(this.getMainHandItem());
	}

	/** Takes the branch in hand: the best of the station's arms. */
	private void arm(final WeaponStyle branch) {
		var weapons = MistinnItems.WEAPONS.get(branch);
		ItemStack weapon = weapons == null ? ItemStack.EMPTY : new ItemStack(weapons.get(WeaponMaterial.NETHERITE));
		this.setItemSlot(EquipmentSlot.MAINHAND, weapon);
		this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
	}

	/** The thing they were sent for. */
	public void hunt(final ServerPlayer target) {
		this.quarry = target.getUUID();
	}

	/** Whether this is the thing they were sent for. */
	public boolean hunts(final net.minecraft.world.entity.player.Player player) {
		return player.getUUID().equals(this.quarry);
	}

	private @Nullable ServerPlayer quarry() {
		if (!(this.level() instanceof ServerLevel level) || this.quarry == null) {
			return null;
		}
		return level.getServer().getPlayerList().getPlayer(this.quarry) instanceof ServerPlayer player && player.isAlive() && !player.isSpectator()
			? player : null;
	}

	// ── Taking blows ──────────────────────────────────────────────

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
		if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, amount);
		}
		// mid-roll, the blow passes through
		if (this.move != null && this.move.immune(this.moveTicks)) {
			return false;
		}
		// a raised guard turns a blow from in front aside, and they answer at once
		if (this.move == ApostleMove.GUARD && source.getEntity() instanceof ServerPlayer hitter && this.inFront(hitter)) {
			level.playSound(null, this.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 1.0F, 1.5F);
			level.playSound(null, this.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.4F, 1.9F);
			Vec3 at = this.getEyePosition().add(Vec3.directionFromRotation(0.0F, this.getYRot()).scale(0.8));
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y - 0.3, at.z, 14, 0.15, 0.15, 0.15, 0.4);
			hitter.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 24, 3));
			Vec3 push = hitter.position().subtract(this.position()).multiply(1.0, 0.0, 1.0).normalize().scale(0.7);
			hitter.setDeltaMovement(push.x, 0.2, push.z);
			hitter.needsSync = true;
			this.begin(ApostleMove.RIPOSTE);
			return false;
		}
		// they do not go down: no blow takes them below a floor of their strength, whatever the thing does
		float room = this.getHealth() - this.getMaxHealth() * FLOOR;
		if (room <= 0.0F) {
			return false;
		}
		boolean hit = super.hurtServer(level, source, Math.min(amount * TAKEN, room));
		if (this.getHealth() < this.getMaxHealth() * FLOOR) {
			this.setHealth(this.getMaxHealth() * FLOOR);
		}
		if (!this.serious && this.getHealth() < this.getMaxHealth() * 0.5F) {
			this.serious = true;
			this.say("apostle_serious");
		}
		return hit;
	}

	@Override
	public boolean isInvulnerableTo(final ServerLevel level, final DamageSource source) {
		return source.is(net.minecraft.tags.DamageTypeTags.IS_FALL) || source.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING) || super.isInvulnerableTo(level, source);
	}

	private boolean inFront(final LivingEntity other) {
		Vec3 look = Vec3.directionFromRotation(0.0F, this.getYRot());
		Vec3 to = other.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
		return to.lengthSqr() > 1.0E-6 && look.dot(to.normalize()) > 0.2;
	}

	// ── Each tick ─────────────────────────────────────────────────

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		ServerPlayer target = this.quarry();
		this.bar.setProgress(this.getHealth() / this.getMaxHealth());
		if (target == null) {
			// the work done: the move in hand finished, then home, walking
			if (this.move != null) {
				this.run(level, null);
			} else if (this.leaving > 0) {
				if (this.getNavigation().isDone()) {
					Vec3 away = this.position().add(Vec3.directionFromRotation(0.0F, this.getYRot()).scale(24.0));
					this.getNavigation().moveTo(away.x, away.y, away.z, 0.9);
				}
				if (--this.leaving == 0) {
					this.discard();
				}
			} else {
				this.getNavigation().stop();
			}
			return;
		}
		this.fightTicks++;
		if (this.reactCooldown > 0) {
			this.reactCooldown--;
		}
		if (this.drawn && --this.nextMutter <= 0) {
			this.nextMutter = MUTTER + this.random.nextInt(80);
			this.mutter();
		}
		if (this.fightTicks % 20 == 0) {
			this.heal(MENDS);
		}
		this.getLookControl().setLookAt(target, 60.0F, 30.0F);
		// enough is enough
		if (this.drawn && !this.finishing && (this.fightTicks >= FIGHT || Thirteenth.spent(target))) {
			this.finishing = true;
			this.queued = null;
			this.say("apostle_finish");
			this.begin(ApostleMove.IAI);
		}
		if (this.move != null) {
			this.run(level, target);
			return;
		}
		double distance = this.distanceTo(target);
		this.face(target, 30.0F);
		if (!this.drawn) {
			// walked up calmly; at the edge of reach, the blade comes out
			if (distance <= DRAWS_AT) {
				this.draw();
				this.getNavigation().stop();
				this.begin(ApostleMove.DRAW);
			} else {
				this.getNavigation().moveTo(target, 0.95);
			}
			return;
		}
		// a swing at them: rolled from, stepped past, or turned aside
		if (this.reactCooldown == 0 && distance < 4.8 && this.swinging(target)) {
			this.reactCooldown = this.serious ? 18 : 28;
			this.react(target);
			return;
		}
		if (this.pause > 0) {
			this.pause--;
			this.footwork(target, distance);
			return;
		}
		ApostleMove next = this.choose(distance);
		if (next == null) {
			this.footwork(target, distance);
			return;
		}
		this.start(next);
	}

	/** Whether the thing is swinging at them, or has just swung. */
	private boolean swinging(final ServerPlayer target) {
		return target.isSwinging() || target.getAttackStrengthScale(0.0F) < 0.5F;
	}

	private void react(final ServerPlayer target) {
		boolean heavy = target.isUsingItem();
		float roll = this.random.nextFloat();
		boolean right = this.random.nextBoolean();
		if (heavy || roll < 0.35F) {
			this.begin(right ? ApostleMove.ROLL_RIGHT : ApostleMove.ROLL_LEFT);
		} else if (roll < 0.62F) {
			this.begin(ApostleMove.GUARD);
		} else {
			this.begin(right ? ApostleMove.STEP_RIGHT : ApostleMove.STEP_LEFT);
		}
	}

	/** Between moves: in the stance, circling at the edge of reach, never standing still. */
	private void footwork(final ServerPlayer target, final double distance) {
		if (distance > 9.0) {
			this.getNavigation().moveTo(target, 1.25);
			return;
		}
		this.getNavigation().stop();
		if (--this.strafeTicks <= 0) {
			this.strafeTicks = 25 + this.random.nextInt(40);
			this.strafeSide = this.random.nextBoolean() ? 1 : -1;
		}
		float forward = distance > 6.0 ? 0.55F : distance < 3.2 ? -0.5F : 0.0F;
		this.getMoveControl().strafe(forward, 0.42F * this.strafeSide);
	}

	/** What fits the distance: never the same blow twice running. */
	private @Nullable ApostleMove choose(final double distance) {
		List<ApostleMove> options = new ArrayList<>();
		if (distance > 9.5) {
			if (distance < 16.0 && this.onGround()) {
				options.add(ApostleMove.LEAP_CLEAVE);
			}
			if (distance < 11.0) {
				options.add(ApostleMove.SPEAR_LUNGE);
			}
		} else if (distance > 6.5) {
			options.add(ApostleMove.SPEAR_LUNGE);
			options.add(ApostleMove.LEAP_CLEAVE);
		} else if (distance > 4.2) {
			options.add(ApostleMove.SPEAR_TRIPLE);
			options.add(ApostleMove.REAPER_SPIN);
			options.add(ApostleMove.BLADE_STRING);
		} else if (distance > 2.2) {
			options.add(ApostleMove.BLADE_STRING);
			options.add(ApostleMove.SPIN_CLEAVE);
			options.add(ApostleMove.DAGGER_DANCE);
			options.add(ApostleMove.REAPER_SPIN);
		} else {
			options.add(ApostleMove.DAGGER_DANCE);
			options.add(ApostleMove.BACKSTEP);
			options.add(ApostleMove.SPIN_CLEAVE);
		}
		options.remove(this.lastBlow);
		return options.isEmpty() ? null : options.get(this.random.nextInt(options.size()));
	}

	/** Begins a move, changing arms first if it is done with another branch. */
	private void start(final ApostleMove next) {
		if (next.style != null && next.style != this.held()) {
			this.queued = next;
			this.begin(ApostleMove.SWAP);
			return;
		}
		this.begin(next);
	}

	private void begin(final ApostleMove next) {
		this.move = next;
		this.moveTicks = 0;
		this.entityData.set(DATA_MOVE, (byte) (next.ordinal() + 1));
		this.entityData.set(DATA_MOVE_START, (int) this.level().getGameTime());
		this.lockedYaw = this.getYRot();
		this.getNavigation().stop();
		this.getMoveControl().strafe(0.0F, 0.0F);
		this.done.add(next);
		if (next.style != null) {
			this.arm(next.style);
		}
		if (next.blow()) {
			this.lastBlow = next;
		}
		switch (next) {
			case ROLL_LEFT, ROLL_RIGHT -> this.rolled++;
			case GUARD -> this.guarded++;
			default -> {
			}
		}
	}

	/** Turns toward the thing, no faster than {@code step} degrees a tick. */
	private void face(final LivingEntity target, final float step) {
		float yaw = (float) Math.toDegrees(Math.atan2(-(target.getX() - this.getX()), target.getZ() - this.getZ()));
		this.hold(Mth.approachDegrees(this.getYRot(), yaw, step));
	}

	private void hold(final float yaw) {
		this.setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
	}

	/** One tick of the move in hand. */
	private void run(final ServerLevel level, final @Nullable ServerPlayer target) {
		ApostleMove current = this.move;
		int t = this.moveTicks;
		// until the move commits they follow the thing; after it, where they meant is where it goes
		if (t < current.lock && target != null) {
			this.face(target, 40.0F);
			this.lockedYaw = this.getYRot();
		} else {
			this.hold(this.lockedYaw);
		}
		Vec3 forward = Vec3.directionFromRotation(0.0F, this.lockedYaw);
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		for (ApostleMove.Impulse impulse : current.impulses) {
			if (impulse.tick() == t) {
				Vec3 push = forward.scale(impulse.forward()).add(right.scale(impulse.side()));
				this.setDeltaMovement(push.x, impulse.up() > 0.0 ? impulse.up() : this.getDeltaMovement().y, push.z);
				this.needsSync = true;
			}
		}
		switch (current) {
			case SWAP -> {
				if (t == 1) {
					level.playSound(null, this.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.HOSTILE, 0.7F, 1.4F);
				}
				if (t == ApostleMove.SWAP_AT && this.queued != null && this.queued.style != null) {
					this.arm(this.queued.style);
					level.playSound(null, this.blockPosition(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.HOSTILE, 0.8F, 1.6F);
				}
			}
			case DRAW -> {
				if (t == 2) {
					level.playSound(null, this.blockPosition(), SoundEvents.TRIDENT_RETURN, SoundSource.HOSTILE, 0.7F, 1.6F);
				}
				if (t == 8 || t == 14) {
					level.playSound(null, this.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.45F, 1.8F);
				}
				if (t == 24) {
					level.playSound(null, this.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.HOSTILE, 0.8F, 1.2F);
				}
			}
			case FLICK -> {
				if (t == 6) {
					level.playSound(null, this.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.5F, 2.0F);
				}
			}
			case LEAP_CLEAVE -> this.leap(level, target, t, forward);
			case IAI -> {
				if (target != null) {
					this.iai(level, target, t);
				}
			}
			default -> {
			}
		}
		for (ApostleMove.Hit hit : current.hits) {
			if (hit.tick() == t) {
				this.strike(level, target, hit, forward);
			}
		}
		if (++this.moveTicks >= current.length) {
			this.end(current);
		}
	}

	private void end(final ApostleMove finished) {
		this.move = null;
		this.entityData.set(DATA_MOVE, (byte) 0);
		if (this.queued != null && finished == ApostleMove.SWAP) {
			ApostleMove next = this.queued;
			this.queued = null;
			this.begin(next);
			return;
		}
		this.pause = this.serious ? 6 + this.random.nextInt(10) : 14 + this.random.nextInt(22);
		if (finished.blow() && finished != ApostleMove.IAI) {
			// out again, or a flick of the blade, as they were taught
			float after = this.random.nextFloat();
			if (after < 0.3F) {
				this.begin(ApostleMove.BACKSTEP);
			} else if (after < 0.5F && this.held() == WeaponStyle.SLASH) {
				this.begin(ApostleMove.FLICK);
			}
		}
	}

	/** A blow lands, or misses: the sound and the cut in the air either way. */
	private void strike(final ServerLevel level, final @Nullable ServerPlayer target, final ApostleMove.Hit hit, final Vec3 forward) {
		WeaponStyle style = this.held();
		if (style != null) {
			this.struckWith.add(style);
			level.playSound(null, this.blockPosition(), style.swingSound(), SoundSource.HOSTILE, 1.0F, 0.95F + this.random.nextFloat() * 0.1F);
		}
		// the cut in the air: a sweep in front, or all round for a turn
		int sweeps = hit.arc() >= 360.0F ? 6 : 2;
		for (int i = 0; i < sweeps; i++) {
			float angle = hit.arc() >= 360.0F ? i * 60.0F : (i - 0.5F) * 30.0F;
			Vec3 dir = forward.yRot((float) Math.toRadians(angle));
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, this.getX() + dir.x * 1.7, this.getY() + 1.0, this.getZ() + dir.z * 1.7, 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (target != null && this.covers(target, hit.reach(), hit.arc(), forward)) {
			this.land(level, target, hit.damage(), hit.knockback(), hit.pull());
		}
	}

	/** 大劍空翻落斬: off the ground toward the thing, over once in the air, and down through it. */
	private void leap(final ServerLevel level, final @Nullable ServerPlayer target, final int t, final Vec3 forward) {
		if (t == 2) {
			level.playSound(null, this.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.HOSTILE, 0.8F, 0.7F);
		}
		if (t == ApostleMove.LEAP_OFF && target != null) {
			Vec3 to = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
			double reach = Math.min(Math.max(to.length() - 1.6, 0.0), 9.0);
			Vec3 run = forward.scale(reach / (ApostleMove.LEAP_DOWN - ApostleMove.LEAP_OFF) * 1.18);
			this.setDeltaMovement(run.x, 0.56, run.z);
			this.needsSync = true;
			level.playSound(null, this.blockPosition(), SoundEvents.BREEZE_JUMP, SoundSource.HOSTILE, 1.0F, 0.8F);
			level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.1, this.getZ(), 10, 0.3, 0.05, 0.3, 0.02);
		}
		if (t == ApostleMove.LEAP_DOWN + 2) {
			level.playSound(null, this.blockPosition(), SoundEvents.MACE_SMASH_GROUND, SoundSource.HOSTILE, 1.0F, 1.1F);
			Vec3 at = this.position().add(forward.scale(1.8));
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.1, at.z, 18, 0.7, 0.05, 0.7, 0.04);
		}
	}

	/** 居合: still and low, then through and past; a breath; it falls. */
	private void iai(final ServerLevel level, final ServerPlayer target, final int t) {
		if (t == 1) {
			level.playSound(null, this.blockPosition(), SoundEvents.TRIDENT_RETURN, SoundSource.HOSTILE, 0.8F, 0.6F);
		}
		if (t == 20) {
			level.playSound(null, this.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.HOSTILE, 0.6F, 0.6F);
		}
		if (t == ApostleMove.IAI_THROUGH) {
			// through the thing and past it, already turned the way the cut carried them
			Vec3 from = this.position();
			Vec3 dir = target.position().subtract(from).multiply(1.0, 0.0, 1.0);
			dir = dir.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, this.getYRot()) : dir.normalize();
			Vec3 past = target.position().add(dir.scale(2.4));
			for (int i = 0; i <= 10; i++) {
				Vec3 p = from.lerp(past, i / 10.0);
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y + 1.1, p.z, 1, 0.0, 0.0, 0.0, 0.0);
				level.sendParticles(ParticleTypes.CLOUD, p.x, p.y + 0.6, p.z, 2, 0.1, 0.2, 0.1, 0.01);
			}
			this.teleportTo(past.x, target.getY(), past.z);
			this.lockedYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
			this.hold(this.lockedYaw);
			level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.4F, 0.6F);
			level.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.HOSTILE, 0.9F, 1.4F);
		}
		if (t == ApostleMove.IAI_FALLS) {
			// no thing walks on after this
			level.playSound(null, this.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.HOSTILE, 1.0F, 1.5F);
			Thirteenth.finishing(target);
			target.hurtServer(level, this.damageSources().mobAttack(this), 4000.0F);
			if (target.isAlive()) {
				target.kill(level);
			}
		}
	}

	private boolean covers(final LivingEntity target, final double reach, final float arc, final Vec3 forward) {
		Vec3 to = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
		double distance = to.length() - target.getBbWidth() / 2.0;
		if (distance > reach || Math.abs(target.getY() - this.getY()) > 2.5) {
			return false;
		}
		if (arc >= 360.0F || to.length() < 0.8) {
			return true;
		}
		return to.normalize().dot(forward) >= Math.cos(Math.toRadians(arc / 2.0));
	}

	private void land(final ServerLevel level, final ServerPlayer target, final float damage, final double knockback, final double pull) {
		target.hurtServer(level, this.damageSources().mobAttack(this), damage * (this.serious ? 1.25F : 1.0F));
		Vec3 away = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
		away = away.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, this.getYRot()) : away.normalize();
		if (knockback > 0.0) {
			target.setDeltaMovement(away.x * knockback, 0.3, away.z * knockback);
			target.needsSync = true;
		} else if (pull > 0.0) {
			target.setDeltaMovement(-away.x * pull, 0.1, -away.z * pull);
			target.needsSync = true;
		}
	}

	// ── Words ─────────────────────────────────────────────────────

	/** One more thing they say to themself; all of them before any again. */
	private void mutter() {
		if (this.mutterOrder.isEmpty()) {
			for (int i = 0; i < MUTTERINGS; i++) {
				this.mutterOrder.add(i);
			}
			java.util.Collections.shuffle(this.mutterOrder, new java.util.Random(this.random.nextLong()));
		}
		this.say("apostle_mutter_" + this.mutterOrder.removeFirst());
		this.muttered++;
	}

	/** Says a line, under the screen, to everyone near. */
	public void say(final String sequence) {
		if (this.level() instanceof ServerLevel level) {
			for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr(this) < 64.0 * 64.0)) {
				ServerPlayNetworking.send(player, new StoryService.LinesPayload(sequence, -1, ""));
			}
		}
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
	public boolean removeWhenFarAway(final double distance) {
		return false;
	}

	/** The work is done: the move in hand finished, they turn for home and are gone in a while. */
	public void goHome() {
		this.quarry = null;
		this.leaving = 160;
		this.bar.removeAllPlayers();
	}

	/** Test hook: finish it now. */
	public void finishNow() {
		this.fightTicks = FIGHT;
		this.draw();
	}

	/** Test hook: run this move now, its arms taken up at once. */
	public void perform(final ApostleMove next) {
		this.draw();
		this.queued = null;
		this.begin(next);
	}

	/** Test hook: hold off choosing anything for a while. */
	public void rest(final int ticks) {
		this.pause = ticks;
	}
}
