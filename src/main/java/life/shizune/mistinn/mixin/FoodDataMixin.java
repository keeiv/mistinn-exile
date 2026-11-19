package life.shizune.mistinn.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import life.shizune.mistinn.body.BodyService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A full stomach no longer heals hunters quickly; body parts recover slowly on their own instead. */
@Mixin(FoodData.class)
public abstract class FoodDataMixin {
	@WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V"))
	private boolean mistinn$noFoodRegen(final ServerPlayer player, final float amount) {
		return !BodyService.applies(player);
	}
}
