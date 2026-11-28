package life.shizune.mistinn.udasru;

/**
 * The regions Udasru was drawn in, by the number the map keeps for each. Each says how closely its ground keeps to the
 * drawn height (a low factor lets the overworld's own folds through: cliffs, overhangs) and how far its peaks may
 * break upward. The biome of each is the one at its number in the biome source's list.
 */
public enum UdasruRegion {
	SEA(0, "sea", 6.0F, 0.0F),
	SHORE(1, "shore", 6.0F, 0.0F),
	MIST_RIM(2, "mist_rim", 4.5F, 0.0F),
	BELL_PLAINS(3, "bell_plains", 6.0F, 0.0F),
	CAPITAL(4, "capital", 8.0F, 0.0F),
	SALT_FLATS(5, "salt_flats", 8.0F, 0.0F),
	SALT_MARSH(6, "salt_marsh", 7.0F, 0.0F),
	OLD_WOODS(7, "old_woods", 4.5F, 0.0F),
	FROST_RIDGE(8, "frost_ridge", 2.6F, 0.55F),
	RUST_WASTES(9, "rust_wastes", 3.0F, 0.25F),
	CRYSTAL_HIGHLAND(10, "crystal_highland", 4.0F, 0.15F),
	RIVER(11, "river", 7.0F, 0.0F),
	LAKE(12, "lake", 7.0F, 0.0F),
	ANCHOR_HILLS(13, "anchor_hills", 4.0F, 0.05F),
	BELL_BAY(14, "bell_bay", 5.0F, 0.0F);

	private static final UdasruRegion[] BY_ID = new UdasruRegion[256];

	static {
		for (UdasruRegion region : values()) {
			BY_ID[region.id] = region;
		}
	}

	public final int id;
	public final String name;
	public final float factor;
	public final float jaggedness;

	UdasruRegion(final int id, final String name, final float factor, final float jaggedness) {
		this.id = id;
		this.name = name;
		this.factor = factor;
		this.jaggedness = jaggedness;
	}

	public static UdasruRegion byId(final int id) {
		UdasruRegion region = id >= 0 && id < BY_ID.length ? BY_ID[id] : null;
		return region == null ? SEA : region;
	}
}
