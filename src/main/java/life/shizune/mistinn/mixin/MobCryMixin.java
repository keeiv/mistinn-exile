package life.shizune.mistinn.mixin;

import life.shizune.mistinn.enemy.CryService;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A creature setting on someone new: when it is a hunter, it cries out (see {@link CryService}). */
@Mixin(Mob.class)
public abstract class MobCryMixin {
	@Inject(method = "setTarget", at = @At("HEAD"))
	private void mistinn$cry(final LivingEntity target, final CallbackInfo ci) {
		Mob mob = (Mob) (Object) this;
		if (target != null && target != mob.getTarget() && !mob.level().isClientSide()) {
			CryService.spotted(mob, target);
		}
	}
}
