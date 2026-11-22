package life.shizune.mistinn.test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.boss.BossMove;
import life.shizune.mistinn.client.keeper.SnufferModel;
import life.shizune.mistinn.keeper.SnufferMoves;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * 燈守的姿勢表: every keeper clip (each move and each idle) frozen at five beats and screenshotted from the side
 * and from the front, so the body and its moves can be judged frame by frame: 熄燈人's, then 沉鐘's (named bell-...).
 * Runs only with -Dmistinn.keepersheet=true (build.gradle: -PkeeperSheet, or -PkeeperSheet=snuffer / =bell for one of
 * them); writes to keepersheet/.
 */
@SuppressWarnings("UnstableApiUsage")
public class MistinnKeeperSheetTest implements FabricClientGameTest {
	private static final Path OUT = Path.of("keepersheet");
	private static final float[] BEATS = {0.0F, 0.22F, 0.42F, 0.62F, 0.85F};

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass()) || !Boolean.getBoolean("mistinn.keepersheet")) {
			return;
		}
		String only = System.getProperty("mistinn.keepersheet.keeper", "");
		boolean snuffer = only.isEmpty() || only.equals("true") || only.equals("snuffer");
		boolean bell = only.isEmpty() || only.equals("true") || only.equals("bell");
		List<String> clips = new ArrayList<>();
		for (BossMove move : SnufferMoves.SET.moves()) {
			clips.add(move.id());
		}
		clips.addAll(List.of(SnufferModel.IDLES));
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("gamemode spectator @a");
			BlockPos stage = world.getServer().computeOnServer(server -> {
				var player = server.getPlayerList().getPlayers().getFirst();
				BlockPos pos = player.blockPosition().above(20).immutable();
				for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-8, -1, -8), pos.offset(8, -1, 8))) {
					player.level().setBlockAndUpdate(floor, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
				}
				for (BlockPos air : BlockPos.betweenClosed(pos.offset(-8, 0, -8), pos.offset(8, 6, 8))) {
					player.level().setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
				}
				return pos;
			});
			double x = stage.getX() + 0.5;
			double y = stage.getY();
			double z = stage.getZ() + 0.5;
			context.runOnClient(mc -> mc.gui.hud.toggle());
			String[][] views = {
				{"side", String.valueOf(x - 4.2), String.valueOf(y - 0.1), String.valueOf(z), "-90", "6"},
				{"front", String.valueOf(x), String.valueOf(y - 0.1), String.valueOf(z + 4.5), "180", "6"},
			};
			if (bell) {
				bellSheet(context, world, x, y, z, views);
			}
			if (!snuffer) {
				context.runOnClient(mc -> mc.gui.hud.toggle());
				return;
			}
			world.getServer().runCommand("summon mistinn:snuffer " + x + " " + y + " " + z + " {NoAI:1b,Rotation:[0.0f,0.0f],Tags:[\"sheet\"]}");
			for (String[] view : views) {
				world.getServer().runCommand("tp @a " + view[1] + " " + view[2] + " " + view[3] + " " + view[4] + " " + view[5]);
				context.waitTicks(10);
				context.takeScreenshot(TestScreenshotOptions.of("rest-" + view[0]).disableCounterPrefix().withDestinationDir(OUT));
				for (String clip : clips) {
					for (int i = 0; i < BEATS.length; i++) {
						float at = BEATS[i];
						context.runOnClient(mc -> {
							SnufferModel.previewClip = clip;
							SnufferModel.previewProgress = at;
						});
						context.waitTicks(2);
						context.takeScreenshot(TestScreenshotOptions.of(clip + "-" + view[0] + "-" + i).disableCounterPrefix().withDestinationDir(OUT));
					}
				}
				context.runOnClient(mc -> SnufferModel.previewClip = null);
			}
			// the dark half: the lantern burns cold
			world.getServer().runCommand("item replace entity @e[tag=sheet] armor.head with minecraft:soul_lantern");
			world.getServer().runCommand("time set 18000");
			world.getServer().runCommand("tp @a " + views[1][1] + " " + views[1][2] + " " + views[1][3] + " " + views[1][4] + " " + views[1][5]);
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("rest-front-dark").disableCounterPrefix().withDestinationDir(OUT));
			context.runOnClient(mc -> mc.gui.hud.toggle());
		}
	}

	/** 沉鐘's sheet: every move and idle of the bell keeper, at the same beats, from the same two sides. */
	private static void bellSheet(final ClientGameTestContext context, final TestSingleplayerContext world, final double x, final double y, final double z,
		final String[][] views) {
		List<String> clips = new ArrayList<>();
		for (BossMove move : life.shizune.mistinn.keeper.BellMoves.SET.moves()) {
			clips.add(move.id());
		}
		clips.addAll(List.of(life.shizune.mistinn.client.keeper.SunkenBellModel.IDLES));
		world.getServer().runCommand("summon mistinn:sunken_bell " + x + " " + y + " " + z + " {NoAI:1b,Rotation:[0.0f,0.0f],Tags:[\"bellsheet\"]}");
		for (String[] view : views) {
			world.getServer().runCommand("tp @a " + view[1] + " " + view[2] + " " + view[3] + " " + view[4] + " " + view[5]);
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("bell-rest-" + view[0]).disableCounterPrefix().withDestinationDir(OUT));
			for (String clip : clips) {
				for (int i = 0; i < BEATS.length; i++) {
					float at = BEATS[i];
					context.runOnClient(mc -> {
						life.shizune.mistinn.client.keeper.SunkenBellModel.previewClip = clip;
						life.shizune.mistinn.client.keeper.SunkenBellModel.previewProgress = at;
					});
					context.waitTicks(2);
					context.takeScreenshot(TestScreenshotOptions.of("bell-" + clip + "-" + view[0] + "-" + i).disableCounterPrefix().withDestinationDir(OUT));
				}
			}
			context.runOnClient(mc -> life.shizune.mistinn.client.keeper.SunkenBellModel.previewClip = null);
		}
		world.getServer().runCommand("kill @e[tag=bellsheet]");
	}
}
