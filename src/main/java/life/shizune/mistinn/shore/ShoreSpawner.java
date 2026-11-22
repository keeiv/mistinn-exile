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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The dead come up out of the shore mist. A hunter inside a lighthouse's mist now and then finds
 * something a little way off in it: a salt corpse, on its feet or lying where it fell, waiting; or
 * a lamp thrall, its lantern a blue point in the grey. Never more than a few round one hunter;
 * never in the open land, never for a hunter who cannot be hurt.
 */
public final class ShoreSpawner {
	private static final int EVERY = 100;
	private static final float CHANCE = 0.6F;
	/** At most this many of the mist's creatures this close round a hunter. */
	public static final int CAP = 3;
	public static final double NEAR = 48.0;
	/** How far off they come up: out of sight in the thick of the mist, not at the hunter's elbow. */
	public static final int CLOSEST = 18;
	public static final int FARTHEST = 32;
	/** The share of them that are lamp thralls. */
	private static final float THRALLS = 0.35F;

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

	/** One try at bringing one of them up in the mist round the hunter; what came up, or null. */
	public static @Nullable Mob tryOnce(final ServerPlayer hunter) {
		return tryOnce(hunter, hunter.getRandom().nextFloat() < THRALLS);
	}

	/** One try at bringing a thrall, or else a corpse, up in the mist round the hunter (also a test hook). */
	public static @Nullable Mob tryOnce(final ServerPlayer hunter, final boolean thrall) {
		ServerLevel level = hunter.level();
		if (!level.dimension().equals(Level.OVERWORLD) || !inMist(level, hunter.getX(), hunter.getZ())) {
			return null;
		}
		if (round(level, hunter) >= CAP) {
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
			Mob creature = thrall ? MistinnEntities.LAMP_THRALL.create(level, EntitySpawnReason.EVENT)
				: MistinnEntities.SALT_CORPSE.create(level, EntitySpawnReason.EVENT);
			if (creature == null) {
				return null;
			}
			creature.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
			if (creature instanceof SaltCorpse corpse && random.nextBoolean()) {
				corpse.lieDormant(false);
			}
			level.addFreshEntity(creature);
			return creature;
		}
		return null;
	}

	/** How many of the mist's creatures are round the hunter. */
	public static int round(final ServerLevel level, final ServerPlayer hunter) {
		return level.getEntitiesOfClass(Mob.class, hunter.getBoundingBox().inflate(NEAR), mob -> mob instanceof SaltCorpse || mob instanceof LampThrall).size();
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
