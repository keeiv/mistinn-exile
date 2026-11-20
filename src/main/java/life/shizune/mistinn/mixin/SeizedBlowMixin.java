package life.shizune.mistinn.mixin;

import life.shizune.mistinn.enemy.ExecutionService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A blow that a finisher takes over is not a hit: it skips vanilla's flinch, damage event and knockback.
 * Fabric's damage events fire before shields, invulnerability frames and armour have been weighed, so the
 * decision (made in the body service) can only be acted on after vanilla's damage step.
 */
@Mixin(LivingEntity.class)
public abstract class SeizedBlowMixin {
	@Inject(method = "hurtServer", cancellable = true, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
		target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)V"))
	private void mistinn$seizedBlow(final ServerLevel level, final DamageSource source, final float damage, final CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ServerPlayer player && ExecutionService.takeSeized(player, source)) {
			cir.setReturnValue(false);
		}
	}
}
