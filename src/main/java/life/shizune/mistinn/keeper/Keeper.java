package life.shizune.mistinn.keeper;

import java.util.List;
import java.util.UUID;
import life.shizune.mistinn.boss.BossBrain;
import life.shizune.mistinn.boss.BossMove;
import life.shizune.mistinn.boss.MoveRunner;
import life.shizune.mistinn.boss.MoveSet;
import life.shizune.mistinn.boss.Planner;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 燈守: what every Lighthouse Keeper shares. A health bar for the hunters near it; a mind of its own for the
 * fight ({@link BossBrain}) that picks its moves and chains them; one move at a time run along its timeline
 * ({@link MoveRunner}) and told to the client, so the body plays the move's clip; a room it will not be carried
 * out of; and full heavy blows that, three in a row, throw it off balance. Each keeper writes only its own moves
 * and the rules of its own room.
 */
public abstract class Keeper extends Monster {
	/** Players this close to the keeper see its health bar. */
	protected static final double BAR_RANGE = 32.0;
	/** Off balance: how long, and how much harder it is hit meanwhile. */
	public static final int STAGGER_TICKS = 100;
	public static final float STAGGER_DAMAGE = 1.5F;
	/** Full heavy blows that throw it off balance, each landing within {@link #POISE_WINDOW} ticks of the last. */
	public static final int POISE_BREAK = 3;
	public static final int POISE_WINDOW = 200;

	/** The move being run, as its index in the keeper's move set plus one (0: none), for the client. */
	private static final EntityDataAccessor<Byte> DATA_MOVE = SynchedEntityData.defineId(Keeper.class, EntityDataSerializers.BYTE);
	/** How far into the move, in ticks. */
	private static final EntityDataAccessor<Integer> DATA_MOVE_TICKS = SynchedEntityData.defineId(Keeper.class, EntityDataSerializers.INT);
	/** Whether the move's clock stands still (in the air, waiting to land): the client holds the pose. */
	private static final EntityDataAccessor<Boolean> DATA_MOVE_HELD = SynchedEntityData.defineId(Keeper.class, EntityDataSerializers.BOOLEAN);

	private final MoveSet set;
	protected final ServerBossEvent bar;
	protected final MoveRunner runner = new MoveRunner();
	/** Its mind for this fight: kept in memory only, never saved. */
	private final BossBrain brain;
	/** Where its room is: where it first stood since it was loaded. */
	protected @Nullable Vec3 home;
	/** Ticks left of plain walking the brain asked for. */
	private int walking;
	/** Whom the move in hand was aimed at when it began: a move does not change its mind halfway. */
	private @Nullable LivingEntity aimedAt;
	protected int staggered;
	/** Full heavy blows taken in a row, and how long until it has its feet under it again. */
	private int poise;
	private int poiseTimer;

