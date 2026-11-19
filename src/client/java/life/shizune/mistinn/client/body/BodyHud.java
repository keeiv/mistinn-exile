package life.shizune.mistinn.client.body;

import life.shizune.mistinn.body.Body;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Takes the place of the heart row for hunters: a small figure beside the hotbar, each part
 * coloured by its health, destroyed parts dark. A destroyed head also closes in the view.
 */
public final class BodyHud implements HudElement {
	/** Where each part sits in the 18 x 28 figure: x, y, width, height. Seen from behind. */
	private static final int[][] SHAPE = {
		{6, 0, 6, 6},   // head
		{5, 7, 8, 10},  // torso
		{1, 7, 3, 10},  // left arm
		{14, 7, 3, 10}, // right arm
		{5, 18, 3, 10}, // left leg
		{10, 18, 3, 10} // right leg
	};
	public static final int WIDTH = 18;
	public static final int HEIGHT = 28;
	private static final int OUTLINE = 0xC0101010;
	private static final int DEAD = 0xFF4A1414;

	private final HudElement vanilla;

	public BodyHud(final HudElement vanilla) {
		this.vanilla = vanilla;
	}

	public static boolean active(final LocalPlayer player) {
		return player != null && BodyService.applies(player);
	}

	public static boolean headDestroyed() {
		LocalPlayer player = Minecraft.getInstance().player;
		return active(player) && BodyService.get(player).destroyed(BodyPart.HEAD);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (!active(player)) {
			this.vanilla.extractRenderState(g, deltaTracker);
			return;
		}
		Body body = BodyService.get(player);
		if (body.destroyed(BodyPart.HEAD)) {
			vignette(g);
		}
		// left of the hotbar, resting on the bottom edge
		int x = g.guiWidth() / 2 - 91 - WIDTH - 6;
		int y = g.guiHeight() - HEIGHT - 3;
		figure(g, body, x, y, 1);
	}

	/** Draws the figure at (x, y), each figure pixel {@code scale} screen pixels wide. */
	public static void figure(final GuiGraphicsExtractor g, final Body body, final int x, final int y, final int scale) {
		for (BodyPart part : BodyPart.values()) {
			int[] s = SHAPE[part.ordinal()];
			int px = x + s[0] * scale;
			int py = y + s[1] * scale;
			int w = s[2] * scale;
			int h = s[3] * scale;
			g.fill(px - 1, py - 1, px + w + 1, py + h + 1, OUTLINE);
			g.fill(px, py, px + w, py + h, colour(body.get(part)));
		}
	}

	/** Pale green when whole, through amber, to red; a dead part is near black red. */
	public static int colour(final float health) {
		if (health <= 0.0F) {
			return DEAD;
		}
		float share = Mth.clamp(health / BodyPart.MAX, 0.0F, 1.0F);
		int r;
		int gr;
		if (share > 0.5F) {
			float t = (share - 0.5F) * 2.0F;
			r = (int) Mth.lerp(t, 0xE0, 0x9C);
			gr = (int) Mth.lerp(t, 0xB0, 0xD8);
		} else {
			float t = share * 2.0F;
			r = (int) Mth.lerp(t, 0xD8, 0xE0);
			gr = (int) Mth.lerp(t, 0x3A, 0xB0);
		}
		return 0xFF000000 | r << 16 | gr << 8 | 0x48;
	}

	/** A dark frame closing in from all four edges; where two meet, the corners are darkest. */
	private static void vignette(final GuiGraphicsExtractor g) {
		int w = g.guiWidth();
		int h = g.guiHeight();
		int depth = Math.min(w, h) * 2 / 5;
		int dark = 0xE0000000;
		g.fillGradient(0, 0, w, depth, dark, 0);
		g.fillGradient(0, h - depth, w, h, 0, dark);
		// fillGradient only fades downwards: turn it a quarter for each side
		org.joml.Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(0.0F, h);
		pose.rotate((float) (-Math.PI / 2));
		g.fillGradient(0, 0, h, depth, dark, 0);
		pose.popMatrix();
		pose.pushMatrix();
		pose.translate(w, 0.0F);
		pose.rotate((float) (Math.PI / 2));
		g.fillGradient(0, 0, h, depth, dark, 0);
		pose.popMatrix();
	}
}
