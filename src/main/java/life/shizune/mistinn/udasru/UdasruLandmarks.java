package life.shizune.mistinn.udasru;

import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.station.StaffRole;
import life.shizune.mistinn.world.Builder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The Bell Capital's great buildings, each seen from across the land: the great bell tower on its terraces, buttressed,
 * galleried, its belfry open on every side under a two-tiered roof; the palace on its platform behind a colonnade, with
 * a raised clerestory and four corner towers; the Great Library with its portico, buttresses and lantern. Each with its
 * origin on the ground at its middle.
 */
public final class UdasruLandmarks {
	private static final Block STONE = MistinnBlocks.UDASRU_STONE_BRICKS;
	private static final Block STAIRS = MistinnBlocks.UDASRU_STONE_BRICK_STAIRS;
	private static final Block SLAB = MistinnBlocks.UDASRU_STONE_BRICK_SLAB;
	private static final Block WALL = MistinnBlocks.UDASRU_STONE_BRICK_WALL;
	private static final Block TILES = MistinnBlocks.UDASRU_ROOF_TILES;
	private static final Block TILE_STAIRS = MistinnBlocks.UDASRU_ROOF_TILE_STAIRS;
	private static final Block TILE_SLAB = MistinnBlocks.UDASRU_ROOF_TILE_SLAB;
	private static final Block BRONZE = MistinnBlocks.BELL_BRONZE;
	private static final Block PLASTER = MistinnBlocks.UDASRU_PLASTER;
	private static final Block TIMBER = MistinnBlocks.UDASRU_TIMBER;

	/** The great tower's heights: its gallery, its upper shaft's top, its belfry floor, and its roof's foot. */
	public static final int GALLERY = 30;
	public static final int UPPER = 44;
	public static final int BELFRY = 45;
	public static final int EAVES = 56;

	private UdasruLandmarks() {
	}

	// ── shared pieces ─────────────────────────────────────────────

	/** A square ring of stairs at height y, half-width h, every stair facing outward (an eave or a terrace's edge). */
	private static void stairRing(final Builder b, final int y, final int h, final Block stairs, final boolean top) {
		for (int a = -h; a <= h; a++) {
			b.stairs(a, y, -h, stairs, Direction.SOUTH, top);
			b.stairs(a, y, h, stairs, Direction.NORTH, top);
			b.stairs(-h, y, a, stairs, Direction.EAST, top);
			b.stairs(h, y, a, stairs, Direction.WEST, top);
		}
	}

	/** A steep pyramid roof from half-width h at y0: two courses a step, then a bronze finial and spire. */
	private static int steepRoof(final Builder b, final int y0, final int h, final int spire) {
		int y = y0;
		for (int r = h; r >= 1; r--) {
			for (int k = 0; k < 2; k++) {
				stairRing(b, y, r, TILE_STAIRS, false);
				if (r > 1) {
					b.fill(-r + 1, y, -r + 1, r - 1, y, r - 1, TILES);
				}
				y++;
			}
		}
		b.set(0, y, 0, TILES);
		for (int i = 1; i <= spire; i++) {
			b.set(0, y + i, 0, i == spire ? Blocks.LIGHTNING_ROD.waxed().unaffected() : BRONZE);
		}
		return y;
	}

	/** An arched window three wide and {@code tall} high in a wall facing {@code out}, its arch of upside-down stairs. */
	private static void archWindow(final Builder b, final int cx, final int y, final int cz, final Direction out, final int tall) {
		int ax = out.getAxis() == Direction.Axis.Z ? 1 : 0;
		int az = out.getAxis() == Direction.Axis.X ? 1 : 0;
		for (int s = -1; s <= 1; s++) {
			for (int dy = 0; dy < tall; dy++) {
				b.set(cx + ax * s, y + dy, cz + az * s, Blocks.GLASS_PANE);
			}
		}
		Direction left = out.getClockWise();
		b.stairs(cx - ax, y + tall - 1, cz - az, STAIRS, ax == 1 ? Direction.EAST : Direction.SOUTH, true);
		b.stairs(cx + ax, y + tall - 1, cz + az, STAIRS, ax == 1 ? Direction.WEST : Direction.NORTH, true);
		b.set(cx + ax * 2, y + tall, cz + az * 2, BRONZE);
		b.set(cx - ax * 2, y + tall, cz - az * 2, BRONZE);
		b.set(cx, y + tall, cz, BRONZE);
		left.getAxis();
	}

