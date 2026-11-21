package life.shizune.mistinn.training;

import static life.shizune.mistinn.training.StationParts.BEAM;
import static life.shizune.mistinn.training.StationParts.BOARD;
import static life.shizune.mistinn.training.StationParts.FRAME;
import static life.shizune.mistinn.training.StationParts.GRATE;
import static life.shizune.mistinn.training.StationParts.HULL;
import static life.shizune.mistinn.training.StationParts.PLANK;
import static life.shizune.mistinn.training.StationParts.POST;
import static life.shizune.mistinn.training.StationParts.RAIL;
import static life.shizune.mistinn.training.StationParts.RUST;
import static life.shizune.mistinn.training.StationParts.STONE;
import static life.shizune.mistinn.training.StationParts.TEAL_SLAB;
import static life.shizune.mistinn.training.StationParts.TEAL_STAIRS;
import static life.shizune.mistinn.training.StationParts.WARM_STAIRS;

import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.story.RadioBlock;
import life.shizune.mistinn.world.Builder;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Rust Anchor Station, built round the great anchor in its basin. Three heights, warm to cold:
 * the docks and barracks at the waterline (y 64), two arms of departments on the middle deck
 * (y 76), and the drop crane alone at the top (y 90). Coordinates are world x and z; heights are
 * above y 64.
 */
final class StationMap {
	static final int LOW = 0;
	static final int MID = 12;
	static final int HIGH = 26;
	private static final int[] SIDES = {-1, 1};

	private StationMap() {
	}

	static void build(final Builder b) {
		docks(b);
		barracks(b);
		store(b);
		warehouse(b);
		jetty(b);
		climb(b);
		mess(b);
		forge(b);
		drillYard(b);
		pen(b);
		infirmary(b);
		workshop(b);
		archive(b);
		bellCourt(b);
		radioRoom(b);
		lampHall(b);
		launch(b);
		reefs(b);
		GreatAnchor.build(b);
		b.settle();
	}

	// ---- the waterline: docks, barracks, the quartermaster's store, the warehouse ----

	private static void docks(final Builder b) {
		StationParts.deck(b, -32, 0, 20, 28, LOW, BOARD, false);
		// dark plank lanes where the carts run
		for (int z : new int[] {9, 10, 18, 19}) {
			b.fill(-31, LOW, z, 19, LOW, z, PLANK);
		}
		// the landing under the stair up to the market
		StationParts.deck(b, -8, -8, -3, -1, LOW, PLANK, true);
		StationParts.rail(b, -3, -8, -3, -1, LOW);
		StationParts.gap(b, -8, 0, -3, 0, LOW);
		for (int x : new int[] {-30, -2, 14}) {
			StationParts.lampPost(b, x, 27, LOW, false);
		}
		for (int x : new int[] {-6, 2, 10}) {
			StationParts.lampPost(b, x, 14, LOW, true);
		}
		StationParts.crates(b, -4, 22, LOW, 6);
		StationParts.crates(b, 2, 24, LOW, 4);
		StationParts.crates(b, -9, 3, LOW, 5);
		StationParts.crates(b, 16, 2, LOW, 7);
		// a davit over the east edge with a load hanging over the water
		b.fill(20, LOW + 1, 14, 20, LOW + 6, 14, POST);
		b.fill(21, LOW + 6, 14, 23, LOW + 6, 14, POST);
		b.axis(23, LOW + 5, 14, Blocks.IRON_CHAIN, Direction.Axis.Y);
		b.axis(23, LOW + 4, 14, Blocks.IRON_CHAIN, Direction.Axis.Y);
		b.set(23, LOW + 3, 14, Blocks.BARREL);
	}

