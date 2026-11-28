package life.shizune.mistinn.test;

import java.util.List;
import life.shizune.mistinn.client.guide.GuideContents;
import life.shizune.mistinn.client.guide.HunterGuideScreen;
import life.shizune.mistinn.registry.MistinnItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.network.chat.Component;

/**
 * Every page of the hunter's guide is written in both languages and fits on its page: wrapped to the page's
 * width in the game's own font, it never runs past the rows above the page number.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnGuidePagesTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel") || Boolean.getBoolean("mistinn.bossreel")) {
			return;
		}
		context.runOnClient(mc -> {
			for (String code : List.of("zh_tw", "en_us")) {
				ClientLanguage language = ClientLanguage.loadFrom(mc.getResourceManager(), List.of(code), false);
				for (int page = 1; page <= MistinnItems.GUIDE_PAGES; page++) {
					String key = "item.mistinn.hunter_guide.page." + page;
					String text = language.getOrDefault(key, "");
					check(!text.isEmpty(), code + " has no page " + page);
					int rows = mc.font.split(Component.literal(text), HunterGuideScreen.PAGE_WIDTH).size();
					check(rows <= HunterGuideScreen.PAGE_ROWS,
						code + " page " + page + " runs to " + rows + " rows, past the " + HunterGuideScreen.PAGE_ROWS + " a page holds");
					// the epic's own copy of a page fits as well
					if (language.has(key + ".epic")) {
						int epic = mc.font.split(Component.literal(language.getOrDefault(key + ".epic", "")), HunterGuideScreen.PAGE_WIDTH).size();
						check(epic <= HunterGuideScreen.PAGE_ROWS, code + " the epic's page " + page + " runs to " + epic + " rows");
					}
				}
				for (int page : new int[] {1, 58, 59}) {
					check(language.has("item.mistinn.hunter_guide.page." + page + ".epic"), code + " the epic rewrites page " + page);
				}
			}
			// the contents hold every chapter, in order: each on a line of its own on its spread, none down over the link
			java.util.Set<String> lines = new java.util.HashSet<>();
			for (int i = 0; i < GuideContents.CHAPTERS.size(); i++) {
				check(GuideContents.CHAPTERS.get(i) <= MistinnItems.GUIDE_PAGES, "chapter " + i + " starts past the last page");
				check(i == 0 || GuideContents.CHAPTERS.get(i) > GuideContents.CHAPTERS.get(i - 1), "chapter " + i + " is out of order");
				double[] at = GuideContents.line(0, 0, i);
				check(lines.add(GuideContents.spreadOf(i) + ":" + (int) at[0] + "," + (int) at[1]), "contents line " + i + " lies over another");
				check(at[1] + 4.0 < GuideContents.LINK_Y - 4, "contents line " + i + " runs down over the link");
			}
		});
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
