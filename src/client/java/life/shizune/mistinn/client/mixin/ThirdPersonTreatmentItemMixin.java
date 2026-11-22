package life.shizune.mistinn.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.client.medical.TreatmentClient;
import life.shizune.mistinn.medical.TreatmentItem;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tilts a medical or repair item in the hand through its use, seen from outside: the injector points down at the thigh. */
@Mixin(ItemInHandLayer.class)
public abstract class ThirdPersonTreatmentItemMixin {
	@Inject(
		method = "submitArmWithItem",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V")
	)
	private void mistinn$tiltTreatment(
		final ArmedEntityRenderState state,
		final ItemStackRenderState item,
		final ItemStack itemStack,
		final HumanoidArm arm,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final int lightCoords,
		final CallbackInfo ci
	) {
		if (arm != state.mainArm || !(itemStack.getItem() instanceof TreatmentItem)) {
			return;
		}
		TreatmentClient.Pose pose = state.getData(TreatmentClient.KEY);
		if (pose != null) {
			TreatmentClient.thirdPersonItem(pose, poseStack);
			if (Boolean.TRUE.equals(state.getData(life.shizune.mistinn.client.view.OwnBody.KEY))) {
				TreatmentClient.ownItem(pose, poseStack);
			}
		}
	}
}
