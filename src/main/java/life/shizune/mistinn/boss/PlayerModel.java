package life.shizune.mistinn.boss;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

/**
 * What a boss believes about one hunter, learned over one fight: where they will be, what they do when a
 * tell of each family begins (counted per family, from a small even prior, so the first few fights' worth
 * of habits matter quickly), the distance they like to keep, and how long after a blow they hit back.
 */
public final class PlayerModel {
	/** Pseudo-counts each response starts with: every response stays possible, and a handful of tells outweigh it. */
	private static final double PRIOR = 0.5;
	/** How far ahead a run is carried on, at most, in ticks: past a second a player has changed their mind. */
	private static final int HORIZON = 20;
	/** How many ticks of a run its speed is read from. */
	private static final int SPEED_WINDOW = 6;
	/** A roll carries on for about this many ticks past where it is seen, then stops. */
	private static final int ROLL_CARRY = 6;
	/** How quickly the habits below follow the latest sighting. */
	private static final double HABIT_RATE = 0.1;

	private final Map<String, EnumMap<Response, Double>> counts = new HashMap<>();
	private double preferredDistance = 4.0;
	private double counterDelay = 10.0;

	/** Where the hunter will be {@code ahead} ticks after {@code now}, read from what the boss has seen. */
	public Vec3 predict(final PlayerTrace trace, final long now, final int ahead) {
		Observation seen = trace.seen(now);
		if (seen == null) {
			return Vec3.ZERO;
		}
		List<Observation> run = trace.history(now, SPEED_WINDOW);
		Vec3 velocity = seen.velocity();
		if (run.size() >= 2) {
			Observation first = run.getFirst();
			Observation last = run.getLast();
			if (last.time() > first.time()) {
				velocity = last.pos().subtract(first.pos()).scale(1.0 / (last.time() - first.time()));
			}
		}
		long span = Math.min(now - seen.time() + ahead, seen.action() == PlayerAction.ROLL ? ROLL_CARRY : HORIZON);
		return seen.pos().add(velocity.x * span, 0.0, velocity.z * span);
	}

	/** Counts what the hunter did when a tell of {@code family} began. */
	public void learn(final String family, final Response response) {
		this.counts.computeIfAbsent(family, f -> new EnumMap<>(Response.class)).merge(response, 1.0, Double::sum);
	}

	/** How likely each response is to a tell of {@code family}; they add up to one. */
	public Map<Response, Double> responses(final String family) {
		EnumMap<Response, Double> seen = this.counts.get(family);
		double total = Response.values().length * PRIOR;
		if (seen != null) {
			for (double n : seen.values()) {
				total += n;
			}
		}
		EnumMap<Response, Double> out = new EnumMap<>(Response.class);
		for (Response response : Response.values()) {
			double n = seen == null ? 0.0 : seen.getOrDefault(response, 0.0);
			out.put(response, (n + PRIOR) / total);
		}
		return out;
	}

	/** Notes how far from the boss the hunter stood while it was not attacking. */
	public void noteDistance(final double distance) {
		this.preferredDistance += (distance - this.preferredDistance) * HABIT_RATE;
	}

	/** Notes how many ticks after a blow's recovery began the hunter struck back. */
	public void noteCounter(final int ticks) {
		this.counterDelay += (ticks - this.counterDelay) * HABIT_RATE * 3.0;
	}

	public double preferredDistance() {
		return this.preferredDistance;
	}

	public int counterDelay() {
		return (int) Math.round(this.counterDelay);
	}
}
