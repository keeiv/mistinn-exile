package life.shizune.mistinn.client.view;

import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.client.mixin.ItemLayerAccessor;
import life.shizune.mistinn.client.mixin.ItemLayersAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * 自己的手臂: in the hunter's own view the work is done by the body's own arms, from its own shoulders. Where a hand
 * should be is found by the hand pass, which works out every motion of a held thing in front of the eye (a swing, a
 * bow drawn, food brought up, a needle put in) but draws none of it ({@link Hold}); the body's arm then turns from its
 * shoulder toward that place and its fist closes there, and what it holds is drawn in that fist, turned as the motion
 * has it. The hand pass runs after the body is drawn, so the body follows it a frame behind.
 *
 * <p>An arm is one straight piece from shoulder to fist, so it reaches only so far; a place further off is reached
 * toward, and the held thing comes back with the fist. All of this is in the view's space (the eye at the origin
 * looking down -z, x to the right, y up, in blocks) until it is turned into the body model's own.
 */
public final class OwnArms {
	/** Set on the hunter's own body while it is drawn: from its model's space to the view's. */
	public static final RenderStateDataKey<Matrix4f> TO_VIEW = RenderStateDataKey.create(() -> "mistinn:own_arms_to_view");
	/** And from the view's space to the world's as it is drawn around the eye. */
	public static final RenderStateDataKey<Matrix4f> FROM_VIEW = RenderStateDataKey.create(() -> "mistinn:own_arms_from_view");

	/** How large a held thing is drawn in the body's fist, against how large the game draws it in front of the eye. */
	private static final float ITEM_SIZE = 0.85F;
	/**
	 * The hand pass moves a held thing about a place low at the side of the view, much further off than a fist can reach
	 * from the body's shoulder. Its motion is taken about this point instead, for the right hand (the left mirrors x):
	 * the arm points from its shoulder the way the work lies from here, so a thing held plainly ends up in a fist
	 * before the chest, a little right of the middle, and every motion keeps its shape, only nearer.
	 */
	private static final Vector3fc PIVOT = new Vector3f(0.66F, -0.62F, -0.12F);
	/**
	 * At least this much of an arm's reach goes forward, away from the eye: an arm turned further across the face would
	 * bring the fist right up against the eye and fill the view.
	 */
	private static final float FORWARD = 0.72F;
	/**
	 * A fist at work is never nearer the eye than this, in blocks: the shoulder is brought forward under the eye as far as
	 * it takes, up to {@link #SHRUG}. Seen this close an arm is as large as the game's own hand in front of the eye.
	 */
	private static final float DEPTH = 0.55F;
	/**
	 * An arm held out to be worked on comes only this far in front of the eye: from shoulders a body's width apart, two
	 * fists meet in front of the chest, not further off.
	 */
	private static final float HELD_OUT = 0.55F;
	/** How far back from its fist an arm held out is worked on, in blocks: near the wrist, where the other hand reaches. */
	private static final float WRIST = 0.08F;
	private static final float SHRUG = 0.35F;
	/** Where the middle of the right arm's fist is from its shoulder, in the arm model's blocks (the left mirrors x). */
	private static final float FIST_X = -1.0F / 16.0F;
	/** The arm's own front, the thumb's side of its fist, where what it holds leaves the fist. */
	private static final Vector3fc FRONT = new Vector3f(0.0F, 0.0F, -1.0F);
	private static final Vector3fc DOWN = new Vector3f(0.0F, 1.0F, 0.0F);
	private static final float TINY = 1.0E-6F;
	/** How thick the hand pass once drew an arm held out to be worked on, in blocks; a treatment's clip is laid out on it. */
	private static final float CLIP_HALF = 0.1F;
	/** How far a tool may be moved along the arm held out, from where its clip puts it, so the hand that holds it reaches. */
	private static final float SLIDE = 0.25F;
	/**
	 * How long what the hand pass found holds good, in nanoseconds: with the HUD hidden it does not run, and the arms
	 * hang rather than replay what it found before.
	 */
	private static final long FRESH = 100_000_000L;

