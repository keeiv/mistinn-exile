package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.gear.DetectorClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A hunter reaching up to touch their detector, seen from outside: the right hand to the head. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidDetectorMixin {
	@Inject(method = "setupAnim", at = @At("TAIL"))
	private void mistinn$reach(final HumanoidRenderState state, final CallbackInfo ci) {
		Float reach = state.getData(DetectorClient.KEY);
		if (reach == null) {
			return;
		}
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		DetectorClient.thirdPerson(reach, model.getArm(HumanoidArm.RIGHT), model.head);
	}
}
