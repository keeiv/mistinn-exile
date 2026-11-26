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
