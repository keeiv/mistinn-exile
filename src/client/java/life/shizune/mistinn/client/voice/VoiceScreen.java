package life.shizune.mistinn.client.voice;

import life.shizune.mistinn.voice.Voice;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 選擇聲音: a man's voice, a girl's, or none. It is asked once, the first time a hunter stands in a world, and then
 * cannot be waved away: the hunter answers, even if the answer is no voice. Opened later with its key, it shows the
 * choice in use and closes like any other screen. Each voice can be heard before it is chosen.
 */
public final class VoiceScreen extends Screen {
	private static final int GOLD = 0xFFE8AF69;
	private static final int PAPER = 0xFFECE3D2;
	private static final int ASH = 0xFFB8B0A0;
	private static final int PANEL_HEIGHT = 158;
	/** The translation key of each choice, by its stored value minus one. */
	private static final String[] KEYS = {"voice.mistinn.male", "voice.mistinn.female", "voice.mistinn.none"};
	/** True when the hunter has not answered yet: the screen stays until they do. */
	private final boolean first;
	/** The choice in use when the screen opened; {@link Voice#UNSET} the first time. */
	private final int current;
	private int left;
	private int top;
	private int panelWidth;
	/** The middle of the button of the choice in use, where its mark is drawn; -1 when there is none. */
	private int currentX = -1;
	/** Which sample a voice's listen button plays next, so a few cries are heard in turn. */
	private final int[] heard = new int[Voice.values().length];

	public VoiceScreen(final int current) {
		super(Component.translatable("voice.mistinn.title"));
		this.current = current;
		this.first = current == Voice.UNSET;
	}

	@Override
	protected void init() {
		this.panelWidth = Math.min(330, this.width - 16);
		this.left = (this.width - this.panelWidth) / 2;
		this.top = Math.max(4, (this.height - PANEL_HEIGHT) / 2);
		int inner = this.panelWidth - 24;
		int gap = 6;
		int third = (inner - 2 * gap) / 3;
		for (int choice = Voice.MALE.stored(); choice <= Voice.NONE; choice++) {
			int x = this.left + 12 + (choice - 1) * (third + gap);
			int picked = choice;
			Button button = this.addRenderableWidget(Button.builder(Component.translatable(KEYS[choice - 1]), pressed -> this.choose(picked))
				.bounds(x, this.top + 72, third, 20).build());
			button.active = choice != this.current;
			if (choice == this.current) {
				this.currentX = x + third / 2;
			}
			if (choice != Voice.NONE) {
				Voice voice = Voice.values()[choice - 1];
				this.addRenderableWidget(Button.builder(Component.translatable("voice.mistinn.listen"), pressed -> this.listen(voice))
					.bounds(x, this.top + 98, third, 20).build());
			}
		}
	}

	private void listen(final Voice voice) {
		int turn = this.heard[voice.ordinal()]++ % 3;
		VoiceClient.play(this.minecraft, turn == 0 ? voice.effort : turn == 1 ? voice.hurt : voice.breath, 0.9F, 1.0F);
	}

	private void choose(final int choice) {
		if (ClientPlayNetworking.canSend(Voice.ChoicePayload.TYPE)) {
			ClientPlayNetworking.send(new Voice.ChoicePayload(choice));
		}
		VoiceClient.answered();
		this.minecraft.gui.setScreen(null);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float alpha) {
		int x = this.left + 12;
		g.fill(this.left, this.top, this.left + this.panelWidth, this.top + PANEL_HEIGHT, 0xEB101014);
		g.fill(this.left, this.top, this.left + this.panelWidth, this.top + 1, 0xBBA97647);
		g.text(this.font, this.title, x, this.top + 12, GOLD, true);
		int y = this.top + 30;
		for (var row : this.font.split(Component.translatable("voice.mistinn.question"), this.panelWidth - 24)) {
			g.text(this.font, row, x, y, PAPER, false);
			y += 10;
		}
		y = this.top + 126;
		for (var row : this.font.split(Component.translatable(this.first ? "voice.mistinn.note" : "voice.mistinn.note.change"), this.panelWidth - 24)) {
			g.text(this.font, row, x, y, ASH, false);
			y += 10;
		}
		if (this.currentX >= 0) {
			g.centeredText(this.font, Component.translatable("voice.mistinn.current"), this.currentX, this.top + 61, GOLD);
		}
		super.extractRenderState(g, mouseX, mouseY, alpha);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return !this.first;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
