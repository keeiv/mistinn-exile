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
 * light behind; then the emblem moves aside and the name comes in to its right; then all of it fades.
 */
public class TeahouseIntroScreen extends IntroScreen {
	private static final Identifier RING_BACK = Mistinn.id("textures/gui/intro/teahouse_ring_back.png");
	private static final Identifier STAR = Mistinn.id("textures/gui/intro/teahouse_star.png");
	private static final Identifier RING_FRONT = Mistinn.id("textures/gui/intro/teahouse_ring_front.png");
	private static final Identifier GLOW = Mistinn.id("textures/gui/intro/teahouse_glow.png");
	private static final Identifier WORDMARK = Mistinn.id("textures/gui/intro/teahouse_wordmark.png");
	private static final int EMBLEM = 256;
	private static final int WORD_W = 597;
	private static final int WORD_H = 77;
	/** The name's height (its texture, padding and all) against the emblem's, and the space between them. */
	private static final float WORD_SCALE = 0.25F;
	private static final float GAP = 0.04F;

	public TeahouseIntroScreen(final Runnable next) {
		super(Component.literal("MoriTeahouse"), next);
	}

	@Override
	protected float length() {
		return 4.6F;
	}

	@Override
	protected void draw(final GuiGraphicsExtractor g, final float t, final float fade) {
		// the lockup: the emblem, and the name to its right; the emblem forms alone in the middle, then moves aside for it
		float size = Math.min(Math.min(oneToOne(EMBLEM), this.height * 0.42F), this.width * 0.85F / (1.0F + GAP + WORD_SCALE * WORD_W / WORD_H));
		float wordH = size * WORD_SCALE;
		float wordW = wordH * WORD_W / WORD_H;
		float lockup = size + size * GAP + wordW;
		float name = ease(t, 2.0F, 0.8F);
		float lockupLeft = this.width / 2.0F - lockup / 2.0F;
		float centred = this.width / 2.0F - size / 2.0F;
		float left = centred + (lockupLeft - centred) * name;
		float cx = left + size / 2.0F;
		float cy = this.height / 2.0F;
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
		// the name, coming in from the right beside the emblem as it moves aside
		if (name > 0.0F) {
			float x = lockupLeft + size + size * GAP + (1.0F - name) * 10.0F;
			image(g, WORDMARK, x, cy - wordH / 2.0F, Math.round(wordW), Math.round(wordH), WORD_W, WORD_H, name * fade);
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
