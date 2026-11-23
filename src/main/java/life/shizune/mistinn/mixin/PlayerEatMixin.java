package life.shizune.mistinn.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import life.shizune.mistinn.food.HungerCap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A hunter whose stomach is as full as damp food left it can eat no more, on either side. */
@Mixin(Player.class)
public abstract class PlayerEatMixin {
	@WrapOperation(method = "canEat", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;needsFood()Z"))
	private boolean mistinn$roomLeft(final FoodData food, final Operation<Boolean> original) {
		return original.call(food) && food.getFoodLevel() < HungerCap.of((Player) (Object) this);
	}
}
