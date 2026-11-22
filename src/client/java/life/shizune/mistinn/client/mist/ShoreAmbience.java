package life.shizune.mistinn.client.mist;

import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

/**
 * 霧岸的聲音: in the shore mist the hunter hears the sea they cannot see, low surf rolling in and a wind that swells and
 * falls, a creak of wood far off. It rises as the mist thickens and fades away as it lifts, the same mist the screen
 * shows (a lighthouse's, or the Sunken Bell's).
 */
public final class ShoreAmbience {
	/** How loud the shore is at the mist's thickest. */
	private static final float LOUDEST = 0.55F;
	/** Below this the mist is too thin to hear the sea through. */
	private static final float HEARD = 0.05F;

	private static @Nullable Loop playing;

	private ShoreAmbience() {
	}

	public static void tick(final Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			if (playing != null) {
				playing.end();
				playing = null;
			}
			return;
		}
		if (ShoreMistClient.density() > HEARD && (playing == null || playing.isStopped())) {
			playing = new Loop();
			mc.getSoundManager().play(playing);
		}
	}

	/** Whether the shore is being heard now. */
	public static boolean playing() {
		return playing != null && !playing.isStopped();
	}

	/** The loop itself: it follows the mist from tick to tick, and stops when the mist is gone. */
	private static final class Loop extends AbstractTickableSoundInstance {
		Loop() {
			super(MistinnSounds.AMBIENT_SHORE_MIST, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
			this.looping = true;
			this.delay = 0;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.volume = volumeFor(ShoreMistClient.density());
		}

		@Override
		public void tick() {
			float density = ShoreMistClient.density();
			if (density <= 0.01F) {
				this.stop();
				return;
			}
			this.volume = volumeFor(density);
		}

		void end() {
			this.stop();
		}

		private static float volumeFor(final float density) {
			return Math.max(0.001F, density * LOUDEST);
		}
	}
}
