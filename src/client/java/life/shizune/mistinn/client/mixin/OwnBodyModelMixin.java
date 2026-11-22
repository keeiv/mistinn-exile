package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.view.OwnBody;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In the hunter's own view their head is not drawn, nor an arm that is up in front of the eye in the hand pass. */
@Mixin(PlayerModel.class)
public abstract class OwnBodyModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void mistinn$ownBody(final AvatarRenderState state, final CallbackInfo ci) {
		PlayerModel model = (PlayerModel) (Object) this;
		boolean own = Boolean.TRUE.equals(state.getData(OwnBody.KEY));
		model.head.visible = !own;
		model.hat.visible = !own && state.showHat;
		if (state.isSpectator) {
			return;
		}
		boolean handsUp = own && OwnBody.raised(1.0F) > 0.0F;
		boolean right = state.mainArm == HumanoidArm.RIGHT;
		model.rightArm.visible = !(handsUp && right);
		model.leftArm.visible = !(handsUp && !right);
		model.rightSleeve.visible = model.rightArm.visible && state.showRightSleeve;
		model.leftSleeve.visible = model.leftArm.visible && state.showLeftSleeve;
	}
}
