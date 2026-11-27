package life.shizune.mistinn.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * 揮擊: each weapon branch's swing, read from its clip ({@code anim/swing/<branch>.json}).
 * A swing has four beats: a short anticipation (the weapon is cocked), the strike (it
 * accelerates into the target and connects at the clip's {@code contact}), the follow-through
 * (momentum carries it past and the body with it) and a slow recovery. The hit itself lands on
 * the click, so the anticipation is kept short and the weight lives in the follow-through.
 *
 * <p>First person moves the held item; third person poses arms, torso and legs (two-handed
 * weapons bring the other hand onto the grip) and tilts the item about the grip. {@code side}
 * is 1 for the right arm and -1 for the left; it mirrors every sideways value.
 */
public final class WeaponSwings {
	/**
	 * For the animation sheet and motion reel tests only: when 0 or more, every swing is drawn
	 * frozen at this progress instead of its real one.
	 */
	public static float preview = -1.0F;

	/** 握點: where the hand closes, from the shoulder pivot, in model pixels (right arm). */
	private static final float HAND_REACH = 10.0F;

	private WeaponSwings() {
	}

	/** The progress to draw: the real one, or the frozen preview. */
	public static float progress(final float real) {
		return preview >= 0.0F ? preview : real;
	}

	public static Clip clip(final WeaponStyle style) {
		return Clip.get("swing/" + style.id);
	}

	/** When the weapon meets the target, as swing progress. */
	public static float contact(final WeaponStyle style) {
		return clip(style).number("contact", 0.2F);
	}

	// ── 第一人稱 First person ─────────────────────────────────────

	/** Moves the held item; called in place of the vanilla swing, after the arm transform. */
	public static void firstPerson(final WeaponStyle style, final float p, final PoseStack pose, final int side) {
		firstPerson(clip(style), p, pose, side);
	}

	/** {@link #firstPerson(WeaponStyle, float, PoseStack, int)} for any clip with a first-person group. */
	public static void firstPerson(final Clip clip, final float p, final PoseStack pose, final int side) {
		pose.translate(side * clip.at("first_person.tx", p), clip.at("first_person.ty", p), clip.at("first_person.tz", p));
		// the whole motion tipped up toward the eye, so a level sweep is seen from above its plane and not along it
		pose.rotateDegrees(Axis.XP, clip.at("first_person.lift", p));
		pose.rotateDegrees(Axis.YP, side * clip.at("first_person.ry", p));
		pose.rotateDegrees(Axis.ZP, side * clip.at("first_person.rz", p));
		pose.rotateDegrees(Axis.XP, clip.at("first_person.rx", p));
	}

	/** The view's sway with the swing: yaw, pitch and roll in degrees. */
	public static void camera(final WeaponStyle style, final float p, final int side, final float[] out) {
		camera(clip(style), p, side, out);
	}

	public static void camera(final Clip clip, final float p, final int side, final float[] out) {
		out[0] += side * clip.at("camera.yaw", p);
		out[1] += clip.at("camera.pitch", p);
		out[2] += side * clip.at("camera.roll", p);
	}

	// ── 第三人稱 Third person ─────────────────────────────────────

	/**
	 * Poses the model through the swing, in place of vanilla's attack animation. The clip's
	 * arm values are the arm's pose relative to the turning torso; they are blended in from (and
	 * back out to) whatever pose the arms had, so walking or crouching flows into the swing.
	 */
	public static void thirdPerson(final WeaponStyle style, final float p, final HumanoidModel<?> model, final ModelPart arm, final ModelPart other, final int side, final float ageScale) {
		Clip clip = clip(style);
		thirdPerson(clip, p, clip.weight(p), model, arm, other, side, ageScale);
	}

	/**
	 * Poses the model from any swing-shaped clip, owning the pose by {@code w} (1 replaces the
	 * arms' pose outright, 0 leaves it).
	 */
	public static void thirdPerson(final Clip clip, final float p, final float w, final HumanoidModel<?> model, final ModelPart arm, final ModelPart other, final int side, final float ageScale) {
		float bodyY = side * clip.rad("third_person.body_y", p);
		model.body.yRot = bodyY;
		model.body.xRot += clip.rad("third_person.body_x", p);
		// the shoulders ride on the turning torso, as vanilla keeps them
		model.rightArm.z = Mth.sin(bodyY) * 5.0F * ageScale;
		model.rightArm.x = -Mth.cos(bodyY) * 5.0F * ageScale;
		model.leftArm.z = -Mth.sin(bodyY) * 5.0F * ageScale;
		model.leftArm.x = Mth.cos(bodyY) * 5.0F * ageScale;
		// the swing goes where the head looks, more for a thrust than for a cut
		float look = clip.number("pitch_follow", 0.0F) * model.head.xRot;
		arm.xRot = Mth.lerp(w, arm.xRot, clip.rad("third_person.arm_x", p) + look);
		arm.yRot = Mth.lerp(w, arm.yRot, side * clip.rad("third_person.arm_y", p)) + bodyY;
		arm.zRot = Mth.lerp(w, arm.zRot, side * clip.rad("third_person.arm_z", p));
		if (clip.flag("two_handed")) {
			// a clip may let the other hand off the grip (its "grip" track below 1) where the
			// model's arms could not reach it without passing through the head or the torso
			float hold = clip.has("third_person.grip") ? Mth.clamp(clip.at("third_person.grip", p), 0.0F, 1.0F) : 1.0F;
			if (hold < 1.0F) {
				other.xRot = Mth.lerp(w, other.xRot, clip.rad("third_person.other_x", p));
				other.yRot = Mth.lerp(w, other.yRot, side * clip.rad("third_person.other_y", p)) + bodyY;
				other.zRot = Mth.lerp(w, other.zRot, side * clip.rad("third_person.other_z", p));
			}
			grip(model, arm, other, side, w * hold);
		} else {
			other.xRot = Mth.lerp(w, other.xRot, clip.rad("third_person.other_x", p));
			other.yRot = Mth.lerp(w, other.yRot, side * clip.rad("third_person.other_y", p)) + bodyY;
			other.zRot = Mth.lerp(w, other.zRot, side * clip.rad("third_person.other_z", p));
		}
		// the leg opposite the weapon arm steps into the blow, the other braces
		ModelPart front = side > 0 ? model.leftLeg : model.rightLeg;
		ModelPart back = side > 0 ? model.rightLeg : model.leftLeg;
		front.xRot += clip.rad("third_person.front_leg", p);
		back.xRot += clip.rad("third_person.back_leg", p);
	}

	/** Tilts the held item about the grip (degrees about the hand's x axis), before it is drawn. */
	public static void thirdPersonItem(final WeaponStyle style, final float p, final PoseStack pose) {
		thirdPersonItem(clip(style), p, pose);
	}

	public static void thirdPersonItem(final Clip clip, final float p, final PoseStack pose) {
		float tilt = clip.at("third_person.item_x", p);
		if (tilt != 0.0F) {
			pose.rotateDegrees(Axis.XP, tilt);
		}
	}

	/**
	 * 雙手握: points the other arm at the weapon hand, so both hands stay on the grip however
	 * the weapon arm moves. Solved in the model's root space.
	 */
	public static void grip(final HumanoidModel<?> model, final ModelPart arm, final ModelPart other, final int side, final float w) {
		// the weapon hand: the end of the arm, turned by the arm's rotation (applied x, y, z)
		float hx = -side * 1.0F;
		float hy = HAND_REACH;
		float hz = 0.0F;
		float cx = Mth.cos(arm.xRot);
		float sx = Mth.sin(arm.xRot);
		float y1 = hy * cx - hz * sx;
		float z1 = hy * sx + hz * cx;
		float cy = Mth.cos(arm.yRot);
		float sy = Mth.sin(arm.yRot);
		float x2 = hx * cy + z1 * sy;
		float z2 = -hx * sy + z1 * cy;
		float cz = Mth.cos(arm.zRot);
		float sz = Mth.sin(arm.zRot);
		float x3 = x2 * cz - y1 * sz;
		float y3 = x2 * sz + y1 * cz;
		// the other hand closes over the weapon hand
		float dx = arm.x + x3 - other.x;
		float dy = arm.y + y3 - other.y;
		float dz = arm.z + z2 - other.z;
		float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
		if (length < 1.0E-3F) {
			return;
		}
		float pitch = -(float) Math.acos(Mth.clamp(dy / length, -1.0F, 1.0F));
		float yaw = Math.abs(Mth.sin(pitch)) < 1.0E-3F ? other.yRot : (float) Mth.atan2(-dx, -dz);
		other.xRot = Mth.lerp(w, other.xRot, pitch);
		other.yRot = Mth.lerp(w, other.yRot, yaw);
		other.zRot = Mth.lerp(w, other.zRot, 0.0F);
	}
}
