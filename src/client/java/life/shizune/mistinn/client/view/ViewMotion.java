package life.shizune.mistinn.client.view;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import life.shizune.mistinn.stamina.StaminaService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * 視角的晃動: the view is carried on a body. Running, it rolls from foot to foot and drops a little at each footfall,
 * harder at a sprint; landing from a height, it is driven down and comes back up; out of breath, it heaves with each
 * breath. None of it moves where the hunter aims: only what they see.
 */
public final class ViewMotion {
	/** At a full sprint: the roll from foot to foot (degrees), the nod at each footfall (degrees), the drop (blocks). */
	public static final float SPRINT_ROLL = 1.1F;
	public static final float SPRINT_NOD = 0.9F;
	public static final float SPRINT_DROP = 0.045F;
	/** Walking moves the view this share of a sprint. */
	public static final float WALK = 0.35F;
	/** A landing: the view dips for this long (seconds); a fall of {@link #HARD_FALL} blocks or more dips it the most. */
	public static final float LANDING = 0.35F;
	public static final float HARD_FALL = 6.0F;
	public static final float LANDING_DROP = 0.22F;
	public static final float LANDING_NOD = 5.0F;
	/** Below this share of their stamina a hunter is out of breath, and the view heaves. */
	public static final float WINDED = 0.35F;

	private static float sprint;
	private static float sprintO;
	private static boolean wasOnGround = true;
	private static float fallen;
	private static long landedAt = Long.MIN_VALUE;
	private static float landing;
	private static float winded;
	private static float windedO;

	private ViewMotion() {
	}

	/** The stride's part of the motion: {roll, nod (degrees), drop (blocks)} at a point of the stride, 0 to 1 of a sprint. */
	public static float[] stride(final float position, final float speed, final float effort) {
		float phase = position * 0.6662F;
		float amount = Mth.clamp(speed, 0.0F, 1.0F) * Mth.lerp(Mth.clamp(effort, 0.0F, 1.0F), WALK, 1.0F);
		float foot = Math.abs(Mth.sin(phase));
		return new float[] {Mth.sin(phase) * SPRINT_ROLL * amount, foot * foot * SPRINT_NOD * amount, foot * SPRINT_DROP * amount};
	}

	/** A landing's dip at {@code since} seconds after it: down fast, back up slower, 0 to 1. */
	public static float dip(final float since) {
		if (since < 0.0F || since >= LANDING) {
			return 0.0F;
		}
		float t = since / LANDING;
		float back = 1.0F - (t - 0.25F) / 0.75F;
		return t < 0.25F ? Mth.sin(t / 0.25F * Mth.HALF_PI) : back * back;
	}

	/** How hard a fall of this many blocks lands, 0 to 1 (a step down is nothing). */
	public static float impact(final float blocks) {
		return Mth.clamp((blocks - 0.6F) / (HARD_FALL - 0.6F), 0.0F, 1.0F);
	}

	public static void tick(final Minecraft mc) {
		sprintO = sprint;
		windedO = winded;
		LocalPlayer player = mc.player;
		if (player == null) {
			sprint = 0.0F;
			winded = 0.0F;
			wasOnGround = true;
			return;
		}
		sprint += Mth.clamp((player.isSprinting() ? 1.0F : 0.0F) - sprint, -0.15F, 0.15F);
		boolean breathless = StaminaService.applies(player) && StaminaService.get(player) < StaminaService.max(player) * WINDED;
		winded += Mth.clamp((breathless ? 1.0F : 0.0F) - winded, -0.03F, 0.08F);
		if (!player.onGround()) {
			fallen = Math.max(fallen, (float) player.fallDistance);
		} else if (!wasOnGround) {
			landing = impact(fallen);
			landedAt = landing > 0.0F ? System.nanoTime() : Long.MIN_VALUE;
			fallen = 0.0F;
		}
		wasOnGround = player.onGround();
	}

	/** How out of breath the hunter is, 0 to 1 (eased), for the breathing they hear. */
	public static float winded(final float partial) {
		return Mth.lerp(partial, windedO, winded);
	}

	/** How hard the last landing was, while it is still being felt (a test hook). */
	public static float landing() {
		return landedAt != Long.MIN_VALUE && (System.nanoTime() - landedAt) / 1.0E9F < LANDING ? landing : 0.0F;
	}

	/** Adds the body's motion to the view. */
	public static void apply(final PoseStack pose, final LocalPlayer player, final float partial) {
		float roll = 0.0F;
		float nod = 0.0F;
		float drop = 0.0F;
		if (player.onGround() && !player.isPassenger()) {
			float[] stride = stride(player.walkAnimation.position(partial), player.walkAnimation.speed(partial), Mth.lerp(partial, sprintO, sprint));
			roll += stride[0];
			nod += stride[1];
			drop += stride[2];
		}
		float d = landedAt == Long.MIN_VALUE ? 0.0F : dip((System.nanoTime() - landedAt) / 1.0E9F) * landing;
		nod += d * LANDING_NOD;
		drop += d * LANDING_DROP;
		float breath = winded(partial);
		if (breath > 0.0F) {
			float age = player.tickCount + partial;
			// about one breath a second, deeper the more winded
			drop += (0.5F + 0.5F * Mth.sin(age * 0.33F)) * 0.02F * breath;
			nod += Mth.sin(age * 0.33F) * 0.5F * breath;
			roll += Mth.sin(age * 0.165F) * 0.3F * breath;
		}
		if (drop != 0.0F) {
			pose.translate(0.0F, drop, 0.0F);
		}
		if (roll != 0.0F || nod != 0.0F) {
			pose.rotateDegrees(Axis.ZP, roll);
			pose.rotateDegrees(Axis.XP, nod);
		}
	}
}
