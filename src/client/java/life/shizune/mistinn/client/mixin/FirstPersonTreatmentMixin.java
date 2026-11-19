package life.shizune.mistinn.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.client.medical.TreatmentClient;
import life.shizune.mistinn.medical.TreatmentItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Moves a medical or repair item through its use in one's own view: the injector is raised
 * and driven down toward the thigh (kept in view), the pills brought up to the mouth, the kit worked on.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonTreatmentMixin {
	@Inject(
		method = "submitArmWithItem",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V")
	)
	private void mistinn$treat(
		final PlayerRenderState playerState,
		final FirstPersonHandsAndItemsRenderState state,
		final float partialTicks,
		final float xRot,
		final InteractionHand hand,
		final float attack,
		final ItemStack itemStack,
		final float inverseArmHeight,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final int lightCoords,
		final CallbackInfo ci
	) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !(itemStack.getItem() instanceof TreatmentItem) || !mc.player.getItemInHand(hand).is(itemStack.getItem())) {
			return;
		}
		TreatmentClient.Pose pose = TreatmentClient.sample(mc.player.getId());
		if (pose == null) {
			return;
		}
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? mc.player.getMainArm() : mc.player.getMainArm().getOpposite();
		TreatmentClient.firstPerson(pose, poseStack, arm == HumanoidArm.RIGHT ? 1 : -1);
	}
}
