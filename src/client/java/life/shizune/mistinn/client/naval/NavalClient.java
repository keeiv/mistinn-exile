package life.shizune.mistinn.client.naval;

import life.shizune.mistinn.naval.Pirate;
import life.shizune.mistinn.naval.SailingShip;
import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.Music;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The sea fight as the hunter feels it: its music from the moment the ship is seen coming until the
 * last pirate is gone, the fog the entrance plays in, and a light haze over the water while it lasts.
 */
public final class NavalClient {
	/** The fight's music: it loops with no pause and cuts in over whatever was playing. */
	public static final Music FIGHT = new Music(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(MistinnSounds.MUSIC_NAVAL), 0, 0, true);
	/** How near pirates (or a ship sailing in) must be for the fight to be on. */
	private static final double NEAR = 80.0;
	/** The haze over the water while it is on: the world ends this far off at most. */
	private static final float HAZE = 150.0F;
	/** The haze's grey. */
	private static final float[] MIST = {0.62F, 0.66F, 0.70F};

	private static boolean fighting;
	private static float haze;

	private NavalClient() {
	}

	public static void tick(final Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			fighting = false;
			haze = 0.0F;
			return;
		}
		fighting = NavalIntro.current() != null
			|| !mc.level.getEntitiesOfClass(Pirate.class, mc.player.getBoundingBox().inflate(NEAR), Pirate::isAlive).isEmpty()
			|| !mc.level.getEntitiesOfClass(SailingShip.class, mc.player.getBoundingBox().inflate(NEAR * 2.0)).isEmpty();
		haze += Mth.clamp((fighting ? 1.0F : 0.0F) - haze, -0.01F, 0.01F);
	}

	/** The music for the sea fight going on around the hunter, or null when there is none. */
	public static @Nullable Music music() {
		return fighting ? FIGHT : null;
	}

	/** Draws the fog in: close during the entrance (drawing back as the ship comes), a light haze while the fight is on. */
	public static void fog(final FogData fog, final float partial) {
		NavalIntro intro = NavalIntro.current();
		float end = Float.MAX_VALUE;
		float d = haze;
		if (intro != null) {
			end = intro.fogEnd(partial);
			d = 1.0F;
		} else if (haze > 0.001F) {
			end = Mth.lerp(haze, 1000.0F, HAZE);
		}
		if (end == Float.MAX_VALUE) {
			return;
		}
		fog.environmentalEnd = Math.min(fog.environmentalEnd, end);
		fog.renderDistanceEnd = Math.min(fog.renderDistanceEnd, end);
		fog.environmentalStart = Math.min(fog.environmentalStart, end * 0.25F);
		fog.renderDistanceStart = Math.min(fog.renderDistanceStart, end * 0.25F);
		fog.skyEnd = Math.min(fog.skyEnd, end);
		fog.cloudEnd = Math.min(fog.cloudEnd, end);
		float light = Mth.clamp((0.299F * fog.color.x + 0.587F * fog.color.y + 0.114F * fog.color.z) / 0.6F, 0.15F, 1.0F);
		float mix = 0.7F * d;
		fog.color.set(Mth.lerp(mix, fog.color.x, MIST[0] * light), Mth.lerp(mix, fog.color.y, MIST[1] * light),
			Mth.lerp(mix, fog.color.z, MIST[2] * light), fog.color.w);
	}
}
