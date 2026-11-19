package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.death.DeathScene;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla takes the dead hunter out of the world a second after death. The death scene still
 * needs the body to kneel, fall and turn to smoke, so it stays until the hunter respawns.
 */
@Mixin(LocalPlayer.class)
public abstract class DeathBodyMixin {
	@Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
	private void mistinn$keepBody(final CallbackInfo ci) {
		if (DeathScene.active()) {
			((LocalPlayer) (Object) this).deathTime++;
			ci.cancel();
		}
	}
}
