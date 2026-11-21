package life.shizune.mistinn.training;

import life.shizune.mistinn.world.Builder;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The pieces Rust Anchor is built from: decks on piles over the sea, rails, walkways, stairs,
 * sheds with deep eaves, lamp posts and chimneys. Heights are relative to the builder's origin
 * (y 64): the sea floor is 13 below, the water's surface 2 below.
 */
final class StationParts {
	static final int SEA_FLOOR = -13;
	static final int WATERLINE = -2;
	static final Block HULL = Blocks.COPPER_BLOCK.waxed().oxidized();
	static final Block FRAME = Blocks.CUT_COPPER.waxed().weathered();
	static final Block RUST = Blocks.CUT_COPPER.waxed().exposed();
	static final Block GRATE = Blocks.COPPER_GRATE.waxed().oxidized();
	static final Block RAIL = Blocks.COPPER_BARS.waxed().oxidized();
	static final Block BULB = Blocks.COPPER_BULB.waxed().exposed();
	static final Block STONE = Blocks.DEEPSLATE_BRICKS;
	static final Block BEAM = Blocks.POLISHED_DEEPSLATE;
	static final Block POST = Blocks.STRIPPED_DARK_OAK_LOG;
	static final Block PLANK = Blocks.DARK_OAK_PLANKS;
	static final Block BOARD = Blocks.SPRUCE_PLANKS;
	static final Block TEAL_STAIRS = Blocks.CUT_COPPER_STAIRS.waxed().oxidized();
	static final Block TEAL_SLAB = Blocks.CUT_COPPER_SLAB.waxed().oxidized();
	static final Block WARM_STAIRS = Blocks.DARK_OAK_STAIRS;
	static final Block WARM_SLAB = Blocks.DARK_OAK_SLAB;

	private StationParts() {
	}

	/** A pile from the sea floor up to {@code top}, with a tuff collar where the water meets it. */
	static void pile(final Builder b, final int x, final int z, final int top) {
		b.fill(x, SEA_FLOOR, z, x, top, z, BEAM);
		b.set(x, WATERLINE, z, Blocks.TUFF);
		b.set(x, WATERLINE - 1, z, Blocks.TUFF);
	}

	/**
	 * A deck: its floor at {@code y}, a copper rim, deepslate joists under it every four blocks and
	 * piles down to the sea floor every eight, rails all round unless {@code open}.
	 */
	static void deck(final Builder b, final int x0, final int z0, final int x1, final int z1, final int y, final Block floor, final boolean open) {
		b.fill(x0, y, z0, x1, y, z1, floor);
		for (int x = x0; x <= x1; x++) {
			b.set(x, y, z0, FRAME);
			b.set(x, y, z1, FRAME);
		}
		for (int z = z0; z <= z1; z++) {
			b.set(x0, y, z, FRAME);
			b.set(x1, y, z, FRAME);
		}
		for (int x = x0; x <= x1; x += 4) {
			b.fill(x, y - 1, z0, x, y - 1, z1, BEAM);
		}
		for (int z = z0; z <= z1; z += 4) {
			b.fill(x0, y - 1, z, x1, y - 1, z, BEAM);
		}
		b.fill(x1, y - 1, z0, x1, y - 1, z1, BEAM);
		b.fill(x0, y - 1, z1, x1, y - 1, z1, BEAM);
		for (int x = x0; ; x = Math.min(x + 8, x1)) {
			for (int z = z0; ; z = Math.min(z + 8, z1)) {
				pile(b, x, z, y - 2);
				if (z == z1) {
					break;
				}
			}
			if (x == x1) {
				break;
			}
		}
		if (!open) {
			railAround(b, x0, z0, x1, z1, y);
		}
	}

	/** Copper bars along the deck's edge, a weathered post every four, a lamp at each corner. */
	static void railAround(final Builder b, final int x0, final int z0, final int x1, final int z1, final int y) {
		rail(b, x0, z0, x1, z0, y, true);
		rail(b, x0, z1, x1, z1, y, true);
		rail(b, x0, z0, x0, z1, y, true);
		rail(b, x1, z0, x1, z1, y, true);
	}

