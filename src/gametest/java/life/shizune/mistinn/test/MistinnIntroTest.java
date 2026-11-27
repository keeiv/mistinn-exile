package life.shizune.mistinn.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
import life.shizune.mistinn.client.title.IntroScreen;
import life.shizune.mistinn.client.title.KeeivIntroScreen;
import life.shizune.mistinn.client.title.MistinnTitleScreen;
import life.shizune.mistinn.client.title.TeahouseIntroScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;

/**
 * The opening: once the game has loaded, the MoriTeahouse card, then keeiv's, then the title screen (the content
 * warning, when due, comes between; its own test covers it). Silent while it plays; any key skips a card. Stills of
 * each card at full size and on the test window; with {@code -PintroClip}, every thirtieth of a second of both cards,
 * held still for the picture, for a clip.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnIntroTest implements FabricClientGameTest {
	private static final Path PHOTOS = Path.of("photos", "intro");
	private static final int FPS = 30;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		check(context.computeOnClient(mc -> !IntroScreen.due()), "the other game tests start without the opening");
		try {
			context.runOnClient(mc -> {
				IntroScreen.test(true);
				mc.options.onboardAccessibility = false;
			});
			context.runOnClient(mc -> mc.gui.buildInitialScreens(null).run());
			context.waitFor(mc -> mc.gui.screen() instanceof TeahouseIntroScreen, 100);
			check(context.computeOnClient(mc -> mc.getSituationalMusic() == null), "the opening is silent");

			// the stills: each card with everything in, as most players see it and on the test window
			for (int[] window : new int[][] {{1920, 1080}, {854, 480}}) {
				context.getInput().resizeWindow(window[0], window[1]);
				context.runOnClient(mc -> IntroScreen.freeze(3.2F));
				context.waitTicks(5);
				context.takeScreenshot(TestScreenshotOptions.of("teahouse-" + window[0]).disableCounterPrefix().withDestinationDir(PHOTOS));
			}
			context.getInput().resizeWindow(1920, 1080);
			if (Boolean.getBoolean("mistinn.introclip")) {
				frames(context, "teahouse", 4.6F);
			}
			// on to keeiv's card
			context.runOnClient(mc -> ((IntroScreen) mc.gui.screen()).finish());
			context.waitFor(mc -> mc.gui.screen() instanceof KeeivIntroScreen, 40);
			String keeivSize = context.computeOnClient(mc -> mc.gui.screen().width + "x" + mc.gui.screen().height);
			check(!keeivSize.startsWith("0x"), "keeiv's card is laid out on the window: " + keeivSize);
			for (int[] window : new int[][] {{1920, 1080}, {854, 480}}) {
				context.getInput().resizeWindow(window[0], window[1]);
				context.runOnClient(mc -> IntroScreen.freeze(2.6F));
				context.waitTicks(5);
				context.takeScreenshot(TestScreenshotOptions.of("keeiv-" + window[0]).disableCounterPrefix().withDestinationDir(PHOTOS));
			}
			context.getInput().resizeWindow(1920, 1080);
			if (Boolean.getBoolean("mistinn.introclip")) {
				frames(context, "keeiv", 3.6F);
			}
			context.getInput().resizeWindow(854, 480);

			// on the clock again: a key skips the card, and after the last comes the title screen
			context.runOnClient(mc -> IntroScreen.freeze(null));
			context.runOnClient(mc -> mc.gui.buildInitialScreens(null).run());
			context.waitFor(mc -> mc.gui.screen() instanceof TeahouseIntroScreen, 100);
			context.waitTicks(10);
			context.getInput().pressKey(InputConstants.KEY_SPACE);
			context.waitFor(mc -> mc.gui.screen() instanceof KeeivIntroScreen, 40);
			check(true, "a key skips the teahouse's card to keeiv's");
			// left alone, keeiv's card runs out by itself and goes on to the title screen
			context.waitFor(mc -> mc.gui.screen() instanceof MistinnTitleScreen, 200);
			check(context.computeOnClient(mc -> mc.getSituationalMusic() != null), "and the title screen has its music");
		} finally {
			context.runOnClient(mc -> {
				IntroScreen.freeze(null);
				IntroScreen.test(false);
			});
			context.getInput().resizeWindow(854, 480);
		}
	}

	/** Every thirtieth of a second of the card on screen, held still for each picture. */
	private static void frames(final ClientGameTestContext context, final String card, final float length) {
		int count = Math.round(length * FPS);
		for (int i = 0; i <= count; i++) {
			float at = i / (float) FPS;
			context.runOnClient(mc -> IntroScreen.freeze(at));
			context.waitTicks(1);
			context.takeScreenshot(TestScreenshotOptions.of(String.format("%s-%03d", card, i)).disableCounterPrefix().withDestinationDir(PHOTOS.resolve("clip")));
		}
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn intro] ok: " + what);
	}
}
