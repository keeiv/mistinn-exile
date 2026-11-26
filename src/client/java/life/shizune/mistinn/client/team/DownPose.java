package life.shizune.mistinn.client.team;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import life.shizune.mistinn.client.anim.Clip;
import life.shizune.mistinn.client.anim.Poser;
import life.shizune.mistinn.team.DownService;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * How a downed hunter lies, waiting for a teammate ({@code anim/down/fall}): struck in the gut they
 * double over, drop onto both knees, hold there hunched, then go over onto their back with a hand
 * pressed to the wound and the other arm flung out. Lying there they breathe hard, turn the head
 * looking for help and now and then lift the free arm toward the sky. Dragged, they turn to lie
 * head toward the rescuer, shoulders pulled up off the ground and a hand in the rescuer's
 * ({@code anim/down/dragged}), blended in and out over half a second.
 */
public final class DownPose {
	/**
	 * A downed hunter as drawn: seconds since they went down, how far into being dragged (0 to 1), the
	 * way a drag turns them, and where the head turns to a teammate kneeling to help (radians, NaN when none is).
	 */
	public record State(float seconds, float drag, float facing, float look) {
	}

	public static final RenderStateDataKey<State> KEY = RenderStateDataKey.create(() -> "mistinn:down");
	/** Seconds the fall takes, to lying still. */
	public static final float FALL = 2.4F;
	/** Ticks a drag takes to blend in or out. */
	private static final float BLEND = 10.0F;
	/** Seconds between the free arm's reaches toward the sky. */
	private static final float REACH_EVERY = 7.0F;
	/** How far the head turns to a teammate kneeling to help, in radians (toward the right when positive). */
	private static final float LOOK = 0.9F;
	private static final String FALL_CLIP = "down/fall";
	private static final String DRAGGED_CLIP = "down/dragged";

	/** Each downed hunter's drag as last seen: whether, since when, and which way it turned them. */
	private static final class Track {
		boolean dragged;
		long since;
		float facing;
	}

	private static final Int2ObjectMap<Track> TRACKS = new Int2ObjectOpenHashMap<>();

	private DownPose() {
	}

	/** The pose of a downed hunter, or null when the entity is not down. */
	public static @Nullable State sample(final Avatar entity, final float partialTicks) {
		if (!(entity instanceof Player player)) {
			return null;
		}
		DownService.Down down = DownService.get(player);
		if (down == null) {
			TRACKS.remove(entity.getId());
			return null;
		}
		long now = entity.level().getGameTime();
		// a hunter seen going down falls; one who was already lying when they came into view just lies there, alive
		float seconds = down.since() <= 0L ? FALL + (now + partialTicks) / 20.0F : Math.max(0.0F, (now - down.since() + partialTicks) / 20.0F);
		Entity rescuer = down.dragging() && down.rescuer() >= 0 ? entity.level().getEntity(down.rescuer()) : null;
		boolean dragged = rescuer != null;
		Track track = TRACKS.get(entity.getId());
		if (track == null) {
			// one already being dragged when first seen is simply dragged
			track = new Track();
			track.dragged = dragged;
			track.since = now - (long) BLEND;
			track.facing = entity.getVisualRotationYInDegrees();
			TRACKS.put(entity.getId(), track);
		} else if (track.dragged != dragged) {
			track.dragged = dragged;
			track.since = now;
		}
		if (rescuer != null) {
			// facing away from the rescuer, so that lying back the head is toward them
			Vec3 away = entity.getPosition(partialTicks).subtract(rescuer.getPosition(partialTicks));
			if (away.horizontalDistanceSqr() > 1.0E-4) {
				track.facing = (float) (Mth.atan2(away.z, away.x) * Mth.RAD_TO_DEG) - 90.0F;
			}
		}
		float t = Mth.clamp((now - track.since + partialTicks) / BLEND, 0.0F, 1.0F);
		// a teammate kneeling to help where they lie: the head turns to them
		float look = Float.NaN;
		if (!down.dragging() && down.rescuer() >= 0 && entity.level().getEntity(down.rescuer()) instanceof Player helper) {
			float yaw = entity.getVisualRotationYInDegrees() * Mth.DEG_TO_RAD;
			Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
			look = helper.position().subtract(entity.position()).dot(right) >= 0.0 ? LOOK : -LOOK;
		}
		return new State(seconds, dragged ? t : 1.0F - t, track.facing, look);
	}

