package life.shizune.mistinn.client.medical;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import life.shizune.mistinn.client.anim.Clip;
import life.shizune.mistinn.client.anim.Poser;
import life.shizune.mistinn.client.view.OwnBody;
import life.shizune.mistinn.client.view.OwnHand;
import life.shizune.mistinn.medical.Treatment;
import life.shizune.mistinn.medical.TreatmentService;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * 處置動作: the client half of timed uses. Keeps every visible hunter's use as the server told
 * it, and plays its clip ({@code anim/use/<treatment>.json}): {@code limbs} poses the body
 * seen from outside, {@code item.x} tilts the held item about the grip, and
 * {@code first_person} ({@code tx ty tz} in blocks, {@code rx ry rz} in degrees) moves the item
 * in one's own view. A clip with {@code loop} (seconds) repeats its motion for as long as the
 * use lasts; one without is stretched over the whole use. Clips blend in over
 * {@link #BLEND} seconds and back out when the use ends, finished or not.
 *
 * <p>A clip with a {@code tool} group is worked with the hands up in front of the hunter's own eye ({@link InHands}):
 * the tool's working point (a needle's tip, a bottle's mouth; {@code tip_x tip_y} in the item's picture, in pixels
 * from its lower left) is put where {@code tool.x y z} say in the hands' space, the rest of the tool running off toward
 * {@code tool.yaw pitch} ({@code axis_x axis_y} is that direction in the picture, and the fist closes {@code grip}
 * pixels along it), turned {@code tool.roll} about itself and drawn {@code tool.size} as large as when plainly held;
 * {@code tool.w} is how far from the plain hold (0) to there (1). {@code other} holds the other arm out to be worked
 * on: its fist at {@code x y z}, its elbow at {@code ex ey ez}, its inside turned {@code turn}, {@code up} from below
 * the view (0) into it (1). So the hand that works is never the limb worked on.
 */
public final class TreatmentClient {
	/** Seconds a use takes to blend in and out. */
	public static final float BLEND = 0.25F;
	/** Seconds the HUD keeps showing a use after it ended. */
	public static final float LINGER = 0.6F;

	/**
	 * A use as the renderer draws it: {@code weight} 1 when the clip owns the pose;
	 * {@code seconds} into the use, for the first-person work beat that runs on its own clock.
	 */
	public record Pose(Treatment treatment, float p, float weight, float seconds) {
	}

	/**
	 * The local hunter's use, when it is one worked with the hands up in front of their own eye: {@code hand} holds the
	 * tool, the other arm is the one worked on.
	 */
	public record InHands(Pose pose, Item item, InteractionHand hand, Clip clip) {
		/** Whether the other arm is held out to be worked on (a bottle of pills needs only the hand that holds it). */
		public boolean presents() {
			return this.clip.has("other.up");
		}
	}

	/** A use as the HUD shows it. */
	public record Progress(Item item, Treatment treatment, float seconds, float duration, float sinceEnd, int result) {
		public boolean running() {
			return this.result == TreatmentService.RESULT_RUNNING;
		}
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "mistinn:treatment");

	private static final class Use {
		final Treatment treatment;
		final Item item;
		final long start;
		final float duration;
		final InteractionHand hand;
		long end = -1L;
		int result = TreatmentService.RESULT_RUNNING;

		Use(final Treatment treatment, final Item item, final long start, final float duration, final InteractionHand hand) {
			this.treatment = treatment;
			this.item = item;
			this.start = start;
			this.duration = duration;
			this.hand = hand;
		}

		float seconds(final long now) {
			long until = this.end >= 0L ? this.end : now;
			return Math.min(this.duration, (until - this.start) / 1.0E9F);
		}

		float sinceEnd(final long now) {
			return this.end < 0L ? 0.0F : (now - this.end) / 1.0E9F;
		}
	}

	/** For the motion reel test only: when 0 or more, every use is drawn frozen at this share of its time. */
	public static float preview = -1.0F;
	/** How large a medical or repair item is drawn in one's own view during its use, against a plain hold. */
	public static final float FIRST_PERSON_SCALE = 0.65F;

	private static final Map<Integer, Use> USES = new HashMap<>();

	private TreatmentClient() {
	}

