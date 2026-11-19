package life.shizune.mistinn.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Swings with weight. Every swing has four beats, as functions of swing progress {@code p}
 * (0 to 1): the wind-up strains against the weapon's weight (slow, and trembling at the end),
 * the strike breaks loose and accelerates, the weapon's momentum carries it past the line of
 * the cut (overshoot), and the body hauls it back slowly. Each moving part is a {@link Channel}
 * that names where it is at the top of the wind-up, at the strike, and how far momentum carries
 * it past. First person moves the held item (and, through {@link CameraSwing}, the view);
 * third person moves arms, torso, legs and head of the model. {@code side} is 1 for the right
 * arm and -1 for the left.
 */
public final class WeaponSwings {
	/**
	 * For the animation sheet test only: when 0 or more, every swing is drawn frozen at this
	 * progress instead of its real one.
	 */
	public static float preview = -1.0F;

	private WeaponSwings() {
	}

	/** The progress to draw: the real one, or the frozen preview. */
	public static float progress(final float real) {
		return preview >= 0.0F ? preview : real;
	}

	/** When each beat ends, as swing progress. Heavier weapons wind up longer. */
	public record Beat(float windEnd, float strikeEnd, float overEnd) {
	}

	/**
	 * One moving part: its pose at the top of the wind-up, at the strike, and how far past the
	 * strike momentum carries it. Rest is zero.
	 */
	public record Channel(float wind, float strike, float over) {
		public float at(final Beat beat, final float p, final float tremble) {
			return WeaponSwings.curve(beat, p, this.wind, this.strike, this.over, tremble);
		}
	}

	public static Beat beat(final WeaponStyle style) {
		return switch (style) {
			case SLASH -> new Beat(0.34F, 0.48F, 0.64F);
			case CLEAVE -> new Beat(0.46F, 0.58F, 0.74F);
			case THRUST -> new Beat(0.36F, 0.47F, 0.62F);
			case FLURRY -> new Beat(0.30F, 0.46F, 0.62F);
			case SMASH -> new Beat(0.44F, 0.58F, 0.76F);
		};
	}

	/** How hard the arms shake as the wind-up tops out: heavier weapons strain more. */
	private static float strain(final WeaponStyle style) {
		return switch (style) {
			case SLASH -> 0.6F;
			case CLEAVE -> 1.6F;
			case THRUST -> 0.8F;
			case FLURRY -> 0.2F;
			case SMASH -> 1.4F;
		};
	}