	private static void barracks(final Builder b) {
		StationParts.shed(b, -30, 6, -12, 26, LOW, 5, BOARD, Blocks.STAINED_GLASS.orange(), WARM_STAIRS, PLANK);
		StationParts.door(b, -12, 16, LOW, false);
		// bunks along both long walls, lockers between them
		for (int x = -28; x <= -16; x += 3) {
			b.bed(x, LOW + 1, 7, Blocks.BED.gray(), Direction.NORTH);
			b.bed(x, LOW + 1, 25, Blocks.BED.gray(), Direction.SOUTH);
			b.set(x + 1, LOW + 1, 7, Blocks.BARREL);
			b.set(x + 1, LOW + 1, 25, Blocks.BARREL);
		}
		// a long table down the middle, candles on it
		for (int x = -26; x <= -17; x++) {
			b.slab(x, LOW + 1, 16, Blocks.SPRUCE_SLAB, true);
			if (x % 3 == 0) {
				b.candles(x, LOW + 2, 16, Blocks.CANDLE, 2, true);
			}
		}
		b.facing(-29, LOW + 1, 16, Blocks.SMOKER, Direction.EAST);
		StationParts.chimney(b, -29, 16, LOW + 2, LOW + 10);
		b.set(-29, LOW + 2, 16, Blocks.BRICKS);
	}

	/** The quartermaster's store: walls on three sides, a counter across the open front. */
	private static void store(final Builder b) {
		StationParts.shed(b, 0, 0, 11, 6, LOW, 5, BOARD, Blocks.STAINED_GLASS.orange(), WARM_STAIRS, PLANK);
		b.clear(1, LOW + 1, 6, 10, LOW + 4, 6);
		for (int x = 1; x <= 10; x++) {
			if (x != 7) {
				b.set(x, LOW + 1, 6, x == 3 ? BOARD : Blocks.BARREL);
			}
		}
		b.facing(7, LOW + 1, 6, Blocks.CHEST, Direction.SOUTH);
		// shelves behind the counter
		for (int x = 1; x <= 10; x++) {
			b.set(x, LOW + 1, 1, Blocks.BARREL);
			b.set(x, LOW + 2, 1, x % 2 == 0 ? Blocks.BARREL : Blocks.CHISELED_BOOKSHELF);
		}
		b.facing(1, LOW + 1, 3, Blocks.SMITHING_TABLE, Direction.EAST);
		b.set(10, LOW + 1, 3, Blocks.CRAFTING_TABLE);
	}

	private static void warehouse(final Builder b) {
		StationParts.shed(b, 6, 12, 19, 27, LOW, 7, Blocks.STRIPPED_SPRUCE_WOOD, Blocks.STAINED_GLASS.yellow(), WARM_STAIRS, PLANK);
		// the cargo door: the whole west wall stands open
		b.clear(6, LOW + 1, 13, 6, LOW + 6, 26);
		for (int z = 14; z <= 25; z += 4) {
			StationParts.crates(b, 10, z, LOW, 8);
			StationParts.crates(b, 15, z, LOW, 6);
		}
	}

	/** A jetty north into the basin, the first place the anchor can be seen whole. */
	private static void jetty(final Builder b) {
		StationParts.deck(b, 13, -13, 17, -1, LOW, PLANK, false);
		StationParts.gap(b, 13, -1, 17, 0, LOW);
		StationParts.lampPost(b, 15, -13, LOW, true);
		b.set(13, LOW + 1, -10, Blocks.BARREL);
	}

	/** The stair from the landing up to the market, twelve steps west. */
	private static void climb(final Builder b) {
		StationParts.flight(b, -7, -4, LOW, Direction.WEST, 3, 12);
	}

	// ---- the middle deck, west arm: market and mess, forge, drill yard ----

