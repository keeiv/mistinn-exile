package life.shizune.mistinn.client.title;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * A card for one choice on the world-creation screens: a dark panel that lifts and lights its
 * edge under the pointer, with a title, an optional tag beside it, and a description.
 */
final class ChoiceCard extends AbstractButton {
	/** The colours of a card: its lit edge, its edge at rest, and its title when lit. */
	record Palette(int accent, int rest, int title) {
	}

	/** The gold of the mode cards. */
	static final Palette GOLD = new Palette(0xD89A50, 0x6B5B3A, 0xF2CC80);
	/** Rust red, for the hardest choice. */
	static final Palette RUST = new Palette(0xC0503A, 0x6E3A2E, 0xF2A894);

	private final Component desc;
	private final @Nullable Component tag;
	private final Palette palette;
	private final Runnable press;
	private float hover;
	private long last = System.nanoTime();

	ChoiceCard(final int x, final int y, final int width, final int height, final Component title, final Component desc,
		final @Nullable Component tag, final Palette palette, final Runnable press) {
		super(x, y, width, height, title);
		this.desc = desc;
		this.tag = tag;
		this.palette = palette;
		this.press = press;
	}

	@Override
	public void onPress(final InputWithModifiers input) {
		this.press.run();
	}

	@Override
	protected void extractContents(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		long now = System.nanoTime();
		float dt = Math.min(0.1F, (now - this.last) / 1.0E9F);
		this.last = now;
		float target = this.isHoveredOrFocused() ? 1.0F : 0.0F;
		this.hover += (target - this.hover) * Math.min(1.0F, dt * 10.0F);
		float h = this.hover;
		int x = this.getX();
		int y = this.getY() - Math.round(3 * h);
		int w = this.getWidth();
		int ht = this.getHeight();
		g.fill(x, y, x + w, y + ht, 0xD8101418);
		int edge = (int) Mth.lerp(h, 0x5A, 0xFF) << 24 | (h > 0.5F ? this.palette.accent() : this.palette.rest());
		g.outline(x, y, w, ht, edge);
		g.fill(x, y, x + 3, y + ht, (int) (255 * h) << 24 | this.palette.accent());
		Font font = Minecraft.getInstance().font;
		int inset = w < 120 ? 7 : 12;
		int textWidth = w - 2 * inset;
		int line = y + 11;
		int titleColour = h > 0.5F ? 0xFF000000 | this.palette.title() : 0xFFE4DED0;
		for (FormattedCharSequence title : font.split(this.getMessage(), textWidth)) {
			g.text(font, title, x + inset, line, titleColour, true);
			line += 10;
		}
		if (this.tag != null) {
			g.text(font, this.tag, x + inset, line, 0xFF8E887C, false);
			line += 10;
		}
		line += 7;
		for (FormattedCharSequence part : font.split(this.desc, textWidth)) {
			g.text(font, part, x + inset, line, 0xFFB8B0A0, false);
			line += 11;
		}
	}

	@Override
	protected void updateWidgetNarration(final NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