	/**
	 * What a hand is doing in front of the eye: where its fist closes ({@code fist}), which way what it holds runs on out
	 * of the fist ({@code along}), and what it holds as it is drawn there (from the item's model to the view's space;
	 * null for an empty hand). For a tool put to the other arm, {@code tip} is its working point.
	 */
	public record Hold(Vector3f fist, Vector3f along, @Nullable Matrix4f item, @Nullable Vector3f tip) {
	}

	/**
	 * An arm held out empty for the other to work on: where the work wants its fist and its elbow, and which way the
	 * inside of the arm is turned ({@code turn}, radians: 0 up, a quarter turn toward the eye).
	 */
	public record Present(Vector3f fist, Vector3f elbow, float turn) {
	}

	/** Found in the last hand pass, by hand. Render thread only. */
	private static final Hold[] HOLD = new Hold[2];
	private static final Present[] PRESENT = new Present[2];
	/** Where each arm's held thing is drawn now, by arm, from its model to the view's space; null where the game places it. */
	private static final Matrix4f[] PLACED = new Matrix4f[2];
	/** Which arm is held out to be worked on now; its held thing is put away. */
	private static final boolean[] PRESENTED = new boolean[2];
	private static float placedWeight;
	private static long foundAt;

	private OwnArms() {
	}

	/** The hand pass begins: what it found the last time is forgotten. */
	public static void begin() {
		HOLD[0] = null;
		HOLD[1] = null;
		PRESENT[0] = null;
		PRESENT[1] = null;
	}

	public static void hold(final InteractionHand hand, final Hold hold) {
		HOLD[hand.ordinal()] = hold;
		foundAt = System.nanoTime();
	}

	public static void present(final InteractionHand hand, final Present present) {
		PRESENT[hand.ordinal()] = present;
		foundAt = System.nanoTime();
	}

	/** Whether the arm is held out now to be worked on, what it held put away. */
	public static boolean presented(final HumanoidArm arm) {
		return PRESENTED[arm.ordinal()];
	}

	/**
	 * Poses the arms of the hunter's own body for the work in front of the eye, as far as they are raised to it. Called
	 * once the body has been posed for everything else.
	 */
	public static void pose(final PlayerModel model, final AvatarRenderState state, final float raised) {
		PLACED[0] = null;
		PLACED[1] = null;
		PRESENTED[0] = false;
		PRESENTED[1] = false;
		placedWeight = raised;
		Matrix4f toView = state.getData(TO_VIEW);
		if (toView == null || raised <= 0.0F || System.nanoTime() - foundAt > FRESH) {
			return;
		}
		Matrix4f toModel = new Matrix4f(toView).invert();
		// first the arm held out to be worked on, then the one that works on it
		Vector3f[] heldOut = null;
		Present design = null;
		for (InteractionHand hand : InteractionHand.values()) {
			Present present = PRESENT[hand.ordinal()];
			if (present == null) {
				continue;
			}
			HumanoidArm arm = arm(state, hand);
			ModelPart part = model.getArm(arm);
			int sign = arm == HumanoidArm.RIGHT ? 1 : -1;
			Vector3f inside = toModel.transformDirection(new Vector3f(0.0F, Mth.cos(present.turn()), Mth.sin(present.turn())));
			Vector3f toward = toModel.transformPosition(forward(part, reach(present.fist(), toView.transformPosition(shoulder(part)), sign), toView, toModel, raised, HELD_OUT));
			Vector3f fist = aim(part, sign, toward, new Vector3f(sign, 0.0F, 0.0F), inside, raised);
			heldOut = new Vector3f[] {toView.transformPosition(fist), toView.transformPosition(shoulder(part))};
			design = present;
			PRESENTED[arm.ordinal()] = true;
			OwnHand.drawn(hand, heldOut[0], heldOut[1]);
		}
		for (InteractionHand hand : InteractionHand.values()) {
			Hold hold = HOLD[hand.ordinal()];
			if (hold == null) {
				continue;
			}
			HumanoidArm arm = arm(state, hand);
			ModelPart part = model.getArm(arm);
			int sign = arm == HumanoidArm.RIGHT ? 1 : -1;
			Vector3f shoulderView = toView.transformPosition(shoulder(part));
			Vector3f toward;
			boolean tool = hold.tip() != null && heldOut != null && design != null;
			if (tool) {
				// a tool put to the arm held out is reached for where it is, the shoulder leaning in if it must
				hold = putTo(hold, design, heldOut, shoulderView, sign);
				toward = lean(part, hold.fist(), toView, toModel, raised);
			} else {
				toward = forward(part, reach(hold.fist(), shoulderView, sign), toView, toModel, raised, DEPTH);
			}
			Vector3f along = toModel.transformDirection(new Vector3f(hold.along()));
			Vector3f fistModel = aim(part, sign, toModel.transformPosition(toward, new Vector3f()), FRONT, along, raised);
			Vector3f fist = toView.transformPosition(fistModel);
			OwnHand.drawn(hand, fist, toView.transformPosition(shoulder(part)));
			Matrix4f item = hold.item();
			if (item != null) {
				Vector3f grip = hold.fist();
				// a tool put to the arm held out is already drawn to size, about its working point
				PLACED[arm.ordinal()] = tool ? new Matrix4f().translation(new Vector3f(fist).sub(grip)).mul(item)
					: new Matrix4f().translation(fist).scale(ITEM_SIZE).translate(-grip.x, -grip.y, -grip.z).mul(item);
				if (tool) {
					OwnHand.worked(new Vector3f(hold.tip()).sub(grip).add(fist));
				}
			}
		}
	}

