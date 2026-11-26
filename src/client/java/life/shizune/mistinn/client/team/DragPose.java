package life.shizune.mistinn.client.team;

import life.shizune.mistinn.client.anim.Clip;
import life.shizune.mistinn.client.anim.Poser;
import life.shizune.mistinn.team.DownService;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

/**
 * A rescuer dragging a downed teammate ({@code anim/down/drag}): bent forward (the crouch's lean),
 * the left hand reached back and down to the teammate's raised hand and tugging with each step,
 * the right, the weapon hand, swinging with the walk.
 */
public final class DragPose {
	public static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "mistinn:drag");
	private static final String CLIP = "down/drag";

	private DragPose() {
	}

	/** Whether this hunter is dragging someone now. */
	public static boolean dragging(final Avatar entity) {
		if (!(entity instanceof Player)) {
			return false;
		}
		for (Player other : entity.level().players()) {
			if (other == entity) {
				continue;
			}
			DownService.Down down = DownService.get(other);
			if (down != null && down.dragging() && down.rescuer() == entity.getId()) {
				return true;
			}
		}
		return false;
	}

	public static void limbs(final HumanoidRenderState state, final HumanoidModel<?> model) {
		Poser.limbs(Clip.get(CLIP), 1.0F, model, 1, 1.0F);
		float walk = state.walkAnimationPos * 0.6662F;
		float speed = Math.min(1.0F, state.walkAnimationSpeed);
		// each step a tug on the pulling arm, and the free arm swings
		model.leftArm.xRot += Mth.sin(walk) * 0.1F * speed;
		model.rightArm.xRot += Mth.cos(walk) * 0.7F * speed;
	}
}
