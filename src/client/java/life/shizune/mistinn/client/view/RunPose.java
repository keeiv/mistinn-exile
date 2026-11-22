package life.shizune.mistinn.client.view;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;

/**
 * 跑步的姿勢: a hunter at a sprint runs like one. The body leans into the run, the arms come up and pump hard against the
 * legs instead of swinging loose, the knees drive higher, the shoulders turn a little with each stride and the body dips
 * at each footfall. It is the same stride the view rolls and nods to ({@link ViewMotion#stride}), so what a hunter sees
 * of their own arms and what they feel in the view are one motion. A hand with a weapon in it pumps less.
 */
public final class RunPose {
	/** How much of a sprint a hunter is at, 0 (walking or still) to 1, eased; null when none. */
	public static final RenderStateDataKey<Float> KEY = RenderStateDataKey.create(() -> "mistinn:run");
	/** At a full sprint: the lean into the run (degrees), about the feet. */
	public static final float LEAN = 9.0F;
	/** The arms: how far forward they are carried (radians), and how hard they pump. */
	public static final float ARM_CARRY = -0.35F;
	public static final float ARM_PUMP = 1.0F;
	/** A hand that holds something pumps this share. */
	public static final float LADEN = 0.55F;
	/** The legs stride this much further than vanilla's. */
	public static final float LEG_DRIVE = 1.18F;
	/** The shoulders turn with the stride (radians), and the body dips at each footfall (blocks). */
	public static final float TWIST = 0.12F;
	public static final float DIP = 0.05F;
	/** Seconds to come up to a sprint's full pose, and to drop out of it. */
	private static final float EASE = 0.25F;

	/** For stills only: every hunter drawn at {share of a sprint, point of the stride}; null when off. */
	public static float[] preview;

	private static final class Runner {
		private float amount;
		private long last = System.nanoTime();
	}

	private static final Map<Integer, Runner> RUNNING = new HashMap<>();

	private RunPose() {
	}

	/** The share of a sprint this hunter is at now, eased by the clock; null when they are not running at all. */
	public static Float sample(final Avatar entity) {
		if (preview != null) {
			return preview[0];
		}
		if (RUNNING.size() > 256) {
			RUNNING.clear();
		}
		Runner runner = RUNNING.computeIfAbsent(entity.getId(), id -> new Runner());
		long now = System.nanoTime();
		float dt = Mth.clamp((now - runner.last) / 1.0E9F, 0.0F, 0.1F);
		runner.last = now;
		boolean running = entity.isSprinting() && entity.onGround() && !entity.isPassenger() && !entity.isCrouching();
		runner.amount = Mth.clamp(runner.amount + (running ? dt : -dt) / EASE, 0.0F, 1.0F);
		return runner.amount <= 0.0F ? null : runner.amount;
	}

	/** The stride's phase, as the view's motion counts it. */
	public static float phase(final HumanoidRenderState state) {
		return (preview != null ? preview[1] : state.walkAnimationPos) * 0.6662F;
	}

	/** Arms, legs and shoulders at a run; {@code amount} 0 to 1 of a sprint. Hands busy with a swing keep their arm. */
	public static void limbs(final HumanoidModel<?> model, final HumanoidRenderState state, final float amount) {
		float a = amount * (preview != null ? 1.0F : Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F));
		if (a <= 0.0F) {
			return;
		}
		float phase = phase(state);
		boolean swinging = state.swingAnimation > 0.0F;
		boolean rightLaden = !state.rightHandItemStack.isEmpty();
		boolean leftLaden = !state.leftHandItemStack.isEmpty();
		if (!(swinging && state.mainArm == HumanoidArm.RIGHT)) {
			float pump = Mth.cos(phase + Mth.PI) * ARM_PUMP * (rightLaden ? LADEN : 1.0F);
			model.rightArm.xRot = Mth.lerp(a, model.rightArm.xRot, ARM_CARRY + pump);
			model.rightArm.zRot = Mth.lerp(a, model.rightArm.zRot, 0.14F);
		}
		if (!(swinging && state.mainArm == HumanoidArm.LEFT)) {
			float pump = Mth.cos(phase) * ARM_PUMP * (leftLaden ? LADEN : 1.0F);
			model.leftArm.xRot = Mth.lerp(a, model.leftArm.xRot, ARM_CARRY + pump);
			model.leftArm.zRot = Mth.lerp(a, model.leftArm.zRot, -0.14F);
		}
		if (preview != null) {
			// a still has no stride of its own: give the legs vanilla's, at a full run
			model.rightLeg.xRot = Mth.cos(phase) * 1.4F;
			model.leftLeg.xRot = Mth.cos(phase + Mth.PI) * 1.4F;
		}
		model.rightLeg.xRot = Mth.lerp(a, model.rightLeg.xRot, model.rightLeg.xRot * LEG_DRIVE);
		model.leftLeg.xRot = Mth.lerp(a, model.leftLeg.xRot, model.leftLeg.xRot * LEG_DRIVE);
		// the shoulders turn against the leading leg, and the head stays on where the hunter looks
		float twist = Mth.sin(phase) * TWIST * a;
		model.body.yRot += twist;
		model.head.xRot -= LEAN * Mth.DEG_TO_RAD * a;
	}

	/** The whole body's lean into the run (degrees) and its dip at each footfall (blocks). */
	public static float[] root(final HumanoidRenderState state, final float amount) {
		float a = amount * (preview != null ? 1.0F : Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F));
		return new float[] {LEAN * a, Math.abs(Mth.sin(phase(state))) * DIP * a};
	}
}