	/**
	 * Draws what an arm of the hunter's own body holds where its work has it, instead of where the game puts it in the
	 * hand; the pose stack is the one the item is about to be drawn with.
	 */
	public static void place(final ArmedEntityRenderState state, final HumanoidArm arm, final ItemStackRenderState item, final PoseStack poseStack) {
		Matrix4f placed = PLACED[arm.ordinal()];
		Matrix4f fromView = state.getData(FROM_VIEW);
		if (placed == null || fromView == null || placedWeight <= 0.0F) {
			return;
		}
		ItemLayersAccessor layers = (ItemLayersAccessor) item;
		if (layers.mistinn$layerCount() == 0) {
			return;
		}
		// the item will be set in the hand as it is when seen from outside: that is undone, and its own place taken
		PoseStack.Pose display = new PoseStack.Pose();
		((ItemLayerAccessor) layers.mistinn$layers()[0]).mistinn$applyTransform(display);
		Matrix4f there = new Matrix4f(fromView).mul(placed).mul(new Matrix4f(display.pose()).invert());
		Matrix4f here = poseStack.last().pose();
		Matrix4f wanted = blend(here, there, placedWeight);
		poseStack.mulPose(new Matrix4f(here).invert().mul(wanted));
	}

	/**
	 * Turns an arm from its shoulder toward {@code target} (in the model's space), its own {@code local} axis turned as
	 * near {@code toward} as it goes, as far as {@code weight} from the pose it had. Returns where its fist is now.
	 */
	private static Vector3f aim(final ModelPart part, final int sign, final Vector3fc target, final Vector3fc local, final Vector3fc toward, final float weight) {
		Vector3f shoulder = shoulder(part);
		Quaternionf pose = new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
		Vector3f down = new Vector3f(target).sub(shoulder);
		if (down.lengthSquared() > TINY) {
			down.normalize();
			Vector3f side = new Vector3f(toward).sub(new Vector3f(down).mul(down.dot(toward)));
			if (side.lengthSquared() < TINY) {
				// what it holds runs straight on along the arm: the fist keeps the turn it had
				side = pose.transform(new Vector3f(local));
				side.sub(new Vector3f(down).mul(down.dot(side)));
			}
			if (side.lengthSquared() > TINY) {
				side.normalize();
				Matrix3f turn = basis(down, side).mul(basis(DOWN, local).transpose());
				pose.slerp(new Quaternionf().setFromNormalized(turn), Mth.clamp(weight, 0.0F, 1.0F));
			}
		}
		Vector3f angles = pose.getEulerAnglesZYX(new Vector3f());
		part.xRot = angles.x;
		part.yRot = angles.y;
		part.zRot = angles.z;
		return pose.transform(new Vector3f(sign * FIST_X, OwnHand.REACH, 0.0F)).add(shoulder);
	}

