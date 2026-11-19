package life.shizune.mistinn.client.anim;

import net.minecraft.util.Mth;

/**
 * 介面動態: small easing helpers for screens and the HUD, so things arrive and leave with a
 * settle instead of a linear slide or a hard cut.
 */
public final class Motion {
	private Motion() {
	}

	/** 0 to 1 over {@code duration} from {@code start}, clamped. */
	public static float progress(final float time, final float start, final float duration) {
		return Mth.clamp((time - start) / duration, 0.0F, 1.0F);
	}

	/** Fast out, soft landing. */
	public static float outCubic(final float t) {
		float u = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
		return 1.0F - u * u * u;
	}

	/** Arrives, overshoots a little, and settles. */
	public static float outBack(final float t) {
		return Clip.Ease.OUT_BACK.apply(Mth.clamp(t, 0.0F, 1.0F));
	}

	/**
	 * 心跳: a heart's two beats (lub, dub) and the rest after them, as a 0 to 1 swell.
	 * {@code period} is seconds per beat; a dying heart lengthens it.
	 */
	public static float heartbeat(final float seconds, final float period) {
		float t = (seconds % period + period) % period;
		return pulse(t, 0.0F, 0.07F, 0.16F) + 0.6F * pulse(t, 0.22F, 0.06F, 0.18F);
	}

	/** One beat: a quick rise from {@code at} and a slower fall. */
	private static float pulse(final float t, final float at, final float rise, final float fall) {
		if (t < at) {
			return 0.0F;
		}
		if (t < at + rise) {
			return outCubic((t - at) / rise);
		}
		float f = (t - at - rise) / fall;
		return f >= 1.0F ? 0.0F : 1.0F - f * f;
	}

	/**
	 * Moves {@code current} toward {@code target} at a frame-rate independent pace: {@code rate}
	 * is how much of the remaining way is covered per second, roughly.
	 */
	public static float approach(final float current, final float target, final float dt, final float rate) {
		return current + (target - current) * Math.min(1.0F, dt * rate);
	}
}
