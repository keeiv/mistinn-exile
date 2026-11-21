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
import static life.shizune.mistinn.training.StationParts.WARM_STAIRS;

import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.story.RadioBlock;
import life.shizune.mistinn.world.Builder;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Rust Anchor Station, built round the great anchor in its basin. Three heights, warm to cold:
 * the docks and barracks at the waterline (y 64), two arms of departments on the middle deck
 * (y 76), and the drop crane alone at the top (y 90). Coordinates are world x and z; heights are
 * above y 64. The buildings are told apart by their roofs: steep dark gables at the docks, a
 * two-storey hall with a hip roof at the market, shallow copper pitches on the east arm.
 */
final class StationMap {
	static final int LOW = 0;
	static final int MID = 12;
	static final int HIGH = 26;
	private static final int[] SIDES = {-1, 1};
	private static final Block COPPER = Blocks.CUT_COPPER.waxed().oxidized();
	private static final Block WEATHERED = Blocks.CUT_COPPER.waxed().weathered();
	private static final Block WEATHERED_SLAB = Blocks.CUT_COPPER_SLAB.waxed().weathered();

	private StationMap() {
	}

	static void build(final Builder b) {
		docks(b);
		barracks(b);
		store(b);
		warehouse(b);
		jetty(b);
		harbour(b);
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
		// a string of lanterns over the loading space, and the barracks' washing
		StationParts.lanternString(b, -10, 4, 12, LOW + 5, new int[] {-8, -5, -1, 2});
		b.fill(-28, LOW + 1, 4, -28, LOW + 5, 4, POST);
		b.fill(-15, LOW + 1, 4, -15, LOW + 5, 4, POST);
		for (int x = -27; x <= -16; x++) {
			b.axis(x, LOW + 5, 4, Blocks.IRON_CHAIN, Direction.Axis.X);
		}
		b.fill(-26, LOW + 4, 4, -25, LOW + 4, 4, Blocks.WOOL.white());
		b.fill(-22, LOW + 4, 4, -21, LOW + 4, 4, Blocks.WOOL.red());
		b.fill(-18, LOW + 4, 4, -17, LOW + 4, 4, Blocks.WOOL.brown());
		b.set(-22, LOW + 3, 4, Blocks.WOOL.red());
		for (int x : new int[] {-30, 14}) {
			StationParts.lampPost(b, x, 27, LOW, false);
		}
		// cargo in small stacks with room to pass, one tall
		StationParts.crates(b, -5, 21, LOW, 4);
		StationParts.crates(b, -2, 23, LOW, 3);
		StationParts.crates(b, 1, 23, LOW, 8);
		StationParts.crates(b, -9, 3, LOW, 4);
		StationParts.crates(b, 16, 2, LOW, 5);
		b.set(19, LOW + 1, 13, Blocks.GRINDSTONE);
		b.facing(19, LOW + 1, 15, Blocks.SPRUCE_TRAPDOOR, Direction.WEST);
		// the davit over the east edge, its load hanging over the freight skiff
		b.fill(20, LOW + 1, 14, 20, LOW + 6, 14, POST);
		b.fill(21, LOW + 6, 14, 25, LOW + 6, 14, POST);
		for (int y = LOW + 2; y <= LOW + 5; y++) {
			b.axis(25, y, 14, Blocks.IRON_CHAIN, Direction.Axis.Y);
		}
		b.set(25, LOW + 1, 14, Blocks.BARREL);
		for (int z : new int[] {9, 13, 17}) {
			b.fill(21, StationParts.WATERLINE, z, 21, LOW, z, Blocks.DARK_OAK_LOG);
		}
	}

	/** Boats at their moorings and buoys out in the water: the harbour is in use. */
	private static void harbour(final Builder b) {
		StationParts.skiff(b, 24, 8, 16);
		b.set(25, StationParts.WATERLINE + 1, 13, Blocks.BARREL);
		b.set(26, StationParts.WATERLINE + 1, 13, Blocks.BARREL);
		for (int x = 21; x <= 23; x++) {
			b.axis(x, LOW, 11, Blocks.IRON_CHAIN, Direction.Axis.X);
		}
		StationParts.skiff(b, -31, 31, 37);
		b.slab(-30, StationParts.WATERLINE + 1, 34, Blocks.SPRUCE_SLAB, false);
		b.slab(-29, StationParts.WATERLINE + 1, 34, Blocks.SPRUCE_SLAB, false);
		b.axis(-30, LOW, 29, Blocks.IRON_CHAIN, Direction.Axis.Z);
		b.axis(-30, LOW, 30, Blocks.IRON_CHAIN, Direction.Axis.Z);
		StationParts.buoy(b, -22, 35, false);
		StationParts.buoy(b, 11, 38, false);
		StationParts.buoy(b, 30, -19, true);
	}

