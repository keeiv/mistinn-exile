package life.shizune.mistinn.mixin;

import life.shizune.mistinn.weapon.ElementArrow;
import life.shizune.mistinn.weapon.SkeletonAmmo;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A skeleton carrying blast, frost or shock arrows looses them, wider, and is rocked back after ({@link SkeletonAmmo}). */
@Mixin(AbstractSkeleton.class)
public abstract class SkeletonAmmoMixin {
	@Inject(method = "getArrow", at = @At("RETURN"), cancellable = true)
	private void mistinn$specialArrow(final ItemStack ammo, final float velocity, final ItemStack weapon, final CallbackInfoReturnable<AbstractArrow> cir) {
		AbstractSkeleton self = (AbstractSkeleton) (Object) this;
		ElementArrow.Kind kind = SkeletonAmmo.of(self);
		if (kind == null) {
			return;
		}
		ElementArrow arrow = new ElementArrow(kind, self, self.level(), new ItemStack(kind.item()), weapon);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		SkeletonAmmo.loosed(self);
		cir.setReturnValue(arrow);
	}

	@ModifyArg(method = "performRangedAttack", index = 7, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectileUsingShoot(Lnet/minecraft/world/entity/projectile/Projectile;"
			+ "Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;DDDFF)Lnet/minecraft/world/entity/projectile/Projectile;"))
	private float mistinn$wider(final float inaccuracy) {
		return SkeletonAmmo.of((AbstractSkeleton) (Object) this) != null ? inaccuracy + SkeletonAmmo.WIDER : inaccuracy;
	}
}
