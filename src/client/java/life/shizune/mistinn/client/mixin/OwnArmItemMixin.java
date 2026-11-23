package life.shizune.mistinn.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.client.view.OwnArms;
import life.shizune.mistinn.client.view.OwnBody;
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
 * In the hunter's own view, what a hand holds while it works in front of the eye is drawn where the work has it, in
 * the body's own fist ({@link OwnArms}); an arm held out to be worked on has put what it held away. Applied after the
 * mixins that tilt a held thing, which this takes the place of while the hands work.
 */
@Mixin(value = ItemInHandLayer.class, priority = 1100)
public abstract class OwnArmItemMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void mistinn$putAway(
		final ArmedEntityRenderState state,
		final ItemStackRenderState item,
		final ItemStack itemStack,
		final HumanoidArm arm,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final int lightCoords,
		final CallbackInfo ci
	) {
		if (Boolean.TRUE.equals(state.getData(OwnBody.KEY)) && OwnArms.presented(arm)) {
			ci.cancel();
		}
		// a lost arm holds nothing, in any view
		if (life.shizune.mistinn.client.body.LostLimbsView.lost(state.getData(life.shizune.mistinn.client.body.LostLimbsView.KEY), life.shizune.mistinn.body.LostLimbs.arm(arm))) {
			ci.cancel();
		}
	}

	@Inject(
		method = "submitArmWithItem",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V")
	)
	private void mistinn$place(
		final ArmedEntityRenderState state,
		final ItemStackRenderState item,
		final ItemStack itemStack,
		final HumanoidArm arm,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final int lightCoords,
		final CallbackInfo ci
	) {
		if (Boolean.TRUE.equals(state.getData(OwnBody.KEY))) {
			OwnArms.place(state, arm, item, poseStack);
		}
	}
}
