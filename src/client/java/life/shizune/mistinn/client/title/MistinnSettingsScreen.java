package life.shizune.mistinn.client.title;

import life.shizune.mistinn.client.ClientSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;

/** 霧岸設定: the hunter's own switches for this mod: whether it may frighten them, and whether the camera swings. */
public class MistinnSettingsScreen extends Screen {
	private static final int W = 220;
	private final Screen parent;

	public MistinnSettingsScreen(final Screen parent) {
		super(Component.translatable("settings.mistinn.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int x = (this.width - W) / 2;
		int y = this.height / 2 - 40;
		this.addRenderableWidget(switchFor(ClientSettings.SCARES, "settings.mistinn.scares", x, y));
		this.addRenderableWidget(switchFor(ClientSettings.SWING_CAMERA, "settings.mistinn.swing_camera", x, y + 26));
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose()).bounds(x, y + 64, W, 20).build());
	}

	private static CycleButton<Boolean> switchFor(final String key, final String label, final int x, final int y) {
		return CycleButton.onOffBuilder(ClientSettings.on(key))
			.withTooltip(on -> Tooltip.create(Component.translatable(label + ".tooltip")))
			.create(x, y, W, 20, Component.translatable(label), (button, on) -> ClientSettings.set(key, on));
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.text(this.font, this.title, (this.width - this.font.width(this.title)) / 2, this.height / 2 - 70, 0xFFE4DED0, true);
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(this.parent);
		}
	}
}
