package life.shizune.mistinn.udasru;

import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.world.Builder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * 霧宿 as the epic finds it, years on: the inn the hunters who never went home built in the fog, grown with them. A
 * long hall of mist pine and pod copper under a patched roof, two floors, a porch with the lamp that is always lit and
 * the golden bell beside it; inside, the hearth, the long table, the wall of number tags, and up the stair the rooms; out
 * the back 小鈴's herb beds and the well. Origin on the ground at its middle, the door to the south.
 */
public final class UdasruInn {
	private static final int HX = 7;
	private static final int HZ = 5;
	/** Where a hunter stands at its door, from the origin. */
	public static final BlockPos DOOR = new BlockPos(0, 1, HZ + 3);
	/** Where the hearth burns, from the origin. */
	public static final BlockPos HEARTH = new BlockPos(-HX + 1, 1, 0);
	/** Where 3412 wakes: their room upstairs. */
	public static final BlockPos BED = new BlockPos(HX - 2, 6, -HZ + 2);

	private static final Block WALL = MistinnBlocks.MIST_PINE_PLANKS;
	private static final Block FRAME = MistinnBlocks.MIST_PINE_LOG;

	private UdasruInn() {
	}

	public static void build(final Builder b) {
		b.clear(-HX - 3, 1, -HZ - 8, HX + 3, 16, HZ + 5);
		for (int x = -HX - 2; x <= HX + 2; x++) {
			for (int z = -HZ - 7; z <= HZ + 4; z++) {
				b.footing(x, z, Blocks.COBBLESTONE, 16);
				boolean inside = Math.abs(x) <= HX && Math.abs(z) <= HZ;
				b.set(x, 0, z, inside ? Blocks.SPRUCE_PLANKS : z > HZ ? Blocks.DIRT_PATH : Blocks.GRASS_BLOCK);
			}
		}
		// two floors of mist pine between log posts, a band of pod copper between them
		for (int y = 1; y <= 9; y++) {
			Block course = y == 5 ? Blocks.CUT_COPPER.waxed().weathered() : WALL;
			for (int x = -HX; x <= HX; x++) {
				b.set(x, y, -HZ, course);
				b.set(x, y, HZ, course);
			}
			for (int z = -HZ; z <= HZ; z++) {
				b.set(-HX, y, z, course);
				b.set(HX, y, z, course);
			}
			for (int[] c : new int[][] {{-HX, -HZ}, {HX, -HZ}, {-HX, HZ}, {HX, HZ}, {0, -HZ}, {0, HZ}}) {
				b.axis(c[0], y, c[1], FRAME, Direction.Axis.Y);
			}
		}
		for (int x = -HX + 2; x <= HX - 2; x += 3) {
			if (x != 0) {
				b.fill(x, 2, HZ, x, 3, HZ, Blocks.GLASS_PANE);
				b.fill(x, 7, HZ, x, 8, HZ, Blocks.GLASS_PANE);
				b.fill(x, 2, -HZ, x, 3, -HZ, Blocks.GLASS_PANE);
				b.fill(x, 7, -HZ, x, 8, -HZ, Blocks.GLASS_PANE);
			}
		}
		b.fill(-HX + 1, 5, -HZ + 1, HX - 1, 5, HZ - 1, Blocks.SPRUCE_PLANKS);
		// the stair up, along the north wall
		for (int i = 0; i < 4; i++) {
			b.stairs(2 + i, 1 + i, -HZ + 1, Blocks.SPRUCE_STAIRS, Direction.EAST, false);
			b.set(2 + i, 5, -HZ + 1, Blocks.AIR);
		}
		b.clear(-1, 1, HZ, 1, 3, HZ);
		b.door(0, 1, HZ, Blocks.SPRUCE_DOOR, Direction.SOUTH, false);
		b.door(-1, 1, HZ, Blocks.SPRUCE_DOOR, Direction.SOUTH, false);

		// the roof: pitched east to west, patched with copper
		for (int i = 0; i <= HZ + 1; i++) {
			int zn = -HZ - 1 + i;
			int zs = HZ + 1 - i;
			if (zn > zs) {
				break;
			}
			for (int x = -HX - 1; x <= HX + 1; x++) {
				Block stairs = b.chance(0.12F) ? Blocks.CUT_COPPER_STAIRS.waxed().weathered() : Blocks.SPRUCE_STAIRS;
				if (zn == zs) {
					b.set(x, 10 + i, zn, Blocks.SPRUCE_PLANKS);
				} else {
					b.stairs(x, 10 + i, zn, stairs, Direction.SOUTH, false);
					b.stairs(x, 10 + i, zs, stairs, Direction.NORTH, false);
				}
			}
			if (i > 0) {
				for (int z = zn + 1; z < zs; z++) {
					b.set(-HX, 9 + i, z, WALL);
					b.set(HX, 9 + i, z, WALL);
				}
			}
		}
		b.set(-HX + 2, 16, 0, Blocks.COBBLESTONE);
		b.set(-HX + 2, 17, 0, Blocks.CAMPFIRE);

		// the hearth on the west wall, the long table, the tag wall on the east
		b.fill(-HX + 1, 1, -1, -HX + 1, 4, 1, Blocks.STONE_BRICKS);
		b.set(HEARTH.getX(), 1, 0, Blocks.CAMPFIRE);
		b.set(HEARTH.getX(), 2, 0, Blocks.AIR);
		for (int x = -3; x <= 3; x++) {
			b.set(x, 1, 0, Blocks.SPRUCE_FENCE);
			b.set(x, 2, 0, Blocks.SPRUCE_PRESSURE_PLATE);
			b.facing(x, 1, -1, Blocks.SPRUCE_STAIRS, Direction.SOUTH);
			b.facing(x, 1, 1, Blocks.SPRUCE_STAIRS, Direction.NORTH);
		}
		b.set(-1, 2, 0, Blocks.CANDLE);
		b.set(2, 2, 0, Blocks.FLOWER_POT);
		for (int z = -HZ + 1; z <= HZ - 1; z++) {
			for (int y = 2; y <= 4; y++) {
				if ((z + y & 1) == 0) {
					b.facing(HX - 1, y, z, Blocks.SPRUCE_WALL_SIGN, Direction.WEST);
				}
			}
		}
		b.hanging(-2, 4, 0, Blocks.LANTERN);
		b.hanging(3, 4, 0, Blocks.LANTERN);
		b.container(HX - 1, 1, HZ - 1, Blocks.BARREL, Direction.UP, "minecraft:chests/village/village_plains_house");

		// upstairs: the rooms
		b.bed(BED.getX(), BED.getY(), BED.getZ(), Blocks.BED.lightBlue(), Direction.NORTH);
		b.bed(-HX + 2, 6, -HZ + 2, Blocks.BED.cyan(), Direction.NORTH);
		b.bed(-HX + 2, 6, HZ - 2, Blocks.BED.white(), Direction.SOUTH);
		b.bed(HX - 2, 6, HZ - 2, Blocks.BED.brown(), Direction.SOUTH);
		b.set(HX - 3, 6, -HZ + 1, Blocks.CHEST);
		b.hanging(0, 9, 0, Blocks.LANTERN);

		// the porch: the lamp always lit, the golden bell beside it
		for (int x = -3; x <= 3; x++) {
			b.set(x, 0, HZ + 1, Blocks.SPRUCE_PLANKS);
			b.set(x, 0, HZ + 2, Blocks.SPRUCE_PLANKS);
		}
		for (int x : new int[] {-3, 3}) {
			for (int y = 1; y <= 4; y++) {
				b.set(x, y, HZ + 2, Blocks.SPRUCE_FENCE);
			}
		}
		for (int x = -3; x <= 3; x++) {
			b.slab(x, 5, HZ + 1, Blocks.SPRUCE_SLAB, false);
			b.slab(x, 5, HZ + 2, Blocks.SPRUCE_SLAB, false);
		}
		b.hanging(2, 4, HZ + 2, Blocks.LANTERN);
		b.set(-2, 1, HZ + 2, MistinnBlocks.GOLDEN_BELL);

		// out the back: the herb beds and the well
		for (int x = -HX; x <= -1; x++) {
			for (int z = -HZ - 6; z <= -HZ - 3; z++) {
				b.set(x, 0, z, (x & 1) == 0 ? Blocks.FARMLAND : Blocks.DIRT_PATH);
				if ((x & 1) == 0) {
					b.set(x, 1, z, b.pick(Blocks.POTATOES, Blocks.CARROTS, Blocks.BEETROOTS, Blocks.WHEAT));
				}
			}
		}
		for (int x = 2; x <= 4; x++) {
			for (int z = -HZ - 5; z <= -HZ - 3; z++) {
				boolean rim = x != 3 || z != -HZ - 4;
				b.set(x, 0, z, rim ? Blocks.COBBLESTONE : Blocks.WATER);
				if (rim) {
					b.set(x, 1, z, Blocks.COBBLESTONE_WALL);
				}
			}
		}
		b.settle();
	}
}
