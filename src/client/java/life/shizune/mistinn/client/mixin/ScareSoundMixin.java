package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.ledger.Haunting;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Nothing goes around the player's scares setting: the sounds the server sends that frighten (the thirteenth's scream)
 * are left out on a client whose scares are off, whatever other hunters in the world have chosen.
 */
@Mixin(SoundManager.class)
public abstract class ScareSoundMixin {
	@Inject(method = "play", at = @At("HEAD"), cancellable = true)
	private void mistinn$noScares(final SoundInstance instance, final CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
		if (!Haunting.scares() && Haunting.frightening(instance.getIdentifier())) {
			cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
		}
	}
}
