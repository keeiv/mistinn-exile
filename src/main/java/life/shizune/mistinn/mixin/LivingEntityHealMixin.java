package life.shizune.mistinn.mixin;

import life.shizune.mistinn.body.BodyService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla healing mends living parts; surgery and resting handle destroyed parts separately. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHealMixin {
	@Inject(method = "heal", at = @At("HEAD"), cancellable = true)
	private void mistinn$healParts(final float amount, final CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player && BodyService.applies(player)) {
			BodyService.heal(player, amount);
			ci.cancel();
		}
	}
}
