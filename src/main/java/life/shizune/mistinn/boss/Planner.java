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
 * feint catches a hunter who rolls early, and how stale it would be to do the same thing again.
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
	/** The chance a roll in reply to a tell is timed well enough to dodge, until the hunter's timing is known. */
	private static final double ROLL_TIMING = 0.6;
	/** Ticks a hunter who just rolled cannot roll again (the roll and its cooldown). */
	private static final int ROLL_BUSY = 18;
	/** How fast the boss closes in when it only walks, blocks per tick. */
	private static final double WALK_SPEED = 0.25;
	private static final int WALK_TICKS = 10;
	/** How many recent choices the planner remembers so it does not repeat itself, or a pattern. */
	private static final int MEMORY = 6;

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

	public record Plan(List<BossMove> steps, double score, int depth, long nanos) {
		public @Nullable BossMove first() {
			return this.steps.isEmpty() ? null : this.steps.getFirst();
		}
	}

	/** One path through the graph, as far as it goes. */
	private static final class Node {
		final @Nullable Node parent;
		final @Nullable BossMove move;
		final int depth;
		final Vec3 boss;
		/** Tick (from now) the move began, and the tick the next one may begin by default. */
		final int start;
		final int end;
		final int[] readyAt;
		/** How likely the hunter is free to react to the next tell (not mid-roll, not committed to healing). */
		final double ready;
		final @Nullable String signal;
		final int signalUntil;
		final double score;
		/** Tick (from now) of this path's last real strike or feint's would-be strike. */
		final int lastStrike;

		Node(final @Nullable Node parent, final @Nullable BossMove move, final int depth, final Vec3 boss, final int start, final int end, final int[] readyAt,
			final double ready, final @Nullable String signal, final int signalUntil, final double score, final int lastStrike) {
			this.parent = parent;
			this.move = move;
			this.depth = depth;
			this.boss = boss;
			this.start = start;
			this.end = end;
			this.readyAt = readyAt;
			this.ready = ready;
			this.signal = signal;
			this.signalUntil = signalUntil;
			this.score = score;
			this.lastStrike = lastStrike;
		}

		List<BossMove> steps() {
			List<BossMove> out = new ArrayList<>();
			for (Node n = this; n != null && n.move != null; n = n.parent) {
				out.addFirst(n.move);
			}
			return out;
		}
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
		Node root = new Node(null, null, 0, now.bossPos(), 0, 0, readyAt, ready, now.signalTicksLeft() > 0 ? now.signal() : null,
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
			children.sort(Comparator.comparingDouble((Node n) -> n.score).reversed());
			frontier = List.copyOf(children.subList(0, Math.min(this.beam, children.size())));
			best = frontier.getFirst();
		}
		if (best == null) {
			return new Plan(List.of(), 0.0, 0, System.nanoTime() - began);
		}
		return new Plan(best.steps(), best.score, best.depth, System.nanoTime() - began);
	}

	/** Every move that may follow {@code node}, scored; only the best {@link #branch} go on. */
	private void expand(final Situation now, final Node node, final List<Node> out) {
		List<Node> mine = new ArrayList<>();
		boolean inMove = node.move == null && now.last() != null && now.lastTicks() < now.last().length();
		List<BossMove> candidates = inMove ? this.set.followUps(now.last(), now.lastTicks()) : this.set.moves();
		for (BossMove move : candidates) {
			Node child = this.step(now, node, move);
			if (child != null) {
				mine.add(child);
			}
		}
		if (!inMove) {
			Node walk = this.step(now, node, WALK);
			if (walk != null) {
				mine.add(walk);
			}
		}
		mine.sort(Comparator.comparingDouble((Node n) -> n.score).reversed());
		out.addAll(mine.subList(0, Math.min(this.branch, mine.size())));
	}

	/** One step of the simulation: where it leaves everything, and what it was worth. */
	private @Nullable Node step(final Situation now, final Node node, final BossMove move) {
		if (!move.plannable()) {
			return null;
		}
		int index = move == WALK ? -1 : this.set.index(move.id());
		int start = node.end;
		BossMove previous = node.move != null ? node.move : now.last() != null && now.lastTicks() < now.last().length() ? now.last() : null;
		if (previous != null) {
			int previousStart = node.move != null ? node.start : -now.lastTicks();
			for (MoveSet.Link link : this.set.links()) {
				if (link.from().equals(previous.id()) && link.to().equals(move.id())) {
					start = Math.max(previousStart + link.earliest(), node.move != null ? previousStart : 0);
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
		if (signalOwed && move.kind() == BossMove.Kind.ATTACK && !move.in(node.signal)) {
			return null;
		}
		Vec3 hunter = this.hunterAt(now, start);
		Vec3 toHunter = flat(hunter.subtract(node.boss));
		double distance = toHunter.length();
		if (move != WALK && !move.range().contains(distance)) {
			return null;
		}
		Vec3 facing = distance < 1.0E-4 ? forward(now.bossYaw()) : toHunter.scale(1.0 / distance);
		Vec3 bossAfter;
		double value;
		int lastStrike = node.lastStrike;
		// a hunter who rolled has the roll back once its cooldown is over; what they are doing now holds for the first step only
		double ready = node.depth > 0 && start - node.lastStrike > ROLL_BUSY ? 1.0 : node.ready;
		double readyAfter = ready;
		if (move == WALK) {
			double walked = Math.min(Math.max(0.0, distance - 2.5), WALK_SPEED * WALK_TICKS);
			bossAfter = node.boss.add(facing.scale(walked));
			value = this.spacing(now, bossAfter, this.hunterAt(now, start + WALK_TICKS)) * 0.5;
		} else {
			Vec3 moved = move.motion().at(move.length());
			bossAfter = node.boss.add(turn(moved, facing));
			value = switch (move.kind()) {
				case ATTACK -> this.attack(now, node, move, facing, start, ready);
				case FEINT -> this.feint(now, move, ready);
				case MOVEMENT -> move.strikes().isEmpty() ? 0.0 : this.attack(now, node, move, facing, start, ready);
				case SIGNAL, IDLE -> 0.05;
				case REACTION -> 0.0;
			};
			value += this.spacing(now, bossAfter, this.hunterAt(now, start + move.length()));
			if (move.kind() == BossMove.Kind.ATTACK || move.kind() == BossMove.Kind.FEINT) {
				String family = this.family(move);
				double rolled = 0.0;
				for (Map.Entry<Response, Double> r : now.hunter().responses(family).entrySet()) {
					if (r.getKey().roll()) {
						rolled += r.getValue();
					}
				}
				lastStrike = start + move.windup();
				readyAfter = 1.0 - ready * rolled;
				value -= this.risk(now, move, bossAfter, this.hunterAt(now, start + move.length()));
			}
			if (previous != null && previous.id().equals(move.id())) {
				value -= this.weights.variety * Math.abs(value) + this.weights.variety;
			}
			if (node.depth == 0) {
				// the same move again, or one it keeps coming back to, is what a hunter learns to read
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
				value *= Math.max(0.05, 1.0 - 0.3 * repeats - 0.12 * seen);
				value -= this.weights.variety * repeats;
			}
		}
		value += (this.random.nextDouble() - 0.5) * 0.05;
		int end = start + move.length();
		int[] readyAt = node.readyAt;
		if (index >= 0) {
			readyAt = readyAt.clone();
			readyAt[index] = end + move.cooldown();
		}
		String signal = node.signal;
		int signalUntil = node.signalUntil;
		if (move.kind() == BossMove.Kind.SIGNAL && move.signals() != null) {
			signal = move.signals();
			signalUntil = end + 60;
		} else if (move.kind() == BossMove.Kind.ATTACK && signalOwed) {
			signal = null;
		}
		double score = node.score + value * Math.pow(DISCOUNT, start / 20.0);
		return new Node(node, move, node.depth + 1, bossAfter, start, end, readyAt, readyAfter, signal, signalUntil, score, lastStrike);
	}

	/** What an attack is expected to be worth: the chance it lands, times what landing is worth. */
	private double attack(final Situation now, final Node node, final BossMove move, final Vec3 facing, final int start, final double ready) {
		if (move.strikes().isEmpty()) {
			return 0.0;
		}
		BossMove.Strike strike = move.strikes().getFirst();
		int strikeTick = strike.tick() == BossMove.Strike.SCRIPTED ? move.windup() : strike.tick();
		Vec3 stay = this.hunterAt(now, start + strikeTick);
		Vec3 boss = node.boss.add(turn(move.motion().at(strikeTick), facing));
		double chance = this.chance(now, move, strike, boss, facing, stay, ready);
		double worth = strike.damage() * this.weights.hit;
		worth *= switch (strike.part().aim()) {
			case HEAD -> 1.0 + this.weights.head;
			case LEGS -> 1.0 + this.weights.legs;
			case BODY -> 1.0;
		};
		if (now.hunterAction() == PlayerAction.TREAT || now.hunterStamina() < 30.0F) {
			worth *= 1.0 + this.weights.pressure;
		}
		return chance * worth;
	}

	/** The chance a strike lands, over what the hunter may do once its tell begins. */
	private double chance(final Situation now, final BossMove move, final BossMove.Strike strike, final Vec3 boss, final Vec3 facing, final Vec3 stay, final double ready) {
		double still = this.lands(move, strike, boss, facing, stay) ? 1.0 : 0.0;
		if (ready <= 0.0) {
			return still;
		}
		Vec3 away = flat(stay.subtract(boss));
		Vec3 back = away.lengthSqr() < 1.0E-4 ? facing : away.normalize();
		// the hunter faces the boss: their left is to the right of the line running away from it
		Vec3 left = new Vec3(-back.z, 0.0, back.x);
		double reacting = 0.0;
		for (Map.Entry<Response, Double> r : now.hunter().responses(this.family(move)).entrySet()) {
			Vec3 where = switch (r.getKey()) {
				case ROLL_LEFT -> stay.add(left.scale(ROLL_DISTANCE));
				case ROLL_RIGHT -> stay.subtract(left.scale(ROLL_DISTANCE));
				case ROLL_BACK -> stay.add(back.scale(ROLL_DISTANCE));
				case BACK_OFF -> stay.add(back.scale(Math.min(BACK_OFF, 0.12 * move.windup())));
				case COUNTER, STAY -> stay;
			};
			double lands = this.lands(move, strike, boss, facing, where) ? 1.0 : 0.0;
			if (r.getKey().roll()) {
				lands *= 1.0 - ROLL_TIMING;
			}
			reacting += r.getValue() * lands;
		}
		return (1.0 - ready) * still + ready * reacting;
	}

	/** Whether a strike from {@code boss} facing {@code facing} reaches a hunter standing at {@code at}. */
	private boolean lands(final BossMove move, final BossMove.Strike strike, final Vec3 boss, final Vec3 facing, final Vec3 at) {
		if (strike.tick() == BossMove.Strike.SCRIPTED) {
			return move.range().contains(flat(at.subtract(boss)).length());
		}
		return strike.shape().reaches(boss, facing, at, 0.3, true);
	}

	/** A feint lands nothing itself; it is worth what it baits: a hunter who rolls at its tell has spent the roll. */
	private double feint(final Situation now, final BossMove move, final double ready) {
		double rolled = 0.0;
		for (Map.Entry<Response, Double> r : now.hunter().responses(this.family(move)).entrySet()) {
			if (r.getKey().roll() || r.getKey() == Response.BACK_OFF) {
				rolled += r.getValue();
			}
		}
		return this.weights.bait * ready * rolled * 2.0 - 0.6;
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
	private String family(final BossMove move) {
		BossMove shown = move.imitates() != null ? this.set.get(move.imitates()) : move;
		return shown.families().isEmpty() ? shown.id() : shown.families().iterator().next();
	}

	/** Where the hunter is believed to be {@code ticks} from now: running on for up to a second. */
	private Vec3 hunterAt(final Situation now, final int ticks) {
		int carried = Math.min(Math.max(0, ticks), 20);
		return now.hunterPos().add(now.hunterVelocity().x * carried, 0.0, now.hunterVelocity().z * carried);
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
