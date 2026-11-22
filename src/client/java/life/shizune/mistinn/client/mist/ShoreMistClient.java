package life.shizune.mistinn.client.mist;

import java.util.List;
import life.shizune.mistinn.world.ShoreMist;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;

/**
 * The shore mist on screen. Near a dead lighthouse the world fades into a grey mist sooner and
 * sooner, until at the tower's foot it is gone a stone's throw away, the sky with it; the map
 * shows little more than where the hunter stands. The mist comes and goes over a few seconds
 * rather than at a line.
 */
public final class ShoreMistClient {
	/** How far the world shows at the mist's thickest, and where it starts to fade. */
	private static final float NEAR_END = 24.0F;
	private static final float NEAR_START = 2.0F;
	/** How much of the map, in blocks around the hunter, shows at the mist's thickest. */
	public static final float MAP_SIGHT = 16.0F;
	/** The mist's own grey, by daylight. */
	private static final float[] MIST = {0.70F, 0.73F, 0.76F};
	/** How fast the mist thickens or thins, a share of its full depth a tick. */
	private static final float EASE = 0.02F;

	private static List<BlockPos> lighthouses = List.of();
	private static float density;
	private static float lastEnd = -1.0F;

	private ShoreMistClient() {
	}

	/** The lighthouses near the hunter, as the server told. */
	public static void receive(final List<BlockPos> near) {
		lighthouses = List.copyOf(near);
	}

	public static void tick(final Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			lighthouses = List.of();
			density = 0.0F;
			return;
		}
		float target = mc.level.dimension().equals(Level.OVERWORLD) ? thickness(mc.player.getX(), mc.player.getZ()) : 0.0F;
		density += Mth.clamp(target - density, -EASE, EASE);
	}

	/** How thick the mist stands at a place: 0 out past its reach, 1 near a lighthouse. */
	public static float thickness(final double x, final double z) {
		double nearest = Double.MAX_VALUE;
		for (BlockPos lighthouse : lighthouses) {
			nearest = Math.min(nearest, Math.hypot(lighthouse.getX() + 0.5 - x, lighthouse.getZ() + 0.5 - z));
		}
		float t = (float) ((ShoreMist.REACH - nearest) / (ShoreMist.REACH - ShoreMist.THICKEST));
		return Mth.smoothstep(Mth.clamp(t, 0.0F, 1.0F));
	}

	/** How thick the mist is around the hunter now, 0 to 1. */
	public static float density() {
		return density;
	}

	/** How far the world showed in the last frame the mist touched, or -1. */
	public static float lastEnd() {
		return lastEnd;
	}

	/** Nothing is drawn further than this; a fog with no end (infinite) counts as ending here. */
	private static final float FARTHEST = 4096.0F;

	/**
	 * A fog distance drawn in toward {@code mist} by {@code d}, never pushed out. A fog with no end
	 * is treated as ending far off, so the blend never meets infinity (which would leave the
	 * screen black).
	 */
	static float thicken(final float distance, final float mist, final float d) {
		float from = Float.isFinite(distance) ? Math.min(distance, FARTHEST) : FARTHEST;
		return from <= mist ? from : mist + (from - mist) * (1.0F - d);
	}

	/** Thickens the fog the world is about to be drawn with; never thins it. */
	public static void apply(final FogData fog, final Camera camera) {
		if (density <= 0.001F) {
			lastEnd = -1.0F;
			return;
		}
		float d = density;
		fog.environmentalStart = thicken(fog.environmentalStart, NEAR_START, d);
		fog.environmentalEnd = thicken(fog.environmentalEnd, NEAR_END, d);
		fog.renderDistanceStart = thicken(fog.renderDistanceStart, NEAR_START, d);
		fog.renderDistanceEnd = thicken(fog.renderDistanceEnd, NEAR_END, d);
		fog.skyEnd = thicken(fog.skyEnd, NEAR_END, d);
		fog.cloudEnd = thicken(fog.cloudEnd, NEAR_END, d);
		if (camera.getFluidInCamera() == FogType.NONE) {
			// grey by day, and dark as the sky is at night
			float light = Mth.clamp((0.299F * fog.color.x + 0.587F * fog.color.y + 0.114F * fog.color.z) / 0.6F, 0.15F, 1.0F);
			fog.color.set(Mth.lerp(d, fog.color.x, MIST[0] * light), Mth.lerp(d, fog.color.y, MIST[1] * light),
				Mth.lerp(d, fog.color.z, MIST[2] * light), fog.color.w);
		}
		lastEnd = Math.min(fog.environmentalEnd, fog.renderDistanceEnd);
	}
}
