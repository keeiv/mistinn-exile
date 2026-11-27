package life.shizune.mistinn.client.terra;

import life.shizune.mistinn.client.story.StoryHud;
import life.shizune.mistinn.terra.Commissions;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** The hunter's commission, in the top-left corner under the story's objective: what it asks, how far along, how long it has. */
public final class CommissionHud implements HudElement {
	private static final int MARGIN = 6;
	private static final int HEAD = 0xFF9ED8D0;
	private static final int TEXT = 0xFFEDE6D8;
	private static final int DIM = 0xFF9A948A;

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.gui.screen() != null || mc.getDebugOverlay().showDebugScreen()) {
			return;
		}
		Commissions.Taken taken = mc.player.getAttached(Commissions.TAKEN);
		if (taken == null) {
			return;
		}
		Font font = mc.font;
		Component head = Component.literal("◆ ").append(Component.translatable("hud.mistinn.commission"));
		Component what = taken.commission().describe().copy()
			.append(Component.literal("  " + taken.done() + "/" + taken.commission().count()).withColor(DIM));
		long ticks = taken.until() - mc.level.getGameTime();
		Component left = Component.translatable("gui.mistinn.commission.left", Math.max(1L, (ticks + 23999L) / 24000L));
		int width = Math.max(Math.max(font.width(head), font.width(what)), font.width(left)) + 14;
		int height = 38;
		int x = MARGIN;
		int y = StoryHud.bottom() + 4;
		g.fill(x, y, x + width, y + height, 0x90080A0E);
		g.fill(x, y, x + 2, y + height, 0xFF3A6B6B);
		g.text(font, head, x + 7, y + 5, HEAD, true);
		g.text(font, what, x + 7, y + 16, TEXT, true);
		g.text(font, left, x + 7, y + 27, DIM, false);
	}
}
