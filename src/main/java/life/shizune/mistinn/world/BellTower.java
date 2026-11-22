package life.shizune.mistinn.world;

import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * 霧鐘台: a fog-bell station on a reef off the shore, from the days when a bell was rung in the mist to guide the boats in.
 * A ring of sea-worn rock just above the water, a little jetty and a golden bell where the boats tied up; a square stone
 * tower of three floors, the lowest half flooded, a stair climbing its walls; and on top, out past the walls, a round
 * open belfry under a green copper roof, where the great bell hangs and 沉鐘 waits beneath it.
 * Built with its origin at the height of the sea's surface: the reef's top is level 0, the belfry's floor {@link #DECK}.
 */
public final class BellTower {
	/** The floors of the tower, and the belfry's deck on top. */
	public static final int[] FLOORS = {0, 5, 10};
	public static final int DECK = 15;
	/** How far the belfry's deck reaches from the middle, and where its rail stands. */
	public static final double DECK_RADIUS = 8.4;
	/** The great bell: the height of its lip, and of the roof it hangs from. */
	public static final int BELL_LIP = DECK + 4;
	private static final int ROOF = DECK + 9;
	/** Half the width of the tower, walls included. */
	private static final int HALF = 4;

	private BellTower() {
	}

	/** Builds the fog-bell station and returns the middle of its belfry floor, where the keeper stands. */
	public static BlockPos build(final Builder b) {
		b.clear(-9, 1, -9, 9, ROOF + 2, 12);
		reef(b);
		tower(b);
		stair(b);
		belfry(b);
		greatBell(b);
		jetty(b);
		stores(b);
		b.spawn(MistinnEntities.SUNKEN_BELL, 0, DECK + 1, 0, 270.0F);
		dead(b);
		b.settle();
		// the mist gathers round it as round every dead light on this coast
		ShoreMist.remember(b.level(), b.at(0, DECK + 1, 0));
		return b.at(0, DECK + 1, 0);
	}

	/** Sea-worn rock just above the water, propped down to the sea floor, green and salt-white at its edges. */
	private static void reef(final Builder b) {
		for (int x = -9; x <= 9; x++) {
			for (int z = -9; z <= 9; z++) {
				double r = Math.sqrt(x * x + z * z) + b.random().nextDouble() * 1.2;
				if (r > 8.8) {
					continue;
				}
				Block block;
				float roll = b.random().nextFloat();
				if (r <= 5.5) {
					block = roll < 0.35F ? Blocks.MOSSY_STONE_BRICKS : roll < 0.5F ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS;
				} else {
					block = roll < 0.25F ? Blocks.MOSSY_COBBLESTONE : roll < 0.45F ? Blocks.DARK_PRISMARINE : roll < 0.6F ? Blocks.TUFF
						: roll < 0.72F ? Blocks.CALCITE : roll < 0.85F ? Blocks.ANDESITE : Blocks.STONE;
				}
				b.set(x, 0, z, block);
				b.footing(x, 0, z, r <= 5.5 ? Blocks.STONE_BRICKS : Blocks.STONE, 14);
				// a boulder here and there on the rim
				if (r > 6.5 && b.chance(0.08F)) {
					b.set(x, 1, z, b.chance(0.5F) ? Blocks.MOSSY_COBBLESTONE : Blocks.TUFF);
				}
			}
		}
	}

	/** The square tower: sea-worn below, salt bands at each floor, slit windows, the door to the south. */
	private static void tower(final Builder b) {
		for (int y = 1; y < DECK; y++) {
			for (int x = -HALF; x <= HALF; x++) {
				for (int z = -HALF; z <= HALF; z++) {
					boolean wall = Math.abs(x) == HALF || Math.abs(z) == HALF;
					if (!wall) {
						continue;
					}
					boolean corner = Math.abs(x) == HALF && Math.abs(z) == HALF;
					Block block;
					float roll = b.random().nextFloat();
					if (corner) {
						block = y <= 3 ? Blocks.MOSSY_STONE_BRICKS : Blocks.POLISHED_ANDESITE;
					} else if (y == FLOORS[1] || y == FLOORS[2]) {
						block = b.chance(0.15F) ? Blocks.DIORITE : Blocks.CALCITE;
					} else if (y <= 3) {
						block = roll < 0.35F ? Blocks.MOSSY_STONE_BRICKS : roll < 0.5F ? Blocks.DARK_PRISMARINE : roll < 0.65F ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS;
					} else {
						block = roll < 0.15F ? Blocks.CRACKED_STONE_BRICKS : roll < 0.22F ? Blocks.MOSSY_STONE_BRICKS : roll < 0.27F ? Blocks.TUFF_BRICKS : Blocks.STONE_BRICKS;
					}
					b.set(x, y, z, block);
				}
			}
		}
		// floors, the lowest half under water
		for (int x = -HALF + 1; x <= HALF - 1; x++) {
			for (int z = -HALF + 1; z <= HALF - 1; z++) {
				b.set(x, 0, z, Math.abs(x) <= 1 && z <= 0 ? Blocks.WATER : b.chance(0.3F) ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS);
				for (int i = 1; i < FLOORS.length; i++) {
					b.set(x, FLOORS[i], z, b.chance(0.2F) ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.SPRUCE_PLANKS);
				}
			}
		}
		// the door to the south under a lintel
		b.clear(0, 1, HALF, 0, 2, HALF);
		b.set(0, 3, HALF, Blocks.CHISELED_STONE_BRICKS);
		// slit windows, salt weeping down below them
		for (int y : new int[] {3, 8, 13}) {
			for (int[] w : new int[][] {{HALF, 0}, {-HALF, 0}, {0, -HALF}}) {
				b.set(w[0], y, w[1], Blocks.IRON_BARS);
				if (y - 1 > 0) {
					b.maybe(0.7F, w[0], y - 1, w[1], Blocks.CALCITE);
				}
			}
		}
		// a lantern long gone out on each floor
		for (int i = 0; i < FLOORS.length; i++) {
			b.hanging(0, FLOORS[i] + 4, 0, Blocks.LANTERN);
		}
	}

