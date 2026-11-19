package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.stamina.StaminaService;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A hunter with no stamina left cannot keep sprinting (sprinting is decided on the client). */
@Mixin(LocalPlayer.class)
public abstract class StaminaSprintMixin {
	@Inject(method = "aiStep", at = @At("TAIL"))
	private void mistinn$spent(final CallbackInfo ci) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		if (StaminaService.applies(self) && self.isSprinting() && StaminaService.get(self) <= 0.0F) {
			self.setSprinting(false);
		}
	}
}
