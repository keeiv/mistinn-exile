package life.shizune.mistinn.client.expedition;

import life.shizune.mistinn.expedition.Expedition;
import life.shizune.mistinn.expedition.Grounds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * A hunter in an expedition's ground sees, at the top of the screen, where they are and how long they have; while they
 * hold a way out, how long until they are out. In a ground their own client does not build either (the server would
 * not let the block stand).
 */
public final class ExpeditionHud implements HudElement {
	private static final int TEXT = 0xFFEDE6D8;
	private static final int URGENT = 0xFFE8B24A;
	private static final int LEAVING = 0xFF9ED8D0;
	private static boolean held;

	/** Keeps the local hunter from building in a ground, and lets them again once out. */
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null || mc.level == null || mc.gameMode == null) {
				held = false;
				return;
			}
			boolean ground = Grounds.is(mc.level) && !mc.player.isCreative();
			if (ground) {
				mc.player.getAbilities().mayBuild = false;
				held = true;
			} else if (held) {
				mc.player.getAbilities().mayBuild = !mc.gameMode.getPlayerMode().isBlockPlacingRestricted();
				held = false;
			}
		});
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.getDebugOverlay().showDebugScreen()) {
			return;
		}
		Expedition.Away away = mc.player.getAttached(Expedition.AWAY);
		if (away == null) {
			return;
		}
		long now = mc.level.getGameTime();
		long left = Math.max(0L, away.ends() - now);
		long seconds = (left + 19L) / 20L;
		Font font = mc.font;
		Component line = Component.translatable("hud.mistinn.expedition.left", Expedition.name(away.ground()),
			String.format("%d:%02d", seconds / 60L, seconds % 60L));
		Component leaving = away.leaving() == 0L ? null
			: Component.translatable("hud.mistinn.expedition.leaving", Math.max(0L, (Expedition.exitTicks - (now - away.leaving()) + 19L) / 20L));
		int width = Math.max(font.width(line), leaving == null ? 0 : font.width(leaving)) + 16;
		int height = leaving == null ? 16 : 27;
		int x = (g.guiWidth() - width) / 2;
		// below the boss bars, if any show
		int bars = ((life.shizune.mistinn.client.mixin.BossOverlayAccessor) mc.gui.hud.getBossOverlay()).mistinn$events().size();
		int y = 4 + bars * 19;
		g.fill(x, y, x + width, y + height, 0x90080A0E);
		g.fill(x, y + height - 1, x + width, y + height, left <= 1200L ? URGENT : 0xFF3A6B6B);
		g.centeredText(font, line, g.guiWidth() / 2, y + 4, left <= 1200L ? URGENT : TEXT);
		if (leaving != null) {
			g.centeredText(font, leaving, g.guiWidth() / 2, y + 15, LEAVING);
		}
	}
}
