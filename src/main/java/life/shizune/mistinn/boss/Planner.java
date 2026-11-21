package life.shizune.mistinn.boss;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Thinks a few moves ahead. From where things stand it walks the move graph up to {@link #MAX_DEPTH} steps
 * deep, keeping only the best few paths at each depth (a beam search), and scores each step against what the
 * hunter is likely to do when its tell begins: roll left, roll right, roll back, back off, hit back, or take it.
 * The score weighs the damage it expects to land (more for the head and the legs) against the openings it
 * leaves, how well it keeps its distance, how hard it presses a hunter who is healing or spent, how well a
 * feint or a held blow catches a hunter who rolls early, and how stale it would be to do the same thing again.
 * Only the first step is ever used; the next decision thinks again from scratch.
 */
public final class Planner {
	public static final int MAX_DEPTH = 6;
	/** What a step is worth shrinks by this much for every second further away it begins: the future is a guess, and slow is bad. */
	private static final double DISCOUNT = 0.85;
	/** How far a roll carries a hunter, in blocks. */
	private static final double ROLL_DISTANCE = 3.2;
	/** How far a hunter walks back during a tell, at most. */
	private static final double BACK_OFF = 2.5;
	/** Ticks of dodge a roll gives on whole legs. */
	private static final int ROLL_IFRAMES = 10;
	/** Ticks a hunter who just rolled cannot roll again (the roll and its cooldown). */
	private static final int ROLL_BUSY = 18;
	/** How long a blow may be held at the end of its tell, waiting out an early roll. */
	public static final int DELAY = 8;
	/** How fast the boss closes in when it only walks, blocks per tick. */
	private static final double WALK_SPEED = 0.25;
	private static final int WALK_TICKS = 10;
	/** How many recent choices the planner remembers so it does not repeat itself, or a pattern. */
	private static final int MEMORY = 6;
	/**
	 * What the time a path leaves free is worth, per second, out to {@link #HORIZON} ticks: paths of different lengths
	 * are compared fairly, so a quick feint and its follow-up are not beaten by two slow blows just for using fewer steps.
	 */
	private static final double FUTURE_RATE = 2.5;
	private static final int HORIZON = 200;
	/** What flowing from one move into the next through a link is worth: it is quicker, and it reads as one fighter. */
	private static final double CHAIN_BONUS = 0.8;
	/** How far past its radius a room reaches: a square room's corners lie about half again as far out. */
	public static final double ROOM_REACH = 1.5;
	/** Ticks a signal binds the next attack to its family. */
	public static final int SIGNAL_TICKS = 60;

	/** Plain walking toward the hunter: not a move in any set, so it has no tell; the brain walks instead. */
	public static final BossMove WALK = BossMove.builder("walk", BossMove.Kind.IDLE).unannounced(0).timing(0, WALK_TICKS).build();

	public record Weights(double hit, double head, double legs, double risk, double range, double corner, double pressure, double bait, double variety) {
		public static final Weights DEFAULT = new Weights(1.0, 0.35, 0.3, 0.5, 0.25, 0.15, 0.6, 1.0, 0.3);
	}

	/**
	 * Where things stand when the boss thinks: its position and facing, the fight's phase, the ticks each move
	 * still has to wait, the move it is in and how far, the hunter as the boss believes them to be now (where,
	 * how fast, doing what, and what the boss has learned of them), a signal it still owes, and its room.
	 */
	public record Situation(Vec3 bossPos, float bossYaw, String phase, Map<String, Integer> cooldowns, @Nullable BossMove last, int lastTicks,
		Vec3 hunterPos, Vec3 hunterVelocity, PlayerAction hunterAction, float hunterStamina, PlayerModel hunter, @Nullable String signal, int signalTicksLeft,
		Vec3 roomCentre, double roomRadius) {
	}

	/** The path chosen: its moves, the tick (from now) each begins, how long each holds its blow, and what it was worth. */
	public record Plan(List<BossMove> steps, List<Integer> starts, List<Integer> delays, double score, int depth, long nanos) {
		public @Nullable BossMove first() {
			return this.steps.isEmpty() ? null : this.steps.getFirst();
		}

		/** Ticks the first move should hold its blow. */
		public int firstDelay() {
			return this.delays.isEmpty() ? 0 : this.delays.getFirst();
		}
	}

	/** One path through the graph, as far as it goes. */
	private static final class Node {
		final @Nullable Node parent;
		final @Nullable BossMove move;
		final int delay;
		final int depth;
		final Vec3 boss;
		/** Where the hunter is believed to have been pushed by the tells so far (rolled or backed away). */
		final Vec3 shift;
		/** Tick (from now) the move began, and the tick the next one may begin by default. */
		final int start;
		final int end;
		final int[] readyAt;
		/** How likely the hunter is free to react to the next tell (not mid-roll, not committed to healing). */
		final double ready;
		final @Nullable String signal;
		final int signalUntil;
		final double score;
		/** Tick (from now) of this path's last tell the hunter answered. */
		final int lastStrike;

		Node(final @Nullable Node parent, final @Nullable BossMove move, final int delay, final int depth, final Vec3 boss, final Vec3 shift, final int start,
			final int end, final int[] readyAt, final double ready, final @Nullable String signal, final int signalUntil, final double score, final int lastStrike) {
			this.parent = parent;
			this.move = move;
			this.delay = delay;
			this.depth = depth;
			this.boss = boss;
			this.shift = shift;
			this.start = start;
			this.end = end;
			this.readyAt = readyAt;
			this.ready = ready;
			this.signal = signal;
			this.signalUntil = signalUntil;
			this.score = score;
			this.lastStrike = lastStrike;
		}

		Plan plan(final long nanos) {
			List<BossMove> steps = new ArrayList<>();
			List<Integer> starts = new ArrayList<>();
			List<Integer> delays = new ArrayList<>();
			for (Node n = this; n != null && n.move != null; n = n.parent) {
				steps.addFirst(n.move);
				starts.addFirst(n.start);
				delays.addFirst(n.delay);
			}
			return new Plan(steps, starts, delays, this.score, this.depth, nanos);
		}
	}

	/** What the hunter's answers to a tell come to: the chance a blow lands, how far they get pushed away, and how often they roll. */
	private record Outcome(double chance, double push, double rolled) {
	}

	private final MoveSet set;
	private final Weights weights;
	private final int beam;
	private final int branch;
	private final RandomSource random;
	private final Deque<String> recent = new ArrayDeque<>();

	public Planner(final MoveSet set, final Weights weights, final int beam, final int branch, final RandomSource random) {
		this.set = set;
		this.weights = weights;
		this.beam = beam;
		this.branch = branch;
		this.random = random;
	}

	public Planner(final MoveSet set, final RandomSource random) {
		this(set, Weights.DEFAULT, 6, 12, random);
	}

	/** Tells the planner which move was actually begun, so it can avoid becoming predictable. */
	public void commit(final BossMove move) {
		this.recent.addLast(move.id());
		while (this.recent.size() > MEMORY) {
			this.recent.removeFirst();
		}
	}

	public void forget() {
		this.recent.clear();
	}

	public Plan plan(final Situation now, final long budgetNanos) {
		long began = System.nanoTime();
		int[] readyAt = new int[this.set.size()];
		for (Map.Entry<String, Integer> cooldown : now.cooldowns().entrySet()) {
			int i = this.set.index(cooldown.getKey());
			if (i >= 0) {
				readyAt[i] = cooldown.getValue();
			}
		}
		double ready = switch (now.hunterAction()) {
			case TREAT -> 0.25;
			case ROLL -> 0.2;
			case SWING -> 0.6;
			default -> 1.0;
		};
		Node root = new Node(null, null, 0, 0, now.bossPos(), Vec3.ZERO, 0, 0, readyAt, ready, now.signalTicksLeft() > 0 ? now.signal() : null,
			now.signalTicksLeft(), 0.0, -100);
		List<Node> frontier = List.of(root);
		Node best = null;
		List<Node> children = new ArrayList<>();
		search:
		for (int depth = 0; depth < MAX_DEPTH; depth++) {
			children.clear();
			for (Node node : frontier) {
				if (System.nanoTime() - began > budgetNanos && best != null) {
					break search;
				}
				this.expand(now, node, children);
			}
			if (children.isEmpty()) {
				break;
			}
			children.sort(Comparator.comparingDouble(Planner::rank).reversed());
			frontier = List.copyOf(children.subList(0, Math.min(this.beam, children.size())));
			best = frontier.getFirst();
		}
		long nanos = System.nanoTime() - began;
		return best == null ? new Plan(List.of(), List.of(), List.of(), 0.0, 0, nanos) : best.plan(nanos);
	}

	/** Every move that may follow {@code node}, scored; only the best {@link #branch} go on. */
	private void expand(final Situation now, final Node node, final List<Node> out) {
		List<Node> mine = new ArrayList<>();
		boolean inMove = node.move == null && now.last() != null && now.lastTicks() < now.last().length();
		List<BossMove> candidates = inMove ? this.set.followUps(now.last(), now.lastTicks()) : this.set.moves();
		for (BossMove move : candidates) {
			add(mine, this.step(now, node, move, 0));
			if (delayable(move)) {
				add(mine, this.step(now, node, move, DELAY));
			}
		}
		if (!inMove) {
			add(mine, this.step(now, node, WALK, 0));
		}
		mine.sort(Comparator.comparingDouble(Planner::rank).reversed());
		out.addAll(mine.subList(0, Math.min(this.branch, mine.size())));
	}

	/** A path's worth so far, and what the time it leaves free is likely worth. */
	private static double rank(final Node node) {
		double from = Math.pow(DISCOUNT, node.end / 20.0);
		double to = Math.pow(DISCOUNT, HORIZON / 20.0);
		return node.score + FUTURE_RATE * Math.max(0.0, from - to) / -Math.log(DISCOUNT);
	}

	private static void add(final List<Node> nodes, final @Nullable Node node) {
		if (node != null) {
			nodes.add(node);
		}
	}

	/** A blow that may be held at the end of its tell: an attack whose blows all fall at fixed ticks. */
	public static boolean delayable(final BossMove move) {
		if (move.kind() != BossMove.Kind.ATTACK || move.strikes().isEmpty() || move.windup() <= 0) {
			return false;
		}
		for (BossMove.Strike strike : move.strikes()) {
			if (strike.tick() == BossMove.Strike.SCRIPTED) {
				return false;
			}
		}
		return true;
	}

	/** One step of the simulation: where it leaves everything, and what it was worth. */
	private @Nullable Node step(final Situation now, final Node node, final BossMove move, final int delay) {
		if (!move.plannable()) {
			return null;
		}
		int index = move == WALK ? -1 : this.set.index(move.id());
		int start = node.end;
		boolean chained = false;
		BossMove previous = node.move != null ? node.move : now.last() != null && now.lastTicks() < now.last().length() ? now.last() : null;
		if (previous != null) {
			int previousStart = node.move != null ? node.start : -now.lastTicks();
			for (MoveSet.Link link : this.set.links()) {
				if (link.from().equals(previous.id()) && link.to().equals(move.id())) {
					start = Math.max(previousStart + link.earliest(), node.move != null ? previousStart : 0);
					chained = true;
				}
			}
		}
		if (index >= 0 && node.readyAt[index] > start) {
			return null;
		}
		if (move.in("dark") && !"dark".equals(now.phase())) {
			return null;
		}
		boolean signalOwed = node.signal != null && start < node.signalUntil;
		if (signalOwed && (move.kind() == BossMove.Kind.FEINT || move.kind() == BossMove.Kind.SIGNAL)) {
			return null;
		}
		if (signalOwed && !move.strikes().isEmpty() && !move.in(node.signal)) {
			return null;
		}
		Vec3 hunter = this.hunterAt(now, node, start);
		Vec3 toHunter = flat(hunter.subtract(node.boss));
		double distance = toHunter.length();
		if (move != WALK && !move.range().contains(distance)) {
			return null;
		}
		Vec3 facing = distance < 1.0E-4 ? forward(now.bossYaw()) : toHunter.scale(1.0 / distance);
		int length = move.length() + delay;
		Vec3 bossAfter;
		Vec3 shift = node.shift;
		double value;
		int lastStrike = node.lastStrike;
		// a hunter who rolled has the roll back once its cooldown is over; what they are doing now holds for the first step only
		double ready = node.depth > 0 && start - node.lastStrike > ROLL_BUSY ? 1.0 : node.ready;
		double readyAfter = ready;
		if (move == WALK) {
			double walked = Math.min(Math.max(0.0, distance - 2.5), WALK_SPEED * WALK_TICKS);
			bossAfter = node.boss.add(facing.scale(walked));
			// walking gives the hunter time: it costs a little, so the boss does not dither in reach
			value = this.spacing(now, bossAfter, this.hunterAt(now, node, start + WALK_TICKS)) * 0.5 - 0.4;
		} else {
			bossAfter = this.inRoom(now, node.boss.add(turn(move.motion().at(move.length()), facing)));
			value = 0.0;
			if (move.kind() == BossMove.Kind.ATTACK || move.kind() == BossMove.Kind.FEINT || !move.strikes().isEmpty()) {
				Outcome outcome = this.outcome(now, node, move, facing, start, delay, ready);
				value += move.kind() == BossMove.Kind.FEINT ? this.bait(outcome, ready) : outcome.chance() * this.worth(now, move);
				lastStrike = start + move.windup() + delay;
				readyAfter = 1.0 - ready * outcome.rolled();
				shift = shift.add(flat(hunter.subtract(node.boss)).normalize().scale(outcome.push()));
				value -= this.risk(now, move, bossAfter, this.hunterAt(now, node, start + length));
			} else if (move.kind() == BossMove.Kind.SIGNAL) {
				// a promise it keeps: worth showing from a distance it can follow up from, while the hunter is not pressing it
				value += distance >= 3.0 && distance <= 10.0 && now.hunterAction() != PlayerAction.SWING ? 2.6 : 0.05;
			} else if (move.kind() == BossMove.Kind.IDLE) {
				value += 0.05;
			}
			value += this.spacing(now, bossAfter, this.hunterAt(now, node, start + length));
			if (previous != null && previous.id().equals(move.id())) {
				value -= this.weights.variety * Math.abs(value) + this.weights.variety;
			}
			if (chained) {
				value += CHAIN_BONUS;
			}
			if (node.depth == 0) {
				value = this.stale(move, value);
			}
		}
		value += (this.random.nextDouble() - 0.5) * 0.05;
		int end = start + length;
		int[] readyAt = node.readyAt;
		if (index >= 0) {
			readyAt = readyAt.clone();
			readyAt[index] = end + move.cooldown();
		}
		String signal = node.signal;
		int signalUntil = node.signalUntil;
		if (move.kind() == BossMove.Kind.SIGNAL && move.signals() != null) {
			signal = move.signals();
			signalUntil = end + SIGNAL_TICKS;
		} else if (!move.strikes().isEmpty() && signalOwed) {
			signal = null;
		}
		double score = node.score + value * Math.pow(DISCOUNT, start / 20.0);
		return new Node(node, move, delay, node.depth + 1, bossAfter, shift, start, end, readyAt, readyAfter, signal, signalUntil, score, lastStrike);
	}

	/** The same move again, or one it keeps coming back to, is what a hunter learns to read. */
	private double stale(final BossMove move, final double value) {
		int repeats = 0;
		for (var it = this.recent.descendingIterator(); it.hasNext() && it.next().equals(move.id()); ) {
			repeats++;
		}
		int seen = 0;
		for (String id : this.recent) {
			if (id.equals(move.id())) {
				seen++;
			}
		}
		// and the same kind of move run after run is the same routine, whatever its name
		String family = this.family(move);
		int kindRepeats = 0;
		for (var it = this.recent.descendingIterator(); it.hasNext(); ) {
			String id = it.next();
			if (this.set.index(id) < 0 || !this.family(this.set.get(id)).equals(family)) {
				break;
			}
			kindRepeats++;
		}
		double keep = Math.max(0.05, 1.0 - 0.3 * repeats - 0.12 * seen - 0.25 * kindRepeats);
		// a worthless move is not made better by being stale
		double scaled = value > 0.0 ? value * keep : value - (1.0 - keep);
		return scaled - this.weights.variety * repeats;
	}

	/** What a blow landing is worth: its damage, more on the head or the legs, more still on a hunter healing or spent. */
	private double worth(final Situation now, final BossMove move) {
		BossMove.Strike strike = move.strikes().getFirst();
		double worth = strike.damage() * this.weights.hit;
		worth *= switch (strike.part().aim()) {
			case HEAD -> 1.0 + this.weights.head;
			case LEGS -> 1.0 + this.weights.legs;
			case BODY -> 1.0;
		};
		if (now.hunterAction() == PlayerAction.TREAT || now.hunterStamina() < 30.0F) {
			worth *= 1.0 + this.weights.pressure;
		}
		return worth;
	}

	/**
	 * Plays out what the hunter may do once a tell begins: for each answer, where they end up, whether the blow
	 * (if the move has one) still reaches them there, and whether a roll's dodge covers the moment it lands.
	 */
	private Outcome outcome(final Situation now, final Node node, final BossMove move, final Vec3 facing, final int start, final int delay, final double ready) {
		BossMove.Strike strike = move.strikes().isEmpty() ? null : move.strikes().getFirst();
		int strikeTick = (strike == null || strike.tick() == BossMove.Strike.SCRIPTED ? move.windup() : strike.tick()) + delay;
		// a blow carried along a dash is checked at every tick it is carried through, from where the body is by then
		List<BossMove.Strike> along = new ArrayList<>();
		if (strike != null) {
			for (BossMove.Strike other : move.strikes()) {
				if (other == strike || strike.group() > 0 && other.group() == strike.group()) {
					along.add(other);
				}
			}
		}
		Vec3 stay = this.hunterAt(now, node, start + strikeTick);
		Vec3 boss = node.boss.add(turn(move.motion().at(Math.min(strikeTick, move.length())), facing));
		double still = strike != null && this.landsAlong(now, node, move, along, facing, start, delay, Vec3.ZERO) ? 1.0 : 0.0;
		Vec3 away = flat(stay.subtract(boss));
		double from = away.length();
		Vec3 back = from < 1.0E-4 ? facing : away.scale(1.0 / from);
		// the hunter faces the boss: their left is to the right of the line running away from it
		Vec3 left = new Vec3(-back.z, 0.0, back.x);
		String family = this.family(move);
		double dodge = now.hunter().dodge(family, delay, ROLL_IFRAMES);
		double reacting = 0.0;
		double push = 0.0;
		double rolled = 0.0;
		for (Map.Entry<Response, Double> r : now.hunter().responses(family).entrySet()) {
			Vec3 offset = switch (r.getKey()) {
				case ROLL_LEFT -> left.scale(ROLL_DISTANCE);
				case ROLL_RIGHT -> left.scale(-ROLL_DISTANCE);
				case ROLL_BACK -> back.scale(ROLL_DISTANCE);
				case BACK_OFF -> back.scale(Math.min(BACK_OFF, 0.12 * (move.windup() + delay)));
				case COUNTER, STAY -> Vec3.ZERO;
			};
			double p = r.getValue();
			push += p * (flat(stay.add(offset).subtract(boss)).length() - from);
			if (r.getKey().roll()) {
				rolled += p;
			}
			if (strike != null && this.landsAlong(now, node, move, along, facing, start, delay, offset)) {
				reacting += p * (r.getKey().roll() ? 1.0 - dodge : 1.0);
			}
		}
		return new Outcome((1.0 - ready) * still + ready * reacting, ready * push, rolled);
	}

	/** The chance a move begun now lands its blow, over what the hunter may do once its tell begins. */
	public double landing(final Situation now, final BossMove move) {
		Node root = new Node(null, null, 0, 0, now.bossPos(), Vec3.ZERO, 0, 0, new int[this.set.size()], 1.0, null, 0, 0.0, -100);
		Vec3 toHunter = flat(now.hunterPos().subtract(now.bossPos()));
		Vec3 facing = toHunter.lengthSqr() < 1.0E-8 ? forward(now.bossYaw()) : toHunter.normalize();
		return this.outcome(now, root, move, facing, 0, 0, 1.0).chance();
	}

	/** Whether any of the blows reaches the hunter, moved by {@code offset} from where they are believed to be by then. */
	private boolean landsAlong(final Situation now, final Node node, final BossMove move, final List<BossMove.Strike> along, final Vec3 facing,
		final int start, final int delay, final Vec3 offset) {
		for (BossMove.Strike strike : along) {
			int tick = (strike.tick() == BossMove.Strike.SCRIPTED ? move.windup() : strike.tick()) + delay;
			Vec3 boss = this.inRoom(now, node.boss.add(turn(move.motion().at(Math.min(tick, move.length())), facing)));
			if (this.lands(move, strike, boss, facing, this.hunterAt(now, node, start + tick).add(offset))) {
				return true;
			}
		}
		return false;
	}

	/** Whether a strike from {@code boss} facing {@code facing} reaches a hunter standing at {@code at}. */
	private boolean lands(final BossMove move, final BossMove.Strike strike, final Vec3 boss, final Vec3 facing, final Vec3 at) {
		if (strike.tick() == BossMove.Strike.SCRIPTED) {
			return move.range().contains(flat(at.subtract(boss)).length());
		}
		return strike.shape().reaches(boss, facing, at, 0.3, true);
	}

	/** A feint lands nothing itself; it is worth what it baits: a hunter who rolls or backs off at its tell has spent it. */
	private double bait(final Outcome outcome, final double ready) {
		return this.weights.bait * ready * (outcome.rolled() + Math.max(0.0, outcome.push()) * 0.1) * 2.0 - 0.6;
	}

	/** The opening a move leaves: a hunter close enough to hit back during its recovery, and inclined to. */
	private double risk(final Situation now, final BossMove move, final Vec3 boss, final Vec3 hunter) {
		double reach = 3.0 + 0.7;
		if (flat(hunter.subtract(boss)).length() > reach) {
			return 0.0;
		}
		double counter = now.hunter().responses(this.family(move)).get(Response.COUNTER) + 0.15;
		int open = Math.max(0, move.recovery() - now.hunter().counterDelay());
		return this.weights.risk * counter * open / 10.0;
	}

	/** Standing well: near the distance its moves like, and with the hunter backed toward the wall. */
	private double spacing(final Situation now, final Vec3 boss, final Vec3 hunter) {
		double distance = flat(hunter.subtract(boss)).length();
		double ideal = 3.5;
		double value = -this.weights.range * Math.min(4.0, Math.abs(distance - ideal)) * 0.25;
		if (now.roomRadius() > 0.0) {
			double edge = flat(hunter.subtract(now.roomCentre())).length() / now.roomRadius();
			value += this.weights.corner * Mth.clamp(edge, 0.0, 1.0);
		}
		if (now.hunterAction() == PlayerAction.TREAT || now.hunterStamina() < 30.0F) {
			value -= this.weights.pressure * Math.min(1.0, distance / 10.0);
		}
		return value;
	}

	/** The family whose tell a move shows: a feint shows the tell of the move it imitates. */
	public String family(final BossMove move) {
		BossMove shown = move.imitates() != null ? this.set.get(move.imitates()) : move;
		return shown.families().isEmpty() ? shown.id() : shown.families().iterator().next();
	}

	/** Where the hunter is believed to be {@code ticks} from now: running on for up to a second, pushed by the tells so far. */
	private Vec3 hunterAt(final Situation now, final Node node, final int ticks) {
		int carried = Math.min(Math.max(0, ticks), 20);
		return now.hunterPos().add(now.hunterVelocity().x * carried, 0.0, now.hunterVelocity().z * carried).add(node.shift);
	}

	/** Where a move carries the body, held inside its room as the move runner holds it (walking is not held). */
	private Vec3 inRoom(final Situation now, final Vec3 at) {
		double reach = now.roomRadius() * ROOM_REACH;
		Vec3 from = flat(at.subtract(now.roomCentre()));
		double out = from.length();
		if (now.roomRadius() <= 0.0 || out <= reach) {
			return at;
		}
		return now.roomCentre().add(from.scale(reach / out)).add(0.0, at.y - now.roomCentre().y, 0.0);
	}

	private static Vec3 flat(final Vec3 v) {
		return new Vec3(v.x, 0.0, v.z);
	}

	private static Vec3 forward(final float yaw) {
		float r = yaw * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(r), 0.0, Mth.cos(r));
	}

	/** A move's own offset (forward, up, to the right) laid along {@code facing}. */
	private static Vec3 turn(final Vec3 local, final Vec3 facing) {
		Vec3 right = new Vec3(-facing.z, 0.0, facing.x);
		return facing.scale(local.x).add(0.0, local.y, 0.0).add(right.scale(local.z));
	}
}
