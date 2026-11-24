package life.shizune.mistinn.world;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.registry.MistinnBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignText;

/**
 * 霧宿: the inn the hunters who never went home built in the mist, out of the copper plates of fallen drop pods and the
 * timber of the lighthouses. Two floors of spruce under a roof patched with pod copper, a lamp always lit by the door
 * and a golden bell beside it; inside, a hearth in a stone fireplace, a long table, and a wall of boards with the
 * numbers of the hunters who came through, and up the ladder their beds and stores. Built with its origin on the ground
 * at the middle of the house, the door to the south.
 */
public final class MistInn {
	/** Half the house's width (east to west) and depth (north to south), to the outside of its walls. */
	private static final int HALF_X = 5;
	private static final int HALF_Z = 4;
	/** Where the hearth burns, from the origin: the place the story's salt is thrown into. */
	public static final BlockPos HEARTH = new BlockPos(-4, 1, 0);
	/** Where a hunter stands at its door, from the origin. */
	public static final BlockPos DOOR = new BlockPos(0, 1, HALF_Z + 2);
	/** The numbers on the boards inside: the three of the salt road among others who came through. */
	private static final int[][] TAGS = {{3388, 3401, 3409}, {3392, 3397, 3405}};

	private MistInn() {
	}

	/** Builds the inn and returns where its hearth burns. */
	public static BlockPos build(final Builder b) {
		b.clear(-HALF_X - 2, 1, -HALF_Z - 2, HALF_X + 2, 13, HALF_Z + 5);
		floor(b);
		walls(b);
		upstairs(b);
		roof(b);
		hearth(b);
		inside(b);
		outside(b);
		b.settle();
		return b.at(HEARTH.getX(), HEARTH.getY(), HEARTH.getZ());
	}

	private static void floor(final Builder b) {
		for (int x = -HALF_X; x <= HALF_X; x++) {
			for (int z = -HALF_Z; z <= HALF_Z; z++) {
				boolean edge = Math.abs(x) == HALF_X || Math.abs(z) == HALF_Z;
				b.set(x, 0, z, edge ? Blocks.STONE_BRICKS : Blocks.SPRUCE_PLANKS);
				b.footing(x, z, Blocks.COBBLESTONE, 8);
			}
		}
	}

	/** The ground floor's walls and the upper floor's, logs at the corners, windows on every side, the door to the south. */
	private static void walls(final Builder b) {
		for (int y = 1; y <= 6; y++) {
			if (y == 4) {
				continue;
			}
			for (int x = -HALF_X; x <= HALF_X; x++) {
				for (int z = -HALF_Z; z <= HALF_Z; z++) {
					boolean xEdge = Math.abs(x) == HALF_X;
					boolean zEdge = Math.abs(z) == HALF_Z;
					if (!xEdge && !zEdge) {
						continue;
					}
					if (xEdge && zEdge) {
						b.axis(x, y, z, y <= 3 ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.DARK_OAK_LOG, Direction.Axis.Y);
					} else {
						b.set(x, y, z, b.chance(0.12F) ? Blocks.STRIPPED_SPRUCE_WOOD : Blocks.SPRUCE_PLANKS);
					}
				}
			}
		}
		// a beam of dark wood between the floors all round
		for (int x = -HALF_X; x <= HALF_X; x++) {
			b.axis(x, 4, -HALF_Z, Blocks.DARK_OAK_LOG, Direction.Axis.X);
			b.axis(x, 4, HALF_Z, Blocks.DARK_OAK_LOG, Direction.Axis.X);
		}
		for (int z = -HALF_Z + 1; z < HALF_Z; z++) {
			b.axis(-HALF_X, 4, z, Blocks.DARK_OAK_LOG, Direction.Axis.Z);
			b.axis(HALF_X, 4, z, Blocks.DARK_OAK_LOG, Direction.Axis.Z);
		}
		// windows, warm with the light inside
		for (int[] w : new int[][] {{-3, 2, HALF_Z}, {3, 2, HALF_Z}, {-2, 2, -HALF_Z}, {2, 2, -HALF_Z}, {0, 5, HALF_Z}, {0, 5, -HALF_Z}}) {
			b.set(w[0], w[1], w[2], Blocks.GLASS_PANE);
		}
		for (int[] w : new int[][] {{HALF_X, 2, -1}, {HALF_X, 2, 1}, {-HALF_X, 5, 0}, {HALF_X, 5, 0}}) {
			b.set(w[0], w[1], w[2], Blocks.GLASS_PANE);
		}
		b.door(0, 1, HALF_Z, Blocks.SPRUCE_DOOR, Direction.SOUTH, false);
	}

	/** The upper floor, and the ladder up to it in the north-east corner. */
	private static void upstairs(final Builder b) {
		for (int x = -HALF_X + 1; x < HALF_X; x++) {
			for (int z = -HALF_Z + 1; z < HALF_Z; z++) {
				b.set(x, 4, z, Blocks.SPRUCE_PLANKS);
			}
		}
		for (int y = 1; y <= 4; y++) {
			b.facing(HALF_X - 1, y, -HALF_Z + 1, Blocks.LADDER, Direction.WEST);
		}
		b.set(HALF_X - 1, 5, -HALF_Z + 1, Blocks.AIR);
		// beds along the north wall, and the stores
		b.bed(-3, 5, -HALF_Z + 2, Blocks.BED.brown(), Direction.SOUTH);
		b.bed(-1, 5, -HALF_Z + 2, Blocks.BED.gray(), Direction.SOUTH);
		b.bed(1, 5, -HALF_Z + 2, Blocks.BED.brown(), Direction.SOUTH);
		b.container(3, 5, 2, Blocks.BARREL, Direction.UP, "chests/drop_station");
		b.container(-3, 5, 2, Blocks.BARREL, Direction.UP, "chests/drop_station");
		b.hanging(0, 6, 0, Blocks.LANTERN);
	}

