package life.shizune.mistinn.rift;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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
 * <p>
 * Some packs run behind an alpha (頭犬), half again as big, with a red name: every so often it
 * stops and howls, the pack quickens, and two hounds from opposite sides of the ring leap at once,
 * whoever is faced. Bring the alpha down and its pack scatters a while. At night the hounds of a
 * scar howl now and then, far off.
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
	/** The alpha: how often it howls its pack on, at the least and the most, how far it is heard, and how long its pack scatters when it falls. */
	public static final int HOWL_MIN = 140;
	public static final int HOWL_MAX = 220;
	public static final double HEARD = 24.0;
	public static final int SCATTER = 200;
	/** One pack in this many that come of themselves runs behind an alpha. */
	public static final int ALPHA_ODDS = 5;
	private static final EntityDataAccessor<Boolean> DATA_ALPHA = SynchedEntityData.defineId(RiftHound.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_HOWL = SynchedEntityData.defineId(RiftHound.class, EntityDataSerializers.BOOLEAN);
	private static final int HOWL_TIME = 30;

	private float crouchO;
	private float crouch;
	private float lungeO;
	private float lunge;
	private float biteO;
	private float bite;
	private float howlO;
	private float howl;
	/** Told by its alpha to leap now, whoever it is faced by: until when the word holds. */
	private long ordered;
	/** Its alpha has fallen: it runs from where, for so long. */
	private int scatter;
	private Vec3 scatterFrom = Vec3.ZERO;
	private int howling;
	private int nextHowl = HOWL_MIN;

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
		builder.define(DATA_ALPHA, false);
		builder.define(DATA_HOWL, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new ScatterGoal());
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

	// ── The alpha ─────────────────────────────────────────────────

	public boolean isAlpha() {
		return this.entityData.get(DATA_ALPHA);
	}

	public boolean isHowling() {
		return this.entityData.get(DATA_HOWL);
	}

	public boolean scattering() {
		return this.scatter > 0;
	}

	/** Makes this hound its pack's alpha: bigger, harder, a red name. */
	public void lead() {
		if (this.isAlpha()) {
			return;
		}
		this.entityData.set(DATA_ALPHA, true);
		modify(this, Attributes.MAX_HEALTH, "alpha_life", 50.0);
		modify(this, Attributes.ATTACK_DAMAGE, "alpha_bite", 3.0);
		modify(this, Attributes.SCALE, "alpha_size", 0.45);
		modify(this, Attributes.KNOCKBACK_RESISTANCE, "alpha_weight", 0.5);
		this.setHealth(this.getMaxHealth());
		this.xpReward = 25;
		this.setCustomName(Component.translatable("entity.mistinn.rift_hound.alpha").withStyle(ChatFormatting.DARK_RED));
		this.nextHowl = HOWL_MIN / 2;
	}

	private static void modify(final RiftHound hound, final Holder<Attribute> attribute, final String name, final double amount) {
		AttributeInstance instance = hound.getAttribute(attribute);
		Identifier id = Mistinn.id(name);
		if (instance != null && !instance.hasModifier(id)) {
			instance.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** The alpha howls: the pack hunting with it quickens, and two of them, from opposite sides of the ring, leap at once. */
	private void howl(final ServerLevel level, final LivingEntity quarry) {
		this.howling = HOWL_TIME;
		this.entityData.set(DATA_HOWL, true);
		this.getNavigation().stop();
		level.playSound(null, this.getX(), this.getY(), this.getZ(), MistinnSounds.RIFT_HOUND_HOWL, SoundSource.HOSTILE, 3.0F, 0.55F);
		level.sendParticles(ParticleTypes.CRIMSON_SPORE, this.getX(), this.getY() + 1.2, this.getZ(), 30, 0.4, 0.4, 0.4, 0.02);
		// the pack within hearing turns on its quarry, and quickens
		List<RiftHound> pack = level.getEntitiesOfClass(RiftHound.class, this.getBoundingBox().inflate(HEARD),
			h -> h != this && h.isAlive() && !h.isAlpha() && !h.scattering());
		for (RiftHound hound : pack) {
			hound.setTarget(quarry);
			hound.addEffect(new MobEffectInstance(MobEffects.SPEED, 100, 0, false, true));
		}
		// of those free to leap at it this moment, the two farthest apart round the ring
		RiftHound[] pincer = pincer(quarry, pack.stream().filter(h -> !h.isCrouching() && !h.isLunging() && h.distanceTo(quarry) > 1.5F
			&& h.distanceTo(quarry) < 12.0F && h.hasLineOfSight(quarry)).toList());
		for (RiftHound hound : pincer) {
			if (hound != null) {
				hound.ordered = level.getGameTime() + 20L;
			}
		}
	}

	/** The two of a pack farthest round from each other about their quarry. */
	static RiftHound[] pincer(final LivingEntity quarry, final List<RiftHound> pack) {
		RiftHound[] best = new RiftHound[2];
		double widest = -1.0;
		for (int i = 0; i < pack.size(); i++) {
			for (int j = i + 1; j < pack.size(); j++) {
				Vec3 a = pack.get(i).position().subtract(quarry.position()).multiply(1.0, 0.0, 1.0);
				Vec3 b = pack.get(j).position().subtract(quarry.position()).multiply(1.0, 0.0, 1.0);
				if (a.lengthSqr() < 1.0E-6 || b.lengthSqr() < 1.0E-6) {
					continue;
				}
				double apart = Math.acos(Mth.clamp(a.normalize().dot(b.normalize()), -1.0, 1.0));
				if (apart > widest) {
					widest = apart;
					best[0] = pack.get(i);
					best[1] = pack.get(j);
				}
			}
		}
		if (best[0] == null && !pack.isEmpty()) {
			best[0] = pack.getFirst();
		}
		return best;
	}

	/** For tests: how long more it scatters (0: it has its nerve back). */
	public void scatterFor(final int ticks) {
		this.scatter = ticks;
	}

	/** The alpha has fallen: its pack breaks and runs from where it fell, a while. */
	private void scatterFrom(final Vec3 from) {
		this.scatter = SCATTER;
		this.scatterFrom = from;
		this.ordered = 0L;
		this.setTarget(null);
	}

	@Override
	public void die(final DamageSource source) {
		super.die(source);
		if (this.isAlpha() && this.level() instanceof ServerLevel level) {
			for (RiftHound hound : level.getEntitiesOfClass(RiftHound.class, this.getBoundingBox().inflate(HEARD), h -> h != this && h.isAlive())) {
				hound.scatterFrom(this.position());
			}
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ZOGLIN_DEATH, SoundSource.HOSTILE, 2.0F, 0.5F);
			if (this.getLastHurtByPlayer() != null) {
				if (this.getLastHurtByPlayer() instanceof net.minecraft.server.level.ServerPlayer killer) {
					life.shizune.mistinn.achievement.AchievementService.grant(killer, life.shizune.mistinn.achievement.AchievementService.ALPHA_SLAIN);
				}
				this.spawnAtLocation(level, new ItemStack(MistinnItems.RIFT_SHARD, 2 + this.random.nextInt(2)));
				if (this.random.nextInt(4) == 0) {
					List<Item> pieces = MistinnItems.gearOfTier(3 + this.random.nextInt(2));
					this.spawnAtLocation(level, new ItemStack(pieces.get(this.random.nextInt(pieces.size()))));
				}
			}
		}
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(final ServerLevelAccessor level, final DifficultyInstance difficulty, final EntitySpawnReason reason,
		final @Nullable SpawnGroupData data) {
		SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
		if ((reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION) && this.random.nextInt(ALPHA_ODDS) == 0
			&& level.getEntitiesOfClass(RiftHound.class, this.getBoundingBox().inflate(48.0), RiftHound::isAlpha).isEmpty()) {
			this.lead();
		}
		return result;
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		if (this.scatter > 0) {
			this.scatter--;
			this.setTarget(null);
		}
		if (this.howling > 0 && --this.howling <= 0) {
			this.entityData.set(DATA_HOWL, false);
		}
	}

	@Override
	public boolean canAttack(final LivingEntity target) {
		return this.scatter <= 0 && super.canAttack(target);
	}

	/** Its alpha fallen, it runs from where, until it has its nerve back. */
	private final class ScatterGoal extends Goal {
		ScatterGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return RiftHound.this.scatter > 0;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			RiftHound self = RiftHound.this;
			if (self.tickCount % 10 != 0 && !self.getNavigation().isDone()) {
				return;
			}
			Vec3 away = self.position().subtract(self.scatterFrom).multiply(1.0, 0.0, 1.0);
			Vec3 run = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
			Vec3 to = self.position().add(run.scale(12.0));
			self.getNavigation().moveTo(to.x, to.y, to.z, 1.5);
		}
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
			if (self.howling > 0) {
				self.getNavigation().stop();
				return;
			}
			if (reach > NEAR) {
				self.getNavigation().moveTo(quarry, 1.4);
				return;
			}
			if (self.isAlpha() && --self.nextHowl <= 0) {
				self.nextHowl = HOWL_MIN + self.random.nextInt(HOWL_MAX - HOWL_MIN);
				self.howl(level, quarry);
				return;
			}
			if (self.ordered >= level.getGameTime()) {
				self.ordered = 0L;
				if (reach > 1.5 && reach < 12.0 && self.hasLineOfSight(quarry)) {
					this.crouching = CROUCH;
					self.entityData.set(DATA_CROUCH, true);
					self.playSound(SoundEvents.ZOGLIN_ANGRY, 1.0F, 1.5F + self.random.nextFloat() * 0.2F);
					this.wait = LUNGE_MIN + self.random.nextInt(LUNGE_MAX - LUNGE_MIN);
					return;
				}
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
		this.howlO = this.howl;
		this.howl = Mth.approach(this.howl, this.isHowling() ? 1.0F : 0.0F, 0.15F);
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

	/** 0 at rest, 1 head thrown back in a howl. */
	public float howl(final float partial) {
		return Mth.lerp(partial, this.howlO, this.howl);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level().isClientSide() && this.random.nextInt(this.isAlpha() ? 2 : 5) == 0) {
			// the rift's light, leaking out through its cracks (an alpha burns brighter)
			this.level().addParticle(ParticleTypes.CRIMSON_SPORE, this.getRandomX(0.5), this.getRandomY(), this.getRandomZ(0.5), 0.0, 0.0, 0.0);
			if (this.isAlpha() && this.random.nextInt(4) == 0) {
				this.level().addParticle(ParticleTypes.SMALL_FLAME, this.getRandomX(0.4), this.getY() + this.getBbHeight() * 0.9, this.getRandomZ(0.4), 0.0, 0.01, 0.0);
			}
		}
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("alpha", this.isAlpha());
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(DATA_ALPHA, input.getBooleanOr("alpha", false));
		if (this.isAlpha()) {
			this.xpReward = 25;
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
	public void playAmbientSound() {
		if (this.level() instanceof ServerLevel level && level.isDarkOutside() && this.getTarget() == null && this.random.nextInt(4) == 0) {
			// at night, a howl, heard far off
			level.playSound(null, this.getX(), this.getY(), this.getZ(), MistinnSounds.RIFT_HOUND_HOWL, SoundSource.HOSTILE, 4.0F,
				(this.isAlpha() ? 0.5F : 0.65F) + this.random.nextFloat() * 0.1F);
			return;
		}
		super.playAmbientSound();
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
