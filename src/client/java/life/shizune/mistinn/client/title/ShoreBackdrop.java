package life.shizune.mistinn.client.title;

import life.shizune.mistinn.Mistinn;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

/**
 * The misty shore with the dead lighthouse, drawn to cover a screen: layered pixel art (480x270)
 * with drifting fog, glinting water, rising salt motes and the last ember in the lamp room, every
 * layer shifting a little with the mouse. The title screen and the mode choice stand on it.
 */
public final class ShoreBackdrop {
	private static final int ART_W = 480;
	private static final int ART_H = 270;
	private static final Identifier SKY = tex("sky");
	private static final Identifier CLIFF = tex("cliff");
	private static final Identifier SEA = tex("sea");
	private static final Identifier GLINTS = tex("glints");
	private static final Identifier FOG_FAR = tex("fog_far");
	private static final Identifier FOG_MID = tex("fog_mid");
	private static final Identifier FOG_NEAR = tex("fog_near");
	private static final Identifier SHORE = tex("shore");
	/** The dead lamp room's glass, in art pixels. */
	private static final int LAMP_X = 364;
	private static final int LAMP_Y = 67;
	private static final int MOTES = 28;

	private ShoreBackdrop() {
	}

	private static Identifier tex(final String name) {
		return Mistinn.id("textures/gui/title/" + name + ".png");
	}

	/** Draws the shore over the whole screen; {@code leftMist} thickens the mist behind a menu on the left. */
	public static void draw(final GuiGraphicsExtractor g, final int width, final int height, final int mouseX, final int mouseY, final float time, final boolean leftMist) {
		// Leave room for the nearest fog, which can shift eleven pixels sideways and six vertically.
		float scale = Math.max((width + 22.0F) / ART_W, (height + 12.0F) / ART_H);
		int w = Mth.ceil(ART_W * scale);
		int h = Mth.ceil(ART_H * scale);
		int ox = (width - w) / 2;
		int oy = (height - h) / 2;
		float lookX = Mth.clamp(mouseX / (float) Math.max(1, width) - 0.5F, -0.5F, 0.5F);
		float lookY = Mth.clamp(mouseY / (float) Math.max(1, height) - 0.5F, -0.5F, 0.5F);

		layer(g, SKY, ox, oy, w, h, 0.0F, lookX, lookY);
		layer(g, CLIFF, ox, oy, w, h, 4.0F, lookX, lookY);
		ember(g, ox, oy, scale, time, lookX, lookY);
		layer(g, SEA, ox, oy, w, h, 6.0F, lookX, lookY);
		scroll(g, GLINTS, ox, oy + Math.round(160 * scale), w, Math.round(60 * scale), scale, time * 6.0F, 6.0F, lookX, lookY, 0xFFFFFFFF);
		scroll(g, FOG_FAR, ox, oy + Math.round(96 * scale), w, Math.round(120 * scale), scale, time * 3.0F, 8.0F, lookX, lookY, 0xC0FFFFFF);
		scroll(g, FOG_MID, ox, oy + Math.round(132 * scale), w, Math.round(120 * scale), scale, -time * 5.0F, 12.0F, lookX, lookY, 0xD0FFFFFF);
		layer(g, SHORE, ox, oy, w, h, 16.0F, lookX, lookY);
		motes(g, ox, oy, scale, time);
		scroll(g, FOG_NEAR, ox, oy + Math.round(176 * scale), w, Math.round(120 * scale), scale, time * 9.0F, 22.0F, lookX, lookY, 0xB0FFFFFF);

		if (leftMist) {
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			pose.translate(0.0F, height);
			pose.rotate((float) (-Math.PI / 2));
			g.fillGradient(0, 0, height, Math.min(width / 2, 300), 0xC0101418, 0x00101418);
			pose.popMatrix();
		}
	}

	/** A full-screen layer, shifted against the mouse by {@code depth} screen pixels at most. */
	private static void layer(final GuiGraphicsExtractor g, final Identifier texture, final int x, final int y, final int w, final int h, final float depth, final float lookX, final float lookY) {
		int dx = Math.round(-lookX * depth);
		int dy = Math.round(-lookY * depth * 0.5F);
		g.blit(RenderPipelines.GUI_TEXTURED, texture, x + dx, y + dy, 0.0F, 0.0F, w, h, ART_W, ART_H, ART_W, ART_H);
	}

	/** A band that tiles sideways (960 art pixels wide), scrolled by {@code offset} art pixels. */
	private static void scroll(final GuiGraphicsExtractor g, final Identifier texture, final int x, final int y, final int w, final int h, final float scale,
		final float offset, final float depth, final float lookX, final float lookY, final int colour) {
		int texW = 960;
		int texH = Math.round(h / scale);
		float u = ((offset % texW) + texW) % texW;
		int dx = Math.round(-lookX * depth);
		int dy = Math.round(-lookY * depth * 0.5F);
		// the visible window may run past the texture's right edge: draw the rest from its start
		float first = Math.min(ART_W, texW - u);
		int firstW = Math.round(first * scale);
		g.blit(RenderPipelines.GUI_TEXTURED, texture, x + dx, y + dy, u, 0.0F, firstW, h, Math.round(first), texH, texW, texH, colour);
		if (first < ART_W) {
			float rest = ART_W - first;
			g.blit(RenderPipelines.GUI_TEXTURED, texture, x + dx + firstW, y + dy, 0.0F, 0.0F, w - firstW, h, Math.round(rest), texH, texW, texH, colour);
		}
	}

	/** The last ember in the dead lamp room: dim, unsteady, sometimes nearly out. */
	private static void ember(final GuiGraphicsExtractor g, final int ox, final int oy, final float scale, final float time, final float lookX, final float lookY) {
		float flicker = 0.55F + 0.25F * Mth.sin(time * 2.3F) + 0.2F * Mth.sin(time * 7.1F + 1.3F);
		int alpha = Mth.clamp((int) (flicker * 200), 20, 230);
		int x = ox + Math.round(LAMP_X * scale) + Math.round(-lookX * 4.0F);
		int y = oy + Math.round(LAMP_Y * scale) + Math.round(-lookY * 2.0F);
		int width = Math.max(2, Math.round(4 * scale));
		int height = Math.max(1, Math.round(2 * scale));
		g.fill(x - 1, y - 1, x + width + 1, y + height + 1, (alpha / 5) << 24 | 0xE89040);
		g.fill(x, y, x + width, y + height, alpha << 24 | 0xE89040);
	}

	/** Salt motes lifting off the shore and fading as they rise. */
	private static void motes(final GuiGraphicsExtractor g, final int ox, final int oy, final float scale, final float time) {
		int size = Math.max(1, Math.round(scale));
		for (int i = 0; i < MOTES; i++) {
			float seed = i * 12.9898F;
			float speed = 4.0F + (Mth.sin(seed) * 0.5F + 0.5F) * 6.0F;
			float life = 90.0F / speed;
			float t = ((time + i * 0.37F) % life) / life;
			float baseX = (Mth.sin(seed * 3.1F) * 0.5F + 0.5F) * ART_W;
			float x = baseX + Mth.sin(time * 0.8F + seed) * 6.0F;
			float y = 222.0F - t * 90.0F;
			int alpha = (int) (200 * Mth.sin(t * Mth.PI));
			g.fill(ox + Math.round(x * scale), oy + Math.round(y * scale), ox + Math.round(x * scale) + size, oy + Math.round(y * scale) + size, alpha << 24 | 0xE6E2D6);
		}
	}
}
