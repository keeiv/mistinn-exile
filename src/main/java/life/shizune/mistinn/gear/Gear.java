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

	public record Helmet(Tier tier, int durability, int armor, float toughness) {
	}

	public record BodyArmor(Tier tier, int durability, int armor, float toughness, float knockbackResistance, double speedPenalty) {
	}

	/** {@code speedPenalty} is a fraction: 0.02 is 2% slower. */
	public record Backpack(Tier tier, GridShape shape, int itemWidth, int itemHeight, double speedPenalty) {
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

	/** Capacity grows with the tier; the larger packs are split into compartments. */
	public static final List<Backpack> BACKPACKS = List.of(
		new Backpack(Tier.N, GridShape.of(3, 2), 2, 2, 0.0),
		new Backpack(Tier.I, GridShape.of(4, 3), 2, 2, 0.0),
		new Backpack(Tier.II, GridShape.of(3, 5), 2, 3, 0.01),
		new Backpack(Tier.III, GridShape.of(2, 2, 2, 2, 4, 3), 3, 3, 0.01),
		new Backpack(Tier.IV, GridShape.of(4, 6), 3, 3, 0.01),
		new Backpack(Tier.V, GridShape.of(2, 3, 2, 3, 4, 5), 3, 4, 0.02),
		new Backpack(Tier.VI, GridShape.of(2, 4, 2, 4, 5, 6), 3, 4, 0.02)
	);
}
