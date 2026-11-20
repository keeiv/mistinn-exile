package life.shizune.mistinn.mixin;

import life.shizune.mistinn.enemy.WindupService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Melee goals and brain behaviours both end in {@code doHurtTarget}, with no event before the blow;
 * holding it here gives every melee creature the same windup. Movement is pinned after the AI step.
 */
@Mixin(Mob.class)
public abstract class MobWindupMixin {
	@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
	private void mistinn$windup(final ServerLevel level, final Entity target, final CallbackInfoReturnable<Boolean> cir) {
		if (WindupService.intercept((Mob) (Object) this, level, target)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "serverAiStep", at = @At("TAIL"))
	private void mistinn$holdStance(final CallbackInfo ci) {
		WindupService.holdStance((Mob) (Object) this);
	}
}
