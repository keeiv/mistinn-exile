package life.shizune.mistinn.client.path;

import life.shizune.mistinn.path.PathService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * 路的記錄, the journal of a world walking the path of longing: the chapters so far (done, and
 * the one under way with what it asks), and beside them the names of those who fell on the way in
 * the order they fell, who walks it now, and how many of the settlement are left to.
 */
public class PathJournalScreen extends Screen {
	private static final int PANEL_W = 380;
	private static final int PANEL_H = 230;
	private static final int FRAME = 0xF0101418;
	private static final int EDGE = 0xFF3E6E68;
	private static final int TITLE = 0xFF8FC8C0;
	private static final int TEXT = 0xFFEDE6D8;
	private static final int DONE = 0xFF7E786E;
	private static final int DIM = 0xFF9A948A;
	private static final int FALLEN = 0xFFC07A6A;

	public PathJournalScreen() {
		super(Component.translatable("path.mistinn.journal"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		Minecraft mc = Minecraft.getInstance();
		PathService.View view = mc.player == null ? null : mc.player.getAttached(PathService.VIEW);
		Font font = this.font;
		int w = Math.min(PANEL_W, this.width - 16);
		int h = Math.min(PANEL_H, this.height - 16);
		int x = (this.width - w) / 2;
		int y = (this.height - h) / 2;
		g.fill(x, y, x + w, y + h, FRAME);
		g.outline(x, y, w, h, EDGE);
		g.text(font, this.title, x + 10, y + 8, TITLE, true);
		if (view == null) {
			return;
		}

		// the chapters: each one done on a line, the one under way with what it asks
		int left = x + 10;
		int colW = 170;
		int ty = y + 26;
		g.text(font, Component.translatable("path.mistinn.journal.chapters"), left, ty, DIM, false);
		ty += 14;
		PathService.Step now = view.stage();
		if (now == PathService.Step.CUT) {
			// how far it came is not kept once it is cut: only that it was
			g.text(font, Component.translatable(now.key()), left, ty, FALLEN, true);
			ty += 12;
			for (FormattedCharSequence row : font.split(Component.translatable(now.key() + ".hint"), colW - 6)) {
				g.text(font, row, left, ty, DIM, false);
				ty += 10;
			}
		} else {
			for (PathService.Step step : PathService.Step.values()) {
				boolean done = step.ordinal() < now.ordinal();
				if (step == PathService.Step.ENDED || !done && step != now) {
					break;
				}
				Component line = Component.literal(done ? "✔ " : "◆ ").append(Component.translatable("path.mistinn.chapter." + step.chapter));
				g.text(font, line, left, ty, done ? DONE : TITLE, !done);
				ty += 11;
				if (!done) {
					for (FormattedCharSequence row : font.split(Component.translatable(step.key()), colW - 6)) {
						g.text(font, row, left + 6, ty, TEXT, false);
						ty += 10;
					}
					for (FormattedCharSequence row : font.split(Component.translatable(step.key() + ".hint"), colW - 6)) {
						g.text(font, row, left + 6, ty, DIM, false);
						ty += 10;
					}
				}
			}
			if (now == PathService.Step.ENDED) {
				g.text(font, Component.translatable(now.key()), left, ty + 4, TITLE, true);
			}
		}

		// the fallen, in the order they fell, and who walks it now
		int rx = x + colW + 24;
		int rw = w - colW - 34;
		g.fill(rx - 8, y + 24, rx - 7, y + h - 8, 0x60FFFFFF);
		int ry = y + 26;
		g.text(font, Component.translatable("path.mistinn.journal.fallen", view.fallen(), PathService.PEOPLE), rx, ry, DIM, false);
		ry += 14;
		if (view.fallen() == 0) {
			g.text(font, Component.translatable("path.mistinn.end.none"), rx, ry, DONE, false);
			ry += 12;
		}
		// a walker takes up the way when the one before falls: those before them, in turn
		int first = view.walker() - view.fallen() + (now == PathService.Step.CUT ? 1 : 0);
		for (int i = 0; i < view.fallen(); i++) {
			Component name = Component.translatable("path.mistinn.memorial", i + 1).append("  ").append(PathService.name(first + i));
			g.text(font, name, rx, ry, FALLEN, false);
			ry += 11;
		}
		ry += 8;
		if (now != PathService.Step.CUT) {
			g.text(font, PathHud.traits(view), rx, ry, DIM, false);
			ry += 12;
			for (FormattedCharSequence row : font.split(Component.translatable("path.mistinn.journal.walker", PathService.name(view.walker()),
				PathService.PEOPLE - view.fallen()), rw)) {
				g.text(font, row, rx, ry, TEXT, false);
				ry += 10;
			}
		}
	}
}
