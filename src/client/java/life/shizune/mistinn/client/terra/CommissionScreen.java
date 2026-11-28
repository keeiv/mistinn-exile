package life.shizune.mistinn.client.terra;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.terra.Commissions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * 委託: the board a woken homestead's keeper shows. Its commissions, each with what it asks and pays and a button to take
 * it; below, the hunter's own commission, how far along and how long it has, with handing in (a delivery, to the keeper
 * who asked) and letting it go; and for the elder, its wares. Everything asked goes to the server, which decides.
 */
public final class CommissionScreen extends Screen {
	private static final int WIDTH = 300;
	private static final int ROW = 30;
	private static final int PANEL = 0xE00C0E14;
	private static final int EDGE = 0xFF3A6B6B;
	private static final int TITLE = 0xFF9ED8D0;
	/** An expedition's line: the colour of the forebears' crystal. */
	private static final int AFAR = 0xFF9ED8D0;
	private static final int TEXT = 0xFFEDE6D8;
	private static final int DIM = 0xFF9A948A;
	private static final int GOLD = 0xFFE0A050;

	private Commissions.BoardPayload board;
	private Commissions.@Nullable Taken shown;
	private int left;
	private int top;
	private int panel;
	private int rows;
	private int currentY;
	private int bottomY;
	private final List<int[]> offerAt = new ArrayList<>();

	public CommissionScreen(final Commissions.BoardPayload board) {
		super(Component.translatable("gui.mistinn.commission.title"));
		this.board = board;
	}

	/** Whose board this is. */
	public int keeper() {
		return this.board.keeper();
	}

	/** The board as the server says it is now. */
	public void update(final Commissions.BoardPayload board) {
		this.board = board;
		this.rebuildWidgets();
	}

	public Commissions.BoardPayload board() {
		return this.board;
	}

