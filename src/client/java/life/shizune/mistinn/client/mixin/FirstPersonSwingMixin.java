package life.shizune.mistinn.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.client.anim.WeaponSwings;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the vanilla first-person swing with each weapon branch's own swing. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonSwingMixin {
	/** The item in the hand being drawn; swingArm does not receive it. Render thread only. */
	@Unique
	private ItemStack mistinn$drawing = ItemStack.EMPTY;

	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void mistinn$remember(
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
		this.mistinn$drawing = itemStack;
	}

	@Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
	private void mistinn$swing(final float animation, final PoseStack poseStack, final int invert, final HumanoidArm arm, final CallbackInfo ci) {
		WeaponStyle style = WeaponStyle.of(this.mistinn$drawing);
		if (style != null) {
			WeaponSwings.firstPerson(style, animation, poseStack, invert);
			ci.cancel();
		}
	}
}