	public static void received(final TreatmentService.TreatmentPayload payload) {
		long now = System.nanoTime();
		if (payload.treatment() >= 0) {
			Item item = Item.byId(payload.item());
			// which hand works is settled when the use begins: the tool may be used up before the hands are down again
			LocalPlayer local = Minecraft.getInstance().player;
			boolean off = local != null && local.getId() == payload.entity() && !local.getMainHandItem().is(item) && local.getOffhandItem().is(item);
			USES.put(payload.entity(), new Use(Treatment.byId(payload.treatment()), item, now, payload.ticks() / 20.0F,
				off ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND));
			return;
		}
		Use use = USES.get(payload.entity());
		if (use != null && use.end < 0L) {
			use.end = now;
			use.result = payload.result();
		}
	}

	/** Drops uses that have faded out, and those of hunters gone from view. */
	public static void tick(final Minecraft mc) {
		long now = System.nanoTime();
		USES.entrySet().removeIf(entry -> entry.getValue().sinceEnd(now) > LINGER
			|| mc.level == null || mc.level.getEntity(entry.getKey()) == null);
	}

	public static @Nullable Pose sample(final int entityId) {
		Use use = USES.get(entityId);
		if (use == null) {
			return null;
		}
		long now = System.nanoTime();
		float seconds = preview >= 0.0F ? preview * use.duration : use.seconds(now);
		float out = preview >= 0.0F ? 1.0F : 1.0F - Mth.clamp(use.sinceEnd(now) / BLEND, 0.0F, 1.0F);
		if (out <= 0.0F) {
			return null;
		}
		float in = smooth(seconds / BLEND);
		Clip clip = clip(use.treatment);
		float loop = clip.number("loop", 0.0F);
		float p;
		if (loop > 0.0F) {
			p = (seconds % loop) / loop;
		} else {
			p = use.duration <= 0.0F ? 1.0F : Mth.clamp(seconds / use.duration, 0.0F, 1.0F);
			in = 1.0F; // a stretched clip starts and ends at rest by itself
		}
		return new Pose(use.treatment, p, in * smooth(out), seconds);
	}

	/** The local hunter's use, for the HUD; null when none is showing. */
	public static @Nullable Progress local() {
		LocalPlayer player = Minecraft.getInstance().player;
		Use use = player == null ? null : USES.get(player.getId());
		if (use == null) {
			return null;
		}
		long now = System.nanoTime();
		float seconds = preview >= 0.0F ? preview * use.duration : use.seconds(now);
		return new Progress(use.item, use.treatment, seconds, use.duration, use.sinceEnd(now), use.result);
	}

	/** The local hunter is mid-use: no sprinting. */
	public static boolean busy() {
		Progress progress = local();
		return progress != null && progress.running();
	}

	public static Clip clip(final Treatment treatment) {
		return Clip.get("use/" + treatment.id);
	}

	// ── 第三人稱 Third person ─────────────────────────────────────

	/** Poses the body through the use; {@code mirror} -1 when the left hand holds the item. */
	public static void thirdPerson(final Pose pose, final HumanoidModel<?> model, final int mirror) {
		Poser.limbs(clip(pose.treatment()), pose.p(), model, mirror, pose.weight());
	}

	/**
	 * In the hunter's own view the arms come further forward than they look from outside ({@code own_lift}, degrees), so
	 * the hands and what they work on are in sight below the eye.
	 */
	public static void ownLift(final Pose pose, final HumanoidModel<?> model) {
		float lift = clip(pose.treatment()).number("own_lift", 0.0F) * pose.weight() * Mth.DEG_TO_RAD;
		model.rightArm.xRot -= lift;
		model.leftArm.xRot -= lift;
	}

	/**
	 * Where the hunter looks while they work, added to their own view only: {pitch, yaw} in degrees (down, and toward the
	 * hand that holds the item), from the clip's {@code camera} tracks.
	 */
	public static float[] camera(final Pose pose, final int side) {
		Clip clip = clip(pose.treatment());
		if (!clip.has("camera.pitch")) {
			return new float[] {0.0F, 0.0F};
		}
		return new float[] {clip.at("camera.pitch", pose.p()) * pose.weight(), side * clip.at("camera.yaw", pose.p()) * pose.weight()};
	}

	/** Tilts the held item about the grip (degrees about the hand's x axis). */
	public static void thirdPersonItem(final Pose pose, final PoseStack stack) {
		float tilt = clip(pose.treatment()).at("item.x", pose.p()) * pose.weight();
		if (tilt != 0.0F) {
			stack.rotateDegrees(Axis.XP, tilt);
		}
	}

