package life.shizune.mistinn.client.title;

import java.util.function.Consumer;
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
 * Asked at world creation. Story and free exploration open the world settings; training opens
 * an introductory course that leads to either destination.
 */
public class ModeSelectScreen extends Screen {
	private static final int CARD_W = 172;
	private static final int CARD_H = 118;

	private final Consumer<WorldMode> proceed;
	private final Runnable back;
	private final long opened = System.nanoTime();

	public ModeSelectScreen(final Consumer<WorldMode> proceed, final Runnable back) {
		super(Component.translatable("mode.mistinn.title"));
		this.proceed = proceed;
		this.back = back;
	}

	@Override
	protected void init() {
		int gap = 14;
		int cardWidth = Math.min(CARD_W, (this.width - 2 * gap - 24) / 3);
		int x = (this.width - 3 * cardWidth - 2 * gap) / 2;
		int y = this.height / 2 - CARD_H / 2 + 10;
		this.addRenderableWidget(new Card(x, y, cardWidth, WorldMode.STORY));
		this.addRenderableWidget(new Card(x + cardWidth + gap, y, cardWidth, WorldMode.FREE));
		this.addRenderableWidget(new Card(x + 2 * (cardWidth + gap), y, cardWidth, WorldMode.TRAINING));
	}

	/** For the client game test: pick a mode as a click would. */
	public void pick(final WorldMode mode) {
		this.proceed.accept(mode);
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

		Card(final int x, final int y, final int width, final WorldMode mode) {
			super(x, y, width, CARD_H, Component.translatable("mode.mistinn." + mode.id));
			this.mode = mode;
			this.desc = Component.translatable("mode.mistinn." + mode.id + ".desc");
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
			int inset = w < 120 ? 7 : 12;
			int textWidth = w - 2 * inset;
			int line = y + 11;
			for (FormattedCharSequence title : font.split(this.getMessage(), textWidth)) {
				g.text(font, title, x + inset, line, h > 0.5F ? 0xFFF2CC80 : 0xFFE4DED0, true);
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
}
