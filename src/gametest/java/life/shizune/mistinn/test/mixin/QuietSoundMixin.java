package life.shizune.mistinn.test.mixin;

import net.minecraft.client.sounds.SoundEngine;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The game tests run silent: each time the sound engine starts (and again after every resource reload), the listener's
 * gain goes to zero. Sounds still play as far as the game can tell, so the tests that check them still pass; nothing
 * reaches the speakers. {@code -Ploud} keeps the sound.
 */
@Mixin(SoundEngine.class)
public abstract class QuietSoundMixin {
	@Inject(method = "loadLibrary", at = @At("TAIL"))
	private void mistinn$silent(final CallbackInfo ci) {
		if (Boolean.getBoolean("mistinn.loud")) {
			return;
		}
		try {
			AL10.alListenerf(AL10.AL_GAIN, 0.0F);
		} catch (RuntimeException | UnsatisfiedLinkError ignored) {
			// no sound device: silent already
		}
	}
}
