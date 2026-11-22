package life.shizune.mistinn.client.medical;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import life.shizune.mistinn.client.anim.Clip;
import life.shizune.mistinn.client.anim.Poser;
import life.shizune.mistinn.medical.Treatment;
import life.shizune.mistinn.medical.TreatmentService;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * 處置動作: the client half of timed uses. Keeps every visible hunter's use as the server told
 * it, and plays its clip ({@code anim/use/<treatment>.json}): {@code limbs} poses the body
 * seen from outside, {@code item.x} tilts the held item about the grip, and
 * {@code first_person} ({@code tx ty tz} in blocks, {@code rx ry rz} in degrees) moves the item
 * in one's own view. A clip with {@code loop} (seconds) repeats its motion for as long as the
 * use lasts; one without is stretched over the whole use. Clips blend in over
 * {@link #BLEND} seconds and back out when the use ends, finished or not.
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
		long end = -1L;
		int result = TreatmentService.RESULT_RUNNING;

		Use(final Treatment treatment, final Item item, final long start, final float duration) {
			this.treatment = treatment;
			this.item = item;
			this.start = start;
			this.duration = duration;
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
			USES.put(payload.entity(), new Use(Treatment.byId(payload.treatment()), Item.byId(payload.item()), now, payload.ticks() / 20.0F));
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
		float scale = Mth.lerp(pose.weight(), 1.0F, clip(pose.treatment()).number("own_scale", 1.0F));
		if (scale != 1.0F) {
			stack.scale(scale, scale, scale);
		}
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

	private static float smooth(final float t) {
		float c = Mth.clamp(t, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}
}