	private static Commissions.@Nullable Taken taken() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? null : mc.player.getAttached(Commissions.TAKEN);
	}

	private int panelHeight() {
		Commissions.Taken taken = taken();
		return 26 + Math.max(1, this.board.offers().size()) * ROW + 16 + (taken != null ? 50 : 16) + 30;
	}

	@Override
	protected void init() {
		this.shown = taken();
		this.panel = Math.min(WIDTH, super.width - 16);
		this.left = (super.width - this.panel) / 2;
		this.top = Math.max(6, (this.height - this.panelHeight()) / 2);
		this.offerAt.clear();
		int y = this.top + 26;
		this.rows = Math.max(1, this.board.offers().size());
		for (Commissions.Commission each : this.board.offers()) {
			Button take = Button.builder(Component.translatable("gui.mistinn.commission.take"), b -> ask("take", each.id()))
				.bounds(this.left + this.panel - 58, y + 4, 52, 20).build();
			take.active = this.shown == null;
			this.addRenderableWidget(take);
			this.offerAt.add(new int[] {y});
			y += ROW;
		}
		if (this.board.offers().isEmpty()) {
			y += ROW;
		}
		this.currentY = y + 6;
		y = this.currentY + 14;
		if (this.shown != null) {
			int bx = this.left + this.panel - 58;
			if (this.shown.commission().kind() == Commissions.Kind.DELIVER && this.shown.domain() == this.board.domain()) {
				this.addRenderableWidget(Button.builder(Component.translatable("gui.mistinn.commission.hand"), b -> ask("hand", ""))
					.bounds(bx - 56, y + 12, 52, 20).build());
			}
			if (this.shown.commission().kind() == Commissions.Kind.EXPEDITION) {
				this.addRenderableWidget(Button.builder(Component.translatable("gui.mistinn.commission.depart"), b -> {
					ask("depart", "");
					this.onClose();
				}).bounds(bx - 56, y + 12, 52, 20).build());
			}
			this.addRenderableWidget(Button.builder(Component.translatable("gui.mistinn.commission.abandon"), b -> ask("abandon", ""))
				.bounds(bx, y + 12, 52, 20).build());
			y += 36;
		} else {
			// room for "no commission"
			y += 14;
		}
		this.bottomY = y + 4;
		if (this.board.trades()) {
			this.addRenderableWidget(Button.builder(Component.translatable("gui.mistinn.commission.trade"), b -> ask("trade", ""))
				.bounds(this.left + 6, this.bottomY, 64, 20).build());
		}
	}

	/** The centre of a widget's button, for tests: the take button of offer {@code index}, or by its words. */
	public double[] buttonCentre(final String key, final int index) {
		int seen = 0;
		for (var child : this.children()) {
			if (child instanceof Button button && button.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents words
				&& words.getKey().equals(key)) {
				if (seen++ == index) {
					return new double[] {button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0};
				}
			}
		}
		throw new IllegalArgumentException("no button " + key + " #" + index);
	}

	private void ask(final String action, final String commission) {
		ClientPlayNetworking.send(new Commissions.AskPayload(this.board.keeper(), action, commission));
	}

	@Override
	public void tick() {
		// the commission changes on the server (taken, handed in, done): the screen follows
		Commissions.Taken now = taken();
		if (now == null ? this.shown != null : !now.equals(this.shown)) {
			this.rebuildWidgets();
		}
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		Font font = this.font;
		int height = this.bottomY + 26 - this.top;
		g.fill(this.left - 1, this.top - 1, this.left + this.panel + 1, this.top + height + 1, EDGE);
		g.fill(this.left, this.top, this.left + this.panel, this.top + height, PANEL);
		g.text(font, this.title, this.left + 8, this.top + 8, TITLE, true);
		Component trust = Component.translatable("gui.mistinn.commission.trust", this.board.trust());
		g.text(font, trust, this.left + this.panel - 8 - font.width(trust), this.top + 8, DIM, false);
		g.fill(this.left + 6, this.top + 20, this.left + this.panel - 6, this.top + 21, EDGE);
		int room = this.panel - 74;
		for (int i = 0; i < this.board.offers().size(); i++) {
			Commissions.Commission each = this.board.offers().get(i);
			int y = this.offerAt.get(i)[0];
			g.text(font, fit(font, each.describe(), room), this.left + 10, y + 5, each.kind() == Commissions.Kind.EXPEDITION ? AFAR : TEXT, false);
			g.text(font, reward(each), this.left + 10, y + 17, GOLD, false);
		}
		if (this.board.offers().isEmpty()) {
			g.text(font, Component.translatable("gui.mistinn.commission.empty"), this.left + 10, this.top + 36, DIM, false);
		}
		g.fill(this.left + 6, this.currentY - 3, this.left + this.panel - 6, this.currentY - 2, EDGE);
		g.text(font, Component.translatable("gui.mistinn.commission.current"), this.left + 8, this.currentY + 2, TITLE, false);
		Commissions.Taken taken = taken();
		if (taken != null) {
			int y = this.currentY + 16;
			g.text(font, fit(font, taken.commission().describe(), this.panel - 20), this.left + 10, y, TEXT, false);
			Component progress = Component.translatable("gui.mistinn.commission.progress", taken.done(), taken.commission().count());
			long ticks = taken.until() - (this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.getGameTime() : 0L);
			Component left = Component.translatable("gui.mistinn.commission.left", Math.max(1L, (ticks + 23999L) / 24000L));
			g.text(font, progress.copy().append("  ").append(left), this.left + 10, y + 12, DIM, false);
			if (taken.domain() != this.board.domain()) {
				g.text(font, Component.translatable("gui.mistinn.commission.elsewhere"), this.left + 10, y + 24, DIM, false);
			}
		} else {
			g.text(font, Component.translatable("gui.mistinn.commission.none"), this.left + 10, this.currentY + 16, DIM, false);
		}
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	/** Words cut to fit, ending in an ellipsis rather than mid-word. */
	private static String fit(final Font font, final Component words, final int room) {
		String all = words.getString();
		if (font.width(all) <= room) {
			return all;
		}
		return font.plainSubstrByWidth(all, room - font.width("…")).stripTrailing() + "…";
	}

	/** What it pays, in words. */
	static Component reward(final Commissions.Commission commission) {
		return commission.cores() > 0
			? Component.translatable("gui.mistinn.commission.reward_cores", commission.salt(), commission.cores())
			: Component.translatable("gui.mistinn.commission.reward", commission.salt());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** The board came: shown, or the open one updated. */
	public static void received(final Commissions.BoardPayload board) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.screen() instanceof CommissionScreen open && open.keeper() == board.keeper()) {
			open.update(board);
		} else {
			mc.gui.setScreen(new CommissionScreen(board));
		}
	}
}
