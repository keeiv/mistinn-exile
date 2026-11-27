package life.shizune.mistinn.client.path;

import life.shizune.mistinn.path.PathService;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The way's panel at the top left, for a world walked by the crystal tillers: the chapter, what
 * the way asks now and a hint, and under them how many have fallen on it and whose turn it is.
 */
public class PathHud implements HudElement {
	private static final int MARGIN = 6;
	private static final int WIDTH = 220;
	private static final int CHAPTER = 0xFF8FC8C0;
	private static final int OBJECTIVE = 0xFFEDE6D8;
	private static final int HINT = 0xFF9A948A;
	private static final int FALLEN = 0xFFC07A6A;
	private static final int EDGE = 0xFF3E6E68;

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui.screen() != null || mc.getDebugOverlay().showDebugScreen()) {
			return;
		}
		PathService.View view = mc.player.getAttached(PathService.VIEW);
		if (view == null) {
			return;
		}
		Font font = mc.font;
		PathService.Step step = view.stage();
		Component chapter = Component.literal("◆ ").append(Component.translatable("path.mistinn.chapter." + step.chapter));
		Component objective = Component.translatable(step.key());
		Component hint = Component.translatable(step.key() + ".hint");
		Component fallen = Component.translatable("path.mistinn.hud.walker", view.fallen(), PathService.PEOPLE, PathService.name(view.walker()));
		Component traits = traits(view);
		var hintRows = font.split(hint, WIDTH);
		int width = Math.max(Math.max(Math.max(font.width(chapter), font.width(objective)), Math.max(Math.min(font.width(hint), WIDTH), font.width(fallen))),
			font.width(traits)) + 14;
		int height = 52 + hintRows.size() * 10;
		int x = MARGIN;
		int y = MARGIN;
		g.fill(x, y, x + width, y + height, 0x90080A0E);
		g.fill(x, y, x + 2, y + height, EDGE);
		int ty = y + 5;
		g.text(font, chapter, x + 7, ty, CHAPTER, true);
		ty += 12;
		g.text(font, objective, x + 7, ty, OBJECTIVE, true);
		ty += 12;
		for (var row : hintRows) {
			g.text(font, row, x + 7, ty, HINT, false);
			ty += 10;
		}
		ty += 2;
		g.text(font, fallen, x + 7, ty, FALLEN, false);
		ty += 10;
		g.text(font, traits, x + 7, ty, HINT, false);
	}

	/** The walker's strength and flaw, and a strength taken up at a memorial. */
	public static Component traits(final PathService.View view) {
		if (view.strength() < 0 || view.flaw() < 0) {
			return Component.empty();
		}
		net.minecraft.network.chat.MutableComponent line = Component.translatable("path.mistinn.hud.traits",
			life.shizune.mistinn.path.PathTraits.Strength.values()[view.strength()].title(), life.shizune.mistinn.path.PathTraits.Flaw.values()[view.flaw()].title());
		if (view.legacy() >= 0) {
			line.append(Component.translatable("path.mistinn.hud.legacy", life.shizune.mistinn.path.PathTraits.Strength.values()[view.legacy()].title()));
		}
		return line;
	}
}
