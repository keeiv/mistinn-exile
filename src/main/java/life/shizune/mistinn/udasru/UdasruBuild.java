package life.shizune.mistinn.udasru;

import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.station.StaffRole;
import life.shizune.mistinn.world.Builder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * How Udasru builds: pale limestone and indigo tiles, white plaster between dark beams, bronze where a bell hangs. Each
 * method raises one thing with its origin on the ground at its middle (y 0 is the ground block, y 1 the first air), and
 * returns, where it matters, the place of its bell.
 */
public final class UdasruBuild {
	private static final Block STONE = MistinnBlocks.UDASRU_STONE_BRICKS;
	private static final Block STONE_STAIRS = MistinnBlocks.UDASRU_STONE_BRICK_STAIRS;
	private static final Block STONE_SLAB = MistinnBlocks.UDASRU_STONE_BRICK_SLAB;
	private static final Block STONE_WALL = MistinnBlocks.UDASRU_STONE_BRICK_WALL;
	private static final Block TILES = MistinnBlocks.UDASRU_ROOF_TILES;
	private static final Block TILE_STAIRS = MistinnBlocks.UDASRU_ROOF_TILE_STAIRS;
	private static final Block TILE_SLAB = MistinnBlocks.UDASRU_ROOF_TILE_SLAB;
	private static final Block PLASTER = MistinnBlocks.UDASRU_PLASTER;
	private static final Block TIMBER = MistinnBlocks.UDASRU_TIMBER;
	private static final Block BRONZE = MistinnBlocks.BELL_BRONZE;

	/** How a town builds its houses: walls, the frame, the floor and the roof. */
	public record Style(Block wall, Block frame, Block floor, Block roof, Block roofStairs, Block roofSlab, Block door, Block ground) {
		public static final Style CAPITAL = new Style(PLASTER, TIMBER, Blocks.SPRUCE_PLANKS, TILES, TILE_STAIRS, TILE_SLAB, Blocks.DARK_OAK_DOOR, STONE);
		public static final Style SALT = new Style(PLASTER, Blocks.STRIPPED_BIRCH_LOG, Blocks.BIRCH_PLANKS, Blocks.SMOOTH_SANDSTONE,
			Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.BIRCH_DOOR, MistinnBlocks.SALT_CRUST);
		public static final Style PORT = new Style(Blocks.SPRUCE_PLANKS, Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_PLANKS, TILES, TILE_STAIRS, TILE_SLAB,
			Blocks.SPRUCE_DOOR, Blocks.GRAVEL);
		public static final Style WOODS = new Style(MistinnBlocks.MIST_PINE_PLANKS, MistinnBlocks.MIST_PINE_LOG, MistinnBlocks.MIST_PINE_PLANKS,
			Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_SLAB, MistinnBlocks.MIST_PINE_DOOR, Blocks.COARSE_DIRT);
		public static final Style FROST = new Style(STONE, Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_STAIRS,
			Blocks.DEEPSLATE_TILE_SLAB, Blocks.SPRUCE_DOOR, Blocks.PACKED_ICE);
		public static final Style FORGE = new Style(Blocks.BRICKS, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.CUT_COPPER.waxed().weathered(),
			Blocks.CUT_COPPER_STAIRS.waxed().weathered(), Blocks.CUT_COPPER_SLAB.waxed().weathered(), Blocks.IRON_DOOR, MistinnBlocks.RUST_SOIL);
	}

	private UdasruBuild() {
	}

	// ── Bell towers ───────────────────────────────────────────────

	/** How tall a bell tower's shaft stands. */
	public static final int SHAFT = 26;