	private static void barracks(final Builder b) {
		StationParts.hall(b, -30, 6, -12, 26, LOW, 5, BOARD, Blocks.STAINED_GLASS.orange(), WARM_STAIRS, PLANK, false);
		StationParts.door(b, -12, 16, LOW, false);
		// bunks along both long walls, heads to the wall, lockers between them
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
		StationParts.chimney(b, -29, 16, LOW + 2, LOW + 11);
	}

	/** The quartermaster's store: walls on three sides under a small gable, a counter across the open front. */
	private static void store(final Builder b) {
		StationParts.hall(b, 0, 0, 11, 6, LOW, 4, BOARD, Blocks.STAINED_GLASS.orange(), WARM_STAIRS, PLANK, true);
		b.clear(1, LOW + 1, 6, 10, LOW + 3, 6);
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
		StationParts.hall(b, 6, 12, 19, 27, LOW, 7, Blocks.STRIPPED_SPRUCE_WOOD, Blocks.STAINED_GLASS.yellow(), WARM_STAIRS, PLANK, false);
		// the cargo front: three loading bays under a copper hood
		b.clear(6, LOW + 1, 13, 6, LOW + 6, 26);
		for (int z : new int[] {17, 22}) {
			b.fill(6, LOW + 1, z, 6, LOW + 6, z, Blocks.STRIPPED_SPRUCE_LOG);
		}
		b.fill(5, LOW + 7, 13, 5, LOW + 7, 26, WEATHERED);
		for (int z : new int[] {15, 20, 24}) {
			b.set(7, LOW + 6, z, BOARD);
			b.hanging(7, LOW + 5, z, Blocks.LANTERN);
		}
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
		// two storeys: the hall below, rooms above, a hip roof over both
		StationParts.walls(b, -44, -28, -30, -14, MID, 5, BOARD, Blocks.STAINED_GLASS.orange(), true);
		StationParts.walls(b, -44, -28, -30, -14, MID + 6, 5, BOARD, Blocks.STAINED_GLASS.orange(), true);
		StationParts.hipRoof(b, -45, -29, -29, -13, MID + 12, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_SLAB);
		StationParts.door(b, -30, -21, MID, false);
		StationParts.door(b, -37, -14, MID, true);
		// the balcony on the east face, on brackets, lanterns hung beneath it
		b.fill(-29, MID + 6, -26, -27, MID + 6, -16, Blocks.DARK_OAK_PLANKS);
		StationParts.rail(b, -27, -26, -27, -16, MID + 6, false);
		StationParts.rail(b, -29, -26, -27, -26, MID + 6, false);
		StationParts.rail(b, -29, -16, -27, -16, MID + 6, false);
		for (int z : new int[] {-25, -21, -17}) {
			for (int x = -29; x <= -27; x++) {
				b.stairs(x, MID + 5, z, WARM_STAIRS, Direction.WEST, true);
			}
		}
		for (int z : new int[] {-24, -20}) {
			b.hanging(-28, MID + 5, z, Blocks.LANTERN);
		}
		b.clear(-30, MID + 7, -22, -30, MID + 9, -20);
		// the stair up inside, along the north wall
		for (int i = 0; i < 6; i++) {
			int x = -37 + i;
			b.stairs(x, MID + 1 + i, -27, Blocks.SPRUCE_STAIRS, Direction.EAST, false);
			b.fill(x, MID + 1, -27, x, MID + i, -27, Blocks.SPRUCE_PLANKS);
			if (i < 5) {
				b.clear(x, MID + 6, -27, x, MID + 6, -27);
			}
		}
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
		StationParts.chimney(b, -42, -26, MID + 1, MID + 29);
		// beds upstairs
		for (int x = -42; x <= -34; x += 4) {
			b.bed(x, MID + 7, -15, Blocks.BED.brown(), Direction.SOUTH);
		}
		// market stalls under coloured canopies along the east side, a string of lanterns between them
		stall(b, -26, -28, Blocks.WOOL.red());
		stall(b, -26, -20, Blocks.WOOL.yellow());
		stall(b, -26, -12, Blocks.WOOL.brown());
		StationParts.lanternString(b, -29, -21, -14, MID + 5, new int[] {-27, -24, -22});
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
		StationParts.hall(b, -60, -58, -46, -42, MID, 6, STONE, Blocks.STAINED_GLASS.orange(), Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, false);
		StationParts.door(b, -46, -50, MID, false);
		StationParts.door(b, -53, -42, MID, true);
		// the hearth: blast furnaces round a lava cauldron, the chimney straight up through the roof
		b.fill(-58, MID + 1, -52, -56, MID + 1, -48, Blocks.BLAST_FURNACE);
		b.set(-57, MID + 1, -50, Blocks.LAVA_CAULDRON);
		b.fill(-58, MID + 2, -52, -56, MID + 3, -48, Blocks.BRICKS);
		b.set(-57, MID + 2, -50, Blocks.AIR);
		StationParts.chimney(b, -57, -50, MID + 4, MID + 16);
		// a hay bale under the fire on top: the one tall plume over the station
		b.set(-57, MID + 16, -50, Blocks.HAY_BLOCK);
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
		// the stand on the north side, under a copper awning
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
		StationParts.walls(b, 28, -51, 48, -31, MID, 5, Blocks.CALCITE, Blocks.STAINED_GLASS.lightBlue(), true);
		// a white fascia under a weathered copper pitch, clerestory panes in the gables
		for (int x = 27; x <= 49; x++) {
			b.set(x, MID + 5, -52, Blocks.SMOOTH_QUARTZ);
			b.set(x, MID + 5, -30, Blocks.SMOOTH_QUARTZ);
		}
		for (int z = -52; z <= -30; z++) {
			b.set(27, MID + 5, z, Blocks.SMOOTH_QUARTZ);
			b.set(49, MID + 5, z, Blocks.SMOOTH_QUARTZ);
		}
		StationParts.shallowRoof(b, 27, -52, 49, -30, MID + 6, true, WEATHERED, WEATHERED_SLAB, Blocks.CALCITE);
		b.fill(36, MID + 7, -51, 40, MID + 7, -51, Blocks.STAINED_GLASS.lightBlue());
		b.fill(36, MID + 7, -31, 40, MID + 7, -31, Blocks.STAINED_GLASS.lightBlue());
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
		StationParts.walls(b, 40, -22, 54, -4, MID, 5, Blocks.TUFF_BRICKS, Blocks.STAINED_GLASS.yellow(), true);
		StationParts.shallowRoof(b, 39, -23, 55, -3, MID + 6, true, COPPER, TEAL_SLAB, Blocks.TUFF_BRICKS);
		// louvres in both gables
		for (int z : new int[] {-22, -4}) {
			for (int x = 46; x <= 48; x++) {
				b.facing(x, MID + 6, z, Blocks.SPRUCE_TRAPDOOR, z < -10 ? Direction.NORTH : Direction.SOUTH);
				b.facing(x, MID + 7, z, Blocks.SPRUCE_TRAPDOOR, z < -10 ? Direction.NORTH : Direction.SOUTH);
			}
		}
		StationParts.door(b, 45, -22, MID, true);
		StationParts.door(b, 40, -8, MID, false);
		b.set(48, MID + 1, -13, Blocks.CRAFTING_TABLE);
		b.facing(48, MID + 1, -15, Blocks.SMITHING_TABLE, Direction.WEST);
		b.set(48, MID + 1, -11, Blocks.LOOM);
		b.fill(51, MID + 1, -20, 52, MID + 1, -6, Blocks.BARREL);
		b.fill(51, MID + 2, -20, 52, MID + 2, -6, Blocks.SPRUCE_SLAB);
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
		StationParts.hall(b, 34, -80, 52, -62, MID, 7, STONE, Blocks.STAINED_GLASS.brown(), WARM_STAIRS, PLANK, true);
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
		belfry(b);
	}

