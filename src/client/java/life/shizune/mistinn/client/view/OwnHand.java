package life.shizune.mistinn.client.view;

import life.shizune.mistinn.weapon.WeaponStyle;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * 握著東西的手: when the hunter's hands come up in front of the eye to work, each is drawn with its arm, gripping what
 * it holds, so the hand swings, digs and draws along with the item instead of the item moving by itself.
 *
 * <p>The arm is found from the item, not the other way round. The fist closes on the item's grip, a point on the
 * item's own model carried through every motion the item makes (its place in the hand, a swing, a heavy blow drawn
 * back, a bow pulled); the forearm runs to it from the elbow, which stays at the hunter's side below the eye; and
 * the fist is turned so that what it holds leaves it on the thumb's side. All of this is worked out in the hands'
 * space: the eye at the origin looking down -z, x to the right, y up, in blocks.
 */
public final class OwnHand {
	/**
	 * While the hands work, a hand and what it holds sit a little higher, nearer the middle and further off than the
	 * game draws a bare item, so the fist is inside the view and not below its corner.
	 */
	public static final float VIEW_X = -0.1F;
	public static final float VIEW_Y = 0.24F;
	public static final float VIEW_Z = -0.14F;
	/** Where the elbow stays, for the right arm: out to the side, well below the eye and a little ahead of it. */
	public static final float ELBOW_X = 0.5F;
	public static final float ELBOW_Y = -0.95F;
	public static final float ELBOW_Z = -0.28F;
	/** The arm against the body's own: seen this close a full-size arm would fill the view. */
	public static final float SIZE = 0.8F;
	/** The game draws a held thing with a vertical field of view of this many degrees, whatever the world's is. */
	public static final float FIELD_OF_VIEW = 70.0F;

	/** The arm's model, in blocks: its shoulder from the body's middle, the slant it hangs at, and its fist from the shoulder. */
	private static final float SHOULDER_X = 5.0F / 16.0F;
	private static final float SHOULDER_Y = 2.0F / 16.0F;
	private static final float SLANT = 0.1F;
	private static final float FIST_X = 1.0F / 16.0F;
	private static final float FIST_Y = 8.5F / 16.0F;
	/**
	 * The arm's model is only as long as an arm, and a fist held high or far would show where it ends. A second arm is
	 * drawn as the upper arm, turned end for end and joined to the first shoulder to shoulder, so the sleeve runs on
	 * unbroken toward the elbow and out of the view. The model is this long from the fist's middle back to its shoulder
	 * end, and this long on past the fist's middle, in blocks.
	 */
	private static final float BACK = 10.5F / 16.0F;
	private static final float PAST = 1.5F / 16.0F;
	/** How thick the arm is drawn, in blocks. */
	public static final float THICK = 4.0F / 16.0F * SIZE;
	/** Below this a direction is too short to turn the fist by. */
	private static final float TINY = 1.0E-4F;
	/**
	 * A fist nearer the eye than this (blocks), or further outside the view than this (1 is the view's edge), gets no
	 * arm: the arm would run close past the eye and fill the view, to reach a hand that is not seen anyway (a trident
	 * drawn back over the shoulder, a shield whose grip is below the view).
	 */
	private static final float NEAR = 0.4F;
	private static final float OUTSIDE = 1.2F;

	/** The arms drawn since the last tick; null for a hand that was not drawn. */
	private static @Nullable Arm mainDrawn;
	private static @Nullable Arm offDrawn;
	private static @Nullable Arm mainSeen;
	private static @Nullable Arm offSeen;
	private static @Nullable Vector3f pointDrawn;
	private static @Nullable Vector3f pointSeen;

	/**
	 * An arm holding an item: how to draw its forearm and its upper arm (each the transform from the arm's model, as the
	 * game draws a bare hand, to the hands' space), and where its fist and its far end are in the hands' space.
	 */
	public record Arm(Matrix4f fore, Matrix4f upper, Vector3f fist, Vector3f end) {
	}

	/** Where a drawn arm was on the screen: x and y from -1 (left, bottom) to 1 (right, top), for its fist and its far end. */
	public record Seen(float fistX, float fistY, float endX, float endY) {
		/** The fist is inside the view. */
		public boolean fistInView() {
			return Math.abs(this.fistX) < 1.0F && Math.abs(this.fistY) < 1.0F;
		}

		/** The arm runs out of the view before it ends: no cut-off arm is seen. */
		public boolean whole() {
			return Math.abs(this.endX) >= 1.0F || Math.abs(this.endY) >= 1.0F;
		}
	}

