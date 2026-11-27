package life.shizune.mistinn.client.story;

import life.shizune.mistinn.story.StoryScript;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/** The line being spoken, typed out under the speaker's name. Shared by the radio and scenes. */
public final class Subtitles {
	private static final int NAME = 0xFFE0A050;
	private static final int TEXT = 0xFFEDE6D8;
	private static final int NARRATION = 0xFFB8B0A0;
	private static final int MAX_WIDTH = 300;

	private Subtitles() {
	}

	/**
	 * Draws the current line centred at {@code centreY}; with {@code panel} it sits on a dark
	 * strip so it reads over the world. Returns false when nothing is being said.
	 */
	public static boolean draw(final GuiGraphicsExtractor g, final int width, final int centreY, final boolean panel) {
		StoryScript.Line line = StoryClient.line();
		if (line == null) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		String full = life.shizune.mistinn.client.ledger.Haunting.text(line.key()).getString();
		int shown = Mth.clamp((int) (StoryClient.lineSeconds(partial) * StoryClient.TYPE_SPEED), 0, full.length());
		boolean narration = StoryScript.NARRATION.equals(line.speaker());
		var wrapped = font.split(Component.literal(full), Math.min(MAX_WIDTH, width - 40));
		int lines = wrapped.size();
		int top = centreY - (lines * 10 + (narration ? 0 : 12)) / 2;
		if (panel) {
			int w = 0;
			for (FormattedCharSequence part : wrapped) {
				w = Math.max(w, font.width(part));
			}
			w = Math.max(w, 80) + 24;
			g.fill(width / 2 - w / 2, top - 6, width / 2 + w / 2, top + lines * 10 + (narration ? 0 : 12) + 4, 0xA0080A0E);
		}
		int y = top;
		if (!narration) {
			Component name = Component.translatable("story.mistinn.speaker." + line.speaker());
			g.text(font, name, width / 2 - font.width(name) / 2, y, NAME, true);
			y += 12;
		}
		// type the line into its wrapped rows, keeping each row centred on its final width
		int left = shown;
		String rest = full;
		for (FormattedCharSequence row : wrapped) {
			int rowChars = rowLength(row);
			String rowText = rest.substring(0, Math.min(rowChars, rest.length()));
			rest = rest.substring(Math.min(rowChars, rest.length()));
			String typed = rowText.substring(0, Mth.clamp(left, 0, rowText.length()));
			left -= rowText.length();
			// a space the wrap swallowed between rows
			while (rest.startsWith(" ")) {
				rest = rest.substring(1);
				left--;
			}
			g.text(font, typed, width / 2 - font.width(rowText) / 2, y, narration ? NARRATION : TEXT, true);
			y += 10;
		}
		return true;
	}

	private static int rowLength(final FormattedCharSequence row) {
		int[] count = {0};
		row.accept((index, style, codePoint) -> {
			count[0] += Character.charCount(codePoint);
			return true;
		});
		return count[0];
	}
}
