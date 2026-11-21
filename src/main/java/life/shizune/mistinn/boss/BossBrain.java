package life.shizune.mistinn.boss;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import life.shizune.mistinn.body.BodyService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A boss's mind over one fight. Every tick it watches the hunters in its room (seeing them a little late, as
 * {@link PlayerTrace} does), picks which one to go after, and at each decision point (no move running, the
 * window where a move may hand over to the next, or every few ticks of a movement) thinks ahead with the
 * {@link Planner} and takes the first step only. Whenever a tell begins it watches what the hunter does about
 * it and learns: which way they roll and when, whether they back off, hit back or take it. A signal it shows
 * binds its next attack. When its room has had no living hunter for {@link #FIGHT_END} ticks, the fight is
 * over and everything it learned is forgotten. Each boss has its own brain; nothing is shared.
 */
public final class BossBrain {
	/** Ticks without a living hunter in the room before the fight counts as over. */
	public static final int FIGHT_END = 600;
	/** The most a decision may think for; it averages far less. */
	private static final long BUDGET = 3_000_000L;
	/** How often a movement is reconsidered. */
	private static final int REPLAN_MOVING = 5;
	/** How long a blow taken is held against the hunter who dealt it, when choosing whom to go after. */
	private static final int GRUDGE = 60;
	/** Flat speed past which a hunter is taken to be rolling or diving, blocks per tick. */
	private static final double ROLL_SPEED = 0.35;
	/** Ticks a roll is given to carry the hunter before it is read for its direction. */
	private static final int ROLL_SETTLE = 6;
	/** How often the distance a hunter keeps is noted, while the boss is not attacking. */
	private static final int DISTANCE_EVERY = 20;

	/** The move to begin, and how long to hold its blow at the end of the tell. */
	public record Decision(BossMove move, int delay) {
	}

	/** What the brain keeps on one hunter. */
	private static final class Hunter {
		final PlayerTrace trace = new PlayerTrace();
		final PlayerModel model = new PlayerModel();
		@Nullable Observation latest;
		long struckUs = Long.MIN_VALUE;
	}

	/** One tell being watched: whom, from where, and what they have done so far. */
	private static final class Watch {
		final String family;
		final UUID hunter;
		final Vec3 boss;
		final long strike;
		final long recovery;
		final long end;
		@Nullable Vec3 from;
		long rolledAt = -1;
		boolean swung;
		boolean answered;
		boolean countered;

		Watch(final String family, final UUID hunter, final Vec3 boss, final long strike, final long recovery, final long end) {
			this.family = family;
			this.hunter = hunter;
			this.boss = boss;
			this.strike = strike;
			this.recovery = recovery;
			this.end = end;
		}
	}

	private final MoveSet set;
	private final Planner planner;
	private final Map<UUID, Hunter> hunters = new HashMap<>();
	private final Map<String, Long> readyAt = new HashMap<>();
	private @Nullable UUID target;
	private @Nullable String signal;
	private long signalUntil;
	private int empty;
	private @Nullable Watch watch;
	private Vec3 centre = Vec3.ZERO;
	private double radius;

	public BossBrain(final MoveSet set, final Planner planner) {
		this.set = set;
		this.planner = planner;
	}

	/** Whether a player counts as a hunter: alive, not watching, and under the body rules (not creative). */
	public static boolean hunts(final Player player) {
		return player.isAlive() && !player.isSpectator() && BodyService.applies(player);
	}

	// ── Seeing ────────────────────────────────────────────────────

	/** Watches every hunter in the room the boss can notice (its follow range), and picks whom to go after. */
	public void observe(final ServerLevel level, final Mob boss, final Vec3 centre, final double radius) {
		this.centre = centre;
		this.radius = radius;
		long now = level.getGameTime();
		double notice = boss.getAttributeValue(Attributes.FOLLOW_RANGE);
		// a square room's corners lie further out than its radius: a hunter backed into one is still in the room
		double room = radius * 1.5 + 4.0;
		List<ServerPlayer> present = level.getPlayers(player -> hunts(player)
			&& player.position().distanceTo(centre) <= room && player.distanceTo(boss) <= notice);
		if (present.isEmpty()) {
			this.target = null;
			if (++this.empty >= FIGHT_END) {
				this.forget();
			}
			return;
		}
		this.empty = 0;
		LivingEntity struck = boss.getLastHurtByMob();
		boolean fresh = boss.tickCount - boss.getLastHurtByMobTimestamp() < GRUDGE;
		for (ServerPlayer player : present) {
			Hunter hunter = this.hunters.computeIfAbsent(player.getUUID(), id -> new Hunter());
			this.see(player.getUUID(), Observation.of(player, now, hunter.latest), boss.position(), now);
			if (fresh && struck == player) {
				hunter.struckUs = now;
			}
		}
		this.target = this.choose(present, boss, now);
	}

	/** One sighting of a hunter: kept for prediction, and read against the tell being watched. */
	public void see(final UUID id, final Observation observation, final Vec3 boss, final long now) {
		Hunter hunter = this.hunters.computeIfAbsent(id, key -> new Hunter());
		hunter.trace.record(observation);
		hunter.latest = observation;
		this.read(id, hunter, observation);
		if (this.watch == null && id.equals(this.target) && now % DISTANCE_EVERY == 0) {
			hunter.model.noteDistance(observation.pos().subtract(boss).horizontalDistance());
		}
	}

	/** Who to go after: the nearest, unless someone has just struck it, or is healing, spent, or on dead legs. */
	private @Nullable UUID choose(final List<ServerPlayer> present, final Mob boss, final long now) {
		UUID best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (ServerPlayer player : present) {
			Hunter hunter = this.hunters.get(player.getUUID());
			Observation seen = hunter.trace.seen(now);
			double score = -boss.distanceTo(player);
			if (now - hunter.struckUs < GRUDGE) {
				score += 4.0;
			}
			if (seen != null) {
				score += seen.action() == PlayerAction.TREAT ? 4.0 : 0.0;
				score += seen.stamina() < 30.0F ? 3.0 : 0.0;
				score += seen.legsGone() ? 3.0 : 0.0;
			}
			// it keeps after whom it is after unless someone else is clearly better
			score += player.getUUID().equals(this.target) ? 2.0 : 0.0;
			if (score > bestScore) {
				bestScore = score;
				best = player.getUUID();
			}
		}
		return best;
	}

	// ── Learning ──────────────────────────────────────────────────

	/** Reads one sighting against the tell being watched: a roll and its direction, a swing, or a step back. */
	private void read(final UUID id, final Hunter hunter, final Observation o) {
		Watch w = this.watch;
		if (w == null || !w.hunter.equals(id)) {
			return;
		}
		if (w.from == null) {
			w.from = o.pos();
		}
		if (!w.answered) {
			boolean rolling = o.action() == PlayerAction.ROLL || o.velocity().horizontalDistance() > ROLL_SPEED;
			if (rolling && w.rolledAt < 0) {
				w.rolledAt = o.time();
			}
			if (o.action() == PlayerAction.SWING) {
				w.swung = true;
			}
			boolean settled = w.rolledAt >= 0 && o.time() >= w.rolledAt + ROLL_SETTLE;
			if (settled || o.time() >= w.strike + 2) {
				w.answered = true;
				this.learn(hunter.model, w, o);
			}
		}
		if (!w.countered && o.time() >= w.recovery && o.action() == PlayerAction.SWING) {
			w.countered = true;
			hunter.model.noteCounter((int) (o.time() - w.recovery));
		}
		if (o.time() >= w.end) {
			this.watch = null;
		}
	}

	private void learn(final PlayerModel model, final Watch w, final Observation o) {
		Vec3 from = w.from == null ? o.pos() : w.from;
		Vec3 away = new Vec3(from.x - w.boss.x, 0.0, from.z - w.boss.z);
		away = away.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
		// facing the boss, the hunter's left is to the right of the line running away from it
		Vec3 left = new Vec3(-away.z, 0.0, away.x);
		Vec3 moved = new Vec3(o.pos().x - from.x, 0.0, o.pos().z - from.z);
		double lateral = moved.dot(left);
		double radial = moved.dot(away);
		Response response;
		if (w.rolledAt >= 0) {
			if (Math.abs(radial) > Math.abs(lateral)) {
				response = radial > 0.0 ? Response.ROLL_BACK : Response.COUNTER;
			} else {
				response = lateral > 0.0 ? Response.ROLL_LEFT : Response.ROLL_RIGHT;
			}
			model.learnRoll(w.family, (int) (w.strike - w.rolledAt));
		} else if (w.swung) {
			response = Response.COUNTER;
		} else if (radial > 1.0) {
			response = Response.BACK_OFF;
		} else {
			response = Response.STAY;
		}
		model.learn(w.family, response);
	}

	/**
	 * A move has begun (chosen by this brain, or forced): its cooldown starts, a signal it shows binds the next
	 * attack, and a tell it shows is watched against the hunter it was aimed at.
	 */
	public void started(final BossMove move, final int delay, final @Nullable UUID aimedAt, final Vec3 boss, final long now) {
		this.readyAt.put(move.id(), now + move.length() + delay + move.cooldown());
		if (move.kind() == BossMove.Kind.SIGNAL && move.signals() != null) {
			this.signal = move.signals();
			this.signalUntil = now + move.length() + Planner.SIGNAL_TICKS;
		} else if (!move.strikes().isEmpty() && this.signal != null && move.in(this.signal)) {
			this.signal = null;
		}
		if (move.plannable() && move != Planner.WALK) {
			this.planner.commit(move);
		}
		boolean tells = move.kind() == BossMove.Kind.ATTACK || move.kind() == BossMove.Kind.FEINT || !move.strikes().isEmpty();
		if (tells && aimedAt != null) {
			long strike = now + move.windup() + delay;
			this.watch = new Watch(this.planner.family(move), aimedAt, boss, strike, strike + move.active(), now + move.length() + delay);
		}
	}

	// ── Deciding ──────────────────────────────────────────────────

	/**
	 * Thinks, if this is a moment to: nothing running, a move in the window where it may hand over, or a
	 * movement due for another look. Returns what to begin, or null to carry on (or to walk, when idle and the
	 * plan says so: {@link Planner#WALK}).
	 */
	public @Nullable Decision decide(final ServerLevel level, final Mob boss, final MoveRunner runner, final String phase) {
		long now = level.getGameTime();
		BossMove current = runner.current();
		if (current != null) {
			boolean chain = runner.inChainWindow() || current.kind() == BossMove.Kind.MOVEMENT && runner.ticks() % REPLAN_MOVING == 0;
			if (!chain || this.set.followUps(current, runner.ticks()).isEmpty()) {
				return null;
			}
		}
		Hunter hunter = this.target == null ? null : this.hunters.get(this.target);
		Observation seen = hunter == null ? null : hunter.trace.seen(now);
		if (seen == null) {
			return null;
		}
		Map<String, Integer> cooldowns = new HashMap<>();
		for (Map.Entry<String, Long> ready : this.readyAt.entrySet()) {
			if (ready.getValue() > now) {
				cooldowns.put(ready.getKey(), (int) (ready.getValue() - now));
			}
		}
		String owed = this.signal != null && now < this.signalUntil ? this.signal : null;
		Planner.Situation situation = new Planner.Situation(boss.position(), boss.getYRot(), phase, cooldowns, current, runner.ticks(),
			hunter.model.predict(hunter.trace, now, 0), seen.velocity(), seen.action(), seen.stamina(), hunter.model,
			owed, owed == null ? 0 : (int) (this.signalUntil - now), this.centre, this.radius);
		Planner.Plan plan = this.planner.plan(situation, BUDGET);
		BossMove first = plan.first();
		if (first == null || current != null && first == Planner.WALK) {
			return null;
		}
		return new Decision(first, plan.firstDelay());
	}

	/** The hunter it is after, if any is in its room. */
	public @Nullable UUID target() {
		return this.target;
	}

	/** What it has learned of one hunter this fight, or null if it has not watched them. */
	public @Nullable PlayerModel model(final UUID hunter) {
		Hunter known = this.hunters.get(hunter);
		return known == null ? null : known.model;
	}

	/** The fight is over: everything learned about the hunters goes. */
	public void forget() {
		this.hunters.clear();
		this.readyAt.clear();
		this.target = null;
		this.signal = null;
		this.watch = null;
		this.empty = 0;
		this.planner.forget();
	}
}
