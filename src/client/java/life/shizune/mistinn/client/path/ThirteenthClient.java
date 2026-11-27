package life.shizune.mistinn.client.path;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.client.ledger.Haunting;
import life.shizune.mistinn.path.Thirteenth;
import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

/**
 * The thirteenth's death, in its own eyes. There is no death screen: its face, too close, for a moment, with its
 * scream; then dark; then it is over, and the hunter it was wakes as themself.
 */
public final class ThirteenthClient {
	public static final Identifier FACE = Mistinn.id("textures/gui/story/stitched_face.png");
	/** Ticks the face is up; ticks of dark before the hunter wakes. */
	public static final int FACE_TICKS = 12;
	public static final int DARK_TICKS = 80;
	/** In the dark, after the face: what the twelve say, let go at last. */
	public static final String REST = "path.mistinn.thirteen.rest";
	private static boolean was;

	private ThirteenthClient() {
	}

	/** Each tick: whether the local hunter, while alive, walks as the thirteenth. */
	public static void tick(final Minecraft mc) {
		if (mc.player == null) {
			was = false;
		} else if (mc.player.isAlive()) {
			was = Thirteenth.stitched(mc.player);
		}
	}

	/** Whether the death now coming is the thirteenth's. */
	public static boolean dying() {
		return was;
	}

	/** The screen its death shows in place of the death screen. */
	public static Screen death() {
		was = false;
		return new Death(Haunting.scares());
	}

	/** Its face, too close, for a moment; then dark; then the hunter wakes. Esc does nothing. */
	public static final class Death extends Screen {
		private final boolean face;
		private int ticks;
		private boolean woke;

		Death(final boolean face) {
			super(Component.empty());
			this.face = face;
		}

		@Override
		protected void init() {
			if (this.ticks == 0 && this.face) {
				Minecraft mc = Minecraft.getInstance();
				mc.getSoundManager().play(SimpleSoundInstance.forUI(MistinnSounds.SCARE_STING, 1.0F, 1.0F));
				mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.GHAST_SCREAM, 0.5F, 1.0F));
			}
		}

		/** Whether the face is up (and not the dark after it). */
		public boolean showing() {
			return this.face && this.ticks < FACE_TICKS;
		}

		@Override
		public void tick() {
			Minecraft mc = Minecraft.getInstance();
			this.ticks++;
			if (!this.woke && this.ticks >= FACE_TICKS + DARK_TICKS && mc.player != null) {
				this.woke = true;
				mc.player.respawn();
			}
			// gone once the hunter is themself again
			if (this.woke && mc.player != null && mc.player.isAlive()) {
				mc.gui.setScreen(null);
			}
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
		public void extractBackground(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
			// nothing of the world behind shows
		}

		@Override
		public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
			g.fill(0, 0, this.width, this.height, 0xFF000000);
			if (this.showing()) {
				// filling the screen, too close: cropped rather than squeezed
				float scale = Math.max(this.width / 320.0F, this.height / 180.0F) * 1.08F;
				int w = Math.round(320 * scale);
				int h = Math.round(180 * scale);
				g.blit(RenderPipelines.GUI_TEXTURED, FACE, (this.width - w) / 2, (this.height - h) / 2, 0.0F, 0.0F, w, h, 320, 180, 320, 180);
			} else {
				// in the dark, faint, the twelve: they can stop now
				int dark = this.ticks - (this.face ? FACE_TICKS : 0);
				int alpha = Math.round(200 * net.minecraft.util.Mth.clamp(Math.min((dark - 14) / 16.0F, (DARK_TICKS - 6 - dark) / 14.0F), 0.0F, 1.0F));
				if (alpha >= 5) {
					Component words = Component.translatable(REST);
					g.text(this.font, words, (this.width - this.font.width(words)) / 2, this.height / 2 - 4, alpha << 24 | 0xC9D6DA, false);
				}
			}
		}
	}
}
