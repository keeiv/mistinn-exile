package life.shizune.mistinn.client.guide;

import java.util.List;
import life.shizune.mistinn.registry.MistinnItems;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/**
 * The guide's contents: a link between the page-turning arrows opens it; the chapters, each by its
 * first page's own title and its page number, down both pages; a click on one turns to it. When there are more
 * chapters than one spread holds, the contents run on over further spreads, turned with the same arrows; they open
 * on the spread that lists the chapter being read, marked.
 */
public final class GuideContents {
	/** The page each chapter starts on. */
	public static final List<Integer> CHAPTERS = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 17, 19, 22, 25, 27, 28, 29, 30, 31, 32, 33, 34, 35,
		36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56);
	/** Down each page: how many lines, from where, how far apart. */
	private static final int PER_PAGE = 16;
	private static final int PER_SPREAD = 2 * PER_PAGE;
	private static final int TOP = 28;
	private static final int ROW = 9;
	/** The link: its middle along the arrows' line. */
	public static final int LINK_Y = 180;

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

	/** How many spreads the contents take. */
	public static int spreads() {
		return Math.max(1, (CHAPTERS.size() + PER_SPREAD - 1) / PER_SPREAD);
	}

	/** The spread of the contents that lists a chapter. */
	public static int spreadOf(final int index) {
		return index / PER_SPREAD;
	}

	/** The chapter a page belongs to: the last one starting on it or before it. */
	public static int chapterOf(final int page) {
		int chapter = 0;
		for (int i = 0; i < CHAPTERS.size(); i++) {
			if (CHAPTERS.get(i) <= page) {
				chapter = i;
			}
		}
		return chapter;
	}

	/** Centre of a chapter's line, on its own spread of the contents. */
	public static double[] line(final int left, final int top, final int index) {
		int side = index % PER_SPREAD / PER_PAGE;
		int row = index % PER_PAGE;
		int px = left + (side == 0 ? 20 : 172);
		return new double[] {px + 64.0, top + TOP + row * ROW + 4.0};
	}

	/** The chapter under a point on this spread of the contents, or -1. */
	static int at(final int left, final int top, final int spread, final double mx, final double my) {
		for (int i = spread * PER_SPREAD; i < Math.min(CHAPTERS.size(), (spread + 1) * PER_SPREAD); i++) {
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

	/** This spread of the contents, the chapter being read ({@code current}) marked in the margin. */
	static void draw(final GuiGraphicsExtractor g, final Font font, final int left, final int top, final int spread, final int current,
		final int mouseX, final int mouseY) {
		if (spread == 0) {
			Component heading = Component.translatable("gui.mistinn.guide.contents");
			g.text(font, heading, left + 20, top + 16, HunterGuideScreen.INK, false);
		}
		int hovered = at(left, top, spread, mouseX, mouseY);
		for (int i = spread * PER_SPREAD; i < Math.min(CHAPTERS.size(), (spread + 1) * PER_SPREAD); i++) {
			int page = CHAPTERS.get(i);
			if (page > MistinnItems.GUIDE_PAGES) {
				continue;
			}
			double[] c = line(left, top, i);
			int px = (int) (c[0] - 64.0);
			int py = (int) (c[1] - 4.0);
			String number = String.valueOf(page);
			int room = 128 - font.width(number) - 6;
			String title = title(page);
			if (font.width(title) > room) {
				// a title too long for its line ends in an ellipsis, not cut off mid-word
				title = font.plainSubstrByWidth(title, room - font.width("…")).stripTrailing() + "…";
			}
			int colour = i == hovered ? HunterGuideScreen.RED : HunterGuideScreen.INK;
			g.text(font, title, px, py, colour, false);
			if (i == current) {
				// where the hunter is reading: a short rule in the margin
				g.fill(px - 6, py + 1, px - 3, py + 7, HunterGuideScreen.RED);
			}
			g.text(font, number, px + 128 - font.width(number), py, i == hovered ? HunterGuideScreen.RED : HunterGuideScreen.FADED, false);
		}
	}
}
