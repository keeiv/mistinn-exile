package life.shizune.mistinn.training;

import life.shizune.mistinn.world.Builder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The rusted anchor Rust Anchor Station is named for: about sixty blocks of copper standing
 * slanted in the basin, its crown half sunk, its stock across the top, and a chain of giant links
 * climbing from its ring into the clouds, as if it held something up there. Built in the plane
 * x = 3..8, so it reads as a silhouette from the decks to the east and west.
 */
final class GreatAnchor {
	/** The shaft runs from the crown at (z −37, y 58) to the eye at (z −20, y 122). */
	private static final int CROWN_Y = -6;
	private static final int EYE_Y = 58;
	private static final double CROWN_Z = -37.0;
	private static final double LEAN = 17.0 / 64.0;
	private static final int X0 = 3;
	private static final int X1 = 8;
	private static final Block HULL = StationParts.HULL;
	private static final Block EDGE = StationParts.FRAME;
	private static final Block WORN = StationParts.RUST;

	private GreatAnchor() {
	}

	/** Where the middle of the shaft is at this height. */
	static double shaftZ(final double y) {
		return CROWN_Z + (y - CROWN_Y) * LEAN;
	}

	static void build(final Builder b) {
		shaft(b);
		arm(b, -37.0, CROWN_Y, -54.0, -10.0, -61.0, 6.0);
		arm(b, -37.0, CROWN_Y, -22.0, -12.0, -15.0, 12.0);
		stock(b);
		ring(b);
		chain(b);
	}

	private static void shaft(final Builder b) {
		for (int y = CROWN_Y; y <= EYE_Y; y++) {
			int zc = (int) Math.round(shaftZ(y));
			for (int x = X0; x <= X1; x++) {
				for (int z = zc - 3; z <= zc + 2; z++) {
					boolean cornerX = x == X0 || x == X1;
					boolean cornerZ = z == zc - 3 || z == zc + 2;
					Block block = HULL;
					if (cornerX && cornerZ) {
						block = EDGE;
					} else if (y % 9 == 0 && (cornerX || cornerZ)) {
						// a band where the shaft was riveted together
						block = WORN;
					} else if ((cornerX || cornerZ) && Math.floorMod(x * 7 + z * 3 + y * 5, 23) == 0) {
						block = WORN;
					}
					b.set(x, y, z, block);
				}
			}
		}
	}

	/**
	 * One arm of the crown: a quadratic curve from the foot of the shaft to the fluke's bill,
	 * four thick, widening into a palm near its end.
	 */
	private static void arm(final Builder b, final double z0, final double y0, final double zc, final double yc, final double z1, final double y1) {
		int steps = 90;
		for (int i = 0; i <= steps; i++) {
			double t = i / (double) steps;
			double u = 1.0 - t;
			double z = u * u * z0 + 2 * u * t * zc + t * t * z1;
			double y = u * u * y0 + 2 * u * t * yc + t * t * y1;
			double half = 1.6;
			int xHalf = 2;
			if (t > 0.66) {
				// the palm: wide across x, thin through the curve
				double p = (t - 0.66) / 0.34;
				xHalf = (int) Math.round(2 + 4 * Math.sin(Math.PI * Math.min(1.0, p * 1.25)));
				half = 1.1;
			}
			for (int x = 6 - xHalf; x <= 5 + xHalf; x++) {
				for (int dy = -2; dy <= 2; dy++) {
					for (int dz = -2; dz <= 2; dz++) {
						if (dy * dy + dz * dz > half * half + 0.5) {
							continue;
						}
						boolean bill = t > 0.94;
						boolean rim = x == 6 - xHalf || x == 5 + xHalf;
						b.set(x, (int) Math.round(y) + dy, (int) Math.round(z) + dz, bill ? EDGE : rim && i % 5 == 0 ? WORN : HULL);
					}
				}
			}
		}
	}

	/** The stock across the top, at right angles to the shaft: dark wood bound in copper, a knob at each end. */
	private static void stock(final Builder b) {
		double cy = 50.0;
		double cz = shaftZ(cy);
		double len = Math.hypot(64.0, 17.0);
		double uz = 64.0 / len;
		double uy = -17.0 / len;
		for (double s = -15.0; s <= 15.0; s += 0.5) {
			int z = (int) Math.round(cz + s * uz);
			int y = (int) Math.round(cy + s * uy);
			boolean band = Math.abs(Math.round(s)) % 5 == 0;
			for (int x = 4; x <= 7; x++) {
				for (int d = -1; d <= 1; d++) {
					b.set(x, y + d, z, band ? EDGE : StationParts.POST);
				}
			}
		}
		for (int end : new int[] {-1, 1}) {
			int z = (int) Math.round(cz + end * 16.0 * uz);
			int y = (int) Math.round(cy + end * 16.0 * uy);
			b.fill(3, y - 2, z - 1, 8, y + 2, z + 1, StationParts.BEAM);
			b.fill(4, y - 1, z - 2, 7, y + 1, z + 2, StationParts.BEAM);
		}
	}

	/** The ring at the eye, standing in the same plane as the arms. */
	private static void ring(final Builder b) {
		double cz = shaftZ(EYE_Y) + 1.0;
		double cy = EYE_Y + 5.0;
		for (int a = 0; a < 72; a++) {
			double angle = Math.PI * 2.0 * a / 72.0;
			for (double r = 4.0; r <= 5.0; r += 0.5) {
				int z = (int) Math.round(cz + r * Math.cos(angle));
				int y = (int) Math.round(cy + r * Math.sin(angle));
				b.set(5, y, z, EDGE);
				b.set(6, y, z, EDGE);
			}
		}
	}

	/** Seven giant links from the ring up into the clouds, broad and edge-on in turn. */
	private static void chain(final Builder b) {
		for (int i = 0; i < 7; i++) {
			double cy = EYE_Y + 13.0 + 4.0 * i;
			double cz = shaftZ(EYE_Y) + 2.0 + 1.7 * i;
			link(b, cy, cz, i % 2 == 0);
		}
	}

	/**
	 * One link: a stadium four long and four across, its outline a block and a half thick, standing
	 * broad to the east and west ({@code broad}) or turned edge-on.
	 */
	private static void link(final Builder b, final double cy, final double cz, final boolean broad) {
		for (int dy = -6; dy <= 6; dy++) {
			for (int dw = -4; dw <= 4; dw++) {
				double along = Math.max(0.0, Math.abs(dy) - 2.2);
				double d = Math.hypot(along, dw);
				if (d < 1.8 || d > 3.3) {
					continue;
				}
				int y = (int) Math.round(cy) + dy;
				// the build height ends at y 159: the last links run on into the clouds
				if (y > 95) {
					continue;
				}
				Block block = Math.floorMod(dy + dw, 7) == 0 ? WORN : broad ? HULL : EDGE;
				if (broad) {
					b.set(5, y, (int) Math.round(cz) + dw, block);
					b.set(6, y, (int) Math.round(cz) + dw, block);
				} else {
					b.set(6 + dw, y, (int) Math.round(cz), block);
					b.set(6 + dw, y, (int) Math.round(cz) + 1, block);
				}
			}
		}
	}
}
