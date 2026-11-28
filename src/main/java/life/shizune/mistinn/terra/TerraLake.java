package life.shizune.mistinn.terra;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
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
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.level.block.RotatedPillarBlock;
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
 * ground, and remembers that it was. And it is dug strictly round anyone's work. Nothing is dug in a chunk a hunter has
 * lived in (half an hour near it), nor near a block entity; and a column with anything in it the land did not grow (any
 * block but earth, stone, ore, sand, the highland's own ground, plants and trees as they grow: no stripped wood, no log
 * standing bare with nothing growing on it) keeps the ground for some blocks round it as it is too: a dug-out or an
 * earthen hut keeps its ground by the torch, bench, bed or door in it. Last, the water is sealed in: wherever it would stand against
 * open air (a cave, a low gap in the shore, anything left as it was), that column of it is filled with sand instead, so it
 * never spills when the ground round it is next disturbed.
 */
public final class TerraLake {
	/**
	 * How wide the lake is (before its shore wavers) as a part of its highland's reach, the least reach a highland holds a
	 * lake at all, how deep it is in the middle, and how far out along the highland it lies.
	 */
	public static final double WIDTH = 0.2;
	public static final int HOLDS_ONE = 150;
	public static final int DEEPEST = 11;
	public static final double OUT = 0.55;
	/** How far out past the shore (as a part of its reach) the bank slopes, and how steeply (blocks up for each out). */
	public static final double BANK = 1.3;
	public static final double BANK_RISE = 0.8;
	/** The version of the lake's digging, raised if it is ever changed, so old land is dug again once. */
	public static final int VERSION = 1;
	/** A chunk a hunter has been near this long (ticks; it counts while one is within 128 blocks) is theirs: no lake there. */
	public static final long LIVED_IN = 20L * 60L * 30L;
	/** How far from a block entity, and from a column with anyone's work in it, no column is dug. */
	public static final int KEEP_CLEAR = 6;
	public static final int MARGIN = 4;
	public static final ResourceKey<Biome> LAKE = ResourceKey.create(Registries.BIOME, Mistinn.id("terra_lake"));
	private static final int FLAGS = Block.UPDATE_CLIENTS;
	/** For tests: as an older world was, before the lakes came (no lake is dug, and the highland turns its ground). */
	public static boolean dormant;

	/** One highland's lake: where its middle is, at what level its water lies, and how far its shore reaches. */
	public record Lake(int domain, int x, int z, int level, int reach) {
		public static final Codec<Lake> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("domain").forGetter(Lake::domain),
			Codec.INT.fieldOf("x").forGetter(Lake::x),
			Codec.INT.fieldOf("z").forGetter(Lake::z),
			Codec.INT.fieldOf("level").forGetter(Lake::level),
			Codec.INT.optionalFieldOf("reach", (int) Math.round(TerraService.RADIUS * WIDTH)).forGetter(Lake::reach)
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

