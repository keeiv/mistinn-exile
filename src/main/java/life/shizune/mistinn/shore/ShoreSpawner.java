package life.shizune.mistinn.shore;

import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.world.ShoreMist;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The dead come up out of the shore mist. A hunter inside a lighthouse's mist now and then finds
 * a salt corpse a little way off in it, on its feet or lying where it fell, waiting. Never more than
 * a few round one hunter; never in the open land, never for a hunter who cannot be hurt.
 */
public final class ShoreSpawner {
	private static final int EVERY = 100;
	private static final float CHANCE = 0.6F;
	/** At most this many corpses this close round a hunter. */
	public static final int CAP = 3;
	public static final double NEAR = 48.0;
	/** How far off they come up: out of sight in the thick of the mist, not at the hunter's elbow. */
	public static final int CLOSEST = 18;
	public static final int FARTHEST = 32;

	private ShoreSpawner() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ShoreSpawner::tick);
	}

	private static void tick(final MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel overworld = server.overworld();
		if (overworld.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		for (ServerPlayer hunter : server.getPlayerList().getPlayers()) {
			if (hunter.level() == overworld && !hunter.isSpectator() && !hunter.isCreative() && hunter.getRandom().nextFloat() < CHANCE) {
				tryOnce(hunter);
			}
		}
	}

	/** One try at bringing a corpse up in the mist round the hunter; the corpse, or null (also a test hook). */
	public static @Nullable SaltCorpse tryOnce(final ServerPlayer hunter) {
		ServerLevel level = hunter.level();
		if (!level.dimension().equals(Level.OVERWORLD) || !inMist(level, hunter.getX(), hunter.getZ())) {
			return null;
		}
		if (level.getEntitiesOfClass(SaltCorpse.class, hunter.getBoundingBox().inflate(NEAR)).size() >= CAP) {
			return null;
		}
		RandomSource random = hunter.getRandom();
		for (int attempt = 0; attempt < 8; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = CLOSEST + random.nextDouble() * (FARTHEST - CLOSEST);
			int x = (int) Math.floor(hunter.getX() + Math.cos(angle) * distance);
			int z = (int) Math.floor(hunter.getZ() + Math.sin(angle) * distance);
			if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos feet = new BlockPos(x, y, z);
			if (!level.getFluidState(feet.below()).isEmpty() || !level.getFluidState(feet).isEmpty()
				|| !level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)) {
				continue;
			}
			if (!level.noCollision(MistinnEntities.SALT_CORPSE.getSpawnAABB(x + 0.5, y, z + 0.5))) {
				continue;
			}
			SaltCorpse corpse = MistinnEntities.SALT_CORPSE.create(level, EntitySpawnReason.EVENT);
			if (corpse == null) {
				return null;
			}
			corpse.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
			if (random.nextBoolean()) {
				corpse.lieDormant(false);
			}
			level.addFreshEntity(corpse);
			return corpse;
		}
		return null;
	}

	/** Whether a place is inside some lighthouse's mist. */
	public static boolean inMist(final ServerLevel level, final double x, final double z) {
		for (BlockPos lighthouse : ShoreMist.lighthouses(level)) {
			if (Math.hypot(lighthouse.getX() + 0.5 - x, lighthouse.getZ() + 0.5 - z) <= ShoreMist.REACH) {
				return true;
			}
		}
		return false;
	}
}
