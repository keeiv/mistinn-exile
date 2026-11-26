package life.shizune.mistinn.client.path;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.path.PathService;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The way's end. Seen: out of the dark comes a sky over the sea, waves running in under a pale
 * horizon, and over it the names of those who fell on the way, one at a time, and last the one
 * who saw it for them. Cut: only fog, and the twelve names. A click shows everything; another
 * closes it.
 */
public class PathEndScreen extends Screen {
	/** Seconds: the dark lifting, the title, the first name, and between names. */
	private static final float DAWN = 2.0F;
	private static final float TITLE = 1.4F;
	private static final float NAMES = 3.2F;
	private static final float EACH = 0.55F;
	private static final int TEXT = 0xFFEDE6D8;
	private static final int NAME = 0xFFC9D6DA;
	private static final int HINT = 0xFF8A9298;

	private final boolean sea;
	private final List<Integer> fallen;
	private final int walker;
	private final long opened = System.nanoTime();
	private float skipped = -1.0F;

	public PathEndScreen(final boolean sea, final List<Integer> fallen, final int walker) {
		super(Component.translatable(sea ? "path.mistinn.sea" : "path.mistinn.cut"));
		this.sea = sea;
		this.fallen = List.copyOf(fallen);
		this.walker = walker;
	}

	public boolean sea() {
		return this.sea;
	}

	private float seconds() {
		float real = (System.nanoTime() - this.opened) / 1.0E9F;
		return this.skipped >= 0.0F ? Math.max(real, this.skipped) : real;
	}

	/** When everything has come up: the last name, then the closing line. */
	private float whole() {
		return NAMES + this.fallen.size() * EACH + 1.2F;
	}

	/** Shows everything at once, or, everything shown, closes. */
	public void advance() {
		if (this.seconds() < this.whole()) {
			this.skipped = this.whole() + 0.01F;
			return;
		}
		this.onClose();
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		this.advance();
		return true;
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		int key = event.key();
		if (key == com.mojang.blaze3d.platform.InputConstants.KEY_SPACE || key == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN) {
			this.advance();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		float t = this.seconds();
		int w = this.width;
		int h = this.height;
		int horizon = Math.round(h * 0.56F);
		if (this.sea) {
			g.fillGradient(0, 0, w, horizon, 0xFF0C1626, 0xFF8FB4C6);
			g.fillGradient(0, horizon, w, h, 0xFF3D6F8C, 0xFF0B2236);
			// the waves: thin bright lines, closer together toward the horizon, running in
			for (int i = 0; i < 26; i++) {
				float depth = (i + (t * 0.35F) % 1.0F) / 26.0F;
				int y = horizon + Math.round((h - horizon) * depth * depth);
				int alpha = Math.round(30 + 70 * (1.0F - depth));
				int reach = Math.round(w * (0.18F + 0.5F * depth));
				int x = Math.round((w - reach) / 2.0F + Mth.sin(t * 0.7F + i * 1.9F) * w * 0.18F);
				g.fill(x, y, x + reach, y + 1, alpha << 24 | 0xDCEBF2);
			}
			// the sun's road on the water
			g.fillGradient(w / 2 - 2, horizon, w / 2 + 2, h, 0x70F4EAD0, 0x00F4EAD0);
		} else {
			g.fillGradient(0, 0, w, h, 0xFF15171A, 0xFF3A3E42);
			// fog drifting up and down in soft bands
			for (int i = 0; i < 7; i++) {
				int y = Math.round(h * (0.16F + i * 0.12F) + Mth.sin(t * 0.25F + i * 2.3F) * 10.0F);
				g.fillGradient(0, y, w, y + 14, 0x00A0A6AA, 0x1EA0A6AA);
				g.fillGradient(0, y + 14, w, y + 28, 0x1EA0A6AA, 0x00A0A6AA);
			}
		}
		// out of the dark
		int dark = Math.round(255 * (1.0F - Mth.clamp(t / DAWN, 0.0F, 1.0F)));
		if (dark > 0) {
			g.fill(0, 0, w, h, dark << 24);
		}
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		this.extractBackground(g, mouseX, mouseY, a);
		Font font = this.font;
		float t = this.seconds();
		int top = Math.round(this.height * 0.14F);
		int title = fade(t, TITLE);
		if (title > 0) {
			Component words = Component.translatable(this.sea ? "path.mistinn.sea" : "path.mistinn.cut");
			g.pose().pushMatrix();
			g.pose().translate(this.width / 2.0F, top);
			g.pose().scale(2.0F, 2.0F);
			g.text(font, words, -font.width(words) / 2, 0, title << 24 | (TEXT & 0xFFFFFF), true);
			g.pose().popMatrix();
		}
		int y = top + 34;
		int header = fade(t, NAMES - 0.6F);
		if (header > 0) {
			Component roll = this.fallen.isEmpty() ? Component.translatable("path.mistinn.end.none") : Component.translatable("path.mistinn.roll");
			g.text(font, roll, (this.width - font.width(roll)) / 2, y, header << 24 | (HINT & 0xFFFFFF), true);
		}
		y += 16;
		// the names, a row at a time, each coming up after the last
		List<List<Integer>> rows = this.rows(font);
		int shown = 0;
		for (List<Integer> row : rows) {
			int width = 0;
			for (int name : row) {
				width += font.width(PathService.name(name)) + 14;
			}
			int x = (this.width - width + 14) / 2;
			for (int name : row) {
				Component each = PathService.name(name);
				int alpha = fade(t, NAMES + shown * EACH);
				if (alpha > 0) {
					g.text(font, each, x, y, alpha << 24 | (NAME & 0xFFFFFF), true);
				}
				x += font.width(each) + 14;
				shown++;
			}
			y += 13;
		}
		y += 10;
		int closing = fade(t, NAMES + this.fallen.size() * EACH + 0.4F);
		if (closing > 0) {
			Component line = this.sea ? Component.translatable("path.mistinn.end.sea", PathService.name(this.walker)) : Component.translatable("path.mistinn.end.cut");
			for (var part : font.split(line, Math.min(320, this.width - 40))) {
				g.text(font, part, (this.width - font.width(part)) / 2, y, closing << 24 | (TEXT & 0xFFFFFF), true);
				y += 11;
			}
		}
		if (t >= this.whole()) {
			Component hint = Component.translatable("path.mistinn.end.hint");
			g.text(font, hint, this.width - font.width(hint) - 8, this.height - 14, HINT, false);
		}
	}

	private List<List<Integer>> rows(final Font font) {
		List<List<Integer>> rows = new ArrayList<>();
		List<Integer> row = new ArrayList<>();
		int width = 0;
		int most = Math.min(300, this.width - 40);
		for (int name : this.fallen) {
			int each = font.width(PathService.name(name)) + 14;
			if (!row.isEmpty() && width + each > most) {
				rows.add(row);
				row = new ArrayList<>();
				width = 0;
			}
			row.add(name);
			width += each;
		}
		if (!row.isEmpty()) {
			rows.add(row);
		}
		return rows;
	}

	/** How far something has faded in, 0 to 255, that starts coming up at {@code from} seconds. */
	private static int fade(final float t, final float from) {
		int alpha = Math.round(255 * Mth.clamp((t - from) / 0.8F, 0.0F, 1.0F));
		// text with almost no alpha is drawn opaque: hold it back until it is really there
		return alpha < 5 ? 0 : alpha;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
