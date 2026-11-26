package life.shizune.mistinn.client.guide;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The guide's crafting pages: a contents page (each chapter and where it starts, a click turns
 * there), then one recipe a page, drawn in ink: the grid, the smithing table or the fire, an arrow,
 * what it makes, and the item's own lines on what it is for. Ingredients that may be any of a kind
 * (any planks) show each in turn; hovering a slot names the item.
 */
final class GuideRecipePages {
	private static final int PAGE = HunterGuideScreen.PAGE_WIDTH;

	private GuideRecipePages() {
	}

	/** How many spreads: the contents page, then a page a recipe. */
	static int spreads() {
		return (GuideRecipes.entries().size() + 2) / 2;
	}

	/** The spread showing recipe {@code index}. */
	static int spreadOf(final int index) {
		return (index + 1) / 2;
	}

	static double[] chapterLine(final int left, final int top, final GuideRecipes.Chapter chapter) {
		int row = new ArrayList<>(GuideRecipes.chapters().keySet()).indexOf(chapter);
		return new double[] {left + 20 + 64.0, top + 60 + row * 16 + 4.0};
	}

	/** The spread a click turns to, or -1 when it turns nothing. */
	static int clicked(final int left, final int top, final int open, final double mx, final double my) {
		if (open > 0 && HunterGuideScreen.inside(mx, my, left + 16, top + 176, 18, 12)) {
			return open - 1;
		}
		if (open < spreads() - 1 && HunterGuideScreen.inside(mx, my, left + HunterGuideScreen.W - 34, top + 176, 18, 12)) {
			return open + 1;
		}
		if (open == 0) {
			for (Map.Entry<GuideRecipes.Chapter, Integer> chapter : GuideRecipes.chapters().entrySet()) {
				double[] c = chapterLine(left, top, chapter.getKey());
				if (HunterGuideScreen.inside(mx, my, (int) c[0] - 64, (int) c[1] - 6, 128, 12)) {
					return spreadOf(chapter.getValue());
				}
			}
		}
		return -1;
	}

	static void draw(final HunterGuideScreen screen, final GuiGraphicsExtractor g, final int x, final int y, final int mouseX, final int mouseY) {
		Font font = screen.guideFont();
		List<GuideRecipes.Entry> entries = GuideRecipes.entries();
		int open = screen.recipeSpread();
		for (int side = 0; side < 2; side++) {
			int page = open * 2 + side;
			int px = x + (side == 0 ? 20 : 172);
			int py = y + 18;
			if (page == 0) {
				contents(g, font, x, y, px, py, mouseX, mouseY);
			} else if (page - 1 < entries.size()) {
				recipe(g, font, entries.get(page - 1), px, py, mouseX, mouseY);
			} else {
				continue;
			}
			String number = String.valueOf(page + 1);
			g.text(font, number, px + 64 - font.width(number) / 2, y + 180, HunterGuideScreen.FADED, false);
		}
		if (open > 0) {
			boolean hover = HunterGuideScreen.inside(mouseX, mouseY, x + 16, y + 176, 18, 12);
			g.blit(RenderPipelines.GUI_TEXTURED, HunterGuideScreen.ARROWS, x + 16, y + 176, 0.0F, hover ? 12 : 0, 18, 12, 36, 24);
		}
		if (open < spreads() - 1) {
			boolean hover = HunterGuideScreen.inside(mouseX, mouseY, x + HunterGuideScreen.W - 34, y + 176, 18, 12);
			g.blit(RenderPipelines.GUI_TEXTURED, HunterGuideScreen.ARROWS, x + HunterGuideScreen.W - 34, y + 176, 18.0F, hover ? 12 : 0, 18, 12, 36, 24);
		}
	}

	private static void contents(final GuiGraphicsExtractor g, final Font font, final int x, final int y, final int px, final int py,
		final int mouseX, final int mouseY) {
		g.text(font, Component.translatable("gui.mistinn.guide.recipes.title"), px, py, HunterGuideScreen.INK, false);
		int ty = py + 12;
		for (FormattedCharSequence row : font.split(Component.translatable("gui.mistinn.guide.recipes.intro"), PAGE)) {
			if (ty > py + 32) {
				break;
			}
			g.text(font, row, px, ty, HunterGuideScreen.FADED, false);
			ty += 10;
		}
		for (Map.Entry<GuideRecipes.Chapter, Integer> chapter : GuideRecipes.chapters().entrySet()) {
			double[] c = chapterLine(x, y, chapter.getKey());
			int ly = (int) c[1] - 4;
			boolean hover = HunterGuideScreen.inside(mouseX, mouseY, px, ly - 2, PAGE, 12);
			g.text(font, Component.translatable(chapter.getKey().key()), px, ly, hover ? HunterGuideScreen.RED : HunterGuideScreen.INK, false);
			String number = String.valueOf(chapter.getValue() + 2);
			g.text(font, number, px + PAGE - font.width(number), ly, HunterGuideScreen.FADED, false);
		}
	}

