package life.shizune.mistinn.client.mixin;

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
 * Arms and legs through a roll or dive, decided by the arms' health: a whole arm tucks in or
 * reaches out to take the fall, a destroyed arm hangs and is flung about by the turn.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidRollMixin {
	@Inject(method = "setupAnim", at = @At("TAIL"))
	private void mistinn$rollLimbs(final HumanoidRenderState state, final CallbackInfo ci) {
		RollClient.Pose roll = state.getData(RollClient.KEY);
		if (roll == null) {
			return;
		}
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		float t = roll.t();
		float arc = Mth.sin(t * Mth.PI);
		if (roll.kind() == RollService.Kind.ROLL) {
			arm(model.rightArm, roll.rightArm(), t, arc, 1);
			arm(model.leftArm, roll.leftArm(), t, arc, -1);
			model.rightLeg.xRot = -1.7F * arc;
			model.leftLeg.xRot = -1.5F * arc;
			model.head.xRot = 0.6F * arc;
		} else {
			reach(model.rightArm, roll.rightArm(), t, 1);
			reach(model.leftArm, roll.leftArm(), t, -1);
			model.rightLeg.xRot = 0.35F * arc;
			model.leftLeg.xRot = 0.25F * arc;
			model.head.xRot = -0.5F * arc;
		}
	}

	/** A whole arm tucks in to the chest; a destroyed one swings loose with the turn. */
	private static void arm(final ModelPart arm, final float health, final float t, final float arc, final int side) {
		if (health > 0.0F) {
			float grip = 0.4F + 0.6F * health;
			arm.xRot = -2.3F * arc * grip;
			arm.zRot = side * -0.45F * arc * grip;
		} else {
			arm.xRot = 0.8F * Mth.sin(t * Mth.TWO_PI * 2.0F);
			arm.zRot = side * (0.6F + 0.5F * arc);
		}
	}

	/** In a dive a whole arm is thrown forward to break the fall; a destroyed one cannot. */
	private static void reach(final ModelPart arm, final float health, final float t, final int side) {
		float out = t < 0.35F ? t / 0.35F : t > 0.75F ? 1.0F - (t - 0.75F) / 0.25F : 1.0F;
		if (health > 0.0F) {
			arm.xRot = -2.9F * out;
			arm.zRot = side * -0.25F * out;
		} else {
			arm.xRot = 0.3F * out;
			arm.zRot = side * 0.15F * out;
		}
	}
}
