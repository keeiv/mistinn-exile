package life.shizune.mistinn.boss;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What a boss has seen of one hunter, the last few seconds of it. It sees them {@link #DELAY} ticks late,
 * as a person would: nothing newer than that is ever handed out.
 */
public final class PlayerTrace {
	/** Ticks between something happening and the boss seeing it. */
	public static final int DELAY = 4;
	private static final int SIZE = 64;

	private final Observation[] ring = new Observation[SIZE];
	private int next;
	private int count;

	public void record(final Observation observation) {
		this.ring[this.next] = observation;
		this.next = (this.next + 1) % SIZE;
		this.count = Math.min(this.count + 1, SIZE);
	}

	/** The newest observation the boss can see at {@code now}, or null if it has seen nothing yet. */
	public @Nullable Observation seen(final long now) {
		for (int i = 1; i <= this.count; i++) {
			Observation o = this.ring[Math.floorMod(this.next - i, SIZE)];
			if (o.time() <= now - DELAY) {
				return o;
			}
		}
		return null;
	}

	/** What the boss can see of the last {@code ticks} ticks, oldest first. */
	public List<Observation> history(final long now, final int ticks) {
		List<Observation> out = new ArrayList<>();
		long newest = now - DELAY;
		for (int i = this.count; i >= 1; i--) {
			Observation o = this.ring[Math.floorMod(this.next - i, SIZE)];
			if (o.time() <= newest && o.time() > newest - ticks) {
				out.add(o);
			}
		}
		return out;
	}

	public void clear() {
		java.util.Arrays.fill(this.ring, null);
		this.next = 0;
		this.count = 0;
	}
}
