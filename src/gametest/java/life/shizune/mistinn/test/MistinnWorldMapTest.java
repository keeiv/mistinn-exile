package life.shizune.mistinn.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.client.story.StoryCardScreen;
import life.shizune.mistinn.client.title.MenuButton;
import life.shizune.mistinn.client.title.MistinnTitleScreen;
import life.shizune.mistinn.client.worldmap.SeenStory;
import life.shizune.mistinn.client.worldmap.WorldMap;
import life.shizune.mistinn.client.worldmap.WorldMapScreen;
import life.shizune.mistinn.story.StoryScript;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * The world map from the title screen. Its stops stand where a story world builds them; every word is written in
 * both languages. The title menu's entry opens it on Rust Anchor Station and it sinks to the coast. On a computer
 * that has shown no story card only the station is out of the fog; each card shown is remembered and brings its
 * stops out; a stop opens its strip and steps to the next stop out of the fog, never into the fog; everything can be
 * shown at once, a stop with two cards turns between them; the rail jumps to each layer of the world, the wheel
 * scrolls, Esc goes back to the title.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnWorldMapTest implements FabricClientGameTest {
	private static final Path SEEN = FabricLoader.getInstance().getConfigDir().resolve("mistinn-story-seen.properties");

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel") || Boolean.getBoolean("mistinn.bossreel")) {
			return;
		}
		this.places();
		this.words(context);

		// screenshots in Traditional Chinese, as the main test leaves the game
		if (!context.computeOnClient(mc -> mc.options.languageCode.equals("zh_tw"))) {
			boolean[] reloaded = {false};
			context.runOnClient(mc -> {
				mc.options.languageCode = "zh_tw";
				mc.getLanguageManager().setSelected("zh_tw");
				mc.reloadResourcePacks().thenRun(() -> reloaded[0] = true);
			});
			context.waitFor(mc -> reloaded[0], 1200);
			// the loading overlay fades out first, taking clicks meanwhile
			context.waitTicks(40);
		}
		int[] window = context.computeOnClient(mc -> new int[] {mc.getWindow().getWidth(), mc.getWindow().getHeight()});
		context.getInput().resizeWindow(1280, 720);
		// a computer that has shown no story card yet
		forget(context);
		try {
			this.walk(context);
		} finally {
			forget(context);
			context.getInput().resizeWindow(window[0], window[1]);
		}
	}

	/** The stops stand where a story world builds them, and every place lies on the picture. */
	private void places() {
		// StoryService: the cairns at 0.3, 0.55 and 0.8 of the road from the drop station to the fog-bell station, the pod 0.35 of the
		// way from the fog-bell station to the salt works, the Mist Inn 0.3 of the way from the salt works back toward the fog-bell station
		between("salt_road", "drop_station", "bell_station", 0.3);
		between("cairn_2", "drop_station", "bell_station", 0.55);
		between("cairn_3", "drop_station", "bell_station", 0.8);
		between("pod", "bell_station", "salt_works", 0.35);
		between("mist_inn", "salt_works", "bell_station", 0.3);
		// the coast formula: the drop station at the origin, the salt works 260 blocks inland of it
		check(base("drop_station")[0] == 196 && base("drop_station")[1] == 790, "the drop station stands at the formula's origin");
		check(Math.abs(base("salt_works")[1] - (790 + 260 * 0.62)) <= 2, "the salt works stand 260 blocks inland");
		// the lighthouse on the other side of the drop station from the road; the salt works inland of the coast
		check(base("lighthouse")[0] < base("drop_station")[0] && base("drop_station")[0] < base("bell_station")[0], "lighthouse, drop station, bell station along the coast");
		check(base("salt_works")[1] > base("drop_station")[1] + 100, "the salt works lie inland");
		// the station over the water, the coast under it
		check(WorldMap.place("rust_anchor_station").y1() <= WorldMap.UNDERSIDE && WorldMap.place("drop_station").y0() > WorldMap.UNDERSIDE,
			"the station stands above the water, the coast below it");
		int chapter = -1;
		for (WorldMap.Place stop : WorldMap.STOPS) {
			check(stop.chapter() >= chapter, "the stops run in the story's order: " + stop.id());
			chapter = stop.chapter();
		}
		for (WorldMap.Place place : WorldMap.PLACES) {
			check(place.x0() >= 0 && place.y0() >= 0 && place.x1() <= WorldMap.W && place.y1() <= WorldMap.H && place.x0() < place.x1() && place.y0() < place.y1(),
				place.id() + " lies on the picture");
			check(place.x() >= 0 && place.x() < WorldMap.W && place.y() >= 0 && place.y() < WorldMap.H, place.id() + "'s marker is on the picture");
			check(place.gx() >= place.x0() - 1 && place.gx() <= place.x1() && place.gy() >= place.y0() && place.gy() <= place.y1(),
				place.id() + " stands inside what is drawn of it");
			for (String card : place.cards()) {
				check(StoryScript.card(card) != null, place.id() + " shows a card that exists: " + card);
			}
		}
		check(WorldMap.ROUTE.getFirst().place().equals("rust_anchor_station"), "the way starts at the station");
		for (WorldMap.Point point : WorldMap.ROUTE) {
			WorldMap.place(point.place());
		}
	}

	private static int[] base(final String id) {
		WorldMap.Place place = WorldMap.place(id);
		return new int[] {place.gx(), place.gy()};
	}

	private static void between(final String id, final String from, final String to, final double share) {
		int[] a = base(from);
		int[] b = base(to);
		int[] at = base(id);
		double x = a[0] + (b[0] - a[0]) * share;
		double y = a[1] + (b[1] - a[1]) * share;
		// within five map pixels, about eight blocks: the game itself looks for level ground round each point
		check(Math.abs(at[0] - x) <= 5 && Math.abs(at[1] - y) <= 5,
			id + " stands " + share + " of the way from " + from + " to " + to + ": at " + at[0] + "," + at[1] + ", expected " + Math.round(x) + "," + Math.round(y));
	}

	/** Every word of the map is written in both languages, and the picture is the size the map is drawn at. */
	private void words(final ClientGameTestContext context) {
		context.runOnClient(mc -> {
			List<String> keys = new java.util.ArrayList<>(List.of("menu.mistinn.world_map", "worldmap.mistinn.title", "worldmap.mistinn.subtitle",
				"worldmap.mistinn.hint", "worldmap.mistinn.prev", "worldmap.mistinn.next", "worldmap.mistinn.hidden", "worldmap.mistinn.reveal.on",
				"worldmap.mistinn.reveal.off", "worldmap.mistinn.chapter.training", "worldmap.mistinn.chip.training"));
			for (int chapter = 0; chapter <= 5; chapter++) {
				keys.add("worldmap.mistinn.chip." + chapter);
			}
			for (WorldMap.Band band : WorldMap.BANDS) {
				keys.add(band.key());
			}
			for (WorldMap.Place place : WorldMap.PLACES) {
				keys.add(place.key());
				if (place.label()) {
					keys.add(place.about(0));
				}
			}
			for (String code : List.of("zh_tw", "en_us")) {
				ClientLanguage language = ClientLanguage.loadFrom(mc.getResourceManager(), List.of(code), false);
				for (String key : keys) {
					check(language.has(key), code + " has " + key);
				}
			}
			try (InputStream in = mc.getResourceManager().open(Mistinn.id("textures/gui/worldmap/map.png")); NativeImage image = NativeImage.read(in)) {
				check(image.getWidth() == WorldMap.W && image.getHeight() == WorldMap.H, "the map is " + WorldMap.W + " by " + WorldMap.H + ": "
					+ image.getWidth() + " by " + image.getHeight());
			} catch (IOException e) {
				throw new AssertionError("the map's picture could not be read", e);
			}
		});
	}

	private void walk(final ClientGameTestContext context) {
		// the title menu's entry opens the map on the station
		context.setScreen(TitleScreen::new);
		context.waitTicks(5);
		check(context.computeOnClient(mc -> mc.gui.screen() instanceof MistinnTitleScreen), "the title screen is the Mistinn one");
		double[] entry = context.computeOnClient(mc -> {
			for (var child : mc.gui.screen().children()) {
				if (child instanceof MenuButton button && button.getMessage().getContents() instanceof TranslatableContents text
					&& text.getKey().equals("menu.mistinn.world_map")) {
					return new double[] {button.getX() + 30.0, button.getY() + 10.0};
				}
			}
			return null;
		});
		check(entry != null, "the title menu has a World Map entry");
		check(context.computeOnClient(mc -> {
			int lowest = 0;
			for (var child : mc.gui.screen().children()) {
				if (child instanceof MenuButton button) {
					lowest = Math.max(lowest, button.getY() + button.getHeight());
				}
			}
			return lowest <= mc.gui.screen().height - 36;
		}), "the title menu's last entry stays above the icons under it");
		context.getInput().setCursorPos(entry[0] * context.computeOnClient(mc -> mc.getWindow().getGuiScale()),
			entry[1] * context.computeOnClient(mc -> mc.getWindow().getGuiScale()));
		context.waitTicks(30);
		shot(context, "worldmap-title-menu");
		click(context, entry);
		check(context.computeOnClient(mc -> mc.gui.screen() instanceof WorldMapScreen), "the entry opens the world map");
		context.waitTicks(12);
		check(map(context).scroll() < WorldMap.UNDERSIDE, "it opens on the station: " + map(context).scroll());
		shot(context, "worldmap-opening");
		context.waitTicks(80);
		check(map(context).scroll() > WorldMap.UNDERSIDE, "and sinks through the water to the coast: " + map(context).scroll());

		// nothing shown yet: only the station is out of the fog, and a stop in the fog does not open
		for (WorldMap.Place stop : WorldMap.STOPS) {
			check(WorldMapScreen.shown(stop) == stop.id().equals("rust_anchor_station"), stop.id() + " is in the fog until its card is shown");
		}
		check(WorldMapScreen.visibleRoute().size() == 1, "the way stops at the station");
		shot(context, "worldmap-fogged");
		click(context, marker(context, "lighthouse"));
		check(map(context).selected() == null, "a stop in the fog does not open");

		// two cards shown, in any world: they are remembered, and their stops come out of the fog
		for (StoryScript.Card card : List.of(StoryScript.DROP, StoryScript.LIGHTHOUSE)) {
			context.setScreen(() -> new StoryCardScreen(card));
			context.waitTicks(3);
		}
		context.runOnClient(mc -> SeenStory.reload());
		check(SeenStory.seen("drop") && SeenStory.seen("lighthouse") && !SeenStory.seen("salt_road"), "the cards shown are remembered on this computer");
		context.setScreen(() -> new WorldMapScreen(new TitleScreen()));
		context.waitTicks(90);
		for (String id : List.of("rust_anchor_station", "spawn_station", "drop_station", "lighthouse")) {
			check(WorldMapScreen.shown(WorldMap.place(id)), id + " is out of the fog");
		}
		check(!WorldMapScreen.shown(WorldMap.place("salt_road")) && !WorldMapScreen.shown(WorldMap.place("mist_inn")), "the stops past them are not");
		check(WorldMapScreen.visibleRoute().size() == 5, "the way runs to the lighthouse and back: " + WorldMapScreen.visibleRoute().size());
		shot(context, "worldmap-two-cards");

		// a stop opens its strip; the next stop is in the fog, so it stays; the one before opens
		click(context, marker(context, "lighthouse"));
		check(map(context).selected() == WorldMap.place("lighthouse"), "the lighthouse opens");
		context.waitTicks(15);
		shot(context, "worldmap-lighthouse");
		context.getInput().pressKey(InputConstants.KEY_RIGHT);
		context.waitTicks(2);
		check(map(context).selected() == WorldMap.place("lighthouse"), "the next stop is in the fog: the lighthouse stays open");
		context.getInput().pressKey(InputConstants.KEY_LEFT);
		context.waitTicks(15);
		check(map(context).selected() == WorldMap.place("drop_station"), "the stop before it opens");
		check(WorldMapScreen.cards(WorldMap.place("drop_station")).isEmpty(), "its chapter's last card has not been shown: the map shows the place instead");
		shot(context, "worldmap-drop-station");

		// everything shown at once
		double[] reveal = context.computeOnClient(mc -> {
			int width = mc.font.width(Component.translatable("worldmap.mistinn.reveal.off"));
			return new double[] {mc.gui.screen().width - 10 - width / 2.0, 13.0};
		});
		click(context, reveal);
		check(SeenStory.revealAll(), "the footer shows everything");
		for (WorldMap.Place place : WorldMap.PLACES) {
			check(WorldMapScreen.shown(place), place.id() + " is shown");
		}
		check(WorldMapScreen.visibleRoute().size() == WorldMap.ROUTE.size(), "the whole way is drawn");
		context.runOnClient(mc -> {
			map(mc.gui.screen()).close();
			map(mc.gui.screen()).jump("overworld");
		});
		context.getInput().setCursorPos(2.0, 2.0);
		context.waitTicks(40);
		shot(context, "worldmap-revealed");
		context.runOnClient(mc -> map(mc.gui.screen()).select(WorldMap.place("bell_station")));
		context.waitTicks(20);
		check(WorldMapScreen.cards(WorldMap.place("bell_station")).equals(List.of("bell_station", "answer")), "the fog-bell station has its chapter's first and last cards");
		shot(context, "worldmap-bell-station");
		context.runOnClient(mc -> map(mc.gui.screen()).turnCard());
		context.waitTicks(3);
		check(map(context).card() == 1, "the picture turns to the chapter's last card");
		shot(context, "worldmap-bell-station-answer");
		for (String next : List.of("pod", "salt_works", "mist_inn", "mist_inn")) {
			context.getInput().pressKey(InputConstants.KEY_RIGHT);
			context.waitTicks(3);
			check(map(context).selected() == WorldMap.place(next), "the next stop is " + next + ": " + map(context).selected());
		}
		context.waitTicks(15);
		shot(context, "worldmap-mist-inn");
		context.runOnClient(mc -> map(mc.gui.screen()).close());

		// the rail jumps to each layer; the wheel scrolls
		for (String band : List.of("nether", "the_end", "rust_anchor")) {
			click(context, context.computeOnClient(mc -> map(mc.gui.screen()).railAt(band)));
			context.waitTicks(40);
			WorldMap.Band layer = WorldMap.band(band);
			float middle = context.computeOnClient(mc -> {
				WorldMapScreen screen = map(mc.gui.screen());
				return screen.scroll() + mc.gui.screen().height / (mc.gui.screen().width / (float) WorldMap.W) / 2.0F;
			});
			check(middle >= layer.y0() - 40 && middle <= layer.y1() + 40, band + " is in view: " + middle);
			shot(context, "worldmap-" + band.replace('_', '-'));
		}
		float before = map(context).scroll();
		context.runOnClient(mc -> mc.gui.screen().mouseScrolled(100.0, 100.0, 0.0, -3.0));
		context.waitTicks(20);
		check(map(context).scroll() > before + 40, "the wheel scrolls down: " + before + " to " + map(context).scroll());

		// Esc goes back to the title
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(3);
		check(context.computeOnClient(mc -> mc.gui.screen() instanceof TitleScreen), "Esc goes back to the title screen");
	}

	private static void forget(final ClientGameTestContext context) {
		context.runOnClient(mc -> {
			try {
				Files.deleteIfExists(SEEN);
			} catch (IOException e) {
				throw new AssertionError(e);
			}
			SeenStory.reload();
		});
	}

	private static WorldMapScreen map(final ClientGameTestContext context) {
		return context.computeOnClient(mc -> map(mc.gui.screen()));
	}

	private static WorldMapScreen map(final net.minecraft.client.gui.screens.Screen screen) {
		check(screen instanceof WorldMapScreen, "the world map is open: " + screen);
		return (WorldMapScreen) screen;
	}

	private static double[] marker(final ClientGameTestContext context, final String id) {
		return context.computeOnClient(mc -> map(mc.gui.screen()).markerAt(WorldMap.place(id)));
	}

	private static void click(final ClientGameTestContext context, final double[] gui) {
		double scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		context.getInput().setCursorPos(gui[0] * scale, gui[1] * scale);
		context.waitTicks(2);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}

	private static void shot(final ClientGameTestContext context, final String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(Path.of("worldmap")));
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
