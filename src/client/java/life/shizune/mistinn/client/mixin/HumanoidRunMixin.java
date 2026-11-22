package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.anim.HeavyClient;
import life.shizune.mistinn.client.medical.TreatmentClient;
import life.shizune.mistinn.client.view.RunPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A sprinting hunter's arms pump and their legs drive, in step with the view's own motion ({@link RunPose}). */
@Mixin(HumanoidModel.class)
public abstract class HumanoidRunMixin {
	@Inject(method = "setupAnim", at = @At("TAIL"), order = 900)
	private void mistinn$run(final HumanoidRenderState state, final CallbackInfo ci) {
		Float run = state.getData(RunPose.KEY);
		// a heavy blow drawn back, or a wound being treated, has the arms
		if (run == null || state.getData(HeavyClient.KEY) != null || state.getData(TreatmentClient.KEY) != null) {
			return;
		}
		RunPose.limbs((HumanoidModel<?>) (Object) this, state, run);
	}
}
