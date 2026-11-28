package life.shizune.mistinn.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.client.gear.DetectorClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In one's own view, the empty right hand rises toward the temple to touch the detector. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonDetectorMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void mistinn$reach(
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
		if (mc.player == null || hand != InteractionHand.MAIN_HAND || !itemStack.isEmpty()) {
			return;
		}
		Float reach = DetectorClient.reach(mc.player.getId());
		if (reach != null) {
			DetectorClient.firstPerson(reach, poseStack);
		}
	}
}
