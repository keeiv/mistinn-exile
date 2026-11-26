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
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
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