	/**
	 * Brings an arm's shoulder forward along the view, as far as it takes for its fist reaching toward {@code toward}
	 * (in the view's space) to be {@code depth} in front of the eye. Returns where it reaches toward from there.
	 */
	private static Vector3f forward(final ModelPart part, final Vector3f toward, final Matrix4fc toView, final Matrix4fc toModel, final float raised,
		final float depth) {
		Vector3f shoulder = toView.transformPosition(shoulder(part));
		Vector3f way = new Vector3f(toward).sub(shoulder);
		if (way.lengthSquared() < TINY) {
			return toward;
		}
		way.normalize();
		float near = shoulder.z + OwnHand.REACH * way.z + depth;
		if (near <= 0.0F) {
			return toward;
		}
		float shrug = Math.min(near, SHRUG) * Mth.clamp(raised, 0.0F, 1.0F);
		Vector3f step = toModel.transformDirection(new Vector3f(0.0F, 0.0F, -shrug)).mul(16.0F);
		part.x += step.x;
		part.y += step.y;
		part.z += step.z;
		return toView.transformPosition(shoulder(part)).add(way);
	}

	/**
	 * Leans an arm's shoulder toward {@code toward} (in the view's space), as far as it takes for its fist to get there,
	 * up to {@link #SHRUG}. Returns {@code toward}.
	 */
	private static Vector3f lean(final ModelPart part, final Vector3f toward, final Matrix4fc toView, final Matrix4fc toModel, final float raised) {
		Vector3f way = new Vector3f(toward).sub(toView.transformPosition(shoulder(part)));
		float far = way.length() - OwnHand.REACH;
		if (far > 0.0F) {
			Vector3f step = toModel.transformDirection(way.normalize().mul(Math.min(far, SHRUG) * Mth.clamp(raised, 0.0F, 1.0F))).mul(16.0F);
			part.x += step.x;
			part.y += step.y;
			part.z += step.z;
		}
		return toward;
	}

	/** Where the body's arm reaches toward for work the hand pass found at {@code work}, both in the view's space. */
	private static Vector3f reach(final Vector3fc work, final Vector3fc shoulder, final int sign) {
		Vector3f toward = new Vector3f(work).sub(sign * PIVOT.x(), PIVOT.y(), PIVOT.z());
		if (toward.lengthSquared() < TINY) {
			return new Vector3f(shoulder).add(0.0F, 0.0F, -1.0F);
		}
		toward.normalize();
		if (toward.z > -FORWARD) {
			// kept far enough forward: the rest of the reach keeps its way up, down or across
			float across = (float) Math.sqrt(1.0F - FORWARD * FORWARD);
			float side = (float) Math.sqrt(toward.x * toward.x + toward.y * toward.y);
			float k = side < TINY ? 0.0F : across / side;
			toward.set(toward.x * k, toward.y * k, -FORWARD);
		}
		return toward.add(shoulder);
	}

	/** The rotation that takes the standard axes to {@code side}, {@code down} and the third square to both. */
	private static Matrix3f basis(final Vector3fc down, final Vector3fc side) {
		return new Matrix3f(side, down, new Vector3f(side).cross(down));
	}

	private static Vector3f shoulder(final ModelPart part) {
		return new Vector3f(part.x, part.y, part.z).div(16.0F);
	}

	private static HumanoidArm arm(final AvatarRenderState state, final InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND ? state.mainArm : state.mainArm.getOpposite();
	}

