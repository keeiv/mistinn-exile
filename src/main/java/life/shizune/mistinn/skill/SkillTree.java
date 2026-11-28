package life.shizune.mistinn.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The skill tree. Major nodes are unlocked with a rust god salt, so the first one, the slide,
 * waits until the hunter has beaten a lighthouse keeper. Below it, each stat has a branch of
 * minor nodes bought with salt, one after another.
 */
public final class SkillTree {
	/** The first node: the slide. Everything else hangs from it. */
	public static final String ROOT = "slide";
	/** Minor nodes in each stat's branch. */
	public static final int BRANCH = 5;
	/** The major nodes closing rows: 千鈞 (strength), 初心本我 (speed), 久經沙場 (constitution), 裝備大師 (intellect). */
	public static final String THOUSAND_WEIGHT = "thousand_weight";
	public static final String BATTLE_HARDENED = "battle_hardened";
	public static final String GEAR_MASTER = "gear_master";
	public static final String TRUE_SELF = "true_self";

	/** One node: a minor node adds one point to its stat; a major node adds none (the root has no stat, the others close its row). */
	public record Node(String id, boolean major, @Nullable Stat stat, @Nullable String parent) {
	}

	/** Every node, the root first, then each branch in order. */
	public static final List<Node> NODES;
	private static final Map<String, Node> BY_ID = new LinkedHashMap<>();

	static {
		List<Node> nodes = new ArrayList<>();
		nodes.add(new Node(ROOT, true, null, null));
		for (Stat stat : Stat.values()) {
			for (int i = 1; i <= BRANCH; i++) {
				nodes.add(new Node(stat.id + "_" + i, false, stat, i == 1 ? ROOT : stat.id + "_" + (i - 1)));
			}
		}
		// each closes its row, after the row's last minor node (its stat says which row; it adds no point to it)
		nodes.add(new Node(THOUSAND_WEIGHT, true, Stat.STRENGTH, Stat.STRENGTH.id + "_" + BRANCH));
		nodes.add(new Node(TRUE_SELF, true, Stat.SPEED, Stat.SPEED.id + "_" + BRANCH));
		nodes.add(new Node(BATTLE_HARDENED, true, Stat.CONSTITUTION, Stat.CONSTITUTION.id + "_" + BRANCH));
		nodes.add(new Node(GEAR_MASTER, true, Stat.INTELLECT, Stat.INTELLECT.id + "_" + BRANCH));
		NODES = List.copyOf(nodes);
		for (Node node : NODES) {
			BY_ID.put(node.id(), node);
		}
	}

	private SkillTree() {
	}

	public static @Nullable Node node(final String id) {
		return BY_ID.get(id);
	}
}