	private static float smooth(final float t) {
		float c = Mth.clamp(t, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}

	/**
	 * The four beats for one value. The wind-up eases in and then crawls (the weight resists);
	 * the strike starts slow and snaps (cubic); the overshoot rises past the strike and falls
	 * back; the recovery drifts home.
	 */
	static float curve(final Beat beat, final float p, final float wind, final float strike, final float over, final float tremble) {
		if (p <= 0.0F) {
			return 0.0F;
		}
		if (p < beat.windEnd()) {
			float t = p / beat.windEnd();
			float pulled = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
			float shake = t > 0.6F ? tremble * (t - 0.6F) / 0.4F : 0.0F;
			return wind * pulled + shake;
		}
		if (p < beat.strikeEnd()) {
			float t = (p - beat.windEnd()) / (beat.strikeEnd() - beat.windEnd());
			return Mth.lerp(t * t * t, wind, strike);
		}
		if (p < beat.overEnd()) {
			float t = (p - beat.strikeEnd()) / (beat.overEnd() - beat.strikeEnd());
			return strike + over * Mth.sin(t * Mth.PI) * (1.0F - 0.35F * t);
		}
		float t = (p - beat.overEnd()) / (1.0F - beat.overEnd());
		return Mth.lerp(smooth(t), strike, 0.0F);
	}

	/** A small fast shake, the same for every caller in a frame. */
	private static float tremble(final WeaponStyle style, final float seed) {
		double time = System.nanoTime() / 1.0E9;
		return strain(style) * (float) (Math.sin(time * 47.0 + seed) * 0.6 + Math.sin(time * 71.0 + seed * 2.3) * 0.4);
	}

	// ── First person ───────────────────────────────────────────────

	private record ItemPose(Channel tx, Channel ty, Channel tz, Channel rx, Channel ry, Channel rz) {
	}

	/**
	 * Translations in blocks, rotations in degrees. The motion stays mostly in the plane of the
	 * screen, so the weapon crosses the view instead of turning away behind the camera.
	 */
	private static ItemPose itemPose(final WeaponStyle style) {
		return switch (style) {
			// raised to the upper right, then a long diagonal cut down through the middle
			case SLASH -> new ItemPose(
				new Channel(0.18F, -0.50F, -0.15F), new Channel(0.22F, -0.18F, -0.06F), new Channel(0.05F, -0.20F, 0.00F),
				new Channel(10.0F, -45.0F, -10.0F), new Channel(15.0F, -25.0F, -8.0F), new Channel(35.0F, -60.0F, -15.0F));
			// heaved high overhead, then dropped like a falling weight
			case CLEAVE -> new ItemPose(
				new Channel(-0.15F, -0.10F, -0.03F), new Channel(0.37F, -0.30F, -0.08F), new Channel(0.10F, -0.25F, 0.00F),
				new Channel(50.0F, -80.0F, -15.0F), new Channel(0.0F, 0.0F, 0.0F), new Channel(10.0F, -10.0F, -3.0F));
			// drawn back to the hip, then driven out to full reach
			case THRUST -> new ItemPose(
				new Channel(0.05F, -0.25F, -0.05F), new Channel(-0.10F, 0.10F, 0.02F), new Channel(0.25F, -0.55F, -0.15F),
				new Channel(10.0F, -40.0F, -8.0F), new Channel(-8.0F, 15.0F, 5.0F), new Channel(-5.0F, 5.0F, 2.0F));
			// a short cocked wrist, then a whip across
			case FLURRY -> new ItemPose(
				new Channel(0.12F, -0.35F, -0.08F), new Channel(0.10F, -0.05F, -0.02F), new Channel(0.00F, -0.15F, 0.00F),
				new Channel(5.0F, -30.0F, -6.0F), new Channel(15.0F, -25.0F, -8.0F), new Channel(20.0F, -40.0F, -10.0F));
			// wound round to the right, then a sweeping blow that drags across to the left
			case SMASH -> new ItemPose(
				new Channel(0.30F, -0.50F, -0.15F), new Channel(0.10F, -0.05F, -0.03F), new Channel(0.05F, -0.20F, 0.03F),
				new Channel(-5.0F, -20.0F, -5.0F), new Channel(40.0F, -60.0F, -20.0F), new Channel(10.0F, -30.0F, -10.0F));
		};
	}

	public static void firstPerson(final WeaponStyle style, final float p, final PoseStack pose, final int side) {
		if (style == WeaponStyle.FLURRY) {
			// two cuts in one swing: right to left, then back again
			float local = p < 0.5F ? p * 2.0F : p * 2.0F - 1.0F;
			applyItem(style, local, pose, p < 0.5F ? side : -side);
			return;
		}
		applyItem(style, p, pose, side);
	}

	private static void applyItem(final WeaponStyle style, final float p, final PoseStack pose, final int side) {
		Beat beat = beat(style);
		ItemPose item = itemPose(style);
		float shake = tremble(style, 0.0F);
		pose.translate(side * item.tx().at(beat, p, 0.0F), item.ty().at(beat, p, shake * 0.004F), item.tz().at(beat, p, 0.0F));
		pose.rotateDegrees(Axis.YP, side * item.ry().at(beat, p, shake));
		pose.rotateDegrees(Axis.ZP, side * item.rz().at(beat, p, shake * 0.6F));
		pose.rotateDegrees(Axis.XP, item.rx().at(beat, p, shake));
	}

	// ── Third person ───────────────────────────────────────────────

	/** Radians. Arm, other arm, torso twist and lean, head, and the front and back legs. */
	private record BodyPose(Channel armX, Channel armY, Channel armZ, Channel otherX, Channel otherY,
		Channel bodyY, Channel bodyX, Channel headY, Channel frontLeg, Channel backLeg) {
	}

	private static BodyPose bodyPose(final WeaponStyle style) {
		return switch (style) {
			case SLASH -> new BodyPose(
				new Channel(-2.9F, -0.9F, -0.2F), new Channel(0.7F, -0.6F, -0.25F), new Channel(0.7F, -0.5F, -0.2F),
				new Channel(-0.5F, -0.9F, -0.2F), new Channel(-0.3F, 0.4F, 0.1F),
				new Channel(0.75F, -0.95F, -0.3F), new Channel(-0.15F, 0.45F, 0.12F), new Channel(-0.45F, 0.55F, 0.12F),
				new Channel(-0.2F, -0.55F, -0.1F), new Channel(0.15F, 0.45F, 0.1F));
			case CLEAVE -> new BodyPose(
				new Channel(-3.3F, -0.6F, 0.25F), new Channel(-0.3F, -0.1F, 0.0F), new Channel(0.0F, 0.0F, 0.0F),
				new Channel(-3.3F, -0.6F, 0.25F), new Channel(0.45F, 0.35F, 0.0F),
				new Channel(0.0F, 0.0F, 0.0F), new Channel(-0.45F, 0.7F, 0.18F), new Channel(0.0F, 0.0F, 0.0F),
				new Channel(-0.2F, -0.75F, -0.15F), new Channel(0.2F, 0.55F, 0.1F));
			case THRUST -> new BodyPose(
				new Channel(-0.9F, -1.6F, -0.15F), new Channel(-0.35F, 0.15F, 0.05F), new Channel(0.0F, 0.0F, 0.0F),
				new Channel(-0.5F, -1.2F, -0.1F), new Channel(0.2F, 0.6F, 0.1F),
				new Channel(0.45F, -0.45F, -0.15F), new Channel(-0.05F, 0.4F, 0.12F), new Channel(-0.3F, 0.3F, 0.05F),
				new Channel(0.1F, -0.85F, -0.15F), new Channel(-0.1F, 0.6F, 0.1F));
			case FLURRY -> new BodyPose(
				new Channel(-1.4F, -1.6F, -0.2F), new Channel(0.7F, -0.8F, -0.25F), new Channel(0.4F, -0.4F, -0.1F),
				new Channel(-0.4F, -0.6F, -0.1F), new Channel(-0.2F, 0.3F, 0.05F),
				new Channel(0.3F, -0.4F, -0.1F), new Channel(0.0F, 0.15F, 0.05F), new Channel(-0.2F, 0.25F, 0.05F),
				new Channel(-0.1F, -0.3F, -0.05F), new Channel(0.05F, 0.25F, 0.05F));
			case SMASH -> new BodyPose(
				new Channel(-1.3F, -1.1F, -0.2F), new Channel(1.4F, -0.6F, -0.3F), new Channel(0.0F, 0.0F, 0.0F),
				new Channel(-1.3F, -1.1F, -0.2F), new Channel(1.6F, -0.4F, -0.2F),
				new Channel(1.0F, -1.15F, -0.4F), new Channel(-0.2F, 0.4F, 0.12F), new Channel(-0.6F, 0.65F, 0.18F),
				new Channel(-0.15F, -0.6F, -0.15F), new Channel(0.2F, 0.5F, 0.12F));
		};
	}

	public static void thirdPerson(final WeaponStyle style, final float p, final HumanoidModel<?> model, final ModelPart arm, final ModelPart otherArm, final int side) {
		if (style == WeaponStyle.FLURRY) {
			float local = p < 0.5F ? p * 2.0F : p * 2.0F - 1.0F;
			applyBody(style, local, model, arm, otherArm, p < 0.5F ? side : -side, side);
			return;
		}
		applyBody(style, p, model, arm, otherArm, side, side);
	}

	/** {@code dir} flips the cut (the dagger's second cut); {@code side} is the swinging arm. */
	private static void applyBody(final WeaponStyle style, final float p, final HumanoidModel<?> model, final ModelPart arm, final ModelPart otherArm, final int dir, final int side) {
		Beat beat = beat(style);
		BodyPose b = bodyPose(style);
		float shake = tremble(style, 1.7F) * 0.02F;
		float bodyY = dir * b.bodyY().at(beat, p, 0.0F);
		model.body.yRot = bodyY;
		model.body.xRot += b.bodyX().at(beat, p, 0.0F);
		// the arms hang from the shoulders: keep them on the turning torso, as vanilla does
		ModelPart right = model.rightArm;
		ModelPart left = model.leftArm;
		right.z = Mth.sin(bodyY) * 5.0F;
		right.x = -Mth.cos(bodyY) * 5.0F;
		left.z = -Mth.sin(bodyY) * 5.0F;
		left.x = Mth.cos(bodyY) * 5.0F;
		arm.xRot = -0.3F + b.armX().at(beat, p, shake);
		arm.yRot = bodyY + dir * b.armY().at(beat, p, shake);
		arm.zRot = dir * b.armZ().at(beat, p, 0.0F);
		otherArm.xRot = -0.2F + b.otherX().at(beat, p, shake);
		otherArm.yRot = bodyY + dir * b.otherY().at(beat, p, 0.0F);
		model.head.yRot += dir * b.headY().at(beat, p, 0.0F);
		// a planted stance on top of the walk: the leg opposite the swinging arm steps into the blow
		ModelPart front = side > 0 ? model.leftLeg : model.rightLeg;
		ModelPart back = side > 0 ? model.rightLeg : model.leftLeg;
		front.xRot += b.frontLeg().at(beat, p, 0.0F);
		back.xRot += b.backLeg().at(beat, p, 0.0F);
	}
}
