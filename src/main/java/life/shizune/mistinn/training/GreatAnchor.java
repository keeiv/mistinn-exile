package life.shizune.mistinn.training;

import life.shizune.mistinn.world.Builder;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * The rusted anchor Rust Anchor Station is named for: an iconic anchor shape, shank, crown arc,
 * two barbed flukes, a dark wooden stock and a ring with sky through it, eighty blocks from crown
 * to ring, standing in the basin with a lean of about 15° to the south. Its chain climbs out of the
 * ring into the clouds. The shape is drawn in its own frame (u up the shank, v across it, toward
 * the south) and extruded along x, so it reads whole from the decks to the east and west.
 */
final class GreatAnchor {
	private static final double L = Math.hypot(64.0, 17.0);
	private static final Block BODY = StationParts.HULL;
	private static final Block EDGE = StationParts.FRAME;
	private static final Block WORN = StationParts.RUST;
	private static final Block STOCK = Blocks.STRIPPED_DARK_OAK_LOG;

	/** One cell of the anchor: its block and how deep it runs in x. */
	private record Cell(@Nullable Block block, int x0, int x1, boolean log) {
	}

	private enum Part { RING, SHOE, STOCK, FLUKE, CROWN, SHANK }

	private static final int[][] FLUKE = {{17, 12}, {19, 12}, {25, 10}, {27, 5}, {22, 4}, {19, 5}, {19, 8}, {17, 10}};
	private static final int[][] CROWN = {{0, -3}, {5, -3}, {10, -2}, {15, 0}, {19, 3}, {22, 6}, {23, 9}, {20, 11}, {17, 7}, {14, 5}, {10, 3}, {5, 2}, {0, 2}};

	private GreatAnchor() {
	}

	static void build(final Builder b) {
		for (int y = 54; y <= 159; y++) {
			for (int z = -64; z <= 0; z++) {
				int dy = y - 58;
				int dz = z + 37;
				int u = round((64.0 * dy + 17.0 * dz) / L);
				int v = round((64.0 * dz - 17.0 * dy) / L);
				Cell cell = cell(u, v);
				if (cell != null) {
					for (int x = cell.x0(); x <= cell.x1(); x++) {
						if (cell.log()) {
							b.axis(x, y - 64, z, STOCK, Direction.Axis.Z);
						} else {
							b.set(x, y - 64, z, cell.block());
						}
					}
				}
				chain(b, y, z, u, v);
			}
		}
	}

	private static int round(final double value) {
		return (int) Math.floor(value + 0.5);
	}

	/** Which part of the profile a local cell belongs to, the first that matches. */
	private static @Nullable Part part(final int u, final int v) {
		int w = Math.abs(v);
		int ring = v * v + (u - 70) * (u - 70);
		if (ring >= 19 && ring <= 56) {
			return Part.RING;
		}
		if (w >= 16 && w <= 18 && u >= 53 && u <= 57) {
			return Part.SHOE;
		}
		if (w <= 18 && u >= 54 && u <= 56) {
			return Part.STOCK;
		}
		double sFluke = v < 0 ? u : u - 6;
		if (inside(FLUKE, w, sFluke)) {
			return Part.FLUKE;
		}
		double sCrown = v < 0 ? u : u - 0.3 * Math.max(0, w - 10);
		if (inside(CROWN, w, sCrown)) {
			return Part.CROWN;
		}
		if (u >= 0 && u <= 64 && (w <= 2 || (w <= 3 && (u <= 18 || u >= 61)))) {
			return Part.SHANK;
		}
		return null;
	}

	private static @Nullable Cell cell(final int u, final int v) {
		Part part = part(u, v);
		if (part == null) {
			return null;
		}
		int w = Math.abs(v);
		return switch (part) {
			case RING -> new Cell(v * v + (u - 70) * (u - 70) >= 43 ? EDGE : BODY, 4, 7, false);
			case SHOE -> new Cell(w == 17 && u == 55 ? WORN : EDGE, 3, 8, false);
			case STOCK -> w == 8 || w <= 3 ? new Cell(EDGE, 4, 7, false) : new Cell(STOCK, 4, 7, true);
			case FLUKE -> {
				double s = v < 0 ? u : u - 6;
				boolean bill = w >= 18 && w <= 20 && s >= 11 && s <= 12;
				boolean tip = w <= 20 && s >= 11;
				yield new Cell(bill ? WORN : edge(u, v) ? EDGE : BODY, tip ? 4 : 3, tip ? 7 : 8, false);
			}
			case CROWN -> new Cell(edge(u, v) ? EDGE : BODY, 2, 9, false);
			case SHANK -> {
				boolean scuff = (v == -2 && u >= 36 && u <= 38) || (v == 2 && u >= 21 && u <= 22);
				yield new Cell(scuff ? WORN : edge(u, v) ? EDGE : BODY, 3, 8, false);
			}
		};
	}

	/** A cell with open sky beside it in the profile wears weathered copper. */
	private static boolean edge(final int u, final int v) {
		return part(u + 1, v) == null || part(u - 1, v) == null || part(u, v + 1) == null || part(u, v - 1) == null;
	}

	/** Whether (w, s) lies inside or on the polygon. */
	private static boolean inside(final int[][] polygon, final double w, final double s) {
		boolean in = false;
		for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
			double wi = polygon[i][0];
			double si = polygon[i][1];
			double wj = polygon[j][0];
			double sj = polygon[j][1];
			// on an edge counts as inside
			double cross = (w - wi) * (sj - si) - (s - si) * (wj - wi);
			if (Math.abs(cross) < 1e-9 && w >= Math.min(wi, wj) - 1e-9 && w <= Math.max(wi, wj) + 1e-9
				&& s >= Math.min(si, sj) - 1e-9 && s <= Math.max(si, sj) + 1e-9) {
				return true;
			}
			if ((si > s) != (sj > s) && w < (wj - wi) * (s - si) / (sj - si) + wi) {
				in = !in;
			}
		}
		return in;
	}

	/** Four links seated in the ring and running on into the fog, edge-on and broadside in turn. */
	private static void chain(final Builder b, final int y, final int z, final int u, final int v) {
		for (int i = 0; i < 4; i++) {
			int centre = 81 + 7 * i;
			double along = Math.max(0, Math.abs(u - centre) - 2);
			if (i % 2 == 0) {
				if (Math.abs(v) > 1) {
					continue;
				}
				for (int x = 0; x <= 11; x++) {
					double w = x - 5.5;
					double d = along * along + w * w;
					if (d >= 5 && d <= 18) {
						b.set(x, y - 64, z, EDGE);
					}
				}
			} else {
				double d = along * along + v * v;
				if (d >= 5 && d <= 18) {
					b.set(5, y - 64, z, BODY);
					b.set(6, y - 64, z, BODY);
				}
			}
		}
	}
}
