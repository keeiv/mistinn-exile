package life.shizune.mistinn.mixin;

import life.shizune.mistinn.hardship.Ferocity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A Rust Anchor skeleton looses its arrows faster, on any difficulty the world was set to ({@link Ferocity#bowGap}). */
@Mixin(AbstractSkeleton.class)
public abstract class SkeletonGapMixin {
	@Inject(method = {"getAttackInterval", "getHardAttackInterval"}, at = @At("RETURN"), cancellable = true)
	private void mistinn$faster(final CallbackInfoReturnable<Integer> cir) {
		AbstractSkeleton self = (AbstractSkeleton) (Object) this;
		cir.setReturnValue(Ferocity.gap(cir.getReturnValueI(), Ferocity.of(self.level()).bowGap()));
	}
}
