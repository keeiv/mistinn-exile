package life.shizune.mistinn.client.medical;

import life.shizune.mistinn.client.anim.Motion;
import life.shizune.mistinn.medical.TreatmentService;
import life.shizune.mistinn.tier.Tier;
import life.shizune.mistinn.tier.Tiers;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * 使用進度: a translucent black box above the hotbar while a treatment runs, "正在使用" and the item's name
 * over a progress bar and the seconds left. It rises in as the use starts; when the use ends
 * the bar fills (done) or turns red (broken off) and the box fades away. Under painkillers a
 * small tag in the same place counts their time down.
 */
public class TreatmentHud implements HudElement {
	private static final int WIDTH = 150;
	private static final int HEIGHT = 27;
	/** Bottom of the box, above the hotbar, the experience bar and the held item's name. */
	private static final int LIFT = 64;
	private static final int BACK = 0x080A0E;
	private static final int TEXT = 0xEDE6D8;
	private static final int HINT = 0x9A948A;
	private static final int TRACK = 0x2A2C30;
	private static final int DONE = 0x7BC77F;
	private static final int BROKEN = 0xC54242;

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui.screen() != null || mc.getDebugOverlay().showDebugScreen()) {
			return;
		}
		TreatmentClient.Progress use = TreatmentClient.local();
		if (use != null) {
			this.channel(g, mc.font, use);
			return;
		}
		long numb = TreatmentService.numbTicks(mc.player);
		if (numb > 0) {
			this.numb(g, mc.font, numb);
		}
	}

	private void channel(final GuiGraphicsExtractor g, final Font font, final TreatmentClient.Progress use) {
		// rise in quickly with a soft landing; after the end, hold a moment and fade
		float arrive = Motion.outCubic(use.seconds() / 0.18F);
		float fade = 1.0F - Mth.clamp((use.sinceEnd() - 0.2F) / (TreatmentClient.LINGER - 0.2F), 0.0F, 1.0F);
		float alpha = Mth.clamp(arrive, 0.0F, 1.0F) * fade;
		if (alpha <= 0.02F) {
			return;
		}
		int x = (g.guiWidth() - WIDTH) / 2;
		int y = g.guiHeight() - LIFT - HEIGHT + Math.round(6.0F * (1.0F - arrive));
		Tier tier = Tiers.of(new ItemStack(use.item()));
		g.fill(x, y, x + WIDTH, y + HEIGHT, argb(0.56F * alpha, BACK));
		g.fill(x, y, x + 2, y + HEIGHT, argb(alpha, tier.rgb()));
		Component label = use.running() || use.result() == TreatmentService.RESULT_DONE
			? Component.translatable("hud.mistinn.using", use.item().getName(new ItemStack(use.item())))
			: Component.translatable("hud.mistinn.interrupted", use.item().getName(new ItemStack(use.item())));
		g.text(font, label, x + 7, y + 4, argb(alpha, use.running() ? TEXT : use.result() == TreatmentService.RESULT_DONE ? DONE : BROKEN), true);
		// the bar, and the seconds left beside it
		float share = use.duration() <= 0.0F ? 1.0F : Mth.clamp(use.seconds() / use.duration(), 0.0F, 1.0F);
		if (use.result() == TreatmentService.RESULT_DONE) {
			share = 1.0F;
		}
		float left = Math.max(0.0F, use.duration() - use.seconds());
		Component seconds = Component.translatable("hud.mistinn.seconds", String.format(java.util.Locale.ROOT, "%.1f", use.result() == TreatmentService.RESULT_DONE ? 0.0F : left));
		int textWidth = font.width(seconds);
		int barX = x + 7;
		int barY = y + 17;
		int barW = WIDTH - 14 - textWidth - 5;
		g.fill(barX - 1, barY - 1, barX + barW + 1, barY + 4, argb(0.85F * alpha, 0x000000));
		g.fill(barX, barY, barX + barW, barY + 3, argb(alpha, TRACK));
		int colour = use.running() ? tier.rgb() : use.result() == TreatmentService.RESULT_DONE ? DONE : BROKEN;
		int filled = Math.round(barW * share);
		g.fill(barX, barY, barX + filled, barY + 3, argb(alpha, colour));
		// a lit leading edge so the bar reads as moving
		if (use.running() && filled > 1) {
			g.fill(barX + filled - 1, barY, barX + filled, barY + 3, argb(alpha, 0xFFFFFF));
		}
		g.text(font, seconds, x + WIDTH - 7 - textWidth, y + 15, argb(alpha, HINT), true);
	}

	private void numb(final GuiGraphicsExtractor g, final Font font, final long ticks) {
		Component tag = Component.translatable("hud.mistinn.numb", (ticks + 19) / 20);
		int w = font.width(tag) + 12;
		int x = (g.guiWidth() - w) / 2;
		int y = g.guiHeight() - LIFT - 13;
		// the last ten seconds pulse, so it does not wear off unnoticed
		float pulse = ticks < 200 ? 0.65F + 0.35F * Mth.sin(ticks * 0.45F) : 1.0F;
		g.fill(x, y, x + w, y + 13, argb(0.5F * pulse, BACK));
		g.fill(x, y, x + 2, y + 13, argb(pulse, Tier.III.rgb()));
		g.text(font, tag, x + 7, y + 3, argb(pulse, HINT), true);
	}

	private static int argb(final float alpha, final int rgb) {
		return (Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24) | (rgb & 0xFFFFFF);
	}
}
