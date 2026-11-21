package life.shizune.mistinn.client.combat;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.client.anim.HeavyClient;
import life.shizune.mistinn.weapon.HeavyService;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * 蓄力條: a small gauge under the crosshair while a heavy blow charges. It fills with lamp
 * light; the part below the least charge that strikes stays dim, and a full charge flashes.
 */
public final class ChargeHud implements HudElement {
	public static final Identifier TEXTURE = Mistinn.id("textures/gui/charge.png");
	private static final int W = 32;
	private static final int H = 5;
	/** The slot the fill shows in, inside the frame. */
	private static final int SLOT_X = 2;
	private static final int SLOT_W = 28;

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.gui.hud.isHidden() || !mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		HeavyClient.Pose pose = HeavyClient.sample(player, deltaTracker.getGameTimeDeltaPartialTick(false));
		if (pose == null || pose.phase() != HeavyClient.Phase.CHARGE) {
			return;
		}
		int x = g.guiWidth() / 2 - W / 2;
		int y = g.guiHeight() / 2 + 9;
		g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, W, H, W, H * 3);
		boolean full = pose.charge() >= 1.0F;
		int fill = full ? SLOT_W : Math.round(SLOT_W * pose.charge());
		if (fill > 0) {
			// below the least charge that strikes, the light is held back
			int colour = pose.charge() < HeavyService.MIN_CHARGE ? 0x80FFFFFF : 0xFFFFFFFF;
			float row = full && (player.tickCount / 3) % 2 == 0 ? H * 2 : H;
			g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + SLOT_X, y, SLOT_X, row, fill, H, W, H * 3, colour);
		}
	}
}
