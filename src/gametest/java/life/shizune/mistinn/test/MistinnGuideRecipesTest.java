package life.shizune.mistinn.test;

import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.client.guide.GuideContents;
import life.shizune.mistinn.client.guide.GuideRecipes;
import life.shizune.mistinn.client.guide.HunterGuideScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;

/** The guide's crafting pages: every recipe of the mod has its page, read from the recipe files themselves. */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnGuideRecipesTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			context.waitTicks(10);
			context.runOnClient(mc -> everyRecipeHasItsPage());
			theCraftingBookmarkAndItsContents(context);
			thePagesContents(context);
		}
	}

	private static void everyRecipeHasItsPage() {
		List<GuideRecipes.Entry> entries = GuideRecipes.entries();
		Set<String> shown = new HashSet<>();
		for (GuideRecipes.Entry entry : entries) {
			shown.add(BuiltInRegistries.ITEM.getKey(entry.result()).toString());
		}
		Path folder = FabricLoader.getInstance().getModContainer(Mistinn.MOD_ID).orElseThrow().findPath("data/mistinn/recipe").orElseThrow();
		int files = 0;
		try (Stream<Path> list = Files.list(folder)) {
			for (Path file : list.filter(p -> p.toString().endsWith(".json")).toList()) {
				files++;
				try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					String result = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("result").get("id").getAsString();
					check(shown.contains(result), "the guide shows what " + file.getFileName() + " makes: " + result);
				}
			}
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
		check(files > 100, "every recipe file was read: " + files);
		long cooking = entries.stream().filter(e -> e.kind() == GuideRecipes.Kind.COOKING).count();
		check(cooking == 6 && entries.stream().filter(e -> e.kind() == GuideRecipes.Kind.COOKING).allMatch(e -> e.cookedIn().size() == 3),
			"cooking in the furnace, the smoker and on the campfire is one page each: " + cooking);
		for (GuideRecipes.Entry entry : entries) {
			for (GuideRecipes.Ingredient ingredient : entry.list()) {
				check(!ingredient.items().isEmpty(), "every ingredient stands for something: " + ingredient.id());
			}
			for (GuideRecipes.Ingredient ingredient : entry.grid()) {
				check(ingredient == null || !ingredient.items().isEmpty(), "every cell stands for something: " + entry.id() + " " + (ingredient == null ? "" : ingredient.id()));
			}
		}
		check(GuideRecipes.chapters().keySet().equals(Set.of(GuideRecipes.Chapter.values())), "every chapter has pages: " + GuideRecipes.chapters());
	}

	private static void theCraftingBookmarkAndItsContents(final ClientGameTestContext context) {
		HunterGuideScreen screen = context.computeOnClient(mc -> {
			HunterGuideScreen opened = new HunterGuideScreen();
			mc.gui.setScreen(opened);
			return opened;
		});
		context.waitTicks(3);
		click(context, context.computeOnClient(mc -> screen.tabCentre(HunterGuideScreen.Tab.RECIPES)));
		check(context.computeOnClient(mc -> screen.tab()) == HunterGuideScreen.Tab.RECIPES, "the crafting bookmark opens the recipes");
		check(context.computeOnClient(mc -> screen.recipeSpread()) == 0, "on the contents");
		context.takeScreenshot("mistinn-guide-recipes-contents");
		int food = GuideRecipes.chapters().get(GuideRecipes.Chapter.FOOD);
		click(context, context.computeOnClient(mc -> screen.chapterLine(GuideRecipes.Chapter.FOOD)));
		check(context.computeOnClient(mc -> screen.recipeSpread()) == (food + 1) / 2, "a chapter's line turns to its first page");
		context.takeScreenshot("mistinn-guide-recipes-food");
		click(context, context.computeOnClient(mc -> screen.chapterLine(GuideRecipes.Chapter.WEAPONS)));
		check(context.computeOnClient(mc -> screen.recipeSpread()) == (food + 1) / 2, "and the contents' lines only work on the contents page");
		click(context, context.computeOnClient(mc -> screen.nextPage()));
		check(context.computeOnClient(mc -> screen.recipeSpread()) == (food + 1) / 2 + 1, "the arrow turns the crafting pages too");
		context.runOnClient(mc -> mc.gui.setScreen(null));
	}

	/** The pages' contents: the link between the arrows opens it, a chapter's line turns to that chapter, and the link closes it again. */
	private static void thePagesContents(final ClientGameTestContext context) {
		HunterGuideScreen screen = context.computeOnClient(mc -> {
			HunterGuideScreen opened = new HunterGuideScreen();
			mc.gui.setScreen(opened);
			return opened;
		});
		context.waitTicks(3);
		check(context.computeOnClient(mc -> screen.tab()) == HunterGuideScreen.Tab.PAGES && !screen.contentsOpen(), "the guide opens on its pages");
		click(context, context.computeOnClient(mc -> screen.contentsLink()));
		check(context.computeOnClient(mc -> screen.contentsOpen()), "the link opens the contents");
		context.takeScreenshot("mistinn-guide-contents");
		click(context, context.computeOnClient(mc -> screen.contentsLine(35)));
		check(context.computeOnClient(mc -> !screen.contentsOpen() && screen.spread() == 17), "a chapter's line turns to it: " + context.computeOnClient(mc -> screen.spread()));
		click(context, context.computeOnClient(mc -> screen.contentsLink()));
		click(context, context.computeOnClient(mc -> screen.contentsLink()));
		check(context.computeOnClient(mc -> !screen.contentsOpen() && screen.spread() == 17), "and the link closes it again, where the hunter was");
		// more chapters than one spread holds: the contents turn with the same arrows
		check(GuideContents.spreads() > 1, "the contents run over more than one spread");
		click(context, context.computeOnClient(mc -> screen.contentsLink()));
		check(context.computeOnClient(mc -> screen.contentsSpread()) == 0, "they open on the spread that lists the chapter being read");
		click(context, context.computeOnClient(mc -> screen.nextPage()));
		check(context.computeOnClient(mc -> screen.contentsOpen() && screen.contentsSpread() == 1), "the arrow turns the contents, not the pages");
		context.takeScreenshot("mistinn-guide-contents-2");
		click(context, context.computeOnClient(mc -> screen.contentsLine(52)));
		check(context.computeOnClient(mc -> !screen.contentsOpen() && screen.spread() == 25), "a chapter on the later spread turns to it");
		click(context, context.computeOnClient(mc -> screen.contentsLink()));
		check(context.computeOnClient(mc -> screen.contentsSpread()) == 1, "and the contents open there again, on its spread");
		click(context, context.computeOnClient(mc -> screen.previousPage()));
		check(context.computeOnClient(mc -> screen.contentsOpen() && screen.contentsSpread() == 0), "the other arrow turns them back");
		context.runOnClient(mc -> mc.gui.setScreen(null));
	}

	private static void click(final ClientGameTestContext context, final double[] gui) {
		double scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		context.getInput().setCursorPos(gui[0] * scale, gui[1] * scale);
		context.waitTicks(2);
		context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