	/** What the land grows (besides logs and leaves, looked at on their own). */
	private static final Set<Block> GROWN = Set.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL, Blocks.MYCELIUM,
		Blocks.MOSS_BLOCK, Blocks.MUD, Blocks.CLAY, Blocks.GRAVEL, Blocks.SNOW, Blocks.SNOW_BLOCK, Blocks.POWDER_SNOW, Blocks.ICE, Blocks.WATER,
		Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.TUFF, Blocks.CALCITE, Blocks.DEEPSLATE, Blocks.SANDSTONE,
		Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN, Blocks.VINE, Blocks.SUGAR_CANE, Blocks.SWEET_BERRY_BUSH, Blocks.DEAD_BUSH,
		MistinnBlocks.JADE_TURF, MistinnBlocks.VEIN_SOIL, MistinnBlocks.VEIN_STONE, MistinnBlocks.VEIN_CRYSTAL_ORE, MistinnBlocks.JADE_GRASS,
		MistinnBlocks.JADE_FERN, MistinnBlocks.MIST_REED, MistinnBlocks.CRYSTAL_SPROUT,
		MistinnBlocks.LATTICE_CRYSTAL, MistinnBlocks.TERRA_CRYSTAL_CLUSTER, MistinnBlocks.TERRA_CRYSTAL_LARGE_BUD, MistinnBlocks.TERRA_CRYSTAL_MEDIUM_BUD,
		MistinnBlocks.TERRA_CRYSTAL_SMALL_BUD, MistinnBlocks.TERRA_CRYSTAL_BLOCK, Blocks.SEAGRASS, Blocks.TALL_SEAGRASS, Blocks.KELP, Blocks.KELP_PLANT,
		Blocks.LILY_PAD, Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM, Blocks.BAMBOO, Blocks.CACTUS, Blocks.SMOOTH_BASALT, Blocks.MAGMA_BLOCK, Blocks.GLOW_LICHEN,
		// what the highland's own turning of the ground set there (an older world's land was turned before the lake came)
		MistinnBlocks.VEIN_STONE_BRICKS, MistinnBlocks.VEIN_STONE_POLISHED);
	/** What the land grows that is not ground: plants and crystal, which make no roof over the air under them. */
	private static final Set<Block> GROWTH = Set.of(Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN, Blocks.VINE, Blocks.SUGAR_CANE,
		Blocks.SWEET_BERRY_BUSH, Blocks.DEAD_BUSH, Blocks.SNOW, Blocks.LILY_PAD, Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM, Blocks.BAMBOO, Blocks.CACTUS,
		Blocks.GLOW_LICHEN, MistinnBlocks.JADE_GRASS, MistinnBlocks.JADE_FERN, MistinnBlocks.MIST_REED, MistinnBlocks.CRYSTAL_SPROUT, MistinnBlocks.LATTICE_CRYSTAL,
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

	/** How wide a highland's lake is, before its shore wavers. */
	public static int reach(final Domain domain) {
		return (int) Math.round(domain.radius() * WIDTH);
	}

	/** Whether a highland holds a lake: a full one does (a small one, laid down for a trial, does not). */
	public static boolean holds(final Domain domain) {
		return domain.radius() >= HOLDS_ONE;
	}

	/** How far its shore reaches out from the middle toward this column: the lake's reach, wavering. */
	private static double shore(final Lake lake, final double angle) {
		return lake.reach() * (1.0 + 0.12 * Math.sin(3.0 * angle + lake.domain()) + 0.07 * Math.sin(5.0 * angle + lake.domain() * 1.7));
	}

	/** How far in a column is, as a part of the way from the shore (1) to the middle (0); above 1, outside. */
	public static double depthPart(final Lake lake, final int x, final int z) {
		double dx = x + 0.5 - lake.x();
		double dz = z + 0.5 - lake.z();
		return Math.hypot(dx, dz) / shore(lake, Math.atan2(dz, dx));
	}

	/** A highland's lake: as placed, or (not placed yet) where it will lie, its level not yet found. */
	public static Lake of(final ServerLevel level, final Domain domain) {
		Lake placed = placed(level, domain.id());
		if (placed != null) {
			return placed;
		}
		BlockPos middle = middle(domain);
		return new Lake(domain.id(), middle.getX(), middle.getZ(), Integer.MIN_VALUE, reach(domain));
	}

	/** The lake of a highland, placed (and its level found) the first time it is asked for. */
	public static Lake lake(final ServerLevel level, final Domain domain) {
		Lake placed = placed(level, domain.id());
		if (placed != null) {
			return placed;
		}
		Lake where = of(level, domain);
		// a little below the lowest ground round its shore, so it does not spill
		int lowest = Integer.MAX_VALUE;
		for (int i = 0; i < 16; i++) {
			double angle = i * Math.PI / 8.0;
			double r = shore(where, angle) + 2.0;
			int x = where.x() + (int) Math.round(Math.cos(angle) * r);
			int z = where.z() + (int) Math.round(Math.sin(angle) * r);
			lowest = Math.min(lowest, level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15));
		}
		Lake lake = new Lake(domain.id(), where.x(), where.z(), Math.max(level.getSeaLevel(), lowest - 1), where.reach());
		List<Lake> next = new ArrayList<>(level.getAttachedOrElse(LAKES, List.of()));
		next.add(lake);
		level.setAttached(LAKES, List.copyOf(next));
		return lake;
	}

