package life.shizune.mistinn.mixin;

import life.shizune.mistinn.skill.Masteries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A hunter's cut with their riposte ready goes in deeper ({@link Masteries#riposte}). */
@Mixin(LivingEntity.class)
public abstract class RiposteMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float mistinn$riposte(final float amount, final ServerLevel level, final DamageSource source, final float original) {
		return Masteries.riposte((LivingEntity) (Object) this, source, amount);
	}
}
