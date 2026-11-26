package life.shizune.mistinn.gear;

import java.util.List;
import life.shizune.mistinn.grid.GridShape;
import life.shizune.mistinn.tier.Tier;

/**
 * Numbers for the hunter's gear in each tier. Higher tiers last longer and hold more, and are
 * heavier: body armour and the larger backpacks slow the hunter down, as in the extraction
 * shooters this inventory takes after.
 */
public final class Gear {
	private Gear() {
	}

	/**
	 * 產線: who made a piece. Every tier has its standard issue; beside it a light line (less to stop
	 * a blow or hold things, but quiet, unburdened, easier on the breath) and a heavy line (more of
	 * both, slower, louder, and hard on the breath).
	 */
	public enum Line {
		STANDARD(""),
		LIGHT("light"),
		HEAVY("heavy");

		public final String id;

		Line(final String id) {
			this.id = id;
		}

		/** The item name of this line's piece of a kind in a tier: {@code helmet_3}, {@code helmet_light_3}. */
		public String item(final String kind, final Tier tier) {
			return this == STANDARD ? kind + "_" + tier.id() : kind + "_" + this.id + "_" + tier.id();
		}
	}

	/**
	 * 負擔: what a piece costs its wearer beyond its weight, each a fraction: {@code noise} makes every
	 * noise the hunter makes carry that much further (negative: quieter), {@code stamina} is how much
	 * faster stamina comes back (negative: slower), {@code swing} how much slower blows come (worn on
	 * the head or body only).
	 */
	public record Burden(float noise, float stamina, float swing) {
		public static final Burden NONE = new Burden(0.0F, 0.0F, 0.0F);
	}

	/** A helmet's numbers; {@code face} is a visor's armour over the face, on top of a mask's. */
	public record Helmet(Tier tier, Line line, int durability, int armor, float toughness, int face, double speedPenalty, Burden burden) {
	}

	/** Body armour's numbers; {@code speedPenalty} is a fraction: 0.02 is 2% slower. */
	public record BodyArmor(Tier tier, Line line, int durability, int armor, float toughness, float knockbackResistance, double speedPenalty,
		Burden burden) {
	}

	/** {@code speedPenalty} is a fraction: 0.02 is 2% slower. */
	public record Backpack(Tier tier, Line line, GridShape shape, int itemWidth, int itemHeight, double speedPenalty, Burden burden) {
	}

	/** 胸掛: quick-reach pouches worn on the chest; filled before the backpack. A heavy rig weighs on the hunter too. */
	public record ChestRig(Tier tier, Line line, GridShape shape, int itemWidth, int itemHeight, double speedPenalty, Burden burden) {
	}

	/** 面罩: worn under the helmet, the only protection the face has. */
	public record Mask(Tier tier, int durability, int armor) {
	}

	/** Leggings and boots both cover the legs. {@code speedPenalty} as for body armour. */
	public record LegGear(Tier tier, int durability, int armor, float toughness, double speedPenalty) {
	}

	public static final List<Helmet> HELMETS = List.of(
		new Helmet(Tier.N, Line.STANDARD, 70, 1, 0.0F, 0, 0.0, Burden.NONE),
		new Helmet(Tier.I, Line.STANDARD, 130, 1, 0.0F, 0, 0.0, Burden.NONE),
		new Helmet(Tier.II, Line.STANDARD, 190, 2, 0.0F, 0, 0.0, Burden.NONE),
		new Helmet(Tier.III, Line.STANDARD, 270, 2, 1.0F, 0, 0.0, Burden.NONE),
		new Helmet(Tier.IV, Line.STANDARD, 360, 3, 1.5F, 0, 0.0, Burden.NONE),
		new Helmet(Tier.V, Line.STANDARD, 480, 3, 2.0F, 0, 0.0, Burden.NONE),
		new Helmet(Tier.VI, Line.STANDARD, 640, 4, 3.0F, 0, 0.0, Burden.NONE),
		new Helmet(Tier.N, Line.LIGHT, 49, 1, 0.0F, 0, 0.0, new Burden(-0.05F, 0.04F, 0.0F)),
		new Helmet(Tier.I, Line.LIGHT, 91, 1, 0.0F, 0, 0.0, new Burden(-0.05F, 0.04F, 0.0F)),
		new Helmet(Tier.II, Line.LIGHT, 133, 1, 0.0F, 0, 0.0, new Burden(-0.05F, 0.04F, 0.0F)),
		new Helmet(Tier.III, Line.LIGHT, 189, 1, 0.5F, 0, 0.0, new Burden(-0.05F, 0.04F, 0.0F)),
		new Helmet(Tier.IV, Line.LIGHT, 252, 2, 0.75F, 0, 0.0, new Burden(-0.05F, 0.04F, 0.0F)),
		new Helmet(Tier.V, Line.LIGHT, 336, 2, 1.0F, 0, 0.0, new Burden(-0.05F, 0.04F, 0.0F)),
		new Helmet(Tier.VI, Line.LIGHT, 448, 3, 1.5F, 0, 0.0, new Burden(-0.05F, 0.04F, 0.0F)),
		new Helmet(Tier.N, Line.HEAVY, 98, 2, 1.0F, 0, 0.01, new Burden(0.05F, -0.04F, 0.03F)),
		new Helmet(Tier.I, Line.HEAVY, 182, 2, 1.0F, 0, 0.01, new Burden(0.05F, -0.04F, 0.03F)),
		new Helmet(Tier.II, Line.HEAVY, 266, 3, 1.0F, 1, 0.01, new Burden(0.05F, -0.04F, 0.03F)),
		new Helmet(Tier.III, Line.HEAVY, 378, 3, 2.0F, 1, 0.01, new Burden(0.05F, -0.04F, 0.03F)),
		new Helmet(Tier.IV, Line.HEAVY, 504, 4, 2.5F, 1, 0.01, new Burden(0.05F, -0.04F, 0.03F)),
		new Helmet(Tier.V, Line.HEAVY, 672, 4, 3.0F, 1, 0.01, new Burden(0.05F, -0.04F, 0.03F)),
		new Helmet(Tier.VI, Line.HEAVY, 896, 5, 4.0F, 1, 0.01, new Burden(0.05F, -0.04F, 0.03F))
	);

