package life.shizune.mistinn.client.epic;

import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Between choosing the Hunter and the world: the dark, a heart beating slow and then faster, and on the last great
 * beat the epic's name. Its glow beats with the sound. A key or a click after the first beats goes on at once.
 */
public class EpicOpeningScreen extends Screen {
	/** When the heart beats (seconds), as the sound has it: lub-dub, slow, then quickening, then the great beat. */
	static final float[] BEATS = {0.30F, 0.55F, 1.55F, 1.80F, 2.65F, 2.88F, 3.55F, 3.76F, 4.30F, 4.48F, 5.00F};
	/** The great beat, and how long the screen holds after it. */
	private static final float GREAT = 5.00F;
	private static final float LENGTH = 7.2F;
	private static final float SKIPPABLE = 1.2F;

	private final Runnable proceed;
	private final long opened = System.nanoTime();
	private boolean sounded;
	private boolean done;

	public EpicOpeningScreen(final Runnable proceed) {
		super(Component.translatable("mode.mistinn.epic"));
		this.proceed = proceed;
	}

	private float seconds() {
		return (System.nanoTime() - this.opened) / 1.0E9F;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		if (!this.sounded && this.minecraft != null) {
			this.sounded = true;
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(MistinnSounds.EPIC_HEARTBEAT, 1.0F, 1.0F));
		}
		if (this.seconds() >= LENGTH) {
			this.go();
		}
	}

	private void go() {
		if (!this.done) {
			this.done = true;
			this.proceed.run();
		}
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		if (this.seconds() > SKIPPABLE) {
			this.go();
		}
		return true;
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (this.seconds() > SKIPPABLE) {
			this.go();
		}
		return true;
	}

	/** How strongly the heart's glow shows at a moment: a quick swell at each beat, fading. */
	static float pulse(final float t) {
		float glow = 0.0F;
		for (int i = 0; i < BEATS.length; i++) {
			float since = t - BEATS[i];
			if (since >= 0.0F && since < 0.6F) {
				float strength = i == BEATS.length - 1 ? 1.0F : (i % 2 == 0 ? 0.55F : 0.35F) * (0.6F + 0.4F * i / BEATS.length);
				glow = Math.max(glow, strength * (float) Math.exp(-since * 6.0));
			}
		}
		return glow;
	}

	@Override
	protected void extractPanorama(final GuiGraphicsExtractor g, final float a) {
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		g.fill(0, 0, this.width, this.height, 0xFF000000);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		float t = this.seconds();
		// the heart's glow, a dark red at the middle of the screen, swelling with each beat
		float glow = pulse(t);
		int cx = this.width / 2;
		int cy = this.height / 2;
		for (int ring = 6; ring >= 1; ring--) {
			int r = (int) ((30 + ring * 18) * (1.0F + glow * 0.25F));
			int alpha = (int) (glow * 40 * (7 - ring) / 6.0F);
			if (alpha > 0) {
				g.fill(cx - r, cy - r / 2, cx + r, cy + r / 2, alpha << 24 | 0x6A1010);
			}
		}
		// on the great beat the name, white out of the dark; the land's name under it
		if (t >= GREAT - 0.05F) {
			float in = Mth.clamp((t - GREAT) / 0.8F, 0.0F, 1.0F);
			float out = Mth.clamp((LENGTH - t) / 0.8F, 0.0F, 1.0F);
			int alpha = (int) (255 * Math.min(in, out));
			if (alpha > 4) {
				Component name = Component.translatable("mode.mistinn.epic");
				g.pose().pushMatrix();
				g.pose().translate(cx, cy - 12);
				g.pose().scale(3.0F, 3.0F);
				g.text(this.font, name, -this.font.width(name) / 2, -4, alpha << 24 | 0xF2EAD8, false);
				g.pose().popMatrix();
				Component land = Component.translatable("epic.mistinn.opening.land");
				int landAlpha = (int) (alpha * Mth.clamp((t - GREAT - 0.6F) / 0.8F, 0.0F, 1.0F));
				if (landAlpha > 4) {
					g.text(this.font, land, cx - this.font.width(land) / 2, cy + 18, landAlpha << 24 | 0x8FC8C0, false);
				}
			}
		}
	}
}
