package life.shizune.mistinn.client.text;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Wrapping that keeps the rules of Chinese typesetting: a closing mark (。，、」 and the like) never begins a line, and an
 * opening one (「 and the like) never ends one. Where the game's own wrapping would break there, the line ends a
 * character earlier and the rest is wrapped again from there.
 */
public final class LineBreak {
	/** The marks that close or follow what is before them, none of which may begin a line. */
	private static final String NO_START = "。，、．：；！？」』）〕】〉》…‥～.,:;!?)]}%";
	/** The marks that open what comes after them, none of which may end a line. */
	private static final String NO_END = "「『（〔【〈《([{";

	private LineBreak() {
	}

	/** Whether a line beginning with {@code c} would break the rule. */
	public static boolean closing(final char c) {
		return NO_START.indexOf(c) >= 0;
	}

	/** {@code text} wrapped to {@code width}: each line is a piece of the text, in order, with only spaces between them. */
	public static List<String> lines(final Font font, final String text, final int width) {
		List<String> rows = new ArrayList<>();
		int from = 0;
		while (from < text.length()) {
			while (from < text.length() && text.charAt(from) == ' ') {
				from++;
			}
			if (from >= text.length()) {
				break;
			}
			String rest = text.substring(from);
			int[] end = {-1};
			font.getSplitter().splitLines(rest, width, Style.EMPTY, false, (style, start, stop) -> {
				if (end[0] < 0) {
					end[0] = stop;
				}
			});
			int cut = end[0] < 0 ? rest.length() : Math.max(1, end[0]);
			int next = cut;
			while (next < rest.length() && rest.charAt(next) == ' ') {
				next++;
			}
			if (next < rest.length() && next == cut && closing(rest.charAt(next)) && cut > 1) {
				// the next line would begin with a mark: the last character that is not one comes down with it
				int back = cut - 1;
				while (back > 1 && closing(rest.charAt(back))) {
					back--;
				}
				cut = back;
			}
			// nor may this line end on an opening mark
			while (cut > 1 && cut < rest.length() && NO_END.indexOf(rest.charAt(cut - 1)) >= 0) {
				cut--;
			}
			rows.add(rest.substring(0, cut));
			from += cut;
		}
		return rows;
	}

	/** The same, ready to draw (the text's own styling is not kept: for plain lines). */
	public static List<FormattedCharSequence> split(final Font font, final Component text, final int width) {
		List<FormattedCharSequence> out = new ArrayList<>();
		for (String row : lines(font, text.getString(), width)) {
			out.add(Component.literal(row).getVisualOrderText());
		}
		return out;
	}
}
