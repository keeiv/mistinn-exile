package life.shizune.mistinn.client.story;

import life.shizune.mistinn.story.StoryProgress;
import life.shizune.mistinn.story.StoryStep;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The objective in the top-left corner (chapter, objective, hint and distance), lit up for a
 * while when it changes; and the radio's subtitles above the hotbar.
 */
public class StoryHud implements HudElement {
	private static final int MARGIN = 6;
	private static final int CHAPTER = 0xFFB89A62;
	private static final int OBJECTIVE = 0xFFEDE6D8;
	private static final int HINT = 0xFF9A948A;
	private static final int FLASH = 0xFFE0A050;

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui.screen() != null || mc.getDebugOverlay().showDebugScreen()) {
			return;
		}
		if (StoryClient.playing() != null && !StoryClient.inScene()) {
			Subtitles.draw(g, g.guiWidth(), g.guiHeight() - 78, true);
		}
		StoryProgress progress = StoryClient.progress();
		if (progress == null) {
			return;
		}
		Font font = mc.font;
		StoryStep step = progress.step();
		Component chapter = Component.literal("◆ ").append(Component.translatable("story.mistinn.chapter." + step.chapter));
		Component objective = Component.translatable(step.key());
		Component hint = Component.translatable(step.key() + ".hint");
		if (progress.target().isPresent() && mc.player.level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
			BlockPos at = progress.target().get();
			int metres = (int) Math.round(Math.sqrt(Math.pow(at.getX() + 0.5 - mc.player.getX(), 2) + Math.pow(at.getZ() + 0.5 - mc.player.getZ(), 2)));
			objective = objective.copy().append(Component.literal("  " + metres + " m").withColor(HINT));
		}
		int flash = StoryClient.flash();
		boolean fresh = flash > 0;
		int width = Math.max(Math.max(font.width(chapter), font.width(objective)), Math.min(font.width(hint), 220)) + 14;
		var hintRows = font.split(hint, 220);
		int height = 30 + hintRows.size() * 10 + (fresh ? 11 : 0);
		// a new objective slides in from the edge and settles; the panel otherwise stays put
		float arrive = fresh ? life.shizune.mistinn.client.anim.Motion.outBack(Mth.clamp((StoryClient.FLASH_TICKS - flash + deltaTracker.getGameTimeDeltaPartialTick(false)) / 10.0F, 0.0F,
			1.0F)) : 1.0F;
		int x = MARGIN - Math.round((width + MARGIN) * (1.0F - arrive));
		int y = MARGIN;
		float glow = fresh ? Mth.clamp(flash / 20.0F, 0.0F, 1.0F) : 0.0F;
		g.fill(x, y, x + width, y + height, 0x90080A0E);
		g.fill(x, y, x + 2, y + height, fresh ? FLASH : 0xFF6B5B3A);
		int ty = y + 5;
		if (fresh) {
			// "new objective", pulsing as it fades
			float pulse = 0.6F + 0.4F * Mth.sin((flash + deltaTracker.getGameTimeDeltaPartialTick(false)) * 0.35F);
			int alpha = (int) (255 * Mth.clamp(pulse * Math.max(glow, 0.35F), 0.0F, 1.0F));
			g.text(font, Component.translatable("story.mistinn.new_objective"), x + 7, ty, alpha << 24 | (FLASH & 0xFFFFFF), true);
			ty += 11;
		}
		g.text(font, chapter, x + 7, ty, CHAPTER, true);
		ty += 12;
		g.text(font, objective, x + 7, ty, OBJECTIVE, true);
		ty += 12;
		for (var row : hintRows) {
			g.text(font, row, x + 7, ty, HINT, false);
			ty += 10;
		}
	}
}
