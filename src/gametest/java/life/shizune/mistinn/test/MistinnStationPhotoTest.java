package life.shizune.mistinn.test;

import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import life.shizune.mistinn.client.training.TrainingClient;
import life.shizune.mistinn.training.TrainingGround;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Photos of Rust Anchor Station in a real training save, from the places it is meant to be seen
 * from, HUD hidden. Runs only in photo mode (-PphotoMode -PonlyTest=MistinnStationPhotoTest), where
 * Sodium, Iris and the shader pack placed in the run folder light it; writes to photos/station-*.png.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnStationPhotoTest implements FabricClientGameTest {
	/** Camera x, y, z (the eye), then the point it looks at. */
	private static final double[][] VIEWS = {
		{0.5, 66.62, 26.5, -3.5, 68, 8.5},
		{25.5, 73.5, -34.5, 5.5, 81, -34.5},
		{-16, 80, -36, 6, 96, -34},
		{-58.5, 78.62, -88.5, -47.5, 79, -80.5},
		{-38.5, 92.62, -114.5, -26.5, 101.5, -129.5},
		{73.5, 139, 48.5, -4, 93, -44},
	};

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass()) || !Boolean.getBoolean("mistinn.photo")) {
			return;
		}
		context.runOnClient(mc -> {
			mc.options.graphicsPreset().set(GraphicsPreset.FABULOUS);
			mc.options.renderDistance().set(16);
			mc.getWindow().setWindowed(1920, 1080);
		});
		context.runOnClient(mc -> TrainingClient.createWorld(mc, life.shizune.mistinn.hardship.Hardship.NORMAL));
		context.waitFor(mc -> mc.player != null && mc.player.level().dimension().equals(TrainingGround.KEY), 2000);
		// with Sodium and Iris the chunk checks never report done: wait for the station to load and the shaders to settle
		context.waitTicks(600);
		server(context, player -> player.setNoGravity(true));
		context.runOnClient(mc -> mc.gui.hud.toggle());
		try {
			for (int i = 0; i < VIEWS.length; i++) {
				double[] v = VIEWS[i];
				double dx = v[3] - v[0];
				double dy = v[4] - v[1];
				double dz = v[5] - v[2];
				float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
				float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
				server(context, player -> {
					player.teleportTo(player.level(), v[0], v[1] - player.getEyeHeight(), v[2], Set.of(), yaw, pitch, true);
					player.setDeltaMovement(Vec3.ZERO);
				});
				context.waitTicks(i == 0 ? 200 : 160);
				context.takeScreenshot(TestScreenshotOptions.of("station-" + (i + 1)).disableCounterPrefix().withDestinationDir(Path.of("photos")));
			}
		} finally {
			context.runOnClient(mc -> mc.gui.hud.toggle());
			closeWorld(context);
		}
	}

	private static void server(final ClientGameTestContext context, final Consumer<ServerPlayer> action) {
		var server = context.computeOnClient(Minecraft::getSingleplayerServer);
		var done = server.submit(() -> action.accept(server.getPlayerList().getPlayers().getFirst()));
		context.waitFor(mc -> done.isDone(), 400);
		done.join();
	}

	private static void closeWorld(final ClientGameTestContext context) {
		var previous = context.computeOnClient(Minecraft::getSingleplayerServer);
		context.runOnClient(mc -> {
			if (mc.level != null) {
				mc.level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE);
				mc.disconnectWithSavingScreen();
			}
		});
		context.waitFor(mc -> mc.level == null && (previous == null || !previous.getRunningThread().isAlive()), 1200);
		context.setScreen(TitleScreen::new);
	}
}
