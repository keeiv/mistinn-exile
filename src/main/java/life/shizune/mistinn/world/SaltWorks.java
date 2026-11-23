package life.shizune.mistinn.world;

import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * 鹽坊: a dead salt works on flat ground near the coast. A walled round yard where the great brine wheel turned, four
 * stone pillars standing in it, the dry channel round its edge; outside the gate a golden bell in a shrine, the boiling
 * house with what was left in it, and the salt pans gone to crust. The Rust Wheel stands in the middle of the yard.
 * Built with its origin on the ground at the middle of the yard.
 */
public final class SaltWorks {
	/** How far the yard reaches from its middle to the inside of its wall. */
	public static final int YARD = 13;
	/** The pillars of the yard, as offsets from its middle: what a rolling wheel is made to run into. */
	public static final int[][] PILLARS = {{7, 7}, {-7, 7}, {7, -7}, {-7, -7}};
	/** The gate: in the south wall, this far either side of the middle. */
	public static final int GATE = 2;
	/** Where the golden bell by the gate stands, from the middle of the yard where the wheel stands. */
	public static final BlockPos BELL = new BlockPos(4, 0, YARD + 5);

	private SaltWorks() {
	}

	/** Builds the salt works and returns where the wheel stands: the middle of the yard, one above the floor. */
	public static BlockPos build(final Builder b) {
		b.clear(-YARD - 3, 1, -YARD - 3, YARD + 3, 9, YARD + 11);
		yard(b);
		wall(b);
		pillars(b);
		shrine(b);
		boilingHouse(b);
		pans(b);
		b.spawn(MistinnEntities.RUST_WHEEL, 0, 1, 0, 0.0F);
		dead(b);
		b.settle();
		// the mist gathers round it as round every dead keeper's place on this coast
		ShoreMist.remember(b.level(), b.at(0, 1, 0));
		return b.at(0, 1, 0);
	}