	private static void mess(final Builder b) {
		StationParts.deck(b, -46, -32, -20, -1, MID, Blocks.SPRUCE_PLANKS, false);
		StationParts.gap(b, -20, -6, -20, -4, MID);
		StationParts.shed(b, -44, -28, -30, -14, MID, 5, BOARD, Blocks.STAINED_GLASS.orange(), WARM_STAIRS, PLANK);
		StationParts.door(b, -30, -21, MID, false);
		StationParts.door(b, -37, -14, MID, true);
		StationParts.chimney(b, -42, -26, MID + 6, MID + 14);
		// tables and benches in the hall
		for (int z = -25; z <= -17; z += 4) {
			for (int x = -41; x <= -33; x++) {
				b.slab(x, MID + 1, z, Blocks.SPRUCE_SLAB, true);
			}
			for (int x = -41; x <= -33; x += 2) {
				b.stairs(x, MID + 1, z - 1, Blocks.SPRUCE_STAIRS, Direction.NORTH, false);
				b.stairs(x, MID + 1, z + 1, Blocks.SPRUCE_STAIRS, Direction.SOUTH, false);
			}
		}
		b.facing(-43, MID + 1, -20, Blocks.SMOKER, Direction.EAST);
		b.facing(-43, MID + 1, -22, Blocks.SMOKER, Direction.EAST);
		b.set(-43, MID + 1, -24, Blocks.CAULDRON);
		// market stalls under coloured canopies along the east side
		stall(b, -26, -28, Blocks.WOOL.red());
		stall(b, -26, -20, Blocks.WOOL.yellow());
		stall(b, -26, -12, Blocks.WOOL.brown());
		// the notice board by the stair head
		b.fill(-24, MID + 1, -4, -21, MID + 3, -4, Blocks.SPRUCE_PLANKS);
		b.fill(-24, MID + 4, -4, -21, MID + 4, -4, Blocks.SPRUCE_SLAB);
		for (int x = -24; x <= -21; x++) {
			b.facing(x, MID + 2, -3, Blocks.SPRUCE_TRAPDOOR, Direction.SOUTH);
		}
		StationParts.lampPost(b, -33, -6, MID, false);
		StationParts.lampPost(b, -45, -10, MID, false);
	}

	private static void stall(final Builder b, final int x, final int z, final Block canopy) {
		for (int dx : new int[] {0, 4}) {
			for (int dz : new int[] {0, 4}) {
				b.fill(x + dx, MID + 1, z + dz, x + dx, MID + 3, z + dz, Blocks.SPRUCE_FENCE);
			}
		}
		b.fill(x, MID + 4, z, x + 4, MID + 4, z + 4, canopy);
		b.fill(x + 1, MID + 1, z, x + 3, MID + 1, z, Blocks.BARREL);
		b.set(x + 2, MID + 2, z, Blocks.LANTERN);
		b.hanging(x + 2, MID + 3, z + 2, Blocks.LANTERN);
	}

	private static void forge(final Builder b) {
		StationParts.deck(b, -62, -60, -38, -37, MID, Blocks.POLISHED_DEEPSLATE, false);
		StationParts.gap(b, -38, -52, -38, -46, MID);
		StationParts.walk(b, -44, -36, -40, -33, MID);
		StationParts.gap(b, -44, -37, -40, -37, MID);
		StationParts.gap(b, -44, -32, -40, -32, MID);
		StationParts.shed(b, -60, -58, -46, -42, MID, 6, STONE, Blocks.STAINED_GLASS.orange(), Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES);
		StationParts.door(b, -46, -50, MID, false);
		StationParts.door(b, -53, -42, MID, true);
		// the hearth: blast furnaces round a lava cauldron, the chimney straight up through the roof
		b.fill(-58, MID + 1, -52, -56, MID + 1, -48, Blocks.BLAST_FURNACE);
		b.set(-57, MID + 1, -50, Blocks.LAVA_CAULDRON);
		b.fill(-58, MID + 2, -52, -56, MID + 3, -48, Blocks.BRICKS);
		b.set(-57, MID + 2, -50, Blocks.AIR);
		StationParts.chimney(b, -57, -50, MID + 4, MID + 16);
		b.set(-52, MID + 1, -46, Blocks.ANVIL);
		b.set(-52, MID + 1, -54, Blocks.ANVIL);
		b.set(-49, MID + 1, -55, Blocks.GRINDSTONE);
		b.set(-49, MID + 1, -45, Blocks.SMITHING_TABLE);
		// the weapon wall
		for (int x = -59; x <= -47; x += 2) {
			b.facing(x, MID + 3, -57, Blocks.SPRUCE_TRAPDOOR, Direction.SOUTH);
		}
	}

