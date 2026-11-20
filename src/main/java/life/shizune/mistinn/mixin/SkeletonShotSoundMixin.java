package life.shizune.mistinn.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import life.shizune.mistinn.registry.VanillaCreatureSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractSkeleton.class)
public abstract class SkeletonShotSoundMixin {
	@ModifyExpressionValue(
		method = "performRangedAttack",
		at = @At(value = "FIELD", target = "Lnet/minecraft/sounds/SoundEvents;SKELETON_SHOOT:Lnet/minecraft/sounds/SoundEvent;")
	)
	private SoundEvent mistinn$variantShot(final SoundEvent fallback) {
		EntityType<?> type = ((AbstractSkeleton) (Object) this).getType();
		if (type == EntityTypes.BOGGED) {
			return VanillaCreatureSounds.BOGGED_SHOOT;
		}
		if (type == EntityTypes.PARCHED) {
			return VanillaCreatureSounds.PARCHED_SHOOT;
		}
		if (type == EntityTypes.STRAY) {
			return VanillaCreatureSounds.STRAY_SHOOT;
		}
		return fallback;
	}
}
