package life.shizune.mistinn.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import life.shizune.mistinn.client.anim.Clip;
import life.shizune.mistinn.client.anim.WeaponSwings;
import life.shizune.mistinn.client.keeper.SnufferModel;
import life.shizune.mistinn.client.keeper.SnufferRenderState;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns a swung weapon in the hand, seen from outside: a spear is levelled along the arm as it
 * is driven out, a hammer laid flat for its sweep. The turn is about the middle of the fist
 * ({@link WeaponSwings#turnInHand}), so the hand stays closed on the handle. The keeper's pole turns with its move instead.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ThirdPersonItemSwingMixin {
	@Inject(
		method = "submitArmWithItem",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V")
	)
	private void mistinn$tiltWeapon(
		final ArmedEntityRenderState state,
		final ItemStackRenderState item,
		final ItemStack itemStack,
		final HumanoidArm arm,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final int lightCoords,
		final CallbackInfo ci
	) {
		if (state instanceof SnufferRenderState keeper && arm == keeper.mainArm) {
			Clip clip = SnufferModel.clip(keeper);
			if (clip != null) {
				// the keeper's move owns its pole, whatever swing runs underneath
				poseStack.rotateDegrees(Axis.XP, clip.at("item.x", SnufferModel.progress(keeper)));
				return;
			}
		}
		if (state instanceof life.shizune.mistinn.client.path.ApostleRenderState apostle && arm == apostle.mainArm) {
			// 3412's move (or stance) owns how the weapon sits in the hand: turned, levelled, spun
			if (apostle.posed) {
				WeaponSwings.turnInHand(poseStack, apostle.arm("item_x"), apostle.arm("item_roll"));
			}
			return;
		}
		life.shizune.mistinn.client.anim.HeavyClient.Pose heavy = state.getData(life.shizune.mistinn.client.anim.HeavyClient.KEY);
		if (heavy != null && arm == state.mainArm) {
			life.shizune.mistinn.client.anim.HeavyClient.thirdPersonItem(heavy, poseStack);
			return;
		}
		if (state.currentSwing == null || state.swingAnimation <= 0.0F || state.currentSwing.hand().asArm(state.mainArm) != arm) {
			return;
		}
		WeaponStyle style = WeaponStyle.of(itemStack);
		if (style != null) {
			WeaponSwings.thirdPersonItem(style, WeaponSwings.progress(state.swingAnimation), poseStack);
		}
	}
}