	private static void drillYard(final Builder b) {
		StationParts.deck(b, -37, -68, -9, -38, MID, Blocks.SMOOTH_STONE, false);
		StationParts.gap(b, -37, -52, -37, -46, MID);
		StationParts.walk(b, -28, -37, -24, -33, MID);
		StationParts.gap(b, -28, -38, -24, -38, MID);
		StationParts.gap(b, -28, -32, -24, -32, MID);
		// the run lane, marked in rust
		for (int z = -66; z <= -40; z++) {
			b.set(-34, MID, z, Blocks.POLISHED_TUFF);
			b.set(-30, MID, z, Blocks.POLISHED_TUFF);
			if (z % 4 == 0) {
				b.fill(-33, MID, z, -31, MID, z, RUST);
			}
		}
		// the sparring ring
		ringFloor(b, -21, -56, 7, RUST, Blocks.POLISHED_TUFF, MID);
		ringFloor(b, -21, -56, 2, Blocks.CHISELED_TUFF, Blocks.SMOOTH_STONE, MID);
		// the stand on the north side
		for (int step = 0; step < 3; step++) {
			int z = -67 + step;
			for (int x = -27; x <= -15; x++) {
				b.stairs(x, MID + 1 + (2 - step), z, Blocks.SPRUCE_STAIRS, Direction.NORTH, false);
				b.fill(x, MID + 1, z, x, MID + (2 - step), z, Blocks.SPRUCE_PLANKS);
			}
		}
		for (int x : new int[] {-27, -21, -15}) {
			b.fill(x, MID + 4, -68, x, MID + 7, -68, POST);
		}
		b.fill(-27, MID + 8, -68, -15, MID + 8, -65, TEAL_SLAB);
		for (int z : new int[] {-62, -50}) {
			StationParts.lampPost(b, -10, z, MID, true);
			StationParts.lampPost(b, -36, z, MID, true);
		}
	}

	private static void ringFloor(final Builder b, final int cx, final int cz, final int radius, final Block band, final Block inner, final int y) {
		for (int dz = -radius; dz <= radius; dz++) {
			for (int dx = -radius; dx <= radius; dx++) {
				int d = dx * dx + dz * dz;
				if (d <= radius * radius) {
					b.set(cx + dx, y, cz + dz, d > (radius - 1) * (radius - 1) ? band : inner);
				}
			}
		}
	}

	// ---- the north span: the listening pen ----

	private static void pen(final Builder b) {
		StationParts.deck(b, -5, -76, 25, -50, MID, Blocks.DEEPSLATE_TILES, false);
		StationParts.walk(b, -8, -62, -6, -58, MID);
		StationParts.gap(b, -9, -62, -9, -58, MID);
		StationParts.gap(b, -5, -62, -5, -58, MID);
		StationParts.gap(b, 25, -53, 25, -51, MID);
		// grates in the floor: the anchor's fluke lies just under them
		b.fill(1, MID, -64, 9, MID, -56, GRATE);
		// three cages along the north wall
		for (int x0 : new int[] {-2, 6, 14}) {
			b.fill(x0, MID + 1, -75, x0 + 6, MID + 1, -75, STONE);
			for (int x = x0; x <= x0 + 6; x++) {
				b.fill(x, MID + 1, -70, x, MID + 4, -70, RAIL);
			}
			for (int z = -75; z <= -70; z++) {
				b.fill(x0, MID + 1, z, x0, MID + 4, z, RAIL);
				b.fill(x0 + 6, MID + 1, z, x0 + 6, MID + 4, z, RAIL);
			}
			b.fill(x0, MID + 5, -75, x0 + 6, MID + 5, -70, FRAME);
			b.set(x0 + 3, MID + 4, -70, FRAME);
		}
		// only two lamps: the pen is kept dim
		StationParts.lampPost(b, -4, -51, MID, false);
		StationParts.lampPost(b, 24, -75, MID, false);
	}

