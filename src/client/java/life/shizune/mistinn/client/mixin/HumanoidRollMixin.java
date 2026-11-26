package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.anim.Clip;
import life.shizune.mistinn.client.anim.Poser;
import life.shizune.mistinn.client.death.DeathScene;
import life.shizune.mistinn.client.enemy.ExecutionView;
import life.shizune.mistinn.client.move.RollClient;
import life.shizune.mistinn.stamina.RollService;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Arms and legs through a roll, a dive and the death scene, from the same clips that move the
 * whole body ({@code anim/move/*}, {@code anim/death/*}). The arms' health bends the clip: a
 * weak arm tucks in less, a destroyed arm cannot hold any pose and is flung about by the turn.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidRollMixin {
	@Inject(method = "setupAnim", at = @At("TAIL"))
	private void mistinn$rollLimbs(final HumanoidRenderState state, final CallbackInfo ci) {
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		ExecutionView.Pose execution = state.getData(ExecutionView.KEY);
		if (execution != null) {
			ExecutionView.limbs(execution, model);
			return;
		}
		Float down = state.getData(life.shizune.mistinn.client.team.DownPose.KEY);
		if (down != null && state.getData(DeathScene.KEY) == null) {
			life.shizune.mistinn.client.team.DownPose.limbs(down, model);
			return;
		}
		DeathScene.Pose death = state.getData(DeathScene.KEY);
		if (death != null) {
			Clip clip = Clip.get(death.kneel() ? "death/kneel" : "death/collapse");
			float p = death.seconds() / DeathScene.GONE;
			Poser.limbs(clip, p, model, death.side(), clip.weightIn(p));
			// it shakes while it holds itself up on the knee
			if (death.kneel() && death.seconds() > 0.75F && death.seconds() < DeathScene.FALL) {
				float shake = Mth.sin(death.seconds() * 38.0F) * 0.03F;
				model.rightArm.xRot += shake;
				model.head.xRot += shake * 0.5F;
			}
			model.head.yRot = clip.has("limbs.head_y") ? model.head.yRot : 0.0F;
			return;
		}
		RollClient.Pose roll = state.getData(RollClient.KEY);
		if (roll == null) {
			return;
		}
		float t = roll.t();
		boolean slide = roll.kind() == RollService.Kind.SLIDE;
		// a dead arm hangs through a slide as it does through a dive: no turn flings it
		boolean dive = roll.kind() == RollService.Kind.DIVE || slide;
		boolean armless = roll.leftArm() <= 0.0F && roll.rightArm() <= 0.0F;
		Clip clip = Clip.get(slide ? "move/slide" : dive ? "move/dive" : armless ? "move/roll_armless" : "move/roll");
		float rest = model.rightArm.xRot;
		float restLeft = model.leftArm.xRot;
		Poser.limbs(clip, t, model, 1, clip.weight(t));
		mistinn$weakArm(model.rightArm, rest, roll.rightArm(), t, dive, 1);
		mistinn$weakArm(model.leftArm, restLeft, roll.leftArm(), t, dive, -1);
	}

	/**
	 * A weak arm holds the clip's pose only partly; a destroyed one hangs, and in a roll the
	 * turn flings it about.
	 */
	private static void mistinn$weakArm(final ModelPart arm, final float rest, final float health, final float t, final boolean dive, final int side) {
		if (health >= 1.0F) {
			return;
		}
		float arc = Mth.sin(t * Mth.PI);
		if (health > 0.0F) {
			float grip = 0.4F + 0.6F * health;
			arm.xRot = Mth.lerp(grip, rest, arm.xRot);
			arm.zRot *= grip;
			return;
		}
		if (dive) {
			arm.xRot = 0.3F * arc;
			arm.zRot = side * 0.15F * arc;
		} else {
			arm.xRot = 0.8F * Mth.sin(t * Mth.TWO_PI * 2.0F) * arc;
			arm.zRot = side * (0.6F + 0.5F * arc) * arc;
		}
	}
}
