package life.shizune.mistinn.keeper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import life.shizune.mistinn.boss.BossBrain;
import life.shizune.mistinn.boss.BossMove;
import life.shizune.mistinn.boss.MoveRunner;
import life.shizune.mistinn.boss.MoveSet;
import life.shizune.mistinn.boss.Planner;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.salt.SaltService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * 熄燈人, the first Lighthouse Keeper: tall and thin, an unlit lantern for a head, a long hook in
 * hand. Each move strikes a different part of the body, so the hunter's gear matters: the sweep
 * cuts across the torso and arms, the slam's shockwave takes the legs, and the hook drags the
 * hunter in for a blow to the face, where only a mask helps.
 * Below half health the lamp room goes dark. Lighting all four lamp stands throws it off balance
 * for a few seconds, taking half again as much damage, until it snuffs them out one by one.
 */
public class Snuffer extends Monster {
	public static final float MAX_HEALTH = 300.0F;
	/** Players this close to the keeper see its health bar. */
	private static final double BAR_RANGE = 32.0;
	/** Lamp stands this close to the keeper belong to its room. */
	private static final int ROOM_RADIUS = 12;
	public static final int STAGGER_TICKS = 100;
	public static final float STAGGER_DAMAGE = 1.5F;

	/** The move being run, as its index in {@link SnufferMoves#SET} plus one (0: none), for the client. */
	private static final EntityDataAccessor<Byte> DATA_MOVE = SynchedEntityData.defineId(Snuffer.class, EntityDataSerializers.BYTE);
	/** How far into the move, in ticks. */
	private static final EntityDataAccessor<Integer> DATA_MOVE_TICKS = SynchedEntityData.defineId(Snuffer.class, EntityDataSerializers.INT);
	/** Whether the move's clock stands still (in the air, waiting to land): the client holds the pose. */
	private static final EntityDataAccessor<Boolean> DATA_MOVE_HELD = SynchedEntityData.defineId(Snuffer.class, EntityDataSerializers.BOOLEAN);

