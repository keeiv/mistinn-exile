package life.shizune.mistinn.client.ambience;

import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;

/**
 * 環境音: now and then, the fog shore is heard. Out under the sky, wind moving through the mist
 * over a far-off surf; under a roof on the coast, which in this world means inside one of the
 * dead lighthouses, wind moaning up the shaft and the old tower creaking. Each is a one-off
 * sound a few seconds long, spaced out at random so it never becomes a loop.
 */
public final class Ambience {
	/** Ticks between fog gusts outside, at least and at most. */
	private static final int FOG_MIN = 25 * 20;
	private static final int FOG_MAX = 50 * 20;
	/** Ticks between sounds inside a lighthouse, at least and at most. */
	private static final int TOWER_MIN = 12 * 20;
	private static final int TOWER_MAX = 25 * 20;

	private static int wait = FOG_MIN;

	private Ambience() {
	}

	public static void tick(final Minecraft mc) {
		if (mc.level == null || mc.player == null || mc.isPaused() || mc.level.dimension() != Level.OVERWORLD) {
			return;
		}
		if (--wait > 0) {
			return;
		}
		BlockPos at = BlockPos.containing(mc.player.getEyePosition());
		boolean sky = mc.level.canSeeSky(at);
		boolean coast = mc.level.getBiome(at).is(BiomeTags.IS_BEACH);
		if (coast && !sky) {
			play(mc, MistinnSounds.AMBIENT_LIGHTHOUSE);
			wait = between(mc, TOWER_MIN, TOWER_MAX);
		} else if (sky && at.getY() >= mc.level.getSeaLevel() - 4) {
			play(mc, MistinnSounds.AMBIENT_FOG);
			wait = between(mc, FOG_MIN, FOG_MAX);
		} else {
			// underground or deep in a building: try again shortly
			wait = 10 * 20;
		}
	}

	private static void play(final Minecraft mc, final SoundEvent sound) {
		mc.getSoundManager().play(SimpleSoundInstance.forAmbientAddition(sound));
	}

	private static int between(final Minecraft mc, final int min, final int max) {
		return min + mc.player.getRandom().nextInt(max - min + 1);
	}
}
