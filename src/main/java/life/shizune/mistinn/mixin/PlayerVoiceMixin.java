package life.shizune.mistinn.mixin;

import life.shizune.mistinn.voice.Voice;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A hunter who has chosen a voice cries out in it when struck and when they fall. */
@Mixin(Player.class)
public abstract class PlayerVoiceMixin {
	@Inject(method = "getHurtSound", at = @At("RETURN"), cancellable = true)
	private void mistinn$hurtVoice(final DamageSource source, final CallbackInfoReturnable<SoundEvent> cir) {
		SoundEvent cry = Voice.hurt((Player) (Object) this);
		if (cry != null) {
			cir.setReturnValue(cry);
		}
	}

	@Inject(method = "getDeathSound", at = @At("RETURN"), cancellable = true)
	private void mistinn$deathVoice(final CallbackInfoReturnable<SoundEvent> cir) {
		Voice voice = Voice.of((Player) (Object) this);
		if (voice != null) {
			cir.setReturnValue(voice.death);
		}
	}
}
