package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.gear.DetectorClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A right click with an empty hand on nothing that answers touches the detector worn on the face ({@link DetectorClient#used}). */
@Mixin(Minecraft.class)
public abstract class DetectorTouchMixin {
	@Inject(method = "startUseItem", at = @At("HEAD"))
	private void mistinn$touch(final CallbackInfo ci) {
		DetectorClient.used((Minecraft) (Object) this);
	}
}
