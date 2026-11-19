package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.medical.TreatmentClient;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Carries a hunter's medical or repair use to the renderer. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarTreatmentMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void mistinn$treatState(final Avatar entity, final AvatarRenderState state, final float partialTicks, final CallbackInfo ci) {
		state.setData(TreatmentClient.KEY, TreatmentClient.sample(entity.getId()));
	}
}
