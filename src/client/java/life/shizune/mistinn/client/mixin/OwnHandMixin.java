package life.shizune.mistinn.client.mixin;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.client.view.OwnBody;
import life.shizune.mistinn.client.view.OwnHand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the arm that holds an item in the hunter's own view, gripping it ({@link OwnHand}). Applied after the mixins
 * that move the item through a swing, a heavy blow or a treatment, so the arm is found from where the item really is.
 */
@Mixin(value = FirstPersonHandsAndItemsRenderer.class, priority = 1100)
public abstract class OwnHandMixin {
	/** The hands' space while one hand is drawn: the pose before anything is moved for that hand. Render thread only. */
	@Unique
	private PoseStack.@Nullable Pose mistinn$hands;

	@Shadow
	private void renderPlayerHand(final PoseStack poseStack, final SubmitNodeCollector collector, final int light, final HumanoidArm arm, final PlayerRenderState playerState) {
		throw new AssertionError();
	}

	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0, shift = At.Shift.AFTER))
	private void mistinn$settle(
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
		this.mistinn$hands = null;
		// a bare hand and a map are drawn with the game's own arms, where the game puts them
		if (itemStack.isEmpty() || itemStack.has(DataComponents.MAP_ID) || playerState.avatarRenderState == null || !OwnBody.active()) {
			return;
		}
		this.mistinn$hands = poseStack.last().copy();
		poseStack.translate(mistinn$side(playerState, hand) * OwnHand.VIEW_X, OwnHand.VIEW_Y, OwnHand.VIEW_Z);
	}

	@Inject(
		method = "submitArmWithItem",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V")
	)
	private void mistinn$arm(
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
		PoseStack.Pose hands = this.mistinn$hands;
		this.mistinn$hands = null;
		if (hands == null || playerState.avatarRenderState == null || playerState.avatarRenderState.isInvisible) {
			return;
		}
		ItemStackRenderState item = hand == InteractionHand.MAIN_HAND ? state.mainHandRenderState : state.offHandRenderState;
		ItemLayersAccessor layers = (ItemLayersAccessor) item;
		if (layers.mistinn$layerCount() == 0) {
			return;
		}
		// from the item's model to the hands' space: how the item is set in the hand, then everything that moved it
		ItemLayerAccessor layer = (ItemLayerAccessor) layers.mistinn$layers()[0];
		OwnHand.Grip grip = layer.mistinn$special() == null ? OwnHand.Grip.of(itemStack) : OwnHand.Grip.around(layer.mistinn$corners().get());
		if (grip == null) {
			return;
		}
		PoseStack.Pose display = new PoseStack.Pose();
		layer.mistinn$applyTransform(display);
		Matrix4f model = new Matrix4f(hands.pose()).invert().mul(poseStack.last().pose()).mul(display.pose());
		int side = mistinn$side(playerState, hand);
		Window window = Minecraft.getInstance().getWindow();
		OwnHand.Arm arm = OwnHand.arm(model, grip, side, (float) window.getWidth() / Math.max(1, window.getHeight()));
		if (arm == null) {
			return;
		}
		OwnHand.drawn(hand, arm, OwnBody.lowered(partialTicks));
		for (Matrix4f part : new Matrix4f[] {arm.upper(), arm.fore()}) {
			poseStack.pushPose();
			poseStack.last().set(hands);
			poseStack.mulPose(part);
			this.renderPlayerHand(poseStack, submitNodeCollector, lightCoords, side > 0 ? HumanoidArm.RIGHT : HumanoidArm.LEFT, playerState);
			poseStack.popPose();
		}
	}

	/** 1 if {@code hand} is the hunter's right arm, -1 if their left. */
	@Unique
	private static int mistinn$side(final PlayerRenderState playerState, final InteractionHand hand) {
		HumanoidArm main = playerState.avatarRenderState.mainArm;
		return (hand == InteractionHand.MAIN_HAND ? main : main.getOpposite()) == HumanoidArm.RIGHT ? 1 : -1;
	}
}