	public static final List<BodyArmor> ARMORS = List.of(
		new BodyArmor(Tier.N, Line.STANDARD, 110, 2, 0.0F, 0.0F, 0.0, Burden.NONE),
		new BodyArmor(Tier.I, Line.STANDARD, 200, 3, 0.0F, 0.0F, 0.01, Burden.NONE),
		new BodyArmor(Tier.II, Line.STANDARD, 290, 4, 0.0F, 0.0F, 0.02, Burden.NONE),
		new BodyArmor(Tier.III, Line.STANDARD, 400, 5, 1.0F, 0.0F, 0.03, Burden.NONE),
		new BodyArmor(Tier.IV, Line.STANDARD, 540, 6, 2.0F, 0.05F, 0.04, Burden.NONE),
		new BodyArmor(Tier.V, Line.STANDARD, 720, 7, 2.5F, 0.1F, 0.05, Burden.NONE),
		new BodyArmor(Tier.VI, Line.STANDARD, 940, 8, 3.0F, 0.15F, 0.06, Burden.NONE),
		new BodyArmor(Tier.N, Line.LIGHT, 82, 1, 0.0F, 0.0F, 0.0, new Burden(-0.1F, 0.08F, 0.0F)),
		new BodyArmor(Tier.I, Line.LIGHT, 150, 1, 0.0F, 0.0F, 0.0, new Burden(-0.1F, 0.08F, 0.0F)),
		new BodyArmor(Tier.II, Line.LIGHT, 218, 2, 0.0F, 0.0F, 0.0, new Burden(-0.1F, 0.08F, 0.0F)),
		new BodyArmor(Tier.III, Line.LIGHT, 300, 3, 0.5F, 0.0F, 0.0, new Burden(-0.1F, 0.08F, 0.0F)),
		new BodyArmor(Tier.IV, Line.LIGHT, 405, 4, 1.0F, 0.0F, 0.0, new Burden(-0.1F, 0.08F, 0.0F)),
		new BodyArmor(Tier.V, Line.LIGHT, 540, 5, 1.25F, 0.0F, 0.0, new Burden(-0.1F, 0.08F, 0.0F)),
		new BodyArmor(Tier.VI, Line.LIGHT, 705, 6, 1.5F, 0.0F, 0.0, new Burden(-0.1F, 0.08F, 0.0F)),
		new BodyArmor(Tier.N, Line.HEAVY, 154, 4, 1.5F, 0.1F, 0.03, new Burden(0.15F, -0.12F, 0.06F)),
		new BodyArmor(Tier.I, Line.HEAVY, 280, 5, 1.5F, 0.1F, 0.04, new Burden(0.15F, -0.12F, 0.06F)),
		new BodyArmor(Tier.II, Line.HEAVY, 406, 6, 1.5F, 0.1F, 0.05, new Burden(0.15F, -0.12F, 0.06F)),
		new BodyArmor(Tier.III, Line.HEAVY, 560, 7, 2.5F, 0.1F, 0.06, new Burden(0.15F, -0.12F, 0.06F)),
		new BodyArmor(Tier.IV, Line.HEAVY, 756, 8, 3.5F, 0.15F, 0.07, new Burden(0.15F, -0.12F, 0.06F)),
		new BodyArmor(Tier.V, Line.HEAVY, 1008, 9, 4.0F, 0.2F, 0.08, new Burden(0.15F, -0.12F, 0.06F)),
		new BodyArmor(Tier.VI, Line.HEAVY, 1316, 10, 4.5F, 0.25F, 0.09, new Burden(0.15F, -0.12F, 0.06F))
	);

