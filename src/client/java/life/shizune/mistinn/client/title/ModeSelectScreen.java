package life.shizune.mistinn.client.title;

import life.shizune.mistinn.story.WorldMode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Asked before a new world's settings: the story, or free exploration. Stands on the title
 * screen's shore; the chosen card opens the vanilla world settings, which then create the
 * world in that mode.
 */
public class ModeSelectScreen extends Screen {
	private static final int CARD_W = 172;
	private static final int CARD_H = 118;

	private final Runnable proceed;
	private final Runnable back;
	private final long opened = System.nanoTime();

	/** {@code proceed} opens the world settings; {@code back} returns to where the player came from. */
	public ModeSelectScreen(final Runnable proceed, final Runnable back) {
		super(Component.translatable("mode.mistinn.title"));
		this.proceed = proceed;
		this.back = back;
	}

	@Override
	protected void init() {
		int gap = 14;
		int x = this.width / 2 - CARD_W - gap / 2;
		int y = this.height / 2 - CARD_H / 2 + 10;
		this.addRenderableWidget(new Card(x, y, WorldMode.STORY, "mode.mistinn.story", "mode.mistinn.story.desc"));
		this.addRenderableWidget(new Card(x + CARD_W + gap, y, WorldMode.FREE, "mode.mistinn.free", "mode.mistinn.free.desc"));
	}

	/** For the client game test: pick a mode as a click would. */
	public void pick(final WorldMode mode) {
		WorldMode.choose(mode);
		this.proceed.run();
	}

	@Override
	public void onClose() {
		this.back.run();
	}

	@Override
	protected void extractPanorama(final GuiGraphicsExtractor g, final float a) {
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		ShoreBackdrop.draw(g, this.width, this.height, mouseX, mouseY, (System.nanoTime() - this.opened) / 1.0E9F, false);
		g.fill(0, 0, this.width, this.height, 0x70080A0E);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		Component title = Component.translatable("mode.mistinn.title");
		g.text(this.font, title, (this.width - this.font.width(title)) / 2, this.height / 2 - CARD_H / 2 - 22, 0xFFE4DED0, true);
		Component hint = Component.translatable("mode.mistinn.hint");
		g.text(this.font, hint, (this.width - this.font.width(hint)) / 2, this.height / 2 + CARD_H / 2 + 22, 0xFF8E887C, false);
	}

	/** A card for one mode: a dark panel that lifts and lights its edge in gold under the pointer. */
	private final class Card extends AbstractButton {
		private final WorldMode mode;
		private final Component desc;
		private float hover;
		private long last = System.nanoTime();

		Card(final int x, final int y, final WorldMode mode, final String name, final String desc) {
			super(x, y, CARD_W, CARD_H, Component.translatable(name));
			this.mode = mode;
			this.desc = Component.translatable(desc);
		}

		@Override
		public void onPress(final InputWithModifiers input) {
			ModeSelectScreen.this.pick(this.mode);
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
			int edge = (int) Mth.lerp(h, 0x5A, 0xFF) << 24 | (h > 0.5F ? 0xD89A50 : 0x6B5B3A);
			g.outline(x, y, w, ht, edge);
			g.fill(x, y, x + 3, y + ht, (int) (255 * h) << 24 | 0xD89A50);
			Font font = ModeSelectScreen.this.font;
			g.text(font, this.getMessage(), x + 12, y + 12, h > 0.5F ? 0xFFF2CC80 : 0xFFE4DED0, true);
			int line = y + 30;
			for (FormattedCharSequence part : font.split(this.desc, w - 24)) {
				g.text(font, part, x + 12, line, 0xFFB8B0A0, false);
				line += 11;
			}
		}

		@Override
		protected void updateWidgetNarration(final NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}
}
