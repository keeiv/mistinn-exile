package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.combat.ParryClient;
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

/** Poses a hunter with their guard up, seen from outside. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidParryMixin {
	@Inject(method = "setupAnim", at = @At("TAIL"))
	private void mistinn$guard(final HumanoidRenderState state, final CallbackInfo ci) {
		ParryClient.Pose pose = state.getData(ParryClient.KEY);
		if (pose == null || state.getData(RollClient.KEY) != null || state.getData(ExecutionView.KEY) != null
			|| state.getData(TreatmentClient.KEY) != null) {
			return;
		}
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		HumanoidArm arm = state.mainArm;
		ParryClient.thirdPerson(pose, model.getArm(arm), model.getArm(arm.getOpposite()), arm == HumanoidArm.RIGHT ? 1 : -1);
	}
}
