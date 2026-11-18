package life.shizune.mistinn.tier;

/**
 * The seven tiers, lowest first. I to VI use the colours the owner set (grey, green, blue,
 * purple, gold, red); N is rust brown, for unrated scrap from the border world.
 */
public enum Tier {
	N("N", 0xFF8A6448),
	I("I", 0xFF9AA0A6),
	II("II", 0xFF4CAF50),
	III("III", 0xFF3D8BFF),
	IV("IV", 0xFFA35BE8),
	V("V", 0xFFE8B33A),
	VI("VI", 0xFFE5484D);

	public final String label;
	/** Opaque ARGB colour of the tier's diamond and name. */
	public final int color;

	Tier(final String label, final int color) {
		this.label = label;
		this.color = color;
	}

	/** RGB without alpha, for text styles. */
	public int rgb() {
		return this.color & 0xFFFFFF;
	}

	/** The tier's colour at the given alpha (0-255), for slot backgrounds. */
	public int withAlpha(final int alpha) {
		return (alpha << 24) | this.rgb();
	}

	/** Lower-case id used in item names: n, 1 ... 6. */
	public String id() {
		return this == N ? "n" : Integer.toString(this.ordinal());
	}
}
