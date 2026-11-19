package life.shizune.mistinn.world;

import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The buildings of the border world, drawn block by block. Origins are the centre of the floor
 * (the floor replaces the top layer of the ground).
 */
public final class Structures {
	private Structures() {
	}

	/** How a drop station looks: as Rust Anchor built it, fallen apart, or faked by someone else. */
	public enum Station {
		/** 追獵者投放站: where hunters are dropped into the zone. Found across the world. */
		INTACT,
		/** 破損的追獵者投放站: one of the spawn-point stations; long abandoned. */
		BROKEN,
		/** 仿冒的追獵者投放站: the other spawn-point station; built by someone copying Rust Anchor. */
		COUNTERFEIT
	}

	/** Where an intact station keeps its radio, relative to the station's origin. */
	public static final BlockPos RADIO = new BlockPos(2, 1, -3);

	/**
	 * A 9x9 landing pad with a drop pod in the middle, a signal mast and a supply barrel.
	 *
	 * @return where a hunter can stand on the pad, relative to the origin (always clear)
	 */
	public static BlockPos station(final Builder b, final Station kind) {
		boolean broken = kind == Station.BROKEN;
		boolean fake = kind == Station.COUNTERFEIT;
		b.clear(-4, 1, -4, 4, 7, 4);

		// the pad
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 4; z++) {
				boolean corner = Math.abs(x) == 4 && Math.abs(z) == 4;
				boolean edge = Math.abs(x) == 4 || Math.abs(z) == 4;
				Block block;
				if (fake) {
					block = corner ? Blocks.SPRUCE_PLANKS : (edge ? Blocks.STRIPPED_OAK_LOG : Blocks.OAK_PLANKS);
				} else {
					block = corner ? Blocks.CHISELED_DEEPSLATE : (edge ? Blocks.DEEPSLATE_TILES : Blocks.POLISHED_DEEPSLATE);
				}
				if (broken) {
					float roll = b.random().nextFloat();
					if (roll < 0.12F && edge) {
						continue; // a gap where the pad has fallen away
					}
					if (roll < 0.35F) {
						block = b.random().nextBoolean() ? Blocks.CRACKED_DEEPSLATE_BRICKS : Blocks.COBBLED_DEEPSLATE;
					}
				}
				b.set(x, 0, z, block);
				b.footing(x, z, fake ? Blocks.STRIPPED_OAK_LOG : Blocks.COBBLED_DEEPSLATE, 8);
				if (broken && !edge && b.random().nextFloat() < 0.18F) {
					b.set(x, 1, z, Blocks.MOSS_CARPET);
				}
			}
		}

		// the drop pod: a 3x3 shell, open to the north
		Block wall = fake ? Blocks.TERRACOTTA : (broken ? Blocks.CUT_COPPER.waxed().oxidized() : Blocks.CUT_COPPER.waxed().weathered());
		Block cap = fake ? Blocks.DYED_TERRACOTTA.orange() : (broken ? Blocks.COPPER_BLOCK.waxed().oxidized() : Blocks.COPPER_BLOCK.waxed().weathered());
		for (int y = 1; y <= 3; y++) {
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					boolean shell = Math.abs(x) == 1 || Math.abs(z) == 1;
					boolean door = z == -1 && x == 0 && y <= 2;
					if (!shell || door) {
						continue;
					}
					if (broken && b.random().nextFloat() < 0.3F) {
						continue;
					}
					b.set(x, y, z, wall);
				}
			}
		}
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				if (!broken || b.random().nextFloat() > 0.45F) {
					b.set(x, 4, z, cap);
				}
			}
		}
		if (broken) {
			b.set(0, 1, 0, Blocks.COBWEB);
		}

		// the signal mast in the south-east corner
		Block mast = fake ? Blocks.OAK_FENCE : Blocks.IRON_BARS;
		int mastHeight = broken ? 2 : 5;
		for (int y = 1; y <= mastHeight; y++) {
			b.set(3, y, 3, mast);
		}
		if (broken) {
			b.set(2, 1, 3, Blocks.SOUL_LANTERN); // knocked off and lying on the pad
		} else {
			b.set(3, mastHeight + 1, 3, fake ? Blocks.LANTERN : Blocks.SOUL_LANTERN);
		}

		// supplies, and the bell a real station keeps
		b.container(-3, 1, 3, Blocks.BARREL, Direction.UP, kind == Station.INTACT ? "chests/drop_station" : "chests/spawn_station");
		if (kind == Station.INTACT) {
			b.set(-3, 1, -3, MistinnBlocks.ORE_BELL);
			// the radio, burned out, facing the hunter's spot on the pad
			b.set(RADIO.getX(), RADIO.getY(), RADIO.getZ(), MistinnBlocks.RADIO.defaultBlockState().setValue(life.shizune.mistinn.story.RadioBlock.FACING, Direction.WEST));
		} else if (fake) {
			// a vanilla bell where the golden one should be: it rings, but it will not call you back
			b.set(-3, 1, -3, Blocks.BELL.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		}
		return new BlockPos(0, 1, -3);
	}

	/** 鹽塚: a low mound of salt with a chest on its crown. */
	public static void saltCairn(final Builder b) {
		for (int y = 0; y <= 3; y++) {
			int r = 3 - y;
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					if (x * x + z * z > r * r + 1) {
						continue;
					}
					Block block = switch (y) {
						case 0 -> Blocks.CALCITE;
						case 1 -> b.random().nextBoolean() ? Blocks.CALCITE : Blocks.SMOOTH_QUARTZ;
						default -> Blocks.QUARTZ_BLOCK;
					};
					b.set(x, y, z, block);
					if (y == 0) {
						b.footing(x, z, Blocks.CALCITE, 4);
					}
				}
			}
		}
		b.clear(-1, 4, -1, 1, 5, 1);
		b.container(0, 4, 0, Blocks.CHEST, Direction.SOUTH, "chests/salt_cairn");
	}

	/** 鏽錨殘骸: a huge rusted anchor half sunk in the sand, something buried beside it. */
	public static void anchorWreck(final Builder b) {
		Block rust = Blocks.CUT_COPPER.waxed().oxidized();
		Block worn = Blocks.COPPER_BLOCK.waxed().weathered();
		for (int y = -2; y <= 6; y++) {
			b.set(0, y, 0, rust); // shank
		}
		for (int x = -2; x <= 2; x++) {
			b.set(x, 5, 0, worn); // stock
		}
		b.set(0, 7, 0, Blocks.IRON_CHAIN);
		b.set(0, 8, 0, Blocks.IRON_CHAIN);
		for (int x = -3; x <= 3; x++) {
			int y = -2 + (Math.abs(x) * Math.abs(x)) / 3; // the curved arms
			b.set(x, y, 0, rust);
		}
		b.set(-3, 0, 0, worn); // flukes
		b.set(3, 0, 0, worn);
		b.set(-3, 1, 0, rust);
		b.set(3, 1, 0, rust);
		b.container(2, -1, 1, Blocks.CHEST, Direction.NORTH, "chests/anchor_wreck");
	}

	/** 霧中崗哨: a small stone watchtower with a lantern and a lookout's chest at the top. */
	public static void watchpost(final Builder b) {
		int top = 8;
		b.clear(-2, 1, -2, 2, top + 2, 2);
		for (int y = 0; y <= top; y++) {
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2;
					boolean floor = y == 0 || y == top;
					if (!wall && !floor) {
						continue;
					}
					if (wall && z == -2 && x == 0 && (y == 1 || y == 2)) {
						continue; // doorway
					}
					float roll = b.random().nextFloat();
					Block block = roll < 0.2F ? Blocks.MOSSY_STONE_BRICKS : (roll < 0.35F ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS);
					b.set(x, y, z, block);
					if (y == 0) {
						b.footing(x, z, Blocks.STONE_BRICKS, 8);
					}
				}
			}
		}
		for (int y = 1; y <= top; y++) {
			b.facing(0, y, 1, Blocks.LADDER, Direction.NORTH);
		}
		// battlements
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				if ((Math.abs(x) == 2 || Math.abs(z) == 2) && (x + z) % 2 == 0) {
					b.set(x, top + 1, z, Blocks.STONE_BRICKS);
				}
			}
		}
		b.set(1, top + 1, -1, Blocks.SOUL_LANTERN);
		b.container(-1, top + 1, -1, Blocks.CHEST, Direction.SOUTH, "chests/watchpost");
	}

	// ── 霧岸燈塔 ──────────────────────────────────────────────────

	/** Height of the lamp room floor above the ground. */
	public static final int LAMP_ROOM = 21;

	/**
	 * 霧岸燈塔: a dead lighthouse on the shore. A golden bell waits by the door; a ladder climbs the
	 * tower past a supply floor; at the top, the lamp room is the first keeper's arena, ringed by
	 * iron bars, with a lamp stand in each corner. Returns the room's floor centre.
	 */
	public static BlockPos lighthouse(final Builder b) {
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				double r = Math.sqrt(x * x + z * z);
				if (r <= 5.5) {
					b.footing(x, z, Blocks.STONE_BRICKS, 12);
					b.set(x, 0, z, (x + z) % 3 == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS);
				}
			}
		}
		b.clear(-6, 1, -6, 6, LAMP_ROOM + 4, 6);
		// the tower: a ring of weathered stone with pale bands, hollow inside
		for (int y = 1; y < LAMP_ROOM; y++) {
			for (int x = -4; x <= 4; x++) {
				for (int z = -4; z <= 4; z++) {
					double r = Math.sqrt(x * x + z * z);
					if (r > 3.4 && r <= 4.4) {
						Block wall = y % 6 == 0 ? Blocks.CALCITE : b.random().nextFloat() < 0.18F ? Blocks.CRACKED_STONE_BRICKS : b.random().nextFloat() < 0.15F ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS;
						b.set(x, y, z, wall);
					}
				}
			}
			// narrow windows on the east and west walls
			if (y % 5 == 3) {
				b.set(4, y, 0, Blocks.AIR);
				b.set(-4, y, 0, Blocks.AIR);
			}
		}
		// door to the south, the golden bell by it
		b.clear(0, 1, 3, 0, 2, 4);
		b.set(0, 1, 7, MistinnBlocks.GOLDEN_BELL);
		b.footing(0, 7, Blocks.STONE_BRICKS, 12);
		// the ladder up the north wall, through both floors
		for (int y = 1; y <= LAMP_ROOM + 1; y++) {
			b.set(0, y, -3, Blocks.STONE_BRICKS);
			b.facing(0, y, -2, Blocks.LADDER, Direction.SOUTH);
		}
		// the supply floor halfway up
		int mid = 11;
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				if (Math.sqrt(x * x + z * z) <= 3.4 && !(x == 0 && z == -2)) {
					b.set(x, mid, z, Blocks.SPRUCE_PLANKS);
				}
			}
		}
		b.container(2, mid + 1, 1, Blocks.CHEST, Direction.WEST, "chests/lighthouse");
		b.container(-2, mid + 1, 1, Blocks.BARREL, Direction.UP, "chests/lighthouse");
		b.set(-2, mid + 1, -1, Blocks.LANTERN);
		// the lamp room: a broad floor, a ring of bars, four lamp stands and the great dead lamp
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				double r = Math.sqrt(x * x + z * z);
				if (r <= 7.4 && !(x == 0 && z == -2)) {
					b.set(x, LAMP_ROOM, z, r <= 2.4 ? Blocks.POLISHED_DEEPSLATE : Blocks.STONE_BRICKS);
				}
				if (r > 6.4 && r <= 7.4) {
					b.set(x, LAMP_ROOM + 1, z, Blocks.IRON_BARS);
				}
			}
		}
		for (int[] corner : new int[][] {{4, 4}, {-4, 4}, {4, -4}, {-4, -4}}) {
			b.set(corner[0], LAMP_ROOM + 1, corner[1], MistinnBlocks.LAMP_STAND);
		}
		b.set(0, LAMP_ROOM + 1, 0, Blocks.COPPER_BLOCK.waxed().oxidized());
		b.set(0, LAMP_ROOM + 2, 0, Blocks.COPPER_GRATE.waxed().oxidized());
		b.set(0, LAMP_ROOM + 3, 0, Blocks.COPPER_BULB.waxed().oxidized());
		b.spawn(MistinnEntities.SNUFFER, 0, LAMP_ROOM + 1, 3, 180.0F);
		return b.at(0, LAMP_ROOM + 1, 0);
	}
}
