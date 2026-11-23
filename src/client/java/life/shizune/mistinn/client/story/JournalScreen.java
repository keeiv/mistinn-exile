package life.shizune.mistinn.client.story;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.story.StoryProgress;
import life.shizune.mistinn.story.StoryScript;
import life.shizune.mistinn.story.StoryStep;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * 任務日誌: the objectives so far, chapter by chapter, and every radio call heard, to read again.
 * The log scrolls with the mouse wheel.
 */
public class JournalScreen extends Screen {
	private static final int PANEL_W = 380;
	private static final int PANEL_H = 230;
	private static final int FRAME = 0xF0101418;
	private static final int EDGE = 0xFF6B5B3A;
	private static final int GOLD = 0xFFE0A050;
	private static final int TEXT = 0xFFEDE6D8;
	private static final int DONE = 0xFF7E786E;
	private static final int DIM = 0xFF9A948A;

	/** How far the radio log is scrolled; it opens on the latest lines. */
	private double scroll = Double.MAX_VALUE;

	public JournalScreen() {
		super(Component.translatable("story.mistinn.journal"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
		this.scroll = Math.max(0.0, this.scroll - scrollY * 12.0);
		return true;
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		Font font = this.font;
		int w = Math.min(PANEL_W, this.width - 16);
		int h = Math.min(PANEL_H, this.height - 16);
		int x = (this.width - w) / 2;
		int y = (this.height - h) / 2;
		g.fill(x, y, x + w, y + h, FRAME);
		g.outline(x, y, w, h, EDGE);
		g.text(font, this.title, x + 10, y + 8, GOLD, true);

		StoryProgress progress = StoryClient.progress();
		if (progress == null) {
			g.text(font, Component.translatable("story.mistinn.journal.free"), x + 10, y + 28, DIM, false);
			return;
		}

		// objectives: a finished chapter on one line, the chapter under way step by step
		int left = x + 10;
		int colW = 150;
		int ty = y + 26;
		g.text(font, Component.translatable("story.mistinn.journal.objectives"), left, ty, DIM, false);
		ty += 14;
		StoryStep now = progress.step();
		for (int chapter = 0; chapter <= now.chapter; chapter++) {
			if (chapter < now.chapter) {
				g.text(font, Component.literal("✔ ").append(Component.translatable("story.mistinn.chapter." + chapter)), left, ty, DONE, false);
				ty += 12;
				continue;
			}
			g.text(font, Component.translatable("story.mistinn.chapter." + chapter), left, ty, GOLD, true);
			ty += 11;
			for (StoryStep step : StoryStep.values()) {
				if (step.chapter != chapter || step.ordinal() > now.ordinal()) {
					continue;
				}
				boolean done = step.ordinal() < now.ordinal();
				Component text = Component.literal(done ? "✔ " : "◆ ").append(Component.translatable(step.key()));
				for (FormattedCharSequence row : font.split(text, colW)) {
					g.text(font, row, left + 4, ty, done ? DONE : TEXT, false);
					ty += 10;
				}
			}
			ty += 5;
		}

		// the radio log, scrolling inside its column
		int logX = x + colW + 24;
		int logW = w - colW - 34;
		int logTop = y + 26;
		g.fill(logX - 8, logTop - 2, logX - 7, y + h - 8, 0x60FFFFFF);
		g.text(font, Component.translatable("story.mistinn.journal.radio"), logX, logTop, DIM, false);
		List<Row> rows = new ArrayList<>();
		for (String sequence : StoryScript.sequences()) {
			if (!progress.heard().contains(sequence)) {
				continue;
			}
			for (StoryScript.Line line : StoryScript.sequence(sequence)) {
				Component said = Component.translatable(line.key());
				boolean narration = StoryScript.NARRATION.equals(line.speaker());
				Component text = narration ? said : Component.empty()
					.append(Component.translatable("story.mistinn.speaker." + line.speaker()).append("：").withColor(GOLD))
					.append(said.copy().withColor(TEXT));
				for (FormattedCharSequence row : font.split(text, logW)) {
					rows.add(new Row(row, narration ? DIM : TEXT));
				}
			}
			rows.add(new Row(FormattedCharSequence.EMPTY, TEXT));
		}
		int viewTop = logTop + 14;
		int viewBottom = y + h - 8;
		if (rows.isEmpty()) {
			g.text(font, Component.translatable("story.mistinn.journal.empty"), logX, viewTop, DONE, false);
			return;
		}
		int contentH = rows.size() * 10;
		this.scroll = Mth.clamp(this.scroll, 0.0, Math.max(0.0, contentH - (viewBottom - viewTop)));
		g.enableScissor(logX, viewTop, logX + logW, viewBottom);
		int ry = viewTop - (int) this.scroll;
		for (Row row : rows) {
			if (ry > viewTop - 10 && ry < viewBottom) {
				g.text(font, row.text(), logX, ry, row.colour(), false);
			}
			ry += 10;
		}
		g.disableScissor();
	}

	private record Row(FormattedCharSequence text, int colour) {
	}
}
