package life.shizune.mistinn.client.view;

import life.shizune.mistinn.weapon.WeaponStyle;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * 握著東西的手: where on a held thing the hand closes ({@link Grip}), and where the hunter's own hands were last seen at
 * work in front of the eye, for whoever needs to know (the tests). The arms themselves are the body's own
 * ({@link OwnArms}); all of this is in the view's space: the eye at the origin looking down -z, x to the right, y up,
 * in blocks.
 */
public final class OwnHand {
	/**
	 * While the hands work, a hand and what it holds sit a little higher, nearer the middle and further off than the
	 * game draws a bare item, so the work is in front of the eye and not below its corner.
	 */
	public static final float VIEW_X = -0.1F;
	public static final float VIEW_Y = 0.24F;
	public static final float VIEW_Z = -0.14F;
	/** The vertical field of view, in degrees, the hands are measured against on the screen. */
	public static final float FIELD_OF_VIEW = 70.0F;
	/** How thick an arm is, in blocks. */
	public static final float THICK = 4.0F / 16.0F;
	/** How far an arm reaches from its shoulder to the middle of its fist, in blocks. */
	public static final float REACH = 8.5F / 16.0F;

	/** The arms seen since the last tick; null for a hand that was not at work. */
	private static @Nullable Vector3f[] mainDrawn;
	private static @Nullable Vector3f[] offDrawn;
	private static @Nullable Vector3f[] mainSeen;
	private static @Nullable Vector3f[] offSeen;
	private static @Nullable Vector3f pointDrawn;
	private static @Nullable Vector3f pointSeen;

	/** Where an arm was on the screen: x and y from -1 (left, bottom) to 1 (right, top), for its fist and its shoulder. */
	public record Seen(float fistX, float fistY, float endX, float endY) {
		/** The fist is inside the view. */
		public boolean fistInView() {
			return Math.abs(this.fistX) < 1.0F && Math.abs(this.fistY) < 1.0F;
		}

		/** The arm runs out of the view toward its shoulder: no cut-off arm is seen. */
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

	/** Notes that a hand was at work in this frame, its fist and its shoulder where they are in the view's space. */
	public static void drawn(final InteractionHand hand, final Vector3fc fist, final Vector3fc shoulder) {
		Vector3f[] seen = {new Vector3f(fist), new Vector3f(shoulder)};
		if (hand == InteractionHand.MAIN_HAND) {
			mainDrawn = seen;
		} else {
			offDrawn = seen;
		}
	}

	/** Notes where the working point of a tool was in this frame (a needle's tip, the middle of a strap). */
	public static void worked(final Vector3fc point) {
		pointDrawn = new Vector3f(point);
	}

	public static void tick() {
		mainSeen = mainDrawn;
		offSeen = offDrawn;
		pointSeen = pointDrawn;
		mainDrawn = null;
		offDrawn = null;
		pointDrawn = null;
	}

	/** Where a hand's fist and shoulder last were at work, in the view's space; null if it was not at work. For films. */
	public static @Nullable Vector3fc[] where(final InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND ? mainSeen : offSeen;
	}

	/**
	 * How far a tool's working point last was from the middle line of a hand's forearm, in blocks (the arm is
	 * {@link #THICK} through); NaN if either was not seen since the tick before last.
	 */
	public static float fromForearm(final InteractionHand hand) {
		Vector3f[] arm = hand == InteractionHand.MAIN_HAND ? mainSeen : offSeen;
		Vector3f point = pointSeen;
		if (arm == null || point == null) {
			return Float.NaN;
		}
		Vector3f toward = new Vector3f(arm[1]).sub(arm[0]).normalize();
		float along = Mth.clamp(new Vector3f(point).sub(arm[0]).dot(toward), 0.0F, REACH);
		return new Vector3f(arm[0]).add(toward.mul(along)).distance(point);
	}

	/**
	 * Where a hand's arm was last seen at work on the screen, for a view {@code aspect} times as wide as it is high;
	 * null if it was not at work since the tick before last.
	 */
	public static @Nullable Seen seen(final InteractionHand hand, final float aspect) {
		Vector3f[] arm = hand == InteractionHand.MAIN_HAND ? mainSeen : offSeen;
		if (arm == null) {
			return null;
		}
		float tan = (float) Math.tan(FIELD_OF_VIEW * Mth.DEG_TO_RAD / 2.0F);
		float fistReach = -arm[0].z * tan;
		// a shoulder behind the eye is out of the view, whatever the division would say
		boolean behind = arm[1].z >= 0.0F;
		float endReach = -arm[1].z * tan;
		return new Seen(
			arm[0].x / (fistReach * aspect),
			arm[0].y / fistReach,
			behind ? Float.POSITIVE_INFINITY : arm[1].x / (endReach * aspect),
			behind ? Float.POSITIVE_INFINITY : arm[1].y / endReach
		);
	}
}