	/**
	 * A tool put to the arm held out: its clip laid it out against an arm where the hand pass would have held it; here
	 * it is turned and moved with that arm to where the body really holds it out, brought to the wrist, and slid along
	 * the arm as far as needed for the hand that holds the tool to reach. {@code sign} is 1 when the right hand holds it.
	 */
	private static Hold putTo(final Hold hold, final Present design, final Vector3f[] heldOut, final Vector3f shoulder, final int sign) {
		Vector3f tip = hold.tip();
		Vector3f toward = new Vector3f(sign, 0.0F, 0.0F);
		Vector3f designAlong = new Vector3f(design.elbow()).sub(design.fist()).normalize();
		Vector3f bodyAlong = new Vector3f(heldOut[1]).sub(heldOut[0]).normalize();
		// both arms seen the same way: along the arm, and toward the hand that works on it
		Matrix3f turn = frame(bodyAlong, toward).mul(frame(designAlong, toward).transpose());
		Vector3f rel = new Vector3f(tip).sub(design.fist());
		float along = rel.dot(designAlong);
		Vector3f round = new Vector3f(rel).sub(new Vector3f(designAlong).mul(along));
		float radius = round.length();
		// on the body's arm, as far off its middle as the clip had it off the arm it was laid out on
		Vector3f lift = turn.transform(round).mul(radius < TINY ? 0.0F : (radius - CLIP_HALF + OwnHand.THICK / 2.0F) / radius);
		Vector3f offset = turn.transform(new Vector3f(hold.fist()).sub(tip)).mul(ITEM_SIZE);
		float wanted = Math.min(along, WRIST);
		float best = wanted;
		float bestError = Float.MAX_VALUE;
		for (int i = 0; i <= 40; i++) {
			float at = 0.02F + SLIDE * i / 40.0F;
			Vector3f grip = new Vector3f(heldOut[0]).add(new Vector3f(bodyAlong).mul(at)).add(lift).add(offset);
			float miss = Math.max(0.0F, grip.distance(shoulder) - OwnHand.REACH - SHRUG);
			float error = miss * miss + 0.05F * (at - wanted) * (at - wanted);
			if (error < bestError) {
				bestError = error;
				best = at;
			}
		}
		Vector3f newTip = new Vector3f(heldOut[0]).add(new Vector3f(bodyAlong).mul(best)).add(lift);
		Matrix4f item = hold.item() == null ? null
			: new Matrix4f().translation(newTip).mul(new Matrix4f().set(turn)).scale(ITEM_SIZE).translate(-tip.x, -tip.y, -tip.z).mul(hold.item());
		return new Hold(new Vector3f(newTip).add(offset), turn.transform(new Vector3f(hold.along())), item, newTip);
	}

	/** An arm's frame: across it toward {@code toward}, along it, and the third square to both. */
	private static Matrix3f frame(final Vector3f along, final Vector3fc toward) {
		Vector3f across = new Vector3f(toward).sub(new Vector3f(along).mul(along.dot(toward)));
		if (across.lengthSquared() < TINY) {
			across.set(0.0F, 1.0F, 0.0F).sub(new Vector3f(along).mul(along.y));
		}
		return basis(along, across.normalize());
	}

	/** Between two placements of a thing, turned, moved and scaled {@code weight} of the way from the first to the second. */
	private static Matrix4f blend(final Matrix4fc from, final Matrix4fc to, final float weight) {
		if (weight >= 1.0F) {
			return new Matrix4f(to);
		}
		Quaternionf turn = from.getNormalizedRotation(new Quaternionf()).slerp(to.getNormalizedRotation(new Quaternionf()), weight);
		Vector3f place = from.getTranslation(new Vector3f()).lerp(to.getTranslation(new Vector3f()), weight);
		float scale = Mth.lerp(weight, from.getScale(new Vector3f()).x, to.getScale(new Vector3f()).x);
		return new Matrix4f().translationRotateScale(place, turn, scale);
	}
}
