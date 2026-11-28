package life.shizune.mistinn.terra;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.function.Supplier;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.domain.DomainService;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnEntities;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The highland's animals in land that was there before them. A Terra highland's ground is turned after its chunks are
 * made, so the world's own spawning at a chunk's making never meets the highland's animals; and a world made before an
 * animal came into the mod has none of it in the land it has already walked. So each chunk of a highland, once its ground
 * is turned, is stocked once for this version of the highland's animals: in the valleys herds of jade-horn antelopes and
 * crystal sheep, here and there a land shark; in the vein waste fewer. Only on open, untouched ground (the highland's own
 * turf or soil, nothing of anyone's near), and not right beside a hunter. A chunk stocked is not stocked again until a
 * later version brings new animals.
 */
public final class TerraFauna {
	/** The version of the highland's animals: raised when a new one comes, so old land is stocked with it once. */
	public static final int VERSION = 1;
	/** How near to a hunter no animal is set down, and how far from anyone's things. */
	public static final double CLEAR_OF_HUNTER = 16.0;
	public static final int CLEAR_OF_THINGS = 6;
	/** How many chunks are stocked each tick. */
	private static final int PER_TICK = 2;
	/** The version each chunk has been stocked for (0: never). */
	public static final AttachmentType<Integer> STOCKED = AttachmentRegistry.<Integer>builder()
		.persistent(Codec.INT)
		.buildAndRegister(Mistinn.id("terra_fauna"));

	private static final LongLinkedOpenHashSet WAITING = new LongLinkedOpenHashSet();

	private TerraFauna() {
	}

	public static void register() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (level.dimension() == Level.OVERWORLD && chunk.getAttachedOrElse(STOCKED, 0) < VERSION) {
				WAITING.add(chunk.getPos().pack());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(TerraFauna::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WAITING.clear());
	}

	private static void tick(final MinecraftServer server) {
		if (WAITING.isEmpty()) {
			return;
		}
		ServerLevel level = server.overworld();
		int worked = 0;
		int looked = 0;
		while (!WAITING.isEmpty() && worked < PER_TICK && looked < 64) {
			long packed = WAITING.removeFirstLong();
			looked++;
			ChunkPos pos = ChunkPos.unpack(packed);
			LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
			if (chunk == null) {
				continue;
			}
			Domain domain = DomainService.at(level, pos.getMiddleBlockX(), pos.getMiddleBlockZ());
			if (domain == null || domain.kind() != Domain.Kind.TERRA) {
				// not a highland's: nothing to stock, ever (a highland that comes here later turns this chunk and loads it anew)
				continue;
			}
			if (!DomainService.worked(server, domain.id(), pos)) {
				// its ground is not turned yet: come back to it
				WAITING.addAndMoveToLast(packed);
				continue;
			}
			stock(level, chunk, domain);
			chunk.setAttached(STOCKED, VERSION);
			chunk.markUnsaved();
			worked++;
		}
	}

	/** Stocks one chunk of a highland with its animals, as its ring keeps them. */
	public static void stock(final ServerLevel level, final LevelChunk chunk, final Domain domain) {
		ChunkPos pos = chunk.getPos();
		RandomSource random = RandomSource.create(pos.pack() ^ (long) domain.id() * 0x9E3779B97F4A7C15L);
		double distance = Math.hypot(pos.getMiddleBlockX() - domain.centre().getX(), pos.getMiddleBlockZ() - domain.centre().getZ());
		TerraShaper.Zone zone = TerraShaper.zone(domain, pos.getMiddleBlockX(), pos.getMiddleBlockZ(), distance);
		switch (zone) {
			case TERRACES -> {
				maybe(level, chunk, random, 6, () -> MistinnEntities.JADE_ANTELOPE, 2, 4);
				maybe(level, chunk, random, 9, () -> MistinnEntities.CRYSTAL_SHEEP, 2, 3);
				maybe(level, chunk, random, 16, () -> MistinnEntities.LAND_SHARK, 1, 1);
			}
			case VEINS -> {
				maybe(level, chunk, random, 18, () -> MistinnEntities.JADE_ANTELOPE, 2, 3);
				maybe(level, chunk, random, 12, () -> MistinnEntities.LAND_SHARK, 1, 2);
			}
			default -> {
			}
		}
	}

	/** One time in {@code odds}, a group of this kind, set down near one open spot of the chunk. */
	private static void maybe(final ServerLevel level, final LevelChunk chunk, final RandomSource random, final int odds,
		final Supplier<EntityType<? extends Mob>> type, final int least, final int most) {
		if (random.nextInt(odds) != 0) {
			return;
		}
		ChunkPos pos = chunk.getPos();
		BlockPos centre = open(level, chunk, pos.getMinBlockX() + 2 + random.nextInt(12), pos.getMinBlockZ() + 2 + random.nextInt(12));
		if (centre == null) {
			return;
		}
		int count = least + random.nextInt(most - least + 1);
		for (int i = 0; i < count; i++) {
			BlockPos at = i == 0 ? centre : open(level, chunk, centre.getX() + random.nextInt(5) - 2, centre.getZ() + random.nextInt(5) - 2);
			if (at == null) {
				continue;
			}
			Mob mob = type.get().create(level, EntitySpawnReason.CHUNK_GENERATION);
			if (mob != null) {
				mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
				mob.setPersistenceRequired();
				level.addFreshEntity(mob);
			}
		}
	}

	/** An open spot on the highland's own ground in this column of the chunk, clear of hunters and of anyone's things; or null. */
	private static BlockPos open(final ServerLevel level, final LevelChunk chunk, final int x, final int z) {
		ChunkPos pos = chunk.getPos();
		if (x < pos.getMinBlockX() || x > pos.getMaxBlockX() || z < pos.getMinBlockZ() || z > pos.getMaxBlockZ()) {
			return null;
		}
		int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
		BlockPos at = new BlockPos(x, y, z);
		BlockState ground = chunk.getBlockState(at.below());
		if (!(ground.is(MistinnBlocks.JADE_TURF) || ground.is(MistinnBlocks.VEIN_SOIL))
			|| !chunk.getBlockState(at).isAir() || !chunk.getBlockState(at.above()).isAir()) {
			return null;
		}
		if (level.getNearestPlayer(x + 0.5, y, z + 0.5, CLEAR_OF_HUNTER, false) != null) {
			return null;
		}
		for (BlockPos block : chunk.getBlockEntitiesPos()) {
			if (Math.abs(block.getX() - x) <= CLEAR_OF_THINGS && Math.abs(block.getZ() - z) <= CLEAR_OF_THINGS) {
				return null;
			}
		}
		return at;
	}

	/** For tests: how many chunks wait to be stocked. */
	public static int waiting() {
		return WAITING.size();
	}
}