	/**
	 * In the hunter's own view an item worked close under the eye is drawn smaller ({@code own_scale}), as if held a
	 * little further off, so it does not fill the view.
	 */
	public static void ownItem(final Pose pose, final PoseStack stack) {
		Clip work = work(pose);
		if (work != null) {
			stack.rotateDegrees(Axis.XP, work.at("own.item_x", beat(pose)) * pose.weight());
		}
		float scale = Mth.lerp(pose.weight(), 1.0F, clip(pose.treatment()).number("own_scale", 1.0F));
		if (scale != 1.0F) {
			stack.scale(scale, scale, scale);
		}
	}

	/**
	 * The work itself, seen close: over the body's slower cycle the hands make the short beat of the job (a stitch drawn,
	 * a binding pulled tight, a patch pressed down), from the {@code own} tracks of the use's {@code _1p} clip, in
	 * degrees added to each arm; {@code mirror} -1 when the left hand holds the item.
	 */
	public static void ownBeat(final Pose pose, final HumanoidModel<?> model, final int mirror) {
		Clip work = work(pose);
		if (work == null) {
			return;
		}
		float p = beat(pose);
		float w = pose.weight();
		var right = mirror < 0 ? model.leftArm : model.rightArm;
		var left = mirror < 0 ? model.rightArm : model.leftArm;
		right.xRot += work.rad("own.right_arm_x", p) * w;
		right.yRot += mirror * work.rad("own.right_arm_y", p) * w;
		right.zRot += mirror * work.rad("own.right_arm_z", p) * w;
		left.xRot += work.rad("own.left_arm_x", p) * w;
		left.yRot += mirror * work.rad("own.left_arm_y", p) * w;
		left.zRot += mirror * work.rad("own.left_arm_z", p) * w;
	}

	/** The clip of a use's work beat; null for a use that has none. */
	public static @Nullable Clip work(final Pose pose) {
		return clip(pose.treatment()).number("first_person_loop", 0.0F) > 0.0F ? Clip.find("use/" + pose.treatment().id + "_1p") : null;
	}

	/** Where in its work beat a use is, 0 to 1. */
	public static float beat(final Pose pose) {
		float beat = clip(pose.treatment()).number("first_person_loop", 0.0F);
		return beat <= 0.0F ? 0.0F : (pose.seconds() % beat) / beat;
	}

	// ── 第一人稱 First person ─────────────────────────────────────

	/** Moves the held item in one's own view; {@code side} 1 for the right hand. */
	public static void firstPerson(final Pose pose, final PoseStack stack, final int side) {
		Clip clip = clip(pose.treatment());
		float w = pose.weight();
		float p = pose.p();
		// a use with a work beat in hand (stitching, tapping a patch down) runs it from its own
		// short clip, looped faster than the body's slower cycle seen from outside
		float beat = clip.number("first_person_loop", 0.0F);
		if (beat > 0.0F) {
			clip = Clip.get("use/" + pose.treatment().id + "_1p");
			p = (pose.seconds() % beat) / beat;
		}
		stack.translate(side * clip.at("first_person.tx", p) * w, clip.at("first_person.ty", p) * w, clip.at("first_person.tz", p) * w);
		stack.rotateDegrees(Axis.YP, side * clip.at("first_person.ry", p) * w);
		stack.rotateDegrees(Axis.ZP, side * clip.at("first_person.rz", p) * w);
		stack.rotateDegrees(Axis.XP, clip.at("first_person.rx", p) * w);
		// held at arm's length rather than up against the eye: the item shrinks while it is in use
		float scale = Mth.lerp(w, 1.0F, FIRST_PERSON_SCALE);
		stack.scale(scale, scale, scale);
	}

	// ── 手舉到眼前的處置 Worked in the hands ──────────────────────

	/** The local hunter's use, if it is one worked with the hands up in front of the eye; null otherwise. */
	public static @Nullable InHands inHands(final LocalPlayer player) {
		Use use = USES.get(player.getId());
		if (use == null || !clip(use.treatment).has("tool.w")) {
			return null;
		}
		Pose pose = sample(player.getId());
		return pose == null ? null : new InHands(pose, use.item, use.hand, clip(use.treatment));
	}

	/** The tool's working point, in the item's own model. */
	public static Vector3f point(final Clip clip) {
		return new Vector3f(clip.number("tip_x", 8.0F) / 16.0F, clip.number("tip_y", 8.0F) / 16.0F, 0.5F);
	}

