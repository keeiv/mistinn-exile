package life.shizune.mistinn.terra;

import java.util.Set;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.domain.DomainService;
import life.shizune.mistinn.registry.MistinnBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * How a Terra highland takes the land it lies on, column by column and only where nature put
 * things: the grass becomes jade turf over grey-violet vein soil, the stone near the surface pale
 * vein stone (here and there the crystal breaks through it), the trees mist pines. Three rings:
 * the outer waste of crystal veins and outcrops; the terraced valleys of jade grass, ferns, reeds
 * by the water, wild crystal sprouts and new mist pines; the crown, where the lattice crystal
 * rises in spires.
 */
public final class TerraShaper implements DomainService.Shaper {
	public static final ResourceKey<Biome> VEINS = key("terra_veins");
	public static final ResourceKey<Biome> TERRACES = key("terra_terraces");
	public static final ResourceKey<Biome> CROWN = key("terra_crown");

	/** Where the crown ends and the terraces begin, and where the terraces end, as parts of the radius. */
	public static final double CROWN_TO = 0.3;
	public static final double TERRACES_TO = 0.72;

	/** The three rings. */
	public enum Zone {
		CROWN, TERRACES, VEINS
	}

	private static final Set<Block> TURF = Set.of(Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.DIRT_PATH, Blocks.MOSS_BLOCK);
	private static final Set<Block> EARTH = Set.of(Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT);
	private static final Set<Block> STONE = Set.of(Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.TUFF, Blocks.CALCITE);
	private static final int DEPTH = 3;
	private static final int TREE = 40;
	private static final int FLAGS = Block.UPDATE_CLIENTS;
	/** Crown spires stand on a grid of cells this wide, one in some of them. */
	private static final int SPIRE_CELL = 9;
	/** New mist pines stand on a grid of cells this wide, one in some of them. */
	private static final int PINE_CELL = 8;
	/** How high a terrace step is. */
	private static final int TERRACE = 3;

	private static ResourceKey<Biome> key(final String name) {
		return ResourceKey.create(Registries.BIOME, Mistinn.id(name));
	}

	/** Which ring a column falls in: its distance as a part of the radius, the borders between rings wavering a little. */
	public static Zone zone(final Domain domain, final int x, final int z, final double distance) {
		double part = distance / domain.radius() + 0.04 * Math.sin(x * 0.09 + domain.id()) + 0.03 * Math.sin(z * 0.11 + domain.id() * 2.0);
		return part < CROWN_TO ? Zone.CROWN : part < TERRACES_TO ? Zone.TERRACES : Zone.VEINS;
	}

	@Override
	public @Nullable Holder<Biome> biome(final ServerLevel level, final Domain domain, final double distance) {
		// a biome cell has no single column: its centre stands for it
		Zone zone = distance / domain.radius() < CROWN_TO ? Zone.CROWN : distance / domain.radius() < TERRACES_TO ? Zone.TERRACES : Zone.VEINS;
		ResourceKey<Biome> key = switch (zone) {
			case CROWN -> CROWN;
			case TERRACES -> TERRACES;
			case VEINS -> VEINS;
		};
		return level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(key);
	}

