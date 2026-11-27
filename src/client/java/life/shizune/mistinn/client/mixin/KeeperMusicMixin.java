package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.music.KeeperMusic;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * During a keeper fight or a sea fight, the music the game wants to play is the fight's; under the path's end, the end's;
 * under the inn ending's last pages, the morning's.
 */
@Mixin(Minecraft.class)
public abstract class KeeperMusicMixin {
	@Inject(method = "getSituationalMusic", at = @At("HEAD"), cancellable = true)
	private void mistinn$keeperFight(final CallbackInfoReturnable<Music> cir) {
		// the opening is silent: no title music under the studio's marks
		if (life.shizune.mistinn.client.title.IntroScreen.showing()) {
			cir.setReturnValue(null);
			return;
		}
		Music fight = life.shizune.mistinn.client.title.ContentWarningScreen.music();
		if (fight == null) {
			fight = life.shizune.mistinn.client.story.StoryCardScreen.music();
		}
		if (fight == null) {
			fight = life.shizune.mistinn.client.path.PathClient.music();
		}
		if (fight == null) {
			fight = KeeperMusic.current();
		}
		if (fight == null) {
			fight = life.shizune.mistinn.client.music.ElderMusic.current();
		}
		if (fight == null) {
			fight = life.shizune.mistinn.client.naval.NavalClient.music();
		}
		if (fight != null) {
			cir.setReturnValue(fight);
		}
	}
}
