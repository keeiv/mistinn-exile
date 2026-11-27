package life.shizune.mistinn.client.guide;

import java.util.List;
import life.shizune.mistinn.registry.MistinnItems;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/**
 * The guide's contents: a link between the page-turning arrows opens it; the chapters, each by its
 * first page's own title and its page number, down both pages; a click on one turns to it.
 */
final class GuideContents {
	/** The page each chapter starts on. */
	static final List<Integer> CHAPTERS = List.of(1, 2, 4, 6, 7, 9, 10, 12, 17, 19, 22, 25, 28, 29, 30, 32, 33, 34, 35, 36, 37, 39, 41, 43, 45, 47, 48, 49, 50, 51);
	/** Down each page: how many lines, from where, how far apart. */
	private static final int PER_PAGE = 14;
	private static final int TOP = 30;
	private static final int ROW = 10;
	/** The link: its middle along the arrows' line. */
	private static final int LINK_Y = 180;

	private GuideContents() {
	}

	/** A chapter's title: the first line of its first page, without the brackets round it. */
	static String title(final int page) {
		String text = Language.getInstance().getOrDefault("item.mistinn.hunter_guide.page." + page);
		String first = text.split("\n", 2)[0].strip();
		if (first.startsWith("【") && first.endsWith("】") || first.startsWith("[") && first.endsWith("]")) {
			first = first.substring(1, first.length() - 1);
		}
		return first;
	}

	/** Top-left of the link that opens (or closes) the contents. */
	static int[] link(final Font font, final int left, final int top) {
		Component label = Component.translatable("gui.mistinn.guide.contents");
		return new int[] {left + HunterGuideScreen.W / 2 - font.width(label) / 2, top + LINK_Y - 4, font.width(label), 10};
	}

	/** Centre of a chapter's line. */
	static double[] line(final int left, final int top, final int index) {
		int side = index / PER_PAGE;
		int row = index % PER_PAGE;
		int px = left + (side == 0 ? 20 : 172);
		return new double[] {px + 64.0, top + TOP + row * ROW + 4.0};
	}

	/** The chapter under a point, or -1. */
	static int at(final int left, final int top, final double mx, final double my) {
		for (int i = 0; i < CHAPTERS.size(); i++) {
			double[] c = line(left, top, i);
			if (Math.abs(mx - c[0]) <= 64.0 && Math.abs(my - c[1]) <= ROW / 2.0) {
				return i;
			}
		}
		return -1;
	}

	static void drawLink(final GuiGraphicsExtractor g, final Font font, final int left, final int top, final int mouseX, final int mouseY) {
		int[] at = link(font, left, top);
		boolean hover = mouseX >= at[0] && mouseX < at[0] + at[2] && mouseY >= at[1] && mouseY < at[1] + at[3];
		g.text(font, Component.translatable("gui.mistinn.guide.contents"), at[0], at[1] + 1, hover ? HunterGuideScreen.RED : HunterGuideScreen.FADED, false);
	}

	static void draw(final GuiGraphicsExtractor g, final Font font, final int left, final int top, final int mouseX, final int mouseY) {
		Component heading = Component.translatable("gui.mistinn.guide.contents");
		g.text(font, heading, left + 20, top + 16, HunterGuideScreen.INK, false);
		int hovered = at(left, top, mouseX, mouseY);
		for (int i = 0; i < CHAPTERS.size(); i++) {
			int page = CHAPTERS.get(i);
			if (page > MistinnItems.GUIDE_PAGES) {
				continue;
			}
			double[] c = line(left, top, i);
			int px = (int) (c[0] - 64.0);
			int py = (int) (c[1] - 4.0);
			String number = String.valueOf(page);
			int room = 128 - font.width(number) - 6;
			String title = font.plainSubstrByWidth(title(page), room);
			int colour = i == hovered ? HunterGuideScreen.RED : HunterGuideScreen.INK;
			g.text(font, title, px, py, colour, false);
			g.text(font, number, px + 128 - font.width(number), py, i == hovered ? HunterGuideScreen.RED : HunterGuideScreen.FADED, false);
		}
	}
}
