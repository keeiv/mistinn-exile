package life.shizune.mistinn.client.achievement;

import java.util.ArrayDeque;
import java.util.Deque;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.achievement.Achievement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * 成就達成: a strip of paper slides in at the top right with the achievement's emblem and name,
 * stays a few seconds and slides away. Several earned at once wait their turn.
 */
public final class AchievementToast implements HudElement {
	public static final Identifier TEXTURE = Mistinn.id("textures/gui/achievement_toast.png");
	private static final Identifier ICONS = Mistinn.id("textures/gui/guide/achievements.png");
	private static final int W = 160;
	private static final int H = 32;
	/** Seconds to slide in, to stay, to slide out. */
	public static final float IN = 0.3F;
	public static final float STAY = 4.0F;
	public static final float OUT = 0.3F;

	private static final Deque<Achievement> WAITING = new ArrayDeque<>();
	private static @Nullable Achievement showing;
	private static long since;

	/** The server says this hunter just earned an achievement. */
	public static void push(final String id) {
		Achievement achievement = Achievement.byId(id);
		if (achievement != null) {
			WAITING.add(achievement);
		}
	}

	/** The achievement on screen now, or null (also for the tests). */
	public static @Nullable Achievement showing() {
		advance();
		return showing;
	}

	private static void advance() {
		long now = System.nanoTime();
		if (showing != null && (now - since) / 1.0E9F > IN + STAY + OUT) {
			showing = null;
		}
		if (showing == null && !WAITING.isEmpty()) {
			showing = WAITING.poll();
			since = now;
		}
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		Achievement achievement = showing();
		if (achievement == null || mc.player == null) {
			return;
		}
		float t = (System.nanoTime() - since) / 1.0E9F;
		float shown = t < IN ? t / IN : t > IN + STAY ? 1.0F - (t - IN - STAY) / OUT : 1.0F;
		shown = Mth.clamp(shown, 0.0F, 1.0F);
		float eased = 1.0F - (1.0F - shown) * (1.0F - shown);
		int x = g.guiWidth() - Math.round((W + 4) * eased);
		int y = 4;
		g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, W, H, W, H);
		g.blit(RenderPipelines.GUI_TEXTURED, ICONS, x + 8, y + 8, (achievement.icon % 4) * 16, (achievement.icon / 4) * 16, 16, 16, 64, 64);
		g.text(mc.font, Component.translatable("gui.mistinn.achievement.earned"), x + 32, y + 7, 0xFF8A5A1E, false);
		g.text(mc.font, Component.translatable(achievement.key()), x + 32, y + 18, 0xFF3B2A1E, false);
	}
}
