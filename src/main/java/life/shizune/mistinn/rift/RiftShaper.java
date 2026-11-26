package life.shizune.mistinn.rift;

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
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

/**
 * How a sky rift sears the ground under it, column by column, touching only what grew there:
 * grass and earth turn to crimson scab, the stone near the surface blackens, the leaves of the
 * trees fall away and leave the trunks standing, the flowers and grass give way to thorn and
 * moss, and the rift's crystal pushes up, thicker towards the middle. Shallow pools of what
 * becomes blood water open in the inner ground.
 */
public final class RiftShaper implements DomainService.Shaper {
	public static final ResourceKey<Biome> OPEN = ResourceKey.create(Registries.BIOME, Mistinn.id("rift_open"));
	public static final ResourceKey<Biome> SCAR = ResourceKey.create(Registries.BIOME, Mistinn.id("rift_scar"));

	private static final Set<Block> TURF = Set.of(Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.DIRT_PATH, Blocks.MOSS_BLOCK);
	private static final Set<Block> EARTH = Set.of(Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT);
	private static final Set<Block> STONE = Set.of(Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.TUFF, Blocks.CALCITE);
	/** How deep under the surface the earth and stone are changed. */
	private static final int DEPTH = 3;
	/** How far down through a tree the shaper looks for the ground. */
	private static final int TREE = 40;
	private static final int FLAGS = Block.UPDATE_CLIENTS;
	/** How likely a chunk of a rift's ground is to hold a hunter's camp it took, and for tests how many times more (0 for none). */
	public static final double CAMP = 1.0 / 25.0;
	public static double camps = 1.0;
	public static final ResourceKey<LootTable> CAMP_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Mistinn.id("chests/rift_camp"));

	@Override
	public @Nullable Holder<Biome> biome(final ServerLevel level, final Domain domain, final double distance) {
		ResourceKey<Biome> key = domain.phase() == Domain.Phase.SCAR ? SCAR : OPEN;
		return level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(key);
	}

	@Override
	public void column(final ServerLevel level, final LevelChunk chunk, final Domain domain, final int x, final int z, final double distance) {
		int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int ground = Integer.MIN_VALUE;
		boolean seared = false;
		for (int y = top; y > top - TREE && y > level.getMinY(); y--) {
			BlockState state = chunk.getBlockState(at.set(x, y, z));
			if (state.isAir()) {
				continue;
			}
			if (state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)) {
				// the leaves wither away; the trunk is left standing
				level.setBlock(at, Blocks.AIR.defaultBlockState(), FLAGS);
				continue;
			}
			if (state.is(BlockTags.LOGS) || state.is(Blocks.VINE) || state.is(Blocks.SNOW)) {
				if (!state.is(BlockTags.LOGS)) {
					level.setBlock(at, Blocks.AIR.defaultBlockState(), FLAGS);
				}
				continue;
			}
			if (growth(state)) {
				level.setBlock(at, Blocks.AIR.defaultBlockState(), FLAGS);
				continue;
			}
			if (!state.getFluidState().isEmpty()) {
				// water stays water: the biome turns it to blood
				return;
			}
			ground = y;
			break;
		}
		if (ground == Integer.MIN_VALUE) {
			return;
		}
		for (int depth = 0; depth <= DEPTH; depth++) {
			at.set(x, ground - depth, z);
			BlockState state = chunk.getBlockState(at);
			BlockState into = null;
			if (TURF.contains(state.getBlock())) {
				into = depth == 0 ? MistinnBlocks.RIFT_TURF.defaultBlockState() : MistinnBlocks.RIFT_SOIL.defaultBlockState();
			} else if (EARTH.contains(state.getBlock())) {
				into = depth == 0 ? MistinnBlocks.RIFT_TURF.defaultBlockState() : MistinnBlocks.RIFT_SOIL.defaultBlockState();
			} else if (STONE.contains(state.getBlock())) {
				into = MistinnBlocks.SCORCHED_STONE.defaultBlockState();
			} else if (state.is(Blocks.SAND)) {
				into = Blocks.RED_SAND.defaultBlockState();
			} else if (state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
				into = MistinnBlocks.RIFT_SOIL.defaultBlockState();
			}
			if (into != null) {
				level.setBlock(at, into, FLAGS);
				seared |= depth == 0;
			}
		}
		if (seared) {
			decorate(level, chunk, domain, x, ground, z, distance);
		}
	}

	/** What grows on the seared ground: crystal, thorn and moss, and in the inner ground pools. */
	private static void decorate(final ServerLevel level, final LevelChunk chunk, final Domain domain, final int x, final int ground, final int z, final double distance) {
		BlockPos surface = new BlockPos(x, ground, z);
		BlockPos above = surface.above();
		if (!chunk.getBlockState(above).isAir()) {
			return;
		}
		double inner = Math.max(0.0, 1.0 - distance / domain.radius());
		if (camp(level, chunk, domain, x, ground, z)) {
			return;
		}
		if (pool(domain, x, z, distance) && chunk.getBlockState(surface).is(MistinnBlocks.RIFT_TURF) && flat(chunk, x, ground, z)) {
			level.setBlock(surface, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
			return;
		}
		double roll = hash(x, z, domain.id());
		double crystal = 0.006 + 0.035 * inner * inner;
		if (roll < crystal) {
			if (inner > 0.6 && hash(z, x, domain.id() + 7) < 0.18) {
				// a spike of the crystal itself, crowned with a cluster
				int height = 1 + (int) (hash(x + 3, z - 5, domain.id()) * 3);
				for (int i = 1; i <= height; i++) {
					level.setBlock(surface.above(i), MistinnBlocks.RIFT_CRYSTAL_BLOCK.defaultBlockState(), FLAGS);
				}
				level.setBlock(surface.above(height + 1), cluster(MistinnBlocks.RIFT_CRYSTAL_CLUSTER), FLAGS);
			} else {
				level.setBlock(above, cluster(hash(x - 9, z + 4, domain.id()) < 0.35 ? MistinnBlocks.RIFT_CRYSTAL_BUD : MistinnBlocks.RIFT_CRYSTAL_CLUSTER), FLAGS);
			}
		} else if (roll < crystal + 0.045) {
			level.setBlock(above, MistinnBlocks.BLOOD_THORN.defaultBlockState(), FLAGS);
		} else if (roll < crystal + 0.045 + 0.25 * (0.4 + inner)) {
			level.setBlock(above, MistinnBlocks.CRIMSON_MOSS_CARPET.defaultBlockState(), FLAGS);
		}
	}

	/**
	 * A hunter's camp the rift took, in some chunks of its ground: a tent's charred frame, a cold
	 * fire, and their pack left where they fell. All of it inside the one chunk, stood on its own
	 * column at the chunk's heart. Returns whether this column was that column (and so is done).
	 */
	private static boolean camp(final ServerLevel level, final LevelChunk chunk, final Domain domain, final int x, final int ground, final int z) {
		int cx = x >> 4;
		int cz = z >> 4;
		if (hash(cx, cz, domain.id() + 501) >= CAMP * camps) {
			return false;
		}
		int ax = (cx << 4) + 5 + (int) (hash(cz, cx, domain.id() + 503) * 6);
		int az = (cz << 4) + 5 + (int) (hash(cx + 3, cz, domain.id() + 505) * 6);
		if (x != ax || z != az || !chunk.getFluidState(new BlockPos(x, ground, z)).isEmpty()) {
			return false;
		}
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		// clear the ground for it, and level it
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int y = 1; y <= 4; y++) {
					at.set(x + dx, ground + y, z + dz);
					if (!chunk.getBlockState(at).isAir() && !chunk.getBlockState(at).is(BlockTags.LOGS)) {
						level.setBlock(at, Blocks.AIR.defaultBlockState(), FLAGS);
					}
				}
				at.set(x + dx, ground, z + dz);
				if (chunk.getBlockState(at).isAir() || !chunk.getFluidState(at).isEmpty()) {
					level.setBlock(at, MistinnBlocks.RIFT_SOIL.defaultBlockState(), FLAGS);
				}
			}
		}
		// the tent's charred frame: two posts and the ridge between, one end fallen
		BlockState post = MistinnBlocks.CHARRED_LOG.defaultBlockState();
		BlockState ridge = MistinnBlocks.CHARRED_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
		for (int y = 1; y <= 2; y++) {
			level.setBlock(at.set(x - 2, ground + y, z - 1), post, FLAGS);
		}
		level.setBlock(at.set(x + 2, ground + 1, z - 1), post, FLAGS);
		level.setBlock(at.set(x - 1, ground + 3, z - 1), ridge, FLAGS);
		level.setBlock(at.set(x, ground + 3, z - 1), ridge, FLAGS);
		level.setBlock(at.set(x + 1, ground + 1, z - 2), ridge, FLAGS);
		// the cold fire, and the pack
		level.setBlock(at.set(x, ground + 1, z + 1), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), FLAGS);
		BlockPos pack = new BlockPos(x + 1, ground + 1, z);
		Direction facing = Direction.from2DDataValue((int) (hash(z, x, domain.id() + 507) * 4));
		level.setBlock(pack, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, facing), FLAGS);
		RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), pack, CAMP_LOOT);
		// the rift's growth over it all
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				at.set(x + dx, ground + 1, z + dz);
				if (chunk.getBlockState(at).isAir() && hash(x + dx, z + dz, domain.id() + 509) < 0.4) {
					level.setBlock(at, MistinnBlocks.CRIMSON_MOSS_CARPET.defaultBlockState(), FLAGS);
				}
			}
		}
		return true;
	}

	private static BlockState cluster(final Block block) {
		return block.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP);
	}

	/** Whether a column is in one of the inner ground's pools: a pool in some of the 11-block cells, two or three blocks across. */
	static boolean pool(final Domain domain, final int x, final int z, final double distance) {
		if (distance > domain.radius() * 0.6) {
			return false;
		}
		int cx = Math.floorDiv(x, 11);
		int cz = Math.floorDiv(z, 11);
		if (hash(cx * 31, cz * 17, domain.id() + 101) > 0.35) {
			return false;
		}
		double px = cx * 11 + 3 + hash(cx, cz, domain.id() + 5) * 5;
		double pz = cz * 11 + 3 + hash(cz, cx, domain.id() + 9) * 5;
		double size = 1.2 + hash(cx + 1, cz + 1, domain.id()) * 1.4;
		return Math.hypot(x + 0.5 - px, z + 0.5 - pz) <= size;
	}

	/** Whether the ground round a column stands level with it, so a pool there holds its water. */
	private static boolean flat(final LevelChunk chunk, final int x, final int ground, final int z) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			int nx = x + side.getStepX();
			int nz = z + side.getStepZ();
			if ((nx >> 4) != (x >> 4) || (nz >> 4) != (z >> 4)) {
				return false;
			}
			BlockState beside = chunk.getBlockState(new BlockPos(nx, ground, nz));
			if (!beside.isSolid() && beside.getFluidState().isEmpty()) {
				return false;
			}
		}
		return true;
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