	private static void recipe(final GuiGraphicsExtractor g, final Font font, final GuideRecipes.Entry entry, final int px, final int py,
		final int mouseX, final int mouseY) {
		long now = Util.getMillis();
		ItemStack made = new ItemStack(entry.result(), entry.count());
		g.text(font, Component.translatable(entry.chapter().key()), px, py, HunterGuideScreen.FADED, false);
		Component name = entry.count() > 1 ? Component.translatable("gui.mistinn.guide.recipes.count", made.getHoverName(), entry.count()) : made.getHoverName();
		// a long name takes a second line, and the grid steps down under it
		List<FormattedCharSequence> nameRows = font.split(name, PAGE);
		int gy = py + 14;
		for (int i = 0; i < Math.min(2, nameRows.size()); i++) {
			g.text(font, nameRows.get(i), px, py + 10 + i * 10, HunterGuideScreen.INK, false);
			gy += 10;
		}
		Component note = null;
		switch (entry.kind()) {
			case SHAPED, SHAPELESS -> {
				for (int i = 0; i < 9; i++) {
					GuideRecipes.Ingredient ingredient = entry.grid().get(i);
					slot(g, font, ingredient == null ? ItemStack.EMPTY : ingredient.shown(now), px + (i % 3) * 18, gy + (i / 3) * 18, mouseX, mouseY);
				}
				if (entry.kind() == GuideRecipes.Kind.SHAPELESS) {
					note = Component.translatable("gui.mistinn.guide.recipes.any_order");
				}
			}
			case SMITHING -> {
				for (int i = 0; i < 3; i++) {
					slot(g, font, entry.list().get(i).shown(now), px + i * 18, gy + 18, mouseX, mouseY);
				}
				note = Component.translatable("gui.mistinn.guide.recipes.smithing");
			}
			case COOKING -> {
				slot(g, font, entry.list().getFirst().shown(now), px + 18, gy + 18, mouseX, mouseY);
				List<Component> ways = new ArrayList<>();
				for (String way : GuideRecipes.COOKING) {
					if (entry.cookedIn().contains(way)) {
						ways.add(Component.translatable("gui.mistinn.guide.recipes." + way));
					}
				}
				note = ComponentUtils.formatList(ways, Component.translatable("gui.mistinn.guide.recipes.or"));
			}
		}
		arrow(g, px + 59, gy + 23);
		slot(g, font, made, px + 82, gy + 18, mouseX, mouseY);
		int ty = gy + 58;
		if (note != null) {
			for (FormattedCharSequence row : font.split(note, PAGE)) {
				g.text(font, row, px, ty, HunterGuideScreen.RED, false);
				ty += 10;
			}
			ty += 2;
		}
		// what it is for: the item's own tooltip under its name (its use and story, or a weapon's damage and speed)
		for (Component line : description(made)) {
			for (FormattedCharSequence row : font.split(line, PAGE)) {
				if (ty > py + 146) {
					return;
				}
				g.text(font, row, px, ty, HunterGuideScreen.FADED, false);
				ty += 10;
			}
		}
	}

	/** The item's tooltip lines after its name, as plain ink: the page cannot show the tooltip's own colours. */
	static List<Component> description(final ItemStack stack) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return List.of();
		}
		List<Component> lines = stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.Default.NORMAL);
		List<Component> plain = new ArrayList<>();
		for (int i = 1; i < lines.size(); i++) {
			String text = lines.get(i).getString().strip();
			if (!text.isEmpty()) {
				plain.add(Component.literal(text));
			}
		}
		return plain;
	}

	/** A slot drawn in ink on the page, with its item; hovering it names the item. */
	private static void slot(final GuiGraphicsExtractor g, final Font font, final ItemStack stack, final int sx, final int sy, final int mouseX, final int mouseY) {
		g.fill(sx, sy, sx + 18, sy + 18, 0x66000000 | (HunterGuideScreen.LINE_DARK & 0xFFFFFF));
		g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x40FFF6E0);
		if (stack.isEmpty()) {
			return;
		}
		g.item(stack, sx + 1, sy + 1);
		g.itemDecorations(font, stack, sx + 1, sy + 1);
		if (HunterGuideScreen.inside(mouseX, mouseY, sx, sy, 18, 18)) {
			g.setTooltipForNextFrame(font, stack, mouseX, mouseY);
		}
	}

	/** The arrow from what goes in to what comes out. */
	private static void arrow(final GuiGraphicsExtractor g, final int ax, final int ay) {
		g.fill(ax, ay + 3, ax + 14, ay + 5, HunterGuideScreen.INK);
		for (int i = 0; i < 4; i++) {
			g.fill(ax + 14 + i, ay + i, ax + 15 + i, ay + 8 - i, HunterGuideScreen.INK);
		}
	}
}
