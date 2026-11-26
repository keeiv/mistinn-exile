package life.shizune.mistinn.naval;

import java.util.EnumSet;
import life.shizune.mistinn.enemy.Voiced;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A pirate of the fog sea: an ordinary person in the player model, a sailor who raids hunters for
 * the salt they carry. What it does aboard comes from its {@link PirateRole}: boarders fall on whoever
 * comes over the side, marksmen and the captain shoot from the quarterdeck (a click of the cock, a
 * breath of aim, the shot, a long reload), gunners keep the guns firing. They keep to their posts
 * and do not throw themselves into the sea after a hunter in a boat.
 *
 * <p>Its blows wind up like any creature's (it fights as a vindicator would), but it cries out with a
 * person's voice. When the captain falls the crew give up the ship and go over the side.
 */
public class Pirate extends Monster implements Voiced {
	/** A marksman's musket: how long it aims, how long it reloads, how far it reaches. */
	public static final int MUSKET_AIM = 30;
	public static final int MUSKET_RELOAD = 70;
	public static final double MUSKET_RANGE = 34.0;
	/** The captain's pistol. */
	public static final int PISTOL_AIM = 22;
	public static final int PISTOL_RELOAD = 90;
	public static final double PISTOL_RANGE = 22.0;
	/** Nearer than this a gun is not raised: the blade is quicker. */
	public static final double POINT_BLANK = 4.0;
	/** How near a hunter must be before a pirate leaves its post for them when they are not aboard. */
	private static final double REACH_OFF_DECK = 3.0;