	// ---- the middle deck, east arm: infirmary, workshop, archive ----

	private static void infirmary(final Builder b) {
		StationParts.deck(b, 26, -53, 50, -29, MID, Blocks.SMOOTH_QUARTZ, false);
		StationParts.gap(b, 26, -53, 26, -51, MID);
		StationParts.shed(b, 28, -51, 48, -31, MID, 5, Blocks.CALCITE, Blocks.STAINED_GLASS.lightBlue(), Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ);
		StationParts.door(b, 28, -41, MID, false);
		StationParts.door(b, 45, -31, MID, true);
		StationParts.door(b, 42, -51, MID, true);
		b.fill(36, MID, -43, 40, MID, -39, Blocks.DYED_TERRACOTTA.cyan());
		b.fill(38, MID, -47, 38, MID, -35, Blocks.DYED_TERRACOTTA.cyan());
		for (int z : new int[] {-49, -46, -36, -33}) {
			b.bed(47, MID + 1, z, Blocks.BED.white(), Direction.EAST);
			b.bed(29, MID + 1, z, Blocks.BED.white(), Direction.WEST);
		}
		b.set(32, MID + 1, -50, Blocks.BREWING_STAND);
		b.fill(34, MID + 1, -50, 37, MID + 1, -50, Blocks.BARREL);
		b.fill(34, MID + 2, -50, 37, MID + 2, -50, Blocks.SMOOTH_QUARTZ_SLAB);
	}

	private static void workshop(final Builder b) {
		StationParts.deck(b, 38, -24, 56, 3, MID, Blocks.POLISHED_ANDESITE, false);
		StationParts.walk(b, 43, -28, 47, -25, MID);
		StationParts.gap(b, 43, -29, 47, -29, MID);
		StationParts.gap(b, 43, -24, 47, -24, MID);
		StationParts.shed(b, 40, -22, 54, -4, MID, 5, Blocks.TUFF_BRICKS, Blocks.STAINED_GLASS.yellow(), TEAL_STAIRS, HULL);
		StationParts.door(b, 45, -22, MID, true);
		StationParts.door(b, 40, -8, MID, false);
		b.set(48, MID + 1, -13, Blocks.CRAFTING_TABLE);
		b.facing(48, MID + 1, -15, Blocks.SMITHING_TABLE, Direction.WEST);
		b.set(48, MID + 1, -11, Blocks.LOOM);
		b.fill(52, MID + 1, -20, 53, MID + 1, -6, Blocks.BARREL);
		b.fill(52, MID + 2, -20, 53, MID + 2, -6, Blocks.SPRUCE_SLAB);
		// the service stair down to the docks
		StationParts.deck(b, 21, 1, 25, 5, LOW, PLANK, true);
		StationParts.rail(b, 21, 5, 25, 5, LOW);
		StationParts.gap(b, 20, 1, 20, 3, LOW);
		StationParts.flight(b, 25, 1, LOW, Direction.EAST, 3, 12);
		StationParts.gap(b, 38, 1, 38, 3, MID);
	}

	private static void archive(final Builder b) {
		StationParts.deck(b, 32, -82, 54, -60, MID, Blocks.DARK_OAK_PLANKS, false);
		StationParts.walk(b, 40, -59, 44, -54, MID);
		StationParts.gap(b, 40, -60, 44, -60, MID);
		StationParts.gap(b, 40, -53, 44, -53, MID);
		StationParts.shed(b, 34, -80, 52, -62, MID, 7, STONE, Blocks.STAINED_GLASS.brown(), WARM_STAIRS, PLANK);
		StationParts.door(b, 42, -62, MID, true);
		StationParts.door(b, 34, -71, MID, false);
		for (int z = -78; z <= -64; z += 3) {
			b.fill(36, MID + 1, z, 39, MID + 4, z, Blocks.BOOKSHELF);
			b.fill(47, MID + 1, z, 50, MID + 4, z, Blocks.CHISELED_BOOKSHELF);
		}
		b.facing(43, MID + 1, -74, Blocks.LECTERN, Direction.SOUTH);
		b.set(43, MID + 1, -68, Blocks.CARTOGRAPHY_TABLE);
		b.candles(44, MID + 2, -68, Blocks.CANDLE, 3, true);
	}

