package life.shizune.mistinn.medical;

import life.shizune.mistinn.tier.Tier;
import net.minecraft.util.Mth;

/**
 * 維修規則: what a repair kit does to a piece of gear. Pure numbers, so the rules can be checked
 * without a world.
 *
 * <ul>
 * <li>A kit of tier III, IV or V on gear of its own tier or lower: restores {@link #NORMAL_RESTORE}
 * of the piece's current maximum, costs no maximum, takes {@link #NORMAL_TICKS}.</li>
 * <li>The same kits on gear above their tier: restore only {@link #POOR_RESTORE}, wear the piece
 * down by {@link #POOR_WEAR} of its original maximum for good, and are slow: III 15 s, IV 10 s,
 * V 7 s.</li>
 * <li>A tier VI kit mends anything fully ({@link #RED_RESTORE}) in {@link #NORMAL_TICKS}, but
 * always wears the piece down by {@link #RED_WEAR}, whatever its tier.</li>
 * <li>Wear never takes a piece below {@link #FLOOR} of its original maximum.</li>
 * </ul>
 */
public final class RepairRules {
	/** Share of the current maximum a matched repair gives back. */
	public static final float NORMAL_RESTORE = 0.5F;
	/** Share of the current maximum a repair above the kit's tier gives back. */
	public static final float POOR_RESTORE = 0.25F;
	/** Share of the original maximum a repair above the kit's tier wears away. */
	public static final float POOR_WEAR = 0.10F;
	/** A tier VI kit restores everything. */
	public static final float RED_RESTORE = 1.0F;
	/** Share of the original maximum a tier VI kit wears away, on any piece. */
	public static final float RED_WEAR = 0.05F;
	/** Wear stops here: a piece keeps at least this share of its original maximum. */
	public static final float FLOOR = 0.30F;
	/** 5 s: matched repairs and every tier VI repair. */
	public static final int NORMAL_TICKS = 100;

	/** 維修方案: how long, how much back, and the new maximum. */
	public record Plan(int ticks, int restore, int newMax, boolean poor, boolean wears) {
	}

	private RepairRules() {
	}

	/** Use time of a kit on gear above its tier: III 15 s, IV 10 s, V 7 s. */
	public static int poorTicks(final Tier kit) {
		return switch (kit) {
			case III -> 300;
			case IV -> 200;
			case V -> 140;
			default -> kit.ordinal() > Tier.V.ordinal() ? NORMAL_TICKS : 300;
		};
	}

	public static boolean poor(final Tier kit, final Tier target) {
		return kit != Tier.VI && target.ordinal() > kit.ordinal();
	}

	public static int ticks(final Tier kit, final Tier target) {
		return poor(kit, target) ? poorTicks(kit) : NORMAL_TICKS;
	}

	/**
	 * The repair of a piece with {@code baseMax} as its original maximum, {@code currentMax} now
	 * (after earlier wear). The restore is worked out on the new maximum.
	 */
	public static Plan plan(final Tier kit, final Tier target, final int baseMax, final int currentMax) {
		boolean red = kit == Tier.VI;
		boolean poor = poor(kit, target);
		float wearShare = red ? RED_WEAR : poor ? POOR_WEAR : 0.0F;
		int floor = Math.max(1, Mth.ceil(baseMax * FLOOR));
		int newMax = currentMax;
		if (wearShare > 0.0F) {
			newMax = Math.max(Math.min(floor, currentMax), currentMax - Math.max(1, Math.round(baseMax * wearShare)));
		}
		float restoreShare = red ? RED_RESTORE : poor ? POOR_RESTORE : NORMAL_RESTORE;
		int restore = Math.max(1, Math.round(newMax * restoreShare));
		return new Plan(ticks(kit, target), restore, newMax, poor, newMax < currentMax);
	}

	/** Damage after the plan: the restore taken off, kept below the new maximum. */
	public static int damageAfter(final Plan plan, final int damage) {
		return Mth.clamp(damage - plan.restore(), 0, Math.max(0, plan.newMax() - 1));
	}
}
