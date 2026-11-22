package life.shizune.mistinn.client.training;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * The drop on screen: the world goes on behind it (nothing pauses), the HUD is put away while it
 * is open, and no key reaches the hunter, who has nothing to do but fall. It cannot be closed;
 * {@link DropCinematic} closes it when the drop is over.
 */
public final class DropScreen extends Screen {
	private boolean hid;

	public DropScreen() {
		super(Component.empty());
	}

	@Override
	protected void init() {
		Minecraft mc = Minecraft.getInstance();
		if (!this.hid && !mc.gui.hud.isHidden()) {
			mc.gui.hud.toggle();
			this.hid = true;
		}
	}

	@Override
	public void removed() {
		Minecraft mc = Minecraft.getInstance();
		if (this.hid && mc.gui.hud.isHidden()) {
			mc.gui.hud.toggle();
		}
		this.hid = false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		return true;
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		// the world shows through: no blur, no dimming but the drop's own
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		DropCinematic.draw(g, a);
	}
}
