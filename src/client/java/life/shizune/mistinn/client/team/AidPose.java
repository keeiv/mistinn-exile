package life.shizune.mistinn.client.team;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import life.shizune.mistinn.client.anim.Clip;
import life.shizune.mistinn.client.anim.Poser;
import life.shizune.mistinn.team.DownService;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A rescuer bringing a downed teammate back where they lie ({@code anim/down/aid}): down on both
 * knees at the teammate's side (whichever side the rescuer came to), upright over them with the
 * shoulders above the wound, arms straight down and both hands pressed on it, the whole body
 * bobbing to press in rhythm. It takes a few ticks to get down.
 */
public final class AidPose {
	/** A rescuer as drawn: how far down on the knees (0 to 1), where to kneel from where they stand (right and forward, once turned), which way to face, seconds in. */
	public record State(float weight, float x, float z, float facing, float seconds) {
	}

	public static final RenderStateDataKey<State> KEY = RenderStateDataKey.create(() -> "mistinn:aid");
	private static final String CLIP = "down/aid";
	/** Ticks to get down on the knees. */
	private static final float KNEEL = 6.0F;
	/** How far toward the head from the feet of a hunter lying on their back the wound is (the hand pressed to it). */
	private static final double WOUND = 0.85;
	/** How far out from the wound, at the side, the kneeling rescuer's knees are: the shoulders come over it. */
	private static final double SIDE = 0.27;
	/** How deep each press goes, in blocks, and how many a second. */
	private static final float PRESS = 0.035F;
	private static final float PRESSES = 1.7F;

	private static final Int2LongMap SINCE = new Int2LongOpenHashMap();

	private AidPose() {
	}

	/** The downed teammate this hunter is bringing back where they lie, or null. */
	private static @Nullable Player casualty(final Avatar entity) {
		if (!(entity instanceof Player)) {
			return null;
		}
		for (Player other : entity.level().players()) {
			if (other == entity) {
				continue;
			}
			DownService.Down down = DownService.get(other);
			if (down != null && !down.dragging() && down.rescuer() == entity.getId()) {
				return other;
			}
		}
		return null;
	}

	/** The pose of a rescuer kneeling by a teammate, or null when this hunter is not one. */
	public static @Nullable State sample(final Avatar entity, final float partialTicks) {
		Player casualty = casualty(entity);
		if (casualty == null) {
			SINCE.remove(entity.getId());
			return null;
		}
		long now = entity.level().getGameTime();
		if (!SINCE.containsKey(entity.getId())) {
			SINCE.put(entity.getId(), now);
		}
		float ticks = now - SINCE.get(entity.getId()) + partialTicks;
		// the wound: toward the head from the feet of one lying on their back (they face the other way)
		float yaw = casualty.getVisualRotationYInDegrees() * Mth.DEG_TO_RAD;
		Vec3 feet = casualty.getPosition(partialTicks);
		Vec3 wound = feet.add(Mth.sin(yaw) * WOUND, 0.0, -Mth.cos(yaw) * WOUND);
		// kneel at the side the rescuer is on, facing in over the wound
		Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
		Vec3 at = entity.getPosition(partialTicks);
		double side = at.subtract(wound).dot(right) >= 0.0 ? 1.0 : -1.0;
		Vec3 knees = wound.add(right.scale(side * SIDE));
		Vec3 in = wound.subtract(knees);
		float facing = (float) (Mth.atan2(in.z, in.x) * Mth.RAD_TO_DEG) - 90.0F;
		// where the knees are from where the rescuer stands, in their own frame once turned to face in
		float turned = facing * Mth.DEG_TO_RAD;
		Vec3 move = knees.subtract(at);
		float forward = (float) (move.x * -Mth.sin(turned) + move.z * Mth.cos(turned));
		float rightward = (float) (move.x * -Mth.cos(turned) + move.z * -Mth.sin(turned));
		return new State(Mth.clamp(ticks / KNEEL, 0.0F, 1.0F), rightward, forward, facing, ticks / 20.0F);
	}

	/** Down on the knees at the side, the whole body bobbing with each press. */
	public static void root(final State state, final PoseStack pose, final float scale) {
		Clip clip = Clip.get(CLIP);
		float w = state.weight();
		float press = press(state.seconds()) * PRESS;
		Poser.root(w * state.x(), w * (clip.at("root.y", 1.0F) - press), w * state.z(), 0.0F, 0.0F, w * clip.at("root.pitch", 1.0F),
			clip.number("pivot", 0.0F), pose, 1, scale);
	}

	/** Arms straight down to the wound, the head bowed over it and nodding with the presses. */
	public static void limbs(final State state, final HumanoidModel<?> model) {
		float w = state.weight();
		Poser.limbs(Clip.get(CLIP), 1.0F, model, 1, w);
		float press = press(state.seconds()) * w;
		model.body.xRot += press * 0.05F;
		model.head.xRot += press * 0.08F;
	}

	/** 0 at the top of a press, 1 at the bottom. */
	private static float press(final float seconds) {
		return Mth.sin(seconds * PRESSES * Mth.PI * 2.0F) * 0.5F + 0.5F;
	}
}