	/** Where the hand closes on the tool: {@code grip} pixels from its working point toward its other end. */
	public static OwnHand.Grip grip(final Clip clip) {
		Vector3f axis = new Vector3f(clip.number("axis_x", 0.0F), clip.number("axis_y", 1.0F), 0.0F).normalize();
		Vector3f at = point(clip).add(new Vector3f(axis).mul(clip.number("grip", 6.0F) / 16.0F));
		return new OwnHand.Grip(at.x, at.y, at.z, axis.x, axis.y, 0.0F);
	}

	/**
	 * Where the tool is drawn: from its model to the hands' space, between {@code held} (as it is plainly held, measured
	 * from the game) and where the clip puts it; {@code side} 1 when the right hand holds it.
	 */
	public static Matrix4f tool(final InHands work, final Matrix4fc held, final int side) {
		Clip clip = work.clip();
		float p = work.pose().p();
		float w = Mth.clamp(clip.at("tool.w", p) * work.pose().weight(), 0.0F, 1.0F);
		if (w <= 0.0F) {
			return new Matrix4f(held);
		}
		Vector3f at = new Vector3f(side * clip.at("tool.x", p), clip.at("tool.y", p), clip.at("tool.z", p));
		float yaw = side * clip.rad("tool.yaw", p);
		float pitch = clip.rad("tool.pitch", p);
		// the tool runs from its working point toward here; of the ways to turn about that, its picture faces the eye
		Vector3f run = new Vector3f(Mth.sin(yaw) * Mth.cos(pitch), Mth.sin(pitch), -Mth.cos(yaw) * Mth.cos(pitch));
		Vector3f eye = at.negate(new Vector3f()).normalize();
		Vector3f face = eye.sub(new Vector3f(run).mul(eye.dot(run)), new Vector3f());
		if (face.lengthSquared() < 1.0E-6F) {
			face.set(0.0F, 0.0F, 1.0F);
		}
		face.normalize();
		float roll = side * clip.rad("tool.roll", p);
		face.mul(Mth.cos(roll)).add(run.cross(face, new Vector3f()).mul(Mth.sin(roll)));
		Vector3f cross = face.cross(run, new Vector3f());
		Vector3f axis = new Vector3f(clip.number("axis_x", 0.0F), clip.number("axis_y", 1.0F), 0.0F).normalize();
		float size = held.getScale(new Vector3f()).x * (clip.has("tool.size") ? clip.at("tool.size", p) : 1.0F);
		// the picture's own axes: along the tool, square to it, and out of the picture
		Vector3f x = new Vector3f(run).mul(axis.x).sub(new Vector3f(cross).mul(axis.y)).mul(size);
		Vector3f y = new Vector3f(run).mul(axis.y).add(new Vector3f(cross).mul(axis.x)).mul(size);
		Vector3f z = new Vector3f(face).mul(size);
		Vector3f tip = point(clip);
		Matrix4f there = new Matrix4f(x.x, x.y, x.z, 0.0F, y.x, y.y, y.z, 0.0F, z.x, z.y, z.z, 0.0F, at.x, at.y, at.z, 1.0F).translate(-tip.x, -tip.y, -tip.z);
		if (w >= 1.0F) {
			return there;
		}
		Quaternionf turn = held.getNormalizedRotation(new Quaternionf()).slerp(there.getNormalizedRotation(new Quaternionf()), w);
		Vector3f place = held.getTranslation(new Vector3f()).lerp(there.getTranslation(new Vector3f()), w);
		return new Matrix4f().translationRotateScale(place, turn, Mth.lerp(w, held.getScale(new Vector3f()).x, there.getScale(new Vector3f()).x));
	}

	/** The other arm, held out to be worked on; null while it is not in view. {@code side} 1 when the right hand holds the tool. */
	public static OwnHand.@Nullable Arm other(final InHands work, final int side) {
		Clip clip = work.clip();
		float p = work.pose().p();
		float up = Mth.clamp(clip.at("other.up", p) * work.pose().weight(), 0.0F, 1.0F);
		if (up <= 0.001F) {
			return null;
		}
		float low = (1.0F - up) * (1.0F - up) * OwnBody.DROP;
		Vector3f fist = new Vector3f(side * clip.at("other.x", p), clip.at("other.y", p) - low, clip.at("other.z", p));
		Vector3f elbow = new Vector3f(side * clip.at("other.ex", p), clip.at("other.ey", p) - low, clip.at("other.ez", p));
		return OwnHand.held(fist, elbow, clip.rad("other.turn", p), -side);
	}

	private static float smooth(final float t) {
		float c = Mth.clamp(t, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}
}
