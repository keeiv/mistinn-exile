package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.body.LostLimbs;
import life.shizune.mistinn.client.body.LostLimbsView;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Carries a player's lost limbs to the renderer. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarLostLimbsMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void mistinn$lostLimbs(final Avatar entity, final AvatarRenderState state, final float partialTicks, final CallbackInfo ci) {
		int mask = entity instanceof Player player ? LostLimbs.mask(player) : 0;
		state.setData(LostLimbsView.KEY, mask == 0 ? null : mask);
	}
}
