package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.mist.ShoreMistClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The shore mist thickens whatever fog the world was going to be drawn with, before it is sent to the screen. */
@Mixin(FogRenderer.class)
public abstract class ShoreMistFogMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void mistinn$shoreMist(final Camera camera, final int renderDistance, final DeltaTracker deltaTracker, final float darken,
		final ClientLevel level, final CallbackInfoReturnable<FogData> cir) {
		ShoreMistClient.apply(cir.getReturnValue(), camera);
	}
}
