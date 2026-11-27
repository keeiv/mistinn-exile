package life.shizune.mistinn.test;

import java.nio.file.Path;
import life.shizune.mistinn.client.ClientSettings;
import life.shizune.mistinn.client.anim.CameraSwing;
import life.shizune.mistinn.client.ledger.Haunting;
import life.shizune.mistinn.client.title.MistinnSettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;

/**
 * 霧岸設定: the hunter's own switches. Turning the scares off is kept, and changing the swing camera after it does not
 * forget it (each used to write the settings file over with its own line alone); the screen opens from the title.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnSettingsTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		context.runOnClient(mc -> {
			ClientSettings.set(ClientSettings.SCARES, false);
			CameraSwing.toggle();
		});
		check(context.computeOnClient(mc -> !Haunting.scares()), "the scares stay off after the swing camera is changed");
		check(context.computeOnClient(mc -> !CameraSwing.enabled()), "and the swing camera keeps its own");
		context.runOnClient(mc -> mc.gui.setScreen(new MistinnSettingsScreen(mc.gui.screen())));
		context.waitTicks(10);
		context.takeScreenshot(TestScreenshotOptions.of("settings").disableCounterPrefix().withDestinationDir(Path.of("photos", "settings")));
		context.runOnClient(mc -> {
			mc.gui.screen().onClose();
			ClientSettings.set(ClientSettings.SCARES, true);
			ClientSettings.set(ClientSettings.SWING_CAMERA, true);
		});
		check(context.computeOnClient(mc -> Haunting.scares() && CameraSwing.enabled()), "and both come back on");
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn settings] ok: " + what);
	}
}