	private static float progress(final float seconds) {
		return Mth.clamp(seconds / FALL, 0.0F, 1.0F);
	}

	/** The way the body faces while drawn: its own, turned to face away from the rescuer as a drag takes it. */
	public static float facing(final State state, final float own) {
		return state.drag() <= 0.0F ? own : Mth.rotLerp(state.drag(), own, state.facing());
	}

	/** The whole body's drop and turn, the fall's blended into the drag's. */
	public static void root(final State state, final PoseStack pose, final float scale) {
		Clip fall = Clip.get(FALL_CLIP);
		float p = progress(state.seconds());
		float w = state.drag();
		if (w <= 0.0F) {
			Poser.root(fall, p, pose, 1, scale);
			return;
		}
		Clip dragged = Clip.get(DRAGGED_CLIP);
		Poser.root(blend(fall, p, dragged, w, "root.x"), blend(fall, p, dragged, w, "root.y"), blend(fall, p, dragged, w, "root.z"),
			blend(fall, p, dragged, w, "root.yaw"), blend(fall, p, dragged, w, "root.roll"), blend(fall, p, dragged, w, "root.pitch"),
			Mth.lerp(w, fall.number("pivot", 0.0F), dragged.number("pivot", 0.0F)), pose, 1, scale);
	}

	private static float blend(final Clip from, final float p, final Clip to, final float w, final String track) {
		return Mth.lerp(w, from.at(track, p), to.at(track, 1.0F));
	}

	/** The limbs: the fall, then lying there alive, and the drag over both as it takes them. */
	public static void limbs(final State state, final HumanoidModel<?> model) {
		Clip fall = Clip.get(FALL_CLIP);
		float p = progress(state.seconds());
		Poser.limbs(fall, p, model, 1, fall.weightIn(p));
		float rest = state.seconds() - FALL;
		boolean helped = !Float.isNaN(state.look());
		if (rest > 0.0F) {
			lying(rest, model, helped);
		} else {
			model.head.yRot = 0.0F;
		}
		if (helped) {
			// looking to the one kneeling there, the free arm drawn in to give them room
			model.head.yRot = state.look();
			model.leftArm.zRot = Mth.lerp(0.8F, model.leftArm.zRot, -0.15F);
			model.leftArm.xRot = Mth.lerp(0.8F, model.leftArm.xRot, 0.0F);
		}
		float w = state.drag();
		if (w > 0.0F) {
			Poser.limbs(Clip.get(DRAGGED_CLIP), 1.0F, model, 1, w);
			// jolted along: the fallen-back head and the trailing arm swing a little with each pull
			float jolt = Mth.sin(state.seconds() * 9.0F) * 0.06F * w;
			model.head.xRot += jolt;
			model.leftArm.zRot += jolt * 1.5F;
			model.head.yRot = Mth.lerp(w, model.head.yRot, 0.0F);
		}
	}

	/**
	 * Lying there: breathing hard, the head turning to look for help, the free arm lifted toward the
	 * sky now and then (not while someone is already helping), the hand on the wound trembling.
	 */
	private static void lying(final float t, final HumanoidModel<?> model, final boolean helped) {
		float breath = Mth.sin(t * 2.6F) * 0.05F;
		model.body.xRot += breath * 0.3F;
		model.rightArm.xRot += breath;
		model.head.xRot += breath * 0.5F;
		model.head.yRot = Mth.sin(t * 0.45F) * 0.6F;
		float cycle = (t % REACH_EVERY) / REACH_EVERY;
		if (!helped && cycle > 0.55F && cycle < 0.85F) {
			float reach = Mth.sin((cycle - 0.55F) / 0.3F * Mth.PI);
			model.leftArm.xRot = Mth.lerp(reach, model.leftArm.xRot, -1.9F);
			model.leftArm.zRot = Mth.lerp(reach, model.leftArm.zRot, -0.25F);
		}
		model.rightArm.zRot += Mth.sin(t * 31.0F) * 0.015F;
	}
}