	static void rail(final Builder b, final int x0, final int z0, final int x1, final int z1, final int y) {
		rail(b, x0, z0, x1, z1, y, true);
	}

	/** A straight rail along x or z; {@code lamps} puts a lantern on its two end posts. */
	static void rail(final Builder b, final int x0, final int z0, final int x1, final int z1, final int y, final boolean lamps) {
		int n = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
		int dx = Integer.signum(x1 - x0);
		int dz = Integer.signum(z1 - z0);
		for (int i = 0; i <= n; i++) {
			int x = x0 + dx * i;
			int z = z0 + dz * i;
			if (i % 4 == 0 || i == n) {
				b.set(x, y + 1, z, FRAME);
				if (lamps && (i == 0 || i == n)) {
					b.set(x, y + 2, z, Blocks.LANTERN);
				}
			} else {
				b.set(x, y + 1, z, RAIL);
			}
		}
	}

	/** Bars all round a deck with no lamps on them: the cold top of the station. */
	static void darkRailAround(final Builder b, final int x0, final int z0, final int x1, final int z1, final int y) {
		rail(b, x0, z0, x1, z0, y, false);
		rail(b, x0, z1, x1, z1, y, false);
		rail(b, x0, z0, x0, z1, y, false);
		rail(b, x1, z0, x1, z1, y, false);
	}

	/** Takes the rail out where a walkway or stair joins the deck. */
	static void gap(final Builder b, final int x0, final int z0, final int x1, final int z1, final int y) {
		b.clear(x0, y + 1, z0, x1, y + 2, z1);
	}

