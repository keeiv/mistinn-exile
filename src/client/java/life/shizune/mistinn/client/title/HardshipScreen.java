package life.shizune.mistinn.client.title;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.story.WorldMode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * Asked after a road of the story or training: how hard the world will be. Only the hardships the mode offers are
 * shown ({@link ModeSelectScreen#offered}): three for the road of rust and training, one for the path of longing (its
 * rules are fixed) and one for the epic. Each card says in a line what it is; the full rules of the card under the
 * pointer (or the one chosen with the keys) are written out across the screen below them. Normal is the default where
 * it is offered, already lit when the screen opens, so Enter takes it; Escape goes back.
 */
public class HardshipScreen extends Screen {
	private static final int CARD_W = 172;
	private static final int CARD_H = 66;
	private static final int GAP = 14;
	/** Space between the cards and the rules below them, and around the rules' text. */
	private static final int PAD = 8;
	private static final int LINE = 11;

	private final Screen parent;
	private final WorldMode mode;
	private final Consumer<Hardship> proceed;
	private final long opened = System.nanoTime();
	private final Map<Hardship, ChoiceCard> cards = new EnumMap<>(Hardship.class);
	private int top;
	private int left;
	private int rowWidth;
	/** Lines of the longest rules: the box below the cards is always this tall, so nothing moves. */
	private int rulesLines;

	public HardshipScreen(final Screen parent, final WorldMode mode, final Consumer<Hardship> proceed) {
		super(Component.translatable("hardship.mistinn.title"));
		this.parent = parent;
		this.mode = mode;
		this.proceed = proceed;
	}

	@Override
	protected void init() {
		List<Hardship> offered = ModeSelectScreen.offered(this.mode);
		int count = Math.max(1, offered.size());
		int cardWidth = Math.min(CARD_W, (this.width - 2 * GAP - 24) / 3);
		// a lone card keeps the rules box as wide as three would, so the rules read the same
		this.rowWidth = 3 * cardWidth + 2 * GAP;
		this.left = (this.width - this.rowWidth) / 2;
		this.rulesLines = 1;
		for (Hardship hardship : offered) {
			this.rulesLines = Math.max(this.rulesLines, this.font.split(rules(hardship), this.rowWidth - 2 * PAD).size());
		}
		// a line under the rules for what No. 3412 says to themself
		this.rulesLines++;
		int total = 22 + CARD_H + PAD + this.rulesBoxHeight() + 22;
		this.top = Math.max(4, (this.height - total) / 2);
		int y = this.top + 22;
		this.cards.clear();
		int x = (this.width - count * cardWidth - (count - 1) * GAP) / 2;
		for (Hardship hardship : offered) {
			this.cards.put(hardship, this.addRenderableWidget(this.card(x, y, cardWidth, hardship)));
			x += cardWidth + GAP;
		}
		this.setInitialFocus(this.cards.getOrDefault(Hardship.NORMAL, this.cards.get(offered.getFirst())));
	}

	private int rulesBoxHeight() {
		return this.rulesLines * LINE + 2 * PAD - 2;
	}

	private ChoiceCard card(final int x, final int y, final int width, final Hardship hardship) {
		String key = "hardship.mistinn." + hardship.id;
		return new ChoiceCard(x, y, width, CARD_H, Component.translatable(key), Component.translatable(key + ".tagline"),
			hardship == Hardship.NORMAL ? Component.translatable("hardship.mistinn.default")
				: this.mode == WorldMode.PATH || this.mode == WorldMode.EPIC ? Component.translatable("hardship.mistinn.only") : null,
			palette(hardship), () -> this.pick(hardship));
	}

	private static ChoiceCard.Palette palette(final Hardship hardship) {
		return hardship == Hardship.RUST_ANCHOR ? ChoiceCard.RUST : hardship == Hardship.HUNTER ? ChoiceCard.EPIC : ChoiceCard.GOLD;
	}

	private static Component rules(final Hardship hardship) {
		return Component.translatable("hardship.mistinn." + hardship.id + ".desc");
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
		ChoiceCard card = this.cards.get(hardship);
		return card != null && this.getFocused() == card;
	}

	/** The card whose rules are shown: the one under the pointer, else the one chosen with the keys. */
	private @Nullable Hardship shown() {
		Hardship focused = null;
		for (Map.Entry<Hardship, ChoiceCard> entry : this.cards.entrySet()) {
			if (entry.getValue().isHovered()) {
				return entry.getKey();
			}
			if (this.getFocused() == entry.getValue()) {
				focused = entry.getKey();
			}
		}
		return focused;
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
		g.text(this.font, title, (this.width - this.font.width(title)) / 2, this.top, 0xFFE4DED0, true);
		int boxY = this.top + 22 + CARD_H + PAD;
		int boxHeight = this.rulesBoxHeight();
		Hardship shown = this.shown();
		if (shown != null) {
			ChoiceCard.Palette palette = palette(shown);
			g.fill(this.left, boxY, this.left + this.rowWidth, boxY + boxHeight, 0xC00C0F12);
			g.outline(this.left, boxY, this.rowWidth, boxHeight, 0xFF000000 | palette.rest());
			List<FormattedCharSequence> lines = this.font.split(rules(shown), this.rowWidth - 2 * PAD);
			int line = boxY + PAD;
			for (FormattedCharSequence part : lines) {
				g.text(this.font, part, this.left + PAD, line, 0xFFC8C0B0, false);
				line += LINE;
			}
			// what No. 3412 says to themself about it, in the box's corner
			Component voice = Component.translatable("hardship.mistinn." + shown.id + ".voice").withStyle(net.minecraft.ChatFormatting.ITALIC);
			g.text(this.font, voice, this.left + this.rowWidth - PAD - this.font.width(voice), boxY + boxHeight - PAD - 8, 0xFF9A948A, false);
		}
		Component hint = Component.translatable("hardship.mistinn.hint",
			Component.translatable(this.mode == WorldMode.STORY ? "mode.mistinn.rust_road" : "mode.mistinn." + this.mode.id));
		g.text(this.font, hint, (this.width - this.font.width(hint)) / 2, boxY + boxHeight + 10, 0xFF8E887C, false);
	}
}
