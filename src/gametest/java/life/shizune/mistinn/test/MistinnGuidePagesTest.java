package life.shizune.mistinn.test;

import java.util.List;
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
				}
			}
		});
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
