package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.grid.QuickWheel;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While the quick-use wheel is open the mouse moves its pick, and the view holds still. */
@Mixin(MouseHandler.class)
public abstract class QuickWheelMouseMixin {
	@Shadow
	private double accumulatedDX;
	@Shadow
	private double accumulatedDY;

	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	private void mistinn$pickOnTheWheel(final double time, final CallbackInfo ci) {
		if (QuickWheel.open()) {
			QuickWheel.mouse(this.accumulatedDX, this.accumulatedDY);
			this.accumulatedDX = 0.0;
			this.accumulatedDY = 0.0;
			ci.cancel();
		}
	}
}
