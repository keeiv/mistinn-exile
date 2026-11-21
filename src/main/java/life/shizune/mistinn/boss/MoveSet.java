package life.shizune.mistinn.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A boss's moves and the links between them: which move may follow which, from which tick of it. A combo is a
 * path the brain walks through these links, not a fixed routine.
 */
public final class MoveSet {
	/** {@code to} may begin once {@code from} has run {@code earliest} ticks, and no later than {@code latest}. */
	public record Link(String from, String to, int earliest, int latest) {
	}

	private final List<BossMove> moves;
	private final List<Link> links;
	private final Map<String, Integer> index = new HashMap<>();

	public MoveSet(final List<BossMove> moves, final List<Link> links) {
		this.moves = List.copyOf(moves);
		this.links = List.copyOf(links);
		for (int i = 0; i < this.moves.size(); i++) {
			if (this.index.put(this.moves.get(i).id(), i) != null) {
				throw new IllegalArgumentException("two moves named " + this.moves.get(i).id());
			}
		}
		for (Link link : this.links) {
			this.get(link.from());
			this.get(link.to());
		}
	}

	public List<BossMove> moves() {
		return this.moves;
	}

	public List<Link> links() {
		return this.links;
	}

	public BossMove get(final String id) {
		Integer i = this.index.get(id);
		if (i == null) {
			throw new IllegalArgumentException("no move " + id);
		}
		return this.moves.get(i);
	}

	public int index(final String id) {
		return this.index.getOrDefault(id, -1);
	}

	public BossMove byIndex(final int i) {
		return this.moves.get(i);
	}

	public int size() {
		return this.moves.size();
	}

	/** The moves that may begin while {@code from} is at {@code tick}. */
	public List<BossMove> followUps(final BossMove from, final int tick) {
		List<BossMove> out = new ArrayList<>();
		for (Link link : this.links) {
			if (link.from().equals(from.id()) && tick >= link.earliest() && tick <= link.latest()) {
				out.add(this.get(link.to()));
			}
		}
		return out;
	}
}
