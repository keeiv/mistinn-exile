package life.shizune.mistinn.test;

import java.util.List;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.domain.FindCommands;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.registry.MistinnTab;
import life.shizune.mistinn.rift.RiftService;
import life.shizune.mistinn.terra.TerraService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The commands that find the places of change: /terra names the nearest highland, its elder and
 * its homestead; /terra list lists them; /rift names an open rift; /eco both; any hunter may use
 * them. And the mod's creative tab wears the crystal core.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnFindCommandsTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("gamerule spawn_mobs false");
			context.waitTicks(20);
			Domain[] places = new Domain[2];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				check(MistinnTab.TAB.getIconItem().is(MistinnItems.CRYSTAL_CORE), "the mod's tab wears the crystal core");
				BlockPos terra = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(300, 0, 0));
				places[0] = TerraService.settleAt(level, terra, 140);
				places[1] = RiftService.tearAt(level, player.blockPosition().offset(-150, 0, 40));
			});
			context.waitTicks(5);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				List<Component> terra = FindCommands.terra(player, false);
				String first = terra.getFirst().getString();
				check(first.contains(String.valueOf(places[0].centre().getX())) && first.contains("300"),
					"/terra names the nearest highland, how far and where its heart is: " + first);
				check(terra.size() == 3, "and how its elder and homestead stand: " + terra.size());
				check(FindCommands.terra(player, true).size() >= 1, "/terra list lists the highlands");
				List<Component> rift = FindCommands.rift(player);
				check(rift.getFirst().getString().contains(String.valueOf(places[1].centre().getX())), "/rift names the open rift: " + rift.getFirst().getString());

				// any hunter may use them
				for (String command : new String[] {"terra", "terra list", "rift", "eco"}) {
					try {
						int said = server.getCommands().getDispatcher().execute(command, player.createCommandSourceStack());
						check(said >= 1, "/" + command + " answers: " + said);
					} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
						throw new AssertionError("failed: /" + command + " is not for any hunter: " + e.getMessage());
					}
				}
			});
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