	private static void column(final Builder b, final int x, final int z, final int y0, final int y1) {
		for (int y = y0; y <= y1; y++) {
			b.set(x, y, z, y == y0 || y == y1 ? STONE : MistinnBlocks.UDASRU_STONE_BRICK_WALL);
		}
		b.set(x, y1 + 1, z, BRONZE);
	}

	private static BlockPos bell(final Builder b, final int x, final int y, final int z, final BellAttachType attach) {
		b.set(x, y, z, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, attach).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		return b.at(x, y, z);
	}

	// ── the great bell tower ──────────────────────────────────────

	/** The capital's great bell tower. Returns the small bell that rings the great one. */
	public static BlockPos greatTower(final Builder b) {
		b.clear(-12, 1, -12, 12, EAVES + 24, 13);
		// two terraces round it, their edges stepped, lanterns at the corners
		for (int x = -11; x <= 11; x++) {
			for (int z = -11; z <= 11; z++) {
				b.footing(x, z, STONE, 30);
				b.set(x, 0, z, (x + z & 1) == 0 ? STONE : Blocks.POLISHED_ANDESITE);
			}
		}
		b.fill(-9, 1, -9, 9, 1, 9, STONE);
		stairRing(b, 1, 10, STAIRS, false);
		for (int[] c : new int[][] {{-10, -10}, {10, -10}, {-10, 10}, {10, 10}}) {
			b.set(c[0], 1, c[1], WALL);
			b.set(c[0], 2, c[1], Blocks.LANTERN);
		}
		// the shaft, half six, with buttresses at its corners stepping in as they rise
		for (int y = 2; y <= GALLERY; y++) {
			Block course = y % 9 == 0 ? BRONZE : STONE;
			for (int i = -6; i <= 6; i++) {
				b.set(i, y, -6, course);
				b.set(i, y, 6, course);
				b.set(-6, y, i, course);
				b.set(6, y, i, course);
			}
			int reach = y < 12 ? 8 : y < 22 ? 7 : 6;
			for (int[] c : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
				for (int d = 6; d <= reach; d++) {
					b.set(c[0] * d, y, c[1] * 6, STONE);
					b.set(c[0] * 6, y, c[1] * d, STONE);
					b.set(c[0] * d, y, c[1] * d, STONE);
				}
				if (reach > 6 && (y == 11 || y == 21)) {
					b.stairs(c[0] * reach, y + 1, c[1] * reach, STAIRS, c[0] > 0 ? Direction.WEST : Direction.EAST, false);
				}
			}
		}
		for (Direction face : new Direction[] {Direction.NORTH, Direction.EAST, Direction.WEST}) {
			archWindow(b, face.getStepX() * 6, 8, face.getStepZ() * 6, face, 4);
			archWindow(b, face.getStepX() * 6, 18, face.getStepZ() * 6, face, 4);
		}
		archWindow(b, 0, 18, 6, Direction.SOUTH, 4);
		// the door: a tall arch to the south
		b.clear(-1, 2, 6, 1, 5, 6);
		b.door(0, 2, 6, Blocks.DARK_OAK_DOOR, Direction.NORTH, false);
		b.door(-1, 2, 6, Blocks.DARK_OAK_DOOR, Direction.NORTH, false);
		b.door(1, 2, 6, Blocks.DARK_OAK_DOOR, Direction.NORTH, false);
		b.stairs(-1, 5, 6, STAIRS, Direction.EAST, true);
		b.stairs(1, 5, 6, STAIRS, Direction.WEST, true);
		b.facing(-3, 4, 7, Blocks.WALL_TORCH, Direction.SOUTH);
		b.facing(3, 4, 7, Blocks.WALL_TORCH, Direction.SOUTH);
		b.facing(0, 7, 7, Blocks.WALL_BANNER.blue(), Direction.SOUTH);

		// the stair round the inside, all the way to the belfry
		int[][] ring = ring(4);
		for (int y = 2; y <= UPPER; y++) {
			int[] at = ring[(y - 2) % ring.length];
			int[] next = ring[(y - 1) % ring.length];
			b.stairs(at[0], y, at[1], STAIRS, Direction.getApproximateNearest(next[0] - at[0], 0, next[1] - at[1]), false);
			b.set(at[0], y - 1, at[1], STONE);
			b.clear(at[0], y + 1, at[1], at[0], y + 3, at[1]);
		}
		for (int y = 6; y <= UPPER; y += 8) {
			b.hanging(0, y, 0, Blocks.LANTERN);
		}

		// the gallery: a railed walk round the shaft
		for (int x = -8; x <= 8; x++) {
			for (int z = -8; z <= 8; z++) {
				if (Math.max(Math.abs(x), Math.abs(z)) >= 6) {
					b.set(x, GALLERY + 1, z, STONE);
				}
				if (Math.max(Math.abs(x), Math.abs(z)) == 8) {
					b.set(x, GALLERY + 2, z, WALL);
				}
			}
		}
		stairRing(b, GALLERY, 8, STAIRS, true);
		// the upper shaft, half five, plaster panels between stone
		for (int y = GALLERY + 2; y <= UPPER; y++) {
			for (int i = -5; i <= 5; i++) {
				Block panel = Math.abs(i) <= 3 && y % 6 != 0 ? PLASTER : STONE;
				b.set(i, y, -5, panel);
				b.set(i, y, 5, panel);
				b.set(-5, y, i, panel);
				b.set(5, y, i, panel);
			}
			for (int[] c : new int[][] {{-5, -5}, {5, -5}, {-5, 5}, {5, 5}}) {
				b.axis(c[0], y, c[1], TIMBER, Direction.Axis.Y);
			}
		}
		b.clear(-4, GALLERY + 2, -4, 4, UPPER, 4);
		ring = ring(4);
		for (int y = GALLERY + 2; y <= UPPER; y++) {
			int[] at = ring[(y - 2) % ring.length];
			int[] next = ring[(y - 1) % ring.length];
			b.stairs(at[0], y, at[1], STAIRS, Direction.getApproximateNearest(next[0] - at[0], 0, next[1] - at[1]), false);
			b.set(at[0], y - 1, at[1], STONE);
		}
		b.clear(-1, GALLERY + 2, 5, 1, GALLERY + 4, 5);

		// the belfry: open arches on every side, the great bell under the beam
		b.fill(-6, BELFRY, -6, 6, BELFRY, 6, STONE);
		int[] top = ring[(UPPER - 2) % ring.length];
		b.set(top[0], BELFRY, top[1], Blocks.AIR);
		for (int[] c : new int[][] {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}, {-6, -2}, {-6, 2}, {6, -2}, {6, 2}, {-2, -6}, {2, -6}, {-2, 6}, {2, 6}}) {
			for (int y = BELFRY + 1; y <= BELFRY + 9; y++) {
				b.set(c[0], y, c[1], y % 4 == 0 ? BRONZE : STONE);
			}
		}
		for (int i = -6; i <= 6; i++) {
			b.set(i, BELFRY + 10, -6, STONE);
			b.set(i, BELFRY + 10, 6, STONE);
			b.set(-6, BELFRY + 10, i, STONE);
			b.set(6, BELFRY + 10, i, STONE);
			b.set(i, BELFRY + 1, -6, Math.abs(i) % 4 == 2 || Math.abs(i) == 6 ? STONE : WALL);
			b.set(i, BELFRY + 1, 6, Math.abs(i) % 4 == 2 || Math.abs(i) == 6 ? STONE : WALL);
			b.set(-6, BELFRY + 1, i, Math.abs(i) % 4 == 2 || Math.abs(i) == 6 ? STONE : WALL);
			b.set(6, BELFRY + 1, i, Math.abs(i) % 4 == 2 || Math.abs(i) == 6 ? STONE : WALL);
		}
		for (int x = -6; x <= 6; x++) {
			b.axis(x, BELFRY + 9, 0, TIMBER, Direction.Axis.X);
		}
		b.set(0, BELFRY + 8, 0, BRONZE);
		b.fill(-1, BELFRY + 6, -1, 1, BELFRY + 7, 1, BRONZE);
		for (int y = BELFRY + 3; y <= BELFRY + 5; y++) {
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					if (Math.abs(x) == 2 || Math.abs(z) == 2 || y == BELFRY + 5) {
						b.set(x, y, z, BRONZE);
					}
				}
			}
		}
		b.set(0, BELFRY + 2, 0, Blocks.IRON_CHAIN);
		BlockPos ringing = bell(b, 0, BELFRY + 1, 4, BellAttachType.FLOOR);
		b.hanging(-4, BELFRY + 9, -4, Blocks.LANTERN);
		b.hanging(4, BELFRY + 9, 4, Blocks.LANTERN);

		// the roof: a flared eave, then a steep spire of tiles
		stairRing(b, EAVES, 8, TILE_STAIRS, false);
		b.fill(-7, EAVES, -7, 7, EAVES, 7, TILES);
		stairRing(b, EAVES + 1, 7, TILE_STAIRS, false);
		b.fill(-6, EAVES + 1, -6, 6, EAVES + 1, 6, TILES);
		steepRoof(b, EAVES + 2, 6, 6);
		b.settle();
		return ringing;
	}

	private static int[][] ring(final int h) {
		java.util.List<int[]> ring = new java.util.ArrayList<>();
		for (int x = -h; x < h; x++) {
			ring.add(new int[] {x, h});
		}
		for (int z = h; z > -h; z--) {
			ring.add(new int[] {h, z});
		}
		for (int x = h; x > -h; x--) {
			ring.add(new int[] {x, -h});
		}
		for (int z = -h; z < h; z++) {
			ring.add(new int[] {-h, z});
		}
		return ring.toArray(new int[0][]);
	}

	// ── the palace ────────────────────────────────────────────────

	/** The palace: a platform, a colonnaded hall under a two-tiered roof, four corner towers, the Regent inside. */
	public static void palace(final Builder b) {
		b.clear(-19, 1, -15, 19, 32, 17);
		for (int x = -18; x <= 18; x++) {
			for (int z = -14; z <= 16; z++) {
				b.footing(x, z, STONE, 30);
				b.set(x, 0, z, STONE);
				if (z <= 13) {
					b.set(x, 1, z, (x + z & 1) == 0 ? STONE : Blocks.POLISHED_ANDESITE);
				}
			}
		}
		for (int x = -4; x <= 4; x++) {
			b.stairs(x, 1, 14, STAIRS, Direction.NORTH, false);
			b.stairs(x, 0, 15, STAIRS, Direction.NORTH, false);
		}
		// the hall's walls
		int hx = 12;
		int hz = 8;
		for (int y = 2; y <= 10; y++) {
			for (int x = -hx; x <= hx; x++) {
				b.set(x, y, -hz, y >= 8 ? PLASTER : STONE);
				b.set(x, y, hz, y >= 8 ? PLASTER : STONE);
			}
			for (int z = -hz; z <= hz; z++) {
				b.set(-hx, y, z, y >= 8 ? PLASTER : STONE);
				b.set(hx, y, z, y >= 8 ? PLASTER : STONE);
			}
		}
		for (int x = -hx + 3; x <= hx - 3; x += 4) {
			archWindow(b, x, 3, -hz, Direction.NORTH, 4);
			archWindow(b, x, 3, hz, Direction.SOUTH, 4);
		}
		b.clear(-1, 2, hz, 1, 5, hz);
		b.door(-1, 2, hz, Blocks.DARK_OAK_DOOR, Direction.SOUTH, false);
		b.door(0, 2, hz, Blocks.DARK_OAK_DOOR, Direction.SOUTH, false);
		b.door(1, 2, hz, Blocks.DARK_OAK_DOOR, Direction.SOUTH, false);
		// the colonnade before it, under a slab porch
		for (int x = -hx + 1; x <= hx - 1; x += 3) {
			column(b, x, hz + 3, 2, 8);
		}
		b.fill(-hx, 10, hz + 1, hx, 10, hz + 4, TILE_SLAB);
		for (int x = -hx; x <= hx; x++) {
			b.stairs(x, 10, hz + 5, TILE_STAIRS, Direction.NORTH, false);
		}
		// the lower roof, all round, and the raised clerestory with its own roof
		for (int i = 0; i <= 3; i++) {
			int rx = hx + 2 - i;
			int rz = hz + 2 - i;
			for (int x = -rx; x <= rx; x++) {
				b.stairs(x, 11 + i, -rz, TILE_STAIRS, Direction.SOUTH, false);
				b.stairs(x, 11 + i, rz, TILE_STAIRS, Direction.NORTH, false);
			}
			for (int z = -rz; z <= rz; z++) {
				b.stairs(-rx, 11 + i, z, TILE_STAIRS, Direction.EAST, false);
				b.stairs(rx, 11 + i, z, TILE_STAIRS, Direction.WEST, false);
			}
			b.fill(-rx + 1, 11 + i, -rz + 1, rx - 1, 11 + i, rz - 1, TILES);
		}
		for (int y = 15; y <= 18; y++) {
			for (int x = -8; x <= 8; x++) {
				b.set(x, y, -4, y == 18 ? BRONZE : x % 3 == 0 ? STONE : Blocks.GLASS_PANE);
				b.set(x, y, 4, y == 18 ? BRONZE : x % 3 == 0 ? STONE : Blocks.GLASS_PANE);
			}
			for (int z = -4; z <= 4; z++) {
				b.set(-8, y, z, STONE);
				b.set(8, y, z, STONE);
			}
		}
		for (int i = 0; i <= 5; i++) {
			int zn = -6 + i;
			int zs = 6 - i;
			if (zn > zs) {
				break;
			}
			for (int x = -10; x <= 10; x++) {
				if (zn == zs) {
					b.set(x, 19 + i, zn, TILES);
				} else {
					b.stairs(x, 19 + i, zn, TILE_STAIRS, Direction.SOUTH, false);
					b.stairs(x, 19 + i, zs, TILE_STAIRS, Direction.NORTH, false);
				}
			}
			if (i > 0) {
				for (int z = zn + 1; z < zs; z++) {
					b.set(-8, 18 + i, z, PLASTER);
					b.set(8, 18 + i, z, PLASTER);
				}
			}
		}
		b.set(-10, 25, 0, BRONZE);
		b.set(10, 25, 0, BRONZE);
		// the corner towers
		for (int[] c : new int[][] {{-15, -11}, {15, -11}, {-15, 11}, {15, 11}}) {
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					for (int y = 1; y <= 14; y++) {
						boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2;
						b.set(c[0] + x, y, c[1] + z, wall ? (y % 5 == 0 ? BRONZE : STONE) : Blocks.AIR);
					}
				}
			}
			b.set(c[0], 8, c[1] + (c[1] > 0 ? 2 : -2), Blocks.GLASS_PANE);
			Builder at = new Builder(b.level(), b.at(c[0], 0, c[1]), b.random());
			stairRing(at, 15, 3, TILE_STAIRS, false);
			at.fill(-2, 15, -2, 2, 15, 2, TILES);
			steepRoof(at, 16, 2, 3);
		}
		// inside: the dais, banners, the Regent and her guard
		b.fill(-4, 2, -7, 4, 2, -5, STONE);
		b.fill(-3, 3, -7, 3, 3, -6, Blocks.CARPET.blue());
		b.facing(0, 3, -7, Blocks.SPRUCE_STAIRS, Direction.NORTH);
		for (int z = -5; z <= hz - 1; z++) {
			b.set(0, 2, z, Blocks.CARPET.blue());
		}
		for (int x = -hx + 2; x <= hx - 2; x += 5) {
			b.facing(x, 7, -hz + 1, Blocks.WALL_BANNER.blue(), Direction.SOUTH);
			b.hanging(x, 9, 0, Blocks.LANTERN);
		}
		b.facing(0, 8, hz + 1, Blocks.WALL_BANNER.blue(), Direction.SOUTH);
		b.settle();
		UdasruBuild.person(b, StaffRole.REGENT, 0, 3, -6, 0.0F, 2);
		UdasruBuild.person(b, StaffRole.CAPITAL_GUARD, 3, 2, hz + 2, 0.0F, 2);
		UdasruBuild.person(b, StaffRole.CAPITAL_GUARD, -3, 2, hz + 2, 0.0F, 2);
	}

	// ── the Great Library ─────────────────────────────────────────

	/** The Great Library: a buttressed hall with a portico and pediment, a lantern on its ridge, shelves within. */
	public static void library(final Builder b) {
		int hx = 12;
		int hz = 8;
		b.clear(-hx - 3, 1, -hz - 2, hx + 3, 30, hz + 7);
		for (int x = -hx - 2; x <= hx + 2; x++) {
			for (int z = -hz - 1; z <= hz + 6; z++) {
				b.footing(x, z, STONE, 30);
				b.set(x, 0, z, STONE);
			}
		}
		for (int y = 1; y <= 11; y++) {
			for (int x = -hx; x <= hx; x++) {
				b.set(x, y, -hz, STONE);
				b.set(x, y, hz, STONE);
			}
			for (int z = -hz; z <= hz; z++) {
				b.set(-hx, y, z, STONE);
				b.set(hx, y, z, STONE);
			}
		}
		// buttresses down the long sides, tall windows between
		for (int x = -hx; x <= hx; x += 4) {
			for (int y = 1; y <= 10; y++) {
				int out = y <= 4 ? 2 : y <= 8 ? 1 : 0;
				for (int d = 1; d <= out; d++) {
					b.set(x, y, -hz - d, STONE);
					b.set(x, y, hz + d, STONE);
				}
			}
			b.stairs(x, 5, -hz - 2, STAIRS, Direction.SOUTH, false);
			b.stairs(x, 9, -hz - 1, STAIRS, Direction.SOUTH, false);
		}
		for (int x = -hx + 2; x <= hx - 2; x += 4) {
			archWindow(b, x, 3, -hz, Direction.NORTH, 6);
		}
		// the portico: four columns under a pediment
		for (int x = -5; x <= 5; x += 3) {
			column(b, x, hz + 4, 1, 8);
		}
		b.fill(-6, 10, hz + 1, 6, 10, hz + 5, STONE);
		for (int i = 0; i <= 6; i++) {
			for (int x = -6 + i; x <= 6 - i; x++) {
				b.set(x, 11 + i, hz + 5, i == 6 - Math.abs(x) && x != 0 ? STONE : x == 6 - i || x == -6 + i ? STONE : PLASTER);
			}
		}
		for (int x = -2; x <= 2; x++) {
			b.stairs(x, 0, hz + 6, STAIRS, Direction.NORTH, false);
		}
		b.clear(-1, 1, hz, 1, 4, hz);
		b.door(-1, 1, hz, Blocks.DARK_OAK_DOOR, Direction.SOUTH, false);
		b.door(1, 1, hz, Blocks.DARK_OAK_DOOR, Direction.SOUTH, false);
		b.door(0, 1, hz, Blocks.DARK_OAK_DOOR, Direction.SOUTH, false);
		// the roof and its lantern
		for (int i = 0; i <= hz + 1; i++) {
			int zn = -hz - 1 + i;
			int zs = hz + 1 - i;
			if (zn > zs) {
				break;
			}
			for (int x = -hx - 1; x <= hx + 1; x++) {
				if (zn == zs) {
					b.set(x, 12 + i, zn, TILES);
				} else {
					b.stairs(x, 12 + i, zn, TILE_STAIRS, Direction.SOUTH, false);
					b.stairs(x, 12 + i, zs, TILE_STAIRS, Direction.NORTH, false);
				}
			}
			if (i > 0) {
				for (int z = zn + 1; z < zs; z++) {
					b.set(-hx, 11 + i, z, PLASTER);
					b.set(hx, 11 + i, z, PLASTER);
				}
			}
		}
		Builder lantern = new Builder(b.level(), b.at(0, 12 + hz + 1, 0), b.random());
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				for (int y = 1; y <= 4; y++) {
					boolean corner = Math.abs(x) == 2 && Math.abs(z) == 2;
					lantern.set(x, y, z, corner ? STONE : Math.abs(x) == 2 || Math.abs(z) == 2 ? Blocks.GLASS_PANE : Blocks.AIR);
				}
			}
		}
		lantern.set(0, 2, 0, Blocks.LANTERN);
		steepRoof(lantern, 5, 3, 2);
		// inside: shelves, reading tables, the Chronicler's desk
		for (int x = -hx + 2; x <= hx - 2; x++) {
			if (Math.abs(x) <= 1) {
				continue;
			}
			b.fill(x, 1, -hz + 1, x, 5, -hz + 1, Blocks.BOOKSHELF);
		}
		for (int x = -8; x <= 8; x += 4) {
			b.fill(x, 1, -3, x, 4, 3, Blocks.BOOKSHELF);
		}
		b.facing(-2, 1, -5, Blocks.LECTERN, Direction.SOUTH);
		b.facing(2, 1, -5, Blocks.LECTERN, Direction.SOUTH);
		b.fill(-1, 1, -6, 1, 1, -6, Blocks.DARK_OAK_PLANKS);
		b.candles(-1, 2, -6, Blocks.DYED_CANDLE.white(), 3, true);
		b.set(1, 2, -6, Blocks.FLOWER_POT);
		for (int x = -hx + 3; x <= hx - 3; x += 5) {
			b.hanging(x, 10, 0, Blocks.LANTERN);
		}
		b.settle();
		UdasruBuild.person(b, StaffRole.CHRONICLER, 0, 1, -4, 180.0F, 3);
		UdasruBuild.person(b, StaffRole.TOWNSWOMAN, 6, 1, 5, 0.0F, 4);
	}
}
