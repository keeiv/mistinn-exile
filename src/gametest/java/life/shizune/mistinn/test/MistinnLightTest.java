package life.shizune.mistinn.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.client.ContentLevel;
import life.shizune.mistinn.client.achievement.ClientAchievements;
import life.shizune.mistinn.client.ledger.Haunting;
import life.shizune.mistinn.client.story.StoryCardScreen;
import life.shizune.mistinn.story.StoryScript;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.server.level.ServerPlayer;

/**
 * 輕鬆: the lightest content level. Only the story is touched (CONTRIBUTING, "what counts as heavy"): a heavy story line
 * reads its gentle version, a heavy story picture is left out and its card is words alone, and names stay as written;
 * no gentle line sits on a name, an item or the guide. The other levels read every line and picture as written. And
 * every achievement can be earned at the lightest level.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnLightTest implements FabricClientGameTest {
	private static final String LIGHT = ".light";
	/** A heavy line (fear coming for the reader), and a name that must not change. */
	private static final String QUOTE = "death.mistinn.quote.execution.0";
	private static final String NAME = "mode.mistinn.path";
	private static final String HEAVY_PICTURE = "path_stitched";

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		ContentLevel was = context.computeOnClient(mc -> ContentLevel.current());
		try {
			Map<String, String> zh = context.computeOnClient(mc -> read(mc, "zh_tw"));
			Map<String, String> en = context.computeOnClient(mc -> read(mc, "en_us"));
			// the language the game has loaded (a test before may have changed the setting without reloading): told by a name
			Map<String, String> shown = context.computeOnClient(mc -> Language.getInstance().getOrDefault(NAME).equals(zh.get(NAME)) ? zh : en);
			int gentle = 0;
			for (String key : zh.keySet()) {
				String base = key.endsWith(LIGHT) ? key.substring(0, key.length() - LIGHT.length()) : null;
				if (base == null || !zh.containsKey(base)) {
					continue;
				}
				gentle++;
				check(ContentLevel.story(base), "only the story has gentle lines, not " + base);
				check(en.containsKey(key), "and each is in both languages: " + key);
				check(!zh.get(key).equals(zh.get(base)), "and says it differently: " + key);
			}
			check(gentle >= 6, "the heavy story lines have gentle versions (" + gentle + ")");

			context.runOnClient(mc -> ContentLevel.set(ContentLevel.LIGHT));
			String told = context.computeOnClient(mc -> Language.getInstance().getOrDefault(QUOTE));
			check(told.equals(shown.get(QUOTE + LIGHT)), "at Gentle a heavy line reads its gentle version (" + told + ")");
			check(context.computeOnClient(mc -> Language.getInstance().getOrDefault(NAME)).equals(shown.get(NAME)), "and names stay as written");
			check(context.computeOnClient(mc -> Language.getInstance().getOrDefault("settings.mistinn.content")).equals(shown.get("settings.mistinn.content")),
				"the settings keep their own words (the level's name is not the setting's)");
			check(context.computeOnClient(mc -> Haunting.picture(HEAVY_PICTURE) == null && !Haunting.shown(HEAVY_PICTURE)),
				"a heavy story picture is left out, on its card and on the world map");
			check(context.computeOnClient(mc -> Haunting.picture("stopped") != null && Haunting.picture("thirteen_end") != null),
				"the other pictures stay, sad ones included");
			context.runOnClient(mc -> mc.gui.setScreen(new StoryCardScreen(StoryScript.PATH_STITCHED, null)));
			context.waitTicks(60);
			context.takeScreenshot(TestScreenshotOptions.of("light-card").disableCounterPrefix().withDestinationDir(Path.of("photos", "light")));
			check(context.computeOnClient(mc -> mc.gui.screen() instanceof StoryCardScreen), "and its card is shown as words alone");
			context.runOnClient(mc -> mc.gui.setScreen(null));

			for (ContentLevel level : List.of(ContentLevel.NORMAL, ContentLevel.FULL)) {
				context.runOnClient(mc -> ContentLevel.set(level));
				check(context.computeOnClient(mc -> Language.getInstance().getOrDefault(QUOTE)).equals(shown.get(QUOTE)),
					"at " + level.id + " the line is as written");
				check(context.computeOnClient(mc -> Haunting.picture(HEAVY_PICTURE) != null && Haunting.shown(HEAVY_PICTURE)), "and the picture is shown");
			}

			context.runOnClient(mc -> ContentLevel.set(ContentLevel.LIGHT));
			try (TestSingleplayerContext world = context.worldBuilder().create()) {
				world.getConnection().waitForChunksRender();
				List<String> ids = world.getServer().computeOnServer(server -> server.getAdvancements().getAllAdvancements().stream()
					.map(holder -> holder.id())
					.filter(id -> id.getNamespace().equals(Mistinn.MOD_ID) && id.getPath().startsWith("hunt/") && !AchievementService.ROOTS.contains(id))
					.map(id -> id.getPath().substring("hunt/".length())).sorted().toList());
				check(ids.size() >= 40, "the hunt tab lists its achievements (" + ids.size() + ")");
				world.getServer().runOnServer(server -> {
					ServerPlayer hunter = server.getPlayerList().getPlayers().getFirst();
					AchievementService.clear(hunter);
					for (String id : ids) {
						AchievementService.grant(hunter, id);
					}
				});
				context.waitTicks(20);
				for (String id : ids) {
					check(context.computeOnClient(mc -> ClientAchievements.has(id)), "at Gentle, " + id + " is earned");
				}
			}
		} finally {
			context.runOnClient(mc -> ContentLevel.set(was));
		}
	}

	/** This mod's lines in one language, as the language file has them. */
	private static Map<String, String> read(final Minecraft mc, final String code) {
		Map<String, String> lines = new HashMap<>();
		mc.getResourceManager().getResource(Mistinn.id("lang/" + code + ".json")).ifPresent(resource -> {
			try (Reader reader = resource.openAsReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				json.entrySet().forEach(entry -> lines.put(entry.getKey(), entry.getValue().getAsString()));
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		});
		check(!lines.isEmpty(), "the " + code + " lines are read");
		return lines;
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn light] ok: " + what);
	}
}
