package life.shizune.mistinn.client.epic;

import life.shizune.mistinn.epic.EpicState;
import life.shizune.mistinn.epic.EpicStep;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The epic's panel at the top left: the part, what the epic asks now and a hint, and under them the bell towers rung
 * and the great bells (with their beasts) of the land.
 */
public class EpicHud implements HudElement {
	private static final int MARGIN = 6;
	private static final int WIDTH = 220;
	private static final int PART = 0xFF8FC8C0;
	private static final int OBJECTIVE = 0xFFEDE6D8;
	private static final int HINT = 0xFF9A948A;
	private static final int TALLY = 0xFFD8B070;
	private static final int EDGE = 0xFF2E4A7A;

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui.screen() != null || mc.getDebugOverlay().showDebugScreen()) {
			return;
		}
		EpicState state = EpicClient.state(mc);
		if (state == null) {
			return;
		}
		Font font = mc.font;
		EpicStep step = state.at();
		Component part = Component.literal("◆ ").append(Component.translatable("epic.mistinn.part." + step.part));
		Component objective = Component.translatable(step.key());
		Component hint = Component.translatable(step.key() + ".hint");
		Component tally = Component.translatable("epic.mistinn.hud.tally", Integer.bitCount(state.towers()), 8, state.bellsRung(), 4);
		var hintRows = font.split(hint, WIDTH);
		int width = Math.max(Math.max(font.width(part), font.width(objective)), Math.max(Math.min(font.width(hint), WIDTH), font.width(tally))) + 14;
		int height = 42 + hintRows.size() * 10;
		int x = MARGIN;
		int y = MARGIN;
		g.fill(x, y, x + width, y + height, 0x90080A0E);
		g.fill(x, y, x + 2, y + height, EDGE);
		int ty = y + 5;
		g.text(font, part, x + 7, ty, PART, true);
		ty += 12;
		g.text(font, objective, x + 7, ty, OBJECTIVE, true);
		ty += 12;
		for (var row : hintRows) {
			g.text(font, row, x + 7, ty, HINT, false);
			ty += 10;
		}
		ty += 2;
		g.text(font, tally, x + 7, ty, TALLY, false);
	}
}
