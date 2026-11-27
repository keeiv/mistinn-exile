package life.shizune.mistinn.client.story;

import life.shizune.mistinn.story.StoryScript;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * A story card: a pixel-art picture on black, the chapter's name above it, and beneath it one
 * line at a time, typed out. A click shows the rest of the line, then the next; Esc skips the card.
 */
public class StoryCardScreen extends Screen {
	/** The pictures are drawn at 320 by 180. */
	private static final int ART_W = 320;
	private static final int ART_H = 180;
	private static final int TEXT = 0xFFEDE6D8;
	private static final int TITLE = 0xFFE0A050;
	private static final int HINT = 0xFF6E6A62;

	private final StoryScript.Card card;
	private final Identifier image;
	private int line;
	private int lineTicks;
	private int ticks;

	public StoryCardScreen(final StoryScript.Card card) {
		super(Component.translatable(card.title().isEmpty() ? card.line(0) : card.title()));
		this.card = card;
		this.image = life.shizune.mistinn.client.ledger.Haunting.picture(card.id());
		// the world map on the title screen opens the places this card has led to
		life.shizune.mistinn.client.worldmap.SeenStory.saw(card.id());
	}

	private String lineText() {
		return life.shizune.mistinn.client.ledger.Haunting.text(this.card.line(this.line)).getString();
	}

	private int typed() {
		return (int) (this.lineTicks / 20.0F * StoryClient.TYPE_SPEED);
	}

	@Override
	public void tick() {
		this.ticks++;
		this.lineTicks++;
	}

	/** Shows the rest of the line, or moves to the next; past the last line the card closes. */
	public void advance() {
		if (this.typed() < this.lineText().length()) {
			this.lineTicks = Math.round(this.lineText().length() / StoryClient.TYPE_SPEED * 20.0F) + 1;
			return;
		}
		this.line++;
		this.lineTicks = 0;
		if (this.line >= this.card.lines()) {
			this.onClose();
		}
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
		g.fill(0, 0, this.width, this.height, 0xFF000000);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		this.extractBackground(g, mouseX, mouseY, a);
		Font font = this.font;
		// the largest whole scale that leaves room for the title and two lines of text
		int scale = Math.max(1, Math.min((this.width - 24) / ART_W, (this.height - 70) / ART_H));
		int w = ART_W * scale;
		int h = ART_H * scale;
		int x = (this.width - w) / 2;
		int y = Math.max(22, (this.height - h - 48) / 2);
		// the picture surfaces out of the dark and settles a few pixels up; the title follows it
		float time = this.ticks + a;
		float fade = Mth.clamp(time / 24.0F, 0.0F, 1.0F);
		float rise = 1.0F - life.shizune.mistinn.client.anim.Motion.outCubic(time / 30.0F);
		int tint = Math.max(4, (int) (255 * fade * fade * (3.0F - 2.0F * fade))) << 24 | 0xFFFFFF;
		g.blit(RenderPipelines.GUI_TEXTURED, this.image, x, y + Math.round(6.0F * rise), 0.0F, 0.0F, w, h, ART_W, ART_H, ART_W, ART_H, tint);

		if (!this.card.title().isEmpty()) {
			Component title = Component.translatable(this.card.title());
			float titleIn = life.shizune.mistinn.client.anim.Motion.outCubic(life.shizune.mistinn.client.anim.Motion.progress(time, 8.0F, 18.0F));
			int titleAlpha = Math.max(4, (int) (255 * titleIn));
			g.text(font, title, (this.width - font.width(title)) / 2, y - 14 + Math.round(3.0F * (1.0F - titleIn)), titleAlpha << 24 | (TITLE & 0xFFFFFF), true);
		}

		if (this.line < this.card.lines() && fade >= 1.0F) {
			String full = this.lineText();
			String shown = full.substring(0, Mth.clamp(this.typed(), 0, full.length()));
			int ty = y + h + 12;
			// wrap on the full line so the text does not jump as it types
			var rows = font.split(Component.literal(full), Math.min(w, this.width - 40));
			int used = 0;
			for (FormattedCharSequence row : rows) {
				int[] count = {0};
				row.accept((index, style, codePoint) -> {
					count[0] += Character.charCount(codePoint);
					return true;
				});
				String rowText = full.substring(Math.min(used, full.length()), Math.min(used + count[0], full.length()));
				String part = shown.length() > used ? shown.substring(used, Math.min(shown.length(), used + count[0])) : "";
				g.text(font, part, (this.width - font.width(rowText)) / 2, ty, TEXT, true);
				used += count[0];
				while (used < full.length() && full.charAt(used) == ' ') {
					used++;
				}
				ty += 11;
			}
		}
		Component hint = Component.translatable("story.mistinn.card.continue").append("  ·  ").append(Component.translatable("story.mistinn.card.skip"));
		g.text(font, hint, this.width - font.width(hint) - 8, this.height - 14, HINT, false);
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}
}