	/** Whether a column lies in a highland's lake (whose ground the lake digs, not the highland's turning). */
	public static boolean inLake(final ServerLevel level, final Domain domain, final int x, final int z) {
		return !dormant && holds(domain) && depthPart(of(level, domain), x, z) < 1.0;
	}

	// ── Digging it ────────────────────────────────────────────────

	private static void tick(final MinecraftServer server) {
		if (WAITING.isEmpty() || dormant) {
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
			if (chunk == null || chunk.getAttachedOrElse(DUG, 0) >= VERSION) {
				continue;
			}
			Domain domain = DomainService.at(level, pos.getMiddleBlockX(), pos.getMiddleBlockZ());
			if (domain == null || domain.kind() != Domain.Kind.TERRA) {
				continue;
			}
			if (!holds(domain) || !near(of(level, domain), pos)) {
				chunk.setAttached(DUG, VERSION);
				continue;
			}
			if (!DomainService.worked(server, domain.id(), pos)) {
				WAITING.addAndMoveToLast(packed);
				continue;
			}
			dig(level, chunk, domain);
			dug++;
			// once the lake's middle is all dug, a research ship may come to lie on it
			ResearchShip.consider(level, domain);
		}
	}

	/** Whether any of a chunk is within the lake's farthest bank. */
	private static boolean near(final Lake lake, final ChunkPos pos) {
		double cx = Math.max(pos.getMinBlockX(), Math.min(lake.x(), pos.getMaxBlockX()));
		double cz = Math.max(pos.getMinBlockZ(), Math.min(lake.z(), pos.getMaxBlockZ()));
		return Math.hypot(cx - lake.x(), cz - lake.z()) <= lake.reach() * 1.2 * BANK + 2.0;
	}

