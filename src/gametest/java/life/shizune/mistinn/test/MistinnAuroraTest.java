package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.client.terra.TerraAurora;
import life.shizune.mistinn.terra.TerraService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The aurora over a Terra highland: none by day and none away from the highlands; as night falls
 * over one, it comes up; with the dawn it goes.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnAuroraTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode creative @a");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("time set 18000");
			context.waitTicks(120);
			check(context.computeOnClient(mc -> TerraAurora.shown()) < 0.05F, "no aurora away from the highlands, even at night");

			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				// the heart a way off, so its elder sleeps
				BlockPos centre = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(110, 0, 0));
				TerraService.settleAt(level, centre, 140);
				player.getAbilities().flying = true;
				player.onUpdateAbilities();
				player.teleportTo(level, player.getX(), player.getY() + 6.0, player.getZ(), Set.of(), -90.0F, -28.0F, true);
			});
			boolean up = false;
			for (int i = 0; i < 40 && !up; i++) {
				context.waitTicks(10);
				up = context.computeOnClient(mc -> TerraAurora.shown()) > 0.8F;
			}
			check(up, "at night over a highland, the aurora comes up");
			// facing the heart, where it hangs, once the place's name has faded
			context.waitTicks(120);
			context.takeScreenshot(TestScreenshotOptions.of("aurora").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("terra")));

			world.getServer().runCommand("time set 1000");
			boolean gone = false;
			for (int i = 0; i < 40 && !gone; i++) {
				context.waitTicks(10);
				gone = context.computeOnClient(mc -> TerraAurora.shown()) < 0.05F;
			}
			check(gone, "and with the day it goes");
		}
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
	}
}
