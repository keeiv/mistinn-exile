package life.shizune.mistinn.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * 擺姿: plays a whole-body clip on a humanoid. A clip's {@code limbs} group turns the parts
 * ({@code head_x}, {@code body_x}, {@code right_arm_x/y/z}, {@code left_leg_x}, ... in
 * degrees); its {@code root} group moves the whole body in the renderer ({@code y} and
 * {@code z} in blocks, {@code z} forward; {@code pitch} forward and {@code roll} to the right in
 * degrees, about a point {@code pivot} blocks up).
 *
 * <p>Clips are written for a fall or turn to the right; {@code mirror} -1 plays them to the
 * left, swapping the sides and turning every sideways angle the other way. A clip that turns
 * the torso ({@code body_y}) carries the shoulders and arms round with it; its arm angles are
 * then relative to the torso.
 */
public final class Poser {
	private Poser() {
	}

	/**
	 * Poses the limbs, blended over whatever pose the model had by {@code weight} (1 = the clip
	 * owns the pose).
	 */
	public static void limbs(final Clip clip, final float p, final HumanoidModel<?> model, final int mirror, final float weight) {
		part(clip, p, "head", model.head, mirror, weight);
		part(clip, p, "body", model.body, mirror, weight);
		boolean swap = mirror < 0;
		boolean twist = clip.has("limbs.body_y");
		part(clip, p, "right_arm", swap ? model.leftArm : model.rightArm, mirror, weight);
		part(clip, p, "left_arm", swap ? model.rightArm : model.leftArm, mirror, weight);
		part(clip, p, "right_leg", swap ? model.leftLeg : model.rightLeg, mirror, weight);
		part(clip, p, "left_leg", swap ? model.rightLeg : model.leftLeg, mirror, weight);
		if (twist) {
			// the shoulders ride on the turning torso, and the arms turn with it
			float bodyY = model.body.yRot;
			float spread = Math.abs(model.rightArm.x);
			model.rightArm.z = Mth.sin(bodyY) * spread;
			model.rightArm.x = -Mth.cos(bodyY) * spread;
			model.leftArm.z = -Mth.sin(bodyY) * spread;
			model.leftArm.x = Mth.cos(bodyY) * spread;
			model.rightArm.yRot += bodyY;
			model.leftArm.yRot += bodyY;
		}
	}

	private static void part(final Clip clip, final float p, final String name, final ModelPart part, final int mirror, final float weight) {
		String prefix = "limbs." + name;
		if (clip.has(prefix + "_x")) {
			part.xRot = Mth.lerp(weight, part.xRot, clip.rad(prefix + "_x", p));
		}
		if (clip.has(prefix + "_y")) {
			part.yRot = Mth.lerp(weight, part.yRot, mirror * clip.rad(prefix + "_y", p));
		}
		if (clip.has(prefix + "_z")) {
			part.zRot = Mth.lerp(weight, part.zRot, mirror * clip.rad(prefix + "_z", p));
		}
	}

	/** Moves and turns the whole body; call where the renderer sets up the body's rotations. */
	public static void root(final Clip clip, final float p, final PoseStack pose, final int mirror, final float scale) {
		root(clip.at("root.x", p), clip.at("root.y", p), clip.at("root.z", p), clip.at("root.yaw", p), clip.at("root.roll", p), clip.at("root.pitch", p),
			clip.number("pivot", 0.9F), pose, mirror, scale);
	}

	/** {@link #root(Clip, float, PoseStack, int, float)} from values already worked out (two clips blended, say). */
	public static void root(final float x, final float y, final float z, final float yaw, final float roll, final float pitch, final float pivot,
		final PoseStack pose, final int mirror, final float scale) {
		// here the body faces -z, +y up and its right toward +x: a forward pitch turns the head
		// toward -z, a roll to the right lowers +x
		pose.translate(mirror * x * scale, y * scale, -z * scale);
		pose.rotateDegrees(Axis.YP, -mirror * yaw);
		pose.translate(0.0F, pivot * scale, 0.0F);
		pose.rotateDegrees(Axis.ZP, -mirror * roll);
		pose.rotateDegrees(Axis.XP, -pitch);
		pose.translate(0.0F, -pivot * scale, 0.0F);
	}
}
