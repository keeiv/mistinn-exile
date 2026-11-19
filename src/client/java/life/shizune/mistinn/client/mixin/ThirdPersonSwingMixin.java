package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.anim.WeaponSwings;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity.SwingDescription;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drives the arms of humanoid models with each weapon branch's swing, seen from outside. */
@Mixin(HumanoidModel.class)
public abstract class ThirdPersonSwingMixin {
	@Inject(method = "setupAttackAnimation", at = @At("TAIL"))
	private void mistinn$swing(final HumanoidRenderState state, final CallbackInfo ci) {
		SwingDescription swing = state.currentSwing;
		if (swing == null || state.swingAnimation <= 0.0F) {
			return;
		}
		ItemStack stack = swing.hand() == InteractionHand.MAIN_HAND ? state.getMainHandItemStack() : (state.mainArm == HumanoidArm.RIGHT ? state.leftHandItemStack : state.rightHandItemStack);
		WeaponStyle style = WeaponStyle.of(stack);
		if (style == null) {
			return;
		}
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		HumanoidArm arm = swing.hand().asArm(state.mainArm);
		WeaponSwings.thirdPerson(style, WeaponSwings.progress(state.swingAnimation), model, model.getArm(arm), model.getArm(arm.getOpposite()), arm == HumanoidArm.RIGHT ? 1 : -1);
	}
}
