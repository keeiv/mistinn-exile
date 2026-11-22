package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.view.OwnBody;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The game leaves the camera's own entity out of a first-person view; the hunter's body is let back in. */
@Mixin(LevelExtractor.class)
public abstract class OwnBodyExtractMixin {
	@Inject(method = "extractVisibleEntities", at = @At("HEAD"))
	private void mistinn$begin(final CallbackInfo ci) {
		OwnBody.world(true);
	}

	@Inject(method = "extractVisibleEntities", at = @At("RETURN"))
	private void mistinn$end(final CallbackInfo ci) {
		OwnBody.world(false);
	}

	@Redirect(method = "extractVisibleEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z"))
	private boolean mistinn$ownBody(final Camera camera) {
		return camera.isDetached() || OwnBody.active();
	}
}
