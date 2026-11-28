package life.shizune.mistinn.mixin;

import life.shizune.mistinn.weapon.SkeletonAmmo;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While a skeleton carrying blast, frost or shock arrows draws, its arrowhead shows in a wisp of colour ({@link SkeletonAmmo#drawing}). */
@Mixin(RangedBowAttackGoal.class)
public abstract class SkeletonDrawMixin {
	@Shadow
	@Final
	private Monster mob;

	@Inject(method = "tick", at = @At("TAIL"))
	private void mistinn$drawing(final CallbackInfo ci) {
		if (this.mob instanceof AbstractSkeleton skeleton) {
			SkeletonAmmo.drawing(skeleton);
		}
	}
}
