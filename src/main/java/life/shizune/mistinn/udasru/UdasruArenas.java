package life.shizune.mistinn.udasru;

import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.world.BellTower;
import life.shizune.mistinn.world.Builder;
import life.shizune.mistinn.world.SaltWorks;
import life.shizune.mistinn.world.Structures;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The grounds of Udasru's great beasts, built in Udasru's own way. Each keeps what its beast needs of a place (the
 * Sunken Bell a belfry deck of the same reach with a bell's lip over it, the Snuffer a lamp room ringed with lamp
 * stands, the Rust Wheel a walled yard with four pillars to break on), and is otherwise a place of the land: the salt
 * lake's bell temple, the lighthouse the old woods kept with no sea to light, and the forge yard where the wheel was cast.
 */
public final class UdasruArenas {
	private static final Block STONE = MistinnBlocks.UDASRU_STONE_BRICKS;
	private static final Block BRONZE = MistinnBlocks.BELL_BRONZE;
	private static final Block SALT = MistinnBlocks.SALT_CRUST;

	private UdasruArenas() {
	}

	// ── 鹽湖鐘殿: the Sunken Bell's temple in the salt lake ──────────

	/**
	 * A round temple standing out of the salt lake on salt-white piers: a stair of landings up to a round deck as wide
	 * as the old fog-bell's, eight bronze-banded columns holding a ring beam, and from it the great bell, its lip a ring of
	 * worn copper over the deck. Origin on the water at its middle.
	 */
	public static void saltTemple(final Builder b) {
		int deck = BellTower.DECK;
		double reach = BellTower.DECK_RADIUS;
		b.clear(-11, 1, -11, 11, deck + 14, 13);
		// the piers and the island they stand on
		for (int x = -10; x <= 10; x++) {
			for (int z = -10; z <= 10; z++) {
				double r = Math.hypot(x, z);
				if (r <= 10.2) {
					b.footing(x, 1, z, SALT, 16);
					b.set(x, 0, z, r > 8.5 ? SALT : STONE);
				}
			}
		}
		// the deck, round, on a core of stone
		for (int x = -9; x <= 9; x++) {
			for (int z = -9; z <= 9; z++) {
				double r = Math.hypot(x, z);
				if (r <= reach) {
					b.set(x, deck, z, (x * 3 + z * 5 & 3) == 0 ? BRONZE : r < 3 ? Blocks.POLISHED_DIORITE : STONE);
					if (r <= 3.2) {
						for (int y = 1; y < deck; y++) {
							b.set(x, y, z, r <= 2.2 ? Blocks.AIR : STONE);
						}
					}
				}
				if (r > reach - 0.6 && r <= reach + 0.4) {
					b.set(x, deck + 1, z, MistinnBlocks.UDASRU_STONE_BRICK_WALL);
				}
			}
		}
		// a ladder up the core, coming out on the deck
		for (int y = 1; y <= deck; y++) {
			b.facing(0, y, 2, Blocks.LADDER, Direction.NORTH);
		}
		b.clear(0, 1, 3, 0, 2, 3);
		b.door(0, 1, 3, Blocks.DARK_OAK_DOOR, Direction.SOUTH, false);
		b.set(0, deck, 2, Blocks.AIR);
		b.facing(0, deck, 2, Blocks.LADDER, Direction.NORTH);
		// eight columns and the ring beam
		for (int k = 0; k < 8; k++) {
			double a = k * Math.PI / 4.0;
			int x = (int) Math.round(Math.cos(a) * (reach - 1.0));
			int z = (int) Math.round(Math.sin(a) * (reach - 1.0));
			for (int y = deck + 1; y <= deck + 8; y++) {
				b.set(x, y, z, y % 3 == 0 ? BRONZE : STONE);
			}
		}
		for (int a = 0; a < 72; a++) {
			double angle = a * Math.PI * 2.0 / 72.0;
			b.set((int) Math.round(Math.cos(angle) * (reach - 1.0)), deck + 9, (int) Math.round(Math.sin(angle) * (reach - 1.0)), STONE);
		}
		for (int x = -7; x <= 7; x++) {
			b.axis(x, deck + 10, 0, MistinnBlocks.UDASRU_TIMBER, Direction.Axis.X);
		}
		// the great bell: crown, shoulder and lip (the lip where the old one's was)
		int lip = BellTower.BELL_LIP;
		for (int y = lip + 3; y < deck + 10; y++) {
			b.axis(0, y, 0, Blocks.IRON_CHAIN, Direction.Axis.Y);
		}
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				boolean corner = Math.abs(x) == 2 && Math.abs(z) == 2;
				if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
					b.set(x, lip + 2, z, BRONZE);
				}
				if (!corner) {
					b.set(x, lip + 1, z, Math.abs(x) <= 1 && Math.abs(z) <= 1 ? Blocks.AIR : BRONZE);
				}
				if (!corner && (Math.abs(x) == 2 || Math.abs(z) == 2)) {
					b.set(x, lip, z, Blocks.CUT_COPPER.waxed().exposed());
				}
			}
		}
		b.set(0, lip + 1, 0, Blocks.IRON_CHAIN);
		for (int k = 0; k < 4; k++) {
			double a = Math.PI / 4.0 + k * Math.PI / 2.0;
			b.hanging((int) Math.round(Math.cos(a) * 5), deck + 8, (int) Math.round(Math.sin(a) * 5), Blocks.LANTERN);
		}
		b.settle();
		b.spawn(MistinnEntities.SUNKEN_BELL, 0, deck + 1, 0, 270.0F);
	}

	// ── 林中燈塔: the lighthouse with no sea ─────────────────────────

	/**
	 * The old woods' lighthouse: an eight-sided tower of limestone banded with mist pine, a stair winding up inside, and
	 * at the top a lamp room of glass ringed with lamp stands the Snuffer keeps dark. It never lit a ship: the woods kept
	 * it lit so the fog would know where the land was. Origin on the ground at its middle.
	 */
	public static void woodsLighthouse(final Builder b) {
		int room = Structures.LAMP_ROOM;
		b.clear(-9, 1, -9, 9, room + 12, 9);
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				if (Math.abs(x) + Math.abs(z) > 10) {
					continue;
				}
				b.footing(x, z, STONE, 20);
				b.set(x, 0, z, STONE);
			}
		}
		// the shaft: an octagon five out, walls a block thick
		for (int y = 1; y <= room; y++) {
			for (int x = -5; x <= 5; x++) {
				for (int z = -5; z <= 5; z++) {
					int d = Math.max(Math.abs(x), Math.abs(z));
					boolean octagon = Math.abs(x) + Math.abs(z) <= 7;
					boolean wall = octagon && (d == 5 || Math.abs(x) + Math.abs(z) == 7);
					if (wall) {
						b.set(x, y, z, y % 6 == 0 ? MistinnBlocks.MIST_PINE_LOG : STONE);
					}
				}
			}
			if (y % 5 == 2) {
				b.set(0, y, -5, Blocks.GLASS_PANE);
				b.set(5, y, 0, Blocks.GLASS_PANE);
				b.set(-5, y, 0, Blocks.GLASS_PANE);
			}
		}
		// the stair: round the inside, a step a block
		int[][] ring = {{-3, 3}, {-2, 3}, {-1, 3}, {0, 3}, {1, 3}, {2, 3}, {3, 3}, {3, 2}, {3, 1}, {3, 0}, {3, -1}, {3, -2}, {3, -3}, {2, -3}, {1, -3},
			{0, -3}, {-1, -3}, {-2, -3}, {-3, -3}, {-3, -2}, {-3, -1}, {-3, 0}, {-3, 1}, {-3, 2}};
		for (int y = 1; y < room; y++) {
			int[] at = ring[(y - 1) % ring.length];
			int[] next = ring[y % ring.length];
			b.stairs(at[0], y, at[1], MistinnBlocks.UDASRU_STONE_BRICK_STAIRS, Direction.getApproximateNearest(next[0] - at[0], 0, next[1] - at[1]), false);
			b.set(at[0], y - 1, at[1], STONE);
			b.clear(at[0], y + 1, at[1], at[0], y + 3, at[1]);
		}
		b.clear(0, 1, 5, 0, 2, 5);
		b.door(0, 1, 5, MistinnBlocks.MIST_PINE_DOOR, Direction.NORTH, false);
		// the lamp room
		for (int x = -5; x <= 5; x++) {
			for (int z = -5; z <= 5; z++) {
				if (Math.abs(x) + Math.abs(z) > 7) {
					continue;
				}
				b.set(x, room, z, MistinnBlocks.MIST_PINE_PLANKS);
				boolean wall = Math.max(Math.abs(x), Math.abs(z)) == 5 || Math.abs(x) + Math.abs(z) == 7;
				for (int y = room + 1; y <= room + 5; y++) {
					b.set(x, y, z, wall ? y == room + 1 || y == room + 5 ? STONE : Blocks.GLASS_PANE : Blocks.AIR);
				}
				b.set(x, room + 6, z, MistinnBlocks.UDASRU_ROOF_TILES);
			}
		}
		int[] last = ring[(room - 2) % ring.length];
		b.set(last[0], room, last[1], Blocks.AIR);
		for (int[] c : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
			b.set(c[0], room + 1, c[1], MistinnBlocks.LAMP_STAND);
		}
		// the great lamp in the middle, dark
		b.set(0, room + 1, 0, BRONZE);
		b.set(0, room + 2, 0, Blocks.GLASS);
		b.set(0, room + 3, 0, BRONZE);
		for (int i = 0; i <= 3; i++) {
			b.fill(-3 + i, room + 7 + i, -3 + i, 3 - i, room + 7 + i, 3 - i, MistinnBlocks.UDASRU_ROOF_TILES);
		}
		b.set(0, room + 11, 0, BRONZE);
		b.settle();
		b.spawn(MistinnEntities.SNUFFER, 0, room + 1, 3, 180.0F);
	}

	// ── 大鏽輪場: the forge yard where the wheel was cast ─────────────

	/**
	 * The yard the rust wastes' smiths cast the great wheel in: rust-red ground walled round with fired brick, a gate
	 * to the south, four great pillars of bronze-bound stone the wheel breaks on, and the cold casting pit's chimneys
	 * at the corners. Origin on the ground at its middle.
	 */
	public static void forgeYard(final Builder b) {
		int yard = SaltWorks.YARD;
		b.clear(-yard - 3, 1, -yard - 3, yard + 3, 12, yard + 3);
		for (int x = -yard - 2; x <= yard + 2; x++) {
			for (int z = -yard - 2; z <= yard + 2; z++) {
				b.footing(x, z, MistinnBlocks.RUST_SOIL, 20);
				b.set(x, 0, z, b.chance(0.15F) ? Blocks.COARSE_DIRT : MistinnBlocks.RUST_SOIL);
				boolean wall = Math.abs(x) == yard + 1 || Math.abs(z) == yard + 1;
				if (wall && !(z == yard + 1 && Math.abs(x) <= SaltWorks.GATE)) {
					for (int y = 1; y <= 6; y++) {
						b.set(x, y, z, y == 6 ? Blocks.MUD_BRICK_WALL : y == 4 ? BRONZE : Blocks.BRICKS);
					}
				}
			}
		}
		for (int[] at : SaltWorks.PILLARS) {
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					for (int y = 1; y <= 7; y++) {
						b.set(at[0] + x, y, at[1] + z, y % 3 == 0 ? BRONZE : STONE);
					}
				}
			}
			b.set(at[0], 8, at[1], Blocks.LANTERN);
		}
		for (int[] c : new int[][] {{-yard - 1, -yard - 1}, {yard + 1, -yard - 1}, {-yard - 1, yard + 1}, {yard + 1, yard + 1}}) {
			for (int y = 1; y <= 11; y++) {
				b.set(c[0], y, c[1], Blocks.BRICKS);
			}
			b.set(c[0], 12, c[1], Blocks.CAMPFIRE);
		}
		b.facing(-yard + 2, 1, -yard + 1, Blocks.ANVIL, Direction.EAST);
		b.facing(yard - 2, 1, -yard + 1, Blocks.BLAST_FURNACE, Direction.SOUTH);
		b.set(0, 1, -yard + 1, Blocks.SMITHING_TABLE);
		for (int x = -SaltWorks.GATE; x <= SaltWorks.GATE; x++) {
			b.set(x, 7, yard + 1, Blocks.BRICKS);
			b.set(x, 6, yard + 1, BRONZE);
		}
		b.settle();
		b.spawn(MistinnEntities.RUST_WHEEL, 0, 1, 0, 0.0F);
	}

	// ── 晶冠祭壇: the crown peak's altar ────────────────────────────

	/** A ring of crystal-bound stone on the crown peak's summit, where the elder comes down to its nest. */
	public static void crownAltar(final Builder b) {
		b.clear(-8, 1, -8, 8, 6, 8);
		for (int x = -8; x <= 8; x++) {
			for (int z = -8; z <= 8; z++) {
				double r = Math.hypot(x, z);
				if (r > 8.3) {
					continue;
				}
				b.footing(x, z, MistinnBlocks.VEIN_STONE, 20);
				b.set(x, 0, z, r < 3 ? Blocks.POLISHED_DIORITE : MistinnBlocks.VEIN_STONE_POLISHED);
				if (r > 7.2 && ((int) Math.round(Math.atan2(z, x) * 8 / Math.PI) & 1) == 0) {
					b.set(x, 1, z, MistinnBlocks.VEIN_STONE_BRICKS);
					b.set(x, 2, z, Blocks.AMETHYST_CLUSTER);
				}
			}
		}
		b.settle();
	}
}
