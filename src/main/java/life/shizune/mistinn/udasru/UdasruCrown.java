package life.shizune.mistinn.udasru;

import life.shizune.mistinn.world.Builder;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * 錨冠: the head of the great anchor out of the southern sea. Its shank stands from the rock, seven blocks thick and
 * thirty tall, rusted red and crusted with salt; across its top lies the deck where the station once stood on it, railed
 * with what is left of the station's girders, and over the deck the anchor's ring. When the land's bells have rung, its
 * chain lies bare across the shallows to the shore.
 */
public final class UdasruCrown {
	/** How high the deck stands above the rock. */
	public static final int DECK = 30;
	/** How far the chain runs from the shore to the rock, along z. */
	public static final int CHAIN = UdasruPlaces.ANCHOR_CROWN.getZ() - UdasruPlaces.CHAIN_SHORE.getZ() - 8;

	private UdasruCrown() {
	}

	private static Block rust(final Builder b) {
		return b.pick(Blocks.RAW_IRON_BLOCK, Blocks.RAW_IRON_BLOCK, Blocks.DYED_TERRACOTTA.brown(), Blocks.DYED_TERRACOTTA.red(), Blocks.TERRACOTTA,
			Blocks.COPPER_BLOCK.waxed().exposed());
	}

	/** Raises the shank, the deck and the ring; origin on the rock at the shank's middle. */
	public static void build(final Builder b) {
		b.clear(-9, 1, -9, 9, DECK + 22, 9);
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				b.footing(x, z, Blocks.RAW_IRON_BLOCK, 40);
				for (int y = 0; y <= DECK; y++) {
					b.set(x, y, z, b.chance(0.08F) ? Blocks.CALCITE : rust(b));
				}
			}
		}
		// the stock: a crossbar under the deck
		for (int x = -9; x <= 9; x++) {
			for (int y = DECK - 4; y <= DECK - 2; y++) {
				b.set(x, y, 0, rust(b));
				b.set(x, y, 1, rust(b));
			}
		}
		// the deck, where the station stood, railed with its girders
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				b.set(x, DECK + 1, z, (x + z & 3) == 0 ? Blocks.IRON_BLOCK : rust(b));
				if (Math.abs(x) == 7 || Math.abs(z) == 7) {
					if (!(z == 7 && Math.abs(x) <= 1)) {
						b.set(x, DECK + 2, z, Blocks.IRON_BARS);
					}
				}
			}
		}
		for (int[] c : new int[][] {{-7, -7}, {7, -7}, {-7, 7}, {7, 7}}) {
			for (int y = DECK + 2; y <= DECK + 5; y++) {
				b.set(c[0], y, c[1], Blocks.IRON_BLOCK);
			}
			b.set(c[0], DECK + 6, c[1], Blocks.LANTERN);
		}
		// the ring, standing over the deck's north edge
		for (int a = 0; a < 64; a++) {
			double angle = a * Math.PI * 2.0 / 64.0;
			int x = (int) Math.round(Math.cos(angle) * 7.0);
			int y = (int) Math.round(Math.sin(angle) * 7.0) + DECK + 10;
			if (y > DECK + 1) {
				b.set(x, y, -6, rust(b));
				b.set(x, y, -5, rust(b));
			}
		}
		// a stair up the shank from the chain's end, round its south face
		for (int i = 0; i <= DECK; i++) {
			int x = -4 + i % 9;
			int side = (i / 9) % 2 == 0 ? 4 : -4;
			b.stairs(side == 4 ? x : -x, i + 1, side, Blocks.STONE_BRICK_STAIRS, side == 4 ? Direction.EAST : Direction.WEST, false);
			b.footing(side == 4 ? x : -x, i + 1, side, Blocks.RAW_IRON_BLOCK, 3);
		}
		b.settle();
	}

	/**
	 * The chain, bare at last: links of rust laid across the shallows from the shore to the rock, wide enough to walk,
	 * each link's rim a little raised. Origin on the shore where it comes up, running south (+z).
	 */
	public static void chain(final Builder b) {
		int walk = 1;
		for (int z = 0; z <= CHAIN; z++) {
			b.clear(-3, walk + 1, z, 3, walk + 4, z);
			for (int x = -2; x <= 2; x++) {
				b.footing(x, walk, z, Blocks.RAW_IRON_BLOCK, 40);
				b.set(x, walk, z, Math.abs(x) == 2 ? Blocks.DYED_TERRACOTTA.brown() : rust(b));
			}
			if (z % 7 == 0) {
				b.set(-2, walk + 1, z, Blocks.IRON_BARS);
				b.set(2, walk + 1, z, Blocks.IRON_BARS);
			}
		}
		b.settle();
	}
}
