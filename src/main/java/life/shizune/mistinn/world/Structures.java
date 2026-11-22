package life.shizune.mistinn.world;

import java.util.HashMap;
import java.util.Map;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.story.RadioBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The buildings of the border world, drawn block by block. Origins are the centre of the floor
 * (the floor replaces the top layer of the ground).
 *
 * <p>Every building tells what happened to the people who used it: who waited, who fled, what
 * came out of the mist. The details are vanilla blocks, chosen so a player reads the story
 * without a word of text.
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

	// ── 追獵者投放站 ──────────────────────────────────────────────

	/**
	 * A 9x9 landing pad on a stepped plinth, with a drop pod in the middle, a signal mast, a bell
	 * frame, the radio and a supply barrel. The ground around it is scorched where pods came down.
	 *
	 * <ul>
	 *   <li><b>Intact:</b> kept by Rust Anchor. A copper landing ring, a pod with its hatch thrown
	 *   open, a lit mast, the ore bell hung in its frame, and a worn path north: the way every
	 *   hunter dropped here walked off into the zone.</li>
	 *   <li><b>Broken:</b> no one came back for it. The pad has cracked and sunk at one corner,
	 *   moss and grass have taken it, the pod's nose lies where it fell, the mast is snapped and
	 *   the bell has been cut down; only its chain still hangs. A skull waits by the supplies.</li>
	 *   <li><b>Counterfeit:</b> someone built it from what they had seen: planks for deepslate,
	 *   painted clay for copper, a note block for a radio and a village bell in the frame. They
	 *   camped beside it, waiting for a drop that never came.</li>
	 * </ul>
	 *
	 * @return where a hunter can stand on the pad, relative to the origin (always clear)
	 */
	public static BlockPos station(final Builder b, final Station kind) {
		b.clear(-4, 1, -4, 4, 7, 4);
		stationGround(b, kind);
		stationPad(b, kind);
		stationPod(b, kind);
		stationMast(b, kind);
		stationBell(b, kind);
		stationRadio(b, kind);
		stationSupplies(b, kind);
		if (kind == Station.INTACT) {
			stationTrail(b);
		} else if (kind == Station.COUNTERFEIT) {
			counterfeitCamp(b);
		}
		// the hunter's spot stays clear whatever fell around it
		b.clear(0, 1, -3, 0, 3, -3);
		b.settle();
		return new BlockPos(0, 1, -3);
	}

	/** The plinth's steps and the scorched ring of ground around the pad (within six blocks). */
	private static void stationGround(final Builder b, final Station kind) {
		boolean fake = kind == Station.COUNTERFEIT;
		Block step = fake ? Blocks.SPRUCE_STAIRS : Blocks.COBBLED_DEEPSLATE_STAIRS;
		Block plinth = fake ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.COBBLED_DEEPSLATE;
		for (int x = -6; x <= 6; x++) {
			for (int z = -6; z <= 6; z++) {
				int ring = Math.max(Math.abs(x), Math.abs(z));
				if (ring < 5 || (Math.abs(x) == 6 && Math.abs(z) == 6)) {
					continue;
				}
				int ground = b.surface(x, z);
				if (ground < -4 || ground > 3 || b.wet(x, z)) {
					continue;
				}
				// cut back rises so the pad is not walled in
				for (int y = ground; y >= 1; y--) {
					b.set(x, y, z, Blocks.AIR);
				}
				if (ring == 5 && ground < 0) {
					// a step up to the pad, on a footing down to the ground
					boolean corner = Math.abs(x) == 5 && Math.abs(z) == 5;
					if (corner) {
						b.set(x, 0, z, plinth);
					} else {
						Direction in = Math.abs(x) == 5 ? (x > 0 ? Direction.WEST : Direction.EAST) : (z > 0 ? Direction.NORTH : Direction.SOUTH);
						b.stairs(x, 0, z, step, in, false);
					}
					b.footing(x, z, plinth, 6);
					continue;
				}
				int top = Math.min(ground, 0);
				b.set(x, top, z, scorch(b, kind, Math.atan2(z, x)));
			}
		}
	}

	/**
	 * What the ground around a station has become: burnt by pods in streaks fanning out from the
	 * pad, overgrown, or trodden flat.
	 */
	private static Block scorch(final Builder b, final Station kind, final double angle) {
		float roll = b.random().nextFloat();
		boolean streak = Math.sin(angle * 7.0 + 1.3) > 0.15;
		return switch (kind) {
			case INTACT -> streak
				? (roll < 0.35F ? Blocks.COARSE_DIRT : roll < 0.6F ? Blocks.TUFF : roll < 0.75F ? Blocks.BLACKSTONE : Blocks.GRAVEL)
				: (roll < 0.25F ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK);
			case BROKEN -> roll < 0.25F ? Blocks.MOSS_BLOCK : roll < 0.4F ? Blocks.COARSE_DIRT : roll < 0.5F ? Blocks.ROOTED_DIRT : Blocks.GRASS_BLOCK;
			case COUNTERFEIT -> roll < 0.35F ? Blocks.DIRT_PATH : roll < 0.5F ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK;
		};
	}

	private static void stationPad(final Builder b, final Station kind) {
		boolean broken = kind == Station.BROKEN;
		boolean fake = kind == Station.COUNTERFEIT;
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 4; z++) {
				boolean corner = Math.abs(x) == 4 && Math.abs(z) == 4;
				boolean edge = Math.abs(x) == 4 || Math.abs(z) == 4;
				// the landing ring painted round the pod, broken at the four doors
				boolean ring = Math.max(Math.abs(x), Math.abs(z)) == 2 && x != 0 && z != 0;
				BlockState block;
				if (fake) {
					block = (corner ? Blocks.SPRUCE_PLANKS
						: edge ? Blocks.STRIPPED_OAK_LOG
						: ring ? Blocks.DYED_TERRACOTTA.orange()
						: b.chance(0.15F) ? Blocks.BIRCH_PLANKS : Blocks.OAK_PLANKS).defaultBlockState();
					if (edge && !corner) {
						block = block.setValue(BlockStateProperties.AXIS, Math.abs(x) == 4 ? Direction.Axis.Z : Direction.Axis.X);
					}
				} else {
					block = (corner ? Blocks.CHISELED_DEEPSLATE
						: edge ? Blocks.DEEPSLATE_TILES
						: ring ? Blocks.CUT_COPPER.waxed().exposed()
						: b.chance(0.08F) ? Blocks.CRACKED_DEEPSLATE_TILES : Blocks.POLISHED_DEEPSLATE).defaultBlockState();
				}
				int y = 0;
				if (broken) {
					float roll = b.random().nextFloat();
					if (roll < 0.12F && edge) {
						continue; // a gap where the pad has fallen away
					}
					if (ring) {
						block = (b.chance(0.5F) ? Blocks.CUT_COPPER.waxed().oxidized() : Blocks.CUT_COPPER.waxed().weathered()).defaultBlockState();
					} else if (roll < 0.22F) {
						block = Blocks.MOSS_BLOCK.defaultBlockState();
					} else if (roll < 0.42F) {
						block = (b.chance(0.5F) ? Blocks.CRACKED_DEEPSLATE_BRICKS : Blocks.COBBLED_DEEPSLATE).defaultBlockState();
					}
					// the south-east corner has sunk into the ground
					if (x >= 1 && z >= 1 && x + z >= 6) {
						y = -1;
						b.set(x, 0, z, Blocks.AIR);
					}
				}
				b.set(x, y, z, block);
				b.footing(x, y, z, fake ? Blocks.STRIPPED_OAK_LOG : Blocks.COBBLED_DEEPSLATE, 8);
				if (broken && !edge && block.is(Blocks.MOSS_BLOCK)) {
					b.set(x, y + 1, z, b.chance(0.6F) ? Blocks.SHORT_GRASS : Blocks.MOSS_CARPET);
				} else if (broken && !edge && b.chance(0.1F)) {
					b.set(x, y + 1, z, Blocks.MOSS_CARPET);
				}
			}
		}
	}

	/**
	 * The number plate by a spawn pod's hatch, on the north face where the pad looks at it. A
	 * spawn's pod is never a real one, yet its plate carries the number of the hunter who comes
	 * down beside it: faded on a broken pod; on a copy, on another wood, without the station's gold,
	 * the name not quite right. The block it hangs on is put back first, as a broken pod may have
	 * lost it.
	 */
	public static void plate(final Builder b, final Station kind) {
		boolean fake = kind == Station.COUNTERFEIT;
		if (b.get(1, 2, -1).isAir()) {
			b.set(1, 2, -1, fake ? Blocks.TERRACOTTA : Blocks.CUT_COPPER.waxed().oxidized());
		}
		b.sign(1, 2, -2, fake ? Blocks.SPRUCE_WALL_SIGN : Blocks.DARK_OAK_WALL_SIGN, Direction.NORTH,
			PodPlate.text(fake ? PodPlate.COPY_KEY : PodPlate.STATION_KEY, PodPlate.HUNTER,
				fake ? net.minecraft.world.item.DyeColor.ORANGE : net.minecraft.world.item.DyeColor.GRAY, false));
	}

	/** The drop pod: a 3x3 copper shell with a tapered nose, its hatch to the north. */
	private static void stationPod(final Builder b, final Station kind) {
		boolean broken = kind == Station.BROKEN;
		boolean fake = kind == Station.COUNTERFEIT;
		Block hull = fake ? Blocks.TERRACOTTA : broken ? Blocks.CUT_COPPER.waxed().oxidized() : Blocks.CUT_COPPER.waxed().weathered();
		Block worn = fake ? Blocks.DYED_TERRACOTTA.orange() : broken ? Blocks.CUT_COPPER.waxed().weathered() : Blocks.CUT_COPPER.waxed().exposed();
		Block vent = fake ? Blocks.SPRUCE_TRAPDOOR : broken ? Blocks.COPPER_GRATE.waxed().oxidized() : Blocks.COPPER_GRATE.waxed().weathered();
		Block nose = fake ? Blocks.DYED_TERRACOTTA.orange() : broken ? Blocks.CUT_COPPER_STAIRS.waxed().oxidized() : Blocks.CUT_COPPER_STAIRS.waxed().weathered();
		Block crown = fake ? Blocks.TERRACOTTA : broken ? Blocks.CHISELED_COPPER.waxed().oxidized() : Blocks.CHISELED_COPPER.waxed().weathered();
		for (int y = 1; y <= 3; y++) {
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					boolean shell = Math.abs(x) == 1 || Math.abs(z) == 1;
					boolean door = z == -1 && x == 0 && y <= 2;
					if (!shell || door) {
						continue;
					}
					if (broken && b.chance(0.25F)) {
						continue;
					}
					boolean side = (x == 0) != (z == 0);
					if (side && y == 2 && !fake) {
						b.set(x, y, z, vent);
					} else if (!side && !fake && y != 2) {
						// heavier plate at the foot and a riveted band at the shoulder
						b.set(x, y, z, y == 1 ? (broken ? Blocks.COPPER_BLOCK.waxed().oxidized() : Blocks.COPPER_BLOCK.waxed().weathered()) : crown);
					} else {
						// scorch and worn paint in streaks down the hull
						b.set(x, y, z, b.chance(0.3F) ? worn : hull);
					}
				}
			}
		}
		// landing feet braced out at the four corners (two have broken off a broken pod)
		for (int x = -2; x <= 2; x += 4) {
			for (int z = -2; z <= 2; z += 4) {
				if (broken && x + z != 0) {
					continue;
				}
				b.stairs(x, 1, z, fake ? Blocks.OAK_STAIRS : nose, x > 0 ? Direction.WEST : Direction.EAST, false);
			}
		}
		if (fake) {
			// painted-on vents, nailed to the outside
			b.set(2, 2, 0, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST).setValue(BlockStateProperties.OPEN, true));
			b.set(-2, 2, 0, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST).setValue(BlockStateProperties.OPEN, true));
		}

		// the nose: stairs sloping up to a crown, and the beacon rod on top
		if (broken) {
			// it came off: one stair still clings on, the rest lies on the pad
			b.stairs(-1, 4, 0, nose, Direction.EAST, false);
			b.set(2, 1, 1, Blocks.COPPER_BLOCK.waxed().oxidized());
			b.stairs(2, 1, 0, nose, Direction.WEST, false);
			b.stairs(1, 1, 2, nose, Direction.SOUTH, true);
			b.set(-2, 1, 1, Blocks.LIGHTNING_ROD.waxed().oxidized().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
			b.set(0, 1, 0, Blocks.COBWEB);
			b.set(0, 3, 0, Blocks.COBWEB);
			// the torn hatch lies in front of the pod
			b.set(0, 1, -2, Blocks.COPPER_TRAPDOOR.waxed().oxidized());
			// ivy up the back of the shell
			for (int y = 1; y <= 3; y++) {
				if (!b.get(0, y, 1).isAir() && b.chance(0.8F)) {
					b.set(0, y, 2, Blocks.VINE.defaultBlockState().setValue(BlockStateProperties.NORTH, true));
				}
			}
			return;
		}
		if (fake) {
			b.fill(-1, 4, -1, 1, 4, 1, Blocks.TERRACOTTA);
			b.set(0, 4, 0, nose);
			b.slab(-1, 5, 0, Blocks.SPRUCE_SLAB, false);
			b.slab(1, 5, 0, Blocks.SPRUCE_SLAB, false);
			b.set(0, 5, 0, Blocks.OAK_FENCE);
			b.set(0, 6, 0, Blocks.OAK_FENCE);
		} else {
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					if (x == 0 && z == 0) {
						b.set(0, 4, 0, crown);
					} else if (x == 0 || z == 0) {
						Direction in = x == 0 ? (z < 0 ? Direction.SOUTH : Direction.NORTH) : (x < 0 ? Direction.EAST : Direction.WEST);
						b.stairs(x, 4, z, nose, in, false);
					} else {
						b.slab(x, 4, z, fake ? Blocks.SPRUCE_SLAB : Blocks.CUT_COPPER_SLAB.waxed().weathered(), false);
					}
				}
			}
			b.set(0, 5, 0, vent);
			b.set(0, 6, 0, Blocks.LIGHTNING_ROD.waxed().weathered());
		}
		// the hatch, thrown open; a jump seat and its harness chain inside
		b.door(0, 1, -1, fake ? Blocks.SPRUCE_DOOR : Blocks.COPPER_DOOR.waxed().weathered(), Direction.NORTH, true);
		b.stairs(0, 1, 0, fake ? Blocks.OAK_STAIRS : Blocks.DARK_OAK_STAIRS, Direction.SOUTH, false);
		b.axis(0, 3, 0, fake ? Blocks.IRON_CHAIN : Blocks.COPPER_CHAIN.waxed().weathered(), Direction.Axis.Y);
	}

	/** The signal mast in the south-east corner, with its yardarm and lanterns. */
	private static void stationMast(final Builder b, final Station kind) {
		switch (kind) {
			case INTACT -> {
				b.set(3, 1, 3, Blocks.DEEPSLATE_TILE_WALL);
				for (int y = 2; y <= 7; y++) {
					b.set(3, y, 3, Blocks.IRON_BARS);
				}
				b.set(2, 6, 3, Blocks.IRON_BARS);
				b.set(3, 6, 2, Blocks.IRON_BARS);
				b.hanging(2, 5, 3, Blocks.SOUL_LANTERN);
				b.hanging(3, 5, 2, Blocks.SOUL_LANTERN);
				b.set(3, 8, 3, Blocks.LIGHTNING_ROD.waxed().weathered());
			}
			case BROKEN -> {
				// snapped two blocks up (its corner of the pad has sunk); the rest lies across
				// the pad, the lantern beside it
				b.set(3, 0, 3, Blocks.COBBLED_DEEPSLATE_WALL);
				b.set(3, 1, 3, Blocks.IRON_BARS);
				for (int z = 2; z >= 0; z--) {
					b.set(3, 1, z, Blocks.LIGHTNING_ROD.waxed().oxidized().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH));
				}
				b.set(2, 1, 3, Blocks.SOUL_LANTERN);
			}
			case COUNTERFEIT -> {
				for (int y = 1; y <= 5; y++) {
					b.set(3, y, 3, Blocks.OAK_FENCE);
				}
				b.set(2, 5, 3, Blocks.OAK_FENCE);
				b.hanging(2, 4, 3, Blocks.LANTERN);
				b.set(3, 6, 3, Blocks.LANTERN);
			}
		}
	}

	/** The bell frame on the west side: the ore bell hangs in a real station; elsewhere, not. */
	private static void stationBell(final Builder b, final Station kind) {
		boolean fake = kind == Station.COUNTERFEIT;
		Block post = fake ? Blocks.SPRUCE_FENCE : Blocks.POLISHED_DEEPSLATE_WALL;
		Block beam = fake ? Blocks.SPRUCE_SLAB : Blocks.POLISHED_DEEPSLATE_SLAB;
		for (int y = 1; y <= 3; y++) {
			b.set(-3, y, -4, post);
			if (kind != Station.BROKEN || y < 3) {
				b.set(-3, y, -2, post);
			}
		}
		b.slab(-3, 4, -4, beam, false);
		b.slab(-3, 4, -3, beam, false);
		switch (kind) {
			case INTACT -> {
				b.slab(-3, 4, -2, beam, false);
				b.axis(-3, 3, -3, Blocks.IRON_CHAIN, Direction.Axis.Y);
				b.axis(-3, 2, -3, Blocks.IRON_CHAIN, Direction.Axis.Y);
				b.set(-3, 1, -3, MistinnBlocks.ORE_BELL);
				// burnt-out candles for the hunters who rang it
				b.candles(-4, 1, -3, Blocks.CANDLE, 2, false);
			}
			case BROKEN -> {
				// the bell has been cut down; its chain ends in the air, the beam's end on the pad
				b.axis(-3, 3, -3, Blocks.IRON_CHAIN, Direction.Axis.Y);
				b.slab(-2, 1, -3, beam, false);
				b.candles(-4, 1, -3, Blocks.CANDLE, 1, false);
			}
			case COUNTERFEIT -> {
				// a village bell where the golden one should be: it rings, but it will not call you back
				b.slab(-3, 4, -2, beam, false);
				b.set(-3, 3, -3, Blocks.BELL.defaultBlockState()
					.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
					.setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.CEILING));
			}
		}
	}

	private static void stationRadio(final Builder b, final Station kind) {
		switch (kind) {
			case INTACT -> {
				// the radio, burned out, facing the hunter's spot on the pad, its aerial behind it
				b.set(RADIO.getX(), RADIO.getY(), RADIO.getZ(), MistinnBlocks.RADIO.defaultBlockState().setValue(RadioBlock.FACING, Direction.WEST));
				b.set(3, 1, -3, Blocks.DEEPSLATE_TILE_WALL);
				b.set(3, 2, -3, Blocks.LIGHTNING_ROD.waxed().exposed());
			}
			case BROKEN -> {
				// only the aerial's stub is left, the rod knocked flat
				b.set(3, 1, -3, Blocks.COBBLED_DEEPSLATE_WALL);
				b.set(2, 1, -3, Blocks.LIGHTNING_ROD.waxed().oxidized().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.WEST));
			}
			case COUNTERFEIT -> {
				// a box that only hums
				b.set(RADIO.getX(), RADIO.getY(), RADIO.getZ(), Blocks.NOTE_BLOCK);
				b.set(3, 1, -3, Blocks.OAK_FENCE);
				b.set(3, 2, -3, Blocks.OAK_FENCE);
			}
		}
	}

	private static void stationSupplies(final Builder b, final Station kind) {
		b.container(-3, 1, 3, Blocks.BARREL, Direction.UP, kind == Station.INTACT ? "chests/drop_station" : "chests/spawn_station");
		switch (kind) {
			case INTACT -> {
				// a crate under a tarp, and a coil of chain
				b.axis(-2, 1, 3, Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X);
				b.set(-2, 2, 3, Blocks.CARPET.brown());
				b.axis(-3, 1, 2, Blocks.IRON_CHAIN, Direction.Axis.X);
			}
			case BROKEN -> {
				// someone waited here for a pod that never came
				b.set(-2, 1, 3, Blocks.SKELETON_SKULL.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 6));
				b.set(-3, 2, 3, Blocks.COBWEB);
			}
			case COUNTERFEIT -> {
				b.set(-2, 1, 3, Blocks.HAY_BLOCK);
				b.set(-3, 1, 2, Blocks.CRAFTING_TABLE);
			}
		}
	}

	/** The way out: a trodden path leading north from the pad, with a lantern post on it. */
	private static void stationTrail(final Builder b) {
		for (int z = -5; z >= -12; z--) {
			int x = (int) Math.round(Math.sin(z * 0.6) * 1.2);
			b.cover(x, z, b.chance(0.6F) ? Blocks.DIRT_PATH : Blocks.COARSE_DIRT, 3);
			if (b.chance(0.4F)) {
				b.cover(x + (b.chance(0.5F) ? 1 : -1), z, Blocks.COARSE_DIRT, 3);
			}
		}
		int y = b.onGround(2, -9, Blocks.DEEPSLATE_TILE_WALL.defaultBlockState(), 3);
		if (y != Integer.MIN_VALUE) {
			b.set(2, y + 1, -9, Blocks.DEEPSLATE_TILE_WALL);
			b.set(2, y + 2, -9, Blocks.COPPER_LANTERN.waxed().weathered());
		}
		// a shed panel from some earlier pod, half in the dirt
		b.onGround(-5, -7, Blocks.CUT_COPPER_SLAB.waxed().exposed().defaultBlockState(), 3);
	}

	/** Whoever built the fake camped beside it: a dead fire, a log to sit on, a bedroll. */
	private static void counterfeitCamp(final Builder b) {
		b.onGround(6, -1, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false), 2);
		b.onGround(6, 1, Blocks.STRIPPED_OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z), 2);
		b.onGround(5, -6, Blocks.CARPET.lightGray().defaultBlockState(), 2);
		b.onGround(6, -5, Blocks.CARPET.lightGray().defaultBlockState(), 2);
		b.onGround(-6, 2, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST), 2);
	}

	// ── 鹽塚 ──────────────────────────────────────────────────────

	/**
	 * 鹽塚: a cairn of salt-crusted stone over the salt of someone who did not come back, with an
	 * offering chest under a capstone at its crown. A ring of standing stones (some fallen, two
	 * with candle stubs) keeps it; offerings lie at its foot; and a line of salt footprints walks
	 * up to it from the south. None walk away.
	 */
	public static void saltCairn(final Builder start) {
		Builder b = start.anyTurn();
		int height = 4 + b.random().nextInt(2);
		double lean = (b.random().nextDouble() - 0.5) * 0.5;
		b.clear(-4, 1, -4, 4, height + 3, 4);

		// the ground around it is crusted white
		for (int x = -6; x <= 6; x++) {
			for (int z = -6; z <= 6; z++) {
				if (x * x + z * z <= 40 && b.chance(0.4F)) {
					b.cover(x, z, b.pick(Blocks.CALCITE, Blocks.CALCITE, Blocks.DIORITE), 3);
				}
			}
		}

		// the mound: courses of rough stone at the foot, salt above, each course stepped in
		double base = 3.6 + b.random().nextDouble() * 0.5;
		int[][] crest = new int[9][9];
		for (int y = 0; y <= height; y++) {
			double t = y / (height + 0.6);
			double radius = base * (1.0 - Math.pow(t, 1.4)) + 0.4;
			double cx = lean * y;
			for (int x = -4; x <= 4; x++) {
				for (int z = -4; z <= 4; z++) {
					double d = Math.sqrt((x - cx) * (x - cx) + z * z) + b.random().nextDouble() * 0.5;
					if (d > radius) {
						continue;
					}
					crest[x + 4][z + 4] = y;
					boolean rim = d > radius - 1.0 && y > 0;
					Block block;
					if (y == 0) {
						block = b.pick(Blocks.TUFF, Blocks.COBBLESTONE, Blocks.CALCITE, Blocks.ANDESITE, Blocks.MOSSY_COBBLESTONE);
					} else if (y == 1) {
						block = b.pick(Blocks.CALCITE, Blocks.DIORITE, Blocks.CALCITE, Blocks.TUFF);
					} else {
						block = b.pick(Blocks.CALCITE, Blocks.SMOOTH_QUARTZ, Blocks.CALCITE, Blocks.QUARTZ_BRICKS);
					}
					if (rim && b.chance(0.6F)) {
						if ((y & 1) == 0) {
							// a flat stone laid on the course below
							b.slab(x, y, z, y <= 1 ? Blocks.COBBLESTONE_SLAB : b.pick(Blocks.SMOOTH_QUARTZ_SLAB, Blocks.DIORITE_SLAB), false);
						} else {
							Direction in = Math.abs(x - cx) > Math.abs(z)
								? (x > cx ? Direction.WEST : Direction.EAST)
								: (z > 0 ? Direction.NORTH : Direction.SOUTH);
							b.stairs(x, y, z, y <= 1 ? Blocks.COBBLESTONE_STAIRS : Blocks.SMOOTH_QUARTZ_STAIRS, in, false);
						}
					} else {
						b.set(x, y, z, block);
					}
					if (y == 0) {
						b.footing(x, z, Blocks.CALCITE, 5);
					}
				}
			}
		}
		// salt crystals grown out of its flanks
		for (int i = 0; i < 4; i++) {
			int x = b.random().nextInt(7) - 3;
			int z = b.random().nextInt(7) - 3;
			int y = crest[x + 4][z + 4];
			BlockState under = b.get(x, y, z);
			boolean stone = under.is(Blocks.CALCITE) || under.is(Blocks.SMOOTH_QUARTZ) || under.is(Blocks.QUARTZ_BRICKS) || under.is(Blocks.DIORITE);
			if (stone && y >= 1 && y < height - 1 && b.get(x, y + 1, z).isAir()) {
				int tall = 1 + b.random().nextInt(2);
				for (int h = 1; h <= tall; h++) {
					b.set(x, y + h, z, Blocks.QUARTZ_PILLAR);
				}
			}
		}

		// the crown: a chest under a capstone between two salt stones, candle stubs on top
		int ox = (int) Math.round(lean * height);
		int top = height + 1;
		b.set(ox, height, 0, Blocks.CALCITE);
		b.set(ox - 1, height, 0, Blocks.CALCITE);
		b.set(ox + 1, height, 0, Blocks.CALCITE);
		b.set(ox, height, -1, Blocks.CALCITE);
		b.container(ox, top, 0, Blocks.CHEST, Direction.SOUTH, "chests/salt_cairn");
		b.set(ox - 1, top, 0, Blocks.QUARTZ_PILLAR);
		b.set(ox + 1, top, 0, Blocks.QUARTZ_PILLAR);
		b.set(ox, top, -1, Blocks.CALCITE);
		b.slab(ox, top + 1, 0, Blocks.SMOOTH_QUARTZ_SLAB, false);
		b.slab(ox, top + 1, -1, Blocks.SMOOTH_QUARTZ_SLAB, false);
		b.candles(ox - 1, top + 1, 0, Blocks.CANDLE, 1 + b.random().nextInt(3), false);
		b.candles(ox + 1, top + 1, 0, Blocks.DYED_CANDLE.white(), 1 + b.random().nextInt(2), false);

		// standing stones round it; some have fallen outward
		int stones = 6 + b.random().nextInt(2);
		double turn = b.random().nextDouble() * Math.PI * 2.0;
		int lit = b.chance(0.25F) ? b.random().nextInt(stones) : -1;
		for (int i = 0; i < stones; i++) {
			double a = turn + i * Math.PI * 2.0 / stones + (b.random().nextDouble() - 0.5) * 0.4;
			int sx = (int) Math.round(Math.cos(a) * 6.2);
			int sz = (int) Math.round(Math.sin(a) * 6.2);
			if (sz >= 5 && Math.abs(sx) <= 1) {
				continue; // the footprints' way in
			}
			if (b.chance(0.25F)) {
				int dx = (int) Math.signum(Math.round(Math.cos(a)));
				int dz = (int) Math.signum(Math.round(Math.sin(a)));
				BlockState lying = Blocks.QUARTZ_PILLAR.defaultBlockState().setValue(BlockStateProperties.AXIS, dx != 0 ? Direction.Axis.X : Direction.Axis.Z);
				b.onGround(sx, sz, lying, 3);
				b.onGround(sx + dx, sz + dz, lying, 3);
				continue;
			}
			int tall = 1 + b.random().nextInt(3);
			int y = b.onGround(sx, sz, Blocks.TUFF.defaultBlockState(), 3);
			if (y == Integer.MIN_VALUE) {
				continue;
			}
			for (int h = 1; h < tall; h++) {
				b.set(sx, y + h, sz, b.pick(Blocks.CALCITE, Blocks.DIORITE, Blocks.CALCITE));
			}
			if (i == lit) {
				b.candles(sx, y + tall, sz, Blocks.CANDLE, 2, true); // someone was here not long ago
			} else if (b.chance(0.35F)) {
				b.candles(sx, y + tall, sz, Blocks.CANDLE, 1 + b.random().nextInt(2), false);
			}
		}

		// offerings at its foot: an oil jar, a bone half in the salt, sometimes a skull
		b.onGround(2, 4, Blocks.DECORATED_POT.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), 3);
		int bone = b.surface(-3, 3);
		if (Math.abs(bone) <= 3) {
			b.set(-3, bone, 3, Blocks.BONE_BLOCK.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
		}
		if (b.chance(0.4F)) {
			b.onGround(-1, 4, Blocks.SKELETON_SKULL.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, b.random().nextInt(16)), 3);
		}

		// salt footprints, left foot and right, up to the cairn and no further
		for (int z = 11; z >= 4; z--) {
			b.cover((z & 1) == 0 ? 0 : 1, z, Blocks.CALCITE, 3);
		}
		b.settle();
	}

	// ── 鏽錨殘骸 ──────────────────────────────────────────────────

	/**
	 * 鏽錨殘骸: a great anchor, Rust Anchor's own sign, driven crown-first into the sand and leaning
	 * on one fluke. Its chain falls from the ring and runs off under the sand. Beside it lie the
	 * ribs of the boat it was lost from, a snapped mast and a rag of sail; and a salt cross marks
	 * where the chest is buried.
	 */
	public static void anchorWreck(final Builder start) {
		Builder b = start.anyTurn();
		// the lie of the beach, read before anything is built on it
		int[][] sand = new int[25][25];
		for (int x = -12; x <= 12; x++) {
			for (int z = -12; z <= 12; z++) {
				sand[x + 12][z + 12] = b.surface(x, z);
			}
		}
		double lean = Math.toRadians(14 + b.random().nextInt(10));
		double tip = Math.toRadians(8 + b.random().nextInt(8));
		// the shank's direction, and the arms' axis across it
		double[] up = normal(new double[] {Math.sin(tip), Math.cos(lean), Math.sin(lean)});
		double[] across = normal(cross(up, new double[] {0, 0, 1}));
		double[] side = cross(across, up);
		double[] crown = {0, -1.5, -1};
		Map<BlockPos, Integer> parts = new HashMap<>();
		final int shank = 0;
		final int stock = 1;
		final int ring = 2;
		final int arm = 3;
		final int fluke = 4;
		double length = 13.0;
		for (double v = 0; v <= length; v += 0.25) {
			mark(parts, crown, up, across, side, 0, v, 0, v < 1.5 ? 1.25 : 1.0, shank);
		}
		for (double w = -4.0; w <= 4.0; w += 0.25) {
			mark(parts, crown, up, across, side, 0, length - 2.0, w, Math.abs(w) > 3.4 ? 1.0 : 0.6, stock);
		}
		for (double a = 0; a < Math.PI * 2.0; a += 0.06) {
			mark(parts, crown, up, across, side, Math.sin(a) * 1.9, length + 1.7 + Math.cos(a) * 1.9, 0, 0.75, ring);
		}
		for (double p = -1.2; p <= 1.2; p += 0.03) {
			mark(parts, crown, up, across, side, Math.sin(p) * 6.0, 6.0 * (1.0 - Math.cos(p)), 0, 0.85, arm);
		}
		for (int s = -1; s <= 1; s += 2) {
			// the flukes: broad spade blades on the arms' ends, and their bills
			for (double k = 0; k <= 1.0; k += 0.08) {
				mark(parts, crown, up, across, side, s * (4.0 + k * 1.4), 1.6 + k * 2.4, 0, 1.6 - k * 0.9, fluke);
			}
			mark(parts, crown, up, across, side, s * 6.2, 5.0, 0, 0.5, fluke);
		}
		for (Map.Entry<BlockPos, Integer> e : parts.entrySet()) {
			BlockPos p = e.getKey();
			if (Math.abs(p.getX()) > 12 || Math.abs(p.getZ()) > 12) {
				continue;
			}
			int ground = sand[p.getX() + 12][p.getZ() + 12];
			b.set(p.getX(), p.getY(), p.getZ(), anchorMetal(b, e.getValue(), p.getY() - ground));
		}

		// the chain: down from the ring to the sand, then away under it
		double[] hook = place(crown, up, across, side, 1.9, length + 1.7, 0);
		// (it hangs from the ring's side, clear of the shank)
		int hx = (int) Math.round(hook[0]) + 1;
		int hz = (int) Math.round(hook[2]);
		Block chain = Blocks.COPPER_CHAIN.waxed().weathered();
		if (Math.abs(hx) <= 11 && Math.abs(hz) <= 11) {
			int ground = sand[hx + 12][hz + 12];
			for (int y = (int) Math.round(hook[1]); y > ground; y--) {
				if (b.get(hx, y, hz).isAir()) {
					b.axis(hx, y, hz, chain, Direction.Axis.Y);
				}
			}
			for (int x = hx + 1; x <= Math.min(12, hx + 8); x++) {
				int z = hz + (int) Math.round(Math.sin(x * 0.5) * 0.8);
				if (Math.abs(z) > 12 || b.chance(0.2F)) {
					continue; // under the sand here
				}
				int g = sand[x + 12][z + 12];
				if (!b.wet(x, z) && b.get(x, g + 1, z).isAir()) {
					b.axis(x, g + 1, z, chain, Direction.Axis.X);
				}
			}
		}

		// the boat: a keel, ribs half buried, a strip of planking, the mast and its sail
		int boatX = -9;
		for (int z = -4; z <= 4; z++) {
			int g = b.surface(boatX, z);
			if (Math.abs(g) > 3) {
				continue;
			}
			b.axis(boatX, g, z, Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Z);
			if (z % 2 == 0 && z != 4) {
				for (int s = -1; s <= 1; s += 2) {
					int rib = boatX + s * 2;
					int rg = b.surface(rib, z);
					if (Math.abs(rg - g) > 2) {
						continue;
					}
					int tall = (z == 0 || s < 0) ? 3 : 1 + b.random().nextInt(2);
					for (int h = 1; h <= tall; h++) {
						b.set(rib, rg + h, z, Blocks.DARK_OAK_FENCE);
					}
					// the rib curls in at its head
					b.stairs(rib - s, rg + tall, z, Blocks.DARK_OAK_STAIRS, s < 0 ? Direction.WEST : Direction.EAST, true);
				}
			} else if (b.chance(0.7F)) {
				int px = boatX - 2;
				int pg = b.surface(px, z);
				if (Math.abs(pg - g) <= 2) {
					b.slab(px, pg + 1, z, Blocks.DARK_OAK_SLAB, b.chance(0.5F));
				}
			}
		}
		for (int x = -4; x >= -10; x--) {
			int g = b.surface(x, 7);
			if (Math.abs(g) <= 3 && !b.wet(x, 7) && x != -7) {
				b.axis(x, g + 1, 7, Blocks.SPRUCE_LOG, Direction.Axis.X);
			}
		}
		for (int[] rag : new int[][] {{-5, 8}, {-6, 8}, {-6, 9}, {-7, 9}, {-5, 9}}) {
			if (b.chance(0.8F)) {
				b.onGround(rag[0], rag[1], (b.chance(0.5F) ? Blocks.CARPET.white() : Blocks.CARPET.lightGray()).defaultBlockState(), 3);
			}
		}
		b.onGround(-5, -3, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.SOUTH), 3);
		b.onGround(-11, 1, Blocks.DRIED_KELP_BLOCK.defaultBlockState(), 3);

		// a salt cross on the sand, and the chest a block beneath it
		int cx = 4;
		int cz = 4;
		int floor = b.surface(cx, cz);
		for (int[] c : new int[][] {{0, 0}, {-1, -1}, {1, 1}, {-1, 1}, {1, -1}}) {
			b.cover(cx + c[0], cz + c[1], Blocks.CALCITE, 4);
		}
		b.container(cx, floor - 1, cz, Blocks.CHEST, Direction.NORTH, "chests/anchor_wreck");
		b.settle();
	}

	/** What the anchor is made of at a point: rust and verdigris, salt-crusted at the sand. */
	private static Block anchorMetal(final Builder b, final int part, final int aboveGround) {
		if (aboveGround <= 0) {
			return b.chance(0.6F) ? Blocks.COPPER_BLOCK.waxed().oxidized() : Blocks.CUT_COPPER.waxed().oxidized();
		}
		if (aboveGround == 1 && b.chance(0.35F)) {
			return Blocks.CALCITE;
		}
		if (part == 2 || part == 4 && b.chance(0.3F)) {
			return b.chance(0.5F) ? Blocks.CHISELED_COPPER.waxed().weathered() : Blocks.COPPER_BLOCK.waxed().weathered();
		}
		float roll = b.random().nextFloat();
		if (aboveGround <= 3) {
			return roll < 0.45F ? Blocks.CUT_COPPER.waxed().oxidized() : roll < 0.8F ? Blocks.CUT_COPPER.waxed().weathered() : Blocks.CUT_COPPER.waxed().exposed();
		}
		return roll < 0.45F ? Blocks.CUT_COPPER.waxed().weathered() : roll < 0.8F ? Blocks.CUT_COPPER.waxed().exposed() : Blocks.COPPER_BLOCK.waxed().exposed();
	}

	private static double[] place(final double[] o, final double[] up, final double[] across, final double[] side, final double u, final double v, final double w) {
		return new double[] {
			o[0] + across[0] * u + up[0] * v + side[0] * w,
			o[1] + across[1] * u + up[1] * v + side[1] * w,
			o[2] + across[2] * u + up[2] * v + side[2] * w
		};
	}

	/** Marks every block within {@code radius} of a point on the anchor as part of it. */
	private static void mark(final Map<BlockPos, Integer> parts, final double[] o, final double[] up, final double[] across, final double[] side, final double u, final double v, final double w, final double radius, final int part) {
		double[] p = place(o, up, across, side, u, v, w);
		int r = (int) Math.ceil(radius);
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					BlockPos at = new BlockPos((int) Math.floor(p[0] + 0.5) + x, (int) Math.floor(p[1] + 0.5) + y, (int) Math.floor(p[2] + 0.5) + z);
					double dx = at.getX() - p[0];
					double dy = at.getY() - p[1];
					double dz = at.getZ() - p[2];
					if (dx * dx + dy * dy + dz * dz <= radius * radius) {
						parts.merge(at, part, Math::max);
					}
				}
			}
		}
	}

	private static double[] cross(final double[] a, final double[] c) {
		return new double[] {a[1] * c[2] - a[2] * c[1], a[2] * c[0] - a[0] * c[2], a[0] * c[1] - a[1] * c[0]};
	}

	private static double[] normal(final double[] a) {
		double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
		return new double[] {a[0] / l, a[1] / l, a[2] / l};
	}

	// ── 霧中崗哨 ──────────────────────────────────────────────────

	/** Height of the watchpost's lookout deck above the ground. */
	private static final int DECK = 10;

	/**
	 * 霧中崗哨: a stone watchtower on a battered plinth, with a sleeping floor and a timber lookout
	 * under a hipped roof. The watcher barricaded the door and poured a line of salt inside it;
	 * the barricade is splintered across the grass, the salt line is broken, claw marks rake the
	 * east wall and the roof is torn open on that side. The alarm bell still hangs under the roof
	 * beam, the bed is made, and salt footprints lead from the door into the mist and stop.
	 */
	public static void watchpost(final Builder start) {
		Builder b = start.anyTurn();
		b.clear(-3, 1, -3, 3, DECK + 7, 3);

		// the plinth, flared at the foot
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				b.set(x, 0, z, b.pick(Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.MOSSY_COBBLESTONE, Blocks.STONE_BRICKS));
				b.footing(x, z, Blocks.COBBLESTONE, 10);
				int ring = Math.max(Math.abs(x), Math.abs(z));
				if (ring == 3 && !(x == 0 && z == -3)) {
					if (Math.abs(x) == 3 && Math.abs(z) == 3) {
						b.set(x, 1, z, Blocks.MOSSY_COBBLESTONE);
					} else {
						Direction in = Math.abs(x) == 3 ? (x > 0 ? Direction.WEST : Direction.EAST) : (z > 0 ? Direction.NORTH : Direction.SOUTH);
						b.stairs(x, 1, z, b.chance(0.3F) ? Blocks.MOSSY_STONE_BRICK_STAIRS : Blocks.STONE_BRICK_STAIRS, in, false);
					}
				}
			}
		}

		// the tower: polished corners, worn walls, a moulding at the sleeping floor
		for (int y = 1; y < DECK; y++) {
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					if (Math.abs(x) != 2 && Math.abs(z) != 2) {
						continue;
					}
					boolean corner = Math.abs(x) == 2 && Math.abs(z) == 2;
					Block block;
					if (corner) {
						block = y <= 2 && b.chance(0.4F) ? Blocks.MOSSY_STONE_BRICKS : Blocks.POLISHED_ANDESITE;
					} else if (y == 5) {
						block = Blocks.CHISELED_STONE_BRICKS;
					} else {
						block = towerStone(b, y);
					}
					b.set(x, y, z, block);
				}
			}
		}
		// claw marks: two long gouges raked down the east wall, one torn right through
		for (int i = 0; i < 2; i++) {
			int z = i == 0 ? -1 : 1;
			int topY = 8 - i;
			for (int y = topY; y > topY - 4; y--) {
				b.set(2, y, z, y == topY - 1 && i == 0 ? Blocks.AIR : Blocks.CRACKED_DEEPSLATE_TILES);
			}
		}
		// the doorway, a lintel over it, slits and windows
		b.clear(0, 1, -2, 0, 2, -2);
		b.stairs(0, 3, -3, Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, true);
		b.set(2, 3, 0, Blocks.AIR);
		b.set(-2, 3, 0, Blocks.AIR);
		for (Direction side : new Direction[] {Direction.EAST, Direction.WEST, Direction.NORTH}) {
			b.set(side.getStepX() * 2, 7, side.getStepZ() * 2, b.chance(0.6F) ? Blocks.GLASS_PANE : Blocks.AIR);
		}

		// ground floor: flagstones, the broken salt line across the threshold, firewood
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				b.set(x, 0, z, b.chance(0.3F) ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS);
			}
		}
		b.set(-1, 0, -1, Blocks.CALCITE);
		b.set(1, 0, -1, Blocks.CALCITE);
		b.set(0, 0, -1, Blocks.GRAVEL); // where something crossed it
		b.axis(-1, 1, 1, Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z);
		b.axis(-1, 2, 1, Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z);
		b.set(1, 1, 1, Blocks.CAULDRON);
		b.slab(-1, 1, -1, Blocks.SPRUCE_SLAB, false); // what is left of the barricade inside

		// the ladder, up the south wall through both floors
		for (int y = 1; y <= DECK; y++) {
			b.facing(0, y, 1, Blocks.LADDER, Direction.NORTH);
		}

		// sleeping floor: a made bed, the watch log's lectern, burnt-down candles
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				if (!(x == 0 && z == 1)) {
					b.set(x, 5, z, Blocks.SPRUCE_PLANKS);
				}
			}
		}
		b.bed(-1, 6, 0, Blocks.BED.white(), Direction.SOUTH);
		b.facing(1, 6, 0, Blocks.LECTERN, Direction.WEST);
		b.candles(1, 6, -1, Blocks.CANDLE, 3, false);
		b.set(0, 8, 0, Blocks.COBWEB);

		// the lookout: a plank deck overhanging on corbels, a fence parapet, a hipped roof
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				int ring = Math.max(Math.abs(x), Math.abs(z));
				if (x == 0 && z == 1) {
					continue;
				}
				if (ring < 3) {
					b.set(x, DECK, z, Blocks.SPRUCE_PLANKS);
				} else {
					b.slab(x, DECK, z, Blocks.SPRUCE_SLAB, true);
					Direction in = Math.abs(x) == 3 ? (x > 0 ? Direction.WEST : Direction.EAST) : (z > 0 ? Direction.NORTH : Direction.SOUTH);
					if (Math.abs(x) == 3 && Math.abs(z) == 3) {
						b.set(x, DECK - 1, z, Blocks.POLISHED_ANDESITE);
					} else {
						b.stairs(x, DECK - 1, z, Blocks.STONE_BRICK_STAIRS, in, true);
					}
				}
			}
		}
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				if (Math.max(Math.abs(x), Math.abs(z)) != 3) {
					continue;
				}
				boolean corner = Math.abs(x) == 3 && Math.abs(z) == 3;
				if (corner) {
					int tall = x == 3 && z == 3 ? 1 : 3; // the east side was torn open
					for (int y = DECK + 1; y <= DECK + tall; y++) {
						b.set(x, y, z, Blocks.STRIPPED_SPRUCE_LOG);
					}
				} else if (!(x == 3 && b.chance(0.6F))) {
					b.set(x, DECK + 1, z, Blocks.SPRUCE_FENCE);
				}
			}
		}
		for (int level = 0; level <= 3; level++) {
			int r = 3 - level;
			int y = DECK + 4 + level;
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					if (Math.max(Math.abs(x), Math.abs(z)) != r) {
						continue;
					}
					if (x >= 1 && z >= -1 && level <= 2) {
						continue; // torn away
					}
					if (r == 0) {
						b.slab(0, y, 0, Blocks.SPRUCE_SLAB, false);
						continue;
					}
					Direction in = Math.abs(x) >= Math.abs(z) ? (x > 0 ? Direction.WEST : Direction.EAST) : (z > 0 ? Direction.NORTH : Direction.SOUTH);
					b.stairs(x, y, z, Blocks.SPRUCE_STAIRS, in, false);
				}
			}
		}
		b.set(0, DECK + 8, 0, Blocks.LIGHTNING_ROD.waxed().weathered());
		// the tie beam: the lantern and the alarm bell hang from it
		for (int x = -2; x <= 0; x++) {
			b.axis(x, DECK + 4, 0, Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X);
		}
		b.hanging(-1, DECK + 3, 0, Blocks.SOUL_LANTERN);
		b.set(0, DECK + 3, 0, Blocks.BELL.defaultBlockState()
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
			.setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.CEILING));
		b.container(-1, DECK + 1, -1, Blocks.CHEST, Direction.SOUTH, "chests/watchpost");
		b.candles(-2, DECK + 1, 2, Blocks.CANDLE, 2, false);

		// outside: the barricade's splinters, the torn roof, a cold fire, a broken palisade
		b.onGround(-1, -4, Blocks.SPRUCE_TRAPDOOR.defaultBlockState(), 3);
		b.onGround(1, -5, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), 3);
		b.onGround(2, -4, Blocks.SPRUCE_SLAB.defaultBlockState(), 3);
		b.onGround(5, 1, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), 3);
		b.onGround(6, -1, Blocks.SPRUCE_SLAB.defaultBlockState(), 3);
		b.onGround(4, 3, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), 3);
		b.onGround(-4, -5, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false), 3);
		b.onGround(-5, -6, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X), 3);
		watchpostShed(b);
		for (int i = 0; i < 14; i++) {
			double a = Math.PI * (0.15 + i * 0.075);
			int x = (int) Math.round(Math.cos(a) * 6.0);
			int z = (int) Math.round(Math.sin(a) * 6.0);
			if (b.chance(0.7F)) {
				b.onGround(x, z, Blocks.SPRUCE_FENCE.defaultBlockState(), 3);
			}
		}
		// salt footprints out of the door, into the mist, and then nothing
		for (int z = -4; z >= -12; z--) {
			b.cover((z & 1) == 0 ? 0 : -1, z, Blocks.CALCITE, 3);
		}
		b.settle();
	}

	/** A lean-to against the west wall: firewood stacked for a watch that never ended. */
	private static void watchpostShed(final Builder b) {
		b.clear(-5, 1, -1, -4, 3, 2);
		for (int x = -5; x <= -4; x++) {
			for (int z = -1; z <= 2; z++) {
				b.set(x, 0, z, b.pick(Blocks.COARSE_DIRT, Blocks.PACKED_MUD, Blocks.COBBLESTONE));
				b.footing(x, z, Blocks.COBBLESTONE, 6);
			}
		}
		for (int z = -1; z <= 2; z += 3) {
			b.set(-5, 1, z, Blocks.SPRUCE_FENCE);
			b.set(-5, 2, z, Blocks.SPRUCE_FENCE);
		}
		for (int z = -1; z <= 2; z++) {
			b.stairs(-5, 3, z, Blocks.SPRUCE_STAIRS, Direction.EAST, false);
			b.stairs(-4, 3, z, Blocks.SPRUCE_STAIRS, Direction.EAST, true);
			b.stairs(-3, 3, z, Blocks.SPRUCE_STAIRS, Direction.EAST, false);
		}
		for (int z = 0; z <= 1; z++) {
			b.axis(-4, 1, z, Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z);
			b.axis(-4, 2, z, b.chance(0.5F) ? Blocks.SPRUCE_LOG : Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z);
		}
		b.axis(-5, 1, 1, Blocks.SPRUCE_LOG, Direction.Axis.Y); // the chopping block
		b.set(-4, 1, -1, Blocks.DECORATED_POT);
	}

	/** Stone for a wall, worn harder near the ground. */
	private static Block towerStone(final Builder b, final int y) {
		float roll = b.random().nextFloat();
		if (y <= 2) {
			return roll < 0.3F ? Blocks.MOSSY_STONE_BRICKS : roll < 0.45F ? Blocks.CRACKED_STONE_BRICKS : roll < 0.55F ? Blocks.MOSSY_COBBLESTONE : Blocks.STONE_BRICKS;
		}
		if (y <= 6) {
			return roll < 0.1F ? Blocks.MOSSY_STONE_BRICKS : roll < 0.22F ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS;
		}
		return roll < 0.2F ? Blocks.CRACKED_STONE_BRICKS : roll < 0.25F ? Blocks.ANDESITE : Blocks.STONE_BRICKS;
	}

	// ── 霧岸燈塔 ──────────────────────────────────────────────────

	/** Height of the lamp room floor above the ground. */
	public static final int LAMP_ROOM = 21;
	/** The spiral stair climbs this many blocks per turn. */
	private static final int TURN = 10;
	/** The tower's hollow: the stair runs between the weight shaft and the wall. */
	private static final double HOLLOW = 3.0;
	private static final double SHAFT = 1.6;

	/**
	 * 霧岸燈塔: a dead lighthouse on the shore. A golden bell waits by the door in a little stone
	 * shrine; the tower, banded in salt-white and streaked with salt below every window, tapers to
	 * a corbelled gallery. Inside, a spiral stair climbs round the shaft that once held the lamp's
	 * clockwork weight: the cable has snapped, the weight lies smashed on the floor, and the chain
	 * hangs short. Halfway up is the keeper's landing with the supplies. At the top, the lamp room
	 * is the first keeper's arena: a ring of bars, four lamp stands, and the great dead lamp in its
	 * cracked glass housing, soot spread round it. The keeper's cottage beside the tower has lost
	 * half its roof. Returns the room's floor centre.
	 */
	public static BlockPos lighthouse(final Builder b) {
		b.clear(-7, 1, -7, 7, LAMP_ROOM + 8, 7);
		lighthouseBase(b);
		lighthouseTower(b);
		lighthouseStair(b);
		lighthouseLanding(b);
		lighthouseShaft(b);
		lighthouseBell(b);
		lighthouseGallery(b);
		keeperCottage(b);
		b.settle();
		b.spawn(MistinnEntities.SNUFFER, 0, LAMP_ROOM + 1, 3, 180.0F);
		// the mist gathers round every lighthouse, wherever it was built
		ShoreMist.remember(b.level(), b.at(0, LAMP_ROOM + 1, 0));
		return b.at(0, LAMP_ROOM + 1, 0);
	}

	/** Outer radius of the tower's wall at a height: it tapers as it climbs. */
	private static double towerRadius(final int y) {
		return 4.6 - 0.65 * y / (double) LAMP_ROOM;
	}

	/** A rough stone footing, worn green and salt-white where the sea reaches it. */
	private static void lighthouseBase(final Builder b) {
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				double r = Math.sqrt(x * x + z * z);
				if (r > 6.6 + b.random().nextDouble() * 0.6) {
					continue;
				}
				Block block;
				if (r <= 5.0) {
					block = (x + z) % 3 == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS;
				} else {
					float roll = b.random().nextFloat();
					block = roll < 0.3F ? Blocks.MOSSY_COBBLESTONE : roll < 0.5F ? Blocks.COBBLESTONE : roll < 0.62F ? Blocks.DARK_PRISMARINE : roll < 0.75F ? Blocks.CALCITE : Blocks.STONE;
				}
				b.set(x, 0, z, block);
				b.footing(x, z, r <= 5.0 ? Blocks.STONE_BRICKS : Blocks.COBBLESTONE, 12);
			}
		}
		// the path from the door to the bell
		for (int z = 5; z <= 8; z++) {
			b.set(0, 0, z, z == 7 ? Blocks.CHISELED_STONE_BRICKS : Blocks.POLISHED_ANDESITE);
			b.footing(0, z, Blocks.STONE_BRICKS, 12);
		}
	}

	private static void lighthouseTower(final Builder b) {
		for (int y = 1; y < LAMP_ROOM; y++) {
			double outer = towerRadius(y);
			for (int x = -5; x <= 5; x++) {
				for (int z = -5; z <= 5; z++) {
					double r = Math.sqrt(x * x + z * z);
					if (r <= HOLLOW || r > outer) {
						continue;
					}
					Block wall;
					if (y % 6 == 0) {
						wall = b.chance(0.12F) ? Blocks.DIORITE : Blocks.CALCITE; // the salt-white daymark bands
					} else if (y <= 3) {
						float roll = b.random().nextFloat();
						wall = roll < 0.3F ? Blocks.MOSSY_STONE_BRICKS : roll < 0.45F ? Blocks.CRACKED_STONE_BRICKS : roll < 0.52F && z > 0 ? Blocks.DARK_PRISMARINE : Blocks.STONE_BRICKS;
					} else {
						float roll = b.random().nextFloat();
						float cracked = 0.1F + y * 0.008F;
						wall = roll < cracked ? Blocks.CRACKED_STONE_BRICKS : roll < cracked + 0.07F ? Blocks.MOSSY_STONE_BRICKS : roll < cracked + 0.1F ? Blocks.TUFF_BRICKS : Blocks.STONE_BRICKS;
					}
					b.set(x, y, z, wall);
				}
			}
		}
		// the door to the south under a stepped arch
		b.clear(0, 1, 3, 0, 2, 5);
		b.stairs(-1, 3, 4, Blocks.STONE_BRICK_STAIRS, Direction.WEST, true);
		b.stairs(1, 3, 4, Blocks.STONE_BRICK_STAIRS, Direction.EAST, true);
		b.set(0, 3, 4, Blocks.CHISELED_STONE_BRICKS);
		// windows on the stair, each with salt weeping down the wall below it
		int[][] windows = {{4, 0, 1, 0}, {-4, 0, -1, 0}, {0, -4, 0, -1}, {3, 3, 1, 1}, {-3, 3, -1, 1}, {3, -3, 1, -1}, {-3, -3, -1, -1}};
		for (int i = 0; i < windows.length; i++) {
			int[] w = windows[i];
			for (int y = 4 + (i % 3) * 2; y < LAMP_ROOM - 1; y += 6) {
				for (int k = 0; k <= 2; k++) {
					int x = w[0] + w[2] * k - w[2];
					int z = w[1] + w[3] * k - w[3];
					double r = Math.sqrt(x * x + z * z);
					if (r > HOLLOW && r <= towerRadius(y)) {
						b.set(x, y, z, b.chance(0.7F) ? Blocks.GLASS_PANE : Blocks.AIR);
						for (int d = 1; d <= 3; d++) {
							if (b.chance(0.8F - d * 0.2F) && y - d > 0) {
								b.set(x, y - d, z, Blocks.CALCITE);
							}
						}
					}
				}
			}
		}
	}

	/** Height of the stair at a block of the hollow, on the turn that starts at height {@code base}. */
	private static int stairHeight(final int x, final int z, final int base) {
		// the stair starts at the door (south) and climbs clockwise seen from above
		double a = Math.atan2(z, x) - Math.PI / 2.0;
		a = (a % (Math.PI * 2.0) + Math.PI * 2.0) % (Math.PI * 2.0);
		return base + (int) Math.floor(a / (Math.PI * 2.0) * TURN);
	}

	private static boolean onStair(final int x, final int z) {
		double r = Math.sqrt(x * x + z * z);
		return r > SHAFT && r <= HOLLOW;
	}

	private static void lighthouseStair(final Builder b) {
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				if (!onStair(x, z)) {
					continue;
				}
				// the way up: the direction the stair turns at this block
				double tx = -z;
				double tz = x;
				Direction up = Math.abs(tx) >= Math.abs(tz) ? (tx > 0 ? Direction.EAST : Direction.WEST) : (tz > 0 ? Direction.SOUTH : Direction.NORTH);
				for (int turn = 0; turn < 3; turn++) {
					int h = stairHeight(x, z, 1 + turn * TURN);
					if (h > LAMP_ROOM) {
						continue;
					}
					b.stairs(x, h, z, b.chance(0.15F) ? Blocks.MOSSY_STONE_BRICK_STAIRS : Blocks.STONE_BRICK_STAIRS, up, false);
					if (h - 1 >= 1 && h != LAMP_ROOM) {
						b.slab(x, h - 1, z, Blocks.STONE_BRICK_SLAB, true);
					}
				}
			}
		}
	}

	/** The keeper's landing halfway up, where the supplies were kept. */
	private static void lighthouseLanding(final Builder b) {
		int floor = 11;
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				if (!onStair(x, z)) {
					continue;
				}
				int below = stairHeight(x, z, 1);
				int above = stairHeight(x, z, 1 + TURN);
				if (below >= floor - 2 || above == floor) {
					continue; // headroom for the stair beneath, and the stair itself
				}
				b.set(x, floor, z, b.chance(0.15F) ? Blocks.STRIPPED_SPRUCE_WOOD : Blocks.SPRUCE_PLANKS);
			}
		}
		// on the side of the landing the stair has already climbed past
		b.container(0, floor + 1, -2, Blocks.CHEST, Direction.SOUTH, "chests/lighthouse");
		b.container(-1, floor + 1, -2, Blocks.BARREL, Direction.UP, "chests/lighthouse");
		b.bed(2, floor + 1, -1, Blocks.BED.lightGray(), Direction.SOUTH);
		b.facing(1, floor + 1, -2, Blocks.LECTERN, Direction.SOUTH);
		b.candles(0, floor + 1, -3, Blocks.CANDLE, 2, false);
		b.set(3, floor + 1, 0, Blocks.LANTERN);
	}

	/** The weight shaft: a cage of bars, a short-snapped chain, the weight smashed below. */
	private static void lighthouseShaft(final Builder b) {
		for (int y = 1; y < LAMP_ROOM; y++) {
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					if (x == 0 && z == 0) {
						continue;
					}
					if (y % 5 == 0 && (x == 0 || z == 0)) {
						b.set(x, y, z, Blocks.STONE_BRICKS);
					} else if (x != 0 && z != 0) {
						b.set(x, y, z, Blocks.STONE_BRICK_WALL);
					} else {
						b.set(x, y, z, Blocks.COPPER_BARS.waxed().weathered());
					}
				}
			}
		}
		for (int y = LAMP_ROOM - 1; y >= 9; y--) {
			b.axis(0, y, 0, Blocks.IRON_CHAIN, Direction.Axis.Y);
		}
		b.set(0, 0, 0, Blocks.CRACKED_STONE_BRICKS);
		b.set(0, 1, 0, Blocks.DAMAGED_ANVIL);
	}

	/** The golden bell by the door, in a little roofed shrine of stone. */
	private static void lighthouseBell(final Builder b) {
		b.set(0, 1, 7, MistinnBlocks.GOLDEN_BELL);
		b.footing(0, 7, Blocks.STONE_BRICKS, 12);
		for (int x = -1; x <= 1; x += 2) {
			b.set(x, 0, 7, Blocks.STONE_BRICKS);
			b.footing(x, 7, Blocks.STONE_BRICKS, 12);
		}
		for (int x = -1; x <= 1; x += 2) {
			for (int y = 1; y <= 2; y++) {
				b.set(x, y, 8, Blocks.STONE_BRICK_WALL);
			}
			b.footing(x, 8, Blocks.STONE_BRICKS, 12);
			b.set(x, 0, 8, Blocks.STONE_BRICKS);
		}
		b.set(0, 1, 8, Blocks.STONE_BRICKS);
		b.set(0, 2, 8, Blocks.CHISELED_STONE_BRICKS);
		b.slab(-1, 3, 8, Blocks.STONE_BRICK_SLAB, false);
		b.slab(1, 3, 8, Blocks.STONE_BRICK_SLAB, false);
		b.stairs(0, 3, 8, Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false);
		b.candles(-1, 1, 7, Blocks.CANDLE, 3, false);
		b.candles(1, 1, 7, Blocks.DYED_CANDLE.white(), 2, false);
	}

	private static void lighthouseGallery(final Builder b) {
		// corbels flaring out under the gallery
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				double r = Math.sqrt(x * x + z * z);
				Direction in = Math.abs(x) >= Math.abs(z) ? (x > 0 ? Direction.WEST : Direction.EAST) : (z > 0 ? Direction.NORTH : Direction.SOUTH);
				if (r > towerRadius(LAMP_ROOM - 2) && r <= towerRadius(LAMP_ROOM - 2) + 1.0) {
					b.stairs(x, LAMP_ROOM - 2, z, Blocks.STONE_BRICK_STAIRS, in, true);
				}
				if (r > towerRadius(LAMP_ROOM - 1) && r <= 5.6) {
					b.set(x, LAMP_ROOM - 1, z, Blocks.STONE_BRICKS);
				} else if (r > 5.6 && r <= 6.6) {
					b.stairs(x, LAMP_ROOM - 1, z, Blocks.STONE_BRICK_STAIRS, in, true);
				}
			}
		}
		// the floor: soot round the dead lamp, salt crust at the rail, the hatch over the stair
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				double r = Math.sqrt(x * x + z * z);
				if (r > 7.4) {
					continue;
				}
				if (onStair(x, z)) {
					int h = stairHeight(x, z, 1 + TURN);
					if (h >= LAMP_ROOM - 2) {
						continue; // the hatch
					}
					if (stairHeight(x, z, 1 + 2 * TURN) == LAMP_ROOM) {
						continue; // the stair's last step, already set
					}
				}
				Block floor;
				float roll = b.random().nextFloat();
				if (r <= 2.4) {
					floor = roll < 0.4F ? Blocks.DEEPSLATE_TILES : roll < 0.6F ? Blocks.CRACKED_DEEPSLATE_TILES : Blocks.POLISHED_DEEPSLATE;
				} else if (r <= 4.2 && roll < 0.3F) {
					floor = roll < 0.12F ? Blocks.SMOOTH_BASALT : Blocks.TUFF_BRICKS;
				} else if (r > 6.0 && roll < 0.25F) {
					floor = Blocks.CALCITE;
				} else {
					floor = roll < 0.15F ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS;
				}
				b.set(x, LAMP_ROOM, z, floor);
				if (r > 6.4) {
					b.set(x, LAMP_ROOM + 1, z, Blocks.IRON_BARS);
				}
			}
		}
		// rail posts at the eight points, with candle stubs
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4.0;
			int x = (int) Math.round(Math.cos(a) * 6.9);
			int z = (int) Math.round(Math.sin(a) * 6.9);
			b.set(x, LAMP_ROOM + 1, z, Blocks.STONE_BRICK_WALL);
			if (i % 2 == 0) {
				b.candles(x, LAMP_ROOM + 2, z, Blocks.CANDLE, 1 + i % 3, false);
			}
		}
		for (int[] corner : new int[][] {{4, 4}, {-4, 4}, {4, -4}, {-4, -4}}) {
			b.set(corner[0], LAMP_ROOM + 1, corner[1], MistinnBlocks.LAMP_STAND);
		}
		// the great dead lamp in its housing, glass cracked out of it
		b.set(0, LAMP_ROOM + 1, 0, Blocks.COPPER_BLOCK.waxed().oxidized());
		b.set(0, LAMP_ROOM + 2, 0, Blocks.COPPER_GRATE.waxed().oxidized());
		b.set(0, LAMP_ROOM + 3, 0, Blocks.COPPER_BULB.waxed().oxidized());
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				if (x == 0 && z == 0) {
					continue;
				}
				boolean corner = x != 0 && z != 0;
				for (int y = LAMP_ROOM + 1; y <= LAMP_ROOM + 3; y++) {
					if (corner) {
						b.set(x, y, z, y == LAMP_ROOM + 2 ? Blocks.COPPER_BARS.waxed().weathered().defaultBlockState() : Blocks.CUT_COPPER.waxed().weathered().defaultBlockState());
					} else if (y == LAMP_ROOM + 1) {
						b.set(x, y, z, Blocks.COPPER_GRATE.waxed().weathered());
					} else if (b.chance(0.8F)) {
						b.set(x, y, z, Blocks.GLASS_PANE);
					}
				}
				Direction in = Math.abs(x) >= Math.abs(z) && x != 0 ? (x > 0 ? Direction.WEST : Direction.EAST) : (z > 0 ? Direction.NORTH : Direction.SOUTH);
				if (corner) {
					b.slab(x, LAMP_ROOM + 4, z, Blocks.CUT_COPPER_SLAB.waxed().oxidized(), false);
				} else {
					b.stairs(x, LAMP_ROOM + 4, z, Blocks.CUT_COPPER_STAIRS.waxed().oxidized(), in, false);
				}
			}
		}
		b.set(0, LAMP_ROOM + 4, 0, Blocks.COPPER_BLOCK.waxed().oxidized());
		b.set(0, LAMP_ROOM + 5, 0, Blocks.LIGHTNING_ROD.waxed().oxidized());
		// spare oil jars and a coil of chain at the rail, out of the keeper's way
		b.set(5, LAMP_ROOM + 1, -2, Blocks.DECORATED_POT);
		b.set(5, LAMP_ROOM + 1, -3, Blocks.DECORATED_POT);
		b.axis(-6, LAMP_ROOM + 1, 1, Blocks.IRON_CHAIN, Direction.Axis.Z);
	}

	/** The keeper's cottage west of the tower: hearth, bed, table; half the roof fallen in. */
	private static void keeperCottage(final Builder b) {
		int x0 = -12;
		int x1 = -7;
		int z0 = -3;
		int z1 = 2;
		b.clear(x0 - 1, 1, z0 - 2, x1 + 1, 8, z1 + 2);
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
				b.set(x, 0, z, wall ? Blocks.COBBLESTONE : b.chance(0.3F) ? Blocks.COARSE_DIRT : Blocks.SPRUCE_PLANKS);
				b.footing(x, z, Blocks.COBBLESTONE, 12);
				if (!wall) {
					continue;
				}
				boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
				for (int y = 1; y <= 3; y++) {
					if (corner) {
						b.set(x, y, z, Blocks.STRIPPED_SPRUCE_LOG);
					} else if (y == 1) {
						b.set(x, y, z, b.chance(0.3F) ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE);
					} else {
						b.set(x, y, z, Blocks.SPRUCE_PLANKS);
					}
				}
			}
		}
		// the door toward the tower, windows with the glass gone from one
		b.clear(x1, 1, 0, x1, 2, 0);
		b.set(-9, 2, z0, Blocks.GLASS_PANE);
		b.set(-10, 2, z1, Blocks.AIR);
		b.set(x0, 2, 1, Blocks.GLASS_PANE);
		// the roof: a gable along the cottage; the north half has fallen in
		for (int step = 0; step < 4; step++) {
			int y = 4 + step;
			for (int x = x0 - 1; x <= x1 + 1; x++) {
				boolean fallen = x >= -11 && x <= -8;
				int north = z0 - 1 + step;
				int south = z1 + 1 - step;
				if (!fallen || step == 3) {
					b.stairs(x, y, north, Blocks.SPRUCE_STAIRS, Direction.SOUTH, false);
				}
				b.stairs(x, y, south, Blocks.SPRUCE_STAIRS, Direction.NORTH, false);
			}
			// gable ends
			for (int z = z0 + step; z <= z1 - step; z++) {
				if (y <= 6) {
					b.set(x0, y, z, Blocks.SPRUCE_PLANKS);
					b.set(x1, y, z, Blocks.SPRUCE_PLANKS);
				}
			}
		}
		for (int x = -11; x <= -8; x += 3) {
			b.axis(x, 5, -2, Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z); // bare rafters
		}
		// the hearth and its chimney on the west wall
		for (int y = 1; y <= 8; y++) {
			b.set(x0 - 1, y, -1, y <= 2 ? Blocks.COBBLESTONE : Blocks.MOSSY_COBBLESTONE);
		}
		b.footing(x0 - 1, -1, Blocks.COBBLESTONE, 12);
		b.set(x0 - 1, 0, -1, Blocks.COBBLESTONE);
		b.set(x0, 1, -1, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false));
		b.set(x0, 2, -1, Blocks.COBBLESTONE);
		// a made bed, the table with a dead candle, a shelf; the fallen roof on the floor
		b.bed(-11, 1, 1, Blocks.BED.blue(), Direction.WEST);
		b.set(-9, 1, -1, Blocks.SPRUCE_FENCE);
		b.candles(-9, 2, -1, Blocks.CANDLE, 1, false);
		b.stairs(-9, 1, -2, Blocks.SPRUCE_STAIRS, Direction.NORTH, false);
		b.set(-8, 1, 1, Blocks.BOOKSHELF);
		b.slab(-10, 1, -2, Blocks.SPRUCE_SLAB, false);
		b.stairs(-8, 1, -2, Blocks.SPRUCE_STAIRS, Direction.EAST, false);
		b.set(-10, 3, 0, Blocks.COBWEB);
		b.set(-8, 3, 1, Blocks.COBWEB);
	}
}