	/** The yard's floor: worn stone, salt crusted in patches, the dry channel round the edge. */
	private static void yard(final Builder b) {
		for (int x = -YARD - 1; x <= YARD + 1; x++) {
			for (int z = -YARD - 1; z <= YARD + 1; z++) {
				double r = Math.sqrt(x * x + z * z);
				if (r > YARD + 1.5) {
					continue;
				}
				Block floor;
				if (r > YARD - 1.5) {
					// the channel the brine ran in, dry now
					floor = b.chance(0.3F) ? Blocks.MUD_BRICKS : Blocks.PACKED_MUD;
				} else {
					float roll = b.random().nextFloat();
					floor = roll < 0.18F ? Blocks.CALCITE : roll < 0.3F ? Blocks.CRACKED_STONE_BRICKS : roll < 0.42F ? Blocks.COBBLESTONE : Blocks.STONE_BRICKS;
				}
				b.set(x, 0, z, floor);
				b.footing(x, z, Blocks.STONE, 8);
			}
		}
		// the wheel wore a pit in the middle
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				b.set(x, 0, z, Blocks.POLISHED_ANDESITE);
			}
		}
	}

	/** The wall round the yard, three high and broken in places, the gate to the south. */
	private static void wall(final Builder b) {
		for (int x = -YARD - 2; x <= YARD + 2; x++) {
			for (int z = -YARD - 2; z <= YARD + 2; z++) {
				double r = Math.sqrt(x * x + z * z);
				if (r < YARD + 0.5 || r > YARD + 1.6) {
					continue;
				}
				if (z > 0 && Math.abs(x) <= GATE) {
					continue;
				}
				int height = b.chance(0.12F) ? 2 : 3;
				for (int y = 0; y <= height; y++) {
					float roll = b.random().nextFloat();
					b.set(x, y, z, roll < 0.25F ? Blocks.MOSSY_STONE_BRICKS : roll < 0.4F ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS);
				}
				b.footing(x, z, Blocks.STONE_BRICKS, 8);
				if (height == 3 && b.chance(0.2F)) {
					b.set(x, 4, z, Blocks.STONE_BRICK_WALL);
				}
			}
		}
		// the gate posts, a chain slung between them
		for (int y = 1; y <= 4; y++) {
			b.set(-GATE - 1, y, YARD + 1, Blocks.CHISELED_STONE_BRICKS);
			b.set(GATE + 1, y, YARD + 1, Blocks.CHISELED_STONE_BRICKS);
		}
		for (int x = -GATE; x <= GATE; x++) {
			b.axis(x, 4, YARD + 1, Blocks.IRON_CHAIN, Direction.Axis.X);
		}
	}

	/** The four pillars: two by two, four high. */
	private static void pillars(final Builder b) {
		for (int[] at : PILLARS) {
			for (int dx = 0; dx <= 1; dx++) {
				for (int dz = 0; dz <= 1; dz++) {
					int x = at[0] + (at[0] > 0 ? dx : -dx);
					int z = at[1] + (at[1] > 0 ? dz : -dz);
					for (int y = 1; y <= 4; y++) {
						b.set(x, y, z, y == 4 ? Blocks.CHISELED_STONE_BRICKS : b.chance(0.25F) ? Blocks.CRACKED_STONE_BRICKS : Blocks.POLISHED_ANDESITE);
					}
				}
			}
		}
	}

	/** Outside the gate, to the east: the golden bell in a little stone shrine. */
	private static void shrine(final Builder b) {
		int z = YARD + 5;
		for (int x = 3; x <= 6; x++) {
			for (int dz = -1; dz <= 1; dz++) {
				b.set(x, 0, z + dz, Blocks.STONE_BRICKS);
				b.footing(x, z + dz, Blocks.STONE, 6);
			}
		}
		b.set(BELL.getX(), 1, BELL.getZ(), MistinnBlocks.GOLDEN_BELL);
		b.set(5, 1, z, Blocks.STONE_BRICK_WALL);
		b.set(5, 2, z, Blocks.STONE_BRICK_WALL);
		b.set(4, 1, z + 1, Blocks.STONE_BRICKS);
		b.set(4, 2, z + 1, Blocks.CHISELED_STONE_BRICKS);
		b.slab(4, 3, z + 1, Blocks.STONE_BRICK_SLAB, false);
		b.slab(5, 3, z, Blocks.STONE_BRICK_SLAB, false);
		b.candles(3, 1, z, Blocks.CANDLE, 2, false);
	}

	/** The boiling house, east of the path: a low stone room, its pans cold, what was left in it. */
	private static void boilingHouse(final Builder b) {
		int x0 = 6;
		int x1 = 12;
		int z0 = YARD + 7;
		int z1 = YARD + 11;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				b.set(x, 0, z, Blocks.COBBLESTONE);
				b.footing(x, z, Blocks.STONE, 6);
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				for (int y = 1; y <= 3; y++) {
					if (edge) {
						b.set(x, y, z, b.chance(0.2F) ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE);
					}
				}
				b.slab(x, 4, z, Blocks.COBBLESTONE_SLAB, false);
			}
		}
		// a doorway toward the path, a window over the pans
		b.clear(x0, 1, z0 + 2, x0, 2, z0 + 2);
		b.set(x1, 2, z0 + 2, Blocks.IRON_BARS);
		// the cold boiling pan, and the stores
		b.set(9, 1, z0 + 2, Blocks.CAULDRON);
		b.set(10, 1, z0 + 2, Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
		b.container(x1 - 1, 1, z1 - 1, Blocks.CHEST, Direction.WEST, "chests/salt_works");
		b.container(x0 + 1, 1, z1 - 1, Blocks.BARREL, Direction.UP, "chests/salt_works_base");
		b.hanging(9, 3, z0 + 2, Blocks.LANTERN);
	}

	/** West of the path, the salt pans: shallow beds gone to crust, a puddle of brine left in one. */
	private static void pans(final Builder b) {
		for (int pan = 0; pan < 3; pan++) {
			int x0 = -12 + pan * 3;
			for (int x = x0; x <= x0 + 1; x++) {
				for (int z = YARD + 6; z <= YARD + 10; z++) {
					b.set(x, 0, z, pan == 1 && z == YARD + 8 ? Blocks.WATER : Blocks.CALCITE);
					b.footing(x, z, Blocks.STONE, 6);
				}
			}
			for (int z = YARD + 5; z <= YARD + 11; z++) {
				b.set(x0 + 2, 0, z, Blocks.MUD_BRICKS);
			}
		}
	}

	/** The drowned lie about the yard, as on every shore where a keeper waits. */
	private static void dead(final Builder b) {
		int laid = 0;
		for (int[] at : new int[][] {{-4, 9}, {9, -2}, {-10, -3}, {3, -10}}) {
			if (laid >= 2) {
				return;
			}
			if (b.get(at[0], 1, at[1]).isAir() && b.get(at[0], 2, at[1]).isAir()) {
				b.spawn(MistinnEntities.SALT_CORPSE, at[0], 1, at[1], b.random().nextFloat() * 360.0F, corpse -> corpse.lieDormant(true));
				laid++;
			}
		}
	}
}