	/**
	 * The stair: on each floor a flight of four up one wall, the east wall going north, then the west going south, then the
	 * east again, through an opening in the floor above. Each step and the floor after it have room overhead.
	 */
	private static void stair(final Builder b) {
		for (int i = 0; i < FLOORS.length; i++) {
			boolean east = i % 2 == 0;
			int x = east ? HALF - 1 : -(HALF - 1);
			for (int k = 0; k < 4; k++) {
				int z = east ? 2 - k : -2 + k;
				int y = FLOORS[i] + 1 + k;
				b.stairs(x, y, z, Blocks.SPRUCE_STAIRS, east ? Direction.NORTH : Direction.SOUTH, false);
				// room overhead: the floor above is open over the flight
				int above = i + 1 < FLOORS.length ? FLOORS[i + 1] : DECK;
				if (k >= 1) {
					b.set(x, above, z, Blocks.AIR);
				}
			}
		}
	}

	/** The open belfry: a round deck reaching out past the walls on corbels, a rail round it, four posts and a copper roof. */
	private static void belfry(final Builder b) {
		for (int x = -9; x <= 9; x++) {
			for (int z = -9; z <= 9; z++) {
				double r = Math.sqrt(x * x + z * z);
				Direction in = Math.abs(x) >= Math.abs(z) ? (x > 0 ? Direction.WEST : Direction.EAST) : (z > 0 ? Direction.NORTH : Direction.SOUTH);
				if (r > HALF + 0.5 && r <= HALF + 2.0) {
					b.stairs(x, DECK - 1, z, Blocks.STONE_BRICK_STAIRS, in, true);
				}
				if (r <= DECK_RADIUS) {
					boolean stone = Math.abs(x) <= HALF && Math.abs(z) <= HALF;
					// the hatch over the stair stays open
					if (b.get(x, DECK, z).isAir() && Math.abs(x) == HALF - 1 && Math.abs(z) <= 1) {
						continue;
					}
					b.set(x, DECK, z, stone ? (b.chance(0.25F) ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS)
						: b.chance(0.15F) ? Blocks.STRIPPED_DARK_OAK_LOG : Blocks.DARK_OAK_PLANKS);
				}
				if (r > DECK_RADIUS - 0.8 && r <= DECK_RADIUS) {
					b.set(x, DECK + 1, z, Blocks.DARK_OAK_FENCE);
				}
				// the roof, in three steps of green copper
				if (r <= 6.6) {
					b.slab(x, ROOF, z, Blocks.CUT_COPPER_SLAB.waxed().oxidized(), false);
				}
				if (r <= 4.6) {
					b.set(x, ROOF + 1, z, Blocks.CUT_COPPER.waxed().oxidized());
				}
				if (r <= 2.2) {
					b.set(x, ROOF + 2, z, Blocks.COPPER_BLOCK.waxed().oxidized());
				}
			}
		}
		b.set(0, ROOF + 3, 0, Blocks.LIGHTNING_ROD.waxed().oxidized());
		// the hatch is open: no rail or deck over it
		for (int z = -1; z <= 1; z++) {
			b.set(HALF - 1, DECK, z, Blocks.AIR);
		}
		// four posts carry the roof
		for (int x = -5; x <= 5; x += 10) {
			for (int z = -5; z <= 5; z += 10) {
				for (int y = DECK + 1; y < ROOF; y++) {
					b.axis(x, y, z, Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y);
				}
			}
		}
		// beams from post to post under the roof
		for (int t = -4; t <= 4; t++) {
			b.axis(t, ROOF - 1, -5, Blocks.DARK_OAK_LOG, Direction.Axis.X);
			b.axis(t, ROOF - 1, 5, Blocks.DARK_OAK_LOG, Direction.Axis.X);
			b.axis(-5, ROOF - 1, t, Blocks.DARK_OAK_LOG, Direction.Axis.Z);
			b.axis(5, ROOF - 1, t, Blocks.DARK_OAK_LOG, Direction.Axis.Z);
			b.axis(t, ROOF - 1, 0, Blocks.DARK_OAK_LOG, Direction.Axis.X);
		}
		// unlit candles left at the rail
		b.candles(-6, DECK + 1, 3, Blocks.CANDLE, 3, false);
		b.candles(5, DECK + 1, -5, Blocks.DYED_CANDLE.white(), 2, false);
	}

