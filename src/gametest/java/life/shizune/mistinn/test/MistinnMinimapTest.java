package life.shizune.mistinn.test;

import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.client.map.MinimapHud;
import life.shizune.mistinn.client.map.MinimapTexture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.server.level.ServerPlayer;

/** The minimap stays up: a destroyed head and an open screen leave it be; only the debug screen covers it. */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnMinimapTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			context.waitTicks(20);
			check(context.computeOnClient(mc -> MinimapTexture.ready() && MinimapHud.reserved() > 0), "the minimap is up");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BodyService.set(player, BodyService.get(player).with(BodyPart.HEAD, 0.0F));
			});
			context.waitTicks(5);
			check(context.computeOnClient(mc -> MinimapHud.reserved() > 0), "a destroyed head does not take the minimap away");
			context.runOnClient(mc -> mc.gui.setScreen(new ChatScreen("", false)));
			context.waitTicks(3);
			check(context.computeOnClient(mc -> MinimapHud.reserved() > 0), "nor does an open screen");
			context.runOnClient(mc -> mc.gui.setScreen(null));
			context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F3);
			context.waitTicks(3);
			check(context.computeOnClient(mc -> MinimapHud.reserved() == 0), "only the debug screen, which fills that corner, covers it");
			context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F3);
			world.getServer().runOnServer(server -> BodyService.restoreAll(server.getPlayerList().getPlayers().getFirst()));
		}
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
