package life.shizune.mistinn.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.client.anim.HeavyClient;
import life.shizune.mistinn.weapon.WeaponStyle;
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

/** Draws the weapon back while a heavy blow charges, and through the strike, in one's own view. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHeavyMixin {
	@Inject(
		method = "submitArmWithItem",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V")
	)
	private void mistinn$heavy(
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
		if (mc.player == null || hand != InteractionHand.MAIN_HAND || WeaponStyle.of(itemStack) == null) {
			return;
		}
		HeavyClient.Pose pose = HeavyClient.sample(mc.player, partialTicks);
		if (pose == null) {
			return;
		}
		HeavyClient.firstPerson(pose, poseStack, mc.player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1);
	}
}