	/**
	 * Digs the lake's part of one chunk, strictly round anyone's work, and seals its water in. Returns how many columns
	 * were dug (or cut to the bank).
	 */
	public static int dig(final ServerLevel level, final LevelChunk chunk, final Domain domain) {
		if (!holds(domain)) {
			return 0;
		}
		Lake lake = lake(level, domain);
		ChunkPos pos = chunk.getPos();
		int dug = 0;
		if (chunk.getInhabitedTime() <= LIVED_IN) {
			List<BlockPos> things = things(level, chunk, lake);
			LongSet marks = marks(level, lake, pos);
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
				for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
					double part = depthPart(lake, x, z);
					if (part >= BANK || near(things, x, z, KEEP_CLEAR) || near(marks, x, z)) {
						continue;
					}
					int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
					if (part >= 1.0) {
						dug += bank(level, chunk, lake, at, x, z, part, top) ? 1 : 0;
						continue;
					}
					int floor = lake.level() - Math.max(1, (int) Math.round(DEEPEST * (1.0 - part * part)));
					for (int y = floor + 1; y <= Math.max(top, lake.level()); y++) {
						level.setBlock(at.set(x, y, z), y <= lake.level() ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), FLAGS);
					}
					BlockState bed = part > 0.85 ? Blocks.SAND.defaultBlockState() : (x * 31 + z * 17) % 5 == 0 ? Blocks.GRAVEL.defaultBlockState()
						: Blocks.CLAY.defaultBlockState();
					level.setBlock(at.set(x, floor, z), bed, FLAGS);
					dug++;
				}
			}
		}
		seal(level, chunk, lake);
		if (dug > 0) {
			lakeBiome(level, chunk, lake);
			loosen(level, chunk, lake);
		}
		chunk.setAttached(DUG, VERSION);
		chunk.markUnsaved();
		return dug;
	}

	/**
	 * Anyone's things (block entities) here and in the chunks round it (a chest just over the line keeps its ground too),
	 * from a little under the lake's bed up: a dungeon far down in the rock is no one's work the lake comes near.
	 */
	private static List<BlockPos> things(final ServerLevel level, final LevelChunk chunk, final Lake lake) {
		List<BlockPos> things = new ArrayList<>();
		int deep = lake.level() - DEEPEST - KEEP_CLEAR;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				LevelChunk near = dx == 0 && dz == 0 ? chunk : level.getChunkSource().getChunkNow(chunk.getPos().x() + dx, chunk.getPos().z() + dz);
				if (near != null) {
					for (BlockPos thing : near.getBlockEntitiesPos()) {
						if (thing.getY() >= deep) {
							things.add(thing);
						}
					}
				}
			}
		}
		return things;
	}

	/** Every column in and round a chunk with anyone's work in it: a block the land did not grow, or ground roofing air. */
	private static LongSet marks(final ServerLevel level, final Lake lake, final ChunkPos pos) {
		LongSet marks = new LongOpenHashSet();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int low = lake.level() - DEEPEST - 1;
		for (int x = pos.getMinBlockX() - MARGIN; x <= pos.getMaxBlockX() + MARGIN; x++) {
			for (int z = pos.getMinBlockZ() - MARGIN; z <= pos.getMaxBlockZ() + MARGIN; z++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
				// (a little past the bank too: someone's house just beyond it keeps the ground beside it)
				if (chunk == null || depthPart(lake, x, z) >= BANK + 0.25) {
					continue;
				}
				int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
				if (!grown(chunk, at, x, z, low, Math.max(top, lake.level()))) {
					marks.add(BlockPos.asLong(x, 0, z));
				}
			}
		}
		return marks;
	}

	/**
	 * The bank: ground standing higher than the slope up from the water is cut down to it, and the cut faced with turf
	 * (sand by the water). Ground already under the slope is left as it is. True if the column was cut.
	 */
	private static boolean bank(final ServerLevel level, final LevelChunk chunk, final Lake lake, final BlockPos.MutableBlockPos at,
		final int x, final int z, final double part, final int top) {
		double past = (part - 1.0) * shore(lake, Math.atan2(z + 0.5 - lake.z(), x + 0.5 - lake.x()));
		int slope = lake.level() + 1 + (int) Math.floor(past * BANK_RISE);
		if (top <= slope || !grown(chunk, at, x, z, slope, top)) {
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
	 * Whether every block of a column, from {@code floor} up, is one the land grew. (A cave is the land's too, even under a
	 * thin roof: a dug-out or an earthen hut has someone's things in it, a torch, a bench, a bed or a door, and those
	 * keep it.)
	 */
	private static boolean grown(final LevelChunk chunk, final BlockPos.MutableBlockPos at, final int x, final int z, final int floor, final int top) {
		return unnatural(chunk, at, x, z, floor, top) == null;
	}

	/** What in a column is someone's work (see {@link #grown}), or null if nothing is. */
	private static @Nullable String unnatural(final LevelChunk chunk, final BlockPos.MutableBlockPos at, final int x, final int z, final int floor,
		final int top) {
		boolean trunk = false;
		for (int y = top; y >= floor; y--) {
			BlockState state = chunk.getBlockState(at.set(x, y, z));
			if (state.isAir()) {
				continue;
			}
			if (state.getBlock() instanceof LeavesBlock) {
				if (state.getValue(LeavesBlock.PERSISTENT)) {
					return "set leaves at " + y;
				}
				continue;
			}
			if (state.is(BlockTags.LOGS)) {
				// wood as a tree grows it: never stripped, and a standing trunk crowned with its leaves
				if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().startsWith("stripped_")) {
					return "stripped wood at " + y;
				}
				if (!trunk && state.hasProperty(RotatedPillarBlock.AXIS) && state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y && !crowned(chunk, x, y, z)) {
					return "a bare log at " + y;
				}
				trunk = true;
				continue;
			}
			if (!natural(state)) {
				return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath() + " at " + y;
			}
		}
		return null;
	}

	/**
	 * Whether the top of a standing trunk has a crown: leaves as a tree grows them (not set down by hand, which keep), or
	 * vines, on it or round it within two blocks (a giant's trunk may end in the middle of its crown, under open air).
	 */
	private static boolean crowned(final LevelChunk chunk, final int x, final int y, final int z) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dy = 0; dy <= 2; dy++) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					if ((dx != 0 || dz != 0 || dy != 0) && ((x + dx) >> 4) == chunk.getPos().x() && ((z + dz) >> 4) == chunk.getPos().z()) {
						BlockState crown = chunk.getBlockState(at.set(x + dx, y + dy, z + dz));
						if (crown.getBlock() instanceof LeavesBlock ? !crown.getValue(LeavesBlock.PERSISTENT) : crown.is(Blocks.VINE)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	/** Whether a block (not a log or leaves) is one the land grows, or the highland's turning set there. */
	public static boolean natural(final BlockState state) {
		return GROWN.contains(state.getBlock()) || state.is(BlockTags.SAND) || state.is(BlockTags.FLOWERS)
			|| state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockItemTags.COAL_ORES.block())
			|| state.is(BlockItemTags.COPPER_ORES.block()) || state.is(BlockItemTags.IRON_ORES.block()) || state.is(BlockItemTags.GOLD_ORES.block())
			|| state.is(BlockItemTags.LAPIS_ORES.block()) || state.is(BlockItemTags.REDSTONE_ORES.block()) || state.is(BlockItemTags.EMERALD_ORES.block())
			|| state.is(BlockItemTags.DIAMOND_ORES.block());
	}

	private static boolean near(final List<BlockPos> things, final int x, final int z, final int clear) {
		for (BlockPos thing : things) {
			if (Math.abs(thing.getX() - x) <= clear && Math.abs(thing.getZ() - z) <= clear) {
				return true;
			}
		}
		return false;
	}

	private static boolean near(final LongSet marks, final int x, final int z) {
		for (int dx = -MARGIN; dx <= MARGIN; dx++) {
			for (int dz = -MARGIN; dz <= MARGIN; dz++) {
				if (marks.contains(BlockPos.asLong(x + dx, 0, z + dz))) {
					return true;
				}
			}
		}
		return false;
	}

	// ── Sealing it in ─────────────────────────────────────────────

	/**
	 * Wherever the lake's water would stand against open air (in this chunk or across its edges), that column of water
	 * is filled with sand, so nothing of it can run out.
	 */
	private static void seal(final ServerLevel level, final LevelChunk chunk, final Lake lake) {
		ChunkPos pos = chunk.getPos();
		int low = lake.level() - DEEPEST - 1;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos by = new BlockPos.MutableBlockPos();
		for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
			for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
				if (depthPart(lake, x, z) >= BANK + 0.1) {
					continue;
				}
				boolean water = depthPart(lake, x, z) < 1.0;
				for (int y = low; y <= lake.level(); y++) {
					BlockState here = level.getBlockState(at.set(x, y, z));
					for (Direction side : Direction.Plane.HORIZONTAL) {
						int nx = x + side.getStepX();
						int nz = z + side.getStepZ();
						LevelChunk other = level.getChunkSource().getChunkNow(nx >> 4, nz >> 4);
						if (other == null) {
							continue;
						}
						BlockState there = other.getBlockState(by.set(nx, y, nz));
						// open air here beside the lake's water there: fill that water in
						if (leaky(here) && isLake(lake, there, nx, nz)) {
							sand(level, lake, nx, nz);
						}
						// the lake's water here beside open air in a chunk already dug, or out past the shore: fill this in
						boolean settled = other == chunk || other.getAttachedOrElse(DUG, 0) >= VERSION || depthPart(lake, nx, nz) >= 1.0;
						if (water && settled && other != chunk && isLake(lake, here, x, z) && leaky(there)) {
							sand(level, lake, x, z);
							here = level.getBlockState(at);
						}
					}
				}
			}
		}
	}

	/** Whether a block lets water through: open air, or something water washes over (a plant, a torch). */
	private static boolean leaky(final BlockState state) {
		return !state.getFluidState().isSource() && !state.isSolid();
	}

	private static boolean isLake(final Lake lake, final BlockState state, final int x, final int z) {
		return state.is(Blocks.WATER) && state.getFluidState().isSource() && depthPart(lake, x, z) < 1.0;
	}

	/** Fills a column of the lake's water with sand, from its surface down. */
	private static void sand(final ServerLevel level, final Lake lake, final int x, final int z) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, lake.level(), z);
		for (int y = lake.level(); y >= lake.level() - DEEPEST - 1 && level.getBlockState(at.setY(y)).is(Blocks.WATER); y--) {
			level.setBlock(at, Blocks.SAND.defaultBlockState(), FLAGS);
		}
	}

	/**
	 * The crowns of trees whose trunks went with the ground: their leaves (as trees grow them) in and round the chunk look
	 * again for wood, and those left with none fall in time, as leaves do; none stay hanging over the water.
	 */
	private static void loosen(final ServerLevel level, final LevelChunk chunk, final Lake lake) {
		ChunkPos pos = chunk.getPos();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = pos.getMinBlockX() - MARGIN; x <= pos.getMaxBlockX() + MARGIN; x++) {
			for (int z = pos.getMinBlockZ() - MARGIN; z <= pos.getMaxBlockZ() + MARGIN; z++) {
				LevelChunk near = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
				if (near == null) {
					continue;
				}
				int top = near.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
				for (int y = lake.level(); y <= top; y++) {
					BlockState state = near.getBlockState(at.set(x, y, z));
					if (state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)) {
						level.scheduleTick(at.immutable(), state.getBlock(), 1 + level.getRandom().nextInt(20));
					}
				}
			}
		}
	}

	/** The lake's cells of the chunk take the lake's biome. */
	private static void lakeBiome(final ServerLevel level, final LevelChunk chunk, final Lake lake) {
		Holder<Biome> water = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(LAKE);
		boolean[] changed = new boolean[1];
		chunk.fillBiomesFromNoise((qx, qy, qz) -> {
			Holder<Biome> existing = chunk.getNoiseBiome(qx, qy, qz);
			int x = (qx << 2) + 2;
			int z = (qz << 2) + 2;
			if (depthPart(lake, x, z) >= 0.95 || existing.equals(water)) {
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

	/** For tests: why a column of a placed lake was not dug (null if nothing stands in the way). */
	public static @Nullable String why(final ServerLevel level, final Domain domain, final int x, final int z) {
		Lake lake = lake(level, domain);
		LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
		if (chunk.getInhabitedTime() > LIVED_IN) {
			return "lived in";
		}
		for (BlockPos thing : things(level, chunk, lake)) {
			if (Math.abs(thing.getX() - x) <= KEEP_CLEAR && Math.abs(thing.getZ() - z) <= KEEP_CLEAR) {
				return "a block entity at " + thing.toShortString();
			}
		}
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dx = -MARGIN; dx <= MARGIN; dx++) {
			for (int dz = -MARGIN; dz <= MARGIN; dz++) {
				LevelChunk near = level.getChunk((x + dx) >> 4, (z + dz) >> 4);
				int top = near.getHeight(Heightmap.Types.WORLD_SURFACE, (x + dx) & 15, (z + dz) & 15);
				String what = unnatural(near, at, x + dx, z + dz, lake.level() - DEEPEST - 1, Math.max(top, lake.level()));
				if (what != null) {
					return "near " + dx + "," + dz + ": " + what;
				}
			}
		}
		return null;
	}

	/** For tests: how many chunks wait to be dug. */
	public static int waiting() {
		return WAITING.size();
	}

	/** A lake, if placed. */
	public static @Nullable Lake placed(final ServerLevel level, final int domain) {
		for (Lake lake : level.getAttachedOrElse(LAKES, List.of())) {
			if (lake.domain() == domain) {
				return lake;
			}
		}
		return null;
	}
}
