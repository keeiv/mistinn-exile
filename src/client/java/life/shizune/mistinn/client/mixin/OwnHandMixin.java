package life.shizune.mistinn.client.mixin;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.client.medical.TreatmentClient;
import life.shizune.mistinn.client.view.OwnBody;
import life.shizune.mistinn.client.view.OwnHand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
 * that move the item through a swing or a heavy blow, so the arm is found from where the item really is.
 *
 * <p>A treatment worked in the hands ({@link TreatmentClient.InHands}) is placed here too: the tool is put where its
 * clip says, the hand that holds it follows, and the other arm is held out to be worked on, whatever it was holding
 * put away meanwhile.
 */
@Mixin(value = FirstPersonHandsAndItemsRenderer.class, priority = 1100)
public abstract class OwnHandMixin {
	/** The hands' space while one hand is drawn: the pose before anything is moved for that hand. Render thread only. */
	@Unique
	private PoseStack.@Nullable Pose mistinn$hands;
	/** The treatment the hand now being drawn works with its tool; null for any other hand. */
	@Unique
	private TreatmentClient.@Nullable InHands mistinn$work;
	/**
	 * How the tool of the treatment under way sat in the hand when plainly held, kept so the hand can finish its motion
	 * after the last of a stack is used up.
	 */
	@Unique
	private @Nullable Matrix4f mistinn$held;

	@Shadow
	private void renderPlayerHand(final PoseStack poseStack, final SubmitNodeCollector collector, final int light, final HumanoidArm arm, final PlayerRenderState playerState) {
		throw new AssertionError();
	}

	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void mistinn$work(
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
		this.mistinn$work = null;
		LocalPlayer player = Minecraft.getInstance().player;
		TreatmentClient.InHands work = player == null || playerState.avatarRenderState == null || playerState.avatarRenderState.isInvisible || !OwnBody.active()
			? null : TreatmentClient.inHands(player);
		if (work == null) {
			if (hand == InteractionHand.MAIN_HAND) {
				this.mistinn$held = null;
			}
			return;
		}
		int side = mistinn$side(playerState, work.hand());
		OwnHand.Arm other = TreatmentClient.other(work, side);
		if (hand != work.hand()) {
			// this arm is the one held out: what it was holding is put away, and it is drawn with the hand that works
			if (other != null) {
				ci.cancel();
			}
			return;
		}
		PoseStack.Pose hands = poseStack.last().copy();
		float lowered = OwnBody.lowered(partialTicks);
		if (other != null) {
			OwnHand.drawn(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, other, lowered);
			this.mistinn$draw(poseStack, submitNodeCollector, lightCoords, hands, other, -side, playerState);
		}
		if (itemStack.is(work.item())) {
			this.mistinn$work = work;
			return;
		}
		Matrix4f held = this.mistinn$held;
		if (itemStack.isEmpty() && held != null) {
			// the last of the stack is used up: the hand finishes its motion without it, instead of a bare arm cutting in
			Matrix4f tool = TreatmentClient.tool(work, held, side);
			OwnHand.Arm arm = OwnHand.arm(tool, TreatmentClient.grip(work.clip()), side, mistinn$aspect());
			if (arm != null) {
				OwnHand.drawn(hand, arm, lowered);
				this.mistinn$draw(poseStack, submitNodeCollector, lightCoords, hands, arm, side, playerState);
			}
			ci.cancel();
		}
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
		TreatmentClient.InHands work = this.mistinn$work;
		this.mistinn$hands = null;
		this.mistinn$work = null;
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
		float lowered = OwnBody.lowered(partialTicks);
		if (work != null) {
			// a treatment: the tool goes where its clip puts it, and the hand closes where the clip says it is held
			this.mistinn$held = new Matrix4f(model);
			model = TreatmentClient.tool(work, model, side);
			grip = TreatmentClient.grip(work.clip());
			poseStack.last().set(hands);
			poseStack.mulPose(model);
			poseStack.mulPose(new Matrix4f(display.pose()).invert());
			OwnHand.worked(model.transformPosition(TreatmentClient.point(work.clip())), lowered);
		}
		OwnHand.Arm arm = OwnHand.arm(model, grip, side, mistinn$aspect());
		if (arm == null) {
			return;
		}
		OwnHand.drawn(hand, arm, lowered);
		this.mistinn$draw(poseStack, submitNodeCollector, lightCoords, hands, arm, side, playerState);
	}

	/** Draws an arm, its upper part and its forearm, in the hands' space. */
	@Unique
	private void mistinn$draw(final PoseStack poseStack, final SubmitNodeCollector collector, final int light, final PoseStack.Pose hands, final OwnHand.Arm arm,
		final int side, final PlayerRenderState playerState) {
		for (Matrix4f part : new Matrix4f[] {arm.upper(), arm.fore()}) {
			poseStack.pushPose();
			poseStack.last().set(hands);
			poseStack.mulPose(part);
			this.renderPlayerHand(poseStack, collector, light, side > 0 ? HumanoidArm.RIGHT : HumanoidArm.LEFT, playerState);
			poseStack.popPose();
		}
	}

	/** How many times as wide as it is high the view is. */
	@Unique
	private static float mistinn$aspect() {
		Window window = Minecraft.getInstance().getWindow();
		return (float) window.getWidth() / Math.max(1, window.getHeight());
	}

	/** 1 if {@code hand} is the hunter's right arm, -1 if their left. */
	@Unique
	private static int mistinn$side(final PlayerRenderState playerState, final InteractionHand hand) {
		HumanoidArm main = playerState.avatarRenderState.mainArm;
		return (hand == InteractionHand.MAIN_HAND ? main : main.getOpposite()) == HumanoidArm.RIGHT ? 1 : -1;
	}
}
