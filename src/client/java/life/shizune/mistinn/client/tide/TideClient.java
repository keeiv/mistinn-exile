package life.shizune.mistinn.client.tide;

import life.shizune.mistinn.tide.TideService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.util.Mth;

/**
 * The fog tide as the hunter sees it: over some ten seconds the fog rolls in until the world ends a
 * stone's throw off, grey and thick from close by, and as slowly it ebbs at dawn.
 */
public final class TideClient {
	/** Under the full tide, the world ends this near. */
	private static final float END = 28.0F;
	/** The tide's grey (darkened with the night, as the sky is). */
	private static final float[] MIST = {0.66F, 0.68F, 0.71F};
	/** How far the fog moves each tick, of the whole way: ten seconds in or out. */
	private static final float STEP = 0.005F;

	private static float haze;

	private TideClient() {
	}

	public static void tick(final Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			haze = 0.0F;
			return;
		}
		boolean up = mc.player.getAttachedOrElse(TideService.FOG, false);
		haze += Mth.clamp((up ? 1.0F : 0.0F) - haze, -STEP, STEP);
	}

	/** How far in the fog has come: 0 none, 1 the full tide. */
	public static float haze() {
		return haze;
	}

	/** Draws the tide's fog in over whatever the world was going to be drawn with. */
	public static void fog(final FogData fog) {
		if (haze <= 0.001F) {
			return;
		}
		float environment = Mth.lerp(haze, fog.environmentalEnd, END);
		float distance = Mth.lerp(haze, fog.renderDistanceEnd, END);
		fog.environmentalEnd = Math.min(fog.environmentalEnd, environment);
		fog.renderDistanceEnd = Math.min(fog.renderDistanceEnd, distance);
		fog.environmentalStart = Math.min(fog.environmentalStart, environment * 0.1F);
		fog.renderDistanceStart = Math.min(fog.renderDistanceStart, distance * 0.1F);
		fog.skyEnd = Math.min(fog.skyEnd, distance);
		fog.cloudEnd = Math.min(fog.cloudEnd, distance);
		// pale even by night, as fog under the moon is: it hides more than the dark does
		float light = Mth.clamp((0.299F * fog.color.x + 0.587F * fog.color.y + 0.114F * fog.color.z) / 0.6F, 0.25F, 1.0F);
		float mix = 0.75F * haze;
		fog.color.set(Mth.lerp(mix, fog.color.x, MIST[0] * light), Mth.lerp(mix, fog.color.y, MIST[1] * light),
			Mth.lerp(mix, fog.color.z, MIST[2] * light), fog.color.w);
	}
}
