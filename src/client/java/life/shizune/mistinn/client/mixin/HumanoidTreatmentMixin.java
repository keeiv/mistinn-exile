package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.enemy.ExecutionView;
import life.shizune.mistinn.client.medical.TreatmentClient;
import life.shizune.mistinn.client.move.RollClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Poses a hunter through a medical or repair use, seen from outside ({@code anim/use/*}). */
@Mixin(HumanoidModel.class)
public abstract class HumanoidTreatmentMixin {
	@Inject(method = "setupAnim", at = @At("TAIL"))
	private void mistinn$treat(final HumanoidRenderState state, final CallbackInfo ci) {
		TreatmentClient.Pose pose = state.getData(TreatmentClient.KEY);
		if (pose == null || state.getData(RollClient.KEY) != null || state.getData(ExecutionView.KEY) != null) {
			return;
		}
		TreatmentClient.thirdPerson(pose, (HumanoidModel<?>) (Object) this, state.mainArm == HumanoidArm.RIGHT ? 1 : -1);
		if (Boolean.TRUE.equals(state.getData(life.shizune.mistinn.client.view.OwnBody.KEY))) {
			TreatmentClient.ownLift(pose, (HumanoidModel<?>) (Object) this);
		}
	}
}
