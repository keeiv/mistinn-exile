package life.shizune.mistinn.client.epic;

import life.shizune.mistinn.epic.EpicService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Chronicler's question, at the end of the epic: under what name it is written. The hand that held 3412 all the
 * way here gives the name; or leaves the number, which is a name too.
 */
public class EpicNameScreen extends Screen {
	private EditBox name;

	public EpicNameScreen() {
		super(Component.translatable("epic.mistinn.name.title"));
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int cy = this.height / 2;
		this.name = new EditBox(this.font, cx - 90, cy - 4, 180, 20, Component.translatable("epic.mistinn.name.field"));
		this.name.setMaxLength(EpicService.NAME_LENGTH);
		this.addRenderableWidget(this.name);
		this.setInitialFocus(this.name);
		this.addRenderableWidget(Button.builder(Component.translatable("epic.mistinn.name.write"), b -> this.write(this.name.getValue()))
			.bounds(cx - 90, cy + 26, 86, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("epic.mistinn.name.number"), b -> this.write(Component.translatable(
			"story.mistinn.speaker.apostle").getString())).bounds(cx + 4, cy + 26, 86, 20).build());
	}

	private void write(final String given) {
		if (EpicService.clean(given).isEmpty()) {
			return;
		}
		ClientPlayNetworking.send(new EpicService.NamePayload(given));
		this.onClose();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		g.fill(0, 0, this.width, this.height, 0xE0100D0A);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int cx = this.width / 2;
		int cy = this.height / 2;
		Component title = this.getTitle();
		g.text(this.font, title, cx - this.font.width(title) / 2, cy - 44, 0xFFF2EAD8, true);
		Component ask = Component.translatable("epic.mistinn.name.ask");
		g.text(this.font, ask, cx - this.font.width(ask) / 2, cy - 26, 0xFFB8B0A0, false);
	}
}