	// ---- the north span: bell court and memorial wall, radio room ----

	private static void bellCourt(final Builder b) {
		StationParts.deck(b, 0, -104, 24, -80, MID, Blocks.POLISHED_DEEPSLATE, false);
		StationParts.walk(b, 25, -92, 31, -88, MID);
		StationParts.gap(b, 24, -92, 24, -88, MID);
		StationParts.gap(b, 32, -74, 32, -70, MID);
		ringFloor(b, 12, -88, 5, Blocks.GOLD_BLOCK, Blocks.POLISHED_DEEPSLATE, MID);
		ringFloor(b, 12, -88, 2, RUST, Blocks.CHISELED_TUFF_BRICKS, MID);
		b.set(12, MID + 1, -88, MistinnBlocks.GOLDEN_BELL);
		// the drill platform where a hunter falls on purpose
		ringFloor(b, 12, -98, 3, Blocks.CUT_SANDSTONE, Blocks.POLISHED_TUFF, MID);
		b.set(12, MID, -98, Blocks.GOLD_BLOCK);
		for (int side : SIDES) {
			b.set(12 + side * 4, MID + 1, -98, Blocks.SOUL_LANTERN);
		}
		// the memorial wall along the west edge: row on row of numbered plates, candles below
		b.fill(1, MID + 1, -102, 2, MID + 6, -82, STONE);
		for (int z = -101; z <= -83; z++) {
			for (int y = MID + 2; y <= MID + 5; y++) {
				b.set(3, y, z, (z + y) % 2 == 0 ? Blocks.CHISELED_DEEPSLATE : Blocks.POLISHED_DEEPSLATE);
			}
			if (z % 2 == 0) {
				b.candles(3, MID + 1, z, Blocks.CANDLE, 1 + Math.floorMod(z, 3), true);
			}
		}
		b.fill(1, MID + 7, -102, 3, MID + 7, -82, Blocks.DEEPSLATE_TILE_SLAB);
		// the bell tower in the north-east corner
		b.fill(17, MID + 1, -103, 23, MID + 1, -97, STONE);
		for (int x : new int[] {17, 23}) {
			for (int z : new int[] {-103, -97}) {
				b.fill(x, MID + 2, z, x, MID + 24, z, BEAM);
			}
		}
		b.fill(17, MID + 18, -103, 23, MID + 18, -97, FRAME);
		b.fill(17, MID + 25, -103, 23, MID + 25, -97, HULL);
		b.fill(18, MID + 26, -102, 22, MID + 26, -98, HULL);
		b.fill(19, MID + 27, -101, 21, MID + 27, -99, GRATE);
		b.set(20, MID + 23, -100, Blocks.BELL);
		b.facing(20, MID + 28, -100, Blocks.LIGHTNING_ROD.waxed().oxidized(), Direction.UP);
		for (int y = MID + 2; y <= MID + 17; y++) {
			b.set(20, y, -97, y % 3 == 0 ? Blocks.STAINED_GLASS.cyan() : STONE);
		}
	}