	private static final EntityDataAccessor<Byte> DATA_ROLE = SynchedEntityData.defineId(Pirate.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_LOOK = SynchedEntityData.defineId(Pirate.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_AIMING = SynchedEntityData.defineId(Pirate.class, EntityDataSerializers.BOOLEAN);

	/** The ship this pirate sails with, or -1. */
	private int ship = -1;
	/** Where it stands its watch. */
	private @Nullable BlockPos post;
	/** How strong a crew it was raised in, 0 to {@link NavalService#MAX_NOTORIETY}. */
	private int notoriety;
	private int reload;
	/** Not giving up the ship. */
	private static final int STAYING = Integer.MIN_VALUE;
	/** Ticks since it went over the side (counting up from minus its wait), or {@link #STAYING}. */
	private int fleeing = STAYING;
	private boolean rallied;

	public Pirate(final EntityType<? extends Pirate> type, final Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.xpReward = 8;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 26.0)
			.add(Attributes.ATTACK_DAMAGE, 2.0)
			.add(Attributes.MOVEMENT_SPEED, 0.28)
			.add(Attributes.FOLLOW_RANGE, 40.0)
			.add(Attributes.ARMOR, 2.0);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ROLE, (byte) PirateRole.BOARDER.ordinal());
		builder.define(DATA_LOOK, (byte) PirateLook.CUTLASS_1.ordinal());
		builder.define(DATA_AIMING, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new ShootGoal(this));
		this.goalSelector.addGoal(2, new BoardGoal(this));
		this.goalSelector.addGoal(4, new PostGoal(this));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Pirate.class).setAlertOthers());
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	// ── Who it is ─────────────────────────────────────────────────

	public PirateRole role() {
		return PirateRole.byIndex(this.entityData.get(DATA_ROLE));
	}

	public PirateLook look() {
		return PirateLook.byIndex(this.entityData.get(DATA_LOOK));
	}

	/** Whether it has a gun raised and is taking aim. */
	public boolean aiming() {
		return this.entityData.get(DATA_AIMING);
	}

	public int ship() {
		return this.ship;
	}

	public @Nullable BlockPos post() {
		return this.post;
	}

	public boolean fleeing() {
		return this.fleeing != STAYING;
	}

	/**
	 * Makes this one a member of a ship's crew: its part, its looks, its watch, and how hard a crew it
	 * comes from (more health, harder blows, better armour and quicker guns as the fog sea's pirates
	 * learn to fear the hunters).
	 */
	public void enlist(final int ship, final PirateRole role, final PirateLook look, final BlockPos post, final int notoriety) {
		this.ship = ship;
		this.post = post.immutable();
		this.notoriety = notoriety;
		this.entityData.set(DATA_ROLE, (byte) role.ordinal());
		this.entityData.set(DATA_LOOK, (byte) look.ordinal());
		double health = switch (role) {
			case BOARDER -> 26.0 + 6.0 * notoriety;
			case MARKSMAN -> 20.0 + 4.0 * notoriety;
			case GUNNER -> 24.0 + 5.0 * notoriety;
			case CAPTAIN -> 140.0 + 40.0 * notoriety;
		};
		this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		this.setHealth((float) health);
		this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(role == PirateRole.CAPTAIN ? 4.0 + 1.5 * notoriety : 2.0 + 0.75 * notoriety);
		this.getAttribute(Attributes.ARMOR).setBaseValue(role == PirateRole.CAPTAIN ? 8.0 + notoriety : 2.0 + notoriety);
		this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(role == PirateRole.BOARDER ? 0.3 : 0.27);
		if (role == PirateRole.CAPTAIN) {
			this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.6);
		}
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(switch (role) {
			case MARKSMAN -> MistinnItems.FLINTLOCK;
			case GUNNER -> Items.IRON_AXE;
			default -> Items.IRON_SWORD;
		}));
		if (role == PirateRole.CAPTAIN) {
			this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(MistinnItems.FLINTLOCK_PISTOL));
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			this.setDropChance(slot, 0.0F);
		}
	}

	// ── Each tick ─────────────────────────────────────────────────

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		if (this.reload > 0) {
			this.reload--;
		}
		if (this.fleeing()) {
			this.flee(level);
			return;
		}
		if (this.role() == PirateRole.CAPTAIN && !this.rallied && this.getHealth() <= this.getMaxHealth() / 2.0F) {
			this.rally(level);
		}
	}

	/** At half health the captain bellows the crew on: they fight harder and faster for a while. */
	private void rally(final ServerLevel level) {
		this.rallied = true;
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 3.0F, 1.2F);
		for (Pirate mate : level.getEntitiesOfClass(Pirate.class, this.getBoundingBox().inflate(32.0), p -> p.ship == this.ship && p.isAlive())) {
			mate.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 400, 0));
			mate.addEffect(new MobEffectInstance(MobEffects.SPEED, 400, 0));
		}
		NavalService.announce(level, this.ship, "message.mistinn.naval.rally");
	}

	/** The captain has fallen: this one is giving up the ship. */
	public void abandon(final int delay) {
		this.fleeing = -delay - 1;
		this.setTarget(null);
		this.entityData.set(DATA_AIMING, false);
	}

	private void flee(final ServerLevel level) {
		this.fleeing++;
		if (this.fleeing < 0) {
			return;
		}
		if (this.fleeing == 0) {
			// over the side: away from the middle of the ship, and up over the rail
			Vec3 away = this.position().subtract(NavalService.middle(this.ship, this.position())).multiply(1.0, 0.0, 1.0);
			Vec3 push = away.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, this.getYRot()) : away.normalize();
			this.setDeltaMovement(push.x * 0.55, 0.62, push.z * 0.55);
			this.needsSync = true;
			this.playSound(this.look().female ? MistinnSounds.VOICE_FEMALE_EFFORT : MistinnSounds.VOICE_MALE_EFFORT, 1.0F, this.getVoicePitch());
		}
		if (this.isInWater() && this.fleeing > 10 || this.fleeing > 160) {
			level.sendParticles(ParticleTypes.SPLASH, this.getX(), this.getY() + 0.5, this.getZ(), 16, 0.4, 0.2, 0.4, 0.1);
			level.sendParticles(ParticleTypes.BUBBLE, this.getX(), this.getY(), this.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
			this.discard();
		}
	}

	@Override
	public void die(final DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level && this.role() == PirateRole.CAPTAIN) {
			NavalService.captainFell(level, this);
		}
	}

	/** The captain's pistol always falls with them; now and then a marksman's musket survives its owner. */
	@Override
	protected void dropCustomDeathLoot(final ServerLevel level, final DamageSource source, final boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		if (this.fleeing()) {
			return;
		}
		if (this.role() == PirateRole.CAPTAIN) {
			this.spawnAtLocation(level, new ItemStack(MistinnItems.FLINTLOCK_PISTOL));
		} else if (this.role() == PirateRole.MARKSMAN && recentlyHit && this.getRandom().nextFloat() < 0.12F) {
			this.spawnAtLocation(level, new ItemStack(MistinnItems.FLINTLOCK));
		}
	}

	@Override
	protected boolean considersEntityAsAlly(final Entity other) {
		return other instanceof Pirate || super.considersEntityAsAlly(other);
	}

	@Override
	public boolean removeWhenFarAway(final double distance) {
		return false;
	}

	// ── The voice ─────────────────────────────────────────────────

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return this.look().female ? MistinnSounds.VOICE_FEMALE_HURT : MistinnSounds.VOICE_MALE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return this.look().female ? MistinnSounds.VOICE_FEMALE_DEATH : MistinnSounds.VOICE_MALE_DEATH;
	}

	@Override
	public SoundEvent windupCry() {
		return this.look().female ? MistinnSounds.VOICE_FEMALE_EFFORT : MistinnSounds.VOICE_MALE_EFFORT;
	}

	// ── Saving ────────────────────────────────────────────────────

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("role", this.role().id);
		output.putString("look", this.look().id);
		output.putInt("ship", this.ship);
		output.putInt("notoriety", this.notoriety);
		output.putBoolean("rallied", this.rallied);
		if (this.post != null) {
			output.putLong("post", this.post.asLong());
		}
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(DATA_ROLE, (byte) PirateRole.byId(input.getStringOr("role", PirateRole.BOARDER.id)).ordinal());
		this.entityData.set(DATA_LOOK, (byte) PirateLook.byId(input.getStringOr("look", PirateLook.CUTLASS_1.id)).ordinal());
		this.ship = input.getIntOr("ship", -1);
		this.notoriety = input.getIntOr("notoriety", 0);
		this.rallied = input.getBooleanOr("rallied", false);
		long post = input.getLongOr("post", Long.MIN_VALUE);
		this.post = post == Long.MIN_VALUE ? null : BlockPos.of(post);
	}

	// ── Goals ─────────────────────────────────────────────────────

	/** Whether a target is somewhere this pirate goes after them: aboard its ship, or right beside it. */
	boolean withinReach(final LivingEntity target) {
		if (this.distanceToSqr(target) < REACH_OFF_DECK * REACH_OFF_DECK) {
			return true;
		}
		ShipFrame frame = NavalService.frame(this.ship);
		// a pirate with no ship (or one off its ship, in the sea) fights where it finds itself
		return frame == null || !frame.aboard(this.position()) || frame.aboard(target.position());
	}

	/** Aim, fire, reload: the marksman's musket and the captain's pistol. */
	static final class ShootGoal extends Goal {
		private final Pirate pirate;
		private int aim;

		ShootGoal(final Pirate pirate) {
			this.pirate = pirate;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		private int aimTicks() {
			return this.pirate.role() == PirateRole.CAPTAIN ? PISTOL_AIM : Math.max(16, MUSKET_AIM - 3 * this.pirate.notoriety);
		}

		private double range() {
			return this.pirate.role() == PirateRole.CAPTAIN ? PISTOL_RANGE : MUSKET_RANGE;
		}

		private boolean fit(final @Nullable LivingEntity target) {
			if (target == null || !target.isAlive() || this.pirate.fleeing() || !this.pirate.role().shoots()) {
				return false;
			}
			double distance = this.pirate.distanceTo(target);
			return distance >= POINT_BLANK && distance <= this.range() && this.pirate.getSensing().hasLineOfSight(target);
		}

		@Override
		public boolean canUse() {
			return this.pirate.reload <= 0 && this.fit(this.pirate.getTarget());
		}

		@Override
		public boolean canContinueToUse() {
			return this.aim < this.aimTicks() && this.fit(this.pirate.getTarget());
		}

		@Override
		public void start() {
			this.aim = 0;
			this.pirate.getNavigation().stop();
			this.pirate.entityData.set(DATA_AIMING, true);
			// the cock drawn back: a hunter who hears it has a breath to get out of the line
			this.pirate.level().playSound(null, this.pirate.getX(), this.pirate.getY(), this.pirate.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(),
				SoundSource.HOSTILE, 1.2F, 0.7F);
		}

		@Override
		public void stop() {
			this.pirate.entityData.set(DATA_AIMING, false);
			this.aim = 0;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = this.pirate.getTarget();
			if (target == null) {
				return;
			}
			this.pirate.getLookControl().setLookAt(target, 60.0F, 60.0F);
			this.pirate.getNavigation().stop();
			if (++this.aim < this.aimTicks() || !(this.pirate.level() instanceof ServerLevel level)) {
				return;
			}
			boolean pistol = this.pirate.role() == PirateRole.CAPTAIN;
			Vec3 muzzle = this.pirate.getEyePosition().add(this.pirate.getViewVector(1.0F).scale(0.7)).add(0.0, -0.15, 0.0);
			Vec3 at = target.getBoundingBox().getCenter().add(0.0, target.getBbHeight() * 0.15, 0.0);
			float damage = pistol ? 10.0F + 2.0F * this.pirate.notoriety : 7.0F + this.pirate.notoriety;
			GunShot.fire(level, this.pirate, muzzle, at, damage, pistol ? 0.03 : 0.022, this.range() + 6.0);
			this.pirate.reload = pistol ? PISTOL_RELOAD : Math.max(40, MUSKET_RELOAD - 6 * this.pirate.notoriety);
		}
	}

	/** The cutlass: goes for a hunter aboard, or one right beside it, never over the side after one in the water. */
	static final class BoardGoal extends MeleeAttackGoal {
		private final Pirate pirate;

		BoardGoal(final Pirate pirate) {
			super(pirate, 1.2, false);
			this.pirate = pirate;
		}

		@Override
		public boolean canUse() {
			LivingEntity target = this.pirate.getTarget();
			return !this.pirate.fleeing() && target != null && this.pirate.withinReach(target)
				&& (!this.pirate.role().shoots() || this.pirate.distanceTo(target) < POINT_BLANK || this.pirate.reload > 0) && super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity target = this.pirate.getTarget();
			return !this.pirate.fleeing() && target != null && this.pirate.withinReach(target) && super.canContinueToUse();
		}
	}

	/** Back to its watch when there is no one to fight there. */
	static final class PostGoal extends Goal {
		private final Pirate pirate;

		PostGoal(final Pirate pirate) {
			this.pirate = pirate;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			BlockPos post = this.pirate.post;
			return post != null && !this.pirate.fleeing() && this.pirate.distanceToSqr(Vec3.atBottomCenterOf(post)) > 2.25;
		}

		@Override
		public boolean canContinueToUse() {
			return this.canUse() && !this.pirate.getNavigation().isDone();
		}

		@Override
		public void start() {
			BlockPos post = this.pirate.post;
			if (post != null) {
				this.pirate.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 0.9);
			}
		}
	}
}
