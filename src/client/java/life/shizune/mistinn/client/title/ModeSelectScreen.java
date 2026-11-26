package life.shizune.mistinn.client.title;

import java.util.function.BiConsumer;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.story.WorldMode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Asked at world creation. Story and free exploration open the world settings; training opens
 * an introductory course that leads to either destination. Story and training ask how hard the
 * world should be first; free exploration is normal; the path of longing is always Rust Anchor.
 */
public class ModeSelectScreen extends Screen {
	private static final int CARD_W = 172;
	private static final int CARD_H = 118;

	private final BiConsumer<WorldMode, Hardship> proceed;
	private final Runnable back;
	private final long opened = System.nanoTime();

	public ModeSelectScreen(final BiConsumer<WorldMode, Hardship> proceed, final Runnable back) {
		super(Component.translatable("mode.mistinn.title"));
		this.proceed = proceed;
		this.back = back;
	}

	@Override
	protected void init() {
		int gap = 14;
		int cardWidth = Math.min(CARD_W, (this.width - 3 * gap - 24) / 4);
		int x = (this.width - 4 * cardWidth - 3 * gap) / 2;
		int y = this.height / 2 - CARD_H / 2 + 10;
		this.addRenderableWidget(this.card(x, y, cardWidth, WorldMode.STORY));
		this.addRenderableWidget(this.card(x + cardWidth + gap, y, cardWidth, WorldMode.FREE));
		this.addRenderableWidget(this.card(x + 2 * (cardWidth + gap), y, cardWidth, WorldMode.TRAINING));
		this.addRenderableWidget(this.card(x + 3 * (cardWidth + gap), y, cardWidth, WorldMode.PATH));
	}

	private ChoiceCard card(final int x, final int y, final int width, final WorldMode mode) {
		return new ChoiceCard(x, y, width, CARD_H, Component.translatable("mode.mistinn." + mode.id),
			Component.translatable("mode.mistinn." + mode.id + ".desc"), null, mode == WorldMode.PATH ? ChoiceCard.RUST : ChoiceCard.GOLD,
			() -> this.pick(mode));
	}

	/** Whether picking this mode asks how hard the world should be. */
	public static boolean asksHardship(final WorldMode mode) {
		return mode == WorldMode.STORY || mode == WorldMode.TRAINING;
	}

	/** Picks a mode as a click would: story and training go on to the hardship screen; the path is Rust Anchor, always. */
	public void pick(final WorldMode mode) {
		if (mode == WorldMode.PATH) {
			this.proceed.accept(mode, Hardship.RUST_ANCHOR);
		} else if (asksHardship(mode) && this.minecraft != null) {
			this.minecraft.gui.setScreen(new HardshipScreen(this, mode, hardship -> this.proceed.accept(mode, hardship)));
		} else {
			this.proceed.accept(mode, Hardship.NORMAL);
		}
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
		backdrop(g, this.width, this.height, mouseX, mouseY, this.opened);
	}

	/** The shore behind both world-creation screens, darkened. */
	static void backdrop(final GuiGraphicsExtractor g, final int width, final int height, final int mouseX, final int mouseY, final long opened) {
		ShoreBackdrop.draw(g, width, height, mouseX, mouseY, (System.nanoTime() - opened) / 1.0E9F, false);
		g.fill(0, 0, width, height, 0x70080A0E);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		Component title = Component.translatable("mode.mistinn.title");
		g.text(this.font, title, (this.width - this.font.width(title)) / 2, this.height / 2 - CARD_H / 2 - 22, 0xFFE4DED0, true);
		Component hint = Component.translatable("mode.mistinn.hint");
		g.text(this.font, hint, (this.width - this.font.width(hint)) / 2, this.height / 2 + CARD_H / 2 + 22, 0xFF8E887C, false);
	}
}
