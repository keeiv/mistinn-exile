package life.shizune.mistinn.client.title;

import java.util.function.Consumer;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.story.WorldMode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Asked after the story or training card: how hard the world will be. Normal is the default,
 * already lit when the screen opens, so Enter takes it; Escape goes back to the modes.
 */
public class HardshipScreen extends Screen {
	private static final int CARD_W = 172;
	private static final int CARD_H = 150;

	private final Screen parent;
	private final WorldMode mode;
	private final Consumer<Hardship> proceed;
	private final long opened = System.nanoTime();
	private @Nullable ChoiceCard normal;

	public HardshipScreen(final Screen parent, final WorldMode mode, final Consumer<Hardship> proceed) {
		super(Component.translatable("hardship.mistinn.title"));
		this.parent = parent;
		this.mode = mode;
		this.proceed = proceed;
	}

	@Override
	protected void init() {
		int gap = 14;
		int cardWidth = Math.min(CARD_W, (this.width - 2 * gap - 24) / 3);
		int x = (this.width - 3 * cardWidth - 2 * gap) / 2;
		int y = this.height / 2 - CARD_H / 2 + 10;
		this.addRenderableWidget(this.card(x, y, cardWidth, Hardship.EASY));
		ChoiceCard middle = this.addRenderableWidget(this.card(x + cardWidth + gap, y, cardWidth, Hardship.NORMAL));
		this.addRenderableWidget(this.card(x + 2 * (cardWidth + gap), y, cardWidth, Hardship.RUST_ANCHOR));
		this.normal = middle;
		this.setInitialFocus(middle);
	}

	private ChoiceCard card(final int x, final int y, final int width, final Hardship hardship) {
		String key = "hardship.mistinn." + hardship.id;
		return new ChoiceCard(x, y, width, CARD_H, Component.translatable(key), Component.translatable(key + ".desc"),
			hardship == Hardship.NORMAL ? Component.translatable("hardship.mistinn.default") : null,
			hardship == Hardship.RUST_ANCHOR ? ChoiceCard.RUST : ChoiceCard.GOLD, () -> this.pick(hardship));
	}

	/** The mode this screen was opened for. */
	public WorldMode mode() {
		return this.mode;
	}

	/** Picks a hardship as a click would. */
	public void pick(final Hardship hardship) {
		this.proceed.accept(hardship);
	}

	/** Whether the card already lit, the one Enter takes, is the given one. */
	public boolean lit(final Hardship hardship) {
		return hardship == Hardship.NORMAL && this.normal != null && this.getFocused() == this.normal;
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(this.parent);
		}
	}

	@Override
	protected void extractPanorama(final GuiGraphicsExtractor g, final float a) {
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		ModeSelectScreen.backdrop(g, this.width, this.height, mouseX, mouseY, this.opened);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		Component title = Component.translatable("hardship.mistinn.title");
		g.text(this.font, title, (this.width - this.font.width(title)) / 2, this.height / 2 - CARD_H / 2 - 22, 0xFFE4DED0, true);
		Component hint = Component.translatable("hardship.mistinn.hint", Component.translatable("mode.mistinn." + this.mode.id));
		g.text(this.font, hint, (this.width - this.font.width(hint)) / 2, this.height / 2 + CARD_H / 2 + 22, 0xFF8E887C, false);
	}
}