	/**
	 * The roof, gabled east to west, laid with the copper plates of fallen pods where there were plates enough and
	 * patched with spruce where there were not; the gable ends boarded up.
	 */
	private static void roof(final Builder b) {
		int eaves = HALF_Z + 1;
		for (int k = 0; k <= eaves; k++) {
			int y = 7 + k;
			for (int x = -HALF_X - 1; x <= HALF_X + 1; x++) {
				for (int side : new int[] {-1, 1}) {
					int z = side * (eaves - k);
					Direction faces = side < 0 ? Direction.SOUTH : Direction.NORTH;
					if (z == 0) {
						b.set(x, y, 0, b.chance(0.6F) ? Blocks.CUT_COPPER.waxed().weathered() : Blocks.SPRUCE_PLANKS);
						continue;
					}
					Block plate = b.chance(0.15F) ? Blocks.SPRUCE_STAIRS
						: b.chance(0.5F) ? Blocks.CUT_COPPER_STAIRS.waxed().oxidized() : Blocks.CUT_COPPER_STAIRS.waxed().weathered();
					b.stairs(x, y, z, plate, faces, false);
				}
			}
			// the gable ends, boarded
			for (int z = -(eaves - k) + 1; z < eaves - k; z++) {
				b.set(-HALF_X, y, z, Blocks.SPRUCE_PLANKS);
				b.set(HALF_X, y, z, Blocks.SPRUCE_PLANKS);
			}
		}
		// the chimney up the west end
		for (int y = 1; y <= 13; y++) {
			b.set(-HALF_X - 1, y, 0, y > 11 ? Blocks.STONE_BRICKS : Blocks.COBBLESTONE);
		}
		b.set(-HALF_X - 1, 14, 0, Blocks.CAMPFIRE);
	}

	/** The hearth: a fire in a fireplace of stone against the west wall. */
	private static void hearth(final Builder b) {
		int x = HEARTH.getX();
		for (int z = -1; z <= 1; z++) {
			b.set(x, 3, z, Blocks.STONE_BRICKS);
		}
		b.set(x, 1, -1, Blocks.STONE_BRICKS);
		b.set(x, 1, 1, Blocks.STONE_BRICKS);
		b.set(x, 2, -1, Blocks.STONE_BRICK_WALL);
		b.set(x, 2, 1, Blocks.STONE_BRICK_WALL);
		b.set(x, 1, 0, Blocks.CAMPFIRE);
		b.set(x + 1, 0, 0, Blocks.STONE_BRICKS);
	}

	/** The long table, its benches, the lamp over it and the boards with the hunters' numbers on the north wall. */
	private static void inside(final Builder b) {
		for (int x = -2; x <= 2; x++) {
			b.set(x, 1, 0, Blocks.SPRUCE_FENCE);
			b.set(x, 2, 0, Blocks.SPRUCE_PRESSURE_PLATE);
			b.stairs(x, 1, -1, Blocks.SPRUCE_STAIRS, Direction.NORTH, false);
			b.stairs(x, 1, 1, Blocks.SPRUCE_STAIRS, Direction.SOUTH, false);
		}
		b.candles(1, 2, 0, Blocks.CANDLE, 3, true);
		b.hanging(0, 3, 0, Blocks.LANTERN);
		b.hanging(-2, 3, 2, Blocks.LANTERN);
		for (int i = 0; i < TAGS.length; i++) {
			b.sign(-1 + 2 * i, 2, -HALF_Z + 1, Blocks.SPRUCE_WALL_SIGN, Direction.SOUTH, numbers(TAGS[i]));
		}
	}

	/** The lamp by the door that is never let go out, the golden bell, and a trodden path up to the door. */
	private static void outside(final Builder b) {
		for (int z = HALF_Z + 1; z <= HALF_Z + 5; z++) {
			for (int x = -1; x <= 1; x++) {
				b.set(x, 0, z, b.chance(0.7F) ? Blocks.DIRT_PATH : Blocks.COARSE_DIRT);
			}
		}
		b.set(2, 1, HALF_Z + 1, Blocks.SPRUCE_FENCE);
		b.set(2, 2, HALF_Z + 1, Blocks.SPRUCE_FENCE);
		b.set(2, 3, HALF_Z + 1, Blocks.LANTERN);
		b.set(-3, 0, HALF_Z + 2, Blocks.STONE_BRICKS);
		b.set(-3, 1, HALF_Z + 2, MistinnBlocks.GOLDEN_BELL);
	}

	private static SignText numbers(final int[] tags) {
		List<Component> lines = new ArrayList<>();
		for (int number : tags) {
			lines.add(Component.translatable("item.mistinn.hunter_tag.numbered", number));
		}
		while (lines.size() < 4) {
			lines.add(Component.empty());
		}
		return new SignText(lines, lines, DyeColor.BROWN, false);
	}
}
