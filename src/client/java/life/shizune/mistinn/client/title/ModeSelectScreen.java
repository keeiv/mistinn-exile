package life.shizune.mistinn.client.title;

import java.util.List;
import java.util.function.BiConsumer;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.story.WorldMode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Asked at world creation: the story, free exploration or training. The story opens its three roads
 * ({@link StorySelectScreen}): the road of rust, the path of longing and the epic. Each road and training
 * then asks how hard the world should be, offering only what it allows: the road of rust and training
 * all three, the path of longing only Rust Anchor (its rules are fixed under it), the epic only Hunter.
 * Free exploration is normal and asks nothing.
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
		int cardWidth = Math.min(CARD_W, (this.width - 2 * gap - 24) / 3);
		int x = (this.width - 3 * cardWidth - 2 * gap) / 2;
		int y = this.height / 2 - CARD_H / 2 + 10;
		this.addRenderableWidget(this.card(x, y, cardWidth, WorldMode.STORY));
		this.addRenderableWidget(this.card(x + cardWidth + gap, y, cardWidth, WorldMode.FREE));
		this.addRenderableWidget(this.card(x + 2 * (cardWidth + gap), y, cardWidth, WorldMode.TRAINING));
	}

	private ChoiceCard card(final int x, final int y, final int width, final WorldMode mode) {
		return new ChoiceCard(x, y, width, CARD_H, Component.translatable("mode.mistinn." + mode.id),
			Component.translatable("mode.mistinn." + mode.id + ".desc"), null, ChoiceCard.GOLD, () -> this.pick(mode));
	}

	/** The hardships a mode offers, in the order shown; none for a mode that does not ask. */
	public static List<Hardship> offered(final WorldMode mode) {
		return switch (mode) {
			case STORY, TRAINING -> List.of(Hardship.EASY, Hardship.NORMAL, Hardship.RUST_ANCHOR);
			case PATH -> List.of(Hardship.RUST_ANCHOR);
			case EPIC -> List.of(Hardship.HUNTER);
			case FREE -> List.of();
		};
	}

	/** Whether picking this mode asks how hard the world should be. */
	public static boolean asksHardship(final WorldMode mode) {
		return !offered(mode).isEmpty();
	}

	/**
	 * Picks a mode as a click would. The story opens its roads; a road (picked there, or here by its mode) and training
	 * go on to the hardship screen with what they offer; free exploration goes straight on at normal.
	 */
	public void pick(final WorldMode mode) {
		if (this.minecraft == null) {
			// not on screen (a test, a mod): no screens to ask with, so the mode's first hardship
			List<Hardship> offered = offered(mode);
			this.proceed.accept(mode, offered.isEmpty() ? Hardship.NORMAL : offered.getFirst());
			return;
		}
		if (mode == WorldMode.STORY && !(this.minecraft.gui.screen() instanceof StorySelectScreen)) {
			this.minecraft.gui.setScreen(new StorySelectScreen(this));
		} else if (asksHardship(mode)) {
			net.minecraft.client.gui.screens.Screen from = this.minecraft.gui.screen() == null ? this : this.minecraft.gui.screen();
			this.minecraft.gui.setScreen(new HardshipScreen(from, mode, hardship -> this.proceed.accept(mode, hardship)));
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
