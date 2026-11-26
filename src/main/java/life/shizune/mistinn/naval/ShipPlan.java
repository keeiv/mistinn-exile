package life.shizune.mistinn.naval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * The pirate ship, block by block, in the ship's own frame: the bow points east (+x), y 0 is the
 * top layer of the sea, z runs across the beam. One plan serves the ship sailing in (drawn whole by
 * the client) and the ship at anchor (placed as blocks by {@link ShipFrame}).
 *
 * <p>A black-sailed two-master: a copper-sheathed hull with an oxblood strake, a main deck of six
 * guns, a stern castle over the captain's cabin with the wheel on top, a hold with barrels under a
 * hatch, and ladders up the hull on both sides so a hunter in the water can climb aboard.
 */
public final class ShipPlan {
	/** The ends of the hull, and how far the bowsprit reaches. */
	public static final int STERN = -12;
	public static final int BOW = 12;
	public static final int BOWSPRIT = 16;
	/** The main deck's floor (hunters stand a block above it), and the quarterdeck's. */
	public static final int DECK = 1;
	public static final int QUARTER = 4;
	/** The lowest and highest layer the ship takes up, and its widest reach across the beam. */
	public static final int BOTTOM = -3;
	public static final int TOP = 20;
	public static final int BEAM = 5;
	/** Where the stern castle begins, from the stern forward. */
	public static final int CASTLE = -6;
	/** The two masts. */
	public static final int MAIN_MAST = -1;
	public static final int FORE_MAST = 7;

	/** A gun: its muzzle in the bulwark, the way it points, and where its gunner stands. */
	public record Gun(BlockPos muzzle, Direction out, BlockPos crew) {
	}

	/** The guns along each side, by x. */
	private static final int[] GUN_X = {-3, 0, 3};
	/** Where the ladders come up the hull, by x. */
	private static final int[] LADDER_X = {-4, 2};

	/** The captain's chest, in the cabin at the stern. */
	public static final BlockPos CHEST = new BlockPos(STERN + 1, DECK + 1, 0);
	/** Where the captain stands, behind the wheel. */
	public static final BlockPos CAPTAIN = new BlockPos(STERN + 1, QUARTER + 1, 0);
	/** The wheel. */
	public static final BlockPos WHEEL = new BlockPos(STERN + 2, QUARTER + 1, 0);
	/** Where the boarders wait on the main deck, in the order they are filled. */
	public static final List<BlockPos> BOARDER_SPOTS = List.of(
		new BlockPos(1, DECK + 1, 1), new BlockPos(2, DECK + 1, -1), new BlockPos(-3, DECK + 1, 0), new BlockPos(5, DECK + 1, 1),
		new BlockPos(5, DECK + 1, -1), new BlockPos(-4, DECK + 1, -1), new BlockPos(9, DECK + 1, 0));
	/** Where the marksmen stand: the quarterdeck first, then the bow. */
	public static final List<BlockPos> MARKSMAN_SPOTS = List.of(
		new BlockPos(CASTLE - 2, QUARTER + 1, 2), new BlockPos(CASTLE - 2, QUARTER + 1, -2), new BlockPos(STERN + 1, QUARTER + 1, 2),
		new BlockPos(STERN + 1, QUARTER + 1, -2), new BlockPos(10, DECK + 1, 0));
	/** The barrels in the hold, which keep the crew's stores. */
	public static final List<BlockPos> HOLD_BARRELS = List.of(
		new BlockPos(-3, -2, 2), new BlockPos(-2, -2, 2), new BlockPos(0, -2, -2), new BlockPos(2, -2, 2), new BlockPos(-6, -2, -2));

