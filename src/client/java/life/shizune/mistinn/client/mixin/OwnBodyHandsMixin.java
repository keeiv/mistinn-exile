package life.shizune.mistinn.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.client.view.OwnBody;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The hands in front of the eye are only there while they work (a swing, a bow drawn, a block broken); otherwise they
 * hang at the hunter's sides on their own body, and this pass draws nothing. They sink out of view as they are lowered.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class OwnBodyHandsMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void mistinn$lowered(final float partialTicks, final PoseStack poseStack, final SubmitNodeCollector collector, final PlayerRenderState playerState,
		final FirstPersonHandsAndItemsRenderState state, final CallbackInfo ci) {
		if (!OwnBody.active()) {
			return;
		}
		float raised = OwnBody.raised(partialTicks);
		if (raised <= 0.0F) {
			ci.cancel();
			return;
		}
		poseStack.translate(0.0F, -(1.0F - raised) * (1.0F - raised) * OwnBody.DROP, 0.0F);
	}
}
