package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.keeper.MistWall;
import life.shizune.mistinn.keeper.Snuffer;
import life.shizune.mistinn.shore.SaltCorpse;
import life.shizune.mistinn.world.Builder;
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
 * 霧牆: a hunter comes up into the lamp room and the mist shuts the hatch behind them; it stays
 * shut while they are in the room, opens once the room has stood empty a breath, and opens at once
 * when the keeper falls.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnMistWallTest implements FabricClientGameTest {
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
			world.getServer().runCommand("difficulty normal");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			BlockPos room = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				player.setGameMode(GameType.CREATIVE);
				ServerLevel level = server.overworld();
				int x = player.getBlockX() + 40;
				int z = player.getBlockZ();
				level.getChunk(x >> 4, z >> 4);
				BlockPos floor = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
				BlockPos lamp = Structures.lighthouse(new Builder(level, floor, RandomSource.create(3L)));
				level.getEntitiesOfClass(SaltCorpse.class, new AABB(floor).inflate(16.0)).forEach(SaltCorpse::discard);
				player.teleportTo(level, lamp.getX() + 0.5, floor.getY() + 1, lamp.getZ() - 10.5, Set.of(), 0.0F, 0.0F, true);
				return lamp;
			});
			context.waitTicks(30);
			world.getServer().runOnServer(server -> check(!MistWall.shut(keeper(server, room), server.overworld()), "with no one up in the room, the hatch is open"));

			// a hunter comes up into the room: the hatch shuts behind them
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.teleportTo(server.overworld(), room.getX() + 0.5, room.getY(), room.getZ() - 3.5, Set.of(), 0.0F, 20.0F, true);
				player.setGameMode(GameType.SURVIVAL);
				// the keeper is not this test's business: nothing it does gets through
				player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 2400, 4, false, false));
			});
			context.waitTicks(12);
			world.getServer().runOnServer(server -> check(MistWall.shut(keeper(server, room), server.overworld()), "a hunter up in the room: the mist shuts the hatch"));
			context.takeScreenshot("wall-01-shut");
			context.waitTicks(40);
			world.getServer().runOnServer(server -> check(MistWall.shut(keeper(server, room), server.overworld()), "and it stays shut while they are there"));

			// they leave by some other way: once the room stands empty, it opens
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.teleportTo(server.overworld(), room.getX() + 0.5, room.getY() - 21, room.getZ() - 12.5, Set.of(), 0.0F, 0.0F, true);
				player.setGameMode(GameType.CREATIVE);
			});
			context.waitTicks(70);
			world.getServer().runOnServer(server -> check(!MistWall.shut(keeper(server, room), server.overworld()), "the room empty a breath, the hatch opens"));

			// back up, and the keeper falls: it opens at once
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.teleportTo(server.overworld(), room.getX() + 0.5, room.getY(), room.getZ() - 3.5, Set.of(), 0.0F, 20.0F, true);
				player.setGameMode(GameType.SURVIVAL);
			});
			context.waitTicks(12);
			world.getServer().runOnServer(server -> {
				Snuffer keeper = keeper(server, room);
				check(MistWall.shut(keeper, server.overworld()), "shut again");
				keeper.kill(server.overworld());
			});
			context.waitTicks(2);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				int open = 0;
				for (BlockPos at : BlockPos.betweenClosed(room.offset(-8, -1, -8), room.offset(8, -1, 8))) {
					if (level.getBlockState(at).is(net.minecraft.world.level.block.Blocks.BARRIER)) {
						open++;
					}
				}
				check(open == 0, "the keeper fallen, the way opens at once: " + open + " cells still shut");
				player(server).setGameMode(GameType.CREATIVE);
			});
		}
	}

	private static Snuffer keeper(final MinecraftServer server, final BlockPos room) {
		return server.overworld().getEntitiesOfClass(Snuffer.class, new AABB(room).inflate(16.0)).getFirst();
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
