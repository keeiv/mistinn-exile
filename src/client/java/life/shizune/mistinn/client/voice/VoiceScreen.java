package life.shizune.mistinn.client.voice;

import life.shizune.mistinn.voice.Voice;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 選擇聲音: asked once, the first time a hunter stands in a world: a man's voice or a girl's. Each can be heard before
 * it is chosen. It cannot be waved away: a hunter has a voice.
 */
public final class VoiceScreen extends Screen {
	private static final int GOLD = 0xFFE8AF69;
	private static final int PAPER = 0xFFECE3D2;
	private static final int ASH = 0xFFB8B0A0;
	private static final int PANEL_HEIGHT = 132;
	private int left;
	private int top;
	private int panelWidth;
	/** Which sample a voice's listen button plays next, so a few cries are heard in turn. */
	private final int[] heard = new int[Voice.values().length];

	public VoiceScreen() {
		super(Component.translatable("voice.mistinn.title"));
	}

	@Override
	protected void init() {
		this.panelWidth = Math.min(300, this.width - 16);
		this.left = (this.width - this.panelWidth) / 2;
		this.top = Math.max(4, (this.height - PANEL_HEIGHT) / 2);
		int inner = this.panelWidth - 24;
		int half = (inner - 8) / 2;
		for (Voice voice : Voice.values()) {
			int x = this.left + 12 + voice.ordinal() * (half + 8);
			String key = voice == Voice.MALE ? "male" : "female";
			this.addRenderableWidget(Button.builder(Component.translatable("voice.mistinn." + key), button -> this.choose(voice))
				.bounds(x, this.top + 66, half, 20).build());
			this.addRenderableWidget(Button.builder(Component.translatable("voice.mistinn.listen"), button -> this.listen(voice))
				.bounds(x, this.top + 92, half, 20).build());
		}
	}

	private void listen(final Voice voice) {
		int turn = this.heard[voice.ordinal()]++ % 3;
		VoiceClient.play(this.minecraft, turn == 0 ? voice.effort : turn == 1 ? voice.hurt : voice.breath, 0.9F, 1.0F);
	}

	private void choose(final Voice voice) {
		if (ClientPlayNetworking.canSend(Voice.ChoicePayload.TYPE)) {
			ClientPlayNetworking.send(new Voice.ChoicePayload(voice.ordinal()));
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
		g.text(this.font, Component.translatable("voice.mistinn.note"), x, this.top + PANEL_HEIGHT - 14, ASH, false);
		super.extractRenderState(g, mouseX, mouseY, alpha);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