	private static void radioRoom(final Builder b) {
		StationParts.deck(b, -28, -96, -4, -72, MID, Blocks.POLISHED_ANDESITE, false);
		StationParts.walk(b, -3, -90, -1, -86, MID);
		StationParts.gap(b, -4, -90, -4, -86, MID);
		StationParts.gap(b, 0, -90, 0, -86, MID);
		StationParts.shed(b, -26, -94, -8, -78, MID, 5, Blocks.TUFF_BRICKS, Blocks.STAINED_GLASS.cyan(), TEAL_STAIRS, HULL);
		StationParts.door(b, -8, -86, MID, false);
		StationParts.door(b, -26, -82, MID, false);
		b.fill(-19, MID, -87, -15, MID, -85, RUST);
		b.set(-17, MID + 1, -86, MistinnBlocks.RADIO.defaultBlockState().setValue(RadioBlock.FACING, Direction.SOUTH));
		for (int x = -24; x <= -10; x += 2) {
			b.set(x, MID + 1, -93, Blocks.BARREL);
			b.set(x, MID + 2, -93, GRATE);
		}
		// the antenna mast
		b.fill(-17, MID + 8, -86, -17, MID + 22, -86, FRAME);
		for (int y = MID + 10; y <= MID + 20; y += 5) {
			b.fill(-19, y, -86, -15, y, -86, RAIL);
			b.set(-20, y, -86, StationParts.BULB.defaultBlockState().setValue(BlockStateProperties.LIT, true));
			b.set(-14, y, -86, StationParts.BULB.defaultBlockState().setValue(BlockStateProperties.LIT, true));
		}
		b.facing(-17, MID + 23, -86, Blocks.LIGHTNING_ROD.waxed().oxidized(), Direction.UP);
	}

	// ---- the lamp hall, the exam ----

	private static void lampHall(final Builder b) {
		StationParts.deck(b, -62, -96, -34, -66, MID, Blocks.POLISHED_BLACKSTONE_BRICKS, false);
		StationParts.walk(b, -33, -84, -29, -80, MID);
		StationParts.gap(b, -34, -84, -34, -80, MID);
		StationParts.gap(b, -28, -84, -28, -80, MID);
		StationParts.shed(b, -60, -94, -36, -68, MID, 9, STONE, Blocks.STAINED_GLASS.gray(), Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES);
		StationParts.door(b, -36, -82, MID, false);
		// the north gate onto the cold stair
		b.clear(-47, MID + 1, -94, -45, MID + 4, -94);
		StationParts.gap(b, -47, -96, -45, -96, MID);
		ringFloor(b, -48, -81, 8, StationParts.FRAME, Blocks.POLISHED_BLACKSTONE_BRICKS, MID);
		b.set(-48, MID, -81, Blocks.CHISELED_POLISHED_BLACKSTONE);
		for (int dx : SIDES) {
			for (int dz : SIDES) {
				b.set(-48 + dx * 7, MID, -81 + dz * 7, Blocks.CHISELED_TUFF);
				b.set(-48 + dx * 7, MID + 1, -81 + dz * 7, MistinnBlocks.LAMP_STAND);
			}
		}
	}

	// ---- the top: the drop crane and its pod ----

	private static void launch(final Builder b) {
		StationParts.flight(b, -47, -96, MID, Direction.NORTH, 3, 14);
		StationParts.deck(b, -47, -115, -45, -111, HIGH, GRATE, true);
		StationParts.rail(b, -47, -115, -47, -111, HIGH);
		StationParts.rail(b, -47, -115, -45, -115, HIGH);
		StationParts.deck(b, -44, -124, -18, -104, HIGH, HULL, false);
		b.fill(-43, HIGH, -123, -19, HIGH, -105, GRATE);
		StationParts.gap(b, -44, -114, -44, -111, HIGH);
		StationParts.gap(b, -30, -124, -24, -124, HIGH);
		// the report console
		b.fill(-34, HIGH + 1, -109, -32, HIGH + 1, -109, HULL);
		b.set(-33, HIGH + 1, -110, MistinnBlocks.RADIO.defaultBlockState().setValue(RadioBlock.FACING, Direction.SOUTH).setValue(RadioBlock.LIT, true));
		// the mast, the boom out over the fog, the counterweight
		b.fill(-28, HIGH + 1, -119, -27, HIGH + 26, -118, BEAM);
		for (int y = HIGH + 4; y <= HIGH + 24; y += 5) {
			b.fill(-29, y, -120, -26, y, -117, FRAME);
		}
		b.fill(-28, HIGH + 24, -140, -26, HIGH + 25, -113, BEAM);
		for (int z = -139; z <= -114; z += 3) {
			b.set(-29, HIGH + 25, z, RAIL);
			b.set(-25, HIGH + 25, z, RAIL);
		}
		b.fill(-29, HIGH + 20, -114, -25, HIGH + 23, -111, STONE);
		pod(b, -27, HIGH);
		// the one light up here, and the cyan lamp at the gate
		b.set(-27, HIGH + 1, -123, Blocks.SOUL_LANTERN);
		b.set(-31, HIGH + 1, -123, FRAME);
		b.set(-23, HIGH + 1, -123, FRAME);
	}

