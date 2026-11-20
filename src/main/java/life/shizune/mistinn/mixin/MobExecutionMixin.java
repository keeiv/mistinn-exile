package life.shizune.mistinn.mixin;

import life.shizune.mistinn.enemy.ExecutionService;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suspends the attacker's goal and movement controllers for the staged move, without saving NoAI. */
@Mixin(Mob.class)
public abstract class MobExecutionMixin {
	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
	private void mistinn$stageFinisher(final CallbackInfo ci) {
		Mob mob = (Mob) (Object) this;
		if (ExecutionService.holdsMob(mob)) {
			mob.setXxa(0.0F);
			mob.setZza(0.0F);
			mob.yya = 0.0F;
			mob.setSpeed(0.0F);
			ci.cancel();
		}
	}
}