	/**
	 * Capacity grows with the tier. From II on a pack has small top pockets over its main
	 * compartment, laid out like the extraction-shooter packs the grid is modelled on. After the
	 * standard issue come the light line's packs (about three fifths as much, no weight) and the
	 * heavy line's (about half as much again, wide rather than tall, and heavy), N to VI each.
	 */
	public static final List<Backpack> BACKPACKS = List.of(
		new Backpack(Tier.N, Line.STANDARD, GridShape.of(3, 2), 2, 2, 0.0, Burden.NONE),
		new Backpack(Tier.I, Line.STANDARD, GridShape.of(4, 3), 2, 2, 0.0, Burden.NONE),
		new Backpack(Tier.II, Line.STANDARD, GridShape.of(1, 2, 1, 2, 3, 4), 2, 3, 0.01, Burden.NONE),
		new Backpack(Tier.III, Line.STANDARD, GridShape.of(1, 2, 2, 2, 1, 2, 4, 4), 3, 3, 0.01, Burden.NONE),
		new Backpack(Tier.IV, Line.STANDARD, GridShape.of(1, 2, 2, 2, 1, 2, 4, 5), 3, 3, 0.01, Burden.NONE),
		new Backpack(Tier.V, Line.STANDARD, GridShape.of(1, 2, 3, 2, 1, 2, 5, 5), 3, 4, 0.02, Burden.NONE),
		new Backpack(Tier.VI, Line.STANDARD, GridShape.of(1, 2, 3, 2, 1, 2, 5, 6), 3, 4, 0.02, Burden.NONE),
		new Backpack(Tier.N, Line.LIGHT, GridShape.of(2, 2), 1, 2, 0.0, new Burden(-0.05F, 0.03F, 0.0F)),
		new Backpack(Tier.I, Line.LIGHT, GridShape.of(2, 3, 1, 2), 2, 2, 0.0, new Burden(-0.05F, 0.03F, 0.0F)),
		new Backpack(Tier.II, Line.LIGHT, GridShape.of(1, 1, 1, 1, 3, 3), 2, 2, 0.0, new Burden(-0.05F, 0.03F, 0.0F)),
		new Backpack(Tier.III, Line.LIGHT, GridShape.of(1, 2, 1, 2, 3, 4), 2, 3, 0.0, new Burden(-0.05F, 0.03F, 0.0F)),
		new Backpack(Tier.IV, Line.LIGHT, GridShape.of(1, 1, 1, 1, 1, 1, 4, 4), 2, 3, 0.0, new Burden(-0.05F, 0.03F, 0.0F)),
		new Backpack(Tier.V, Line.LIGHT, GridShape.of(2, 2, 2, 2, 4, 4), 3, 3, 0.0, new Burden(-0.05F, 0.03F, 0.0F)),
		new Backpack(Tier.VI, Line.LIGHT, GridShape.of(1, 3, 1, 3, 4, 5), 3, 3, 0.0, new Burden(-0.05F, 0.03F, 0.0F)),
		new Backpack(Tier.N, Line.HEAVY, GridShape.of(1, 2, 3, 3), 3, 3, 0.01, new Burden(0.08F, -0.08F, 0.0F)),
		new Backpack(Tier.I, Line.HEAVY, GridShape.of(5, 3, 1, 2), 3, 3, 0.02, new Burden(0.08F, -0.08F, 0.0F)),
		new Backpack(Tier.II, Line.HEAVY, GridShape.of(1, 2, 1, 2, 1, 2, 5, 4), 3, 3, 0.02, new Burden(0.08F, -0.08F, 0.0F)),
		new Backpack(Tier.III, Line.HEAVY, GridShape.of(2, 2, 2, 2, 2, 2, 6, 4), 3, 4, 0.03, new Burden(0.08F, -0.08F, 0.0F)),
		new Backpack(Tier.IV, Line.HEAVY, GridShape.of(1, 3, 1, 3, 2, 3, 6, 5), 3, 4, 0.03, new Burden(0.08F, -0.08F, 0.0F)),
		new Backpack(Tier.V, Line.HEAVY, GridShape.of(2, 3, 2, 3, 2, 3, 7, 5), 4, 4, 0.04, new Burden(0.08F, -0.08F, 0.0F)),
		new Backpack(Tier.VI, Line.HEAVY, GridShape.of(1, 4, 1, 4, 8, 6), 4, 4, 0.05, new Burden(0.08F, -0.08F, 0.0F))
	);

