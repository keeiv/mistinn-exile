package life.shizune.mistinn.terra;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.domain.DomainService;
import life.shizune.mistinn.registry.MistinnBlocks;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * 霧鏡湖: each Terra highland holds one great lake in its valleys, on the side away from its tillers' settlement. Its
 * water lies at one level, a little below the lowest of the ground round its shore, and it is deepest in the middle; what
 * ground stands higher round it slopes down to it as a bank.
 * <p>
 * The lake is dug into land as it is, old worlds' land too: a chunk over it is dug once, after the highland has turned its
 * ground, and remembers that it was. And it is dug strictly round anyone's work: never in a chunk a hunter has spent more
 * than a few minutes in, never near a block entity, and a column with anything in it that the land did not grow (any
 * block but earth, stone, ore, sand, the highland's own ground, plants, trees, water), or with a roof of ground over open
 * air above the water (a dug-out, a hut of earth), is left exactly as it is.
 */
public final class TerraLake {
	/** How wide the lake is (before its shore wavers), how deep in the middle, and how far out along the highland it lies. */
	public static final int RADIUS = 52;
	public static final int DEEPEST = 11;
	public static final double OUT = 0.55;
	/** How far out past the shore (as a part of its reach) the bank slopes, and how steeply (blocks up for each out). */
	public static final double BANK = 1.3;
	public static final double BANK_RISE = 0.8;
	/** The version of the lake's digging, raised if it is ever changed, so old land is dug again once. */
	public static final int VERSION = 1;
	/** A chunk a hunter has been in this long (ticks) is theirs: no lake is dug in it. */
	public static final long LIVED_IN = 20L * 60L * 5L;
	/** How far from a block entity no column is dug. */
	public static final int KEEP_CLEAR = 6;
	public static final ResourceKey<Biome> LAKE = ResourceKey.create(Registries.BIOME, Mistinn.id("terra_lake"));
	private static final int FLAGS = Block.UPDATE_CLIENTS;

	/** One highland's lake: where its middle is and at what level its water lies. */
	public record Lake(int domain, int x, int z, int level) {
		public static final Codec<Lake> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("domain").forGetter(Lake::domain),
			Codec.INT.fieldOf("x").forGetter(Lake::x),
			Codec.INT.fieldOf("z").forGetter(Lake::z),
			Codec.INT.fieldOf("level").forGetter(Lake::level)
		).apply(i, Lake::new));
	}

	/** The world's lakes, once placed. */
	public static final AttachmentType<List<Lake>> LAKES = AttachmentRegistry.<List<Lake>>builder()
		.persistent(Lake.CODEC.listOf())
		.buildAndRegister(Mistinn.id("terra_lakes"));
	/** Which version of the digging a chunk has had (0: none). */
	public static final AttachmentType<Integer> DUG = AttachmentRegistry.<Integer>builder()
		.persistent(Codec.INT)
		.buildAndRegister(Mistinn.id("terra_lake_dug"));

	/** What the land grows: a column of only these may be dug. */
	private static final Set<Block> GROWN = Set.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL, Blocks.MYCELIUM,
		Blocks.MOSS_BLOCK, Blocks.MUD, Blocks.CLAY, Blocks.GRAVEL, Blocks.SNOW, Blocks.SNOW_BLOCK, Blocks.POWDER_SNOW, Blocks.ICE, Blocks.WATER,
		Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.TUFF, Blocks.CALCITE, Blocks.DEEPSLATE, Blocks.SANDSTONE,
		Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN, Blocks.VINE, Blocks.SUGAR_CANE, Blocks.SWEET_BERRY_BUSH, Blocks.DEAD_BUSH,
		MistinnBlocks.JADE_TURF, MistinnBlocks.VEIN_SOIL, MistinnBlocks.VEIN_STONE, MistinnBlocks.VEIN_CRYSTAL_ORE, MistinnBlocks.JADE_GRASS,
		MistinnBlocks.JADE_FERN, MistinnBlocks.MIST_REED, MistinnBlocks.CRYSTAL_SPROUT, MistinnBlocks.MIST_PINE_LOG, MistinnBlocks.MIST_PINE_LEAVES,
		MistinnBlocks.LATTICE_CRYSTAL, MistinnBlocks.TERRA_CRYSTAL_CLUSTER, MistinnBlocks.TERRA_CRYSTAL_LARGE_BUD, MistinnBlocks.TERRA_CRYSTAL_MEDIUM_BUD,
		MistinnBlocks.TERRA_CRYSTAL_SMALL_BUD, MistinnBlocks.TERRA_CRYSTAL_BLOCK, Blocks.SEAGRASS, Blocks.TALL_SEAGRASS, Blocks.KELP, Blocks.KELP_PLANT,
		Blocks.LILY_PAD, Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM, Blocks.BAMBOO, Blocks.CACTUS, Blocks.SMOOTH_BASALT, Blocks.MAGMA_BLOCK,
		// what the highland's own turning of the ground set there (an older world's land was turned before the lake came)
		MistinnBlocks.VEIN_STONE_BRICKS, MistinnBlocks.VEIN_STONE_POLISHED);
	/** What the land grows that is not ground: plants and crystal, which make no roof over the air under them. */
	private static final Set<Block> GROWTH = Set.of(Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN, Blocks.VINE, Blocks.SUGAR_CANE,
		Blocks.SWEET_BERRY_BUSH, Blocks.DEAD_BUSH, Blocks.SNOW, Blocks.LILY_PAD, Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM, Blocks.BAMBOO, Blocks.CACTUS,
		MistinnBlocks.JADE_GRASS, MistinnBlocks.JADE_FERN, MistinnBlocks.MIST_REED, MistinnBlocks.CRYSTAL_SPROUT, MistinnBlocks.LATTICE_CRYSTAL,
		MistinnBlocks.TERRA_CRYSTAL_CLUSTER, MistinnBlocks.TERRA_CRYSTAL_LARGE_BUD, MistinnBlocks.TERRA_CRYSTAL_MEDIUM_BUD,
		MistinnBlocks.TERRA_CRYSTAL_SMALL_BUD, MistinnBlocks.TERRA_CRYSTAL_BLOCK);

	private static final LongLinkedOpenHashSet WAITING = new LongLinkedOpenHashSet();

	private TerraLake() {
	}

	public static void register() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (level.dimension() == Level.OVERWORLD && chunk.getAttachedOrElse(DUG, 0) < VERSION) {
				WAITING.add(chunk.getPos().pack());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(TerraLake::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WAITING.clear());
	}

	// ── Where it lies ─────────────────────────────────────────────

	/** Where a highland's lake has its middle: out along the highland on the side away from its settlement. */
	public static BlockPos middle(final Domain domain) {
		// the settlement's site is known from the first (before the settlement is built), so the lake's is too
		BlockPos home = Settlements.site(domain);
		double angle = Math.atan2(home.getZ() - domain.centre().getZ(), home.getX() - domain.centre().getX()) + Math.PI;
		double out = domain.radius() * OUT;
		return new BlockPos(domain.centre().getX() + (int) Math.round(Math.cos(angle) * out), 0, domain.centre().getZ() + (int) Math.round(Math.sin(angle) * out));
	}

	/** How far its shore reaches out from the middle toward this column: the lake's radius, wavering. */
	private static double shore(final Domain domain, final double angle) {
		return RADIUS * (1.0 + 0.12 * Math.sin(3.0 * angle + domain.id()) + 0.07 * Math.sin(5.0 * angle + domain.id() * 1.7));
	}

	/** How far in a column is, as a part of the way from the shore (1) to the middle (0); above 1, outside. */
	public static double depthPart(final Domain domain, final int lx, final int lz, final int x, final int z) {
		double dx = x + 0.5 - lx;
		double dz = z + 0.5 - lz;
		return Math.hypot(dx, dz) / shore(domain, Math.atan2(dz, dx));
	}

	/** The lake of a highland, placed (and its level found) the first time it is asked for. */
	public static Lake lake(final ServerLevel level, final Domain domain) {
		List<Lake> lakes = level.getAttachedOrElse(LAKES, List.of());
		for (Lake lake : lakes) {
			if (lake.domain() == domain.id()) {
				return lake;
			}
		}
		BlockPos middle = middle(domain);
		// a little below the lowest ground round its shore, so it does not spill
		int lowest = Integer.MAX_VALUE;
		for (int i = 0; i < 16; i++) {
			double angle = i * Math.PI / 8.0;
			double r = shore(domain, angle) + 2.0;
			int x = middle.getX() + (int) Math.round(Math.cos(angle) * r);
			int z = middle.getZ() + (int) Math.round(Math.sin(angle) * r);
			lowest = Math.min(lowest, level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15));
		}
		Lake lake = new Lake(domain.id(), middle.getX(), middle.getZ(), Math.max(level.getSeaLevel(), lowest - 1));
		List<Lake> next = new ArrayList<>(lakes);
		next.add(lake);
		level.setAttached(LAKES, List.copyOf(next));
		return lake;
	}

	/** Whether a column lies in a highland's lake (whose ground the lake digs, not the highland's turning). */
	public static boolean inLake(final Domain domain, final int x, final int z) {
		BlockPos middle = middle(domain);
		return depthPart(domain, middle.getX(), middle.getZ(), x, z) < 1.0;
	}

	// ── Digging it ────────────────────────────────────────────────

	private static void tick(final MinecraftServer server) {
		if (WAITING.isEmpty()) {
			return;
		}
		ServerLevel level = server.overworld();
		int looked = 0;
		int dug = 0;
		while (!WAITING.isEmpty() && dug < 1 && looked < 64) {
			long packed = WAITING.removeFirstLong();
			looked++;
			ChunkPos pos = ChunkPos.unpack(packed);
			LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
			if (chunk == null) {
				continue;
			}
			Domain domain = DomainService.at(level, pos.getMiddleBlockX(), pos.getMiddleBlockZ());
			if (domain == null || domain.kind() != Domain.Kind.TERRA) {
				continue;
			}
			BlockPos middle = middle(domain);
			if (!near(middle, pos)) {
				chunk.setAttached(DUG, VERSION);
				continue;
			}
			if (!DomainService.worked(server, domain.id(), pos)) {
				WAITING.addAndMoveToLast(packed);
				continue;
			}
			dig(level, chunk, domain);
			chunk.setAttached(DUG, VERSION);
			chunk.markUnsaved();
			dug++;
		}
	}

	/** Whether any of a chunk is within the lake's farthest bank. */
	private static boolean near(final BlockPos middle, final ChunkPos pos) {
		double cx = Math.max(pos.getMinBlockX(), Math.min(middle.getX(), pos.getMaxBlockX()));
		double cz = Math.max(pos.getMinBlockZ(), Math.min(middle.getZ(), pos.getMaxBlockZ()));
		return Math.hypot(cx - middle.getX(), cz - middle.getZ()) <= RADIUS * 1.2 * BANK + 2.0;
	}

	/** Digs the lake's part of one chunk, strictly round anyone's work. Returns how many columns were dug. */
	public static int dig(final ServerLevel level, final LevelChunk chunk, final Domain domain) {
		if (chunk.getInhabitedTime() > LIVED_IN) {
			return 0;
		}
		Lake lake = lake(level, domain);
		ChunkPos pos = chunk.getPos();
		// anyone's things here and in the chunks round it (a chest just over the line keeps its ground too)
		List<BlockPos> things = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				LevelChunk near = dx == 0 && dz == 0 ? chunk : level.getChunkSource().getChunkNow(pos.x() + dx, pos.z() + dz);
				if (near != null) {
					things.addAll(near.getBlockEntitiesPos());
				}
			}
		}
		int dug = 0;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
			for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
				double part = depthPart(domain, lake.x(), lake.z(), x, z);
				if (part >= BANK || nearThings(things, x, z)) {
					continue;
				}
				int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
				if (part >= 1.0) {
					dug += bank(level, chunk, domain, lake, at, x, z, part, top) ? 1 : 0;
					continue;
				}
				int floor = lake.level() - Math.max(1, (int) Math.round(DEEPEST * (1.0 - part * part)));
				if (!grown(chunk, at, x, z, floor, Math.max(top, lake.level()), lake.level())) {
					continue;
				}
				for (int y = floor + 1; y <= Math.max(top, lake.level()); y++) {
					level.setBlock(at.set(x, y, z), y <= lake.level() ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), FLAGS);
				}
				BlockState bed = part > 0.85 ? Blocks.SAND.defaultBlockState() : (x * 31 + z * 17) % 5 == 0 ? Blocks.GRAVEL.defaultBlockState()
					: Blocks.CLAY.defaultBlockState();
				level.setBlock(at.set(x, floor, z), bed, FLAGS);
				dug++;
			}
		}
		if (dug > 0) {
			lakeBiome(level, chunk, domain, lake);
		}
		return dug;
	}

	/**
	 * The bank: ground standing higher than the slope up from the water is cut down to it, and the cut faced with turf
	 * (sand by the water). Ground already under the slope is left as it is. True if the column was cut.
	 */
	private static boolean bank(final ServerLevel level, final LevelChunk chunk, final Domain domain, final Lake lake, final BlockPos.MutableBlockPos at,
		final int x, final int z, final double part, final int top) {
		double past = (part - 1.0) * shore(domain, Math.atan2(z + 0.5 - lake.z(), x + 0.5 - lake.x()));
		int slope = lake.level() + 1 + (int) Math.floor(past * BANK_RISE);
		if (top <= slope || !grown(chunk, at, x, z, slope, top, slope)) {
			return false;
		}
		for (int y = slope + 1; y <= top; y++) {
			level.setBlock(at.set(x, y, z), Blocks.AIR.defaultBlockState(), FLAGS);
		}
		BlockState face = chunk.getBlockState(at.set(x, slope, z));
		if (!face.isAir() && !GROWTH.contains(face.getBlock()) && face.getFluidState().isEmpty()) {
			level.setBlock(at, (slope <= lake.level() + 2 ? Blocks.SAND : MistinnBlocks.JADE_TURF).defaultBlockState(), FLAGS);
		}
		return true;
	}

	/**
	 * Whether every block of a column, from {@code floor} up, is one the land grew (leaves only as they grow, not as set
	 * down), and no ground roofs open air above {@code open} (a cave under the water's level may be flooded; a dug-out
	 * above it is someone's).
	 */
	private static boolean grown(final LevelChunk chunk, final BlockPos.MutableBlockPos at, final int x, final int z, final int floor, final int top,
		final int open) {
		boolean roofed = false;
		for (int y = top; y >= floor; y--) {
			BlockState state = chunk.getBlockState(at.set(x, y, z));
			if (state.isAir()) {
				if (roofed && y > open) {
					return false;
				}
				continue;
			}
			if (state.getBlock() instanceof LeavesBlock) {
				if (state.getValue(LeavesBlock.PERSISTENT)) {
					return false;
				}
				continue;
			}
			if (!natural(state)) {
				return false;
			}
			if (!GROWTH.contains(state.getBlock()) && !state.is(BlockTags.LOGS) && !state.is(BlockTags.FLOWERS) && state.getFluidState().isEmpty()) {
				roofed = true;
			}
		}
		return true;
	}

	/** Whether a block is one the land grows (or the highland's turning set there). */
	private static boolean natural(final BlockState state) {
		return GROWN.contains(state.getBlock()) || state.is(BlockTags.LOGS) || state.is(BlockTags.SAND) || state.is(BlockTags.FLOWERS)
			|| state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockItemTags.COAL_ORES.block())
			|| state.is(BlockItemTags.COPPER_ORES.block()) || state.is(BlockItemTags.IRON_ORES.block()) || state.is(BlockItemTags.GOLD_ORES.block())
			|| state.is(BlockItemTags.LAPIS_ORES.block()) || state.is(BlockItemTags.REDSTONE_ORES.block()) || state.is(BlockItemTags.EMERALD_ORES.block())
			|| state.is(BlockItemTags.DIAMOND_ORES.block());
	}

	private static boolean nearThings(final List<BlockPos> things, final int x, final int z) {
		for (BlockPos thing : things) {
			if (Math.abs(thing.getX() - x) <= KEEP_CLEAR && Math.abs(thing.getZ() - z) <= KEEP_CLEAR) {
				return true;
			}
		}
		return false;
	}

	/** The lake's cells of the chunk take the lake's biome. */
	private static void lakeBiome(final ServerLevel level, final LevelChunk chunk, final Domain domain, final Lake lake) {
		Holder<Biome> water = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(LAKE);
		boolean[] changed = new boolean[1];
		chunk.fillBiomesFromNoise((qx, qy, qz) -> {
			Holder<Biome> existing = chunk.getNoiseBiome(qx, qy, qz);
			int x = (qx << 2) + 2;
			int z = (qz << 2) + 2;
			if (depthPart(domain, lake.x(), lake.z(), x, z) >= 0.95 || existing.equals(water)) {
				return existing;
			}
			changed[0] = true;
			return water;
		});
		if (changed[0]) {
			chunk.markUnsaved();
			level.getChunkSource().chunkMap.resendBiomesForChunks(List.<ChunkAccess>of(chunk));
		}
	}

	/** For tests: how many chunks wait to be dug. */
	public static int waiting() {
		return WAITING.size();
	}

	/** For tests: a lake's level, if placed. */
	public static @Nullable Lake placed(final ServerLevel level, final int domain) {
		for (Lake lake : level.getAttachedOrElse(LAKES, List.of())) {
			if (lake.domain() == domain) {
				return lake;
			}
		}
		return null;
	}
}