	@Override
	public void column(final ServerLevel level, final LevelChunk chunk, final Domain domain, final int x, final int z, final double distance) {
		Zone zone = zone(domain, x, z, distance);
		int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int ground = Integer.MIN_VALUE;
		boolean tree = false;
		// what grew on the ground, to put back as the highland's own if nothing else is set there
		Block plant = null;
		for (int y = top; y > top - TREE && y > level.getMinY(); y--) {
			BlockState state = chunk.getBlockState(at.set(x, y, z));
			if (state.isAir()) {
				continue;
			}
			if (state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)) {
				// the tree's crown turns to mist pine, keeping how far it is from wood
				level.setBlock(at, MistinnBlocks.MIST_PINE_LEAVES.defaultBlockState()
					.setValue(LeavesBlock.DISTANCE, state.getValue(LeavesBlock.DISTANCE))
					.setValue(LeavesBlock.WATERLOGGED, state.getValue(LeavesBlock.WATERLOGGED)), FLAGS);
				tree = true;
				continue;
			}
			if (state.is(BlockTags.LOGS)) {
				// a trunk under a crown is a mist pine's
				if (tree && !state.is(MistinnBlocks.MIST_PINE_LOG) && state.hasProperty(RotatedPillarBlock.AXIS)) {
					level.setBlock(at, MistinnBlocks.MIST_PINE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS)), FLAGS);
				}
				continue;
			}
			if (state.is(Blocks.SNOW) || state.is(Blocks.VINE)) {
				level.setBlock(at, Blocks.AIR.defaultBlockState(), FLAGS);
				continue;
			}
			if (growth(state)) {
				// the grass and flowers give way to the highland's own (put back below); tall plants go
				level.setBlock(at, Blocks.AIR.defaultBlockState(), FLAGS);
				if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN) || state.is(BlockTags.SMALL_FLOWERS)) {
					plant = state.is(Blocks.FERN) ? MistinnBlocks.JADE_FERN : MistinnBlocks.JADE_GRASS;
				}
				continue;
			}
			if (!state.getFluidState().isEmpty()) {
				return;
			}
			ground = y;
			break;
		}
		if (ground == Integer.MIN_VALUE) {
			return;
		}
		boolean surface = false;
		for (int depth = 0; depth <= DEPTH; depth++) {
			at.set(x, ground - depth, z);
			BlockState state = chunk.getBlockState(at);
			BlockState into = null;
			if (TURF.contains(state.getBlock()) || EARTH.contains(state.getBlock())) {
				into = depth == 0 && chunk.getBlockState(at.above()).isAir() || TURF.contains(state.getBlock())
					? (zone != Zone.TERRACES && hash(x, z, domain.id() + 3) < (zone == Zone.CROWN ? 0.5 : 0.45) ? MistinnBlocks.VEIN_SOIL : MistinnBlocks.JADE_TURF)
						.defaultBlockState()
					: MistinnBlocks.VEIN_SOIL.defaultBlockState();
			} else if (STONE.contains(state.getBlock())) {
				into = hash(x * 7 + depth, z, domain.id() + 11) < (zone == Zone.VEINS ? 0.05 : 0.02)
					? MistinnBlocks.VEIN_CRYSTAL_ORE.defaultBlockState() : MistinnBlocks.VEIN_STONE.defaultBlockState();
			}
			if (into != null) {
				level.setBlock(at, into, FLAGS);
				surface |= depth == 0;
			}
		}
		if (surface && zone == Zone.TERRACES) {
			ground = terrace(level, chunk, domain, x, ground, z);
			if (chunk.getBlockState(new BlockPos(x, ground, z)).is(Blocks.WATER)) {
				return;
			}
		}
		if (surface) {
			decorate(level, chunk, domain, zone, x, ground, z, distance);
		}
		BlockPos above = new BlockPos(x, ground + 1, z);
		if (plant != null && chunk.getBlockState(above).isAir()) {
			level.setBlock(above, plant.defaultBlockState(), FLAGS);
		}
	}

	/**
	 * In the valley the slopes are stepped into terraces, a step every three blocks up from the
	 * sea's level: the ground is built up to its step (never cut down, so nothing standing on it is
	 * left hanging, and never where something already stands), the step's face walled in vein
	 * stone bricks where it stands above a neighbour's, and some level stretches flooded as
	 * paddies. Returns the column's ground now.
	 */
	private static int terrace(final ServerLevel level, final LevelChunk chunk, final Domain domain, final int x, final int ground, final int z) {
		int sea = level.getSeaLevel();
		if (ground < sea) {
			return ground;
		}
		int step = stepOf(ground, sea);
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int y = ground + 1; y <= step; y++) {
			BlockState there = chunk.getBlockState(at.set(x, y, z));
			if (!there.isAir() && !there.canBeReplaced()) {
				return ground;
			}
		}
		if (step > ground) {
			for (int y = ground; y < step; y++) {
				level.setBlock(at.set(x, y, z), MistinnBlocks.VEIN_SOIL.defaultBlockState(), FLAGS);
			}
			level.setBlock(at.set(x, step, z), MistinnBlocks.JADE_TURF.defaultBlockState(), FLAGS);
		}
		int lowest = step;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			int nx = x + side.getStepX();
			int nz = z + side.getStepZ();
			if (!level.hasChunk(nx >> 4, nz >> 4)) {
				continue;
			}
			int neighbour = groundAt(level, nx, nz);
			lowest = Math.min(lowest, neighbour < sea ? neighbour : stepOf(neighbour, sea));
		}
		// the step's face, where it stands above its neighbour, walled in stone
		for (int y = Math.max(lowest + 1, step - TERRACE * 3); y < step; y++) {
			level.setBlock(at.set(x, y, z), MistinnBlocks.VEIN_STONE_BRICKS.defaultBlockState(), FLAGS);
		}
		if (lowest == step && paddy(domain, x, z, step)) {
			level.setBlock(at.set(x, step, z), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
		}
		return step;
	}

	/** How high a terrace step at this height stands: up to the next multiple of the step from the sea's level. */
	private static int stepOf(final int height, final int sea) {
		return Math.floorDiv(height - sea + TERRACE - 1, TERRACE) * TERRACE + sea;
	}

	/** The ground under a column, looking past trees, crystal and growth. */
	private static int groundAt(final ServerLevel level, final int x, final int z) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
		for (int i = 0; i < TREE; i++, at.move(Direction.DOWN)) {
			BlockState state = level.getBlockState(at);
			if (state.isAir() || state.canBeReplaced() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || !state.getFluidState().isEmpty()
				|| state.is(MistinnBlocks.LATTICE_CRYSTAL) || state.is(MistinnBlocks.TERRA_CRYSTAL_BLOCK) || state.getBlock() instanceof AmethystClusterBlock
				|| state.is(MistinnBlocks.MIST_REED) || state.is(MistinnBlocks.CRYSTAL_SPROUT)) {
				if (!state.getFluidState().isEmpty()) {
					return at.getY();
				}
				continue;
			}
			return at.getY();
		}
		return at.getY();
	}

	/** Whether a level stretch of a terrace is flooded as a paddy: some of the 9-block cells on each step. */
	private static boolean paddy(final Domain domain, final int x, final int z, final int step) {
		return hash(Math.floorDiv(x, 9) * 7 + step, Math.floorDiv(z, 9) * 13 - step, domain.id() + 97) < 0.18;
	}

	/** What grows on the changed ground, by ring. */
	private static void decorate(final ServerLevel level, final LevelChunk chunk, final Domain domain, final Zone zone, final int x, final int ground, final int z,
		final double distance) {
		BlockPos surface = new BlockPos(x, ground, z);
		BlockPos above = surface.above();
		if (!chunk.getBlockState(above).isAir()) {
			return;
		}
		double roll = hash(x, z, domain.id());
		switch (zone) {
			case CROWN -> {
				int spire = spire(domain, x, z);
				if (spire > 0) {
					for (int i = 1; i <= spire; i++) {
						level.setBlock(surface.above(i), (i == spire && spire > 2 ? MistinnBlocks.TERRA_CRYSTAL_BLOCK : MistinnBlocks.LATTICE_CRYSTAL).defaultBlockState(), FLAGS);
					}
					if (hash(x + 5, z, domain.id() + 13) < 0.5) {
						level.setBlock(surface.above(spire + 1), bud(MistinnBlocks.TERRA_CRYSTAL_CLUSTER), FLAGS);
					}
				} else if (roll < 0.07) {
					level.setBlock(above, bud(budFor(hash(z, x, domain.id() + 5))), FLAGS);
				} else if (roll < 0.16) {
					level.setBlock(above, MistinnBlocks.JADE_GRASS.defaultBlockState(), FLAGS);
				}
			}
			case TERRACES -> {
				if (pine(level, chunk, domain, x, ground, z)) {
					return;
				}
				if (roll < 0.3) {
					level.setBlock(above, MistinnBlocks.JADE_GRASS.defaultBlockState(), FLAGS);
				} else if (roll < 0.37) {
					level.setBlock(above, MistinnBlocks.JADE_FERN.defaultBlockState(), FLAGS);
				} else if (roll < (nearWater(chunk, x, ground, z) ? 0.6 : 0.39) && chunk.getBlockState(above.above()).isAir()) {
					level.setBlock(above, MistinnBlocks.MIST_REED.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), FLAGS);
					level.setBlock(above.above(), MistinnBlocks.MIST_REED.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), FLAGS);
				} else if (roll > 0.994) {
					level.setBlock(above, MistinnBlocks.CRYSTAL_SPROUT.defaultBlockState()
						.setValue(life.shizune.mistinn.block.CrystalSproutBlock.AGE, (int) (hash(z, x, 7) * 4)), FLAGS);
				} else if (roll > 0.99) {
					level.setBlock(above, bud(budFor(hash(z, x, domain.id() + 5) * 0.7)), FLAGS);
				}
			}
			case VEINS -> {
				if (hash(x / PINE_CELL, z / PINE_CELL, domain.id() + 79) < 0.45 && pine(level, chunk, domain, x, ground, z)) {
					return;
				}
				if (roll < 0.004 && chunk.getBlockState(surface).is(MistinnBlocks.VEIN_STONE)) {
					// a lattice outcrop breaking up through the stone
					int height = 2 + (int) (hash(x - 3, z + 8, domain.id()) * 4);
					for (int i = 1; i <= height; i++) {
						level.setBlock(surface.above(i), MistinnBlocks.LATTICE_CRYSTAL.defaultBlockState(), FLAGS);
					}
					level.setBlock(surface.above(height + 1), bud(MistinnBlocks.TERRA_CRYSTAL_CLUSTER), FLAGS);
				} else if (roll < 0.045) {
					level.setBlock(above, bud(budFor(hash(z, x, domain.id() + 5))), FLAGS);
				} else if (roll < 0.11) {
					level.setBlock(above, MistinnBlocks.JADE_GRASS.defaultBlockState(), FLAGS);
				}
			}
		}
	}

	/**
	 * How tall a crown spire stands over this column (0: none): spires stand in some of a grid's
	 * cells, tallest at their middle and falling away over two or three blocks.
	 */
	static int spire(final Domain domain, final int x, final int z) {
		int cx = Math.floorDiv(x, SPIRE_CELL);
		int cz = Math.floorDiv(z, SPIRE_CELL);
		int best = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int gx = cx + dx;
				int gz = cz + dz;
				if (hash(gx * 13, gz * 29, domain.id() + 41) > 0.45) {
					continue;
				}
				double sx = gx * SPIRE_CELL + 2 + hash(gx, gz, domain.id() + 43) * (SPIRE_CELL - 4);
				double sz = gz * SPIRE_CELL + 2 + hash(gz, gx, domain.id() + 47) * (SPIRE_CELL - 4);
				int height = 6 + (int) (hash(gx + 1, gz - 1, domain.id() + 53) * 11);
				double spread = 1.6 + hash(gx - 2, gz + 2, domain.id() + 59) * 1.4;
				double off = Math.hypot(x + 0.5 - sx, z + 0.5 - sz);
				if (off <= spread) {
					best = Math.max(best, (int) Math.round(height * (1.0 - off / (spread + 0.6))));
				}
			}
		}
		return best;
	}

	/**
	 * If this column is where a new mist pine stands, grows it whole and says so. Pines stand in
	 * some of a grid's cells, never so near the chunk's edge that their crowns would reach out of it.
	 */
	private static boolean pine(final ServerLevel level, final LevelChunk chunk, final Domain domain, final int x, final int ground, final int z) {
		int cx = Math.floorDiv(x, PINE_CELL);
		int cz = Math.floorDiv(z, PINE_CELL);
		if (hash(cx * 17, cz * 23, domain.id() + 61) > 0.32) {
			return false;
		}
		int px = cx * PINE_CELL + 3 + (int) (hash(cx, cz, domain.id() + 67) * 2);
		int pz = cz * PINE_CELL + 3 + (int) (hash(cz, cx, domain.id() + 71) * 2);
		int lx = x & 15;
		int lz = z & 15;
		if (x != px || z != pz || lx < 3 || lx > 12 || lz < 3 || lz > 12 || !chunk.getBlockState(new BlockPos(x, ground, z)).is(MistinnBlocks.JADE_TURF)) {
			return false;
		}
		int height = 7 + (int) (hash(x, z, domain.id() + 73) * 5);
		BlockPos base = new BlockPos(x, ground + 1, z);
		for (int i = 0; i < height; i++) {
			if (!chunk.getBlockState(base.above(i)).isAir()) {
				return false;
			}
		}
		for (int i = 0; i < height; i++) {
			level.setBlock(base.above(i), MistinnBlocks.MIST_PINE_LOG.defaultBlockState(), FLAGS);
		}
		// a narrow cone of needles: ring by ring from the top down, two wide then one, as pines grow
		BlockState needles = MistinnBlocks.MIST_PINE_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
		level.setBlock(base.above(height), needles, FLAGS);
		for (int layer = 1; layer < height - 2; layer++) {
			int y = height - layer;
			int reach = layer < 2 ? 1 : (layer % 2 == 0 ? 2 : 1);
			for (int dx = -reach; dx <= reach; dx++) {
				for (int dz = -reach; dz <= reach; dz++) {
					if ((dx == 0 && dz == 0) || Math.abs(dx) + Math.abs(dz) > reach + 1 || (reach == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2)) {
						continue;
					}
					BlockPos leaf = base.offset(dx, y, dz);
					if (chunk.getBlockState(leaf).isAir()) {
						level.setBlock(leaf, needles, FLAGS);
					}
				}
			}
		}
		return true;
	}

	private static boolean nearWater(final LevelChunk chunk, final int x, final int ground, final int z) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			for (int step = 1; step <= 2; step++) {
				int nx = x + side.getStepX() * step;
				int nz = z + side.getStepZ() * step;
				if ((nx >> 4) == (x >> 4) && (nz >> 4) == (z >> 4) && chunk.getFluidState(new BlockPos(nx, ground, nz)).is(net.minecraft.tags.FluidTags.WATER)) {
					return true;
				}
			}
		}
		return false;
	}

	private static Block budFor(final double roll) {
		return roll < 0.35 ? MistinnBlocks.TERRA_CRYSTAL_SMALL_BUD : roll < 0.65 ? MistinnBlocks.TERRA_CRYSTAL_MEDIUM_BUD
			: roll < 0.88 ? MistinnBlocks.TERRA_CRYSTAL_LARGE_BUD : MistinnBlocks.TERRA_CRYSTAL_CLUSTER;
	}

	private static BlockState bud(final Block block) {
		return block.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP);
	}

	private static boolean growth(final BlockState state) {
		return state.is(BlockTags.FLOWERS) || state.is(BlockTags.SMALL_FLOWERS) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)
			|| state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN) || state.is(Blocks.DEAD_BUSH) || state.is(Blocks.BUSH)
			|| state.is(Blocks.SHORT_DRY_GRASS) || state.is(Blocks.TALL_DRY_GRASS) || state.is(Blocks.SWEET_BERRY_BUSH)
			|| state.is(Blocks.LEAF_LITTER) || state.is(Blocks.PINK_PETALS) || state.is(Blocks.WILDFLOWERS) || state.is(Blocks.FIREFLY_BUSH);
	}

	/** A steady number in [0, 1) for a column. */
	static double hash(final int x, final int z, final int salt) {
		long h = x * 3129871L ^ z * 116129781L ^ salt * 0x9E3779B97F4A7C15L;
		h = h * h * 42317861L + h * 11L;
		h ^= h >>> 29;
		return ((h >>> 16) & 0xFFFFL) / 65536.0;
	}
}
