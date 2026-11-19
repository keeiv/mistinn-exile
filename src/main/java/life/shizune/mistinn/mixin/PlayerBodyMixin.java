package life.shizune.mistinn.mixin;

import life.shizune.mistinn.body.BodyService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hunters take damage on body parts instead of one health pool. Only the final step is replaced:
 * invulnerability frames, shields, knockback and the death check around it stay vanilla's.
 */
@Mixin(Player.class)
public abstract class PlayerBodyMixin {
	@Inject(method = "actuallyHurt", at = @At("HEAD"), cancellable = true)
	private void mistinn$hurtParts(final ServerLevel level, final DamageSource source, final float damage, final CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player && BodyService.applies(player)) {
			BodyService.hurt(player, level, source, damage);
			ci.cancel();
		}
	}
}
