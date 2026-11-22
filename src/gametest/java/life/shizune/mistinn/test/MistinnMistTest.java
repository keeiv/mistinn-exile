package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.client.mist.ShoreMistClient;
import life.shizune.mistinn.keeper.Snuffer;
import life.shizune.mistinn.world.Builder;
import life.shizune.mistinn.world.ShoreMist;
import life.shizune.mistinn.world.Structures;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * 霧岸: the mist that closes in round a dead lighthouse. A lighthouse is remembered when it is
 * built; out past the mist's reach the world is drawn as ever; near the tower it is gone a
 * stone's throw away; between, it thickens with every step in. Leaving, it lifts again.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnMistTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel") || Boolean.getBoolean("mistinn.bossreel")) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			BlockPos lighthouse = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				player.setGameMode(GameType.CREATIVE);
				ServerLevel level = server.overworld();
				int x = player.getBlockX() + 240;
				int z = player.getBlockZ();
				BlockPos floor = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
				BlockPos room = Structures.lighthouse(new Builder(level, floor, RandomSource.create(7L)));
				// the keeper is another test's business
				level.getEntitiesOfClass(Snuffer.class, new AABB(floor).inflate(64.0)).forEach(Snuffer::discard);
				return room;
			});
			context.waitTicks(2);
			world.getServer().runOnServer(server -> check(ShoreMist.lighthouses(server.overworld()).contains(lighthouse),
				"the lighthouse is remembered where it was built"));

			// far out: no mist
			stand(context, world, lighthouse, 200);
			context.waitTicks(80);
			context.runOnClient(mc -> {
				check(ShoreMistClient.density() == 0.0F, "two hundred blocks out there is no mist: " + ShoreMistClient.density());
				check(ShoreMistClient.lastEnd() < 0.0F, "and the world is drawn as ever");
				check(!life.shizune.mistinn.client.mist.ShoreAmbience.playing(), "and the shore is quiet");
			});
			context.takeScreenshot("mist-01-far");

			// halfway in: the mist has begun
			stand(context, world, lighthouse, 70);
			context.waitFor(mc -> Math.abs(ShoreMistClient.density() - ShoreMistClient.thickness(mc.player.getX(), mc.player.getZ())) < 0.02F
				&& ShoreMistClient.density() > 0.1F, 200);
			float halfway = context.computeOnClient(mc -> ShoreMistClient.lastEnd());
			check(Float.isFinite(halfway) && halfway > 24.0F, "halfway in, the world is drawn, thinned by the mist: " + halfway);
			context.takeScreenshot("mist-02-halfway");

			// at the tower's foot, out on open ground: a stone's throw
			stand(context, world, lighthouse, 24);
			context.waitFor(mc -> ShoreMistClient.density() > 0.99F, 200);
			context.waitTicks(4);
			float foot = context.computeOnClient(mc -> ShoreMistClient.lastEnd());
			check(foot > 0.0F && foot <= 24.5F, "at the tower's foot the world is gone some 24 blocks away: " + foot);
			check(halfway > foot, "halfway in, the mist is thinner than at the foot: " + halfway + " against " + foot);
			check(context.computeOnClient(mc -> life.shizune.mistinn.client.mist.ShoreAmbience.playing()), "in the mist, the surf and the wind are heard");
			context.takeScreenshot("mist-03-foot");

			// and back out: it lifts
			stand(context, world, lighthouse, 200);
			context.waitFor(mc -> ShoreMistClient.density() == 0.0F && ShoreMistClient.lastEnd() < 0.0F, 300);
			context.waitTicks(3);
			check(!context.computeOnClient(mc -> life.shizune.mistinn.client.mist.ShoreAmbience.playing()), "out of it again, they fade away");
		}
	}

	/**
	 * Stands the hunter on dry ground {@code away} blocks from the lighthouse, facing it: lighthouses
	 * stand on the shore, so the first way round that is not sea.
	 */
	private static void stand(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos lighthouse, final int away) {
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			for (int turn = 0; turn < 24; turn++) {
				double angle = Math.toRadians(180.0 + turn * 15.0);
				int x = lighthouse.getX() + (int) Math.round(Math.cos(angle) * away);
				int z = lighthouse.getZ() + (int) Math.round(Math.sin(angle) * away);
				// generated first: an unloaded column has no height to stand on
				level.getChunk(x >> 4, z >> 4);
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				if (!level.getFluidState(new BlockPos(x, y - 1, z)).isEmpty() || !level.getFluidState(new BlockPos(x, y, z)).isEmpty()) {
					continue;
				}
				float yaw = (float) Math.toDegrees(Math.atan2(lighthouse.getZ() - z, lighthouse.getX() - x)) - 90.0F;
				player(server).teleportTo(level, x + 0.5, y, z + 0.5, Set.of(), yaw, -10.0F, true);
				return;
			}
			throw new AssertionError("no dry ground " + away + " blocks round the lighthouse");
		});
		context.waitTicks(10);
		// the land round a new spot is drawn before anything there is looked at
		world.getConnection().waitForChunksRender();
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