	/**
	 * A bell tower: a square shaft of limestone banded with bronze every seven courses, a ladder up its north wall, an open
	 * belfry under a tiled pyramid with the bell hanging from its beam, and its keeper at the door. Returns the bell.
	 */
	public static BlockPos tower(final Builder b) {
		b.clear(-5, 1, -5, 5, SHAFT + 16, 6);
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 4; z++) {
				b.footing(x, z, STONE, 24);
				b.set(x, 0, z, STONE);
			}
		}
		for (int y = 1; y <= SHAFT; y++) {
			Block course = y % 7 == 0 ? BRONZE : STONE;
			for (int i = -4; i <= 4; i++) {
				b.set(i, y, -4, course);
				b.set(i, y, 4, course);
				b.set(-4, y, i, course);
				b.set(4, y, i, course);
			}
			if (y % 6 == 3) {
				// slits to see out by
				b.set(0, y, 4, Blocks.AIR);
				b.set(4, y, 0, Blocks.AIR);
				b.set(-4, y, 0, Blocks.AIR);
			}
			b.facing(0, y, -3, Blocks.LADDER, Direction.SOUTH);
		}
		b.clear(0, 1, 4, 0, 2, 4);
		b.door(0, 1, 4, Blocks.DARK_OAK_DOOR, Direction.NORTH, false);
		b.facing(1, 3, 5, Blocks.WALL_TORCH, Direction.SOUTH);
		b.facing(-1, 3, 5, Blocks.WALL_TORCH, Direction.SOUTH);
		b.stairs(0, 0, 5, STONE_STAIRS, Direction.NORTH, false);

		// the belfry floor, open round the ladder's hole
		int deck = SHAFT + 1;
		b.fill(-4, deck, -4, 4, deck, 4, STONE);
		b.set(0, deck, -3, Blocks.AIR);
		b.facing(0, deck, -3, Blocks.LADDER, Direction.SOUTH);
		for (int[] c : new int[][] {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
			for (int y = deck + 1; y <= deck + 6; y++) {
				b.axis(c[0], y, c[1], TIMBER, Direction.Axis.Y);
			}
		}
		for (int i = -3; i <= 3; i++) {
			b.set(i, deck + 1, -4, STONE_WALL);
			b.set(i, deck + 1, 4, STONE_WALL);
			b.set(-4, deck + 1, i, STONE_WALL);
			b.set(4, deck + 1, i, STONE_WALL);
		}
		b.set(0, deck + 1, -4, Blocks.AIR);
		for (int x = -4; x <= 4; x++) {
			b.axis(x, deck + 6, 0, TIMBER, Direction.Axis.X);
		}
		BlockPos bell = bell(b, 0, deck + 5, 0, BellAttachType.CEILING);
		b.hanging(2, deck + 5, 2, Blocks.LANTERN);
		b.hanging(-2, deck + 5, -2, Blocks.LANTERN);

		// the roof: a pyramid of tiles, and a bronze spire
		roofPyramid(b, deck + 7, 5);
		b.set(0, deck + 12, 0, BRONZE);
		b.set(0, deck + 13, 0, Blocks.LIGHTNING_ROD.waxed().unaffected());
		b.settle();
		return bell;
	}

	/** A pyramid of tile stairs from a square of half-width {@code half}, rising a course at a time. */
	private static void roofPyramid(final Builder b, final int y0, final int half) {
		for (int i = 0; i <= half; i++) {
			int h = half - i;
			int y = y0 + i;
			if (h == 0) {
				b.set(0, y, 0, TILES);
				break;
			}
			for (int a = -h; a <= h; a++) {
				b.stairs(a, y, -h, TILE_STAIRS, Direction.SOUTH, false);
				b.stairs(a, y, h, TILE_STAIRS, Direction.NORTH, false);
				b.stairs(-h, y, a, TILE_STAIRS, Direction.EAST, false);
				b.stairs(h, y, a, TILE_STAIRS, Direction.WEST, false);
			}
			b.fill(-h + 1, y, -h + 1, h - 1, y, h - 1, TILES);
		}
	}

	/** A bell at a relative place, hung as asked, facing south; returns where it is. */
	private static BlockPos bell(final Builder b, final int x, final int y, final int z, final BellAttachType attach) {
		BlockState state = Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, attach)
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
		b.set(x, y, z, state);
		return b.at(x, y, z);
	}

	// ── The great bells' shrines ──────────────────────────────────

	/**
	 * A great bell's shrine: a limestone platform under a timber frame from whose beam a great bronze bell hangs, five
	 * blocks across at its lip, and before it the small bell that rings it. Returns the small bell.
	 */
	public static BlockPos shrine(final Builder b) {
		b.clear(-6, 1, -6, 6, 12, 6);
		for (int x = -5; x <= 5; x++) {
			for (int z = -5; z <= 5; z++) {
				b.footing(x, z, STONE, 30);
				b.set(x, 0, z, (x + z & 1) == 0 ? STONE : Blocks.POLISHED_ANDESITE);
			}
		}
		for (int y = 1; y <= 8; y++) {
			b.axis(-4, y, 0, TIMBER, Direction.Axis.Y);
			b.axis(4, y, 0, TIMBER, Direction.Axis.Y);
		}
		for (int x = -4; x <= 4; x++) {
			b.axis(x, 9, 0, TIMBER, Direction.Axis.X);
		}
		b.set(0, 8, 0, BRONZE);
		b.fill(-1, 6, -1, 1, 7, 1, BRONZE);
		for (int y = 4; y <= 5; y++) {
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					if (Math.abs(x) == 2 || Math.abs(z) == 2) {
						b.set(x, y, z, BRONZE);
					}
				}
			}
		}
		b.set(0, 3, 0, Blocks.IRON_CHAIN);
		b.set(0, 2, 0, BRONZE);
		b.set(0, 1, 4, STONE_WALL);
		BlockPos ring = bell(b, 0, 2, 4, BellAttachType.FLOOR);
		b.set(-5, 1, 5, Blocks.LANTERN);
		b.set(5, 1, 5, Blocks.LANTERN);
		b.set(-5, 1, -5, Blocks.LANTERN);
		b.set(5, 1, -5, Blocks.LANTERN);
		for (int x = -2; x <= 2; x++) {
			b.stairs(x, 0, 6, STONE_STAIRS, Direction.NORTH, false);
			b.footing(x, 0, 6, STONE, 30);
		}
		b.settle();
		return ring;
	}

	// ── Houses and towns ──────────────────────────────────────────

	/**
	 * A house: a timber frame filled with the style's walls on a plank floor, a door to the south, windows, a pitched roof
	 * along its length, and inside a bed, a table, a chest and a light. {@code hx} and {@code hz} are its half-sizes to
	 * the outside of its walls; {@code floors} is one or two.
	 */
	public static void house(final Builder b, final Style s, final int hx, final int hz, final int floors) {
		int wallTop = floors * 4;
		b.clear(-hx - 1, 1, -hz - 1, hx + 1, wallTop + hz + 4, hz + 2);
		for (int x = -hx; x <= hx; x++) {
			for (int z = -hz; z <= hz; z++) {
				b.footing(x, z, s.ground() == Blocks.PACKED_ICE ? STONE : Blocks.COBBLESTONE, 24);
				b.set(x, 0, z, s.floor());
			}
		}
		for (int y = 1; y <= wallTop; y++) {
			for (int x = -hx; x <= hx; x++) {
				b.set(x, y, -hz, s.wall());
				b.set(x, y, hz, s.wall());
			}
			for (int z = -hz; z <= hz; z++) {
				b.set(-hx, y, z, s.wall());
				b.set(hx, y, z, s.wall());
			}
			for (int[] c : new int[][] {{-hx, -hz}, {hx, -hz}, {-hx, hz}, {hx, hz}}) {
				b.axis(c[0], y, c[1], s.frame(), Direction.Axis.Y);
			}
		}
		for (int f = 1; f <= floors; f++) {
			int top = f * 4;
			for (int x = -hx; x <= hx; x++) {
				b.axis(x, top, -hz, s.frame(), Direction.Axis.X);
				b.axis(x, top, hz, s.frame(), Direction.Axis.X);
			}
			for (int z = -hz; z <= hz; z++) {
				b.axis(-hx, top, z, s.frame(), Direction.Axis.Z);
				b.axis(hx, top, z, s.frame(), Direction.Axis.Z);
			}
			if (f < floors) {
				b.fill(-hx + 1, top, -hz + 1, hx - 1, top, hz - 1, s.floor());
				b.set(hx - 1, top, -hz + 1, Blocks.AIR);
				for (int y = top - 3; y <= top; y++) {
					b.facing(hx - 1, y, -hz + 1, Blocks.LADDER, Direction.WEST);
				}
			}
			// windows, one each side a floor
			int wy = top - 2;
			b.set(0, wy, -hz, Blocks.GLASS_PANE);
			b.set(-hx, wy, 0, Blocks.GLASS_PANE);
			b.set(hx, wy, 0, Blocks.GLASS_PANE);
			if (hx >= 4) {
				b.set(-hx + 2, wy, hz, Blocks.GLASS_PANE);
				b.set(hx - 2, wy, hz, Blocks.GLASS_PANE);
			}
		}
		b.door(0, 1, hz, s.door(), Direction.SOUTH, false);
		b.facing(1, 3, hz + 1, Blocks.WALL_TORCH, Direction.SOUTH);

		// the roof: a ridge along x, stairs climbing from the long walls, gables filled with the wall
		int y = wallTop + 1;
		for (int i = 0; i <= hz + 1; i++) {
			int zn = -hz - 1 + i;
			int zs = hz + 1 - i;
			if (zn > zs) {
				break;
			}
			for (int x = -hx - 1; x <= hx + 1; x++) {
				if (zn == zs) {
					b.slab(x, y + i, zn, s.roofSlab(), false);
				} else {
					b.stairs(x, y + i, zn, s.roofStairs(), Direction.SOUTH, false);
					b.stairs(x, y + i, zs, s.roofStairs(), Direction.NORTH, false);
				}
			}
			if (i > 0) {
				for (int z = zn + 1; z < zs; z++) {
					b.set(-hx, y + i - 1, z, s.wall());
					b.set(hx, y + i - 1, z, s.wall());
				}
			}
		}

		// inside: a bed, a table, a chest and a light
		b.bed(-hx + 1, 1, -hz + 2, Blocks.BED.lightBlue(), Direction.NORTH);
		b.set(hx - 1, 1, hz - 1, Blocks.SPRUCE_FENCE);
		b.set(hx - 1, 2, hz - 1, Blocks.SPRUCE_PRESSURE_PLATE);
		b.container(hx - 1, 1, -hz + 1, Blocks.CHEST, Direction.WEST, "minecraft:chests/village/village_plains_house");
		b.set(-hx + 1, 1, hz - 1, Blocks.BARREL);
		b.hanging(0, wallTop - 1, 0, Blocks.LANTERN);
		b.settle();
	}

	/** A town's square: paving round a well, and a small bell on a post to greet by. */
	public static void square(final Builder b, final Style s, final int half) {
		b.clear(-half, 1, -half, half, 6, half);
		for (int x = -half; x <= half; x++) {
			for (int z = -half; z <= half; z++) {
				b.footing(x, z, Blocks.COBBLESTONE, 16);
				b.set(x, 0, z, (x * 7 + z * 3 & 3) == 0 ? Blocks.POLISHED_ANDESITE : s.ground() == MistinnBlocks.SALT_CRUST ? Blocks.SMOOTH_SANDSTONE : STONE);
			}
		}
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				boolean rim = Math.abs(x) == 2 || Math.abs(z) == 2;
				b.set(x, 0, z, rim ? STONE : Blocks.WATER);
				if (rim) {
					b.set(x, 1, z, STONE_WALL);
				}
			}
		}
		b.fill(-1, -3, -1, 1, -1, 1, Blocks.WATER);
		for (int[] c : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
			b.axis(c[0], 2, c[1], s.frame(), Direction.Axis.Y);
			b.axis(c[0], 3, c[1], s.frame(), Direction.Axis.Y);
		}
		for (int x = -2; x <= 2; x++) {
			b.slab(x, 4, -2, s.roofSlab(), false);
			b.slab(x, 4, 2, s.roofSlab(), false);
		}
		b.fill(-1, 4, -1, 1, 4, 1, s.roofSlab() == TILE_SLAB ? TILES : s.roof());
		b.set(half - 2, 1, half - 2, s.frame());
		b.set(half - 2, 2, half - 2, s.frame());
		bell(b, half - 2, 3, half - 2, BellAttachType.FLOOR);
		b.set(-half + 2, 1, half - 2, Blocks.LANTERN);
		b.settle();
	}

	/** One of Udasru's people, at home here, keeping within a few blocks of it. */
	public static void person(final Builder b, final StaffRole role, final int x, final int y, final int z, final float yaw, final int roam) {
		b.spawn(MistinnEntities.STATION_HAND, x, y, z, yaw, hand -> {
			hand.setRole(role);
			hand.setHomeTo(b.at(x, y, z), roam);
		});
	}

	// ── The capital's great buildings ─────────────────────────────

	/** How tall the capital's great bell tower stands to its belfry. */
	public static final int GREAT_TOWER = 46;

	/**
	 * The capital's great bell tower: a limestone shaft thirteen blocks across with a stair round its inside, banded in
	 * bronze, a belfry where the land's greatest bell hangs (five blocks at its lip) with a small bell to ring it by, and a
	 * tiled roof with a bronze spire. Returns the small bell.
	 */
	public static BlockPos greatTower(final Builder b) {
		int half = 6;
		b.clear(-half - 1, 1, -half - 1, half + 1, GREAT_TOWER + 22, half + 2);
		for (int x = -half; x <= half; x++) {
			for (int z = -half; z <= half; z++) {
				b.footing(x, z, STONE, 30);
				b.set(x, 0, z, STONE);
			}
		}
		for (int y = 1; y <= GREAT_TOWER; y++) {
			Block course = y % 8 == 0 ? BRONZE : STONE;
			for (int i = -half; i <= half; i++) {
				b.set(i, y, -half, course);
				b.set(i, y, half, course);
				b.set(-half, y, i, course);
				b.set(half, y, i, course);
			}
			if (y % 8 == 4) {
				for (int i = -1; i <= 1; i++) {
					b.set(i, y, -half, Blocks.GLASS_PANE);
					b.set(i, y, half, Blocks.GLASS_PANE);
					b.set(-half, y, i, Blocks.GLASS_PANE);
					b.set(half, y, i, Blocks.GLASS_PANE);
				}
			}
		}
		// a square stair round the inside, a step up each block, landing on the belfry
		int[][] ring = stairRing(half - 1);
		for (int y = 1; y <= GREAT_TOWER; y++) {
			int[] at = ring[(y - 1) % ring.length];
			int[] next = ring[y % ring.length];
			Direction up = Direction.getApproximateNearest(next[0] - at[0], 0, next[1] - at[1]);
			b.stairs(at[0], y, at[1], STONE_STAIRS, up, false);
			b.set(at[0], y - 1, at[1], STONE);
			b.clear(at[0], y + 1, at[1], at[0], y + 3, at[1]);
		}
		b.clear(-1, 1, half, 1, 3, half);
		b.door(0, 1, half, Blocks.DARK_OAK_DOOR, Direction.NORTH, false);
		b.facing(2, 3, half + 1, Blocks.WALL_TORCH, Direction.SOUTH);
		b.facing(-2, 3, half + 1, Blocks.WALL_TORCH, Direction.SOUTH);

		int deck = GREAT_TOWER + 1;
		b.fill(-half, deck, -half, half, deck, half, STONE);
		int[] last = ring[(GREAT_TOWER - 1) % ring.length];
		b.set(last[0], deck, last[1], Blocks.AIR);
		for (int[] c : new int[][] {{-half, -half}, {half, -half}, {-half, half}, {half, half}, {0, -half}, {0, half}, {-half, 0}, {half, 0}}) {
			for (int y = deck + 1; y <= deck + 10; y++) {
				b.axis(c[0], y, c[1], TIMBER, Direction.Axis.Y);
			}
		}
		for (int x = -half; x <= half; x++) {
			b.axis(x, deck + 11, 0, TIMBER, Direction.Axis.X);
		}
		// the great bell
		b.set(0, deck + 10, 0, BRONZE);
		b.fill(-1, deck + 8, -1, 1, deck + 9, 1, BRONZE);
		for (int y = deck + 5; y <= deck + 7; y++) {
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					if (Math.abs(x) == 2 || Math.abs(z) == 2 || y == deck + 7) {
						b.set(x, y, z, BRONZE);
					}
				}
			}
		}
		b.set(0, deck + 4, 0, Blocks.IRON_CHAIN);
		b.set(0, deck + 3, 0, BRONZE);
		BlockPos ring1 = bell(b, 0, deck + 1, 4, BellAttachType.FLOOR);
		for (int i = -half; i <= half; i++) {
			b.set(i, deck + 1, -half, i == 0 ? Blocks.AIR : STONE_WALL);
			b.set(-half, deck + 1, i, STONE_WALL);
			b.set(half, deck + 1, i, STONE_WALL);
		}
		roofPyramid(b, deck + 12, half + 1);
		b.set(0, deck + 20, 0, BRONZE);
		b.set(0, deck + 21, 0, BRONZE);
		b.set(0, deck + 22, 0, Blocks.LIGHTNING_ROD.waxed().unaffected());
		b.settle();
		return ring1;
	}

	/** The places of a stair going round the inside of a square of half-width {@code h}, clockwise from the south-west. */
	private static int[][] stairRing(final int h) {
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

	/** A hall: a long limestone building with a tiled roof, high windows and its door to the south. */
	public static void hall(final Builder b, final int hx, final int hz, final int height) {
		b.clear(-hx - 1, 1, -hz - 1, hx + 1, height + hz + 4, hz + 2);
		for (int x = -hx; x <= hx; x++) {
			for (int z = -hz; z <= hz; z++) {
				b.footing(x, z, STONE, 24);
				b.set(x, 0, z, (x + z & 1) == 0 ? Blocks.POLISHED_ANDESITE : STONE);
			}
		}
		for (int y = 1; y <= height; y++) {
			for (int x = -hx; x <= hx; x++) {
				b.set(x, y, -hz, STONE);
				b.set(x, y, hz, STONE);
			}
			for (int z = -hz; z <= hz; z++) {
				b.set(-hx, y, z, STONE);
				b.set(hx, y, z, STONE);
			}
		}
		for (int x = -hx + 2; x <= hx - 2; x += 3) {
			b.fill(x, 3, -hz, x, height - 2, -hz, Blocks.GLASS_PANE);
			b.fill(x, 3, hz, x, height - 2, hz, Blocks.GLASS_PANE);
		}
		for (int z = -hz + 2; z <= hz - 2; z += 3) {
			b.fill(-hx, 3, z, -hx, height - 2, z, Blocks.GLASS_PANE);
			b.fill(hx, 3, z, hx, height - 2, z, Blocks.GLASS_PANE);
		}
		for (int x = -hx; x <= hx; x++) {
			b.set(x, height, -hz, BRONZE);
			b.set(x, height, hz, BRONZE);
		}
		b.clear(-1, 1, hz, 1, 3, hz);
		b.door(0, 1, hz, Blocks.DARK_OAK_DOOR, Direction.SOUTH, false);
		b.door(1, 1, hz, Blocks.DARK_OAK_DOOR, Direction.SOUTH, false);
		b.door(-1, 1, hz, Blocks.DARK_OAK_DOOR, Direction.SOUTH, false);
		int y = height + 1;
		for (int i = 0; i <= hz + 1; i++) {
			int zn = -hz - 1 + i;
			int zs = hz + 1 - i;
			if (zn > zs) {
				break;
			}
			for (int x = -hx - 1; x <= hx + 1; x++) {
				if (zn == zs) {
					b.set(x, y + i, zn, TILES);
				} else {
					b.stairs(x, y + i, zn, TILE_STAIRS, Direction.SOUTH, false);
					b.stairs(x, y + i, zs, TILE_STAIRS, Direction.NORTH, false);
				}
			}
			if (i > 0) {
				for (int z = zn + 1; z < zs; z++) {
					b.set(-hx, y + i - 1, z, STONE);
					b.set(hx, y + i - 1, z, STONE);
				}
			}
		}
		for (int x = -hx + 3; x <= hx - 3; x += 4) {
			b.hanging(x, height - 1, 0, Blocks.LANTERN);
		}
		b.settle();
	}

	/** The Great Library: a hall lined with shelves and lecterns, the Chronicler's desk at its north end. */
	public static void library(final Builder b) {
		hall(b, 12, 9, 9);
		for (int x = -10; x <= 10; x++) {
			if (Math.abs(x) <= 1) {
				continue;
			}
			b.fill(x, 1, -7, x, 4, -7, Blocks.BOOKSHELF);
			b.fill(x, 1, 7, x, 4, 7, Blocks.BOOKSHELF);
		}
		for (int x = -8; x <= 8; x += 4) {
			b.fill(x, 1, -2, x, 3, 2, Blocks.BOOKSHELF);
		}
		b.facing(-2, 1, -5, Blocks.LECTERN, Direction.SOUTH);
		b.facing(2, 1, -5, Blocks.LECTERN, Direction.SOUTH);
		b.fill(-1, 1, -6, 1, 1, -6, Blocks.DARK_OAK_PLANKS);
		b.candles(-1, 2, -6, Blocks.DYED_CANDLE.white(), 3, true);
		b.set(1, 2, -6, Blocks.FLOWER_POT);
		person(b, StaffRole.CHRONICLER, 0, 1, -4, 180.0F, 3);
		person(b, StaffRole.TOWNSWOMAN, 6, 1, 4, 0.0F, 4);
	}

	/** The palace: a great hall with a dais for the Regent, banners and a guard at the door. */
	public static void palace(final Builder b) {
		hall(b, 14, 10, 11);
		b.fill(-4, 1, -8, 4, 1, -6, STONE);
		b.fill(-3, 2, -8, 3, 2, -7, Blocks.CARPET.blue());
		b.facing(0, 2, -8, Blocks.SPRUCE_STAIRS, Direction.NORTH);
		for (int x = -12; x <= 12; x += 6) {
			b.facing(x, 6, -9, Blocks.WALL_BANNER.blue(), Direction.SOUTH);
			b.facing(x, 6, 9, Blocks.WALL_BANNER.blue(), Direction.NORTH);
		}
		for (int z = -6; z <= 8; z++) {
			b.set(0, 1, z, Blocks.CARPET.blue());
		}
		person(b, StaffRole.REGENT, 0, 2, -7, 0.0F, 2);
		person(b, StaffRole.CAPITAL_GUARD, 3, 1, 8, 0.0F, 2);
		person(b, StaffRole.CAPITAL_GUARD, -3, 1, 8, 0.0F, 2);
	}

	/** 森茶館: a small tea house, its name on a sign, and a little mark by the door for those who look. */
	public static void teaHouse(final Builder b) {
		house(b, Style.CAPITAL, 4, 3, 1);
		b.set(-2, 1, 0, Blocks.SPRUCE_FENCE);
		b.set(-2, 2, 0, Blocks.SPRUCE_PRESSURE_PLATE);
		b.set(2, 1, 0, Blocks.SPRUCE_FENCE);
		b.set(2, 2, 0, Blocks.FLOWER_POT);
		b.set(0, 1, -2, Blocks.CAULDRON);
		b.facing(-1, 1, 1, Blocks.SPRUCE_STAIRS, Direction.EAST);
		b.facing(1, 1, 1, Blocks.SPRUCE_STAIRS, Direction.WEST);
		b.sign(-1, 3, 4, Blocks.SPRUCE_WALL_SIGN, Direction.SOUTH, text("sign.mistinn.udasru.tea.0", "sign.mistinn.udasru.tea.1", "sign.mistinn.udasru.tea.2", ""));
		b.sign(3, 2, 4, Blocks.SPRUCE_WALL_SIGN, Direction.SOUTH, text("", "sign.mistinn.udasru.tea_mark", "", ""));
		person(b, StaffRole.TOWNSWOMAN, 0, 1, 0, 180.0F, 2);
	}

	/** 未歸者之碑: a garden of stelae, each with numbers of hunters who never went home and the names they took here. */
	public static void stelae(final Builder b) {
		b.clear(-8, 1, -8, 8, 6, 8);
		for (int x = -8; x <= 8; x++) {
			for (int z = -8; z <= 8; z++) {
				b.footing(x, z, Blocks.DIRT, 12);
				b.set(x, 0, z, (Math.abs(x) <= 1 || Math.abs(z) <= 1) ? Blocks.GRAVEL : Blocks.GRASS_BLOCK);
			}
		}
		int n = 0;
		for (int[] at : new int[][] {{-5, -5}, {0, -6}, {5, -5}, {-6, 0}, {6, 0}, {-5, 5}, {5, 5}}) {
			b.set(at[0], 1, at[1], STONE);
			b.set(at[0], 2, at[1], STONE);
			b.set(at[0], 3, at[1], STONE_SLAB);
			Direction face = Direction.getApproximateNearest(-at[0], 0, -at[1]);
			b.sign(at[0] + face.getStepX(), 2, at[1] + face.getStepZ(), Blocks.SPRUCE_WALL_SIGN, face,
				text("sign.mistinn.udasru.stele." + n + ".0", "sign.mistinn.udasru.stele." + n + ".1", "sign.mistinn.udasru.stele." + n + ".2", ""));
			b.set(at[0] - face.getStepX(), 1, at[1] - face.getStepZ(), Blocks.POPPY);
			n++;
		}
		b.set(0, 1, 0, Blocks.LANTERN);
		b.settle();
	}

	/** A market stall: a counter under a striped awning. */
	public static void stall(final Builder b, final Block cloth) {
		b.clear(-2, 1, -2, 2, 4, 2);
		for (int x = -2; x <= 2; x++) {
			b.set(x, 1, -1, Blocks.SPRUCE_SLAB);
			for (int z = -2; z <= 2; z++) {
				b.footing(x, z, STONE, 8);
				b.set(x, 4, z, (x & 1) == 0 ? cloth : Blocks.WOOL.white());
			}
		}
		for (int[] c : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
			b.set(c[0], 1, c[1], Blocks.SPRUCE_FENCE);
			b.set(c[0], 2, c[1], Blocks.SPRUCE_FENCE);
			b.set(c[0], 3, c[1], Blocks.SPRUCE_FENCE);
		}
		b.set(0, 2, -1, Blocks.BARREL);
		b.settle();
	}

	/** A sign's four lines from translation keys (an empty key leaves the line blank). */
	public static SignText text(final String a, final String b, final String c, final String d) {
		return new SignText(java.util.List.of(line(a), line(b), line(c), line(d)), java.util.List.of(line(a), line(b), line(c), line(d)),
			net.minecraft.world.item.DyeColor.BLACK, false);
	}

	private static Component line(final String key) {
		return key.isEmpty() ? Component.empty() : Component.translatable(key);
	}
}