	/** A walkway between decks: dark planks with a grate down the middle, rails on its long sides. */
	static void walk(final Builder b, final int x0, final int z0, final int x1, final int z1, final int y) {
		b.fill(x0, y, z0, x1, y, z1, PLANK);
		b.fill(x0, y - 1, z0, x1, y - 1, z1, BEAM);
		boolean alongZ = Math.abs(z1 - z0) >= Math.abs(x1 - x0);
		if (alongZ) {
			int mid = (x0 + x1) / 2;
			b.fill(mid, y, z0, mid, y, z1, GRATE);
			rail(b, x0, z0, x0, z1, y);
			rail(b, x1, z0, x1, z1, y);
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z += 6) {
				pile(b, x0, z, y - 2);
				pile(b, x1, z, y - 2);
			}
		} else {
			int mid = (z0 + z1) / 2;
			b.fill(x0, y, mid, x1, y, mid, GRATE);
			rail(b, x0, z0, x1, z0, y);
			rail(b, x0, z1, x1, z1, y);
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x += 6) {
				pile(b, x, z0, y - 2);
				pile(b, x, z1, y - 2);
			}
		}
	}

	/**
	 * A flight of stairs climbing {@code rise} blocks in direction {@code up}, {@code width} wide,
	 * starting one step on from ({@code x}, {@code z}) at height {@code y}. Returns nothing; the
	 * top step lands at ({@code x} + rise·dx, {@code y} + rise, {@code z} + rise·dz).
	 */
	static void flight(final Builder b, final int x, final int z, final int y, final Direction up, final int width, final int rise) {
		Direction side = up.getClockWise();
		for (int i = 1; i <= rise; i++) {
			for (int w = 0; w < width; w++) {
				int sx = x + up.getStepX() * i + side.getStepX() * w;
				int sz = z + up.getStepZ() * i + side.getStepZ() * w;
				b.stairs(sx, y + i, sz, Blocks.POLISHED_DEEPSLATE_STAIRS, up, false);
				b.set(sx, y + i - 1, sz, BEAM);
				b.clear(sx, y + i + 1, sz, sx, y + i + 3, sz);
			}
			for (int edge : new int[] {-1, width}) {
				int rx = x + up.getStepX() * i + side.getStepX() * edge;
				int rz = z + up.getStepZ() * i + side.getStepZ() * edge;
				b.set(rx, y + i, rz, FRAME);
				b.set(rx, y + i + 1, rz, i % 4 == 0 ? FRAME : RAIL);
				if (i % 4 == 0) {
					pile(b, rx, rz, y + i - 1);
				}
			}
		}
	}

	/** A lit copper bulb on a short post, or a lantern on a tall one. */
	static void lampPost(final Builder b, final int x, final int z, final int y, final boolean bulb) {
		b.fill(x, y + 1, z, x, y + 3, z, FRAME);
		if (bulb) {
			b.set(x, y + 4, z, BULB.defaultBlockState().setValue(BlockStateProperties.LIT, true));
		} else {
			b.set(x, y + 4, z, Blocks.LANTERN);
		}
	}

	/** A brick chimney with a lit campfire on top: smoke a long way up into the fog. */
	static void chimney(final Builder b, final int x, final int z, final int y0, final int y1) {
		b.fill(x, y0, z, x, y1, z, Blocks.BRICKS);
		b.set(x, y1 + 1, z, Blocks.CAMPFIRE);
	}

	/**
	 * A shed: posts at the corners and every five, walls between them with warm windows, a deep
	 * stepped eave and a flat roof. {@code y} is the floor; the walls rise {@code height}.
	 */
	static void shed(final Builder b, final int x0, final int z0, final int x1, final int z1, final int y, final int height,
		final Block wall, final Block glass, final Block eaves, final Block roof) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean edgeX = x == x0 || x == x1;
				boolean edgeZ = z == z0 || z == z1;
				if (!edgeX && !edgeZ) {
					continue;
				}
				boolean post = (edgeX && edgeZ) || (edgeZ && (x - x0) % 5 == 0) || (edgeX && (z - z0) % 5 == 0);
				for (int h = 1; h <= height; h++) {
					boolean window = !post && (h == 2 || h == 3) && ((edgeZ ? x - x0 : z - z0) % 5 == 2 || (edgeZ ? x - x0 : z - z0) % 5 == 3);
					b.set(x, y + h, z, post ? POST : window ? glass : wall);
				}
			}
		}
		// a deep eave: upside-down stairs one out, then the roof one up, stepping in
		int top = y + height + 1;
		ring(b, x0 - 1, z0 - 1, x1 + 1, z1 + 1, top, eaves, true);
		b.fill(x0, top, z0, x1, top, z1, roof);
		ring(b, x0, z0, x1, z1, top + 1, eaves, false);
		b.fill(x0 + 1, top + 1, z0 + 1, x1 - 1, top + 1, z1 - 1, roof);
		// light inside, hanging from the ceiling
		for (int x = x0 + 3; x < x1 - 1; x += 5) {
			for (int z = z0 + 3; z < z1 - 1; z += 5) {
				b.hanging(x, top - 1, z, Blocks.LANTERN);
			}
		}
	}

	/**
	 * A pitched roof over a rectangle, overhanging it by one, its ridge along x ({@code alongX}) or
	 * z, the two gable ends walled in {@code gable}. {@code y} is the first course of the roof.
	 * Returns the height of the ridge.
	 */
	static int gable(final Builder b, final int x0, final int z0, final int x1, final int z1, final int y, final boolean alongX,
		final Block stairs, final Block ridge, final Block gable) {
		int lo = (alongX ? z0 : x0) - 1;
		int hi = (alongX ? z1 : x1) + 1;
		int from = (alongX ? x0 : z0) - 1;
		int to = (alongX ? x1 : z1) + 1;
		int k = 0;
		while (lo + k < hi - k) {
			int yy = y + k;
			for (int a = from; a <= to; a++) {
				if (alongX) {
					b.stairs(a, yy, lo + k, stairs, Direction.SOUTH, false);
					b.stairs(a, yy, hi - k, stairs, Direction.NORTH, false);
				} else {
					b.stairs(lo + k, yy, a, stairs, Direction.EAST, false);
					b.stairs(hi - k, yy, a, stairs, Direction.WEST, false);
				}
			}
			for (int c = lo + k + 1; c <= hi - k - 1; c++) {
				if (alongX) {
					b.set(x0, yy, c, gable);
					b.set(x1, yy, c, gable);
				} else {
					b.set(c, yy, z0, gable);
					b.set(c, yy, z1, gable);
				}
			}
			k++;
		}
		if (lo + k == hi - k) {
			for (int a = from; a <= to; a++) {
				b.set(alongX ? a : lo + k, y + k, alongX ? lo + k : a, ridge);
			}
		}
		return y + k;
	}

	/**
	 * A hall: walls, then a steep pitched roof one block out from them with a window in each gable.
	 * Returns the height of the ridge.
	 */
	static int hall(final Builder b, final int x0, final int z0, final int x1, final int z1, final int y, final int height,
		final Block wall, final Block glass, final Block stairs, final Block ridge, final boolean alongX) {
		walls(b, x0, z0, x1, z1, y, height, wall, glass, true);
		int top = gable(b, x0, z0, x1, z1, y + height + 1, alongX, stairs, ridge, wall);
		int mid = alongX ? (z0 + z1) / 2 : (x0 + x1) / 2;
		int window = y + height + 1 + Math.max(1, (top - y - height - 1) / 2);
		if (alongX) {
			b.set(x0, window, mid, glass);
			b.set(x1, window, mid, glass);
		} else {
			b.set(mid, window, z0, glass);
			b.set(mid, window, z1, glass);
		}
		return top;
	}

	/**
	 * The walls of a building without its roof: posts at the corners and every five, 2-high windows
	 * set into each bay, a plank ceiling at {@code y + height + 1} and, if {@code lit}, lanterns
	 * hung from it every five blocks.
	 */
	static void walls(final Builder b, final int x0, final int z0, final int x1, final int z1, final int y, final int height,
		final Block wall, final Block glass, final boolean lit) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean edgeX = x == x0 || x == x1;
				boolean edgeZ = z == z0 || z == z1;
				if (!edgeX && !edgeZ) {
					continue;
				}
				int along = edgeZ ? x - x0 : z - z0;
				boolean post = (edgeX && edgeZ) || along % 5 == 0;
				for (int h = 1; h <= height; h++) {
					boolean window = !post && (h == 2 || h == 3) && (along % 5 == 2 || along % 5 == 3);
					b.set(x, y + h, z, post ? POST : window ? glass : wall);
				}
			}
		}
		b.fill(x0, y + height + 1, z0, x1, y + height + 1, z1, PLANK);
		if (lit) {
			for (int x = x0 + 3; x < x1 - 1; x += 5) {
				for (int z = z0 + 3; z < z1 - 1; z += 5) {
					b.hanging(x, y + height, z, Blocks.LANTERN);
				}
			}
		}
	}

	/**
	 * A shallow pitched roof: its skin rises half a block a step from each long edge to the ridge,
	 * slabs and full blocks in turn, with a backing block under it, and the two gable ends filled
	 * in {@code gable} below it. The ridge runs along z ({@code ridgeAlongZ}) or x.
	 */
	static void shallowRoof(final Builder b, final int x0, final int z0, final int x1, final int z1, final int eave, final boolean ridgeAlongZ,
		final Block full, final Block slab, final Block gable) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int d = ridgeAlongZ ? Math.min(x - x0, x1 - x) : Math.min(z - z0, z1 - z);
				int y = eave + d / 2;
				if (d % 2 == 0) {
					b.slab(x, y, z, slab, false);
				} else {
					b.set(x, y, z, full);
				}
				if (d >= 2) {
					b.set(x, y - 1, z, full);
				}
				boolean end = ridgeAlongZ ? z == z0 + 1 || z == z1 - 1 : x == x0 + 1 || x == x1 - 1;
				if (end && d >= 1) {
					for (int g = eave; g < y - (d >= 2 ? 1 : 0); g++) {
						b.set(x, g, z, gable);
					}
				}
			}
		}
	}

	/** A hip roof: the skin rises half a block a step from every edge, slabs and full blocks in turn. */
	static void hipRoof(final Builder b, final int x0, final int z0, final int x1, final int z1, final int eave, final Block full, final Block slab) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int d = Math.min(Math.min(x - x0, x1 - x), Math.min(z - z0, z1 - z));
				int y = eave + d / 2;
				if (d % 2 == 0) {
					b.slab(x, y, z, slab, false);
				} else {
					b.set(x, y, z, full);
				}
				if (d >= 2) {
					b.fill(x, eave, z, x, y - 1, z, full);
				}
			}
		}
	}

	/** A chain strung between two posts with lanterns hanging from it, along x. */
	static void lanternString(final Builder b, final int x0, final int x1, final int z, final int y, final int[] lanterns) {
		b.fill(x0, y - 4, z, x0, y, z, POST);
		b.fill(x1, y - 4, z, x1, y, z, POST);
		for (int x = x0 + 1; x < x1; x++) {
			b.axis(x, y, z, Blocks.IRON_CHAIN, Direction.Axis.X);
		}
		for (int x : lanterns) {
			b.hanging(x, y - 1, z, Blocks.LANTERN);
		}
	}

	/** A small boat moored at the waterline, bow and stern along z. */
	static void skiff(final Builder b, final int x0, final int z0, final int z1) {
		for (int z = z0; z <= z1; z++) {
			boolean end = z == z0 || z == z1;
			b.set(x0 + 1, WATERLINE, z, BOARD);
			b.set(x0 + 2, WATERLINE, z, BOARD);
			if (!end) {
				b.set(x0, WATERLINE, z, PLANK);
				b.set(x0 + 3, WATERLINE, z, PLANK);
				b.stairs(x0, WATERLINE + 1, z, WARM_STAIRS, Direction.WEST, false);
				b.stairs(x0 + 3, WATERLINE + 1, z, WARM_STAIRS, Direction.EAST, false);
			}
		}
		b.stairs(x0 + 1, WATERLINE + 1, z0, WARM_STAIRS, Direction.NORTH, false);
		b.stairs(x0 + 2, WATERLINE + 1, z0, WARM_STAIRS, Direction.NORTH, false);
		b.stairs(x0 + 1, WATERLINE + 1, z1, WARM_STAIRS, Direction.SOUTH, false);
		b.stairs(x0 + 2, WATERLINE + 1, z1, WARM_STAIRS, Direction.SOUTH, false);
	}

	/** A buoy: a float, a post and a copper cap, a lantern on top if it marks a way in. */
	static void buoy(final Builder b, final int x, final int z, final boolean lit) {
		b.set(x, WATERLINE, z, PLANK);
		b.set(x, WATERLINE + 1, z, Blocks.DARK_OAK_FENCE);
		b.set(x, WATERLINE + 2, z, lit ? RUST : Blocks.CUT_COPPER_SLAB.waxed().exposed());
		if (lit) {
			b.set(x, WATERLINE + 3, z, Blocks.LANTERN);
		}
	}

	/** A ring of stairs round a rectangle, each facing out (an eave, upside down) or in (a roof step). */
	static void ring(final Builder b, final int x0, final int z0, final int x1, final int z1, final int y, final Block stairs, final boolean eave) {
		for (int x = x0; x <= x1; x++) {
			b.stairs(x, y, z0, stairs, eave ? Direction.SOUTH : Direction.SOUTH, eave);
			b.stairs(x, y, z1, stairs, eave ? Direction.NORTH : Direction.NORTH, eave);
		}
		for (int z = z0 + 1; z < z1; z++) {
			b.stairs(x0, y, z, stairs, Direction.EAST, eave);
			b.stairs(x1, y, z, stairs, Direction.WEST, eave);
		}
	}

	/** A doorway through a wall: three wide, three high, centred on ({@code x}, {@code z}). */
	static void door(final Builder b, final int x, final int z, final int y, final boolean alongX) {
		if (alongX) {
			b.clear(x - 1, y + 1, z, x + 1, y + 3, z);
		} else {
			b.clear(x, y + 1, z - 1, x, y + 3, z + 1);
		}
	}

	/** A stack of crates: barrels and spruce boxes, as the docks leave them. */
	static void crates(final Builder b, final int x, final int z, final int y, final int count) {
		for (int i = 0; i < count; i++) {
			int dx = i % 2;
			int dz = (i / 2) % 2;
			int dy = i / 4;
			b.set(x + dx, y + 1 + dy, z + dz, b.chance(0.45F) ? Blocks.BARREL : BOARD);
		}
	}
}