	/**
	 * Where on an item's model the hand closes, and which way the item runs on from there (out of the fist on the
	 * thumb's side), both in the model's own space: a flat item's picture is its x (left to right) and y (bottom to
	 * top) from 0 to 1, at z 0.5.
	 */
	public record Grip(float x, float y, float z, float alongX, float alongY, float alongZ) {
		/** Anything not held by a handle: from underneath, the item standing up out of the fist. */
		public static final Grip UNDER = new Grip(0.5F, 0.16F, 0.5F, 0.0F, 1.0F, 0.0F);
		/** A tool: the end of its handle at the lower left, the head up and to the right. */
		public static final Grip HANDLE = diagonal(3.0F);
		/** A bow: the middle of its stave, which curves from the lower left to the upper right. */
		public static final Grip STAVE = new Grip(5.5F / 16.0F, 10.5F / 16.0F, 0.5F, 1.0F, 1.0F, 0.0F);
		/** A crossbow: its stock, behind the bow. */
		public static final Grip STOCK = diagonal(5.0F);

		/**
		 * A grip on a model that is not a picture (a shield, a trident, a head): its middle, the model standing up out
		 * of the fist. Null for a model with no corners to find a middle from.
		 */
		public static @Nullable Grip around(final Vector3fc[] corners) {
			if (corners.length == 0) {
				return null;
			}
			Vector3f low = new Vector3f(corners[0]);
			Vector3f high = new Vector3f(corners[0]);
			for (Vector3fc corner : corners) {
				low.min(corner);
				high.max(corner);
			}
			return new Grip((low.x + high.x) / 2.0F, (low.y + high.y) / 2.0F, (low.z + high.z) / 2.0F, 0.0F, 1.0F, 0.0F);
		}

		/** A grip on a picture drawn from the lower left to the upper right, this many pixels up its diagonal. */
		private static Grip diagonal(final float pixels) {
			return new Grip(pixels / 16.0F, pixels / 16.0F, 0.5F, 1.0F, 1.0F, 0.0F);
		}

		public static Grip of(final ItemStack stack) {
			WeaponStyle style = WeaponStyle.of(stack);
			if (style != null) {
				return switch (style) {
					case SLASH -> diagonal(2.5F);
					case CLEAVE, SMASH -> diagonal(3.0F);
					case FLURRY, REAP -> diagonal(3.5F);
					case THRUST -> diagonal(4.0F);
				};
			}
			if (stack.getItem() instanceof BowItem) {
				return STAVE;
			}
			if (stack.getItem() instanceof CrossbowItem) {
				return STOCK;
			}
			return stack.has(DataComponents.TOOL) || stack.has(DataComponents.WEAPON) ? HANDLE : UNDER;
		}
	}

	private OwnHand() {
	}

	/**
	 * The arm that holds an item, or null if none is drawn for it there: the fist would be out of sight or too near the
	 * eye, or the grip lies on the elbow.
	 *
	 * @param item from the item's model to the hands' space
	 * @param side 1 for the right arm, -1 for the left
	 * @param aspect how many times as wide as it is high the view is
	 */
	public static @Nullable Arm arm(final Matrix4fc item, final Grip grip, final int side, final float aspect) {
		Vector3f fist = item.transformPosition(grip.x(), grip.y(), grip.z(), new Vector3f());
		float reach = -fist.z * (float) Math.tan(FIELD_OF_VIEW * Mth.DEG_TO_RAD / 2.0F);
		if (-fist.z < NEAR || Math.abs(fist.y) > OUTSIDE * reach || Math.abs(fist.x) > OUTSIDE * reach * aspect) {
			return null;
		}
		Vector3f along = item.transformDirection(grip.alongX(), grip.alongY(), grip.alongZ(), new Vector3f());
		// the forearm: from the elbow to the fist
		Vector3f forearm = new Vector3f(fist).sub(side * ELBOW_X, ELBOW_Y, ELBOW_Z);
		if (forearm.lengthSquared() < TINY || along.lengthSquared() < TINY) {
			return null;
		}
		forearm.normalize();
		// the item leaves the fist square to the forearm; what of its run lies along the forearm does not turn the fist
		along.normalize();
		along.sub(new Vector3f(forearm).mul(along.dot(forearm)));
		if (along.lengthSquared() < TINY) {
			return null;
		}
		along.normalize();
		// the arm's model hangs down its own y, its front (the thumb's side of a fist) toward its -z
		Vector3f back = along.negate(new Vector3f());
		return whole(forearm.cross(back, new Vector3f()), forearm, back, fist, side);
	}

	/**
	 * An arm held out in front of the eye with nothing in its hand, for the other hand to work on (a needle put into
	 * it, a strap wound round it).
	 *
	 * @param fist where the middle of its fist is, in the hands' space
	 * @param elbow where its elbow is
	 * @param turn which way the inside of the arm faces, in radians: 0 up, a quarter turn toward the eye
	 * @param side 1 for the right arm, -1 for the left
	 */
	public static @Nullable Arm held(final Vector3fc fist, final Vector3fc elbow, final float turn, final int side) {
		Vector3f forearm = new Vector3f(fist).sub(elbow);
		if (forearm.lengthSquared() < TINY) {
			return null;
		}
		forearm.normalize();
		Vector3f inside = new Vector3f(0.0F, Mth.cos(turn), Mth.sin(turn));
		inside.sub(new Vector3f(forearm).mul(inside.dot(forearm)));
		if (inside.lengthSquared() < TINY) {
			return null;
		}
		// the inside of the right arm is its model's +x, of the left its -x
		Vector3f across = inside.normalize().mul(side);
		return whole(across, forearm, across.cross(forearm, new Vector3f()), new Vector3f(fist), side);
	}

