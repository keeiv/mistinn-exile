package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.move.RollClient;
import life.shizune.mistinn.stamina.RollService;
import life.shizune.mistinn.stamina.StaminaService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every swing (hit or miss) is paid for with stamina; with none left, or mid-roll, the hunter
 * cannot swing. Digging at a block is not a swing.
 */
@Mixin(Minecraft.class)
public abstract class StaminaInputMixin {
	@Shadow
	public @Nullable LocalPlayer player;

	@Shadow
	public @Nullable HitResult hitResult;

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void mistinn$payForSwing(final CallbackInfoReturnable<Boolean> cir) {
		LocalPlayer self = this.player;
		if (self == null || !StaminaService.applies(self)) {
			return;
		}
		if (RollClient.busy()) {
			cir.setReturnValue(false);
			return;
		}
		if (this.hitResult != null && this.hitResult.getType() == HitResult.Type.BLOCK) {
			return;
		}
		if (StaminaService.get(self) <= 0.0F) {
			cir.setReturnValue(false);
			return;
		}
		ClientPlayNetworking.send(new RollService.SwingPayload());
	}
}
