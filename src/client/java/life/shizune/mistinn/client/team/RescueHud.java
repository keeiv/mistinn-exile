package life.shizune.mistinn.client.team;

import java.util.Locale;
import life.shizune.mistinn.team.DownService;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * 倒地 on the screen. Down: a red edge, how long they can still hold out, and who is helping. Beside a
 * downed teammate: which keys bring them back. While helping: the rescue's bar above the hotbar.
 */
public final class RescueHud implements HudElement {
	private static final int WIDTH = 160;
	private static final int LIFT = 64;
	private static final int TEXT = 0xFFEDE6D8;
	private static final int HINT = 0xFFB8B2A8;
	private static final int BLOOD = 0xFFC54242;
	private static final int HELP = 0xFF7BC77F;
	private static final int TRACK = 0xFF2A2C30;

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.getDebugOverlay().showDebugScreen()) {
			return;
		}
		Font font = mc.font;
		DownService.Down own = DownService.get(mc.player);
		if (own != null) {
			this.downed(g, font, mc, own);
			return;
		}
		if (mc.gui.screen() != null) {
			return;
		}
		Player helped = RescueClient.helping(mc);
		if (helped != null) {
			DownService.Down down = DownService.get(helped);
			if (down != null) {
				Component label = Component.translatable(RescueClient.key(down.dragging() ? "dragging" : "helping"), helped.getDisplayName());
				this.bar(g, font, (g.guiWidth() - WIDTH) / 2, g.guiHeight() - LIFT - 27, label, down.progress() / (float) DownService.RESCUE_TICKS,
					(DownService.RESCUE_TICKS - down.progress()) / 20.0F, HELP);
			}
			return;
		}
		Player target = RescueClient.target(mc);
		if (target != null) {
			Component prompt = Component.translatable(RescueClient.key("prompt"), RescueClient.rescueKey().getTranslatedKeyMessage(),
				RescueClient.dragKey().getTranslatedKeyMessage(), target.getDisplayName());
			g.text(font, prompt, (g.guiWidth() - font.width(prompt)) / 2, g.guiHeight() / 2 + 14, TEXT, true);
		}
	}

	/** On the ground: the edge of the view reddens as the time runs out. */
	private void downed(final GuiGraphicsExtractor g, final Font font, final Minecraft mc, final DownService.Down down) {
		float left = Mth.clamp(down.left() / (float) DownService.BLEED_TICKS, 0.0F, 1.0F);
		int edge = Math.round((0.25F + 0.45F * (1.0F - left)) * 255.0F) << 24 | 0x5A0008;
		int w = g.guiWidth();
		int h = g.guiHeight();
		int band = Math.max(8, h / 10);
		g.fill(0, 0, w, band, edge);
		g.fill(0, h - band, w, h, edge);
		g.fill(0, band, band, h - band, edge);
		g.fill(w - band, band, w, h - band, edge);
		Component title = Component.translatable(RescueClient.key("title"));
		int y = h / 2 - 34;
		g.pose().pushMatrix();
		g.pose().translate(w / 2.0F, y);
		g.pose().scale(1.6F, 1.6F);
		g.text(font, title, -font.width(title) / 2, 0, BLOOD, true);
		g.pose().popMatrix();
		Component seconds = Component.translatable(RescueClient.key("left"), String.format(Locale.ROOT, "%.0f", Math.max(0.0F, down.left() / 20.0F)));
		g.text(font, seconds, (w - font.width(seconds)) / 2, y + 20, TEXT, true);
		if (down.rescuer() >= 0 && mc.level.getEntity(down.rescuer()) instanceof Player rescuer) {
			Component by = Component.translatable(RescueClient.key(down.dragging() ? "dragged_by" : "helped_by"), rescuer.getDisplayName());
			this.bar(g, font, (w - WIDTH) / 2, y + 34, by, down.progress() / (float) DownService.RESCUE_TICKS,
				(DownService.RESCUE_TICKS - down.progress()) / 20.0F, HELP);
		} else {
			Component wait = Component.translatable(RescueClient.key("wait"));
			g.text(font, wait, (w - font.width(wait)) / 2, y + 34, HINT, true);
		}
	}

	private void bar(final GuiGraphicsExtractor g, final Font font, final int x, final int y, final Component label, final float share,
		final float secondsLeft, final int colour) {
		g.fill(x, y, x + WIDTH, y + 27, 0x8F080A0E);
		g.fill(x, y, x + 2, y + 27, colour);
		g.text(font, label, x + 7, y + 4, TEXT, true);
		Component seconds = Component.translatable("hud.mistinn.seconds", String.format(Locale.ROOT, "%.1f", Math.max(0.0F, secondsLeft)));
		int barX = x + 7;
		int barY = y + 17;
		int barW = WIDTH - 14 - font.width(seconds) - 5;
		g.fill(barX - 1, barY - 1, barX + barW + 1, barY + 4, 0xD9000000);
		g.fill(barX, barY, barX + barW, barY + 3, TRACK);
		g.fill(barX, barY, barX + Math.round(barW * Mth.clamp(share, 0.0F, 1.0F)), barY + 3, colour);
		g.text(font, seconds, barX + barW + 5, barY - 2, HINT, true);
	}
}
