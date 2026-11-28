package life.shizune.mistinn.client.title;

import life.shizune.mistinn.story.WorldMode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The story's three roads, after the story card: 救贖的鏽之路 (the road of rust, the story as it began), 憧憬的屍之道
 * (the path of longing) and 史詩成篇 (the epic). A road picked goes on to how hard its world is; Escape goes back.
 */
public class StorySelectScreen extends Screen {
	private static final int CARD_W = 172;
	private static final int CARD_H = 124;

	private final ModeSelectScreen modes;
	private final long opened = System.nanoTime();

	public StorySelectScreen(final ModeSelectScreen modes) {
		super(Component.translatable("mode.mistinn.roads"));
		this.modes = modes;
	}

	@Override
	protected void init() {
		int gap = 14;
		int cardWidth = Math.min(CARD_W, (this.width - 2 * gap - 24) / 3);
		int x = (this.width - 3 * cardWidth - 2 * gap) / 2;
		int y = this.height / 2 - CARD_H / 2 + 10;
		this.addRenderableWidget(this.card(x, y, cardWidth, WorldMode.STORY, "rust_road", ChoiceCard.GOLD));
		this.addRenderableWidget(this.card(x + cardWidth + gap, y, cardWidth, WorldMode.PATH, "path", ChoiceCard.RUST));
		this.addRenderableWidget(this.card(x + 2 * (cardWidth + gap), y, cardWidth, WorldMode.EPIC, "epic", ChoiceCard.EPIC));
	}

	private ChoiceCard card(final int x, final int y, final int width, final WorldMode mode, final String key, final ChoiceCard.Palette palette) {
		return new ChoiceCard(x, y, width, CARD_H, Component.translatable("mode.mistinn." + key), Component.translatable("mode.mistinn." + key + ".desc"),
			mode == WorldMode.EPIC ? Component.translatable("mode.mistinn.epic.tag") : null, palette, () -> this.pick(mode));
	}

	/** Picks a road as a click would: on to how hard its world is. */
	public void pick(final WorldMode road) {
		this.modes.pick(road);
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(this.modes);
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
		Component title = this.getTitle();
		g.text(this.font, title, (this.width - this.font.width(title)) / 2, this.height / 2 - CARD_H / 2 - 22, 0xFFE4DED0, true);
		Component hint = Component.translatable("mode.mistinn.roads.hint");
		g.text(this.font, hint, (this.width - this.font.width(hint)) / 2, this.height / 2 + CARD_H / 2 + 22, 0xFF8E887C, false);
	}
}
