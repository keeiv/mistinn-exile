package life.shizune.mistinn.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.client.anim.CameraSwing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla tilts the view here when the player is hurt, both for the world and for the hands;
 * the swing motion goes in the same place, so world and weapon move together.
 */
@Mixin(GameRenderer.class)
public abstract class CameraSwingMixin {
	@Inject(method = "bobHurt", at = @At("RETURN"))
	private void mistinn$swingView(final CameraRenderState cameraState, final PoseStack poseStack, final CallbackInfo ci) {
		CameraSwing.apply(poseStack, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
	}
}
