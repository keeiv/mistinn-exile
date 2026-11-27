package life.shizune.mistinn.client.title;

import life.shizune.mistinn.Mistinn;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * The opening's second card: keeiv's mark, a lowercase k in two thin strokes with a small dot of the teahouse's colours
 * where an i would have its dot, and the name beside it. The stem is drawn down; the bent stroke follows it; the dot
 * comes up; the name comes in from the right; then all of it fades. Laid out as the lockup is drawn: the mark's
 * 256-pixel sheets at 0.845 of their size, the name at its own, on a lockup 160 pixels tall.
 */
public class KeeivIntroScreen extends IntroScreen {
	private static final Identifier STEM = Mistinn.id("textures/gui/intro/keeiv_stem.png");
	private static final Identifier BRANCH = Mistinn.id("textures/gui/intro/keeiv_branch.png");
	private static final Identifier SPARK = Mistinn.id("textures/gui/intro/keeiv_spark.png");
	private static final Identifier WORDMARK = Mistinn.id("textures/gui/intro/keeiv_wordmark.png");
	private static final int SHEET = 256;
	private static final int WORD_W = 317;
	private static final int WORD_H = 113;
	// the lockup, in its own pixels: where the mark's sheet and the name sit, and what the drawing covers
	private static final float LOCKUP_W = 480.0F;
	private static final float LOCKUP_H = 160.0F;
	private static final float SHEET_SCALE = 0.845F;
	private static final float SHEET_X = 8.0F - 53.0F * SHEET_SCALE;
	private static final float SHEET_Y = 8.0F - 43.0F * SHEET_SCALE;
	private static final float WORD_X = 163.0F;
	private static final float WORD_Y = 20.0F;
	private static final float MARK_TOP = 8.0F;
	private static final float MARK_BOTTOM = 152.0F;
	private static final float SPARK_X = 8.0F + (193.0F - 53.0F) * SHEET_SCALE;
	private static final float SPARK_Y = 8.0F + (52.5F - 43.0F) * SHEET_SCALE;

	public KeeivIntroScreen(final Runnable next) {
		super(Component.literal("keeiv"), next);
	}

	@Override
	protected float length() {
		return 3.6F;
	}

	@Override
	protected void draw(final GuiGraphicsExtractor g, final float t, final float fade) {
		// one lockup pixel to one screen pixel, smaller only if the window is narrow
		float unit = Math.min(oneToOne(1), this.width * 0.8F / LOCKUP_W);
		float x0 = this.width / 2.0F - LOCKUP_W * unit / 2.0F;
		float y0 = this.height / 2.0F - LOCKUP_H * unit / 2.0F;
		float sheetX = x0 + SHEET_X * unit;
		float sheetY = y0 + SHEET_Y * unit;
		int sheet = Math.round(SHEET * SHEET_SCALE * unit);
		int top = Math.round(y0 + MARK_TOP * unit);
		float height = (MARK_BOTTOM - MARK_TOP) * unit;

		// the stem, drawn down from its top
		float stem = ease(t, 0.15F, 0.6F);
		if (stem > 0.0F) {
			g.enableScissor(0, top, this.width, top + Math.round(height * stem));
			TeahouseIntroScreen.image(g, STEM, sheetX, sheetY, sheet, sheet, SHEET, SHEET, fade);
			g.disableScissor();
		}
		// the bent stroke, drawn down after it
		float branch = ease(t, 0.6F, 0.65F);
		if (branch > 0.0F) {
			g.enableScissor(0, top, this.width, top + Math.round(height * branch));
			TeahouseIntroScreen.image(g, BRANCH, sheetX, sheetY, sheet, sheet, SHEET, SHEET, fade);
			g.disableScissor();
		}
		// the dot, coming up from nothing a little past its size and settling
		float dot = ease(t, 1.15F, 0.45F);
		if (dot > 0.0F) {
			float grow = dot < 1.0F ? 0.4F + 0.75F * dot : 1.0F;
			grow = Math.min(grow, 1.0F + 0.15F * (1.0F - ease(t, 1.45F, 0.3F)));
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			float cx = x0 + SPARK_X * unit;
			float cy = y0 + SPARK_Y * unit;
			pose.translate(cx, cy);
			pose.scale(grow, grow);
			pose.translate(-cx, -cy);
			TeahouseIntroScreen.image(g, SPARK, sheetX, sheetY, sheet, sheet, SHEET, SHEET, dot * fade);
			pose.popMatrix();
		}
		// the name, coming in from the right
		float name = ease(t, 1.35F, 0.8F);
		if (name > 0.0F) {
			TeahouseIntroScreen.image(g, WORDMARK, x0 + (WORD_X + 10.0F * (1.0F - name)) * unit, y0 + WORD_Y * unit,
				Math.round(WORD_W * unit), Math.round(WORD_H * unit), WORD_W, WORD_H, name * fade);
		}
	}
}
