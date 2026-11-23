package life.shizune.mistinn.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.food.HungerCap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A full stomach no longer heals hunters quickly; body parts recover slowly on their own instead.
 * A stomach ruined by damp food ({@link HungerCap}) holds no more than its cap.
 */
@Mixin(FoodData.class)
public abstract class FoodDataMixin {
	@Shadow
	private int foodLevel;

	@Shadow
	private float saturationLevel;

	/** The owner's cap, taken each tick: eating, which has no owner at hand, is held to it. */
	@Unique
	private int mistinn$cap = HungerCap.FULL;

	@WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V"))
	private boolean mistinn$noFoodRegen(final ServerPlayer player, final float amount) {
		return !BodyService.applies(player);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void mistinn$takeCap(final ServerPlayer player, final CallbackInfo ci) {
		this.mistinn$cap = HungerCap.of(player);
		this.mistinn$holdToCap();
	}

	@Inject(method = "add", at = @At("TAIL"))
	private void mistinn$capMeal(final int food, final float saturation, final CallbackInfo ci) {
		this.mistinn$holdToCap();
	}

	@Unique
	private void mistinn$holdToCap() {
		if (this.foodLevel > this.mistinn$cap) {
			this.foodLevel = this.mistinn$cap;
		}
		if (this.saturationLevel > this.foodLevel) {
			this.saturationLevel = this.foodLevel;
		}
	}
}