	protected Keeper(final EntityType<? extends Keeper> type, final Level level, final MoveSet set, final BossEvent.BossBarColor color) {
		super(type, level);
		this.set = set;
		this.brain = new BossBrain(set, new Planner(set, RandomSource.create()));
		this.bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable(type.getDescriptionId()), color, BossEvent.BossBarOverlay.NOTCHED_10);
		this.setPersistenceRequired();
	}

	/** The name its messages go under: {@code message.mistinn.<key>.<what>}. */
	protected abstract String key();

	/** How far from the middle of its room its room reaches. */
	protected abstract int roomRadius();

	/** The phase of the fight, as its moves know it. */
	protected abstract String phase();

	/** Its room's own rules, each tick before it thinks (the dark, the mist): it may perform or stagger from here. */
	protected void roomRules(final ServerLevel level) {
	}

	/** Thrown off balance, by whatever did it: what else its room does about that. */
	protected void staggeredBy(final ServerLevel level) {
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
		return this.set;
	}

	/** The move the body is in, as the client sees it, or null when it only walks or stands. */
	public @Nullable BossMove currentMove() {
		int i = this.entityData.get(DATA_MOVE) - 1;
		return i >= 0 && i < this.set.size() ? this.set.byIndex(i) : null;
	}

	/** Ticks since the current move began. */
	public int moveTicks() {
		return this.entityData.get(DATA_MOVE_TICKS);
	}

	/** Whether the current move's clock stands still. */
	public boolean moveHeld() {
		return this.entityData.get(DATA_MOVE_HELD);
	}

	protected void syncMove() {
		BossMove move = this.runner.current();
		this.entityData.set(DATA_MOVE, (byte) (move == null ? 0 : this.set.index(move.id()) + 1));
		this.entityData.set(DATA_MOVE_TICKS, this.runner.ticks());
		this.entityData.set(DATA_MOVE_HELD, this.runner.held());
	}

	public boolean staggered() {
		return this.staggered > 0;
	}

	/** How many full heavy blows it has taken in a row, toward {@link #POISE_BREAK}. */
	public int poise() {
		return this.poise;
	}

	/** Its mind, for the game tests. */
	public BossBrain brain() {
		return this.brain;
	}

	/**
	 * A full heavy blow lands: its frame groans, and the third in a row, each within ten seconds of the last,
	 * throws it off balance. A keeper already off balance takes no count.
	 */
	public void takeHeavyBlow(final ServerLevel level) {
		if (this.staggered > 0 || this.isDeadOrDying()) {
			return;
		}
		this.poise++;
		this.poiseTimer = POISE_WINDOW;
		if (this.poise >= POISE_BREAK) {
			this.poise = 0;
			this.poiseTimer = 0;
			this.stagger(level, "message.mistinn." + this.key() + ".stagger_blows");
			return;
		}
		level.playSound(null, this.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.2F, 0.5F + 0.15F * this.poise);
	}

	// ── Every tick ────────────────────────────────────────────────

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		this.bar.setProgress(this.getHealth() / this.getMaxHealth());
		this.updateBarViewers(level);
		this.roomRules(level);
		if (this.home == null) {
			this.home = this.position();
			// a dash may carry it into the corners of its room, not out of it
			this.runner.leash(this.home, this.roomRadius() * Planner.ROOM_REACH);
		}
		this.brain.observe(level, this, this.home, this.roomRadius());
		UUID chosen = this.brain.target();
		Player hunter = chosen == null ? null : level.getPlayerByUUID(chosen);
		this.setTarget(hunter);
		LivingEntity target = hunter;
		if (this.poiseTimer > 0 && --this.poiseTimer == 0) {
			this.poise = 0;
		}
		if (this.staggered > 0) {
			this.staggered--;
			this.getNavigation().stop();
			level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getEyeY() + 0.3, this.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
			this.runner.tick(level, this, this.aimed());
			this.syncMove();
			return;
		}
		if (!this.runner.running()) {
			// nothing that hangs it in the air or hides it outlives the move that did it
			this.setNoGravity(false);
			this.setInvisible(false);
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
			this.runner.tick(level, this, this.aimed());
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

	/** The move's target, while it is still there to be struck. */
	private @Nullable LivingEntity aimed() {
		LivingEntity target = this.aimedAt;
		return target != null && target.isAlive() && !target.isRemoved() && target.level() == this.level() ? target : null;
	}

	/** Whether a move is being run now. */
	protected boolean busy() {
		return this.runner.running();
	}

	/** Starts a move from its tell, dropping whatever ran before. Public so the game tests can call each one. */
	public void perform(final String id) {
		this.perform(id, 0);
	}

	/** Starts a move, holding its blow {@code delay} ticks at the end of the tell. */
	public void perform(final String id, final int delay) {
		BossMove move = this.set.get(id);
		// a move cut off (by a stagger, by the next move) leaves nothing behind: it is seen and it falls
		this.setInvisible(false);
		this.setNoGravity(false);
		this.runner.start(this, move);
		this.runner.delay(delay);
		this.getNavigation().stop();
		this.walking = 0;
		LivingEntity target = this.getTarget();
		this.aimedAt = target;
		this.brain.started(move, delay, target instanceof Player player ? player.getUUID() : null, this.position(), this.level().getGameTime());
		this.syncMove();
	}

	/** Keeps what its moves carry inside a room (the room it first stood in, unless told otherwise). */
	public void leash(final Vec3 centre, final double radius) {
		this.home = centre;
		this.runner.leash(centre, radius);
	}

	/** A dead keeper finishes nothing it began. */
	@Override
	public void die(final DamageSource source) {
		this.runner.stop();
		this.setNoGravity(false);
		this.setInvisible(false);
		this.syncMove();
		super.die(source);
	}

	/** Test hook: deals a move's blow at once, skipping the tell, on the part that move strikes. */
	public void strikeNow(final ServerLevel level, final ServerPlayer player, final String id) {
		BossMove.Strike strike = this.set.get(id).strikes().getFirst();
		MoveRunner.hit(level, this, player, strike.part(), strike.damage());
	}

	/** Thrown off balance: it reels for {@link #STAGGER_TICKS}, taking half again as much, and every hunter near is told. */
	protected void stagger(final ServerLevel level, final String message) {
		this.staggered = STAGGER_TICKS;
		this.staggeredBy(level);
		this.perform("stagger");
		level.playSound(null, this.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 2.0F, 0.6F);
		for (ServerPlayer player : this.bar.getPlayers()) {
			player.sendSystemMessage(Component.translatable(message), true);
		}
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float damage) {
		return super.hurtServer(level, source, this.staggered > 0 ? damage * STAGGER_DAMAGE : damage);
	}

	// ── Health bar ────────────────────────────────────────────────

	/** The hunters who see its health bar. */
	protected List<ServerPlayer> watchers() {
		return List.copyOf(this.bar.getPlayers());
	}

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
}
