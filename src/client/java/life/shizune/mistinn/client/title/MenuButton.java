package life.shizune.mistinn.client.title;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A title-menu entry written into the mist: no box until the pointer comes near, then a dark
 * band opens behind it, a rust-gold line lights at its edge and the words step forward.
 */
public class MenuButton extends AbstractButton {
	private static final int TEXT = 0xFFE4DED0;
	private static final int TEXT_HOT = 0xFFF2CC80;
	private static final int TEXT_OFF = 0xFF6A665E;
	private static final int ACCENT = 0xD89A50;

	private final Runnable action;
	private float hover;
	private long last = System.nanoTime();

	public MenuButton(final int x, final int y, final int width, final Component message, final Runnable action) {
		super(x, y, width, 20, message);
		this.action = action;
	}

	@Override
	public void onPress(final InputWithModifiers input) {
		this.action.run();
	}

	@Override
	protected void extractContents(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		long now = System.nanoTime();
		float dt = Math.min(0.1F, (now - this.last) / 1.0E9F);
		this.last = now;
		float target = this.active && this.isHoveredOrFocused() ? 1.0F : 0.0F;
		// eases in fast and out slower, so passing over the menu leaves a short trail
		this.hover += (target - this.hover) * Math.min(1.0F, dt * (target > this.hover ? 14.0F : 6.0F));
		float h = this.hover;
		int x = this.getX();
		int y = this.getY();
		int band = Math.round(this.getWidth() * (0.55F + 0.45F * h));
		g.fillGradient(x, y, x + band, y + this.getHeight(), (int) (0x90 * h) << 24 | 0x0E1216, (int) (0x60 * h) << 24 | 0x0E1216);
		g.fill(x, y + 3, x + 2, y + this.getHeight() - 3, (int) (255 * h) << 24 | ACCENT);
		int textX = x + 10 + Math.round(6 * h);
		int colour = !this.active ? TEXT_OFF : lerpColour(TEXT, TEXT_HOT, h);
		if (h > 0.05F) {
			g.text(Minecraft.getInstance().font, "◆", textX - 9, y + 6, (int) (255 * h) << 24 | ACCENT, false);
		}
		g.text(Minecraft.getInstance().font, this.getMessage(), textX, y + 6, colour, true);
	}

	private static int lerpColour(final int from, final int to, final float t) {
		int r = (int) Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF);
		int gr = (int) Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF);
		int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
		return 0xFF000000 | r << 16 | gr << 8 | b;
	}

	@Override
	protected void updateWidgetNarration(final NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
