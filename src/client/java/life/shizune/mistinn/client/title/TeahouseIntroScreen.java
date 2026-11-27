package life.shizune.mistinn.client.title;

import life.shizune.mistinn.Mistinn;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * The opening's first card: the MoriTeahouse emblem, the four-pointed star thrust through a tilted ring, as the studio's
 * site draws it. The ring's far half is drawn in from the left; the star comes down into place, turning upright as it
 * comes; the ring's near half is drawn across in front of it from the right, so the star goes through the ring; a soft
 * light behind, then the name under it; then all of it fades.
 */
public class TeahouseIntroScreen extends IntroScreen {
	private static final Identifier RING_BACK = Mistinn.id("textures/gui/intro/teahouse_ring_back.png");
	private static final Identifier STAR = Mistinn.id("textures/gui/intro/teahouse_star.png");
	private static final Identifier RING_FRONT = Mistinn.id("textures/gui/intro/teahouse_ring_front.png");
	private static final Identifier GLOW = Mistinn.id("textures/gui/intro/teahouse_glow.png");
	private static final Identifier WORDMARK = Mistinn.id("textures/gui/intro/teahouse_wordmark.png");
	private static final int EMBLEM = 256;
	private static final int WORD_W = 401;
	private static final int WORD_H = 54;

	public TeahouseIntroScreen(final Runnable next) {
		super(Component.literal("MoriTeahouse"), next);
	}

	@Override
	protected float length() {
		return 4.6F;
	}

	@Override
	protected void draw(final GuiGraphicsExtractor g, final float t, final float fade) {
		float size = Math.min(oneToOne(EMBLEM), this.height * 0.42F);
		float cx = this.width / 2.0F;
		float cy = this.height / 2.0F - size * 0.14F;
		float left = cx - size / 2.0F;
		float top = cy - size / 2.0F;
		int side = Math.round(size);

		// a soft light behind, once the star is through the ring, settling low
		float light = ease(t, 1.45F, 0.35F) * (1.0F - 0.6F * ease(t, 1.9F, 0.9F));
		if (light > 0.0F) {
			float glow = size * (1.2F + 0.2F * ease(t, 1.45F, 1.2F));
			image(g, GLOW, cx - glow / 2.0F, cy - glow / 2.0F, Math.round(glow), Math.round(glow), EMBLEM, EMBLEM, light * fade);
		}
		// the ring's far half, drawn in from the left
		float back = ease(t, 0.35F, 0.75F);
		if (back > 0.0F) {
			g.enableScissor(Math.round(left), Math.round(top), Math.round(left + size * back), Math.round(top + size));
			image(g, RING_BACK, left, top, side, side, EMBLEM, EMBLEM, Math.min(1.0F, back * 1.5F) * fade);
			g.disableScissor();
		}
		// the star, coming down into place and turning upright
		float star = ease(t, 0.75F, 0.9F);
		if (star > 0.0F) {
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			pose.translate(cx, cy - (1.0F - star) * size * 0.22F);
			pose.rotate(-0.5F * (1.0F - star));
			float grow = 0.82F + 0.18F * star;
			pose.scale(grow, grow);
			image(g, STAR, -size / 2.0F, -size / 2.0F, side, side, EMBLEM, EMBLEM, star * fade);
			pose.popMatrix();
		}
		// the ring's near half, drawn across in front of it from the right: the star goes through the ring
		float front = ease(t, 1.3F, 0.7F);
		if (front > 0.0F) {
			g.enableScissor(Math.round(left + size * (1.0F - front)), Math.round(top), Math.round(left + size), Math.round(top + size));
			image(g, RING_FRONT, left, top, side, side, EMBLEM, EMBLEM, Math.min(1.0F, front * 1.5F) * fade);
			g.disableScissor();
		}
		// the name, rising a little into place
		float name = ease(t, 2.0F, 0.8F);
		if (name > 0.0F) {
			float h = size * 0.2F;
			float w = h * WORD_W / WORD_H;
			image(g, WORDMARK, cx - w / 2.0F, top + size * 1.04F + (1.0F - name) * 6.0F, Math.round(w), Math.round(h), WORD_W, WORD_H, name * fade);
		}
	}

	/** A whole texture drawn at a place that need not be whole, in the given size, faded by {@code alpha}. */
	static void image(final GuiGraphicsExtractor g, final Identifier id, final float x, final float y, final int w, final int h,
		final int texW, final int texH, final float alpha) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		int a = Math.round(255 * Math.max(0.0F, Math.min(1.0F, alpha)));
		g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0.0F, 0.0F, w, h, texW, texH, texW, texH, a << 24 | 0xFFFFFF);
		pose.popMatrix();
	}
}
