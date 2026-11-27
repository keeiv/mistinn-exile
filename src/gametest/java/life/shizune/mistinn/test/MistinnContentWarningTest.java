package life.shizune.mistinn.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
import life.shizune.mistinn.client.ClientSettings;
import life.shizune.mistinn.client.title.ContentWarning;
import life.shizune.mistinn.client.title.ContentWarningScreen;
import life.shizune.mistinn.client.title.MistinnTitleScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * 內容警告: on a fresh install, or the first run of a newer version, the warning comes before the title screen (the
 * same chain of screens a launcher's straight join waits on). Esc does not dismiss it; both choices stay on screen in
 * both languages and on the smallest window; the music box plays behind it; "Off" turns the scares off, remembers this
 * version, goes on to the title screen and lets the title's music take over. With {@code -PwarningClip} it also keeps a
 * run of frames of the creatures along the bottom, for a short clip.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnContentWarningTest implements FabricClientGameTest {
	private static final Path PHOTOS = Path.of("photos", "warning");

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		String running = ContentWarning.version();
		check(ContentWarning.due(null, running), "a fresh install is warned (" + running + ")");
		check(ContentWarning.due("0.0.1", running), "the first run of a newer version is warned");
		check(!ContentWarning.due(running, running), "the same version is not warned again");
		check(context.computeOnClient(mc -> !ContentWarning.due()), "the other game tests run without it");
		String answered = context.computeOnClient(mc -> ClientSettings.get(ClientSettings.WARNED_VERSION));
		life.shizune.mistinn.client.ContentLevel level = context.computeOnClient(mc -> life.shizune.mistinn.client.ContentLevel.current());
		String language = context.computeOnClient(mc -> mc.options.languageCode);
		try {
			context.runOnClient(mc -> {
				ContentWarning.test(true);
				ClientSettings.put(ClientSettings.WARNED_VERSION, null);
				life.shizune.mistinn.client.ContentLevel.set(life.shizune.mistinn.client.ContentLevel.FULL);
				mc.options.onboardAccessibility = false;
			});
			check(context.computeOnClient(mc -> ContentWarning.due()), "with no version answered, it is due");
			language(context, "zh_tw");

			// what the game does once it has loaded: the warning first, then what was to come
			context.runOnClient(mc -> mc.gui.buildInitialScreens(null).run());
			context.waitFor(mc -> mc.gui.screen() instanceof ContentWarningScreen, 100);
			context.waitTicks(40);
			check(context.computeOnClient(mc -> mc.getSituationalMusic() == ContentWarningScreen.MUSIC), "the music box plays behind it");
			check(onScreen(context), "all three choices are on screen (zh)");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(5);
			check(context.computeOnClient(mc -> mc.gui.screen() instanceof ContentWarningScreen), "Esc does not dismiss it");
			// no line of the warning begins with a closing mark (a lone 。 on a line of its own), at any width it is shown at
			check(context.computeOnClient(mc -> {
				for (String key : java.util.List.of("warning.mistinn.lead", "warning.mistinn.age", "warning.mistinn.item.0", "warning.mistinn.item.1",
					"warning.mistinn.item.2", "warning.mistinn.kept", "warning.mistinn.advice", "warning.mistinn.choose", "settings.mistinn.level.light.about")) {
					String text = net.minecraft.locale.Language.getInstance().getOrDefault(key);
					for (int width = 150; width <= 420; width++) {
						java.util.List<String> rows = life.shizune.mistinn.client.text.LineBreak.lines(mc.font, text, width);
						for (String row : rows.subList(1, rows.size())) {
							if (!row.isEmpty() && life.shizune.mistinn.client.text.LineBreak.closing(row.charAt(0))) {
								System.out.println("[mistinn warning] " + key + " at " + width + ": " + row);
								return false;
							}
						}
					}
				}
				return true;
			}), "no line of the warning begins with a closing mark");
			// as most players see it (1920x1080), then the test window, then the smallest the game allows
			sizes(context, "zh");
			if (Boolean.getBoolean("mistinn.warningclip")) {
				context.getInput().resizeWindow(1920, 1080);
				context.waitTicks(10);
				for (int frame = 0; frame < 90; frame++) {
					context.takeScreenshot(TestScreenshotOptions.of(String.format("clip-%03d", frame)).disableCounterPrefix()
						.withDestinationDir(PHOTOS.resolve("clip")));
					context.waitTicks(1);
				}
				context.getInput().resizeWindow(854, 480);
				context.waitTicks(10);
			}

			// the English, opened afresh after the language changes
			language(context, "en_us");
			context.runOnClient(mc -> mc.gui.setScreen(new ContentWarningScreen(() -> mc.gui.setScreen(new TitleScreen()))));
			context.waitTicks(40);
			sizes(context, "en");

			// the light level tells the frightening words gently: the Snuffer's count, here
			context.runOnClient(mc -> life.shizune.mistinn.client.ContentLevel.set(life.shizune.mistinn.client.ContentLevel.LIGHT));
			check(context.computeOnClient(mc -> net.minecraft.locale.Language.getInstance().getOrDefault("story.mistinn.radio.snuffer_end.count", ""))
				.contains("came again"), "the light level tells the Snuffer's count gently");
			context.runOnClient(mc -> life.shizune.mistinn.client.ContentLevel.set(life.shizune.mistinn.client.ContentLevel.FULL));
			check(context.computeOnClient(mc -> net.minecraft.locale.Language.getInstance().getOrDefault("story.mistinn.radio.snuffer_end.count", ""))
				.contains("sent"), "and the full level as written");

			// "Standard": no frightening pictures or sounds, this version answered, on to the title screen and its own music
			click(context, context.computeOnClient(mc -> ((ContentWarningScreen) mc.gui.screen()).choiceCentre(1)));
			context.waitFor(mc -> mc.gui.screen() instanceof MistinnTitleScreen, 100);
			check(context.computeOnClient(mc -> life.shizune.mistinn.client.ContentLevel.current() == life.shizune.mistinn.client.ContentLevel.NORMAL
				&& !life.shizune.mistinn.client.ledger.Haunting.scares()),
				"Standard is chosen: no frightening pictures or sounds");
			check(context.computeOnClient(mc -> running.equals(ClientSettings.get(ClientSettings.WARNED_VERSION)) && !ContentWarning.due()),
				"and this version is answered, so it does not come again");
			context.waitTicks(20);
			check(context.computeOnClient(mc -> mc.getSituationalMusic() != ContentWarningScreen.MUSIC), "the title's own music takes over");
		} finally {
			context.runOnClient(mc -> {
				ClientSettings.put(ClientSettings.WARNED_VERSION, answered);
				life.shizune.mistinn.client.ContentLevel.set(level);
				ContentWarning.test(false);
			});
			language(context, language);
			context.runOnClient(mc -> mc.gui.setScreen(new TitleScreen()));
		}
	}

	/** At each window size: both choices on screen, and a picture of it. Ends at the test window. */
	private static void sizes(final ClientGameTestContext context, final String language) {
		int[][] windows = {{1920, 1080}, {854, 480}, {640, 480}};
		for (int[] window : windows) {
			context.getInput().resizeWindow(window[0], window[1]);
			context.waitTicks(10);
			String seen = context.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight()
				+ " at " + (mc.gui.screen() instanceof ContentWarningScreen warning ? warning.drawnAt() : -1.0F));
			check(onScreen(context), "all three choices are on screen (" + language + ", " + window[0] + "x" + window[1] + ": " + seen + ")");
			context.takeScreenshot(TestScreenshotOptions.of("warning-" + language + "-" + window[0]).disableCounterPrefix().withDestinationDir(PHOTOS));
		}
		context.getInput().resizeWindow(854, 480);
		context.waitTicks(10);
	}

	private static boolean onScreen(final ClientGameTestContext context) {
		return context.computeOnClient(mc -> mc.gui.screen() instanceof ContentWarningScreen warning && warning.choicesOnScreen());
	}

	private static void language(final ClientGameTestContext context, final String code) {
		if (code.equals(context.computeOnClient(mc -> mc.options.languageCode))) {
			return;
		}
		context.runOnClient(mc -> {
			mc.options.languageCode = code;
			mc.getLanguageManager().setSelected(code);
			mc.reloadResourcePacks();
		});
		context.waitTicks(200);
	}

	private static void click(final ClientGameTestContext context, final double[] gui) {
		double scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		context.getInput().setCursorPos(gui[0] * scale, gui[1] * scale);
		context.waitTicks(2);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn warning] ok: " + what);
	}
}
