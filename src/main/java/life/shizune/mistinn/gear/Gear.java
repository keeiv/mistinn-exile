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

	/** A helmet's numbers, by tier. */
	public record Helmet(Tier tier, int durability, int armor, float toughness) {
	}

	/** Body armour's numbers, by tier; {@code speedPenalty} is a fraction: 0.02 is 2% slower. */
	public record BodyArmor(Tier tier, int durability, int armor, float toughness, float knockbackResistance, double speedPenalty) {
	}

	/** {@code speedPenalty} is a fraction: 0.02 is 2% slower. */
	public record Backpack(Tier tier, GridShape shape, int itemWidth, int itemHeight, double speedPenalty) {
	}

	/** 胸掛: quick-reach pouches worn on the chest; filled before the backpack. */
	public record ChestRig(Tier tier, GridShape shape, int itemWidth, int itemHeight) {
	}

	/** 面罩: worn under the helmet, the only protection the face has. */
	public record Mask(Tier tier, int durability, int armor) {
	}

	/** Leggings and boots both cover the legs. {@code speedPenalty} as for body armour. */
	public record LegGear(Tier tier, int durability, int armor, float toughness, double speedPenalty) {
	}

	public static final List<Helmet> HELMETS = List.of(
		new Helmet(Tier.N, 70, 1, 0.0F),
		new Helmet(Tier.I, 130, 1, 0.0F),
		new Helmet(Tier.II, 190, 2, 0.0F),
		new Helmet(Tier.III, 270, 2, 1.0F),
		new Helmet(Tier.IV, 360, 3, 1.5F),
		new Helmet(Tier.V, 480, 3, 2.0F),
		new Helmet(Tier.VI, 640, 4, 3.0F)
	);

	public static final List<BodyArmor> ARMORS = List.of(
		new BodyArmor(Tier.N, 110, 2, 0.0F, 0.0F, 0.0),
		new BodyArmor(Tier.I, 200, 3, 0.0F, 0.0F, 0.01),
		new BodyArmor(Tier.II, 290, 4, 0.0F, 0.0F, 0.02),
		new BodyArmor(Tier.III, 400, 5, 1.0F, 0.0F, 0.03),
		new BodyArmor(Tier.IV, 540, 6, 2.0F, 0.05F, 0.04),
		new BodyArmor(Tier.V, 720, 7, 2.5F, 0.1F, 0.05),
		new BodyArmor(Tier.VI, 940, 8, 3.0F, 0.15F, 0.06)
	);

	/**
	 * Capacity grows with the tier. From II on a pack has small top pockets over its main
	 * compartment, laid out like the extraction-shooter packs the grid is modelled on.
	 */
	public static final List<Backpack> BACKPACKS = List.of(
		new Backpack(Tier.N, GridShape.of(3, 2), 2, 2, 0.0),
		new Backpack(Tier.I, GridShape.of(4, 3), 2, 2, 0.0),
		new Backpack(Tier.II, GridShape.of(1, 2, 1, 2, 3, 4), 2, 3, 0.01),
		new Backpack(Tier.III, GridShape.of(1, 2, 2, 2, 1, 2, 4, 4), 3, 3, 0.01),
		new Backpack(Tier.IV, GridShape.of(1, 2, 2, 2, 1, 2, 4, 5), 3, 3, 0.01),
		new Backpack(Tier.V, GridShape.of(1, 2, 3, 2, 1, 2, 5, 5), 3, 4, 0.02),
		new Backpack(Tier.VI, GridShape.of(1, 2, 3, 2, 1, 2, 5, 6), 3, 4, 0.02)
	);

	/** Pouches by tier: two narrow ones at N, square pouches over long magazine pouches above. */
	public static final List<ChestRig> RIGS = List.of(
		new ChestRig(Tier.N, GridShape.of(1, 2, 1, 2), 2, 2),
		new ChestRig(Tier.I, GridShape.of(1, 2, 1, 2, 1, 2, 1, 2), 2, 2),
		new ChestRig(Tier.II, GridShape.of(2, 2, 1, 2, 1, 2, 1, 2, 1, 2), 2, 3),
		new ChestRig(Tier.III, GridShape.of(2, 2, 2, 2, 1, 2, 1, 2, 1, 2, 1, 2), 2, 3),
		new ChestRig(Tier.IV, GridShape.of(2, 2, 2, 2, 1, 3, 1, 3, 1, 3, 1, 3), 2, 3),
		new ChestRig(Tier.V, GridShape.of(2, 2, 2, 2, 2, 2, 1, 3, 1, 3, 1, 3, 1, 3), 2, 3),
		new ChestRig(Tier.VI, GridShape.of(2, 3, 2, 3, 2, 2, 1, 3, 1, 3, 1, 3, 1, 3), 2, 3)
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
