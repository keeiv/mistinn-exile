package life.shizune.mistinn.mixin;

import life.shizune.mistinn.enemy.WindupService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hoglins and zoglins attack without calling {@code Mob.doHurtTarget}, so their windup is held here. */
@Mixin({Hoglin.class, Zoglin.class})
public abstract class HoglinWindupMixin {
	@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
	private void mistinn$windup(final ServerLevel level, final Entity target, final CallbackInfoReturnable<Boolean> cir) {
		if (WindupService.intercept((Mob) (Object) this, level, target)) {
			cir.setReturnValue(false);
		}
	}
}