	/** The great bell, green bronze, hanging on a chain from the middle beam over the keeper's head. */
	private static void greatBell(final Builder b) {
		for (int y = BELL_LIP + 3; y < ROOF - 1; y++) {
			b.axis(0, y, 0, Blocks.IRON_CHAIN, Direction.Axis.Y);
		}
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				boolean cornerOut = Math.abs(x) == 2 && Math.abs(z) == 2;
				if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
					b.set(x, BELL_LIP + 2, z, Blocks.COPPER_BLOCK.waxed().weathered());
				}
				if (!cornerOut) {
					b.set(x, BELL_LIP + 1, z, Math.abs(x) <= 1 && Math.abs(z) <= 1 ? Blocks.AIR : Blocks.CUT_COPPER.waxed().weathered());
				}
				// the lip: a ring, open in the middle where the tongue once hung
				if (!cornerOut && (Math.abs(x) == 2 || Math.abs(z) == 2)) {
					b.set(x, BELL_LIP, z, Blocks.CUT_COPPER.waxed().exposed());
				}
			}
		}
		b.set(0, BELL_LIP + 1, 0, Blocks.IRON_CHAIN);
	}

	/** The jetty to the south where the boats tied up, and the golden bell at its foot. */
	private static void jetty(final Builder b) {
		for (int z = HALF + 1; z <= 12; z++) {
			for (int x = -1; x <= 1; x++) {
				if (z > 8) {
					b.set(x, 0, z, Blocks.SPRUCE_PLANKS);
					if (Math.abs(x) == 1 && z % 2 == 0) {
						b.set(x, 1, z, Blocks.SPRUCE_FENCE);
						b.footing(x, 0, z, Blocks.SPRUCE_FENCE, 14);
					}
				} else if (x == 0) {
					b.set(0, 0, z, z == 6 ? Blocks.CHISELED_STONE_BRICKS : Blocks.POLISHED_ANDESITE);
				}
			}
		}
		// the golden bell in a little stone shrine beside the path
		b.set(2, 1, 6, MistinnBlocks.GOLDEN_BELL);
		b.set(3, 1, 6, Blocks.STONE_BRICK_WALL);
		b.set(3, 2, 6, Blocks.STONE_BRICK_WALL);
		b.set(2, 1, 7, Blocks.STONE_BRICKS);
		b.set(2, 2, 7, Blocks.CHISELED_STONE_BRICKS);
		b.slab(2, 3, 7, Blocks.STONE_BRICK_SLAB, false);
		b.slab(3, 3, 6, Blocks.STONE_BRICK_SLAB, false);
		b.candles(1, 1, 6, Blocks.CANDLE, 2, false);
	}

	/** The same three floors of stores as a lighthouse: the higher, the better. */
	private static void stores(final Builder b) {
		b.container(-HALF + 1, FLOORS[0] + 1, HALF - 1, Blocks.BARREL, Direction.UP, "chests/lighthouse_base");
		b.container(-HALF + 2, FLOORS[1] + 1, HALF - 1, Blocks.CHEST, Direction.NORTH, "chests/lighthouse");
		b.container(-7, DECK + 1, 0, Blocks.CHEST, Direction.EAST, "chests/lighthouse_lamp");
	}

	/** The drowned lie on the reef, as on every shore where a keeper waits. */
	private static void dead(final Builder b) {
		int laid = 0;
		for (int[] at : new int[][] {{-6, -4}, {6, 3}, {-5, 5}, {5, -5}}) {
			if (laid >= 2) {
				return;
			}
			if (b.get(at[0], 1, at[1]).isAir() && b.get(at[0], 2, at[1]).isAir() && b.get(at[0], 0, at[1]).isSolid()) {
				b.spawn(MistinnEntities.SALT_CORPSE, at[0], 1, at[1], b.random().nextFloat() * 360.0F, corpse -> corpse.lieDormant(true));
				laid++;
			}
		}
	}
}
