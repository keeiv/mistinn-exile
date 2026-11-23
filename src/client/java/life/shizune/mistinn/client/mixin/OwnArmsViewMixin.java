package life.shizune.mistinn.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.client.view.OwnArms;
import life.shizune.mistinn.client.view.OwnBody;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notes where the hunter's own body is drawn against their eye, once it stands where it is drawn, so its arms can be
 * turned toward the work in front of the eye ({@link OwnArms}).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class OwnArmsViewMixin {
	@Inject(
		method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;isBodyVisible("
			+ "Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z")
	)
	private void mistinn$ownView(final LivingEntityRenderState state, final PoseStack poseStack, final SubmitNodeCollector collector, final CameraRenderState camera,
		final CallbackInfo ci) {
		if (!Boolean.TRUE.equals(state.getData(OwnBody.KEY))) {
			return;
		}
		state.setData(OwnArms.TO_VIEW, new Matrix4f(camera.viewRotationMatrix).mul(poseStack.last().pose()));
		state.setData(OwnArms.FROM_VIEW, new Matrix4f(camera.viewRotationMatrix).invert());
	}
}