	/** Pouches by tier: two narrow ones at N, square pouches over long magazine pouches above; then the heavy line's vests, N to VI. */
	public static final List<ChestRig> RIGS = List.of(
		new ChestRig(Tier.N, Line.STANDARD, GridShape.of(1, 2, 1, 2), 2, 2, 0.0, Burden.NONE),
		new ChestRig(Tier.I, Line.STANDARD, GridShape.of(1, 2, 1, 2, 1, 2, 1, 2), 2, 2, 0.0, Burden.NONE),
		new ChestRig(Tier.II, Line.STANDARD, GridShape.of(2, 2, 1, 2, 1, 2, 1, 2, 1, 2), 2, 3, 0.0, Burden.NONE),
		new ChestRig(Tier.III, Line.STANDARD, GridShape.of(2, 2, 2, 2, 1, 2, 1, 2, 1, 2, 1, 2), 2, 3, 0.0, Burden.NONE),
		new ChestRig(Tier.IV, Line.STANDARD, GridShape.of(2, 2, 2, 2, 1, 3, 1, 3, 1, 3, 1, 3), 2, 3, 0.0, Burden.NONE),
		new ChestRig(Tier.V, Line.STANDARD, GridShape.of(2, 2, 2, 2, 2, 2, 1, 3, 1, 3, 1, 3, 1, 3), 2, 3, 0.0, Burden.NONE),
		new ChestRig(Tier.VI, Line.STANDARD, GridShape.of(2, 3, 2, 3, 2, 2, 1, 3, 1, 3, 1, 3, 1, 3), 2, 3, 0.0, Burden.NONE),
		new ChestRig(Tier.N, Line.HEAVY, GridShape.of(1, 2, 1, 2, 1, 2), 2, 2, 0.005, new Burden(0.05F, -0.03F, 0.0F)),
		new ChestRig(Tier.I, Line.HEAVY, GridShape.of(2, 2, 1, 2, 1, 2, 1, 2, 1, 2), 2, 3, 0.005, new Burden(0.05F, -0.03F, 0.0F)),
		new ChestRig(Tier.II, Line.HEAVY, GridShape.of(2, 2, 2, 2, 1, 2, 1, 2, 1, 2, 1, 2), 2, 3, 0.01, new Burden(0.05F, -0.03F, 0.0F)),
		new ChestRig(Tier.III, Line.HEAVY, GridShape.of(2, 2, 2, 2, 1, 3, 1, 3, 1, 3, 1, 3), 2, 3, 0.01, new Burden(0.05F, -0.03F, 0.0F)),
		new ChestRig(Tier.IV, Line.HEAVY, GridShape.of(2, 2, 2, 2, 2, 2, 1, 3, 1, 3, 1, 3, 1, 3), 3, 3, 0.015, new Burden(0.05F, -0.03F, 0.0F)),
		new ChestRig(Tier.V, Line.HEAVY, GridShape.of(2, 3, 2, 3, 2, 2, 2, 2, 1, 3, 1, 3, 1, 3, 1, 3), 3, 3, 0.015, new Burden(0.05F, -0.03F, 0.0F)),
		new ChestRig(Tier.VI, Line.HEAVY, GridShape.of(2, 3, 2, 3, 2, 3, 2, 2, 1, 3, 1, 3, 1, 3, 1, 3), 3, 3, 0.02, new Burden(0.05F, -0.03F, 0.0F))
	);

	public static final List<Mask> MASKS = List.of(
		new Mask(Tier.N, 50, 1),
		new Mask(Tier.I, 90, 1),
		new Mask(Tier.II, 140, 2),
		new Mask(Tier.III, 200, 2),
		new Mask(Tier.IV, 270, 3),
		new Mask(Tier.V, 360, 3),
		new Mask(Tier.VI, 480, 4)
	);

	/** N to V only: there are no tier VI leggings. */
	public static final List<LegGear> LEGGINGS = List.of(
		new LegGear(Tier.N, 90, 1, 0.0F, 0.0),
		new LegGear(Tier.I, 170, 2, 0.0F, 0.0),
		new LegGear(Tier.II, 250, 3, 0.0F, 0.005),
		new LegGear(Tier.III, 340, 4, 1.0F, 0.01),
		new LegGear(Tier.IV, 460, 5, 1.5F, 0.015),
		new LegGear(Tier.V, 610, 6, 2.0F, 0.02)
	);

	/** N to V only: there are no tier VI boots. */
	public static final List<LegGear> BOOTS = List.of(
		new LegGear(Tier.N, 60, 1, 0.0F, 0.0),
		new LegGear(Tier.I, 120, 1, 0.0F, 0.0),
		new LegGear(Tier.II, 180, 2, 0.0F, 0.0),
		new LegGear(Tier.III, 250, 2, 0.5F, 0.0),
		new LegGear(Tier.IV, 330, 3, 1.0F, 0.005),
		new LegGear(Tier.V, 430, 3, 1.5F, 0.01)
	);
}
