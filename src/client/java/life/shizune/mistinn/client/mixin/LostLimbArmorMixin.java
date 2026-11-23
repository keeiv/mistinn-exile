package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.client.body.LostLimbsView;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Armour on a player's lost limb goes with it. The player's own model is seen to by {@link OwnBodyModelMixin}. */
@Mixin(HumanoidModel.class)
public abstract class LostLimbArmorMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void mistinn$lostLimbArmor(final HumanoidRenderState state, final CallbackInfo ci) {
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		if (model instanceof PlayerModel || !(state instanceof AvatarRenderState)) {
			return;
		}
		Integer mask = state.getData(LostLimbsView.KEY);
		LostLimbsView.show(model.leftArm, mask, BodyPart.LEFT_ARM);
		LostLimbsView.show(model.rightArm, mask, BodyPart.RIGHT_ARM);
		LostLimbsView.show(model.leftLeg, mask, BodyPart.LEFT_LEG);
		LostLimbsView.show(model.rightLeg, mask, BodyPart.RIGHT_LEG);
	}
}