	/** The drop pod, hanging from the boom at the deck's edge with its door open to the deck. */
	private static void pod(final Builder b, final int cx, final int y) {
		b.fill(cx - 3, y, -133, cx + 3, y, -125, HULL);
		b.fill(cx - 1, y, -131, cx + 1, y, -125, GRATE);
		b.fill(cx - 2, y + 1, -132, cx + 2, y + 3, -132, BEAM);
		b.fill(cx - 1, y + 4, -132, cx + 1, y + 4, -126, HULL);
		b.fill(cx - 1, y + 5, -131, cx + 1, y + 5, -127, FRAME);
		b.fill(cx - 1, y + 3, -126, cx + 1, y + 3, -126, RUST);
		b.set(cx, y + 2, -132, Blocks.GOLD_BLOCK);
		b.facing(cx, y + 2, -131, Blocks.POLISHED_BLACKSTONE_BUTTON, Direction.SOUTH);
		b.set(cx, y + 4, -129, Blocks.SEA_LANTERN);
		for (int side : SIDES) {
			b.fill(cx + side * 2, y + 1, -132, cx + side * 2, y + 3, -126, HULL);
			b.fill(cx + side * 2, y + 2, -131, cx + side * 2, y + 2, -127, Blocks.STAINED_GLASS.cyan());
			b.fill(cx + side * 2, y + 4, -131, cx + side * 2, y + 4, -127, FRAME);
			b.fill(cx + side * 3, y + 1, -131, cx + side * 3, y + 1, -127, RUST);
			b.fill(cx + side * 3, y + 2, -130, cx + side * 3, y + 2, -128, GRATE);
			b.fill(cx + side * 3, y + 6, -132, cx + side * 3, y + 6, -128, Blocks.IRON_BLOCK);
			// the hanging chains up to the boom
			for (int h = y + 7; h < y + 24; h++) {
				b.axis(cx + side * 2, h, -129, Blocks.IRON_CHAIN, Direction.Axis.Y);
			}
		}
		b.fill(cx - 3, y + 6, -132, cx + 3, y + 6, -128, BEAM);
	}

	/** Rocks out in the water, dark against the fog. */
	private static void reefs(final Builder b) {
		reef(b, -82, -40, 7, 16, 9);
		reef(b, 78, -60, 6, 14, 7);
		reef(b, -70, -140, 9, 18, 20);
		reef(b, 70, -130, 8, 14, 12);
		reef(b, -60, 40, 6, 10, 5);
		reef(b, 50, 40, 5, 9, 4);
	}

	private static void reef(final Builder b, final int x, final int z, final int width, final int length, final int height) {
		for (int dx = -width; dx <= width; dx++) {
			for (int dz = -length; dz <= length; dz++) {
				double d = Math.pow(dx / (double) width, 2) + Math.pow(dz / (double) length, 2);
				if (d >= 1) {
					continue;
				}
				int top = (int) Math.round(height * (1 - d)) - b.random().nextInt(3) + StationParts.WATERLINE;
				b.fill(x + dx, StationParts.SEA_FLOOR, z + dz, x + dx, top - 1, z + dz, b.pick(Blocks.TUFF, Blocks.BASALT, Blocks.DEEPSLATE));
				b.set(x + dx, top, z + dz, b.pick(Blocks.TUFF, Blocks.POLISHED_TUFF, Blocks.COBBLED_DEEPSLATE));
			}
		}
	}
}
