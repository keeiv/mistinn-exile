package life.shizune.mistinn.client.body;

import life.shizune.mistinn.stamina.StaminaService;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/** The stamina bar, on the row the hearts used to take. Amber when low, red when spent. */
public final class StaminaHud implements HudElement {
	private static final int WIDTH = 81;

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || !StaminaService.applies(player) || mc.gui.hud.isHidden()) {
			return;
		}
		float share = Math.min(1.0F, StaminaService.get(player) / StaminaService.max(player));
		int x = g.guiWidth() / 2 - 91;
		int y = g.guiHeight() - 38;
		g.fill(x - 1, y - 1, x + WIDTH + 1, y + 4, 0xC0101010);
		int fill = Math.round(WIDTH * share);
		int colour = share <= 0.0F ? 0xFFB03A2E : share < 0.3F ? 0xFFD08A3A : 0xFFD8C060;
		g.fill(x, y, x + fill, y + 3, colour);
		g.fill(x, y, x + fill, y + 1, 0x40FFFFFF);
	}
}
