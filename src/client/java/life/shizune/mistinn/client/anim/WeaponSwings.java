package life.shizune.mistinn.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Swing shapes for each weapon branch, as functions of swing progress {@code p} (0 to 1).
 * First person moves the held item; third person moves the arms of the player model.
 * {@code side} is 1 for the right arm and -1 for the left.
 */
public final class WeaponSwings {
	private WeaponSwings() {
	}

	private static float smooth(final float from, final float to, final float p) {
		float t = Mth.clamp((p - from) / (to - from), 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	private static float arc(final float p) {
		return Mth.sin(p * Mth.PI);
	}

	// ── First person ───────────────────────────────────────────────

	public static void firstPerson(final WeaponStyle style, final float p, final PoseStack pose, final int side) {
		switch (style) {
			case SLASH -> {
				// a diagonal cut from the upper right down across to the lower left
				float sweep = smooth(0.0F, 0.6F, p);
				float out = arc(Math.min(1.0F, p * 1.25F));
				pose.translate(side * (0.15F - 0.55F * sweep) * out, 0.12F * out - 0.1F * sweep * out, -0.25F * out);
				pose.rotateDegrees(Axis.YP, side * (30.0F - 70.0F * sweep) * out);
				pose.rotateDegrees(Axis.ZP, side * (25.0F - 60.0F * sweep) * out);
				pose.rotateDegrees(Axis.XP, -55.0F * out);
			}
			case CLEAVE -> {
				// heave the blade up over the shoulder, then bring it down hard
				float raise = smooth(0.0F, 0.4F, p);
				float slam = smooth(0.4F, 0.6F, p);
				float recover = smooth(0.7F, 1.0F, p);
				float up = raise * (1.0F - slam);
				float down = slam * (1.0F - recover);
				pose.translate(side * -0.1F * up, 0.35F * up - 0.3F * down, 0.15F * up - 0.35F * down);
				pose.rotateDegrees(Axis.XP, 70.0F * up - 85.0F * down);
				pose.rotateDegrees(Axis.ZP, side * (12.0F * up - 6.0F * down));
			}
			case THRUST -> {
				// draw back, then drive the point straight ahead
				float back = smooth(0.0F, 0.25F, p) * (1.0F - smooth(0.25F, 0.4F, p));
				float jab = smooth(0.25F, 0.45F, p) * (1.0F - smooth(0.65F, 1.0F, p));
				pose.translate(side * -0.22F * jab, 0.06F * jab, 0.2F * back - 0.75F * jab);
				pose.rotateDegrees(Axis.XP, -50.0F * jab + 8.0F * back);
				pose.rotateDegrees(Axis.YP, side * 20.0F * jab);
			}
			case FLURRY -> {
				// two quick cuts in one swing: right to left, then back
				float phase = p * 2.0F;
				int dir = phase < 1.0F ? 1 : -1;
				float local = phase < 1.0F ? phase : phase - 1.0F;
				float out = arc(local);
				pose.translate(side * dir * (0.1F - 0.4F * local) * out, 0.05F * out, -0.2F * out);
				pose.rotateDegrees(Axis.ZP, side * dir * 45.0F * out);
				pose.rotateDegrees(Axis.XP, -35.0F * out);
			}
			case SMASH -> {
				// wind the hammer back to the side, then sweep it hard across the body
				float wind = smooth(0.0F, 0.35F, p) * (1.0F - smooth(0.35F, 0.5F, p));
				float sweep = smooth(0.35F, 0.62F, p) * (1.0F - smooth(0.75F, 1.0F, p));
				pose.translate(side * (0.25F * wind - 0.5F * sweep), 0.1F * wind - 0.05F * sweep, -0.25F * sweep);
				pose.rotateDegrees(Axis.YP, side * (40.0F * wind - 80.0F * sweep));
				pose.rotateDegrees(Axis.ZP, side * -30.0F * sweep);
				pose.rotateDegrees(Axis.XP, -20.0F * (wind + sweep));
			}
		}
	}

	// ── Third person ───────────────────────────────────────────────

	public static void thirdPerson(final WeaponStyle style, final float p, final ModelPart arm, final ModelPart otherArm, final ModelPart body, final int side) {
		switch (style) {
			case SLASH -> {
				float sweep = smooth(0.0F, 0.6F, p);
				float out = arc(Math.min(1.0F, p * 1.25F));
				arm.xRot = -0.4F - 1.5F * out;
				arm.yRot = side * (0.7F - 1.6F * sweep) * out;
				arm.zRot = side * (0.6F - 1.0F * sweep) * out;
				body.yRot = side * (0.35F - 0.7F * sweep) * out;
			}
			case CLEAVE -> {
				float raise = smooth(0.0F, 0.4F, p);
				float slam = smooth(0.4F, 0.6F, p);
				float recover = smooth(0.7F, 1.0F, p);
				float up = raise * (1.0F - slam);
				float down = slam * (1.0F - recover);
				float angle = -3.0F * up - 0.9F * down;
				// both hands on the grip
				arm.xRot = angle;
				arm.yRot = side * -0.25F * (up + down);
				otherArm.xRot = angle;
				otherArm.yRot = side * 0.45F * (up + down);
				body.xRot = 0.25F * down;
			}
			case THRUST -> {
				float back = smooth(0.0F, 0.25F, p) * (1.0F - smooth(0.25F, 0.4F, p));
				float jab = smooth(0.25F, 0.45F, p) * (1.0F - smooth(0.65F, 1.0F, p));
				arm.xRot = -1.2F - 0.4F * jab + 0.5F * back;
				arm.yRot = side * -0.15F * jab;
				arm.z = arm.z - 3.0F * jab + 2.0F * back;
				otherArm.xRot = -0.9F * (jab + back);
				otherArm.yRot = side * 0.5F * (jab + back);
				body.yRot = side * (-0.3F * jab + 0.25F * back);
			}
			case FLURRY -> {
				float phase = p * 2.0F;
				int dir = phase < 1.0F ? 1 : -1;
				float local = phase < 1.0F ? phase : phase - 1.0F;
				float out = arc(local);
				arm.xRot = -1.1F - 0.5F * out;
				arm.yRot = side * dir * (0.6F - 1.2F * local) * out;
				arm.zRot = side * dir * 0.5F * out;
				body.yRot = side * dir * 0.25F * out;
			}
			case SMASH -> {
				float wind = smooth(0.0F, 0.35F, p) * (1.0F - smooth(0.35F, 0.5F, p));
				float sweep = smooth(0.35F, 0.62F, p) * (1.0F - smooth(0.75F, 1.0F, p));
				// both hands on the haft, the whole body turning with the swing
				arm.xRot = -1.0F - 0.6F * (wind + sweep);
				arm.yRot = side * (1.0F * wind - 1.4F * sweep);
				otherArm.xRot = arm.xRot;
				otherArm.yRot = side * (1.3F * wind - 1.0F * sweep);
				body.yRot = side * (0.45F * wind - 0.6F * sweep);
			}
		}
	}
}