	/** The forearm and the upper arm behind it, for an arm whose fist and three axes are known. */
	private static Arm whole(final Vector3f across, final Vector3f forearm, final Vector3f back, final Vector3f fist, final int side) {
		Matrix4f fore = model(across, forearm, back, fist, side);
		// the upper arm: the same model turned about its width, its shoulder end on the forearm's
		Vector3f turned = new Vector3f(fist).sub(new Vector3f(forearm).mul(2.0F * BACK * SIZE));
		Matrix4f upper = model(across, forearm.negate(new Vector3f()), back.negate(new Vector3f()), turned, side);
		Vector3f end = new Vector3f(turned).sub(new Vector3f(forearm).mul(PAST * SIZE));
		return new Arm(fore, upper, fist, end);
	}

	/**
	 * From the arm's model to the hands' space, for an arm lying along {@code along} with the middle of its fist at
	 * {@code fist}.
	 */
	private static Matrix4f model(final Vector3f across, final Vector3f along, final Vector3f back, final Vector3f fist, final int side) {
		Matrix4f arm = new Matrix4f(
			across.x, across.y, across.z, 0.0F,
			along.x, along.y, along.z, 0.0F,
			back.x, back.y, back.z, 0.0F,
			fist.x, fist.y, fist.z, 1.0F
		);
		arm.scale(SIZE);
		arm.translate(side * FIST_X, -FIST_Y, 0.0F);
		// the game hangs the arm from its shoulder at a slant before drawing it: undo both
		arm.rotateZ(-side * SLANT);
		arm.translate(side * SHOULDER_X, -SHOULDER_Y, 0.0F);
		return arm;
	}

	/**
	 * Notes that a hand's arm was drawn in this frame, {@code lowered} below its working place (the hands sink out of
	 * view when their work is done, and the whole hands' space sinks with them).
	 */
	public static void drawn(final InteractionHand hand, final Arm arm, final float lowered) {
		Arm seen = new Arm(arm.fore(), arm.upper(), new Vector3f(arm.fist()).sub(0.0F, lowered, 0.0F), new Vector3f(arm.end()).sub(0.0F, lowered, 0.0F));
		if (hand == InteractionHand.MAIN_HAND) {
			mainDrawn = seen;
		} else {
			offDrawn = seen;
		}
	}

	/**
	 * Notes where the working point of a tool was in this frame (a needle's tip, the middle of a strap), in the hands'
	 * space, {@code lowered} as in {@link #drawn}.
	 */
	public static void worked(final Vector3fc point, final float lowered) {
		pointDrawn = new Vector3f(point).sub(0.0F, lowered, 0.0F);
	}

	public static void tick() {
		mainSeen = mainDrawn;
		offSeen = offDrawn;
		pointSeen = pointDrawn;
		mainDrawn = null;
		offDrawn = null;
		pointDrawn = null;
	}

	/**
	 * How far a tool's working point last was from the middle line of a hand's forearm, in blocks (the forearm is
	 * {@link #THICK} through); NaN if either was not drawn since the tick before last.
	 */
	public static float fromForearm(final InteractionHand hand) {
		Arm arm = hand == InteractionHand.MAIN_HAND ? mainSeen : offSeen;
		Vector3f point = pointSeen;
		if (arm == null || point == null) {
			return Float.NaN;
		}
		Vector3f toward = new Vector3f(arm.end()).sub(arm.fist()).normalize();
		float along = Mth.clamp(new Vector3f(point).sub(arm.fist()).dot(toward), 0.0F, BACK * SIZE);
		return new Vector3f(arm.fist()).add(toward.mul(along)).distance(point);
	}

	/**
	 * Where a hand's arm was last drawn on the screen, for a view {@code aspect} times as wide as it is high; null if
	 * it was not drawn since the tick before last.
	 */
	public static @Nullable Seen seen(final InteractionHand hand, final float aspect) {
		Arm arm = hand == InteractionHand.MAIN_HAND ? mainSeen : offSeen;
		if (arm == null) {
			return null;
		}
		float tan = (float) Math.tan(FIELD_OF_VIEW * Mth.DEG_TO_RAD / 2.0F);
		float fistReach = -arm.fist().z * tan;
		// a far end behind the eye is out of the view, whatever the division would say
		boolean behind = arm.end().z >= 0.0F;
		float endReach = -arm.end().z * tan;
		return new Seen(
			arm.fist().x / (fistReach * aspect),
			arm.fist().y / fistReach,
			behind ? Float.POSITIVE_INFINITY : arm.end().x / (endReach * aspect),
			behind ? Float.POSITIVE_INFINITY : arm.end().y / endReach
		);
	}
}