	/** The hull's sheathing under the water, and the sails' cloth. */
	private static final Block COPPER = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("waxed_weathered_copper"));
	private static final Block BLACK = Blocks.WOOL.pick(DyeColor.BLACK);
	private static final Block GRAY = Blocks.WOOL.pick(DyeColor.GRAY);
	private static final Block WHITE = Blocks.WOOL.pick(DyeColor.WHITE);

	private static Map<BlockPos, BlockState> blocks;
	private static List<Gun> guns;
	private static List<BlockPos> boardings;

	private ShipPlan() {
	}

	/**
	 * The hull's half width at a station along the keel, at the deck and above: full amidships,
	 * narrowing to the stem at the bow and a little at the transom.
	 */
	public static int halfWidth(final int x) {
		if (x <= STERN) {
			return 3;
		}
		if (x <= 6) {
			return 4;
		}
		if (x <= 8) {
			return 3;
		}
		if (x <= 10) {
			return 2;
		}
		return x == 11 ? 1 : 0;
	}

	/** The hull's half width at a station and a layer: the bilge narrows under the waterline. */
	private static int halfWidth(final int x, final int y) {
		int w = halfWidth(x);
		return y >= -1 ? w : w - (-1 - y);
	}

	private static boolean inHull(final int x, final int y, final int z) {
		return x >= STERN && x <= BOW && y >= BOTTOM && y <= DECK && Math.abs(z) <= halfWidth(x, y);
	}

	/** Every block of the ship, air in the hold included, in the ship's frame. */
	public static synchronized Map<BlockPos, BlockState> blocks() {
		if (blocks == null) {
			blocks = Collections.unmodifiableMap(build());
		}
		return blocks;
	}

	/** The guns, three a side. */
	public static synchronized List<Gun> guns() {
		if (guns == null) {
			List<Gun> list = new ArrayList<>();
			for (int side : new int[] {1, -1}) {
				for (int x : GUN_X) {
					int w = halfWidth(x);
					list.add(new Gun(new BlockPos(x, DECK + 1, side * w), side > 0 ? Direction.SOUTH : Direction.NORTH, new BlockPos(x, DECK + 1, side * (w - 2))));
				}
			}
			guns = List.copyOf(list);
		}
		return guns;
	}

	/** The deck cells a hunter steps onto from the top of each ladder. */
	public static synchronized List<BlockPos> boardings() {
		if (boardings == null) {
			List<BlockPos> list = new ArrayList<>();
			for (int x : LADDER_X) {
				for (int side : new int[] {1, -1}) {
					list.add(new BlockPos(x, DECK + 1, side * (halfWidth(x) - 1)));
				}
			}
			boardings = List.copyOf(list);
		}
		return boardings;
	}

	/** Whether a point in the ship's frame (block {@code (x, z)} spans x to x+1) is aboard: over the deck, within the bulwarks, up to the masthead. */
	public static boolean aboard(final double x, final double y, final double z) {
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		return bx >= STERN && bx <= BOW && y >= DECK + 0.5 && y <= TOP + 1 && Math.abs(bz) <= halfWidth(bx);
	}

	// ── Building ──────────────────────────────────────────────────

	private static Map<BlockPos, BlockState> build() {
		Map<BlockPos, BlockState> m = new LinkedHashMap<>();
		hull(m);
		bulwarks(m);
		castle(m);
		masts(m);
		fittings(m);
		connect(m);
		return m;
	}

	private static void put(final Map<BlockPos, BlockState> m, final int x, final int y, final int z, final BlockState state) {
		m.put(new BlockPos(x, y, z), state);
	}

	private static void put(final Map<BlockPos, BlockState> m, final int x, final int y, final int z, final Block block) {
		put(m, x, y, z, block.defaultBlockState());
	}

	/** The hull up to the main deck: copper below the water, dark oak above, an oxblood strake at the deck, the hold left empty. */
	private static void hull(final Map<BlockPos, BlockState> m) {
		for (int x = STERN; x <= BOW; x++) {
			for (int y = BOTTOM; y <= DECK; y++) {
				int w = halfWidth(x, y);
				for (int z = -w; z <= w; z++) {
					boolean shell = !inHull(x + 1, y, z) || !inHull(x - 1, y, z) || !inHull(x, y, z + 1) || !inHull(x, y, z - 1)
						|| !inHull(x, y - 1, z) || x == STERN;
					if (y == DECK) {
						put(m, x, y, z, Math.abs(z) == w || x == STERN || x == BOW
							? Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X)
							: Blocks.SPRUCE_PLANKS.defaultBlockState());
					} else if (shell) {
						put(m, x, y, z, y <= -1 ? COPPER : Blocks.DARK_OAK_PLANKS);
					} else {
						put(m, x, y, z, Blocks.AIR);
					}
				}
			}
		}
	}

	/** The bulwarks round the main deck with the rail on top, the gunports and the ladder openings. */
	private static void bulwarks(final Map<BlockPos, BlockState> m) {
		for (int x = CASTLE + 1; x <= BOW; x++) {
			int w = halfWidth(x);
			for (int side : w == 0 ? new int[] {0} : new int[] {1, -1}) {
				int z = side * w;
				if (isLadder(x)) {
					continue;
				}
				put(m, x, DECK + 1, z, Blocks.MANGROVE_PLANKS);
				put(m, x, DECK + 2, z, Blocks.DARK_OAK_FENCE);
			}
			// where the hull narrows the bulwark closes the step between one station and the next
			int next = halfWidth(x + 1);
			if (x < BOW && next < w - 1) {
				for (int z = next + 1; z < w; z++) {
					put(m, x, DECK + 1, z, Blocks.DARK_OAK_PLANKS);
					put(m, x, DECK + 1, -z, Blocks.DARK_OAK_PLANKS);
					put(m, x, DECK + 2, z, Blocks.DARK_OAK_FENCE);
					put(m, x, DECK + 2, -z, Blocks.DARK_OAK_FENCE);
				}
			}
		}
		for (Gun gun : guns()) {
			// the muzzle looks out through the bulwark; the barrel lies behind it on its carriage
			put(m, gun.muzzle().getX(), gun.muzzle().getY(), gun.muzzle().getZ(),
				Blocks.DISPENSER.defaultBlockState().setValue(DirectionalBlock.FACING, gun.out()));
			BlockPos barrel = gun.muzzle().relative(gun.out().getOpposite());
			put(m, barrel.getX(), barrel.getY(), barrel.getZ(), Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, Direction.EAST));
		}
		for (int x : LADDER_X) {
			int w = halfWidth(x);
			for (int side : new int[] {1, -1}) {
				Direction out = side > 0 ? Direction.SOUTH : Direction.NORTH;
				// up the hull from under the water to the deck's edge, against the planks
				for (int y = -1; y <= DECK; y++) {
					put(m, x, y, side * (w + 1), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, out)
						.setValue(BlockStateProperties.WATERLOGGED, y <= 0));
				}
			}
		}
		// the bowsprit and a lantern at its end
		for (int x = BOW; x <= BOWSPRIT; x++) {
			put(m, x, DECK + 2, 0, Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
		}
		put(m, BOWSPRIT, DECK + 3, 0, Blocks.LANTERN);
	}

	private static boolean isLadder(final int x) {
		for (int ladder : LADDER_X) {
			if (ladder == x) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The stern castle: walls up to the quarterdeck, the captain's cabin inside with its door to the
	 * main deck and windows in the transom, stairs up both sides, the wheel and the stern lanterns.
	 */
	private static void castle(final Map<BlockPos, BlockState> m) {
		for (int x = STERN; x <= CASTLE; x++) {
			int w = halfWidth(x);
			for (int y = DECK + 1; y <= QUARTER - 1; y++) {
				for (int z = -w; z <= w; z++) {
					boolean cabin = x > STERN && x < CASTLE && Math.abs(z) <= 1;
					put(m, x, y, z, cabin ? Blocks.AIR.defaultBlockState() : Blocks.DARK_OAK_PLANKS.defaultBlockState());
				}
			}
			for (int z = -w; z <= w; z++) {
				put(m, x, QUARTER, z, Math.abs(z) == w || x == STERN ? Blocks.MANGROVE_PLANKS : Blocks.SPRUCE_PLANKS);
				if (Math.abs(z) == w || x == STERN) {
					put(m, x, QUARTER + 1, z, Blocks.DARK_OAK_FENCE);
				}
			}
		}
		// the cabin: windows astern, the door forward, the chest and a lantern
		for (int z = -1; z <= 1; z++) {
			for (int y = DECK + 1; y <= DECK + 2; y++) {
				put(m, STERN, y, z, Blocks.GLASS_PANE);
			}
		}
		put(m, CASTLE, DECK + 1, 0, Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST)
			.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT));
		put(m, CASTLE, DECK + 2, 0, Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST)
			.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT));
		put(m, CHEST.getX(), CHEST.getY(), CHEST.getZ(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
		put(m, STERN + 1, DECK + 1, 1, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
		put(m, STERN + 1, DECK + 1, -1, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
		put(m, CASTLE - 2, DECK + 2, 0, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		// stairs up both sides, along the castle's walls from the main deck
		for (int side : new int[] {1, -1}) {
			int z = side * (halfWidth(CASTLE) - 1);
			put(m, CASTLE + 1, DECK + 1, z, stair(Direction.WEST));
			put(m, CASTLE, DECK + 2, z, stair(Direction.WEST));
			put(m, CASTLE - 1, QUARTER, z, stair(Direction.WEST));
			put(m, CASTLE, DECK + 1, z, Blocks.DARK_OAK_PLANKS);
			put(m, CASTLE, QUARTER, z, Blocks.AIR);
			put(m, CASTLE, QUARTER + 1, z, Blocks.AIR);
			put(m, CASTLE - 1, DECK + 1, z, Blocks.DARK_OAK_PLANKS);
			put(m, CASTLE - 1, DECK + 2, z, Blocks.DARK_OAK_PLANKS);
		}
		// the rail across the front of the quarterdeck, open above the stairs
		for (int z = -(halfWidth(CASTLE) - 2); z <= halfWidth(CASTLE) - 2; z++) {
			put(m, CASTLE, QUARTER + 1, z, Blocks.DARK_OAK_FENCE);
		}
		// the wheel, and the stern lanterns on their posts
		put(m, WHEEL.getX(), WHEEL.getY(), WHEEL.getZ(), Blocks.GRINDSTONE.defaultBlockState()
			.setValue(GrindstoneBlock.FACE, AttachFace.WALL).setValue(GrindstoneBlock.FACING, Direction.EAST));
		for (int side : new int[] {1, -1}) {
			put(m, STERN, QUARTER + 2, side * 3, Blocks.LANTERN);
		}
	}

	private static BlockState stair(final Direction up) {
		return Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, up).setValue(StairBlock.HALF, Half.BOTTOM);
	}

	/**
	 * Two masts carrying great black battened sails along the keel, so a hunter off either side sees
	 * them whole, the salt crow in white across the main; topsails above, the flag at the masthead.
	 * The sails hang clear of the deck: the lowest batten is overhead, not in the way.
	 */
	private static void masts(final Map<BlockPos, BlockState> m) {
		for (int y = -2; y <= TOP; y++) {
			put(m, MAIN_MAST, y, 0, Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
		}
		for (int y = -2; y <= 15; y++) {
			put(m, FORE_MAST, y, 0, Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
		}
		// main sail and topsail, fore sail and topsail: x from, x to, y from, y to
		sail(m, -5, 3, 5, 13, true);
		sail(m, -4, 2, 15, 17, false);
		sail(m, 4, 9, 5, 11, false);
		sail(m, 5, 8, 13, 14, false);
		// battens at the foot and the head of each sail
		batten(m, -5, 3, 4);
		batten(m, -5, 3, 14);
		batten(m, -4, 2, 18);
		batten(m, 4, 9, 4);
		batten(m, 4, 9, 12);
		batten(m, 5, 8, 15);
		// the flag streams aft from the main masthead
		for (int x = MAIN_MAST - 3; x <= MAIN_MAST - 1; x++) {
			for (int y = TOP - 1; y <= TOP; y++) {
				put(m, x, y, 0, x == MAIN_MAST - 2 && y == TOP ? WHITE : BLACK);
			}
		}
		// lanterns on the rail posts along both sides
		for (int side : new int[] {1, -1}) {
			for (int x : new int[] {-2, 4, 9}) {
				int z = side * halfWidth(x);
				if (m.get(new BlockPos(x, DECK + 2, z)) != null && m.get(new BlockPos(x, DECK + 2, z)).getBlock() instanceof FenceBlock) {
					put(m, x, DECK + 3, z, Blocks.LANTERN);
				}
			}
		}
	}

	/** A batten along the keel at height {@code y}, from {@code x0} to {@code x1}, the mast left standing through it. */
	private static void batten(final Map<BlockPos, BlockState> m, final int x0, final int x1, final int y) {
		for (int x = x0; x <= x1; x++) {
			if (x != MAIN_MAST && x != FORE_MAST) {
				put(m, x, y, 0, Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
			}
		}
	}

	/**
	 * A sail in the plane along the keel, from {@code x0} to {@code x1} and {@code y0} to {@code y1},
	 * the mast left standing through it, worn grey here and there and torn at its foot.
	 */
	private static void sail(final Map<BlockPos, BlockState> m, final int x0, final int x1, final int y0, final int y1, final boolean crow) {
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				if (x == MAIN_MAST || x == FORE_MAST) {
					continue;
				}
				boolean torn = y == y0 && (x == x0 || x == x1 || x == x0 + 3) || y == y0 + 1 && x == x1;
				if (torn) {
					continue;
				}
				boolean worn = (x * 7 + y * 13 & 7) == 0;
				put(m, x, y, 0, worn ? GRAY : BLACK);
			}
		}
		if (crow) {
			// the salt crow, wings up, in white across the middle of the main sail
			int cx = (x0 + x1) / 2;
			int cy = (y0 + y1) / 2;
			int[][] cells = {{2, -3}, {2, 3}, {1, -3}, {1, -2}, {1, 2}, {1, 3}, {0, -2}, {0, -1}, {0, 0}, {0, 1}, {0, 2}, {-1, 0}, {-2, -1}, {-2, 1}};
			for (int[] cell : cells) {
				int x = cx + cell[1];
				if (x != MAIN_MAST && x != FORE_MAST) {
					put(m, x, cy + cell[0], 0, WHITE);
				}
			}
		}
	}

	/** The hatch to the hold with its ladder, the hold's barrels and lanterns, and barrels on deck. */
	private static void fittings(final Map<BlockPos, BlockState> m) {
		int hatchX = 5;
		int hatchZ = -(halfWidth(hatchX) - 1);
		put(m, hatchX, DECK, hatchZ, Blocks.DARK_OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP)
			.setValue(TrapDoorBlock.FACING, Direction.SOUTH).setValue(TrapDoorBlock.OPEN, false));
		for (int y = -1; y <= 0; y++) {
			put(m, hatchX, y, hatchZ, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
		}
		for (BlockPos barrel : HOLD_BARRELS) {
			put(m, barrel.getX(), barrel.getY(), barrel.getZ(), Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
		}
		put(m, -2, 0, 0, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		put(m, 3, 0, 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		put(m, 9, DECK + 1, 1, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
		put(m, 9, DECK + 1, -1, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
		put(m, -4, DECK + 1, 2, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
	}

	/** Fences and panes join their neighbours as they would in the world, so the plan draws as it will stand. */
	private static void connect(final Map<BlockPos, BlockState> m) {
		for (Map.Entry<BlockPos, BlockState> entry : m.entrySet()) {
			BlockState state = entry.getValue();
			if (!(state.getBlock() instanceof FenceBlock) && !(state.getBlock() instanceof IronBarsBlock)) {
				continue;
			}
			BlockPos pos = entry.getKey();
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockState other = m.get(pos.relative(direction));
				boolean joins = other != null && (other.getBlock() == state.getBlock()
					|| other.getBlock() instanceof FenceBlock && state.getBlock() instanceof FenceBlock
					|| other.isSolidRender() && !(other.getBlock() instanceof LanternBlock));
				state = state.setValue(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(direction), joins);
			}
			entry.setValue(state);
		}
	}

	/** How the plan's block at a place is lit, for the client: a lantern glows. */
	public static boolean glows(final BlockState state) {
		return state.getBlock() instanceof LanternBlock;
	}

	/** The plan's blocks that are seen from outside: everything but air and what is closed in on all six sides. */
	public static List<Map.Entry<BlockPos, BlockState>> visible() {
		List<Map.Entry<BlockPos, BlockState>> list = new ArrayList<>();
		Map<BlockPos, BlockState> m = blocks();
		for (Map.Entry<BlockPos, BlockState> entry : m.entrySet()) {
			if (entry.getValue().isAir()) {
				continue;
			}
			boolean hidden = true;
			for (Direction direction : Direction.values()) {
				BlockState other = m.get(entry.getKey().relative(direction));
				if (other == null || !other.isSolidRender()) {
					hidden = false;
					break;
				}
			}
			if (!hidden) {
				list.add(entry);
			}
		}
		return list;
	}
}
