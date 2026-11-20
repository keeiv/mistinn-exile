package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.music.KeeperMusic;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** During a keeper fight, the music the game wants to play is the fight's. */
@Mixin(Minecraft.class)
public abstract class KeeperMusicMixin {
	@Inject(method = "getSituationalMusic", at = @At("HEAD"), cancellable = true)
	private void mistinn$keeperFight(final CallbackInfoReturnable<Music> cir) {
		Music fight = KeeperMusic.current();
		if (fight != null) {
			cir.setReturnValue(fight);
		}
	}
}