	/** The open bell tower in the court's north-east corner: a closed shaft, an open chamber, a stepped copper cap. */
	private static void belfry(final Builder b) {
		b.fill(17, MID + 1, -103, 23, MID + 1, -97, STONE);
		for (int x : new int[] {17, 23}) {
			for (int z : new int[] {-103, -97}) {
				b.fill(x, MID + 2, z, x, MID + 24, z, BEAM);
			}
		}
		for (int y = MID + 2; y <= MID + 17; y++) {
			Block shell = (y - MID) % 4 == 0 ? STONE : Blocks.TUFF_BRICKS;
			b.fill(18, y, -103, 22, y, -103, shell);
			b.fill(18, y, -97, 22, y, -97, shell);
			b.fill(17, y, -102, 17, y, -98, shell);
			b.fill(23, y, -102, 23, y, -98, shell);
		}
		for (int y : new int[] {MID + 6, MID + 7, MID + 8, MID + 12, MID + 13, MID + 14}) {
			b.set(20, y, -97, Blocks.STAINED_GLASS.cyan());
		}
		b.fill(17, MID + 18, -103, 23, MID + 18, -97, WEATHERED);
		// the chamber: open on all four sides between the piers, the bell hung from a beam
		for (int y = MID + 19; y <= MID + 23; y++) {
			for (int i = 18; i <= 22; i++) {
				boolean open = y >= MID + 20 && i >= 19 && i <= 21;
				Block shell = open ? Blocks.AIR : Blocks.TUFF_BRICKS;
				b.set(i, y, -103, shell);
				b.set(i, y, -97, shell);
				b.set(17, y, i - 120, shell);
				b.set(23, y, i - 120, shell);
			}
		}
		for (int x = 17; x <= 23; x++) {
			b.axis(x, MID + 24, -100, Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X);
		}
		b.set(20, MID + 23, -100, Blocks.BELL.defaultBlockState()
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
			.setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.CEILING));
		// the stepped cap
		for (int k = 0; k < 4; k++) {
			b.fill(16 + k, MID + 25 + k, -104 + k, 24 - k, MID + 25 + k, -96 - k, k == 0 ? WEATHERED : HULL);
		}
		b.facing(20, MID + 29, -100, Blocks.LIGHTNING_ROD.waxed().oxidized(), Direction.UP);
	}

	private static void radioRoom(final Builder b) {
		StationParts.deck(b, -28, -96, -4, -72, MID, Blocks.POLISHED_ANDESITE, false);
		StationParts.walk(b, -3, -90, -1, -86, MID);
		StationParts.gap(b, -4, -90, -4, -86, MID);
		StationParts.gap(b, 0, -90, 0, -86, MID);
		StationParts.walls(b, -26, -94, -8, -78, MID, 5, Blocks.TUFF_BRICKS, Blocks.STAINED_GLASS.cyan(), true);
		StationParts.shallowRoof(b, -27, -95, -7, -77, MID + 6, true, COPPER, TEAL_SLAB, Blocks.TUFF_BRICKS);
		StationParts.door(b, -8, -86, MID, false);
		StationParts.door(b, -26, -82, MID, false);
		b.fill(-19, MID, -87, -15, MID, -85, RUST);
		b.set(-17, MID + 1, -86, MistinnBlocks.RADIO.defaultBlockState().setValue(RadioBlock.FACING, Direction.SOUTH));
		for (int x = -24; x <= -10; x += 2) {
			b.set(x, MID + 1, -93, Blocks.BARREL);
			b.set(x, MID + 2, -93, GRATE);
		}
		// the antenna mast, through the ridge on a copper apron
		b.fill(-18, MID + 11, -87, -16, MID + 11, -85, WEATHERED);
		b.fill(-17, MID + 6, -86, -17, MID + 22, -86, FRAME);
		for (int y : new int[] {MID + 13, MID + 17, MID + 21}) {
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
		StationParts.walls(b, -60, -94, -36, -68, MID, 9, STONE, Blocks.STAINED_GLASS.gray(), false);
		StationParts.shallowRoof(b, -61, -95, -35, -67, MID + 10, false, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_SLAB, STONE);
		StationParts.door(b, -36, -82, MID, false);
		// the light stays at the walls: crossbeams, six lanterns, the middle of the floor left dark
		for (int z : new int[] {-89, -84, -78, -72}) {
			for (int x = -59; x <= -37; x++) {
				b.axis(x, MID + 8, z, Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X);
			}
		}
		for (int x : new int[] {-56, -40}) {
			for (int z : new int[] {-84, -78, -72}) {
				b.hanging(x, MID + 7, z, Blocks.LANTERN);
			}
		}
		// the north gate onto the cold stair
		b.clear(-47, MID + 1, -94, -45, MID + 4, -94);
		StationParts.gap(b, -47, -96, -45, -96, MID);
		ringFloor(b, -48, -81, 8, FRAME, Blocks.POLISHED_BLACKSTONE_BRICKS, MID);
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
		StationParts.rail(b, -47, -115, -47, -111, HIGH, false);
		StationParts.rail(b, -47, -115, -45, -115, HIGH, false);
		StationParts.deck(b, -44, -124, -18, -104, HIGH, HULL, true);
		StationParts.darkRailAround(b, -44, -124, -18, -104, HIGH);
		b.fill(-43, HIGH, -123, -19, HIGH, -105, GRATE);
		StationParts.gap(b, -44, -114, -44, -111, HIGH);
		StationParts.gap(b, -28, -124, -26, -124, HIGH);
		// the report console, the last warm light, and a soul lantern at the gate
		b.fill(-34, HIGH + 1, -109, -32, HIGH + 1, -109, HULL);
		b.set(-33, HIGH + 1, -110, MistinnBlocks.RADIO.defaultBlockState().setValue(RadioBlock.FACING, Direction.SOUTH).setValue(RadioBlock.LIT, true));
		b.set(-30, HIGH + 1, -123, WEATHERED);
		b.set(-30, HIGH + 2, -123, Blocks.SOUL_LANTERN);
		crane(b);
		pod(b, -27, HIGH);
	}

	/** The mast, the boom out over the fog with an open truss above it, the counterweight behind. */
	private static void crane(final Builder b) {
		b.fill(-28, HIGH + 1, -119, -27, HIGH + 26, -118, BEAM);
		b.fill(-28, HIGH + 24, -140, -26, HIGH + 25, -113, BEAM);
		// the truss: two upper chords, verticals at the panel lines, one diagonal in each bay
		int[] panels = {-140, -134, -128, -122, -116, -113};
		for (int x : new int[] {-28, -26}) {
			b.fill(x, HIGH + 30, -140, x, HIGH + 30, -113, BEAM);
			for (int z : panels) {
				b.fill(x, HIGH + 26, z, x, HIGH + 29, z, BEAM);
			}
			for (int p = 0; p < panels.length - 1; p++) {
				int za = panels[p];
				int zb = panels[p + 1];
				for (int t = 0; t <= 4; t++) {
					int z = za + Math.round(t * (zb - za) / 4.0F);
					int y = p % 2 == 0 ? HIGH + 26 + t : HIGH + 30 - t;
					b.set(x, Math.min(y, HIGH + 29), z, BEAM);
				}
			}
		}
		for (int z : panels) {
			b.set(-27, HIGH + 30, z, BEAM);
		}
		b.fill(-29, HIGH + 27, -120, -27, HIGH + 29, -118, BEAM);
		// the counterweight, banded
		b.fill(-29, HIGH + 20, -114, -25, HIGH + 23, -111, STONE);
		b.fill(-29, HIGH + 21, -114, -25, HIGH + 21, -111, Blocks.POLISHED_BLACKSTONE);
		// the hoist saddle across the boom, where the pod's chains hang from
		b.fill(-30, HIGH + 24, -130, -24, HIGH + 24, -128, BEAM);
	}

	/**
	 * The drop pod, hanging from the hoist at the deck's edge: dark ribs and copper panels, a framed
	 * mouth facing the deck, one cold light recessed in its ceiling.
	 */
	private static void pod(final Builder b, final int cx, final int y) {
		b.fill(cx - 3, y, -133, cx + 3, y, -125, HULL);
		b.fill(cx - 1, y, -131, cx + 1, y, -125, GRATE);
		for (int side : SIDES) {
			int x = cx + side * 2;
			b.fill(x, y + 1, -132, x, y + 4, -126, HULL);
			for (int z : new int[] {-131, -129, -127}) {
				b.fill(x, y + 1, z, x, y + 4, z, Blocks.POLISHED_DEEPSLATE);
			}
			b.set(x, y + 2, -130, Blocks.STAINED_GLASS.cyan());
			b.fill(x, y + 1, -126, x, y + 4, -126, WEATHERED);
			for (int h = y + 7; h < y + 24; h++) {
				b.axis(x, h, -129, Blocks.IRON_CHAIN, Direction.Axis.Y);
			}
		}
		b.fill(cx - 1, y + 1, -132, cx + 1, y + 4, -132, BEAM);
		b.set(cx, y + 2, -132, Blocks.GOLD_BLOCK);
		b.facing(cx, y + 2, -131, Blocks.POLISHED_BLACKSTONE_BUTTON, Direction.SOUTH);
		b.fill(cx - 1, y + 4, -126, cx + 1, y + 4, -126, BEAM);
		b.fill(cx - 1, y + 4, -132, cx + 1, y + 4, -127, BEAM);
		b.facing(cx, y + 4, -129, Blocks.IRON_TRAPDOOR, Direction.NORTH);
		b.set(cx, y + 5, -129, Blocks.SEA_LANTERN);
		b.fill(cx - 2, y + 5, -132, cx + 2, y + 5, -126, HULL);
		b.set(cx, y + 5, -129, Blocks.SEA_LANTERN);
		b.fill(cx - 2, y + 6, -129, cx + 2, y + 6, -129, BEAM);
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