	private final ServerBossEvent bar = new ServerBossEvent(java.util.UUID.randomUUID(), Component.translatable("entity.mistinn.snuffer"), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
	private final MoveRunner runner = new MoveRunner();
	/** Its mind for this fight: kept in memory only, never saved. */
	private final BossBrain brain = new BossBrain(SnufferMoves.SET, new Planner(SnufferMoves.SET, RandomSource.create()));
	/** Where its room is: where it first stood since it was loaded. */
	private @Nullable Vec3 home;
	/** Ticks left of plain walking the brain asked for. */
	private int walking;
	private boolean dark;
	private int staggered;
	private int snuffTimer;

	public Snuffer(final EntityType<? extends Snuffer> type, final Level level) {
		super(type, level);
		this.xpReward = 120;
		this.setPersistenceRequired();
		this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LANTERN));
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(MistinnItems.MIST_SPEAR));
		this.setDropChance(EquipmentSlot.HEAD, 0.0F);
		this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, MAX_HEALTH)
			.add(Attributes.ARMOR, 8.0)
			.add(Attributes.ATTACK_DAMAGE, 6.0)
			.add(Attributes.MOVEMENT_SPEED, 0.27)
			.add(Attributes.FOLLOW_RANGE, 40.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		// whom it goes after is the brain's to decide
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
	}

	@Override
	public boolean removeWhenFarAway(final double distance) {
		return false;
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MOVE, (byte) 0);
		builder.define(DATA_MOVE_TICKS, 0);
		builder.define(DATA_MOVE_HELD, false);
	}

	// ── State, for tests and the client ───────────────────────────

	/** Every move the keeper has. */
	public MoveSet moves() {
		return SnufferMoves.SET;
	}

	/** The move the body is in, as the client sees it, or null when it only walks or stands. */
	public @Nullable BossMove currentMove() {
		int i = this.entityData.get(DATA_MOVE) - 1;
		return i >= 0 && i < SnufferMoves.SET.size() ? SnufferMoves.SET.byIndex(i) : null;
	}

	/** Ticks since the current move began. */
	public int moveTicks() {
		return this.entityData.get(DATA_MOVE_TICKS);
	}

	/** Whether the current move's clock stands still. */
	public boolean moveHeld() {
		return this.entityData.get(DATA_MOVE_HELD);
	}

	private void syncMove() {
		BossMove move = this.runner.current();
		this.entityData.set(DATA_MOVE, (byte) (move == null ? 0 : SnufferMoves.SET.index(move.id()) + 1));
		this.entityData.set(DATA_MOVE_TICKS, this.runner.ticks());
		this.entityData.set(DATA_MOVE_HELD, this.runner.held());
	}

	public boolean dark() {
		return this.dark;
	}

	public boolean staggered() {
		return this.staggered > 0;
	}

	// ── Every tick ────────────────────────────────────────────────

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		this.bar.setProgress(this.getHealth() / this.getMaxHealth());
		this.updateBarViewers(level);
		if (!this.dark && this.getHealth() <= this.getMaxHealth() / 2.0F) {
			this.goDark(level);
		}
		if (this.dark) {
			this.darkRoom(level);
		}
		if (this.home == null) {
			this.home = this.position();
		}
		this.brain.observe(level, this, this.home, ROOM_RADIUS);
		UUID chosen = this.brain.target();
		Player hunter = chosen == null ? null : level.getPlayerByUUID(chosen);
		this.setTarget(hunter);
		LivingEntity target = hunter;
		if (this.staggered > 0) {
			this.staggered--;
			this.getNavigation().stop();
			level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getEyeY() + 0.3, this.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
			this.runner.tick(level, this, target);
			this.syncMove();
			return;
		}
		if (this.runner.running()) {
			this.getNavigation().stop();
			if (target != null) {
				this.getLookControl().setLookAt(target, 30.0F, 30.0F);
			}
			// a move in the window where it may hand over: the brain may chain the next one in
			BossBrain.Decision next = this.brain.decide(level, this, this.runner, this.phase());
			if (next != null && next.move() != Planner.WALK) {
				this.perform(next.move().id(), next.delay());
			}
			this.runner.tick(level, this, target);
			this.syncMove();
			return;
		}
		if (target == null || !target.isAlive()) {
			this.walking = 0;
			return;
		}
		this.getLookControl().setLookAt(target, 30.0F, 30.0F);
		if (this.walking > 0) {
			this.walking--;
			this.getNavigation().moveTo(target, 1.0);
			return;
		}
		BossBrain.Decision decision = this.brain.decide(level, this, this.runner, this.phase());
		if (decision == null || decision.move() == Planner.WALK) {
			this.walking = decision == null ? 5 : decision.move().length();
			this.getNavigation().moveTo(target, 1.0);
			return;
		}
		this.perform(decision.move().id(), decision.delay());
	}

	/** The phase of the fight, as its moves know it. */
	private String phase() {
		return this.dark ? "dark" : "light";
	}

	/** Starts a move from its tell, dropping whatever ran before. Public so the game tests can call each one. */
	public void perform(final String id) {
		this.perform(id, 0);
	}

	/** Starts a move, holding its blow {@code delay} ticks at the end of the tell. */
	public void perform(final String id, final int delay) {
		BossMove move = SnufferMoves.SET.get(id);
		this.runner.start(this, move);
		this.runner.delay(delay);
		this.getNavigation().stop();
		this.walking = 0;
		LivingEntity target = this.getTarget();
		this.brain.started(move, delay, target instanceof Player player ? player.getUUID() : null, this.position(), this.level().getGameTime());
		this.syncMove();
	}

	/** Its mind, for the game tests. */
	public BossBrain brain() {
		return this.brain;
	}

	/** A dead keeper finishes nothing it began. */
	@Override
	public void die(final DamageSource source) {
		this.runner.stop();
		this.syncMove();
		super.die(source);
	}

	/** Test hook: deals a move's blow at once, skipping the tell, on the part that move strikes. */
	public void strikeNow(final ServerLevel level, final ServerPlayer player, final String id) {
		BossMove.Strike strike = SnufferMoves.SET.get(id).strikes().getFirst();
		MoveRunner.hit(level, this, player, strike.part(), strike.damage());
	}

	// ── The dark half ─────────────────────────────────────────────

	private void goDark(final ServerLevel level) {
		this.dark = true;
		this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.SOUL_LANTERN));
		for (BlockPos lamp : this.lamps(level)) {
			level.setBlockAndUpdate(lamp, level.getBlockState(lamp).setValue(LampStandBlock.LIT, false));
		}
		level.playSound(null, this.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2.0F, 0.5F);
		for (ServerPlayer player : this.bar.getPlayers()) {
			player.sendSystemMessage(Component.translatable("message.mistinn.snuffer.dark"), true);
		}
	}

	private void darkRoom(final ServerLevel level) {
		for (ServerPlayer player : this.bar.getPlayers()) {
			if (player.distanceTo(this) < ROOM_RADIUS + 4 && (this.tickCount % 40 == 0 || !player.hasEffect(MobEffects.DARKNESS))) {
				player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
			}
		}
		List<BlockPos> lamps = this.lamps(level);
		long lit = lamps.stream().filter(pos -> level.getBlockState(pos).getValue(LampStandBlock.LIT)).count();
		if (this.staggered <= 0 && lamps.size() >= 4 && lit == lamps.size()) {
			this.stagger(level);
		}
		// after a stagger it snuffs the lamps again, one at a time
		if (this.staggered <= 0 && lit > 0 && --this.snuffTimer <= 0) {
			this.snuffTimer = 60;
			for (BlockPos pos : lamps) {
				BlockState state = level.getBlockState(pos);
				if (state.getValue(LampStandBlock.LIT)) {
					level.setBlockAndUpdate(pos, state.setValue(LampStandBlock.LIT, false));
					level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 10, 0.1, 0.1, 0.1, 0.02);
					break;
				}
			}
		}
	}

	private void stagger(final ServerLevel level) {
		this.staggered = STAGGER_TICKS;
		this.snuffTimer = STAGGER_TICKS + 40;
		this.perform("stagger");
		level.playSound(null, this.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 2.0F, 0.6F);
		for (ServerPlayer player : this.bar.getPlayers()) {
			player.sendSystemMessage(Component.translatable("message.mistinn.snuffer.stagger"), true);
		}
	}

	/** The lamp stands of this keeper's room. */
	public List<BlockPos> lamps(final ServerLevel level) {
		List<BlockPos> found = new ArrayList<>();
		BlockPos centre = this.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-ROOM_RADIUS, -3, -ROOM_RADIUS), centre.offset(ROOM_RADIUS, 3, ROOM_RADIUS))) {
			if (level.getBlockState(pos).is(MistinnBlocks.LAMP_STAND)) {
				found.add(pos.immutable());
			}
		}
		return found;
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float damage) {
		return super.hurtServer(level, source, this.staggered > 0 ? damage * STAGGER_DAMAGE : damage);
	}

	// ── Voice ────────────────────────────────────────────────────

	/** Breath drawn through the empty lantern, its frame creaking and the hook's chain stirring. */
	@Override
	protected SoundEvent getAmbientSound() {
		return MistinnSounds.SNUFFER_AMBIENT;
	}

	/** The lantern head struck: tin and cracked glass, and a hiss. */
	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return MistinnSounds.SNUFFER_HURT;
	}

	/** A long exhale, then the lantern crashing down. */
	@Override
	protected SoundEvent getDeathSound() {
		return MistinnSounds.SNUFFER_DEATH;
	}

	/** A heavy, slow footfall with the hook's chain swinging. */
	@Override
	protected void playStepSound(final BlockPos pos, final BlockState state) {
		this.playSound(MistinnSounds.SNUFFER_STEP, 0.7F, 0.9F + this.random.nextFloat() * 0.2F);
	}

	// ── Health bar and spoils ─────────────────────────────────────

	private void updateBarViewers(final ServerLevel level) {
		for (ServerPlayer player : List.copyOf(this.bar.getPlayers())) {
			if (!player.isAlive() || player.level() != level || player.distanceTo(this) > BAR_RANGE) {
				this.bar.removePlayer(player);
			}
		}
		for (ServerPlayer player : level.getPlayers(p -> p.distanceTo(this) <= BAR_RANGE)) {
			this.bar.addPlayer(player);
		}
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
	protected void dropCustomDeathLoot(final ServerLevel level, final DamageSource source, final boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		this.spawnAtLocation(level, new ItemStack(MistinnItems.RUST_GOD_SALT));
		if (this.random.nextFloat() < 0.3F) {
			this.spawnAtLocation(level, new ItemStack(this.gearDrop()));
		}
		if (source.getEntity() instanceof ServerPlayer player) {
			SaltService.add(player, 3000);
		}
		this.bar.removeAllPlayers();
	}

	/** How long the fallen keeper lies before it is gone, in a story world: its last words play over it. */
	public static final int STORY_DEATH_TICKS = 220;

	@Override
	protected void tickDeath() {
		if (!(this.level() instanceof ServerLevel level) || !life.shizune.mistinn.story.StoryService.active(level.getServer())) {
			super.tickDeath();
			return;
		}
		this.deathTime++;
		if (this.deathTime >= STORY_DEATH_TICKS && !this.isRemoved()) {
			level.broadcastEntityEvent(this, (byte) 60);
			this.remove(RemovalReason.KILLED);
		}
	}

	/** One piece of tier III or IV gear. */
	private net.minecraft.world.item.Item gearDrop() {
		int tier = 3 + this.random.nextInt(2);
		List<net.minecraft.world.item.Item> pool = List.of(
			MistinnItems.HELMETS.get(tier), MistinnItems.ARMORS.get(tier), MistinnItems.MASKS.get(tier),
			MistinnItems.RIGS.get(tier), MistinnItems.LEGGINGS.get(tier), MistinnItems.BOOTS.get(tier)
		);
		return pool.get(this.random.nextInt(pool.size()));
	}
}
